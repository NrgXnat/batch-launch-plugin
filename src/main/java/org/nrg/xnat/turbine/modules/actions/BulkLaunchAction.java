package org.nrg.xnat.turbine.modules.actions;

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
import org.nrg.xdat.om.XdatStoredSearch;
import org.nrg.xdat.search.DisplaySearch;
import org.nrg.xdat.turbine.modules.actions.DisplaySearchAction;
import org.nrg.xdat.turbine.modules.actions.SearchA;
import org.nrg.xdat.turbine.modules.actions.SearchA.SearchTimeoutException;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.XFTItem;
import org.nrg.xft.exception.DBPoolException;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xft.schema.Wrappers.XMLWrapper.SAXReader;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.xml.sax.InputSource;
import org.nrg.xdat.om.*;
import org.nrg.xdat.model.*;

@SuppressWarnings("unused")


public class BulkLaunchAction extends DisplaySearchAction {
	   static Logger logger = Logger.getLogger(BulkLaunchAction.class);


	public void doPerform(RunData data, Context context)
	{
		try {
		    doPreliminaryProcessing(data,context);
		    UserI user = TurbineUtils.getUser(data);
		    String search_xml = data.getParameters().getString("search_xml");
		    search_xml = search_xml.replaceAll("%", "%25");
		    search_xml = URLDecoder.decode(search_xml, "UTF-8");
		    search_xml = StringUtils.replace(search_xml, ".close.", "/");

		    final StringReader sr = new StringReader(search_xml);
		    final InputSource is = new InputSource(sr);
		    final SAXReader reader = new SAXReader(user);
		    final XFTItem item = reader.parse(is);
		    final XdatStoredSearch search = new XdatStoredSearch(item);
		    search.setId("");
		    search.setTag("");
		    //search.setAllowedUser(XDAT.);
		    StringWriter sw = new StringWriter();
			search.toXML(sw, false);
			context.put("xss", StringEscapeUtils.escapeXml(sw.toString()));
			super.doPreliminaryProcessing(data, context);
			data.setScreenTemplate(getScreenTemplate());

			doFinalProcessing(data,context);
			//data.getParameters().add("xss", StringEscapeUtils.escapeXml(sw.toString()));
			//data.setScreen(getScreen());
		} catch (SearchTimeoutException e) {
        logger.error(e);
        data.setMessage(e.getMessage());
        data.setScreenTemplate("Index.vm");
    } catch (XFTInitException e) {
        this.error(e, data);
	} catch (ElementNotFoundException e) {
        this.error(e, data);
	} catch (DBPoolException e) {
        this.error(e, data);
	}catch (IllegalAccessException e){
        data.setMessage("The user does not have access to this data.");
        data.setScreenTemplate("Error.vm");
        data.getParameters().setString("exception", e.toString());
	}catch (InvalidSearchException e){
        data.setMessage("You specified an invalid search condition: " + e.getMessage());
        data.setScreenTemplate("Error.vm");
	} catch (Exception e) {
		e.printStackTrace();
        this.error(e, data);
	}
	}
	 public void doPreliminaryProcessing(RunData data, Context context) throws Exception{

		 //Inject the Project field into the search (even if it already exists).
	     if (((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("search_xml",data))!=null){
				 String search_xml = data.getParameters().getString("search_xml");
				 String replaced_search_xml = appendProjectSearchField(search_xml);
			     //Add the workflow columns per pipeline/container configured for the project
			     String appended_search_xml = appendWorkflowDisplaySearchFields(replaced_search_xml, data);
			     data.getParameters().remove("search_xml");
				 data.getParameters().add("search_xml", appended_search_xml);
	     }
	     //super.doPreliminaryProcessing(data, context);
	 }




	 private String appendProjectSearchField(String search_xml) {
		 String replaced_search_xml = null;
		 Pattern MY_PATTERN = Pattern.compile("<xdat:root_element_name>(.*?)</xdat:root_element_name>");
			Matcher m = MY_PATTERN.matcher(search_xml);
			if (m.find()) {
			    String rootElementName = m.group(1);
			    //Add the xdat field which contains the project field
			    String projectSearchField = "<xdat:search_field><xdat:element_name>"+ rootElementName + "</xdat:element_name><xdat:field_ID>PROJECT</xdat:field_ID><xdat:sequence>-1</xdat:sequence><xdat:type>string</xdat:type><xdat:header>Project</xdat:header></xdat:search_field>";
			    int location = search_xml.indexOf("<xdat:search_field>");
			    if (location != -1) {
				    StringBuilder builder = new StringBuilder();
				    builder.append(search_xml.substring(0, location));
				    builder.append(projectSearchField);
				    builder.append(search_xml.substring(location));
				    replaced_search_xml = builder.toString();
			    }
			}
		return replaced_search_xml;
	 }

	 private String appendWorkflowDisplaySearchFields(final String xml, RunData data) throws Exception{
         String appended_search_xml = xml;
         String search_xml = xml;
         UserI user = TurbineUtils.getUser(data);
         if (user == null)
         {
             throw new Exception("Invalid User.");
         }

         search_xml = search_xml.replaceAll("%", "%25");
         search_xml = URLDecoder.decode(search_xml, "UTF-8");
         search_xml = StringUtils.replace(search_xml, ".close.", "/");

         final StringReader sr = new StringReader(search_xml);
         final InputSource is = new InputSource(sr);
         final SAXReader reader = new SAXReader(user);
         final XFTItem item = reader.parse(is);
         final XdatStoredSearch search = new XdatStoredSearch(item);
         final DisplaySearch ds = search.getCSVDisplaySearch(user);

            if (ds==null) {
                throw new SearchTimeoutException("BuldLaunchAction Session Expired: The previously performed search has timed out.");
            }
            String rootElementName = ds.getRootElement().getFullXMLName();
            //Load search results into a table
            org.nrg.xft.XFTTable table = (org.nrg.xft.XFTTable)ds.execute(null,TurbineUtils.getUser(data).getLogin());


            //The 'session_id' value is specified as the DisplayField ID for the xnat:mrSessionData/ID field in the Display docs.
            //This value should match the value at the header of the session id column in the previous ExampleListingActionScreen implementation.
            String sessionIDHeader ="session_id";
            String projectHeader  = "Project";
            //Distinct projects
            Hashtable<String, String> distinctProjectsInSearch = new Hashtable<String, String>();
            table.resetRowCursor();
            while (table.hasMoreRows()){
                Hashtable row= table.nextRowHash();
                String project = (String)row.get(projectHeader);
                if (project == null) project = (String)row.get(projectHeader.toLowerCase());
                Object rowSessionId = row.get(sessionIDHeader);
                if (!distinctProjectsInSearch.containsKey(project)) {
                	distinctProjectsInSearch.put(project, "DUMMY");
                }
            }
            //Get a list of all pipelines/containers which have been configured for the project.
            List<String> configuredPipelinesOrContainers = new ArrayList<String>();
            Enumeration projectEnumeration = distinctProjectsInSearch.keys();
            while(projectEnumeration.hasMoreElements()) {
            	String proj = (String)projectEnumeration.nextElement();
                //Get configured pipelines for the project
            	ArcProject aProject = ArcSpecManager.GetFreshInstance().getProjectArc(proj);
        		if (aProject != null) {
                	List<ArcProjectDescendantI> descendants = aProject.getPipelines_descendants_descendant();
                    for (ArcProjectDescendantI descendant : descendants) {
                        ArcProjectDescendant instance = (ArcProjectDescendant) descendant;
                        if (instance.getXsitype().equals("All Datatypes") || instance.getXsitype().equals(rootElementName)) {
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
            projectEnumeration = distinctProjectsInSearch.keys();
            while(projectEnumeration.hasMoreElements()) {
            	String proj = (String)projectEnumeration.nextElement();
            	//Get configured containers
        		try {
            		List<String> cmmds = containerWrappersForDataType((String)proj,rootElementName,user);
            		configuredPipelinesOrContainers.addAll(cmmds);
        		}catch(NoSuchBeanDefinitionException nsbe) {

        		}
            }
           

		    StringBuilder builder = new StringBuilder();
            Pattern MY_PATTERN = Pattern.compile("<xdat:root_element_name>(.*?)</xdat:root_element_name>");
			Matcher m = MY_PATTERN.matcher(xml);
			int sequence = 100;
			if (m.find()) {
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
					String pipelineEscaped = pipeline.replace(".", "_").replace("\\s+", "_");
					String pipelineDisplay="<xdat:search_field><xdat:element_name>"+rootElementName+"</xdat:element_name>" +
							"<xdat:field_ID>WRK_STATUS="+ pipelineEscaped +"</xdat:field_ID>" +
							"<xdat:sequence>"+sequence+"</xdat:sequence>" +
							"<xdat:type>string</xdat:type>" +
							"<xdat:header>"+header+"</xdat:header>" +
							"<xdat:value>"+ pipeline.replace(".", "_").replace("\\s+", "_")+"</xdat:value>" +
							"</xdat:search_field>";
							//Add the xdat field which contains the project field
					builder.append(pipelineDisplay);
					sequence++;
				}
			}
			String wrkSearch = builder.toString();
			builder = new StringBuilder();
			int location = xml.indexOf("<xdat:search_field>");
		    if (location != -1) {
			    builder.append(xml.substring(0, location));
			    builder.append(wrkSearch);
			    builder.append(xml.substring(location));
		    }
			appended_search_xml = (sequence==100?xml:builder.toString());
		return appended_search_xml;
	 }


	public String getScreenTemplate(){
		return "XDATScreen_bulk_action.vm";
	}

	public String getScreen(){
		return "XDATScreen_bulk_action";
	}

	

	private List<String> containerWrappersForDataType(String project, String xsiType, UserI user) throws Exception {
		List<String> wrapperNames = new ArrayList<String>();
		CommandService cmdService = XDAT.getContextService().getBean(CommandService.class);
		List<CommandSummaryForContext> cmdSummary = cmdService.available(project, xsiType, user);
		for (CommandSummaryForContext c:cmdSummary) {
			wrapperNames.add(c.wrapperName());
		}
		return wrapperNames;
	}


}


