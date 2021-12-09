/*
 * web: org.nrg.xnatx.plugins.batch.workflows.repositories.WorkflowRepository
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2020, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnatx.plugins.batch.workflows.repository;

import com.google.common.collect.ImmutableMap;
import org.intellij.lang.annotations.Language;
import org.nrg.action.ClientException;
import org.nrg.framework.ajax.sql.PageableRepository;
import org.nrg.xdat.om.*;
import org.nrg.xdat.security.helpers.Groups;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.FieldNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xnatx.plugins.batch.workflows.model.Workflow;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnatx.plugins.batch.workflows.model.WorkflowPaginatedRequest;
import org.restlet.data.Status;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Nonnull;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.*;

@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve"})
@Slf4j
@Repository
public class WorkflowRepository implements PageableRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    private static final String WRK_FIELDS = "wrk.wrk_workflowdata_id, wrk.id, wrk.externalid, wrk.pipeline_name, " +
            "wrk.data_type, wrk.comments, wrk.details, wrk.justification, wrk.launch_time, wrk.status, " +
            "wrk.step_description, wrk.percentagecomplete, last_modified, COALESCE(wrk.create_user, u.login) AS create_user ";

    private static final Map<String, ColumnDataType> COLUMN_INFO = ImmutableMap.<String, ColumnDataType>builder()
            .put("wrk_workflowdata_id", new ColumnDataType("wfid", int.class))
            .put("id", new ColumnDataType("id", String.class))
            .put("label", new ColumnDataType("label", String.class))
            .put("externalid", new ColumnDataType("externalId", String.class))
            .put("pipeline_name", new ColumnDataType("pipelineName", String.class))
            .put("data_type", new ColumnDataType("dataType", String.class))
            .put("comments", new ColumnDataType("comments", String.class))
            .put("details", new ColumnDataType("details", String.class))
            .put("justification", new ColumnDataType("justification", String.class))
            .put("launch_time", new ColumnDataType("launchTime", Timestamp.class))
            .put("status", new ColumnDataType("status", String.class))
            .put("step_description", new ColumnDataType("stepDescription", String.class))
            .put("percentagecomplete", new ColumnDataType("percentageComplete", String.class))
            .put("last_modified", new ColumnDataType("modTime", Timestamp.class))
            .put("create_user", new ColumnDataType("createUser", String.class))
            .build();

    private static final RowMapper<Workflow> WF_ROW_MAPPER = (resultSet, index) -> {
        ResultSetMetaData metaData = resultSet.getMetaData();
        Workflow w = new Workflow();
        for (int i=1; i<=metaData.getColumnCount(); i++) {
            String columnName = metaData.getColumnName(i);
            Object item = resultSet.getObject(columnName);
            if (!COLUMN_INFO.containsKey(columnName) || item == null) {
                continue;
            }
            ColumnDataType cdt = COLUMN_INFO.get(columnName);
            Class<?> columnClass = cdt.dataType;
            if (columnClass.equals(Timestamp.class)) {
                item = new Date(((Timestamp) item).getTime());
                columnClass = Date.class;
            }
            w.setProperty(cdt.columnName, item, columnClass);
        }
        return w;
    };

    @Language("SQL")
    public static final String QUERY_WFS = "SELECT " + WRK_FIELDS + ", " +
            " COALESCE(expt.label, subj.label, wrk.id) AS label, " +
            " COALESCE(es.project, ss.project)::varchar AS shared_project " +
            "           FROM wrk_workflowData wrk " +
            "               LEFT JOIN wrk_workflowdata_meta_data meta ON wrk.workflowData_info = meta.meta_data_id " +
            "               LEFT JOIN xdat_user u ON meta.insert_user_xdat_user_id = u.xdat_user_id " +
            "               LEFT JOIN xnat_experimentData expt ON wrk.id = expt.id " +
            "               LEFT JOIN xnat_experimentdata_share es ON expt.id = es.sharing_share_xnat_experimentda_id " +
            "               LEFT JOIN xnat_subjectData subj ON wrk.id = subj.id " +
            "               LEFT JOIN xnat_projectparticipant ss ON subj.id = ss.subject_id ";

    // Pipelines for project
    @Language("SQL")
    public static final String QUERY_PROJECT_WFS = "SELECT * FROM (" + QUERY_WFS + ") AS pq WHERE " +
            "(externalid = :id OR shared_project = :id OR " +
            "(id = :id AND data_type = '" + XnatProjectdata.SCHEMA_ELEMENT_NAME + "') OR " +
            "(id = :arcId AND data_type = '" + ArcProject.SCHEMA_ELEMENT_NAME + "')) ";

    // SQL from WorkflowBasedHistoryBuilder
    // Pipelines on subject
    @Language("SQL")
    public static final String QUERY_SUBJECT_WFS = "SELECT " + WRK_FIELDS + ", " +
            "       subj.label, " +
            "       ss.project AS shared_project " +
            "FROM (SELECT * " +
            "      FROM wrk_workflowData w " +
            "      WHERE id = :id " +
            "         OR id IN (SELECT DISTINCT id " +
            "                   FROM (SELECT sad.id " +
            "                         FROM xnat_subjectassessordata sad " +
            "                         WHERE subject_id = :id " +
            "                         UNION " +
            "                         SELECT iad.id " +
            "                         FROM xnat_subjectassessordata sad " +
            "                                  LEFT JOIN xnat_imageassessordata iad " +
            "                                            ON sad.id = iad.imagesession_id " +
            "                         WHERE iad.id IS NOT NULL " +
            "                           AND subject_id = :id " +
            "                         UNION " +
            "                         SELECT sad.id " +
            "                         FROM xnat_subjectassessordata_history sad " +
            "                         WHERE subject_id = :id " +
            "                         UNION " +
            "                         SELECT iad.id " +
            "                         FROM xnat_subjectassessordata sad " +
            "                                  LEFT JOIN xnat_imageassessordata_history iad " +
            "                                            ON sad.id = iad.imagesession_id " +
            "                         WHERE iad.id IS NOT NULL " +
            "                           AND subject_id = :id " +
            "                         UNION " +
            "                         SELECT iad.id " +
            "                         FROM xnat_subjectassessordata_history sad " +
            "                                  LEFT JOIN xnat_imageassessordata_history iad " +
            "                                            ON sad.id = iad.imagesession_id " +
            "                         WHERE iad.id IS NOT NULL " +
            "                           AND subject_id = :id) AS idq)) AS wrk " +
            "         INNER JOIN xnat_subjectdata subj ON wrk.id = subj.id " +
            "         LEFT JOIN xnat_projectparticipant ss ON subj.id = ss.subject_id " +
            "         LEFT JOIN wrk_workflowdata_meta_data meta ON wrk.workflowData_info = meta.meta_data_id " +
            "         LEFT JOIN xdat_user u ON meta.insert_user_xdat_user_id = u.xdat_user_id ";

    // Pipelines on experiment
    @Language("SQL")
    public static final String QUERY_EXPT_WFS = "SELECT " + WRK_FIELDS + ", " +
            "       expt.label, " +
            "       es.project AS shared_project " +
            "FROM (SELECT * " +
            "      FROM wrk_workflowData w " +
            "      WHERE id = :id " +
            "         OR id IN (SELECT DISTINCT id " +
            "                   FROM (SELECT iad.id " +
            "                         FROM xnat_imageassessordata iad " +
            "                         WHERE iad.id IS NOT NULL " +
            "                           AND iad.imagesession_id = :id " +
            "                         UNION " +
            "                         SELECT iad.id " +
            "                         FROM xnat_imageassessordata_history iad " +
            "                         WHERE iad.id IS NOT NULL " +
            "                           AND iad.imagesession_id = :id) AS idq)) as wrk " +
            "         INNER JOIN xnat_experimentdata expt ON wrk.id = expt.id " +
            "         LEFT JOIN xnat_experimentdata_share es ON expt.id = es.sharing_share_xnat_experimentda_id" +
            "         LEFT JOIN wrk_workflowdata_meta_data meta ON wrk.workflowData_info = meta.meta_data_id " +
            "         LEFT JOIN xdat_user u ON meta.insert_user_xdat_user_id = u.xdat_user_id ";

    private static final String PROJECT_PLINE_DATATYPES = "'" + ArcProject.SCHEMA_ELEMENT_NAME + "','" +
            XnatProjectdata.SCHEMA_ELEMENT_NAME + "'";

    @Autowired
    public WorkflowRepository(final NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Set<String> getAllowableSortColumns() {
        return COLUMN_INFO.keySet();
    }
    public Set<String> getAllowableFilterColumns() {
        return COLUMN_INFO.keySet();
    }

    public Map<String, ColumnDataType> getColumnMapping() {
        return COLUMN_INFO;
    }

    /**
     * Get list of model
     *
     * @param id        item id
     * @param dataType  item type
     * @param user      user (no permissions checking, just used if item type == xdat:user)
     * @param request   the pagination/filter/sort object
     * @return list of model
     * @throws DataAccessException for issues accessing data
     * @throws Exception for issues retrieving xnat data types
     */
    public List<Workflow> getWorkflows(String id, String dataType, UserI user,
                                       WorkflowPaginatedRequest request) throws Exception {

        MapSqlParameterSource namedParams = new MapSqlParameterSource();

        if (StringUtils.isNotBlank(id)) {
            namedParams.addValue("id", id);
        }

        String query;
        switch(dataType) {
            case "xdat:user":
                if (request.isAdminWorkflows()) {
                    if (!Groups.isSiteAdmin(user)) {
                        throw new ClientException(Status.CLIENT_ERROR_FORBIDDEN, "Must be site admin");
                    }
                    query = QUERY_WFS + " WHERE wrk.externalid = '" + PersistentWorkflowUtils.ADMIN_EXTERNAL_ID + "'" +
                            getDaysSuffix(request.getDays(), true);
                } else {
                    query = makeQueryPerUserPermissions(user, namedParams, request, QUERY_WFS, false);
                }
                break;
            case XnatProjectdata.SCHEMA_ELEMENT_NAME:
                namedParams.addValue("arcId",
                        XnatProjectdata.getXnatProjectdatasById(id, user, false)
                                .getArcSpecification().getId());
                query = makeQueryPerUserPermissions(user, namedParams, request, QUERY_PROJECT_WFS, true);
                break;
            case XnatSubjectdata.SCHEMA_ELEMENT_NAME:
                query = makeQueryPerUserPermissions(user, namedParams, request, QUERY_SUBJECT_WFS, false);
                break;
            default:
                query = makeQueryPerUserPermissions(user, namedParams, request, QUERY_EXPT_WFS, false);
                break;
        }

        if (request.getSortable()) {
            query = "SELECT * FROM (" + query + ") AS q"; // Allow for WHERE in query suffix
            query += request.getQuerySuffix(getColumnMapping(), getAllowableFilterColumns(),
                    getAllowableSortColumns(), namedParams);
        }

        return jdbcTemplate.query(query, namedParams, WF_ROW_MAPPER);
    }

    private String getDaysSuffix(int days) {
        return getDaysSuffix(days, false);
    }

    private String getDaysSuffix(int days, boolean useAnd) {
        String op = useAnd ? " AND " : " WHERE ";
        return days > 0 ? op + " launch_time > (NOW() - INTERVAL '" + days + " days') " : "";
    }

    /**
     * Make query to retrieve workflows per <strong>workflowBaseQuery</strong>, restricted by user's permissions
     * (a.k.a., workflows on data user can read -- if all data admin: all workflows from workflowBaseQuery)
     *
     * If <strong>includeUserWorkflows</strong> is true:
     *  - All workflows user has launched and workflows associated with data user can read
     *  - If site admin: the above plus all workflows with ADMIN externalId
     *
     * @param user the user
     * @param namedParams the map SQL params
     * @param workflowBaseQuery base query to execute
     *                          (e.g., all workflows for projectA, or all workflows for sessionB, etc.)
     * @return the query
     */
    @Language("SQL")
    private String makeQueryPerUserPermissions(UserI user,
                                               MapSqlParameterSource namedParams,
                                               WorkflowPaginatedRequest request,
                                               @Nonnull @Language("SQL") String workflowBaseQuery,
                                               boolean useAnd) {

        @Language("SQL") String limitSuffix = "";
        if (!request.getSortable()) {
            namedParams.addValue("rowLimit", request.getPageSize())
                .addValue("rowOffset", request.getOffset());

            limitSuffix = " ORDER BY wrk_workflowdata_id DESC LIMIT :rowLimit OFFSET :rowOffset";
        }

        @Language("SQL") String query;
        if (Groups.isDataAdmin(user)) {
            // Access to all workflows from workflowBaseQuery
            query = workflowBaseQuery;
            if (Groups.isSiteAdmin(user)) {
                query += getDaysSuffix(request.getDays(), useAnd);
            } else {
                query += (useAnd ? " AND " : " WHERE ") + "wrk.externalId != '" + PersistentWorkflowUtils.ADMIN_EXTERNAL_ID + "' " +
                        getDaysSuffix(request.getDays(), true);
            }
            query += limitSuffix;
        } else {
            namedParams.addValue("username", user.getLogin());

            // Workflows user has launched and workflows associated with data user can read
            query = "WITH wrkSubQ AS " +
                    "   (" + workflowBaseQuery + getDaysSuffix(request.getDays(), useAnd) + "), " +
                    "permSub1 AS (SELECT * FROM data_type_fns_get_user_access_by_project(:username)), " +
                    "permSub2 AS (SELECT '" + ArcProject.SCHEMA_ELEMENT_NAME + "'::varchar AS data_type, " +
                    "   ap.arc_project_id::varchar              AS project, " +
                    "   FALSE                                   AS shared, " +
                    "   permSub1.can_create, " +
                    "   permSub1.can_read, " +
                    "   permSub1.can_edit, " +
                    "   permSub1.can_delete, " +
                    "   permSub1.can_active " +
                    "       FROM permSub1 " +
                    "           INNER JOIN arc_project ap ON permSub1.data_type = '" +
                                    XnatProjectdata.SCHEMA_ELEMENT_NAME + "' AND permSub1.project = ap.id), " +
                    "permSubQ AS (SELECT DISTINCT data_type, project, shared FROM " +
                    "   (SELECT * FROM permSub1 UNION SELECT * FROM permSub2) AS dta " +
                    "       WHERE dta.data_type IN (" + PROJECT_PLINE_DATATYPES + ") AND dta.can_edit " +
                    "       OR dta.data_type NOT IN (" + PROJECT_PLINE_DATATYPES + ") AND dta.can_read)" +
                    "SELECT wrk_workflowdata_id, label, id, COALESCE(shared_project, externalid) AS externalid, " +
                    "       pipeline_name, wrkSubQ.data_type, comments, details, justification, launch_time, status, " +
                    "       step_description, percentagecomplete, last_modified, create_user FROM permSubQ " +
                    "       LEFT JOIN wrkSubQ ON " +
                    "              (permSubQ.data_type IN (" + PROJECT_PLINE_DATATYPES + ") AND " +
                    "                   permSubQ.data_type = wrkSubQ.data_type AND NOT permSubQ.shared AND " +
                    "                   permSubQ.project = wrkSubQ.id) " +
                    "              OR " +
                    "              (permSubQ.data_type NOT IN (" + PROJECT_PLINE_DATATYPES + ") AND " +
                    "                   permSubQ.data_type = wrkSubQ.data_type AND NOT permSubQ.shared AND " +
                    "                   permSubQ.project = wrkSubQ.externalId) " +
                    "               OR " +
                    "              (permSubQ.data_type NOT IN (" + PROJECT_PLINE_DATATYPES + ") AND " +
                    "                   permSubQ.data_type = wrkSubQ.data_type AND permSubQ.shared AND " +
                    "                   permSubQ.project = wrkSubQ.shared_project) " +
                    "      WHERE wrk_workflowdata_id IS NOT NULL " + limitSuffix;
        }
        return query;
    }

    /**
     * Get Workflow model object from PersistentWorkflowI object
     * @param wrk   PersistentWorkflowI object
     * @param user  user
     * @return      Worflow model object
     */
    public Workflow getWorkflow(PersistentWorkflowI wrk, UserI user) {
        String label;
        Date lastMod = null;
        try {
            lastMod = ((WrkWorkflowdata) wrk).getItem().getMeta().getDateProperty("last_modified");
        } catch (XFTInitException | ElementNotFoundException | FieldNotFoundException | ParseException e) {
            // Ignore exceptions and just leave mod time null
            log.error("Unable to retrieve last modified time from workflow meta data", e);
        }

        String createUser = wrk.getCreateUser();
        if (createUser == null) {
            createUser = ((WrkWorkflowdata) wrk).getItem().getUser().getLogin();
        }

        switch(wrk.getDataType()) {
            case XnatProjectdata.SCHEMA_ELEMENT_NAME:
                XnatProjectdata proj = XnatProjectdata.getXnatProjectdatasById(wrk.getId(), user, false);
                label = proj.getId();
                break;
            case XnatSubjectdata.SCHEMA_ELEMENT_NAME:
                XnatSubjectdata subj = XnatSubjectdata.getXnatSubjectdatasById(wrk.getId(), user, false);
                label = subj.getLabel();
                break;
            default:
                XnatExperimentdata exp = XnatExperimentdata.getXnatExperimentdatasById(wrk.getId(), user, false);
                label = exp.getLabel();
                break;
        }

        return new Workflow(wrk.getWorkflowId(), wrk.getId(), label, wrk.getExternalid(),
                wrk.getPipelineName(), wrk.getDataType(),
                wrk.getComments(), wrk.getDetails(), wrk.getJustification(), wrk.getLaunchTimeDate(), wrk.getStatus(), wrk.getStepDescription(), wrk.getPercentagecomplete(),
                lastMod, createUser);
    }
}