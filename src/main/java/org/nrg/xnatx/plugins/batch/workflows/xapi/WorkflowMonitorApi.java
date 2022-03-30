// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnatx.plugins.batch.workflows.xapi;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.swagger.annotations.*;
import org.nrg.action.ClientException;
import org.nrg.action.ServerException;
import org.nrg.containers.exceptions.DockerServerException;
import org.nrg.containers.exceptions.NoDockerServerException;
import org.nrg.framework.ajax.sql.SortOrFilterException;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

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
                              @Qualifier("batchLaunchThreadPoolExecutorFactoryBean")
                                  final ThreadPoolExecutorFactoryBean batchLaunchThreadPoolExecutorFactoryBean,
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
    public ResponseEntity<List<Workflow>> getWorkflows(@RequestBody WorkflowPaginatedRequest workflowPaginatedRequest)
            throws ClientException, ServerException {

        final UserI user = getSessionUser();
        if (!checkAccess("read", user, workflowPaginatedRequest.getId(),
                workflowPaginatedRequest.getDataType())) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        try {
            return new ResponseEntity<>(workflowService.getWorkflows(workflowPaginatedRequest.getId(),
                    workflowPaginatedRequest.getDataType(), user, workflowPaginatedRequest), HttpStatus.OK);
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
    public ResponseEntity<Workflow> getWorkflowStatus(@PathVariable("wfid") String wfid)
            throws NotFoundException, ServerException {
        final UserI user = getSessionUser();
        PersistentWorkflowI wrk = getWorkflowById(wfid, user);
        try {
            return new ResponseEntity<>(workflowService.getWorkflowModelFromWorkflowI(wrk, user), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error retrieving workflow", e);
            throw new ServerException(e);
        }
    }


    /**
     * Check if user can read item indicated by id and dataType.
     *
     * @param accessType    the type of access (read or edit)
     * @param user          the user
     * @param id            the item id
     * @param dataType      the item data type
     * @return T/F
     */
    private boolean checkAccess(String accessType, UserI user, String id, String dataType) {
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
        return item != null && checkAccess(accessType, user, item);
    }

    /**
     * Check if user can access item, swallowing exceptions
     *
     * @param accessType    the type of access (read or edit)
     * @param user          the user
     * @param item          the item
     * @return T/F
     */
    private boolean checkAccess(String accessType, UserI user, ArchivableItem item) {
        try {
            return Permissions.can(user, item, accessType);
        } catch (Exception e) {
            log.error("Exception checking access for user {} on item {}", user.getLogin(), item.getId(), e);
            return false;
        }
    }

    /**
     * Check if user can read item on which workflow was run.
     *
     * @param accessType    the type of access (read or edit)
     * @param user      the user
     * @param wrk       the workflow object
     * @return T/F
     */
    private boolean checkAccess(String accessType, UserI user, PersistentWorkflowI wrk) {
        return checkAccess(accessType, user, wrk, null, null);
    }

    /**
     * Check if user can read item on which workflow was run. If pathStr provided, ensure that the path is indeed
     * relevant to the pipeline (within build dir) or container if container provided (e.g., within some directory
     * mounted for the container run)).
     *
     * @param accessType    the type of access (read or edit)
     * @param user      the user
     * @param wrk       the workflow
     * @param pathStr   optional path
     * @param container the container object
     * @return T/F
     */
    private boolean checkAccess(String accessType, UserI user, PersistentWorkflowI wrk, @Nullable String pathStr,
                                @Nullable Container container) {
        // Get item (to ensure user has access)
        String id = wrk.getId();
        String dataType = wrk.getDataType();
        if (!checkAccess(accessType, user, id, dataType)) {
            return false;
        }
        if (StringUtils.isBlank(pathStr)) {
            // No path provided, just want to know if workflow item is readable, which it is if we get here
            return true;
        } else {
            // Item is readable, now ensure that requested path is within container mount dir / pipeline build dir
            Path path = Paths.get(pathStr);
            if (container == null) {
                String buildDir = workflowService.getBuildDir(wrk);
                return StringUtils.isNotBlank(buildDir) && path.startsWith(buildDir);
            } else {
                for (Container.ContainerMount mount : container.mounts()) {
                    if (path.startsWith(mount.xnatHostPath())) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private PersistentWorkflowI getWorkflowById(String wfid, UserI user) throws NotFoundException {
        PersistentWorkflowI wrk = WorkflowUtils.getUniqueWorkflow(user, wfid);
        if (wrk == null) {
            throw new NotFoundException("Access denied or no such workflow " + wfid);
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
    @XapiRequestMapping(value = "/{wfid}/build_dir", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<String> getBuildDirJson(@PathVariable final String wfid) throws XapiException, ServerException {

        final UserI user = getSessionUser();
        PersistentWorkflowI wrk;
        try {
            wrk = getWorkflowById(wfid, user);
        } catch (NotFoundException e) {
            throw new InsufficientPrivilegesException("Access denied or no such workflow");
        }

        //Build dir base
        final Path buildDirPrefix = Paths.get(preferences.getBuildPath());
        List<String> buildDirs = new ArrayList<>();
        if (!checkAccess("read", user, wrk)) {
            throw new InsufficientPrivilegesException("Access denied");
        }

        switch (workflowService.getWorkflowType(wrk)) {
            case OTHER:
                throw new XapiException(HttpStatus.UNPROCESSABLE_ENTITY, "Not a pipeline or container");

            case CONTAINER:
                String containerId = workflowService.getContainerId(wrk);
                final Container container = containerService.retrieve(containerId);
                if (container == null) {
                    throw new InsufficientPrivilegesException("Access denied or no such container");
                }
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
            String json = new String(stream.toByteArray(), StandardCharsets.UTF_8);
            return new ResponseEntity<>(json, HttpStatus.OK);
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
    @XapiRequestMapping(value = "/{wfid}/build_dir_contd", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<String> getBuildDirJsonContinued(@PathVariable final String wfid,
                                                           @RequestParam final String inputPath)
            throws XapiException {

        final UserI user = getSessionUser();
        final PersistentWorkflowI wrk = getWorkflowById(wfid, user);

        // Get container, may be null if not a container workflow, do this outside of checkAccess so we don't repeatedly run it
        final Container container = getContainerForWorkflow(wrk);
        final Path buildPath = Paths.get(preferences.getBuildPath());

        // Check that file exists & is relative to container build dir
        final Path path = buildPath.resolve(inputPath);
        final File file = path.toFile();
        if (!file.exists() || !file.isDirectory()) {
            throw new NotFoundException(inputPath + " not found or not a directory");
        }
        if (!checkAccess("read", user, wrk, path.toString(), container)) {
            throw new InsufficientPrivilegesException(user.getUsername());
        }

        try {
            //JSON stream
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            JsonFactory jfactory = new JsonFactory();
            final JsonGenerator jGenerator = jfactory
                    .createGenerator(stream, JsonEncoding.UTF8);
            // start with children of this base directory
            addJsonForDir(path.toFile(), jGenerator, buildPath, wfid, true);
            jGenerator.close();
            String json = new String(stream.toByteArray(), StandardCharsets.UTF_8);
            return new ResponseEntity<>(json, HttpStatus.OK);
        } catch (Exception e) {
            throw new XapiException(HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    @ApiOperation(value = "Returns requested file.")
    @XapiRequestMapping(value = "/{wfid}/get_file", method = RequestMethod.GET,
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void getBuildDirFile(@PathVariable String wfid,
                                @RequestParam("path") String inputPath,
                                final HttpServletResponse response) throws Exception {

        final UserI user = getSessionUser();
        PersistentWorkflowI wrk = getWorkflowById(wfid, user);

        Path path = Paths.get(preferences.getBuildPath()).resolve(inputPath);
        File file = path.toFile();
        if (!file.exists() || !file.isFile()) {
            throw new NotFoundException(inputPath + " not found or not a file");
        }

        // Get container, may be null if not a container wf
        Container container = getContainerForWorkflow(wrk);

        if (!checkAccess("read", user, wrk, path.toString(), container)) {
            throw new InsufficientPrivilegesException(user.getUsername());
        }

        response.setStatus(HttpStatus.OK.value());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, getAttachmentDisposition(file.getName()));
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(file.length()));

        Files.copy(path, response.getOutputStream());
    }

    @ApiOperation(value = "Returns requested files.")
    @XapiRequestMapping(value = "/{wfid}/get_zip", method = RequestMethod.POST,
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public void getBuildDirZip(@PathVariable String wfid,
                               @RequestParam("inputPaths") List<String> inputPaths,
                               final HttpServletResponse response) throws Exception {

        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, getAttachmentDisposition("WorkflowBuildDir" + wfid, "zip"));
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);

        final UserI user = getSessionUser();
        final PersistentWorkflowI wrk = getWorkflowById(wfid, user);

        // Get container, may be null if not a container workflow, do this outside of checkAccess so we don't repeatedly run it
        final Container container = getContainerForWorkflow(wrk);
        final Path buildPath = Paths.get(preferences.getBuildPath());

        try (final ZipOutputStream zipStream = new ZipOutputStream(response.getOutputStream())) {
            for (final String inputPath : inputPaths) {
                // Check that file exists & is relative to container build dir
                final Path path = buildPath.resolve(inputPath);
                final File file = path.toFile();
                if (!file.exists()) {
                    throw new NotFoundException(inputPath + " not found");
                }
                if (!checkAccess("read", user, wrk, path.toString(), container)) {
                    throw new InsufficientPrivilegesException(user.getUsername());
                }
                // Add to zip
                if (file.isDirectory()) {
                    addToZipRecursive(file, zipStream, buildPath);
                } else {
                    addToZip(buildPath.relativize(path).toString(), file, zipStream);
                }
            }
        }

        response.setStatus(HttpStatus.OK.value());
    }

    private void addToZipRecursive(File dir, ZipOutputStream zipStream, Path buildPath) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                addToZipRecursive(f, zipStream, buildPath);
            } else {
                addToZip(buildPath.relativize(f.toPath()).toString(), f, zipStream);
            }
        }
    }

    private void addToZip(String inputPath, File file, ZipOutputStream zipStream) {
        // Add to zip file, log any errors but continue
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
        }
    }


    @ApiOperation(value = "Gets the log file for a given workflow")
    @ApiResponses({@ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/{workflowid}/logs/{file}", method = RequestMethod.GET, produces = {MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<String> getFile(@PathVariable("workflowid") final String workflowId, final @PathVariable("file") @ApiParam(allowableValues = "stdout, stderr") String file) throws NoContentException, NotFoundException, ServerException, DockerServerException, NoDockerServerException {
        //Get the workflow
        final UserI user = getSessionUser();
        PersistentWorkflowI wrkFlow = WorkflowUtils.getUniqueWorkflow(user, workflowId);
        if (wrkFlow == null) {
            throw new NoContentException("Workflow not found");
        }
        // Get the log
        InputStream logStream = null;
        if (workflowService.getWorkflowType(wrkFlow) == WorkflowService.WorkflowType.CONTAINER) {
            //Is a container launch - could be service or containter id
            final String _containerId = wrkFlow.getComments().trim();
            try {
                logStream = containerService.getLogStream(_containerId, file);
            } catch (org.nrg.framework.exceptions.NotFoundException e) {
                throw new NotFoundException(e.getMessage());
            }
        }
        if (logStream == null) {
            throw new NoContentException("Log file not found");
        }

        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = logStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, length);
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, getAttachmentDisposition(wrkFlow.getId() + "-" + file, "log"))
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .body(byteArrayOutputStream.toString(StandardCharsets.UTF_8.name()));
        } catch (IOException e) {
            throw new ServerException(e);
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
    public ResponseEntity<String> killActive(@PathVariable final String containerName,
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
                if (!checkAccess("read", user, resourceData.getItem())) {
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
                        if (!checkAccess("read", user, item)) {
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
                        } catch (ServerException|ClientException|InsufficientPrivilegesException e) {
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
            String json = new String(stream.toByteArray(), StandardCharsets.UTF_8);
            return new ResponseEntity<>(json, HttpStatus.OK);
        } catch (Exception e) {
            throw new ServerException(e);
        }
    }

    /**
     * Kill running container, perform permissions check against item
     * @param workflowId    id for corresponding workflow
     * @param user          user
     * @return string status
     */
    private String killJob(String workflowId, UserI user) throws Exception {
        PersistentWorkflowI wrkFlow = getWorkflowById(workflowId, user); //Throws exception if null
        return killJob(wrkFlow, user);
    }

    /**
     * Kill running container, assume permissions have already been checked
     * @param wrkFlow   the corresponding workflow
     * @param user      the user
     * @return  string status
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
     * @param path the path
     * @return the URL
     */
    private String makeRootUrl(String path) {
        return StringUtils.removeEnd(preferences.getSiteUrl(), "/") +
                StringUtils.prependIfMissing(path, "/");
    }
}
