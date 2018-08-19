package org.nrg.xnat.bulk.xapi;

import static org.springframework.web.bind.annotation.RequestMethod.POST;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import org.nrg.framework.annotations.XapiRestController;
import org.nrg.xapi.rest.AbstractXapiProjectRestController;
import org.nrg.xapi.rest.XapiRequestMapping;
import org.nrg.xdat.security.helpers.Permissions;
import org.nrg.xdat.security.services.RoleHolder;
import org.nrg.xdat.security.services.UserManagementServiceI;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.utils.WorkflowUtils;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.containers.services.ContainerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * @author Mohana Ramaratnam
 *
 */
@XapiRestController
@RequestMapping(value = "/workflows")
@Api(description = "Workflow Management API")
public class WorkflowManagement extends AbstractXapiProjectRestController {

		@Autowired
		public WorkflowManagement(final UserManagementServiceI userManagementService, final RoleHolder roleHolder, final ContainerService containerService) {
			super(userManagementService, roleHolder);
			_containerService = containerService;
		}



		@ApiOperation(value = "Gets the log file for a given workflow" )
		@ApiResponses({@ApiResponse(code = 500, message = "Unexpected error")})
	    @XapiRequestMapping(value = "/{workflowid}/logs/{file}", method = RequestMethod.GET, produces = {MediaType.TEXT_PLAIN_VALUE})
		public ResponseEntity<String> getFile(@PathVariable("workflowid") final String workflowId, final @PathVariable("file") @ApiParam(allowableValues = "stdout, stderr")  String file) throws Exception{
			//Get the workflow
			final UserI user = getSessionUser();
			try {
				PersistentWorkflowI wrkFlow = WorkflowUtils.getUniqueWorkflow(user, workflowId);
				if (wrkFlow != null) {
					String justification = wrkFlow.getJustification();
					InputStream logStream = null;
					if (WORKFLOW_JUSTIFICATION.equals(justification)) {
						//Is a container launch - could be service or containter id
						final String _containerId = wrkFlow.getComments().trim();
						logStream = _containerService.getLogStream(_containerId, file);
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
				                    .header(HttpHeaders.CONTENT_TYPE,  MediaType.TEXT_PLAIN_VALUE)
				                    .body(byteArrayOutputStream.toString(StandardCharsets.UTF_8.name()));
				        } catch (IOException e) {
				            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
				        }

					}else {
			            return new ResponseEntity<>("Log file not found", HttpStatus.NO_CONTENT);
					}
				}else {
		            return new ResponseEntity<>("Workflow not found", HttpStatus.NO_CONTENT);
				}
			}catch(Exception e) {
	            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
			}
		}

		@XapiRequestMapping(value = "/{id}/kill", method = POST)
	    @ApiOperation(value = "Kill Process (those users who have delete permissions on associated project, can terminate)")
	    @ResponseBody
	    public String kill(final @PathVariable String workflowId) {
			//Get the workflow
			final UserI user = getSessionUser();
			String rtn = "Insufficient privelege";
			try {
				PersistentWorkflowI wrkFlow = WorkflowUtils.getUniqueWorkflow(user, workflowId);
				if (wrkFlow != null) {
					String externalId = wrkFlow.getExternalid();
					XnatProjectdata proj = XnatProjectdata.getProjectByIDorAlias(externalId, user, false);
					if (proj != null && Permissions.canEdit(user,proj)) {
						String justification = wrkFlow.getJustification();
						if (WORKFLOW_JUSTIFICATION.equals(justification)) {
							String containerId = wrkFlow.getComments();
							rtn = _containerService.kill(containerId, user);
						}
					}
				}
			}catch(Exception e) {
	            return e.getMessage();
			}
			return rtn;
		}

		@XapiRequestMapping(value = "/killrunning", method = POST)
	    @ApiOperation(value = "Kill All Running Container Processes for a list of sessions passed as query parameter accessionid=CSV values")
	    @ResponseBody
	    public String killRunning(final @RequestParam Map<String, String> allRequestParams) {
			//Get the workflow
			final UserI user = getSessionUser();
			String rtn = "";
			try {
				WorkflowUtils workflowUtils = new WorkflowUtils();
				
				String accessionIdCSV = allRequestParams.get(ACCESSION_ID);
				if (accessionIdCSV != null) {
					final String[] splitValue = StringUtils.split(accessionIdCSV, ",");
					if (splitValue != null && splitValue.length > 0) {
						for (String accessionId : splitValue) {
							final Collection<? extends PersistentWorkflowI> workFlows = workflowUtils.getOpenWorkflows(user,accessionId);
							if (workFlows != null) {
								for (PersistentWorkflowI w: workFlows) {
									String externalId = w.getExternalid();
									XnatProjectdata proj = XnatProjectdata.getProjectByIDorAlias(externalId, user, false);
									if (proj != null && Permissions.canEdit(user,proj)) {
										String justification = w.getJustification();
										if (WORKFLOW_JUSTIFICATION.equals(justification) && RUNNING.equals(w.getStatus().toUpperCase())) {
											String containerId = w.getComments();
											String killStatus = _containerService.kill(containerId, user);
											rtn+= "Session: " + accessionId + " job " + w.getPipelineName() + " terminated: " + killStatus + "\n";
										}
									}else {
										rtn +=  "Insufficient privilege to terminate workflow for " + accessionId + "\n";
									}
								}
							}
						}
					}
				}
			}catch(Exception e) {
	            return e.getMessage();
			}
			return rtn;
		}


		private static String getAttachmentDisposition(final String name, final String extension) {
	        return String.format(ATTACHMENT_DISPOSITION, name, extension);
	    }


		private final String WORKFLOW_JUSTIFICATION = "Container launch";
		private final String RUNNING 			    = "RUNNING";
		private final String ACCESSION_ID           = "accessionid";
		private final ContainerService 				_containerService;
		private static final String ATTACHMENT_DISPOSITION = "attachment; filename=\"%s.%s\"";
}
