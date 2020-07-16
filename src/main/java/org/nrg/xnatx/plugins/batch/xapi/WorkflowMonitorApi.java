// Copyright 2019 Radiologics, Inc
// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnatx.plugins.batch.xapi;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import io.swagger.annotations.*;
import org.nrg.action.ClientException;
import org.nrg.action.ServerException;
import org.nrg.xdat.security.helpers.Permissions;
import org.nrg.xnat.archive.ResourceData;
import org.nrg.xnatx.plugins.batch.exceptions.FilterException;
import org.nrg.xnatx.plugins.batch.model.Workflow;
import org.nrg.xnatx.plugins.batch.model.WorkflowListingRequest;
import org.nrg.xnatx.plugins.batch.services.WorkflowService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.containers.model.container.auto.Container;
import org.nrg.containers.services.ContainerService;
import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.exceptions.InsufficientPrivilegesException;
import org.nrg.xapi.exceptions.NotFoundException;
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
import org.restlet.data.Status;
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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Slf4j
@Api(description = "XNAT Workflow Extended API")
@XapiRestController
@RequestMapping(value = "/workflows")
public class WorkflowMonitorApi extends AbstractXapiProjectRestController {
    private ContainerService containerService;
    private       SiteConfigPreferences preferences;
    private       WorkflowService       workflowService;
    private final CatalogService        catalogService;
    private final ExecutorService executorService;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
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
    public ResponseEntity<List<Workflow>> getWorkflows(@RequestBody WorkflowListingRequest workflowListingRequest)
            throws ClientException, ServerException {

        if (workflowListingRequest.getPage() < 1 || workflowListingRequest.getSize() < 1) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        final UserI user = getSessionUser();
        if (!checkAccess("read", user, workflowListingRequest.getId(),
                workflowListingRequest.getDataType())) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        try {
            return new ResponseEntity<>(workflowService.getWorkflows(workflowListingRequest.getId(),
                    workflowListingRequest.getDataType(), user, workflowListingRequest.getSortColumn(),
                    workflowListingRequest.getSortDir(), workflowListingRequest.getPage(),
                    workflowListingRequest.getSize(), workflowListingRequest.getFiltersMap()), HttpStatus.OK);
        } catch (FilterException e) {
            log.error("Error querying workflows", e);
            throw new ClientException(Status.CLIENT_ERROR_BAD_REQUEST, e);
        } catch (Exception e) {
            log.error("Error querying workflows", e);
            throw new ServerException(Status.SERVER_ERROR_INTERNAL, e);
        }
    }

