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
import org.nrg.xft.db.PoolDBUtils;
import org.nrg.xft.exception.DBPoolException;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xft.schema.Wrappers.XMLWrapper.SAXReader;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.bulk.utils.SearchXMLBuilder;
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
		   // doPreliminaryProcessing(data,context);
		    final UserI user = TurbineUtils.getUser(data);
		    String search_xml = data.getParameters().getString("search_xml");
		    if(search_xml==null){
		    	data.setMessage("Your search result has expired.  Please resubmit your query. ");
		        data.setScreenTemplate("Error.vm");
		        return;
		    }
		    search_xml = search_xml.replaceAll("%", "%25");
		    search_xml = URLDecoder.decode(search_xml, "UTF-8");
		    search_xml = StringUtils.replace(search_xml, ".close.", "/");

		    final int startWhere=search_xml.indexOf("<xdat:search_where");
		    final int endWhere=search_xml.indexOf("</xdat:bundle") -1;
		    String whereClause;
		    if(startWhere==-1){
		    	whereClause="";
		    }else{
		    	whereClause=search_xml.substring(startWhere, endWhere);
		    }
		    
		    
			context.put("xss",this.buildNewSearchXML(search_xml, user, whereClause,data));
			super.doPreliminaryProcessing(data, context);
			data.setScreenTemplate(getScreenTemplate());

			doFinalProcessing(data,context);
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

	 private String buildNewSearchXML(final String search_xml, UserI user, final String whereClause, final RunData data) throws Exception{;
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
            org.nrg.xft.XFTTable table = (org.nrg.xft.XFTTable)ds.execute(null,user.getLogin());


            //The 'session_id' value is specified as the DisplayField ID for the xnat:mrSessionData/ID field in the Display docs.
            //This value should match the value at the header of the session id column in the previous ExampleListingActionScreen implementation.
            String sessionIDHeader ="session_id";
            String projectHeader  = "Project";
            //Distinct projects
            List<String> distinctProjectsInSearch = new ArrayList<String>();
            table.resetRowCursor();
            while (table.hasMoreRows()){
                Hashtable<?, ?> row= table.nextRowHash();
                String project = (String)row.get(projectHeader);
                if (project == null) project = (String)row.get(projectHeader.toLowerCase());
                if (!distinctProjectsInSearch.contains(project)) {
                	distinctProjectsInSearch.add(project);
                }
            }
            
            String job = data.getParameters().getString("job");
            if (job!=null && PoolDBUtils.HackCheck(job)) {
            	throw new Exception("Invalid value submitted.");
            }
            
            String resources = data.getParameters().getString("resources");
            if (resources!=null && PoolDBUtils.HackCheck(resources)) {
            	throw new Exception("Invalid value submitted.");
            }
            
            String[] resourceArray=null;
            if(org.apache.commons.lang3.StringUtils.isNotEmpty(resources)){
            	if(resources.contains(",")){
            		resourceArray=resources.split(",");
            	}
            }
            
            return (new SearchXMLBuilder()).execute(distinctProjectsInSearch, rootElementName, user, whereClause,job,resourceArray==null?null:java.util.Arrays.asList(resourceArray));
	 }



	public String getScreenTemplate(){
		return "XDATScreen_bulk_action.vm";
	}

	public String getScreen(){
		return "XDATScreen_bulk_action";
	}

	

}


