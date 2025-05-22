// Developer: Mohana Ramaratnam <mohana@radiologics.com>

package org.nrg.xnatx.plugins.batch.utils;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.turbine.util.RunData;
import org.nrg.action.ClientException;
import org.nrg.containers.model.command.auto.Command.CommandWrapper;
import org.nrg.containers.model.command.auto.Command.CommandWrapperOutput;
import org.nrg.containers.model.command.auto.CommandSummaryForContext;
import org.nrg.containers.model.orchestration.auto.Orchestration;
import org.nrg.containers.services.CommandService;
import org.nrg.containers.services.OrchestrationService;
import org.nrg.containers.services.impl.ContainerServiceImpl;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.model.ArcProjectDescendantI;
import org.nrg.xdat.model.ArcProjectDescendantPipelineI;
import org.nrg.xdat.om.*;
import org.nrg.xdat.schema.SchemaElement;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.XFTTable;
import org.nrg.xft.db.PoolDBUtils;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xft.search.SQLClause;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.restlet.data.Status;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

@Slf4j
public class SearchXMLBuilder {
    public static String getCheckedParameter(final RunData data, final String parameter) throws ClientException {
		final String encoded = (String) TurbineUtils.GetPassedParameter(parameter, data);
        if (encoded != null && PoolDBUtils.HackCheck(encoded)) {
            log.error("The user {} submitted an invalid value for parameter \"{}\": {}", XDAT.getUserDetails().getUsername(), parameter, encoded);
            throw new ClientException(Status.CLIENT_ERROR_BAD_REQUEST, "Invalid value submitted");
        }
        return encoded;
    }

	public static List<String> getItemList(final RunData data, final String parameter) throws ClientException {
        final String encoded = getCheckedParameter(data, parameter);
		return StringUtils.isBlank(encoded) ? Collections.emptyList() : Arrays.asList(encoded.split("\\s*,\\s*"));
    }

