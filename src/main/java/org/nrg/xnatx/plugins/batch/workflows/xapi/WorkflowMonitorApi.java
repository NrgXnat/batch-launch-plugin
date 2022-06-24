// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnatx.plugins.batch.workflows.xapi;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.swagger.annotations.*;
import org.nrg.action.ClientException;
import org.nrg.action.ServerException;
import org.nrg.containers.security.ContainerControlUserAuthorization;
import org.nrg.containers.security.WorkflowId;
import org.nrg.framework.ajax.sql.SortOrFilterException;
import org.nrg.xapi.rest.AuthDelegate;
import org.nrg.xdat.security.Authorizer;
import org.nrg.xdat.security.helpers.Features;
import org.nrg.xdat.security.helpers.Permissions;
import org.nrg.xnat.archive.ResourceData;
import org.nrg.xnatx.plugins.batch.workflows.model.Workflow;
import org.nrg.xnatx.plugins.batch.workflows.model.WorkflowPaginatedRequest;
import org.nrg.xnatx.plugins.batch.workflows.services.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.containers.model.container.auto.Container;
import org.nrg.containers.services.ContainerService;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.exceptions.InsufficientPrivilegesException;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xapi.exceptions.NoContentException;
import org.nrg.xapi.exceptions.XapiException;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.services.archive.CatalogService;
import org.nrg.xnat.turbine.utils.ArchivableItem;
import org.nrg.xnat.utils.WorkflowUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.springframework.web.bind.annotation.RequestMethod.POST;
import static org.nrg.xdat.security.helpers.AccessLevel.Authorizer;

@Slf4j
@Api()
@XapiRestController
@RequestMapping(value = "/workflows")
public class WorkflowMonitorApi extends AbstractXapiProjectRestController {
    private final ContainerService containerService;
    private final SiteConfigPreferences preferences;
    private final WorkflowService workflowService;
    private final CatalogService catalogService;
    private final ExecutorService executorService;

    @Autowired
    public WorkflowMonitorApi(final SiteConfigPreferences preferences,
                              final ContainerService containerService,
                              final WorkflowService workflowService,
                              final CatalogService catalogService,
                              @Qualifier("batchLaunchThreadPoolExecutorFactoryBean") final ThreadPoolExecutorFactoryBean batchLaunchThreadPoolExecutorFactoryBean,
                              final UserManagementServiceI userManagementService,
                              final RoleHolder roleHolder) {
        super(userManagementService, roleHolder);
        this.preferences = preferences;
        this.containerService = containerService;
        this.workflowService = workflowService;
        this.catalogService = catalogService;
        this.executorService = batchLaunchThreadPoolExecutorFactoryBean.getObject();
    }

