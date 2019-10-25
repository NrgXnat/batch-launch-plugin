// Copyright 2019 Radiologics, Inc
// Developer: Mohana Ramaratnam <mohana@radiologics.com>

package org.nrg.xnat.bulk.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.nrg.containers.model.command.auto.Command.CommandOutput;
import org.nrg.containers.model.command.auto.Command.CommandWrapper;
import org.nrg.containers.model.command.auto.Command.CommandWrapperOutput;
import org.nrg.containers.model.command.auto.CommandSummaryForContext;
import org.nrg.containers.services.CommandService;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.model.ArcProjectDescendantI;
import org.nrg.xdat.model.ArcProjectDescendantPipelineI;
import org.nrg.xdat.om.*;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.turbine.utils.ArcSpecManager;

import com.google.common.collect.Lists;

public class SearchXMLBuilder {
	public String execute(final List<String> projects,
						  final String dataType,
						  final UserI user,
						  final String whereClause,
						  String specificJob,
						  List<String> resources,
						  List<String> scan_types){

		String projIdField;
		if (XnatMrsessiondata.SCHEMA_ELEMENT_NAME.equals(dataType)) {
			projIdField = "MR_PROJECT_IDENTIFIER";
		} else if (dataType != null) {
			projIdField = dataType.replaceFirst(":", "_").toUpperCase() + "_PROJECT_IDENTIFIER";
		} else {
			projIdField = "PROJECT_IDENTIFIER";
		}

		StringBuilder sb=new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
		sb.append("<xdat:bundle ID=\"\" allow-diff-columns=\"0\" secure=\"0\" brief-description=\"Sessions\" xmlns:arc=\"http://nrg.wustl.edu/arc\" xmlns:val=\"http://nrg.wustl.edu/val\" xmlns:pipe=\"http://nrg.wustl.edu/pipe\" xmlns:wrk=\"http://nrg.wustl.edu/workflow\" xmlns:scr=\"http://nrg.wustl.edu/scr\" xmlns:xdat=\"http://nrg.wustl.edu/security\" xmlns:cat=\"http://nrg.wustl.edu/catalog\" xmlns:prov=\"http://www.nbirn.net/prov\" xmlns:xnat=\"http://nrg.wustl.edu/xnat\" xmlns:xnat_a=\"http://nrg.wustl.edu/xnat_assessments\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://nrg.wustl.edu/workflow " + XDAT.getSiteUrl() + "/schemas/workflow.xsd http://nrg.wustl.edu/catalog " + XDAT.getSiteUrl() + "/schemas/catalog.xsd http://nrg.wustl.edu/pipe " + XDAT.getSiteUrl() + "/schemas/repository.xsd http://nrg.wustl.edu/scr " + XDAT.getSiteUrl() + "/schemas/screeningAssessment.xsd http://nrg.wustl.edu/arc " + XDAT.getSiteUrl() + "/schemas/project.xsd http://nrg.wustl.edu/val " + XDAT.getSiteUrl() + "/schemas/protocolValidation.xsd http://nrg.wustl.edu/xnat " + XDAT.getSiteUrl() + "/schemas/xnat.xsd http://nrg.wustl.edu/xnat_assessments " + XDAT.getSiteUrl() + "/schemas/assessments.xsd http://www.nbirn.net/prov " + XDAT.getSiteUrl() + "/schemas/birnprov.xsd http://nrg.wustl.edu/security " + XDAT.getSiteUrl() + "/schemas/security.xsd\">");
		sb.append("<xdat:root_element_name>").append(dataType).append("</xdat:root_element_name>");
		sb.append("<xdat:search_field>");
		sb.append("<xdat:element_name>").append(dataType).append("</xdat:element_name>");
		sb.append("<xdat:field_ID>PROJECT</xdat:field_ID>");
		sb.append("<xdat:sequence>-1</xdat:sequence>");
		sb.append("<xdat:type>string</xdat:type>");
		sb.append("<xdat:header>Project</xdat:header>");
		sb.append("</xdat:search_field>");
		if(projects.size()>1){
			//if more then 1 project is in scope, then show default project label
			sb.append("<xdat:search_field>");
			sb.append("<xdat:element_name>").append(dataType).append("</xdat:element_name>");
			sb.append("<xdat:field_ID>LABEL</xdat:field_ID>");
			sb.append("<xdat:sequence>0</xdat:sequence>");
			sb.append("<xdat:type>string</xdat:type>");
			sb.append("<xdat:header>Session</xdat:header>");
			sb.append("</xdat:search_field>");
			sb.append("<xdat:search_field>");
			sb.append("<xdat:element_name>xnat:subjectData</xdat:element_name>");
			sb.append("<xdat:field_ID>SUBJECT_LABEL</xdat:field_ID>");
			sb.append("<xdat:sequence>2</xdat:sequence>");
			sb.append("<xdat:type>string</xdat:type>");
			sb.append("<xdat:header>Subject</xdat:header>");
			sb.append("</xdat:search_field>");
		}else{
			//if only 1 project is in scope, show that project's
			sb.append("<xdat:search_field>");
			sb.append("<xdat:element_name>").append(dataType).append("</xdat:element_name>");
			sb.append("<xdat:field_ID>").append(projIdField).append("=").append(projects.get(0)).append("</xdat:field_ID>");
			sb.append("<xdat:sequence>0</xdat:sequence>");
			sb.append("<xdat:type>string</xdat:type>");
			sb.append("<xdat:header>Session</xdat:header>");
			sb.append("<xdat:value>").append(projects.get(0)).append("</xdat:value>");
			sb.append("</xdat:search_field>");
			sb.append("<xdat:search_field>");
			sb.append("<xdat:element_name>xnat:subjectData</xdat:element_name>");
			sb.append("<xdat:field_ID>SUB_PROJECT_IDENTIFIER=").append(projects.get(0)).append("</xdat:field_ID>");
			sb.append("<xdat:sequence>2</xdat:sequence>");
			sb.append("<xdat:type>string</xdat:type>");
			sb.append("<xdat:header>Subject</xdat:header>");
			sb.append("<xdat:value>").append(projects.get(0)).append("</xdat:value>");
			sb.append("</xdat:search_field>");
		}
		sb.append("<xdat:search_field>");
		sb.append("<xdat:element_name>").append(dataType).append("</xdat:element_name>");
		sb.append("<xdat:field_ID>VISIT</xdat:field_ID>");
		sb.append("<xdat:sequence>3</xdat:sequence>");
		sb.append("<xdat:type>string</xdat:type>");
		sb.append("<xdat:header>Visit</xdat:header>");
		sb.append("</xdat:search_field>");
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
	        List<String> configuredPipelinesOrContainers = new ArrayList<String>();
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
		        				 String path = pipeline.getLocation() ;
		         				configuredPipelinesOrContainers.add(path);
		                    }
		                }
		    		 }
				}
	        }

	    	//Get configured containers
			try {
	    		List<String> cmmds = containerWrappersForDataType(projects,dataType,user);
	    		configuredPipelinesOrContainers.addAll(cmmds);
			}catch(Exception nsbe) {

			}

	        for (String pipeline:configuredPipelinesOrContainers) {
				int lastSlash = pipeline.lastIndexOf(File.separator);
				String header = pipeline;
				if (lastSlash != -1) {
					header = pipeline.substring(lastSlash+1);
				}else {
					lastSlash = pipeline.lastIndexOf("/");
					if (lastSlash != -1) {
						header = pipeline.substring(lastSlash+1);
					}else {
						lastSlash = pipeline.lastIndexOf("\\");
						if (lastSlash != -1) {
							header = pipeline.substring(lastSlash+1);
						}
					}
				}
				int lastDot = header.lastIndexOf(".");
				if (lastDot != -1) {
					header = header.substring(0,lastDot);
				}

				String pipelineEscaped = pipeline.replace(".", "_").replaceAll("\\s+", "_");
				sequence = addPipeline(pipelineEscaped, dataType, sb, sequence);
			}
		}else{
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

			for(String resource:getOuputResourceLabelsForContainer(specificJob,dataType,user)){
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

		if(resources!=null){
			for(String resource: resources){
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

	private List<String> getOuputResourceLabelsForContainer(String containerName, String xsiType,UserI user){
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

	private List<String> containerWrappersForDataType(List<String> projects, String xsiType, UserI user) throws Exception {
		List<String> wrapperNames = new ArrayList<String>();
		CommandService cmdService = XDAT.getContextService().getBean(CommandService.class);
		for(String project: projects){
			List<CommandSummaryForContext> cmdSummary = cmdService.available(project, xsiType, user);
			for (CommandSummaryForContext c:cmdSummary) {
				if(!wrapperNames.contains(c.wrapperName())){
					wrapperNames.add(c.wrapperName());
				}
			}
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
}