	public String execute(final List<String> projects,
                          @Nonnull final String dataType,
                          final UserI user,
                          final String whereClause,
                          String specificJob,
                          List<String> resources,
                          @Nullable List<String> scan_types) throws XFTInitException, ElementNotFoundException {

		StringBuilder sb=new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
		sb.append("<xdat:bundle ID=\"\" allow-diff-columns=\"0\" secure=\"0\" brief-description=\"PD\" xmlns:arc=\"http://nrg.wustl.edu/arc\" xmlns:val=\"http://nrg.wustl.edu/val\" xmlns:pipe=\"http://nrg.wustl.edu/pipe\" xmlns:wrk=\"http://nrg.wustl.edu/workflow\" xmlns:scr=\"http://nrg.wustl.edu/scr\" xmlns:xdat=\"http://nrg.wustl.edu/security\" xmlns:cat=\"http://nrg.wustl.edu/catalog\" xmlns:prov=\"http://www.nbirn.net/prov\" xmlns:xnat=\"http://nrg.wustl.edu/xnat\" xmlns:xnat_a=\"http://nrg.wustl.edu/xnat_assessments\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://nrg.wustl.edu/workflow ").append(XDAT.getSiteUrl()).append("/schemas/workflow.xsd http://nrg.wustl.edu/catalog ").append(XDAT.getSiteUrl()).append("/schemas/catalog.xsd http://nrg.wustl.edu/pipe ").append(XDAT.getSiteUrl()).append("/schemas/repository.xsd http://nrg.wustl.edu/scr ").append(XDAT.getSiteUrl()).append("/schemas/screeningAssessment.xsd http://nrg.wustl.edu/arc ").append(XDAT.getSiteUrl()).append("/schemas/project.xsd http://nrg.wustl.edu/val ").append(XDAT.getSiteUrl()).append("/schemas/protocolValidation.xsd http://nrg.wustl.edu/xnat ").append(XDAT.getSiteUrl()).append("/schemas/xnat.xsd http://nrg.wustl.edu/xnat_assessments ").append(XDAT.getSiteUrl()).append("/schemas/assessments.xsd http://www.nbirn.net/prov ").append(XDAT.getSiteUrl()).append("/schemas/birnprov.xsd http://nrg.wustl.edu/security ").append(XDAT.getSiteUrl()).append("/schemas/security.xsd\">");
		sb.append("<xdat:root_element_name>").append(dataType).append("</xdat:root_element_name>");

		SchemaElement se = SchemaElement.GetElement(dataType);
		if (XnatProjectdata.SCHEMA_ELEMENT_NAME.equals(dataType)) {
			sb.append("<xdat:search_field>");
			sb.append("<xdat:element_name>").append(dataType).append("</xdat:element_name>");
			sb.append("<xdat:field_ID>ID</xdat:field_ID>");
			sb.append("<xdat:sequence>0</xdat:sequence>");
			sb.append("<xdat:type>string</xdat:type>");
			sb.append("<xdat:header>Project</xdat:header>");
			sb.append("</xdat:search_field>");
			addUriField(sb, dataType);
		} else if (XnatSubjectdata.SCHEMA_ELEMENT_NAME.equals(dataType)) {
			addProjectColumn(sb, dataType);
			addSubjectColumn(sb, dataType, projects);
			addUriField(sb, dataType);
		} else if (se.instanceOf(XnatImagescandata.SCHEMA_ELEMENT_NAME)) {
			addProjectColumn(sb, dataType);
			addSubjectColumn(sb, dataType, projects);
			addScanColumns(sb, dataType, projects);
			addUriField(sb, dataType);
		} else {
			addProjectColumn(sb, dataType);
			addSubjectColumn(sb, dataType, projects);
			addExptColumn(sb, dataType, projects);
			addUriField(sb, dataType);
		}

		int sequence=100;

		if(scan_types!=null){
			for(String sType: scan_types){
				String pipelineDisplay="<xdat:search_field><xdat:element_name>"+dataType+"</xdat:element_name>" +
						"<xdat:field_ID>SCAN_TYPE_COUNT="+ sType +"</xdat:field_ID>" +
						"<xdat:sequence>"+sequence+"</xdat:sequence>" +
						"<xdat:type>integer</xdat:type>" +
						"<xdat:header>"+sType+"</xdat:header>" +
						"<xdat:value>"+sType+"</xdat:value>" +
						"</xdat:search_field>";
				sb.append(pipelineDisplay);
				sequence++;
			}
		}

		if(StringUtils.isBlank(specificJob)){
	        //Get a list of all pipelines/containers which have been configured for the project.
	        List<String> executedPipelinesOrContainers = new ArrayList<>();
	        for(String project: projects){
				ArcProject aProject = ArcSpecManager.GetFreshInstance().getProjectArc(project);
				if (aProject != null) {
					List<ArcProjectDescendantI> descendants = aProject.getPipelines_descendants_descendant();
					for (ArcProjectDescendantI descendant : descendants) {
						ArcProjectDescendant instance = (ArcProjectDescendant) descendant;
						if (instance.getXsitype().equals("All Datatypes") || instance.getXsitype().equals(dataType)) {
							List<ArcProjectDescendantPipelineI> pipelines = instance.getPipeline();
							for (ArcProjectDescendantPipelineI pipeline1 : pipelines) {
								ArcProjectDescendantPipeline descPipeline = (ArcProjectDescendantPipeline) pipeline1;
								ArcPipelinedata pipeline = descPipeline.getPipelinedata();
								String path = pipeline.getLocation();
								executedPipelinesOrContainers.add(path);
							}
						}
					}
				}
			}

	    	//Get configured containers
			try {
	    		executedPipelinesOrContainers.addAll(getContainersExecuted(projects,dataType,user));
			} catch (Exception e) {
				log.error("Unable to determine available containers for {}", projects, e);
			}

	        for (String pipeline:executedPipelinesOrContainers) {
				//replacing with 2 _ or 3 _ decreases the odds of running into collisions, but doesn't eliminate it entirely.  We could probably do better.
				String pipelineEscaped = pipeline.replace(".", "__").replaceAll("\\s+", "___");
				sequence = addPipeline(pipelineEscaped, dataType, sb, sequence);
			}
		} else {
			//user is working on one specific pipeline
			sequence = addPipeline(specificJob, dataType, sb, sequence);

			String pipelineDisplay="<xdat:search_field><xdat:element_name>"+dataType+"</xdat:element_name>" +
					"<xdat:field_ID>WRK_STATUS_LAUNCH="+ specificJob +"</xdat:field_ID>" +
					"<xdat:sequence>"+sequence+"</xdat:sequence>" +
					"<xdat:type>date</xdat:type>" +
					"<xdat:header>Launched</xdat:header>" +
					"<xdat:value>"+specificJob+"</xdat:value>" +
					"</xdat:search_field>";
			sb.append(pipelineDisplay);
			sequence++;

			pipelineDisplay="<xdat:search_field><xdat:element_name>"+dataType+"</xdat:element_name>" +
					"<xdat:field_ID>WRK_STATUS_LASTMOD="+ specificJob +"</xdat:field_ID>" +
					"<xdat:sequence>"+sequence+"</xdat:sequence>" +
					"<xdat:type>date</xdat:type>" +
					"<xdat:header>Last Mod</xdat:header>" +
					"<xdat:value>"+specificJob+"</xdat:value>" +
					"</xdat:search_field>";
			sb.append(pipelineDisplay);
			sequence++;

			pipelineDisplay="<xdat:search_field><xdat:element_name>"+dataType+"</xdat:element_name>" +
					"<xdat:field_ID>WRK_STATUS_NUMRUNS="+ specificJob +"</xdat:field_ID>" +
					"<xdat:sequence>"+sequence+"</xdat:sequence>" +
					"<xdat:type>integer</xdat:type>" +
					"<xdat:header>Runs</xdat:header>" +
					"<xdat:value>"+specificJob+"</xdat:value>" +
					"</xdat:search_field>";
			sb.append(pipelineDisplay);
			sequence++;

			for(String resource: getOutputResourceLabelsForContainer(specificJob,dataType,user)){
				pipelineDisplay="<xdat:search_field><xdat:element_name>"+dataType+"</xdat:element_name>" +
						"<xdat:field_ID>RES_FILE_COUNT="+ resource +"</xdat:field_ID>" +
						"<xdat:sequence>"+sequence+"</xdat:sequence>" +
						"<xdat:type>string</xdat:type>" +
						"<xdat:header>"+resource+"</xdat:header>" +
						"<xdat:value>"+resource+"</xdat:value>" +
						"</xdat:search_field>";
				sb.append(pipelineDisplay);
				sequence++;
			}
		}

		if (resources!=null) {
			for (String resource: resources) {
				String pipelineDisplay="<xdat:search_field><xdat:element_name>"+dataType+"</xdat:element_name>" +
						"<xdat:field_ID>RES_FILE_COUNT="+ resource +"</xdat:field_ID>" +
						"<xdat:sequence>"+sequence+"</xdat:sequence>" +
						"<xdat:type>string</xdat:type>" +
						"<xdat:header>"+resource+"</xdat:header>" +
						"<xdat:value>"+resource+"</xdat:value>" +
						"</xdat:search_field>";
				sb.append(pipelineDisplay);
				sequence++;
			}
		}

		sb.append(whereClause);

		sb.append("</xdat:bundle>");

		return sb.toString();
	}

