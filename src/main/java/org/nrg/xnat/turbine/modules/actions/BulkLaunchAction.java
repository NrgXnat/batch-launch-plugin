// Developer: Mohana Ramaratnam <mohana@radiologics.com>

package org.nrg.xnat.turbine.modules.actions;

import static org.nrg.xnatx.plugins.batch.utils.SearchXMLBuilder.getCheckedParameter;
import static org.nrg.xnatx.plugins.batch.utils.SearchXMLBuilder.getItemList;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.xdat.exceptions.InvalidSearchException;
import org.nrg.xdat.om.XdatStoredSearch;
import org.nrg.xdat.search.DisplaySearch;
import org.nrg.xdat.turbine.modules.actions.DisplaySearchAction;
import org.nrg.xft.XFTTable;
import org.nrg.xft.schema.Wrappers.XMLWrapper.SAXReader;
import org.nrg.xft.security.UserI;
import org.nrg.xnatx.plugins.batch.utils.SearchXMLBuilder;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

@SuppressWarnings("unused")
@Slf4j
public class BulkLaunchAction extends DisplaySearchAction {
    @Override
    public void doPerform(final RunData data, final Context context) {
        final UserI user = getUser();
        try {
            final String rawSearchXml = data.getParameters().getString("search_xml");
            if (StringUtils.isBlank(rawSearchXml)) {
                data.setMessage("Your search result has expired.  Please resubmit your query. ");
                data.setScreenTemplate("Error.vm");
                return;
            }

            final String searchXml   = StringUtils.replace(URLDecoder.decode(RegExUtils.replaceAll(rawSearchXml,
                    "%", "%25"), "UTF-8"), ".close.", "/");
            final String whereClause = getWhereClause(searchXml);
            context.put("xss", buildNewSearchXML(searchXml, user, whereClause, data));
            super.doPreliminaryProcessing(data, context);
            data.setScreenTemplate(getScreenTemplate(data));
            doFinalProcessing(data, context);
        } catch (SearchTimeoutException e) {
            log.error("A search by user {} appeared to time out", user.getUsername(), e);
            data.setMessage(e.getMessage());
            data.setScreenTemplate("Index.vm");
        } catch (IllegalAccessException e) {
            data.setMessage("The user does not have access to this data.");
            data.setScreenTemplate("Error.vm");
            data.getParameters().setString("exception", e.toString());
        } catch (InvalidSearchException e) {
            data.setMessage("You specified an invalid search condition: " + e.getMessage());
            data.setScreenTemplate("Error.vm");
        } catch (Exception e) {
            error(e, data);
        }
    }

    @Override
    public String getScreenTemplate(final RunData data) {
        return DEFAULT_SCREEN_TEMPLATE;
    }

    private String buildNewSearchXML(final String searchXml, final UserI user, final String whereClause, final RunData data) throws Exception {
        final DisplaySearch displaySearch = new XdatStoredSearch(new SAXReader(user).parse(new InputSource(new StringReader(searchXml)))).getCSVDisplaySearch(user);

        if (displaySearch == null) {
            throw new SearchTimeoutException("BulkLaunchAction Session Expired: The previously performed search has timed out.");
        }

        final String rootElementName = displaySearch.getRootElement().getFullXMLName();
        if (StringUtils.isBlank(rootElementName)) {
            throw new Exception("Invalid value submitted.");
        }

        //Load search results into a table
        final XFTTable table = (XFTTable) displaySearch.execute(null, user.getLogin());

        //The 'session_id' value is specified as the DisplayField ID for the xnat:mrSessionData/ID field in the Display docs.
        //This value should match the value at the header of the session id column in the previous ExampleListingActionScreen implementation.
        //Distinct projects
        final List<String> distinctProjectsInSearch = new ArrayList<>();
        table.resetRowCursor();
        while (table.hasMoreRows()) {
            final Hashtable<?, ?> row     = table.nextRowHash();
            final String          project = StringUtils.defaultIfBlank((String) row.get(PROJECT_HEADER), (String) row.get(PROJECT_HEADER_LOWER));
            if (!distinctProjectsInSearch.contains(project)) {
                distinctProjectsInSearch.add(project);
            }
        }

        return (new SearchXMLBuilder()).execute(distinctProjectsInSearch, rootElementName, user, whereClause,
                getCheckedParameter(data, "job"),
                getItemList(data, "resources"),
                getItemList(data, "scan_types"));
    }

    private static String getWhereClause(final String searchXml) {
        final String whereClause = StringUtils.substringBetween(searchXml, START_TAG, END_TAG);
        return StringUtils.isNotBlank(whereClause) ? String.format(WHERE_CLAUSE, whereClause) : "";
    }

    private static final String DEFAULT_SCREEN_TEMPLATE = "XDATScreen_bulk_action.vm";
    private static final String START_TAG               = "<xdat:search_where";
    private static final String END_TAG                 = "</xdat:search_where>";
    private static final String WHERE_CLAUSE            = START_TAG + "%s" + END_TAG;
    private static final String PROJECT_HEADER          = "Project";
    private static final String PROJECT_HEADER_LOWER    = StringUtils.lowerCase(PROJECT_HEADER);

}
