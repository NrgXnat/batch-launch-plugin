package org.nrg.xnat.turbine.modules.screens;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.containers.model.command.auto.CommandSummaryForContext;
import org.nrg.containers.services.CommandService;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.search.DisplaySearch;
import org.nrg.xdat.turbine.modules.screens.SecureScreen;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.nrg.xdat.om.*;
import org.nrg.xdat.model.*;

@SuppressWarnings("unused")

public class  XDATScreen_bulk_action  extends SecureScreen {

	    @Override
	    protected void doBuildTemplate(RunData data, Context context) throws Exception {
	    	System.out.println("XDATScreen_bulk_action called");
	    	System.out.println("XSS:");
	    	System.out.println(context.get("xss"));
	    	

	    }
}
/*	@Override
    protected void doBuildTemplate(RunData data, Context context) throws Exception {
        
        //retrieve passed search object
        DisplaySearch search = TurbineUtils.getSearch(data);
        search.setPagingOn(false);
        String rootElementName = search.getRootElement().getFullXMLName();
        //Load search results into a table
        org.nrg.xft.XFTTable table = (org.nrg.xft.XFTTable)search.execute(null,TurbineUtils.getUser(data).getLogin());
        search.setPagingOn(true);
        
        UserI user = TurbineUtils.getUser(data);
        if (user == null)
        {
            throw new Exception("Invalid User.");
        }

        
        //The 'session_id' value is specified as the DisplayField ID for the xnat:mrSessionData/ID field in the Display docs.
        //This value should match the value at the header of the session id column in the previous ExampleListingActionScreen implementation.
        String sessionIDHeader ="session_id";
        String projectHeader  = "Project";
        Hashtable<Object, List<Object>> idProjectHash = new Hashtable<Object, List<Object>>();
        //Distinct projects
        Hashtable<String, String> distinctProjectsIds = new Hashtable<String, String>();
        //Build a comma-delimited list from the passed search table to use in our WHERE clause.
        StringBuffer session_ids = new StringBuffer("(");
        int counter = 0;
        table.resetRowCursor();
        while (table.hasMoreRows()){
            Hashtable row= table.nextRowHash();
            Object project = row.get(projectHeader);
            Object rowSessionId = row.get(sessionIDHeader);
            if (!idProjectHash.containsKey(project)) {
            	idProjectHash.put(project, new ArrayList<Object>());
            }
            if (counter++>0)
            {
                session_ids.append(",");
            }
            session_ids.append( "'" + rowSessionId  + "'");
            idProjectHash.get(project).add(rowSessionId);
        }
        
        session_ids.append(")");
        
        List<Object> distinctProjectsInSearch = new ArrayList<Object>();
        Enumeration projectEnumeration = idProjectHash.keys();
        while(projectEnumeration.hasMoreElements()) {
        	distinctProjectsInSearch.add(projectEnumeration.nextElement());
        }
        //Get a list of all pipelines/containers which have been configured for the project.
        List<String> configuredPipelinesOrContainers = new ArrayList<String>();

        for (Object proj:distinctProjectsInSearch) {
            //Get configured pipelines
        	ArcProject aProject = ArcSpecManager.GetFreshInstance().getProjectArc((String)proj);
    		if (aProject != null) {
    			List<ArcProjectPipelineI> projectPipelines = aProject.getPipelines_pipeline();
    			if (projectPipelines == null || projectPipelines.size() <1) continue;
    	        for (ArcProjectPipelineI projectPipeline : projectPipelines) {
    	            ArcProjectPipeline instance = (ArcProjectPipeline) projectPipeline;
    	            ArcPipelinedata pipeline = instance.getPipelinedata();
    				String path = pipeline.getLocation() ;
    				int slashIndex = path.lastIndexOf(File.separator);
    				if (slashIndex != -1) {
    					path.substring(slashIndex+1);
    				}
    				configuredPipelinesOrContainers.add(path);
    	        }
    		}
            //Get configured containers
    		CommandService cmdService = XDAT.getContextService().getBean(CommandService.class);
    		List<CommandSummaryForContext> availableCommands = cmdService.available((String)proj, rootElementName, user);
    		List<String> cmmds = new ArrayList<String>();
    		for (CommandSummaryForContext cmdSummary: availableCommands) {
    			cmmds.add(cmdSummary.commandLabel());
    		}
    		configuredPipelinesOrContainers.addAll(cmmds);
        }
        String query = getWorkflowSearchQuery(rootElementName, session_ids.toString(), configuredPipelinesOrContainers);
        
        org.nrg.xft.XFTTable workFlowTable = org.nrg.xft.XFTTable.Execute(query,null,user.getLogin());    	
        context.put("workFlowTable", workFlowTable);
	}
   
	private String getWorkflowSearchQuery(String rootElementName, String sessionIdsAsList, List<String> configuredPipelinesOrContainers) {
	 String subQuery = "join xnat_experimentdata e on e.id = w.id ";	
	 if (rootElementName.equals("xnat:subjectData")) {
		 subQuery = "join xnat_subjectdata e on e.id = w.id ";
	 }
	 String query =	"SELECT * FROM crosstab(" +
				       "$$select w.id::TEXT as session_id, w.externalid::TEXT as project, e.label::TEXT  w.pipeline_name::text , w.status::TEXT from wrk_workflowdata w " + 
				       "  inner join (select id, pipeline_name, max(launch_time) as latestDate from wrk_workflowdata"+
				            "group by id, pipeline_name) wm "+ 
				        " on w.id = wm.id and w.launch_time = wm.latestDate" +
				         subQuery +    
				        "where w.id in "+sessionIdsAsList + "order by session_id,pipeline_name;" +
				        "$$,$$select distinct pipeline_name from wrk_workflowdata$$" +
				     ") AS t(session_id text, project text, label text, " ;
	 
     //All these configuredPipelinesOrContainers would result in one column in the display
     int numberOfConfiguredPipelinesOrContainers = configuredPipelinesOrContainers.size();
     for (String pName:configuredPipelinesOrContainers) {
    	 query += pName + " TEXT, ";
     }
     if (query.endsWith(",")) query += query.substring(0, query.length());
     query += ")";
     return query;
	}

} */




/*public class  XDATScreen_bulk_action  extends SecureScreen {
	
    @Override
    protected void doBuildTemplate(RunData data, Context context) throws Exception {
        
        //retrieve passed search object
        DisplaySearch search = TurbineUtils.getSearch(data);
        search.setPagingOn(false);
        //Load search results into a table
        
        org.nrg.xft.XFTTable table = (org.nrg.xft.XFTTable)search.execute(null,TurbineUtils.getUser(data).getLogin());
        search.setPagingOn(true);
        
        UserI user = TurbineUtils.getUser(data);
        if (user == null)
        {
            throw new Exception("Invalid User.");
        }
        

        context.put("table", table);
    }

}
*/ 