	private List<String> getOutputResourceLabelsForContainer(String containerName, String xsiType, UserI user){
		final List<String> resources= Lists.newArrayList();
		if(StringUtils.isNotEmpty(containerName)){
			final CommandService cmdService = XDAT.getContextService().getBean(CommandService.class);
			try {
				final List<CommandSummaryForContext> cmdSummary = cmdService.available(xsiType, user);
				for (final CommandSummaryForContext c:cmdSummary) {
					if(StringUtils.equals(containerName, c.wrapperName())){
						final CommandWrapper cmd=cmdService.retrieveWrapper(c.wrapperId());
						for(final CommandWrapperOutput out:cmd.outputHandlers()){
							resources.add(out.label());
						}
					}
				}
			} catch (ElementNotFoundException e) {
				//ignore
			}
		}

		return resources;
	}

	private Set<String> getContainersExecuted(final List<String> projects,
											 @Nonnull final String dataType,
											 final UserI user) throws Exception {
		Set<String> wrapperNames = new LinkedHashSet<>();
		if (projects.isEmpty()) {
			return wrapperNames;
		}
		SQLClause.ParamValue[] sqlValues = new SQLClause.ParamValue[projects.size() + 1];
		StringBuffer project_ids_holder = new StringBuffer("(");
		int counter = 0;
		for (String projectId : projects) {
			sqlValues[counter] = new SQLClause.ParamValue(projectId, -1);
			if (counter++>0) {
				project_ids_holder.append(",");
			}
			project_ids_holder.append("?");
		}
		project_ids_holder.append(")");
		sqlValues[projects.size()] = new SQLClause.ParamValue(dataType, -1);
		PoolDBUtils con = new PoolDBUtils();
		try {
			final String colName = "pipeline_name";
			String query = "SELECT distinct " + colName +  "  FROM wrk_workflowdata where externalid in " + project_ids_holder +  " and data_type = ? and justification='" + ContainerServiceImpl.containerLaunchJustification + "';";

			XFTTable t = con.executeSelectPS(query, sqlValues);
			while (t.hasMoreRows()) {
				t.nextRow();
				wrapperNames.add(t.getCellValue(colName).toString());
			}
		} catch (Exception e) {
			log.error("Could not get container names", e);
		} finally {
			con.closeConnection();
		}
		return wrapperNames;
	}