    @ApiOperation(value = "Returns workflow model.", response = Workflow.class, responseContainer = "Workflow")
    @ApiResponses({@ApiResponse(code = 200, message = "Workflow successfully retrieved."),
            @ApiResponse(code = 401, message = "Must be authenticated to access the XNAT REST API."),
            @ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/{wfid}", produces = {MediaType.APPLICATION_JSON_VALUE}, method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<Workflow> getWorkflowStatus(@PathVariable("wfid") String wfid) {
        final UserI user = getSessionUser();
        PersistentWorkflowI wrk;
        try {
            wrk = getWorkflowById(wfid, user);
        } catch (NotFoundException e) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        try {
            return new ResponseEntity<>(workflowService.getWorkflowModelFromWorkflowI(wrk, user), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error retrieving workflow", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
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
        return checkAccess(accessType, user, item);
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
    public ResponseEntity<String> getBuildDirJson(@PathVariable final String wfid) {

        final UserI user = getSessionUser();
        PersistentWorkflowI wrk;
        try {
            wrk = getWorkflowById(wfid, user);
        } catch (NotFoundException e) {
            return new ResponseEntity<>("Access denied or no such workflow", HttpStatus.FORBIDDEN);
        }

        try {
            //Build dir base
            final Path buildDirPrefix = Paths.get(preferences.getBuildPath());
            List<String> buildDirs = new ArrayList<>();
            if (!checkAccess("read", user, wrk)) {
                return new ResponseEntity<>("Access denied", HttpStatus.FORBIDDEN);
            }

            switch (workflowService.getWorkflowType(wrk)) {
                case OTHER:
                    return new ResponseEntity<>("Not a pipeline or container", HttpStatus.UNPROCESSABLE_ENTITY);
                case CONTAINER:
                    String containerId = workflowService.getContainerId(wrk);
                    final Container container = containerService.retrieve(containerId);
                    if (container == null) {
                        return new ResponseEntity<>("Access denied or no such container", HttpStatus.FORBIDDEN);
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
                        return new ResponseEntity<>("No build directory", HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                    buildDirs.add(buildDir);
                    break;
            }

            boolean hasDir = false;
            for (String buildDir : buildDirs) {
                hasDir |= Files.exists(Paths.get(buildDir));
            }
            if (!hasDir) {
                return new ResponseEntity<>("Build directories no longer exist", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            //JSON stream
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            JsonFactory jfactory = new JsonFactory();
            final JsonGenerator jGenerator = jfactory
                    .createGenerator(stream, JsonEncoding.UTF8);
            jGenerator.writeStartArray();
            for (String buildDir : buildDirs) {
                try {
                    Files.walkFileTree(Paths.get(buildDir), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            try {
                                String fpath = buildDirPrefix.relativize(file).toString();
                                jGenerator.writeStartObject();
                                jGenerator.writeStringField("title",
                                        "<a href='/xapi/workflows/" + wfid +
                                                "/get_file?path=" + fpath + "'>" + file.getFileName().toString() + "</a>");
                                jGenerator.writeStringField("path", fpath);
                                jGenerator.writeEndObject();
                            } catch (IOException e) {
                                log.error("Issue writing json", e);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException e) {
                            return FileVisitResult.CONTINUE;
                        }
                        @Override
                        public FileVisitResult preVisitDirectory(Path file, BasicFileAttributes attrs) {
                            try {
                                jGenerator.writeStartObject();
                                jGenerator.writeStringField("title", file.getFileName().toString());
                                jGenerator.writeStringField("path", buildDirPrefix.relativize(file).toString());
                                jGenerator.writeBooleanField("folder", true);
                                jGenerator.writeFieldName("children");
                                jGenerator.writeStartArray();
                            } catch (IOException e) {
                                log.error("Issue writing json", e);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                        @Override
                        public FileVisitResult postVisitDirectory(Path file, IOException e) {
                            try {
                                jGenerator.writeEndArray();
                                jGenerator.writeEndObject();
                            } catch (IOException e2) {
                                log.error("Issue writing json", e2);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    throw new AssertionError("Files.walkFileTree shouldn't throw IOException " +
                            "since we modified SimpleFileVisitor not to do so");
                }
            }
            jGenerator.writeEndArray();
            jGenerator.close();
            String json = new String(stream.toByteArray(), StandardCharsets.UTF_8);
            return new ResponseEntity<>(json, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
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
        if (!file.exists()) {
            throw new NotFoundException(inputPath + " not found");
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
        PersistentWorkflowI wrk = getWorkflowById(wfid, user);

        // Get container, may be null if not a container workflow, do this outside of checkAccess so we don't repeatedly run it
        Container container = getContainerForWorkflow(wrk);

        try (final ZipOutputStream zipStream = new ZipOutputStream(response.getOutputStream())) {
            for (final String inputPath : inputPaths) {
                // Check that file exists & is relative to container build dir
                final Path path = Paths.get(preferences.getBuildPath()).resolve(inputPath);
                final File file = path.toFile();
                if (!file.exists()) {
                    throw new NotFoundException(inputPath + " not found");
                }
                if (!checkAccess("read", user, wrk, path.toString(), container)) {
                    throw new InsufficientPrivilegesException(user.getUsername());
                }
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
        }

        response.setStatus(HttpStatus.OK.value());
    }



    @ApiOperation(value = "Gets the log file for a given workflow")
    @ApiResponses({@ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/{workflowid}/logs/{file}", method = RequestMethod.GET, produces = {MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<String> getFile(@PathVariable("workflowid") final String workflowId, final @PathVariable("file") @ApiParam(allowableValues = "stdout, stderr") String file) throws Exception {
        //Get the workflow
        final UserI user = getSessionUser();
        try {
            PersistentWorkflowI wrkFlow = WorkflowUtils.getUniqueWorkflow(user, workflowId);
            if (wrkFlow != null) {
                InputStream logStream = null;
                if (workflowService.getWorkflowType(wrkFlow) == WorkflowService.WorkflowType.CONTAINER) {
                    //Is a container launch - could be service or containter id
                    final String _containerId = wrkFlow.getComments().trim();
                    logStream = containerService.getLogStream(_containerId, file);
                }
                if (logStream != null) {
                    final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int length;
                    try {
                        while ((length = logStream.read(buffer)) != -1) {
                            byteArrayOutputStream.write(buffer, 0, length);
                        }
                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, getAttachmentDisposition(wrkFlow.getId() + "-" + file, "log"))
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                                .body(byteArrayOutputStream.toString(StandardCharsets.UTF_8.name()));
                    } catch (IOException e) {
                        return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
                    }

                } else {
                    return new ResponseEntity<>("Log file not found", HttpStatus.NO_CONTENT);
                }
            } else {
                return new ResponseEntity<>("Workflow not found", HttpStatus.NO_CONTENT);
            }
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
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
                @RequestParam("elements[]") List<String> elements) {

        try {
            if (elements.isEmpty()) {
                return new ResponseEntity<>("No elements specified", HttpStatus.BAD_REQUEST);
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
                    if (!checkAccess("edit", user, resourceData.getItem())) {
                        failureMessages.add("Insufficient permissions to terminate " + containerName + " workflows for " +
                                uri + ". It's likely that attempts to terminate other elements will also fail.");
                        errMsg = "; however, termination may fail due to permissions";
                        continue;
                    }
                }

                try {
                    executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            boolean flag = false;
                            ArchivableItem item;
                            try {
                                ResourceData resourceData = catalogService.getResourceDataFromUri(uri);
                                item = resourceData.getItem();
                                if (!checkAccess("edit", user, item)) {
                                    log.error("User {} doesn't have edit permissions for {}", user.getLogin(), uri);
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
                                } catch (ServerException|ClientException e) {
                                    log.error("Unable to kill {} workflow {}", uri, wrk.getWorkflowId(), e);
                                }
                            }
                            if (!flag) {
                                log.debug("Experiment {}: No {} workflows in a state that can be terminated",
                                        uri, containerName);
                            }
                        }
                    });
                    successMessages.add(uri + ": queued for termination" + errMsg);
                } catch (Exception e) {
                    // Most exceptions will be logged, this will only reflect issues submitting to the executorService
                    failureMessages.add(uri + ": unable to queue for termination due to " + e.getMessage());
                    log.error(e.getMessage(), e);
                }
            }


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
            String json = new String(stream.toByteArray(), "UTF-8");
            return new ResponseEntity<>(json, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
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
        if (!checkAccess("edit", user, wrkFlow.getId(), wrkFlow.getDataType())) {
            throw new ClientException("Insufficient privilege");
        }
        return killJob(wrkFlow, user);
    }

    /**
     * Kill running container, assume permissions have already been checked
     * @param wrkFlow   the corresponding workflow
     * @param user      the user
     * @return  string status
     */
    private String killJob(PersistentWorkflowI wrkFlow, UserI user) throws ServerException, ClientException {
        String rtn;
        if (workflowService.getWorkflowType(wrkFlow) == WorkflowService.WorkflowType.CONTAINER) {
            String containerId = workflowService.getContainerId(wrkFlow);
            try {
                rtn = containerService.kill(containerId, user);
            } catch (Exception e) {
                throw new ServerException(e.getMessage());
            }
        } else {
            throw new ClientException("Unable to terminate non-container workflows at this time");
//                //Could be service request is created but not yet in preparing status
//                WrkWorkflowdata workflow = (WrkWorkflowdata) wrkFlow;
//                EventDetails eventDetails = EventUtils.newEventInstance(EventUtils.CATEGORY.PROJECT_ADMIN,
//                        EventUtils.TYPE.WEB_SERVICE, EventUtils.getDeleteAction(workflow.getXSIType()));
//
//                final PersistentWorkflowI deletedWorkflow = WorkflowUtils.getOrCreateWorkflowData(null, user,
//                        WrkWorkflowdata.SCHEMA_ELEMENT_NAME, workflow.getId(), proj.getId(), eventDetails);
//                final EventMetaI ciDeleted = deletedWorkflow.buildEvent();
//                try {
//                    // If the workflow exists, delete it -
//                    final EventMetaI ci = workflow.buildEvent();
//                    SaveItemHelper.authorizedDelete(workflow.getCurrentDBVersion(), user, ci);
//                    PersistentWorkflowUtils.complete(deletedWorkflow, ciDeleted);
//                    //TODO
//                    //Remove the request from Docker Also
//
//                } catch (Exception de) {
//                    PersistentWorkflowUtils.fail(deletedWorkflow, ciDeleted);
//                    throw de;
//                }
        }
        return rtn;
    }

    @ApiOperation(value = "Gets the container/service ID from a workflow")
    @ApiResponses({@ApiResponse(code = 500, message = "Unexpected error")})
    @XapiRequestMapping(value = "/{workflowid}/container", method = RequestMethod.GET, produces = {MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<String> getContainerOrServiceId(@PathVariable("workflowid") final String workflowId) throws Exception {
        //Get the workflow
        final UserI user = getSessionUser();
        try {
            PersistentWorkflowI wrkFlow = WorkflowUtils.getUniqueWorkflow(user, workflowId);
            if (wrkFlow != null) {
                if (workflowService.getWorkflowType(wrkFlow) == WorkflowService.WorkflowType.CONTAINER) {
                    //Is a container launch - could be service or containter id
                    final String _containerOrServiceId = wrkFlow.getComments();
                    if (_containerOrServiceId != null) {
                        return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                                .body(_containerOrServiceId.trim());
                    } else {
                        return new ResponseEntity<>("Container or Service ID not found for the workflow", HttpStatus.NO_CONTENT);
                    }
                } else {
                    return new ResponseEntity<>("Container/Service not found", HttpStatus.NO_CONTENT);
                }
            } else {
                return new ResponseEntity<>("Workflow not found", HttpStatus.NO_CONTENT);
            }
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
