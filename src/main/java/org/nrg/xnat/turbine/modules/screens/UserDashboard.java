// Copyright 2019 Radiologics, Inc
// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnat.turbine.modules.screens;

import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.xdat.turbine.modules.screens.SecureScreen;
import org.nrg.xdat.turbine.utils.TurbineUtils;

public class UserDashboard extends SecureScreen {
    /**
     * {@inheritDoc}
     */
    @Override
    protected void doBuildTemplate(RunData data, Context context) throws Exception {
    }


    protected void preserveVariables(RunData data, Context context) {
        super.preserveVariables(data,context);

        if (data.getParameters().containsKey("allowSort")) {
            context.put("allowSort", TurbineUtils.escapeParam(((String) TurbineUtils.GetPassedParameter("allowSort", data))));
        }

        if (data.getParameters().containsKey("days")) {
            context.put("days", TurbineUtils.escapeParam((TurbineUtils.GetPassedParameter("days", data))));
        }
    }
}