	private int addPipeline(String pipelineEscaped, String dataType, StringBuilder sb, int sequence) {
		String pipelineDisplay="<xdat:search_field><xdat:element_name>"+dataType+"</xdat:element_name>" +
				"<xdat:field_ID>WRK_STATUS="+ pipelineEscaped +"</xdat:field_ID>" +
				"<xdat:sequence>"+sequence+"</xdat:sequence>" +
				"<xdat:type>string</xdat:type>" +
				"<xdat:header>"+pipelineEscaped+"</xdat:header>" +
				"<xdat:value>"+pipelineEscaped+"</xdat:value>" +
				"</xdat:search_field>";
		//Add the xdat field which contains the project field
		sb.append(pipelineDisplay);
		sequence++;

		pipelineDisplay="<xdat:search_field><xdat:element_name>"+dataType+"</xdat:element_name>" +
				"<xdat:field_ID>WRK_STATUS_CID="+ pipelineEscaped +"</xdat:field_ID>" +
				"<xdat:sequence>"+sequence+"</xdat:sequence>" +
				"<xdat:type>string</xdat:type>" +
				"<xdat:header>Container ID</xdat:header>" +
				"<xdat:value>"+pipelineEscaped+"</xdat:value>" +
				"</xdat:search_field>";
		sb.append(pipelineDisplay);
		sequence++;
		return sequence;
	}
	
	private void addProjectColumn(StringBuilder sb, String dataType) {
		sb.append("<xdat:search_field>");
		sb.append("<xdat:element_name>").append(dataType).append("</xdat:element_name>");
		sb.append("<xdat:field_ID>PROJECT</xdat:field_ID>");
		sb.append("<xdat:sequence>0</xdat:sequence>");
		sb.append("<xdat:type>string</xdat:type>");
		sb.append("<xdat:header>Project</xdat:header>");
		sb.append("</xdat:search_field>");
	}

	private void addSubjectColumn(StringBuilder sb, String dataType, List<String> projects) {
		addLabelField(sb, XnatSubjectdata.SCHEMA_ELEMENT_NAME, projects, XnatSubjectdata.SCHEMA_ELEMENT_NAME.equals(dataType));
	}

