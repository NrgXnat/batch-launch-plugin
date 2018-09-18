package org.nrg.xnat.turbine.modules.screens;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
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
			//context.put("xss", data.getParameters().get("xss"));
			
	    }

	    
}

