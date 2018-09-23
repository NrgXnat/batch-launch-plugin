package org.nrg.xnat.turbine.modules.screens;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.containers.model.command.auto.CommandSummaryForContext;
import org.nrg.containers.services.CommandService;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.exceptions.InvalidSearchException;
import org.nrg.xdat.search.DisplaySearch;
import org.nrg.xdat.turbine.modules.actions.SearchA.SearchTimeoutException;
import org.nrg.xdat.turbine.modules.screens.SecureScreen;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.XFTItem;
import org.nrg.xft.exception.DBPoolException;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xft.schema.Wrappers.XMLWrapper.SAXReader;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.bulk.utils.SearchXMLBuilder;
import org.nrg.xnat.turbine.modules.actions.BulkLaunchAction;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.xml.sax.InputSource;

import com.google.common.collect.Lists;

import org.nrg.xdat.om.*;
import org.nrg.xdat.model.*;

@SuppressWarnings("unused")

public class  XDATScreen_bulk_action  extends SecureScreen {
	   static Logger logger = Logger.getLogger(XDATScreen_bulk_action.class);

	    @Override
	    protected void doBuildTemplate(RunData data, Context context) throws Exception {
		    UserI user = TurbineUtils.getUser(data);
			//context.put("xss", data.getParameters().get("xss"));
	    	if(context.get("xss")!=null){
	    		//search already configured
	    		context.put("preventRefresh", true);
	    		return;
	    	}
	    	
	    	if(TurbineUtils.HasPassedParameter("project", data) && TurbineUtils.HasPassedParameter("dataType", data)){
	    		final String project=(String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("project",data);
	    		final String dataType=(String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("dataType",data);
	    		
	    		StringBuilder sb = new StringBuilder();
	    		sb.append("<xdat:search_where method=\"AND\">");
				sb.append("<xdat:child_set method=\"OR\">");
				sb.append("<xdat:criteria override_value_formatting=\"0\">");
				sb.append("<xdat:schema_field>").append(dataType).append("/sharing/share/project</xdat:schema_field>");
				sb.append("<xdat:comparison_type>=</xdat:comparison_type>");
				sb.append("<xdat:value>").append(project).append("</xdat:value>");
				sb.append("</xdat:criteria>");
				sb.append("<xdat:criteria override_value_formatting=\"0\">");
				sb.append("<xdat:schema_field>").append(dataType).append("/PROJECT</xdat:schema_field>");
				sb.append("<xdat:comparison_type>=</xdat:comparison_type>");
				sb.append("<xdat:value>").append(project).append("</xdat:value>");
				sb.append("</xdat:criteria>");
				sb.append("</xdat:child_set>");
				sb.append("</xdat:search_where>");
	    		
	    		context.put("xss", (new SearchXMLBuilder()).execute(Lists.newArrayList(project), dataType, user, sb.toString()));
	    		context.put("preventRefresh", false);
	    	}
	    	
	}

//
//		private List<String> containerWrappersForDataType(String project, String xsiType, UserI user) throws Exception {
//			List<String> wrapperNames = new ArrayList<String>();
//			CommandService cmdService = XDAT.getContextService().getBean(CommandService.class);
//			List<CommandSummaryForContext> cmdSummary = cmdService.available(project, xsiType, user);
//			for (CommandSummaryForContext c:cmdSummary) {
//				wrapperNames.add(c.wrapperName());
//			}
//			return wrapperNames;
//		}  
//		
//		private String buildSearchXML(final String project, final String dataType, final UserI user){
//			StringBuilder sb=new StringBuilder();
//			sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
//			sb.append("<xdat:bundle ID=\"\" allow-diff-columns=\"0\" secure=\"0\" brief-description=\"Sessions\" xmlns:arc=\"http://nrg.wustl.edu/arc\" xmlns:val=\"http://nrg.wustl.edu/val\" xmlns:pipe=\"http://nrg.wustl.edu/pipe\" xmlns:wrk=\"http://nrg.wustl.edu/workflow\" xmlns:scr=\"http://nrg.wustl.edu/scr\" xmlns:xdat=\"http://nrg.wustl.edu/security\" xmlns:cat=\"http://nrg.wustl.edu/catalog\" xmlns:prov=\"http://www.nbirn.net/prov\" xmlns:xnat=\"http://nrg.wustl.edu/xnat\" xmlns:xnat_a=\"http://nrg.wustl.edu/xnat_assessments\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://nrg.wustl.edu/workflow https://imagingdb.blackthornrx.com/schemas/workflow.xsd http://nrg.wustl.edu/catalog https://imagingdb.blackthornrx.com/schemas/catalog.xsd http://nrg.wustl.edu/pipe https://imagingdb.blackthornrx.com/schemas/repository.xsd http://nrg.wustl.edu/scr https://imagingdb.blackthornrx.com/schemas/screeningAssessment.xsd http://nrg.wustl.edu/arc https://imagingdb.blackthornrx.com/schemas/project.xsd http://nrg.wustl.edu/val https://imagingdb.blackthornrx.com/schemas/protocolValidation.xsd http://nrg.wustl.edu/xnat https://imagingdb.blackthornrx.com/schemas/xnat.xsd http://nrg.wustl.edu/xnat_assessments https://imagingdb.blackthornrx.com/schemas/assessments.xsd http://www.nbirn.net/prov https://imagingdb.blackthornrx.com/schemas/birnprov.xsd http://nrg.wustl.edu/security https://imagingdb.blackthornrx.com/schemas/security.xsd\">");
//			sb.append("<xdat:root_element_name>").append(dataType).append("</xdat:root_element_name>");
//			sb.append("<xdat:search_field>");
//			sb.append("<xdat:element_name>").append(dataType).append("</xdat:element_name>");
//			sb.append("<xdat:field_ID>PROJECT</xdat:field_ID>");
//			sb.append("<xdat:sequence>-1</xdat:sequence>");
//			sb.append("<xdat:type>string</xdat:type>");
//			sb.append("<xdat:header>Project</xdat:header>");
//			sb.append("</xdat:search_field>");
//			sb.append("<xdat:search_field>");
//			sb.append("<xdat:element_name>").append(dataType).append("</xdat:element_name>");
//			sb.append("<xdat:field_ID>MR_PROJECT_IDENTIFIER=").append(project).append("</xdat:field_ID>");
//			sb.append("<xdat:sequence>0</xdat:sequence>");
//			sb.append("<xdat:type>string</xdat:type>");
//			sb.append("<xdat:header>MR ID</xdat:header>");
//			sb.append("<xdat:value>").append(project).append("</xdat:value>");
//			sb.append("</xdat:search_field>");
//			sb.append("<xdat:search_field>");
//			sb.append("<xdat:element_name>xnat:subjectData</xdat:element_name>");
//			sb.append("<xdat:field_ID>SUB_PROJECT_IDENTIFIER=").append(project).append("</xdat:field_ID>");
//			sb.append("<xdat:sequence>2</xdat:sequence>");
//			sb.append("<xdat:type>string</xdat:type>");
//			sb.append("<xdat:header>Subject</xdat:header>");
//			sb.append("<xdat:value>").append(project).append("</xdat:value>");
//			sb.append("</xdat:search_field>");
//
//            //Get a list of all pipelines/containers which have been configured for the project.
//            List<String> configuredPipelinesOrContainers = new ArrayList<String>();
//			ArcProject aProject = ArcSpecManager.GetFreshInstance().getProjectArc(project);
//    		if (aProject != null) {
//            	List<ArcProjectDescendantI> descendants = aProject.getPipelines_descendants_descendant();
//                for (ArcProjectDescendantI descendant : descendants) {
//                    ArcProjectDescendant instance = (ArcProjectDescendant) descendant;
//                    if (instance.getXsitype().equals("All Datatypes") || instance.getXsitype().equals(dataType)) {
//                        List<ArcProjectDescendantPipelineI> pipelines = instance.getPipeline();
//                        for (ArcProjectDescendantPipelineI pipeline1 : pipelines) {
//                            ArcProjectDescendantPipeline descPipeline = (ArcProjectDescendantPipeline) pipeline1;
//                            ArcPipelinedata pipeline = descPipeline.getPipelinedata();
//            				 String path = pipeline.getLocation() ;
//             				configuredPipelinesOrContainers.add(path);
//                        }
//                    }
//        		 }
//    		}
//    		
//        	//Get configured containers
//    		try {
//        		List<String> cmmds = containerWrappersForDataType(project,dataType,user);
//        		configuredPipelinesOrContainers.addAll(cmmds);
//    		}catch(Exception nsbe) {
//
//    		}
//           
//    		int sequence=100;
//            for (String pipeline:configuredPipelinesOrContainers) {
//				int lastSlash = pipeline.lastIndexOf(File.separator);
//				String header = pipeline;
//				if (lastSlash != -1) {
//					header = pipeline.substring(lastSlash+1);
//				}else {
//					lastSlash = pipeline.lastIndexOf("/");
//					if (lastSlash != -1) {
//						header = pipeline.substring(lastSlash+1);
//					}else {
//						lastSlash = pipeline.lastIndexOf("\\");
//						if (lastSlash != -1) {
//							header = pipeline.substring(lastSlash+1);
//						}
//					}
//				}
//				int lastDot = header.lastIndexOf(".");
//				if (lastDot != -1) {
//					header = header.substring(0,lastDot);
//				}
//				String pipelineEscaped = pipeline.replace(".", "_").replace("\\s+", "_");
//				String pipelineDisplay="<xdat:search_field><xdat:element_name>"+dataType+"</xdat:element_name>" +
//						"<xdat:field_ID>WRK_STATUS="+ pipelineEscaped +"</xdat:field_ID>" +
//						"<xdat:sequence>"+sequence+"</xdat:sequence>" +
//						"<xdat:type>string</xdat:type>" +
//						"</xdat:search_field>";
//						//Add the xdat field which contains the project field
//				sb.append(pipelineDisplay);
//				sequence++;
//			}
//            
//			
//			
//			sb.append("</xdat:bundle>");
//			
//			return sb.toString();
//		}
}