	private void addExptColumn(StringBuilder sb, String dataType, List<String> projects) {
		addLabelField(sb, dataType, projects, true);
		sb.append("<xdat:search_field>");
		sb.append("<xdat:element_name>").append(dataType).append("</xdat:element_name>");
		sb.append("<xdat:field_ID>VISIT</xdat:field_ID>");
		sb.append("<xdat:sequence>4</xdat:sequence>");
		sb.append("<xdat:type>string</xdat:type>");
		sb.append("<xdat:header>Visit</xdat:header>");
		sb.append("</xdat:search_field>");
	}

	private void addScanColumns(StringBuilder sb, String dataType, List<String> projects) {
		// Add session label
		addLabelField(sb, dataType.replace("Scan", "Session"), projects, true);
		// add scan id
		sb.append("<xdat:search_field>");
		sb.append("<xdat:element_name>").append(dataType).append("</xdat:element_name>");
		sb.append("<xdat:field_ID>ID</xdat:field_ID>");
		sb.append("<xdat:sequence>4</xdat:sequence>");
		sb.append("<xdat:type>string</xdat:type>");
		sb.append("<xdat:header>").append("Scan").append("</xdat:header>");
		sb.append("</xdat:search_field>");
	}

	private void addLabelField(StringBuilder sb, String dataType, List<String> projects, boolean baseDataTypeMatches) {
		String projIdField = dataType.replaceFirst(":", "_").toUpperCase() + "_PROJECT_IDENTIFIER";
		String header = "Experiment";
		String seq = "3";
		if (XnatSubjectdata.SCHEMA_ELEMENT_NAME.equals(dataType)) {
			header = "Subject";
			seq = "2";
			projIdField = "SUB_PROJECT_IDENTIFIER";
		} else if (XnatMrsessiondata.SCHEMA_ELEMENT_NAME.equals(dataType)) {
			projIdField = "MR_PROJECT_IDENTIFIER";
		}

		if (projects.size()>1 || projects.size()==0) {
			//if more then 1 project is in scope, then show default label
			sb.append("<xdat:search_field>");
			sb.append("<xdat:element_name>").append(dataType).append("</xdat:element_name>");
			if (baseDataTypeMatches) {
				sb.append("<xdat:field_ID>LABEL</xdat:field_ID>");
			} else {
				sb.append("<xdat:field_ID>SUBJECT_LABEL</xdat:field_ID>");
			}
			sb.append("<xdat:sequence>").append(seq).append("</xdat:sequence>");
			sb.append("<xdat:type>string</xdat:type>");
			sb.append("<xdat:header>").append(header).append("</xdat:header>");
			sb.append("</xdat:search_field>");
		} else {
			//if only 1 project is in scope, show that project's label
			sb.append("<xdat:search_field>");
			sb.append("<xdat:element_name>").append(dataType).append("</xdat:element_name>");
			sb.append("<xdat:field_ID>").append(projIdField).append("=").append(projects.get(0)).append("</xdat:field_ID>");
			sb.append("<xdat:sequence>").append(seq).append("</xdat:sequence>");
			sb.append("<xdat:type>string</xdat:type>");
			sb.append("<xdat:header>").append(header).append("</xdat:header>");
			sb.append("<xdat:value>").append(projects.get(0)).append("</xdat:value>");
			sb.append("</xdat:search_field>");
		}
	}

	private void addUriField(StringBuilder sb, String dataType) {
		sb.append("<xdat:search_field>");
		sb.append("<xdat:element_name>").append(dataType).append("</xdat:element_name>");
		sb.append("<xdat:field_ID>URI</xdat:field_ID>");
		sb.append("<xdat:sequence>1</xdat:sequence>");
		sb.append("<xdat:type>string</xdat:type>");
		sb.append("<xdat:header>uri</xdat:header>");
		sb.append("</xdat:search_field>");
	}
}
