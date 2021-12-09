// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnatx.plugins.batch.workflows.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.nrg.framework.ajax.sql.SqlPaginatedRequest;

public class WorkflowPaginatedRequest extends SqlPaginatedRequest {
    @JsonProperty(value = "data_type", required = true) String dataType;
    @JsonProperty(value = "sortable", defaultValue = "false") boolean sortable = false;
    @JsonProperty(value = "days", defaultValue = "90") int days = 90;
    @JsonProperty(value = "admin_workflows", defaultValue = "false") boolean adminWorkflows = false;

    @Override
    public String getDefaultSortColumn() {
        return "wfid";
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public boolean getSortable() {
        return sortable;
    }

    public void setSortable(boolean sortable) {
        this.sortable = sortable;
    }

    public boolean isAdminWorkflows() {
        return adminWorkflows;
    }

    public void setAdminWorkflows(boolean adminWorkflows) {
        this.adminWorkflows = adminWorkflows;
    }
}
