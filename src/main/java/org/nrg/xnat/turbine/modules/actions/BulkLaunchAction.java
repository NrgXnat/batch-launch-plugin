package org.nrg.xnat.turbine.modules.actions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.xdat.turbine.modules.actions.DisplaySearchAction;


@SuppressWarnings("unused")

public class BulkLaunchAction extends DisplaySearchAction {
	
	 public void doPreliminaryProcessing(RunData data, Context context) throws Exception{
		 //Inject the Project field into the search (even if it already exists). 
	     if (((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("search_xml",data))!=null){
				 String search_xml = data.getParameters().getString("search_xml");
				 String replaced_search_xml = appendProjectSearchField(search_xml);
                 data.getParameters().remove("search_xml");
				 data.getParameters().add("search_xml", replaced_search_xml);
         }
	     super.doPreliminaryProcessing(data, context);
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
    
	public String getScreenTemplate(RunData data){
		return "XDATScreen_bulk_action.vm";
	}

	
}


