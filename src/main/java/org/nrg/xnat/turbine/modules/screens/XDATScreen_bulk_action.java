package org.nrg.xnat.turbine.modules.screens;

import org.apache.log4j.Logger;
import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.xdat.turbine.modules.screens.SecureScreen;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.db.PoolDBUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.bulk.utils.SearchXMLBuilder;

import com.google.common.collect.Lists;

import java.util.Calendar;

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
	    		
				String job = (String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("job",data);
	            if (org.apache.commons.lang3.StringUtils.isNotEmpty(job) && PoolDBUtils.HackCheck(job)) {
	            	throw new Exception("Invalid value submitted.");
	            }else if(org.apache.commons.lang3.StringUtils.isNotEmpty(job)){
	            	context.put("job", job);
	            }
	            
	            
	            String resources = (String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("resources",data);
	            if (org.apache.commons.lang3.StringUtils.isNotEmpty(resources) && PoolDBUtils.HackCheck(resources)) {
	            	throw new Exception("Invalid value submitted.");
	            }
	            
	            java.util.List<String> resourceList=null;
	            if(org.apache.commons.lang3.StringUtils.isNotEmpty(resources)){
	            	if(resources.indexOf(",")>0){
	            		resourceList=java.util.Arrays.asList(resources.split(","));
	            	}else{
	            		resourceList=Lists.newArrayList(resources);
	            	}
	            }
	            
	            String scans = (String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("scan_types",data);
	            if (org.apache.commons.lang3.StringUtils.isNotEmpty(scans) && PoolDBUtils.HackCheck(scans)) {
	            	throw new Exception("Invalid value submitted.");
	            }
	            
	            java.util.List<String> typesList=null;
	            if(org.apache.commons.lang3.StringUtils.isNotEmpty(scans)){
	            	if(scans.indexOf(",")>0){
	            		typesList=java.util.Arrays.asList(scans.split(","));
	            	}else{
	            		typesList=Lists.newArrayList(scans);
	            	}
	            }
	            
	            
	    		context.put("xss", (new SearchXMLBuilder()).execute(Lists.newArrayList(project), dataType, user, sb.toString(),job,resourceList,typesList));
	    		context.put("preventRefresh", false);
				context.put("timezoneOffset", Calendar.getInstance().getTimeZone().getOffset(Calendar.getInstance().getTimeInMillis()));
	    	}
	    	
	}
}

