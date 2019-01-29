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
import org.nrg.xft.db.PoolDBUtils;
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
	    		
				String job = (String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("job",data);
	            if (org.apache.commons.lang3.StringUtils.isNotEmpty(job) && PoolDBUtils.HackCheck(job)) {
	            	throw new Exception("Invalid value submitted.");
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
	            
	    		context.put("xss", (new SearchXMLBuilder()).execute(Lists.newArrayList(project), dataType, user, sb.toString(),job,resourceList));
	    		context.put("preventRefresh", false);
	    	}
	    	
	}
}

