// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnatx.plugins.batch.workflows.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.nrg.framework.ajax.sql.SqlPaginatedRequest;

public class WorkflowPaginatedRequest extends SqlPaginatedRequest {
    @JsonProperty(value = "data_type", required = true) String dataType;

    @Override
    public String getDefaultSortColumn() {
        return "launchTime";
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }
}
