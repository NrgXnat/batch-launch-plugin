package org.nrg.xnat.bulk.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApiModel(description = "Contains the properties that define a workflow on the system.")
public class WorkflowDuration {
    private String pipelineName;
    private long duration;

    public WorkflowDuration() {};
    public WorkflowDuration(String pipelineName, Long duration) {
        this.pipelineName = pipelineName;
        this.duration = (duration == null) ? 0 : duration;
    }

    @ApiModelProperty(value = "The workflow pipelineName")
    public String getPipelineName() {
        return pipelineName;
    }
    public void setPipelineName(final String pipelineName) {
        this.pipelineName = pipelineName;
    }

    @ApiModelProperty(value = "The workflow dataType")
    public long getDuration() {
        return duration;
    }
    public void setDuration(final long duration) {
        this.duration = duration;
    }
}
