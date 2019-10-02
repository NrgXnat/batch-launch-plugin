// Copyright 2019 Radiologics, Inc
// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnat.bulk.repositories;

import com.google.common.collect.ImmutableMap;
import org.nrg.containers.services.impl.ContainerServiceImpl;
import org.nrg.xdat.om.WrkWorkflowdata;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.schema.SchemaElement;
import org.nrg.xdat.security.ElementSecurity;
import org.nrg.xft.exception.ElementNotFoundException;
import org.nrg.xft.exception.FieldNotFoundException;
import org.nrg.xft.exception.XFTInitException;
import org.nrg.xnat.bulk.model.Workflow;
import org.nrg.xnat.bulk.xapi.PageRequest;
import org.nrg.xnat.bulk.model.WorkflowDuration;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.security.UserI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Nullable;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.*;

@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve"})
@Slf4j
@Repository
public class WorkflowRepository implements PageableRepository {
    private NamedParameterJdbcTemplate jdbcTemplate;
    private Map<String, Long> workflowDurationMap = null;
    private long workflowDurationMapExpiration = System.currentTimeMillis();
    private int WF_DURATION_EXP_SEC = 14400; // 4 hours

    private static final List<String> inactiveStatuses = Arrays.asList(PersistentWorkflowUtils.COMPLETE,
            PersistentWorkflowUtils.FAILED, PersistentWorkflowUtils.QUEUED, ContainerServiceImpl.CREATED);

