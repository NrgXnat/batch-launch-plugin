package org.nrg.xnat.turbine.modules.screens;

import java.util.Enumeration;
import java.util.Hashtable;

import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.xdat.search.DisplaySearch;
import org.nrg.xdat.turbine.modules.screens.SecureScreen;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.security.UserI;

@SuppressWarnings("unused")
public class  XDATScreen_bulk_action  extends SecureScreen {
	
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
 