package org.nrg.xnat.bulk.repositories;

import com.google.common.collect.ImmutableMap;
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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
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
            PersistentWorkflowUtils.FAILED, PersistentWorkflowUtils.QUEUED);

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

    private static final List<String> ALLOWABLE_SORT_COLUMNS = Arrays.asList("id", "externalid", "launch_time",
            "pipeline_name", "percentagecomplete", "status");
    private static final List<String> ALLOWABLE_FILTER_COLUMNS = Arrays.asList("id", "externalid", "launch_time",
            "pipeline_name", "status");
    private static final Map<String, ColumnDataType> COLUMN_INFO = ImmutableMap.<String, ColumnDataType>builder()
            .put("wrk_workflowdata_id", new ColumnDataType("wfid", int.class))
            .put("id", new ColumnDataType("id", String.class))
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

    // Pipelines user has launched
    public static final String QUERY_USER_LAUNCH = "SELECT wrk.* FROM wrk_workflowData wrk " +
            "LEFT JOIN wrk_workflowdata_meta_data meta ON wrk.workflowData_info = meta.meta_data_id " +
            "WHERE meta.insert_user_xdat_user_id = :userId OR wrk.create_user = :username";

    // Pipelines associated with project user can read
    public static final String QUERY_USER_READ = "SELECT wrk.* FROM wrk_workflowData wrk INNER JOIN xnat_projectdata proj " +
            "ON (wrk.externalId = proj.id OR wrk.externalId = proj.id) INNER JOIN xdat_usergroup ug ON (ug.tag = proj.id) " +
            "INNER JOIN xdat_user_groupid gid ON (gid.xdat_user_groupid_id = ug.xdat_usergroup_id) INNER JOIN " +
            "xdat_user u ON (u.xdat_user_id = gid.groups_groupid_xdat_user_xdat_user_id) WHERE u.xdat_user_id = :userId";

    public static final String QUERY_USER_WFS = QUERY_USER_LAUNCH + " UNION " + QUERY_USER_READ;

    // Pipelines for project or for entries within project (experiments, subjects, etc)
    public static final String QUERY_PROJECT_WFS = "SELECT * FROM wrk_workflowData WHERE " +
            "id = :id OR externalid = :id OR " +
            "(id = :arcId AND data_type = 'arc:project')";

    // SQL from WorkflowBasedHistoryBuilder
    // Pipelines on subject
    public static final String QUERY_SUBJECT_WFS = "SELECT * FROM wrk_workflowData WHERE id = :id OR " +
            "id IN (SELECT DISTINCT id FROM (SELECT sad.id FROM xnat_subjectassessordata sad " +
            "WHERE subject_id=:id UNION " +
            "SELECT iad.id FROM xnat_subjectassessordata sad LEFT JOIN xnat_imageassessordata iad " +
            "ON sad.id=iad.imagesession_id WHERE iad.id IS NOT NULL AND subject_id=:id UNION " +
            "SELECT sad.id FROM xnat_subjectassessordata_history sad WHERE subject_id=:id UNION " +
            "SELECT iad.id FROM xnat_subjectassessordata sad LEFT JOIN xnat_imageassessordata_history iad " +
            "ON sad.id=iad.imagesession_id WHERE iad.id IS NOT NULL AND subject_id=:id UNION " +
            "SELECT iad.id FROM xnat_subjectassessordata_history sad LEFT JOIN xnat_imageassessordata_history iad " +
            "ON sad.id=iad.imagesession_id WHERE iad.id IS NOT NULL AND subject_id=:id) AS idq)";

    // Pipelines on experiment
    public static final String QUERY_EXPT_WFS = "SELECT * FROM wrk_workflowData WHERE id = :id OR " +
            "id IN (SELECT DISTINCT id FROM (SELECT iad.id FROM xnat_imageassessordata iad " +
            "WHERE iad.id IS NOT NULL AND iad.imagesession_id=:id UNION " +
            "SELECT iad.id FROM xnat_imageassessordata_history iad " +
            "WHERE iad.id IS NOT NULL AND iad.imagesession_id=:id) AS idq)";


    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    public WorkflowRepository(final NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> getAllowableSortColumns() {
        return ALLOWABLE_SORT_COLUMNS;
    }
    public List<String> getAllowableFilterColumns() {
        return ALLOWABLE_FILTER_COLUMNS;
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
     * @throws DataAccessException
     */
    public List<Workflow> getWorkflows(String id, String dataType, UserI user,
                                       PageRequest request) throws DataAccessException {

        MapSqlParameterSource namedParams = new MapSqlParameterSource().addValue("id", id);

        String query;
        switch(dataType) {
            case "xdat:user":
                namedParams.addValue("userId", user.getID())
                        .addValue("username", user.getLogin());
                query = QUERY_USER_WFS;
                break;
            case "xnat:projectData":
                namedParams.addValue("arcId",
                        XnatProjectdata.getXnatProjectdatasById(id, user, false)
                                .getArcSpecification().getId());
                query = QUERY_PROJECT_WFS;
                break;
            case "xnat:subjectData":
                query = QUERY_SUBJECT_WFS;
                break;
            default:
                query = QUERY_EXPT_WFS;
                break;
        }
        query = "SELECT * FROM (" + query + ") AS q"; //Allow for WHERE in query suffix
        query += request.getQuerySuffix();

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
     * @return      Worflow model object
     */
    public Workflow getWorkflow(PersistentWorkflowI wrk) {
        Date cslt = (wrk.getCurrentStepLaunchTime() instanceof Date) ? (Date) wrk.getCurrentStepLaunchTime() : null;
        Workflow wf = new Workflow(wrk.getWorkflowId(), wrk.getId(), wrk.getExternalid(), wrk.getPipelineName(), wrk.getDataType(),
                wrk.getComments(), wrk.getDetails(), wrk.getJustification(), null, null, wrk.getType(),
                wrk.getCategory(), cslt, wrk.getLaunchTimeDate(), wrk.getCurrentStepId(), wrk.getStatus(),
                wrk.getCreateUser(), null, wrk.getStepDescription(), wrk.getPercentagecomplete(), null
        );
        // Estimate % complete if not provided
        updateWorkflowProgress(wf);
        return wf;
    }
}