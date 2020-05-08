// Copyright 2019 Radiologics, Inc
// Developer: Mohana Ramaratnam <mohana@radiologics.com>

package org.nrg.xnat.turbine.modules.screens;

import org.apache.log4j.Logger;
import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.xdat.turbine.modules.screens.SecureScreen;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.db.PoolDBUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.bulk.utils.DynamicAddSqlQueryFieldsToDataTypes;
import org.nrg.xnat.bulk.utils.SearchXMLBuilder;

import com.google.common.collect.Lists;

import java.util.Calendar;


@SuppressWarnings("unused")

public class XDATScreen_bulk_action extends SecureScreen {
    static Logger logger = Logger.getLogger(XDATScreen_bulk_action.class);

    @Override
    protected void doBuildTemplate(RunData data, Context context) throws Exception {
        UserI user = TurbineUtils.getUser(data);

        context.put("timezoneOffset",
                Calendar.getInstance().getTimeZone().getOffset(Calendar.getInstance().getTimeInMillis()));

        String job = (String) org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("job", data);
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(job) && PoolDBUtils.HackCheck(job)) {
            throw new Exception("Invalid value submitted.");
        } else if (org.apache.commons.lang3.StringUtils.isNotEmpty(job)) {
            context.put("job", job);
        }

        String resources = (String) org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("resources", data);
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(resources) && PoolDBUtils.HackCheck(resources)) {
            throw new Exception("Invalid value submitted.");
        }

        java.util.List<String> resourceList = null;
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(resources)) {
            if (resources.indexOf(",") > 0) {
                resourceList = java.util.Arrays.asList(resources.split(","));
            } else {
                resourceList = Lists.newArrayList(resources);
            }
        }

        String scans = (String) org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("scan_types", data);
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(scans) && PoolDBUtils.HackCheck(scans)) {
            throw new Exception("Invalid value submitted.");
        }

        java.util.List<String> typesList = null;
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(scans)) {
            if (scans.indexOf(",") > 0) {
                typesList = java.util.Arrays.asList(scans.split(","));
            } else {
                typesList = Lists.newArrayList(scans);
            }
        }

        // The below section is skipped if a search is passed in, ensure that everything else you need is in-context
        if (TurbineUtils.HasPassedParameter("project", data) && TurbineUtils.HasPassedParameter("dataType", data)) {
            final String project = (String) org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("project", data);
            final String dataType = (String) org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("dataType", data);

            String sb = "<xdat:search_where method=\"AND\">" +
                    "<xdat:child_set method=\"OR\">" +
                    "<xdat:criteria override_value_formatting=\"0\">" +
                    "<xdat:schema_field>" + dataType + "/sharing/share/project</xdat:schema_field>" +
                    "<xdat:comparison_type>=</xdat:comparison_type>" +
                    "<xdat:value>" + project + "</xdat:value>" +
                    "</xdat:criteria>" +
                    "<xdat:criteria override_value_formatting=\"0\">" +
                    "<xdat:schema_field>" + dataType + "/PROJECT</xdat:schema_field>" +
                    "<xdat:comparison_type>=</xdat:comparison_type>" +
                    "<xdat:value>" + project + "</xdat:value>" +
                    "</xdat:criteria>" +
                    "</xdat:child_set>" +
                    "</xdat:search_where>";

            context.put("xss", (new SearchXMLBuilder()).execute(Lists.newArrayList(project), dataType, user, sb,
                    job, resourceList, typesList));

            DynamicAddSqlQueryFieldsToDataTypes.addFields();
        }

    }
}