    private static final RowMapper<WorkflowDuration> DURATION_WF_MAPPER = new RowMapper<WorkflowDuration>() {
        @Override
        public WorkflowDuration mapRow(final ResultSet resultSet, final int index) throws SQLException {
            final String name = resultSet.getString("pipeline_name");
            final Long duration = resultSet.getLong("max_duration");
            return new WorkflowDuration(name, duration);
        }
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

    private static final RowMapper<Workflow> WF_ROW_MAPPER = new RowMapper<Workflow>() {
        @Override
        public Workflow mapRow(final ResultSet resultSet, final int index) throws SQLException {
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
        }
    };

    // Pipelines for project or for entries within project (experiments, subjects, etc)
    public static final String QUERY_PROJECT_WFS = "SELECT wrk.*, wrk.id AS label, NULL as item_time " +
            "FROM wrk_workflowData wrk WHERE id = :id OR (id = :arcId AND data_type = 'arc:project')";

    // SQL from WorkflowBasedHistoryBuilder
    // Pipelines on subject
    public static final String QUERY_SUBJECT_WFS = "SELECT wrk.*, xnat_subjectdata.label, NULL AS item_time, " +
            "meta.last_modified FROM (SELECT * FROM wrk_workflowData WHERE id = :id OR " +
            "id IN (SELECT DISTINCT id FROM (SELECT sad.id FROM xnat_subjectassessordata sad WHERE subject_id=:id " +
            "UNION SELECT iad.id FROM xnat_subjectassessordata sad LEFT JOIN xnat_imageassessordata iad " +
            "ON sad.id=iad.imagesession_id WHERE iad.id IS NOT NULL AND subject_id=:id UNION " +
            "SELECT sad.id FROM xnat_subjectassessordata_history sad WHERE subject_id=:id UNION " +
            "SELECT iad.id FROM xnat_subjectassessordata sad LEFT JOIN xnat_imageassessordata_history iad " +
            "ON sad.id=iad.imagesession_id WHERE iad.id IS NOT NULL AND subject_id=:id UNION " +
            "SELECT iad.id FROM xnat_subjectassessordata_history sad LEFT JOIN xnat_imageassessordata_history iad " +
            "ON sad.id=iad.imagesession_id WHERE iad.id IS NOT NULL AND subject_id=:id) AS idq)) AS wrk INNER JOIN " +
            "xnat_subjectdata ON wrk.id=xnat_subjectdata.id LEFT JOIN wrk_workflowdata_meta_data meta " +
            "ON wrk.workflowData_info=meta.meta_data_id";

    // Pipelines on experiment
    public static final String QUERY_EXPT_WFS = "SELECT wrk.*, xnat_experimentdata.label, " +
            "xnat_experimentdata.date + xnat_experimentdata.time AS item_time, meta.last_modified FROM " +
            "(SELECT * FROM wrk_workflowData WHERE id = :id OR " +
            "id IN (SELECT DISTINCT id FROM (SELECT iad.id FROM xnat_imageassessordata iad " +
            "WHERE iad.id IS NOT NULL AND iad.imagesession_id=:id UNION " +
            "SELECT iad.id FROM xnat_imageassessordata_history iad " +
            "WHERE iad.id IS NOT NULL AND iad.imagesession_id=:id) AS idq)) as wrk INNER JOIN " +
            "xnat_experimentdata ON wrk.id=xnat_experimentdata.id LEFT JOIN wrk_workflowdata_meta_data meta " +
            "ON wrk.workflowData_info=meta.meta_data_id";


    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
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
     * @param request   the request object
     * @return list of model
     * @throws DataAccessException for issues accessing data
     * @throws Exception for issues retrieving xnat data types
     */
    public List<Workflow> getWorkflows(String id, String dataType, UserI user,
                                       PageRequest request) throws Exception {

        MapSqlParameterSource namedParams = new MapSqlParameterSource();

        if (StringUtils.isNotBlank(id)) {
            namedParams.addValue("id", id);
        }

        String query;
        switch(dataType) {
            case "xdat:user":
                namedParams.addValue("userId", user.getID())
                        .addValue("username", user.getLogin());
                // Pipelines user has launched and pipelines associated with projects user can read
                query = "WITH subq AS (SELECT wrkSub.*, meta.last_modified, meta.insert_user_xdat_user_id FROM (" +
                        buildQueryWithDataTypeLabels() + ") AS wrkSub LEFT JOIN wrk_workflowdata_meta_data meta ON " +
                        "wrkSub.workflowData_info = meta.meta_data_id ) " +
                        "SELECT subq.* FROM subq WHERE insert_user_xdat_user_id = :userId OR create_user = :username " +
                        "UNION " +
                        "SELECT subq.* FROM subq INNER JOIN (SELECT * FROM (SELECT * FROM xdat_user_groupid gid WHERE " +
                        "groups_groupid_xdat_user_xdat_user_id = :userId) AS usrgrp " +
                        "INNER JOIN xdat_usergroup ug ON usrgrp.groupid = ug.id) AS userq " +
                        "ON userq.tag = subq.externalid OR userq.tag = subq.id";
                break;
            case XnatProjectdata.SCHEMA_ELEMENT_NAME:
                namedParams.addValue("arcId",
                        XnatProjectdata.getXnatProjectdatasById(id, user, false)
                                .getArcSpecification().getId());
                query = "SELECT wrkSub.*, meta.last_modified FROM (" +
                        buildQueryWithDataTypeLabels(QUERY_PROJECT_WFS, "externalid = :id") + ") AS wrkSub " +
                        "LEFT JOIN wrk_workflowdata_meta_data meta ON wrkSub.workflowData_info = meta.meta_data_id";
                break;
            case XnatSubjectdata.SCHEMA_ELEMENT_NAME:
                query = QUERY_SUBJECT_WFS;
                break;
            default:
                query = QUERY_EXPT_WFS;
                break;
        }
        query = "SELECT * FROM (" + query + ") AS q"; //Allow for WHERE in query suffix
        query += request.getQuerySuffix(namedParams);

        List<Workflow> wfs = jdbcTemplate.query(query, namedParams, WF_ROW_MAPPER);

        // Estimate % complete if not provided
        updateWorkflowProgress(wfs);
        return wfs;
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
        for (String is : inactiveStatuses) {
            if (status.contains(is)) {
                return false;
            }
        }
        return true;
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
     * Get Worflow model object from PersistentWorkflowI object
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
     * @param initialQuery intial query onto which the unions will be appended
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

        String constraints = "";
        if (addlConstraints != null && addlConstraints.length > 0) {
            constraints = " AND " + StringUtils.join(addlConstraints," AND ");
        }

        List<String> dataTypes = jdbcTemplate.query("SELECT DISTINCT(data_type) FROM wrk_workflowData",
                new RowMapper<String>() {
                    @Override
                    public String mapRow(ResultSet resultSet, int rowNum) throws SQLException {
                        return resultSet.getString("data_type");
                    }
                });
        String experimentTable = SchemaElement.GetElement(XnatExperimentdata.SCHEMA_ELEMENT_NAME).getSQLName();
        String experimentDateStr = ", xnat_experimentdata.date + xnat_experimentdata.time AS item_time";
        for (int i = 0; i < dataTypes.size(); i++) {
            String type = dataTypes.get(i);
            String tableName = null;
            String timeStr = ", NULL::timestamp AS item_time";
            String outname = "wrk" + i;

            qb.append(unionStr);

            switch(type) {
                case XnatProjectdata.SCHEMA_ELEMENT_NAME:
                    qb.append(" SELECT " + outname + ".*, " + outname + ".id AS label " + timeStr + " FROM wrk_workflowData " +
                            outname + " WHERE data_type = '" + type + "' " + constraints);
                    break;
                case XnatSubjectdata.SCHEMA_ELEMENT_NAME:
                    tableName = SchemaElement.GetElement(type).getSQLName();
                    break;
                default:
                    tableName = experimentTable;
                    timeStr = experimentDateStr;
                    break;
            }
            if (tableName != null) {
                qb.append(" SELECT " + outname + ".*, " + tableName + ".label" + timeStr + " FROM " +
                        "(SELECT * FROM wrk_workflowData WHERE data_type = '" + type + "' " + constraints + ") " +
                        "AS " + outname + " INNER JOIN " + tableName + " ON " + outname + ".id=" + tableName + ".id");
            }
            unionStr = " UNION";
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

}