    @ApiOperation(value = "Returns a map of workflow models.", response = List.class, responseContainer = "List")
    @ApiResponses({@ApiResponse(code = 200, message = "Workflows successfully retrieved."),
            @ApiResponse(code = 400, message = "Invalid request."),
            @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    @ResponseBody
    public List<Workflow> getWorkflows(@RequestBody WorkflowPaginatedRequest workflowPaginatedRequest)
            throws ClientException, ServerException, InsufficientPrivilegesException {

        final UserI user = getSessionUser();
        if (!hasReadAccess(user, workflowPaginatedRequest.getId(),
                workflowPaginatedRequest.getDataType())) {
            throw new InsufficientPrivilegesException("Access denied");
        }

        try {
            return workflowService.getWorkflows(workflowPaginatedRequest.getId(),
                    workflowPaginatedRequest.getDataType(), user, workflowPaginatedRequest);
        } catch (SortOrFilterException | RuntimeException e) {
            log.error("Error querying workflows", e);
            throw new ClientException(e);
        } catch (Exception e) {
            log.error("Error querying workflows", e);
            throw new ServerException(e);
        }
    }

    @ApiOperation(value = "Returns workflow model.", response = Workflow.class, responseContainer = "Workflow")
    @ApiResponses({@ApiResponse(code = 200, message = "Workflow successfully retrieved."),
            @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/{wfid}", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    @ResponseBody
    public Workflow getWorkflowStatus(@PathVariable("wfid") String wfid)
            throws NotFoundException, ServerException {
        final UserI user = getSessionUser();
        PersistentWorkflowI wrk = getWorkflowById(wfid, user);
        try {
            return workflowService.getWorkflowModelFromWorkflowI(wrk, user);
        } catch (Exception e) {
            log.error("Error retrieving workflow", e);
            throw new ServerException(e);
        }
    }

    /**
     * Check if user can read item indicated by id and dataType.
     *
     * @param user     the user
     * @param id       the item id
     * @param dataType the item data type
     * @return T/F
     */
    private boolean hasReadAccess(UserI user, String id, String dataType) {
        ArchivableItem item;
        switch (dataType) {
            case "xdat:user":
                return true;
            case XnatProjectdata.SCHEMA_ELEMENT_NAME:
                item = XnatProjectdata.getXnatProjectdatasById(id, user, false);
                break;
            case XnatSubjectdata.SCHEMA_ELEMENT_NAME:
                item = XnatSubjectdata.getXnatSubjectdatasById(id, user, false);
                break;
            default:
                item = XnatExperimentdata.getXnatExperimentdatasById(id, user, false);
                break;
        }
        return item != null;
    }

    /**
     * Check if user can access item, swallowing exceptions
     *
     * @param user the user
     * @param item the item
     * @return T/F
     */
    private boolean hasReadAccess(UserI user, ArchivableItem item) {
        try {
            return Permissions.canRead(user, item);
        } catch (Exception e) {
            log.error("Exception checking access for user {} on item {}", user.getLogin(), item.getId(), e);
            return false;
        }
    }

    /**
     * Check if user can read item on which workflow was run.
     *
     * @param user the user
     * @param wrk  the workflow object
     * @return T/F
     */
    private boolean hasReadAccess(UserI user, PersistentWorkflowI wrk) {
        // Get item (to ensure user has access)
        String id = wrk.getId();
        String dataType = wrk.getDataType();
        return hasReadAccess(user, id, dataType);
    }

    private PersistentWorkflowI getWorkflowById(String wfid, UserI user) throws NotFoundException {
        PersistentWorkflowI wrk = WorkflowUtils.getUniqueWorkflow(user, wfid);
        if (wrk == null) {
            throw new NotFoundException(wfid + " not valid workflow");
        }
        return wrk;
    }

    private Container getContainerForWorkflow(PersistentWorkflowI wrk) throws NotFoundException {
        Container container = null;
        if (workflowService.getWorkflowType(wrk) == WorkflowService.WorkflowType.CONTAINER) {
            String containerId = workflowService.getContainerId(wrk);
            if ((container = containerService.retrieve(containerId)) == null) {
                throw new NotFoundException(containerId + " not valid container");
            }
        }
        return container;
    }

    @ApiOperation(value = "Returns json representation of build directory.", response = String.class, responseContainer = "String")
    @ApiResponses({@ApiResponse(code = 200, message = "Build directory contents successfully retrieved."),
            @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."),
            @ApiResponse(code = 403, message = "User account does not have access to requested data."),
            @ApiResponse(code = 422, message = "Not a pipeline or container or no build directory."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    @AuthDelegate(ContainerControlUserAuthorization.class)
    @XapiRequestMapping(value = "/{wfid}/build_dir", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET, restrictTo = Authorizer)
    @ResponseBody
    public String getBuildDirJson(@PathVariable @WorkflowId final String wfid) throws XapiException, ServerException {

        PersistentWorkflowI wrk = getWorkflowAndCheckReadAndDownloadAccess(wfid);

        //Build dir base
        final Path buildDirPrefix = Paths.get(preferences.getBuildPath());
        List<String> buildDirs = new ArrayList<>();
        switch (workflowService.getWorkflowType(wrk)) {
            case OTHER:
                throw new XapiException(HttpStatus.UNPROCESSABLE_ENTITY, "Not a pipeline or container");

            case CONTAINER:
                final Container container = getContainerForWorkflow(wrk);
                for (Container.ContainerMount mount : container.mounts()) {
                    String xnatPath = mount.xnatHostPath();
                    // Only list mounts relative to build directory
                    // Should we filter based on writable?
                    if (Paths.get(xnatPath).startsWith(buildDirPrefix)) {
                        buildDirs.add(xnatPath);
                    }
                }
                break;
            case PIPELINE:
                String buildDir = workflowService.getBuildDir(wrk);
                if (StringUtils.isBlank(buildDir)) {
                    throw new XapiException(HttpStatus.UNPROCESSABLE_ENTITY, "No build directory");
                }
                buildDirs.add(buildDir);
                break;
        }

        boolean hasDir = false;
        for (String buildDir : buildDirs) {
            hasDir |= Files.exists(Paths.get(buildDir));
        }
        if (!hasDir) {
            throw new XapiException(HttpStatus.UNPROCESSABLE_ENTITY, "Build directories no longer exist");
        }

        try {
            //JSON stream
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            JsonFactory jfactory = new JsonFactory();
            final JsonGenerator jGenerator = jfactory
                    .createGenerator(stream, JsonEncoding.UTF8);
            jGenerator.writeStartArray();
            for (String buildDir : buildDirs) {
                addJsonForDir(new File(buildDir), jGenerator, buildDirPrefix, wfid);
            }
            jGenerator.writeEndArray();
            jGenerator.close();
            return new String(stream.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    private void addJsonForDir(File dir, JsonGenerator jGenerator, Path buildDirPrefix, String wfid)
            throws IOException {
        addJsonForDir(dir, jGenerator, buildDirPrefix, wfid, false);
    }

    private void addJsonForDir(File dir, JsonGenerator jGenerator, Path buildDirPrefix, String wfid,
                               boolean childrenOnly)
            throws IOException {

        Path d = dir.toPath();

        if (!childrenOnly) {
            // Info about base directory object
            jGenerator.writeStartObject();
            jGenerator.writeStringField("text", d.getFileName().toString());
            jGenerator.writeStringField("path", buildDirPrefix.relativize(d).toString());
            jGenerator.writeBooleanField("folder", true);
        }

        File[] files = dir.listFiles();

        boolean allOrNone = dir.listFiles().length > 1000;

        if (allOrNone && !childrenOnly) {
            // if childrenOnly, we set this with js
            jGenerator.writeBooleanField("downloadAll", true);
        }

        if (!childrenOnly) {
            jGenerator.writeFieldName("children");
        }
        // make children array
        jGenerator.writeStartArray();
        if (allOrNone) {
            jGenerator.writeStartObject();
            jGenerator.writeStringField("text", "[Directory contains more than 1000 files and subdirectories. Please download all data if needed.]");
            jGenerator.writeStringField("statusNodeType", "paging");
            jGenerator.writeBooleanField("icon", false);
            jGenerator.writeStringField("type", "folder");
            jGenerator.writeEndObject();
        } else {
            Arrays.sort(files);
            // recursively add all files and dirs to json
            for (File f : files) {
                if (f.isDirectory()) {
                    jGenerator.writeStartObject();
                    jGenerator.writeStringField("text", f.toPath().getFileName().toString());
                    jGenerator.writeStringField("path", buildDirPrefix.relativize(f.toPath()).toString());
                    jGenerator.writeBooleanField("folder", true);
                    jGenerator.writeBooleanField("children", true);
                    jGenerator.writeStringField("type", "folder");
                    jGenerator.writeEndObject();
                } else {
                    Path file = f.toPath();
                    String fpath = buildDirPrefix.relativize(file).toString();
                    String url = makeRootUrl("/xapi/workflows/" + wfid + "/get_file?path=" + fpath);
                    jGenerator.writeStartObject();
                    jGenerator.writeStringField("text",
                            "<a href='" + url + "'>" + file.getFileName().toString() + "</a>");
                    jGenerator.writeStringField("download_link", url);
                    jGenerator.writeStringField("path", fpath);
                    jGenerator.writeStringField("type", "file");
                    jGenerator.writeEndObject();
                }
            }
        }
        jGenerator.writeEndArray(); // end children array

        if (!childrenOnly) {
            jGenerator.writeEndObject();
        }
    }

    @ApiOperation(value = "Returns json representation of build directory, continued from path.",
            response = String.class, responseContainer = "String")
    @ApiResponses({@ApiResponse(code = 200, message = "Build directory contents successfully retrieved."),
            @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."),
            @ApiResponse(code = 403, message = "User account does not have access to requested data."),
            @ApiResponse(code = 422, message = "Not a pipeline or container or no build directory."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    @AuthDelegate(ContainerControlUserAuthorization.class)
    @XapiRequestMapping(value = "/{wfid}/build_dir_contd", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET, restrictTo = Authorizer)
    @ResponseBody
    public String getBuildDirJsonContinued(@PathVariable @WorkflowId final String wfid,
                                           @RequestParam final String inputPath)
            throws XapiException {

        final PersistentWorkflowI wrk = getWorkflowAndCheckReadAndDownloadAccess(wfid);

        // Get container, may be null if not a container workflow, do this outside of checkAccess so we don't repeatedly run it
        final Container container = getContainerForWorkflow(wrk);
        final Path buildPath = Paths.get(preferences.getBuildPath());

        // Check that file exists & is relative to container build dir
        final Path path = getBuildDirPathFromUserInput(inputPath, wrk, container);

        try {
            //JSON stream
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            JsonFactory jfactory = new JsonFactory();
            final JsonGenerator jGenerator = jfactory
                    .createGenerator(stream, JsonEncoding.UTF8);
            // start with children of this base directory
            addJsonForDir(path.toFile(), jGenerator, buildPath, wfid, true);
            jGenerator.close();
            return new String(stream.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new XapiException(HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    @ApiOperation(value = "Returns requested file.")
    @AuthDelegate(ContainerControlUserAuthorization.class)
    @XapiRequestMapping(value = "/{wfid}/get_file", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE, restrictTo = Authorizer)
    @ResponseBody
    public FileSystemResource getBuildDirFile(@PathVariable @WorkflowId String wfid,
                                              @RequestParam("path") String inputPath) throws Exception {

        PersistentWorkflowI wrk = getWorkflowAndCheckReadAndDownloadAccess(wfid);
        Path path = getBuildDirPathFromUserInput(inputPath, wrk, getContainerForWorkflow(wrk));
        return new FileSystemResource(path.toFile());
    }

    @ApiOperation(value = "Returns requested files.")
    @AuthDelegate(ContainerControlUserAuthorization.class)
    @XapiRequestMapping(value = "/{wfid}/get_zip", method = RequestMethod.POST,
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE, restrictTo = Authorizer)
    public ResponseEntity<StreamingResponseBody> getBuildDirZip(@PathVariable @WorkflowId String wfid,
                                                                @RequestParam("inputPaths") List<String> inputPaths)
            throws Exception {

        final PersistentWorkflowI wrk = getWorkflowAndCheckReadAndDownloadAccess(wfid);

        // Get container, may be null if not a container workflow, do this outside of checkAccess so we don't repeatedly run it
        final Container container = getContainerForWorkflow(wrk);
        final Path buildPath = Paths.get(preferences.getBuildPath());

        // Pre-check access so we can handle exceptions (once we're streaming the response body, we can't)
        final List<Path> approvedPaths = new ArrayList<>();
        for (final String inputPath : inputPaths) {
            // Check that file exists & is relative to container build dir
            approvedPaths.add(getBuildDirPathFromUserInput(inputPath, wrk, container));
        }

        return ResponseEntity
                .ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, getAttachmentDisposition("WorkflowBuildDir" + wfid, "zip"))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .body(out -> {
                    try (final ZipOutputStream zipStream = new ZipOutputStream(out)) {
                        for (final Path path : approvedPaths) {
                            // Add to zip
                            File file = path.toFile();
                            if (file.isDirectory()) {
                                addToZipRecursive(file, zipStream, buildPath);
                            } else {
                                addToZip(buildPath.relativize(path).toString(), file, zipStream);
                            }
                        }
                    }
                });
    }

    private PersistentWorkflowI getWorkflowAndCheckReadAndDownloadAccess(@PathVariable String wfid)
            throws NotFoundException, InsufficientPrivilegesException {
        final UserI user = getSessionUser();
        PersistentWorkflowI wrk = getWorkflowById(wfid, user);
        if (!hasReadAccess(user, wrk)) {
            throw new InsufficientPrivilegesException("Access denied");
        }
        if (!Features.checkRestrictedFeature(user, wrk.getExternalid(), Features.DATA_DOWNLOAD_FEATURE)) {
            throw new InsufficientPrivilegesException("User does not have access to data downloads.");
        }
        return wrk;
    }

    private Path getBuildDirPathFromUserInput(String inputPath, PersistentWorkflowI wrk, @Nullable Container container) throws NotFoundException {
        Path buildPath = Paths.get(preferences.getBuildPath());
        Path path = buildPath.resolve(inputPath).toAbsolutePath().normalize();
        if (!path.startsWith(buildPath) || !Files.exists(path)) {
            throw new NotFoundException(inputPath + " not a build directory path");
        }
        if (container == null) {
            String buildDir = workflowService.getBuildDir(wrk);
            if (StringUtils.isNotBlank(buildDir) && path.startsWith(buildDir)) {
                return path;
            }
        } else {
            for (Container.ContainerMount mount : container.mounts()) {
                if (path.startsWith(mount.xnatHostPath())) {
                    return path;
                }
            }
        }
        throw new NotFoundException(inputPath + " not from referenced workflow");
    }

    private void addToZipRecursive(@Nonnull File dir, ZipOutputStream zipStream, Path buildPath) throws IOException {
        for (File f : Objects.requireNonNull(dir.listFiles())) {
            if (f.isDirectory()) {
                addToZipRecursive(f, zipStream, buildPath);
            } else {
                addToZip(buildPath.relativize(f.toPath()).toString(), f, zipStream);
            }
        }
    }

    private void addToZip(String inputPath, File file, ZipOutputStream zipStream) throws IOException {
        // Add to zip file, log any errors
        try {
            final ZipEntry entry = new ZipEntry(inputPath);
            zipStream.putNextEntry(entry);
            try (FileInputStream inputStream = new FileInputStream(file)) {
                byte[] readBuffer = new byte[2048];
                int amountRead;
                while ((amountRead = inputStream.read(readBuffer)) > 0) {
                    zipStream.write(readBuffer, 0, amountRead);
                }
            }
            zipStream.closeEntry();
        } catch (IOException e) {
            log.error("There was a problem writing %s to the zip. " + e.getMessage(), inputPath);
            throw e;
        }
    }

    @XapiRequestMapping(value = "/{workflowId}/kill", method = POST)
    @ApiOperation(value = "Kill Process")
    @ResponseBody
    public String kill(final @PathVariable String workflowId) throws Exception {
        final UserI user = getSessionUser();
        return killJob(workflowId, user);
    }

    private void writeKillJsonReport(JsonGenerator jGenerator, String type, List<String> messages)
            throws IOException {
        jGenerator.writeFieldName(type);
        jGenerator.writeStartArray();
        for (String str : messages) {
            jGenerator.writeString(str);
        }
        jGenerator.writeEndArray();
    }

    @ApiOperation(value = "Kill all running *containerName* container processes for a list of sessions IDs.",
            response = String.class, responseContainer = "String")
    @ApiResponses({@ApiResponse(code = 200, message = "Containers successfully terminated."),
            @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/{containerName}/killactive", method = POST,
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String killActive(@PathVariable final String containerName,
                             @RequestParam("elements[]") List<String> elements)
            throws ClientException, ServerException {

        if (elements.isEmpty()) {
            throw new ClientException("No elements specified");
        }

        final UserI user = getSessionUser();
        final List<String> successMessages = new ArrayList<>();
        final List<String> failureMessages = new ArrayList<>();
        boolean isFirst = true;
        String errMsg = "";

        for (final String uri : elements) {
            if (isFirst) {
                isFirst = false;
                ResourceData resourceData = catalogService.getResourceDataFromUri(uri);
                if (!hasReadAccess(user, resourceData.getItem())) {
                    failureMessages.add("Insufficient permissions to terminate " + containerName + " workflows for " +
                            uri + ". It's likely that attempts to terminate other elements will also fail.");
                    errMsg = "; however, termination may fail due to permissions";
                    continue;
                }
            }

            try {
                executorService.submit(() -> {
                    boolean flag = false;
                    ArchivableItem item;
                    try {
                        ResourceData resourceData = catalogService.getResourceDataFromUri(uri);
                        item = resourceData.getItem();
                        if (!hasReadAccess(user, item)) {
                            log.error("User {} doesn't have read permissions for {}", user.getLogin(), uri);
                            return;
                        }
                    } catch (ClientException e) {
                        log.error("Cannot determine security item for {}", uri);
                        return;
                    }
                    for (final PersistentWorkflowI wrk : WorkflowUtils.getOpenWorkflowsForPipeline(user,
                            item.getId(), item.getXSIType(), containerName)) {
                        flag = true;
                        try {
                            killJob(wrk, user);
                        } catch (ServerException | ClientException | InsufficientPrivilegesException e) {
                            log.error("Unable to kill {} workflow {}", uri, wrk.getWorkflowId(), e);
                        }
                    }
                    if (!flag) {
                        log.debug("Experiment {}: No {} workflows in a state that can be terminated",
                                uri, containerName);
                    }
                });
                successMessages.add(uri + ": queued for termination" + errMsg);
            } catch (Exception e) {
                // Most exceptions will be logged, this will only reflect issues submitting to the executorService
                failureMessages.add(uri + ": unable to queue for termination due to " + e.getMessage());
                log.error(e.getMessage(), e);
            }
        }

        try {
            //Write json
            JsonFactory jfactory = new JsonFactory();
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            final JsonGenerator jGenerator = jfactory
                    .createGenerator(stream, JsonEncoding.UTF8);
            jGenerator.writeStartObject();
            writeKillJsonReport(jGenerator, "failures", failureMessages);
            writeKillJsonReport(jGenerator, "successes", successMessages);
            jGenerator.writeEndObject();
            jGenerator.close();
            return new String(stream.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    /**
     * Kill running container, perform permissions check against item
     *
     * @param workflowId id for corresponding workflow
     * @param user       user
     * @return string status
     */
    private String killJob(String workflowId, UserI user) throws Exception {
        PersistentWorkflowI wrkFlow = getWorkflowById(workflowId, user); //Throws exception if null
        return killJob(wrkFlow, user);
    }

    /**
     * Kill running container, assume permissions have already been checked
     *
     * @param wrkFlow the corresponding workflow
     * @param user    the user
     * @return string status
     */
    private String killJob(PersistentWorkflowI wrkFlow, UserI user)
            throws ServerException, ClientException, InsufficientPrivilegesException {
        String rtn;
        if (workflowService.getWorkflowType(wrkFlow) == WorkflowService.WorkflowType.CONTAINER) {
            String containerId = workflowService.getContainerId(wrkFlow);
            if (!containerService.canKill(containerId, user)) {
                throw new InsufficientPrivilegesException(user.getUsername());
            }
            try {
                rtn = containerService.kill(containerId, user);
            } catch (Exception e) {
                throw new ServerException(e.getMessage());
            }
        } else {
            throw new ClientException("Unable to terminate non-container workflows at this time");
            // pipeline termination implemented in xnat-web PipelineApi
        }
        return rtn;
    }

    @ApiOperation(value = "Gets the container/service ID from a workflow")
    @ApiResponses({@ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/{workflowid}/container", method = RequestMethod.GET, produces = {MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<String> getContainerOrServiceId(@PathVariable("workflowid") final String workflowId)
            throws NoContentException {
        //Get the workflow
        final UserI user = getSessionUser();
        PersistentWorkflowI wrkFlow = WorkflowUtils.getUniqueWorkflow(user, workflowId);
        if (wrkFlow == null) {
            throw new NoContentException("Workflow not found");
        }
        if (workflowService.getWorkflowType(wrkFlow) != WorkflowService.WorkflowType.CONTAINER) {
            throw new NoContentException("Container/service not found");
        }
        //Is a container launch - could be service or containter id
        final String _containerOrServiceId = wrkFlow.getComments();
        if (_containerOrServiceId == null) {
            throw new NoContentException("Container or Service ID not found for the workflow");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body(_containerOrServiceId.trim());
    }

    /**
     * Prepend site URL to path if needed
     *
     * @param path the path
     * @return the URL
     */
    private String makeRootUrl(String path) {
        return StringUtils.removeEnd(preferences.getSiteUrl(), "/") +
                StringUtils.prependIfMissing(path, "/");
    }
}
