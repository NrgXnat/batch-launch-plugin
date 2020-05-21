// Copyright 2019 Radiologics, Inc
// Developer: Mohana Ramaratnam <mohana@radiologics.com>

package org.nrg.xnat.turbine.modules.screens;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.action.ClientException;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.turbine.modules.screens.SecureScreen;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnatx.plugins.batch.utils.SearchXMLBuilder;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
@Slf4j
public class XDATScreen_bulk_action extends SecureScreen {

    @Override
    protected void doBuildTemplate(final RunData data, final Context context) throws ClientException {
        final UserI user = XDAT.getUserDetails();

        context.put("timezoneOffset", Calendar.getInstance().getTimeZone().getOffset(Calendar.getInstance().getTimeInMillis()));

        final String job = SearchXMLBuilder.getCheckedParameter(data, "job");
        if (StringUtils.isNotEmpty(job)) {
            context.put("job", job);
        }

        // The below section is skipped if a search is passed in, ensure that everything else you need is in-context
        if (TurbineUtils.HasPassedParameter("project", data) && TurbineUtils.HasPassedParameter("dataType", data)) {
            final String       project   = (String) TurbineUtils.GetPassedParameter("project", data);
            final String       dataType  = (String) TurbineUtils.GetPassedParameter("dataType", data);
            final List<String> resources = SearchXMLBuilder.getItemList(data, "resources");
            final List<String> scanTypes = SearchXMLBuilder.getItemList(data, "scan_types");
            context.put("xss", (new SearchXMLBuilder()).execute(Collections.singletonList(project), dataType, user, String.format(SEARCH_TEMPLATE, dataType, project), job, resources, scanTypes));
        }
    }

    private static final String SEARCH_TEMPLATE = "<xdat:search_where method=\"AND\">" +
                                                  "<xdat:child_set method=\"OR\">" +
                                                  "<xdat:criteria override_value_formatting=\"0\">" +
                                                  "<xdat:schema_field>%1$s/sharing/share/project</xdat:schema_field>" +
                                                  "<xdat:comparison_type>=</xdat:comparison_type>" +
                                                  "<xdat:value>%2$s</xdat:value>" +
                                                  "</xdat:criteria>" +
                                                  "<xdat:criteria override_value_formatting=\"0\">" +
                                                  "<xdat:schema_field>%1$s/PROJECT</xdat:schema_field>" +
                                                  "<xdat:comparison_type>=</xdat:comparison_type>" +
                                                  "<xdat:value>%2$s</xdat:value>" +
                                                  "</xdat:criteria>" +
                                                  "</xdat:child_set>" +
                                                  "</xdat:search_where>";
}

