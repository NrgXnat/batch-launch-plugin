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
import org.nrg.framework.ajax.sql.PageableRepository;
import org.nrg.xdat.om.*;
import org.nrg.xdat.schema.SchemaElement;
import org.nrg.xdat.schema.SchemaField;
import org.nrg.xdat.security.helpers.Groups;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.FieldNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xnatx.plugins.batch.workflows.model.Workflow;
import org.nrg.xnatx.plugins.batch.workflows.model.WorkflowDuration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnatx.plugins.batch.workflows.model.WorkflowPaginatedRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.*;

@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve"})
@Slf4j
@Repository
public class WorkflowRepository implements PageableRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private Map<String, Long> workflowDurationMap = null;
    private long workflowDurationMapExpiration = System.currentTimeMillis();

    private static final int WF_DURATION_EXP_SEC = 14400; // 4 hours

    private static final RowMapper<WorkflowDuration> DURATION_WF_MAPPER = (resultSet, index) -> {
        final String name = resultSet.getString("pipeline_name");
        final Long duration = resultSet.getLong("max_duration");
        return new WorkflowDuration(name, duration);
    };

    // Pipeline max duration
    public static final String QUERY_WF_TIME = "SELECT pipeline_name, extract(EPOCH from MAX(last_modified-launch_time)) " +
            "AS max_duration FROM (SELECT * FROM wrk_workflowData ORDER BY launch_time LIMIT 50000) AS wrk LEFT JOIN " +
            "wrk_workflowData_meta_data meta ON wrk.workflowdata_info=meta.meta_data_id WHERE " +
            "wrk.status='Complete' GROUP BY pipeline_name";

    private static final Map<String, ColumnDataType> COLUMN_INFO = ImmutableMap.<String, ColumnDataType>builder()
            .put("wrk_workflowdata_id", new ColumnDataType("wfid", int.class))
            .put("id", new ColumnDataType("id", String.class))
            .put("label", new ColumnDataType("label", String.class))
            .put("item_time", new ColumnDataType("itemTime", Timestamp.class))
            .put("externalid", new ColumnDataType("externalId", String.class))
            .put("pipeline_name", new ColumnDataType("pipelineName", String.class))
            .put("data_type", new ColumnDataType("dataType", String.class))
            .put("comments", new ColumnDataType("comments", String.class))
            .put("details", new ColumnDataType("details", String.class))
            .put("justification", new ColumnDataType("justification", String.class))
            .put("description", new ColumnDataType("description", String.class))
            .put("src", new ColumnDataType("src", String.class))
            .put("type", new ColumnDataType("type", String.class))
            .put("category", new ColumnDataType("category", String.class))
            .put("current_step_launch_time", new ColumnDataType("currentStepLaunchTime", Timestamp.class))
            .put("launch_time", new ColumnDataType("launchTime", Timestamp.class))
            .put("current_step_id", new ColumnDataType("currentStepId", String.class))
            .put("status", new ColumnDataType("status", String.class))
            .put("create_user", new ColumnDataType("createUser", String.class))
            .put("next_step_id", new ColumnDataType("nextStepId", String.class))
            .put("step_description", new ColumnDataType("stepDescription", String.class))
            .put("percentagecomplete", new ColumnDataType("percentageComplete", String.class))
            .put("jobid", new ColumnDataType("jobId", String.class))
            .put("last_modified", new ColumnDataType("modTime", Timestamp.class))
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
            Class columnClass = cdt.dataType;
            if (columnClass.equals(Timestamp.class)) {
                item = new Date(((Timestamp) item).getTime());
                columnClass = Date.class;
            }
            w.setProperty(cdt.columnName, item, columnClass);
        }
        return w;
    };

    // Pipelines for project
    @Language("SQL")
    public static final String QUERY_PROJECT_WFS = "SELECT wrk.*, p.id AS label, md.insert_date AS item_time, " +
            "NULL::varchar AS shared_project " +
            "FROM wrk_workflowData wrk " +
            "         INNER JOIN xnat_projectdata p ON p.id = wrk.id " +
            "         LEFT JOIN " +
            "     xnat_projectdata_meta_data md ON p.projectdata_info = md.meta_data_id " +
            "WHERE wrk.id = :id " +
            "  AND wrk.data_type = '" + XnatProjectdata.SCHEMA_ELEMENT_NAME + "' " +
            "UNION " +
            "SELECT wrk.*, p.id AS label, md.insert_date as item_time, NULL::varchar AS shared_project " +
            "FROM wrk_workflowData wrk " +
            "         INNER JOIN arc_project a ON a.arc_project_id::varchar = wrk.id " +
            "         INNER JOIN xnat_projectdata p ON p.id = a.id " +
            "         LEFT JOIN " +
            "     xnat_projectdata_meta_data md ON p.projectdata_info = md.meta_data_id " +
            "WHERE wrk.id = :arcId " +
            "  AND wrk.data_type = 'arc:project'";

    // SQL from WorkflowBasedHistoryBuilder
    // Pipelines on subject
    @Language("SQL")
    public static final String QUERY_SUBJECT_WFS = "SELECT wrk.*, " +
            "       s.label, " +
            "       md.insert_date AS item_time, " +
            "       " + getSharedProjectJoinSQL("s", true)[0] + " AS shared_project " +
            "FROM (SELECT * " +
            "      FROM wrk_workflowData " +
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
            "         INNER JOIN " +
            "     xnat_subjectdata s ON wrk.id = s.id " +
            "         LEFT JOIN xnat_subjectdata_meta_data md " +
            "                   ON s.subjectdata_info = md.meta_data_id " +
                getSharedProjectJoinSQL("s", true)[1];

    // Pipelines on experiment
    @Language("SQL")
    public static final String QUERY_EXPT_WFS = "SELECT wrk.*, " +
            "       xnat_experimentdata.label, " +
            getSharedProjectJoinSQL("xnat_experimentdata", false)[0] + " AS shared_project, " +
            getExperimentItemTimeSQL() + " AS item_time " +
            "FROM (SELECT * " +
            "      FROM wrk_workflowData " +
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
            "         INNER JOIN xnat_experimentdata ON wrk.id = xnat_experimentdata.id " +
                    getSharedProjectJoinSQL("xnat_experimentdata", false)[1];

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
                query = makeQueryPerUserPermissions(user, namedParams, buildQueryWithDataTypeLabels(), true);
                break;
            case XnatProjectdata.SCHEMA_ELEMENT_NAME:
                namedParams.addValue("arcId",
                        XnatProjectdata.getXnatProjectdatasById(id, user, false)
                                .getArcSpecification().getId());
                query = makeQueryPerUserPermissions(user, namedParams,
                            buildQueryWithDataTypeLabels(QUERY_PROJECT_WFS,
                                    "(externalid = :id OR shared_project = :id)"));
                break;
            case XnatSubjectdata.SCHEMA_ELEMENT_NAME:
                query = makeQueryPerUserPermissions(user, namedParams, QUERY_SUBJECT_WFS);
                break;
            default:
                query = makeQueryPerUserPermissions(user, namedParams, QUERY_EXPT_WFS);
                break;
        }
        query = "SELECT * FROM (" + query + ") AS q"; //Allow for WHERE in query suffix
        query += request.getQuerySuffix(getColumnMapping(), getAllowableFilterColumns(), getAllowableSortColumns(),
                namedParams);

        List<Workflow> wfs = jdbcTemplate.query(query, namedParams, WF_ROW_MAPPER);

        // Estimate % complete if not provided
        updateWorkflowProgress(wfs);
        return wfs;
    }

    /**
     * Make query to retrieve workflows based on user's permissions:
     *  - Workflows user has launched and workflows associated with data user can read
     *  - if site admin: the above plus all workflows with ADMIN externalId
     *  - if all data admin: return all workflows
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
                                               @Nonnull String workflowBaseQuery) {
        return makeQueryPerUserPermissions(user, namedParams, workflowBaseQuery, false);
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
     * @param includeUserWorkflows include workflows user has launched / admin workflows if admin user
     * @return the query
     */
    @Language("SQL")
    private String makeQueryPerUserPermissions(UserI user,
                                               MapSqlParameterSource namedParams,
                                               @Nonnull String workflowBaseQuery,
                                               boolean includeUserWorkflows) {
        @Language("SQL") String query;
        @Language("SQL") String wrkSubQ = "SELECT wrkSub.*, meta.last_modified, meta.insert_user_xdat_user_id " +
                "           FROM (" + workflowBaseQuery + ") AS wrkSub " +
                "               LEFT JOIN wrk_workflowdata_meta_data meta " +
                "                   ON wrkSub.workflowData_info = meta.meta_data_id";
        if (Groups.isDataAdmin(user)) {
            // Access to all workflows from workflowBaseQuery
            query = "SELECT DISTINCT " + String.join(", ", COLUMN_INFO.keySet()) +
                    " FROM (" + wrkSubQ + ") AS wsqF";
        } else {
            String usrLaunchedWfs = "";
            if (includeUserWorkflows) {
                usrLaunchedWfs = "SELECT " + getReturnColumns() +
                        "   FROM wrkSubQ " +
                        "   WHERE insert_user_xdat_user_id = :userId OR create_user = :username ";
                if (Groups.isSiteAdmin(user)) {
                    // Add site admin workflows
                    usrLaunchedWfs += " OR wrkSubQ.externalId = '" + PersistentWorkflowUtils.ADMIN_EXTERNAL_ID + "' ";
                }
                usrLaunchedWfs += "UNION ";
            }
            namedParams.addValue("userId", user.getID())
                    .addValue("username", user.getLogin());
            // Workflows user has launched and workflows associated with data user can read
            query = "WITH wrkSubQ AS " +
                    "   (" + wrkSubQ + "), " +
                    "permSub1 AS (SELECT * FROM data_type_fns_get_user_access_by_project(:username)), " +
                    "permSub2 AS (SELECT 'arc:project'::varchar AS data_type, " +
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
                    usrLaunchedWfs +
                    "SELECT DISTINCT " + getReturnColumns() +
                    "   FROM wrkSubQ " +
                    "       INNER JOIN permSubQ ON " +
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
                    "                   permSubQ.project = wrkSubQ.shared_project) ";
        }
        return query;
    }

    /**
     * Update workflowDurationMap
     * @return T if map has entires
     */
    private boolean updateWorkflowDurationMap() {
        if (workflowDurationMap == null || System.currentTimeMillis() >= workflowDurationMapExpiration) {
            // Get workflow duration info
            List<WorkflowDuration> wfds = jdbcTemplate.query(QUERY_WF_TIME, DURATION_WF_MAPPER);
            workflowDurationMap = new HashMap<>();
            for (WorkflowDuration wd : wfds) {
                workflowDurationMap.put(wd.getPipelineName(), wd.getDuration());
            }
            workflowDurationMapExpiration = System.currentTimeMillis() + 1000 * WF_DURATION_EXP_SEC;
        }
        return workflowDurationMap != null && !workflowDurationMap.isEmpty();
    }

    /**
     * Test if workflow status is active (e.g., if percentage complete should be computed)
     * @param status the status
     * @return T/F
     */
    private boolean isActiveStatus(String status) {
        return status.equalsIgnoreCase(PersistentWorkflowUtils.RUNNING);
    }

    /**
     * Update percentage complete for workflow
     * @param w the workflow
     */
    private void updateWorkflowProgress(Workflow w) {
        updateWorkflowProgress(w, true);
    }

    /**
     * Update percentage complete for workflow
     * @param w         the workflow
     * @param doUpdate  update workflowDurationMap?
     */
    private void updateWorkflowProgress(Workflow w, boolean doUpdate) {
        if (doUpdate) {
            if (!updateWorkflowDurationMap()) {
                return;
            }
        }
        if (!isActiveStatus(w.getStatus()) || StringUtils.isNotBlank(w.getPercentageComplete())) {
            return;
        }
        String name = w.getPipelineName();
        long duration;
        if (workflowDurationMap.containsKey(name) && (duration = workflowDurationMap.get(name)) > 0) {
            w.setPercentageComplete(String.format("%1.2f",
                    (System.currentTimeMillis()-w.getLaunchTime().getTime()) / 1000.0 / duration * 100.0));
            w.setStepDescription("[progress % estimated]");
        }
    }

    /**
     * Update percentage complete for list of model
     * @param wfs   list of model
     */
    private void updateWorkflowProgress(List<Workflow> wfs) {
        if (!updateWorkflowDurationMap()) {
            return;
        }
        for (Workflow w : wfs) {
            updateWorkflowProgress(w, false);
        }
    }

    /**
     * Get Workflow model object from PersistentWorkflowI object
     * @param wrk   PersistentWorkflowI object
     * @param user  user
     * @return      Worflow model object
     */
    public Workflow getWorkflow(PersistentWorkflowI wrk, UserI user) {
        Date cslt = (wrk.getCurrentStepLaunchTime() instanceof Date) ? (Date) wrk.getCurrentStepLaunchTime() : null;
        String label;
        Date itemTime;
        Date lastMod = null;
        try {
            lastMod = ((WrkWorkflowdata) wrk).getItem().getMeta().getDateProperty("last_modified");
        } catch (XFTInitException | ElementNotFoundException | FieldNotFoundException | ParseException e) {
            // Ignore exceptions and just leave mod time null
            log.error("Unable to retrieve last modified time from workflow meta data", e);
        }

        switch(wrk.getDataType()) {
            case XnatProjectdata.SCHEMA_ELEMENT_NAME:
                XnatProjectdata proj = XnatProjectdata.getXnatProjectdatasById(wrk.getId(), user, false);
                label = proj.getId();
                itemTime = proj.getInsertDate();
                break;
            case XnatSubjectdata.SCHEMA_ELEMENT_NAME:
                XnatSubjectdata subj = XnatSubjectdata.getXnatSubjectdatasById(wrk.getId(), user, false);
                label = subj.getLabel();
                itemTime = subj.getInsertDate();
                break;
            default:
                XnatExperimentdata exp = XnatExperimentdata.getXnatExperimentdatasById(wrk.getId(), user, false);
                label = exp.getLabel();
                itemTime = exp.getInsertDate();
                break;
        }
        Workflow wf = new Workflow(wrk.getWorkflowId(), wrk.getId(), label, itemTime, wrk.getExternalid(),
                wrk.getPipelineName(), wrk.getDataType(),
                wrk.getComments(), wrk.getDetails(), wrk.getJustification(), null, null, wrk.getType(),
                wrk.getCategory(), cslt, wrk.getLaunchTimeDate(), wrk.getCurrentStepId(), wrk.getStatus(),
                wrk.getCreateUser(), null, wrk.getStepDescription(), wrk.getPercentagecomplete(),
                null, lastMod
        );
        // Estimate % complete if not provided
        updateWorkflowProgress(wf);
        return wf;
    }

    /**
     * Return query that contains unions of data type queries that select label field
     * @param initialQuery initial query onto which the unions will be appended
     * @param addlConstraints constraints to apply
     * @return the query string
     * @throws Exception for issues retrieving xnat data types
     */
    private String buildQueryWithDataTypeLabels(@Nullable final String initialQuery,
                                                @Nullable String... addlConstraints)
            throws Exception {

        StringBuilder qb;
        String unionStr = "";
        if (initialQuery != null) {
            qb = new StringBuilder(initialQuery);
            unionStr = " UNION";
        } else {
            qb = new StringBuilder();
        }

        String constraints = null;
        if (addlConstraints != null && addlConstraints.length > 0) {
            constraints = StringUtils.join(addlConstraints," AND ");
        }

        List<String> dataTypes = jdbcTemplate.query("SELECT DISTINCT(data_type) FROM wrk_workflowData",
                (resultSet, rowNum) -> resultSet.getString("data_type"));

        String experimentTable = SchemaElement.GetElement(XnatExperimentdata.SCHEMA_ELEMENT_NAME).getSQLName();
        for (int i = 0; i < dataTypes.size(); i++) {
            String outname = "wrk" + i;
            String type = dataTypes.get(i);
            String tableName;
            String timeStr = "md.insert_date";
            String labelCol = "label";
            String idCol = "id";
            String mdJoin = null;
            String sharedProjectJoin = null;
            String sharedProjectCol = "NULL::varchar";

            SchemaElement se;
            try {
                se = SchemaElement.GetElement(type);
            } catch (ElementNotFoundException e) {
                log.warn("Found workflow for datatype that no longer exists", e);
                continue;
            }
            switch(type) {
                case XnatProjectdata.SCHEMA_ELEMENT_NAME:
                    tableName = se.getSQLName();
                    labelCol = "id";
                    mdJoin = getMetaDataJoinSQL(tableName, se);
                    break;
                case XnatSubjectdata.SCHEMA_ELEMENT_NAME:
                    tableName = se.getSQLName();
                    mdJoin = getMetaDataJoinSQL(tableName, se);
                    String[] sinfo = getSharedProjectJoinSQL(tableName, true);
                    sharedProjectCol = sinfo[0];
                    sharedProjectJoin = sinfo[1];
                    break;
                case ArcProject.SCHEMA_ELEMENT_NAME:
                    tableName = se.getSQLName();
                    labelCol = "id";
                    idCol = "arc_project_id";
                    SchemaElement projSe = SchemaElement.GetElement(XnatProjectdata.SCHEMA_ELEMENT_NAME);
                    String projTable = projSe.getSQLName();
                    mdJoin = " LEFT JOIN " + projTable + " ON " + projTable + ".id = " + tableName + ".id " +
                            getMetaDataJoinSQL(projTable, projSe);
                    break;
                default:
                    if (se.instanceOf(XnatExperimentdata.SCHEMA_ELEMENT_NAME)) {
                        tableName = experimentTable;
                        timeStr = getExperimentItemTimeSQL();
                        String[] einfo = getSharedProjectJoinSQL(tableName, false);
                        sharedProjectCol = einfo[0];
                        sharedProjectJoin = einfo[1];
                    } else {
                        ArrayList pks = se.getAllPrimaryKeys();
                        if (pks.size() != 1) {
                            continue;
                        }
                        idCol = ((SchemaField) pks.get(0)).getGenericXFTField().getId();
                        labelCol = idCol;
                        tableName = se.getSQLName();
                        mdJoin = getMetaDataJoinSQL(tableName, se);
                    }
                    break;
            }

            qb.append(unionStr);
            qb.append(" SELECT * FROM (");
            qb.append(" SELECT " + outname + ".*, " + tableName + "." + labelCol + "::varchar AS label, " +
                    timeStr + " AS item_time, " + sharedProjectCol + " AS shared_project " +
                    " FROM (SELECT * FROM wrk_workflowData WHERE data_type = '" + type + "') " +
                    "AS " + outname + " LEFT JOIN " + tableName + " ON " + outname + ".id = " +
                    tableName + "." + idCol + "::varchar");
            if (mdJoin != null) {
                qb.append(mdJoin);
            }
            if (sharedProjectJoin != null) {
                qb.append(sharedProjectJoin);
            }
            qb.append(") AS " + outname + "F ");
            if (constraints != null) {
                qb.append(" WHERE " + constraints);
            }
            unionStr = " UNION ";
        }
        return qb.toString();
    }

    /**
     * See {@link #buildQueryWithDataTypeLabels(String, String...)}
     * @return query
     * @throws Exception for issues collecting datatypes
     */
    private String buildQueryWithDataTypeLabels() throws Exception {
        return buildQueryWithDataTypeLabels(null);
    }

    /**
     * Return the SQL for item_time for experiment data
     * @return the SQL string
     */
    private static String getExperimentItemTimeSQL() {
        return "CASE " +
                "WHEN xnat_experimentdata.date IS null THEN null::timestamp " +
                "ELSE nullif(concat_ws(' ', xnat_experimentdata.date, xnat_experimentdata.time),'')::timestamp " +
                "END ";
    }

    /**
     * Get join SQL for adding metadata table
     * @param tableName the table
     * @param se the schema element for the data type
     * @return the SQL join string
     */
    private String getMetaDataJoinSQL(String tableName, SchemaElement se) {
        return " LEFT JOIN " + tableName + "_meta_data md ON " + tableName +
                "." + se.getGenericXFTElement().getName() + "_info = md.meta_data_id";
    }

    /**
     * Get join SQL for adding project share table
     * @param tableName the table
     * @param isSubject subject data?
     * @return the name of the joined table and SQL join string
     */
    private static String[] getSharedProjectJoinSQL(String tableName, boolean isSubject) {
        String joinedTableName;
        String sql;
        if (isSubject) {
            joinedTableName = "pp";
            sql = " LEFT JOIN xnat_projectparticipant " + joinedTableName +
                    " ON " + tableName + ".id = " + joinedTableName+ ".subject_id ";
        } else {
            joinedTableName = "es";
            sql = " LEFT JOIN xnat_experimentdata_share " + joinedTableName +
                    " ON " + tableName + ".id = " + joinedTableName + ".sharing_share_xnat_experimentda_id ";
        }
        return new String[]{joinedTableName + ".project", sql};
    }
    
    private String getReturnColumns() {
        return "wrk_workflowdata_id, " +
                "          id, " +
                "          label, " +
                "          item_time, " +
                "          externalid, " +
                "          pipeline_name, " +
                "          wrkSubQ.data_type, " +
                "          comments, " +
                "          details, " +
                "          justification, " +
                "          description, " +
                "          src, " +
                "          type, " +
                "          category, " +
                "          current_step_launch_time, " +
                "          launch_time, " +
                "          current_step_id, " +
                "          status, " +
                "          create_user, " +
                "          next_step_id, " +
                "          step_description, " +
                "          percentagecomplete, " +
                "          jobid, " +
                "          wrkSubQ.last_modified ";
    }

}