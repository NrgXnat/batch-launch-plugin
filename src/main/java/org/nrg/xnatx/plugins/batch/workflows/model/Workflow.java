// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnatx.plugins.batch.workflows.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Date;

@Slf4j
@ApiModel(description = "Contains the properties that define a workflow on the system.")
public class Workflow {
    private Integer wfid;
    private String  id;
    private String  label;
    private String  externalId;
    private String  pipelineName;
    private String  dataType;
    private String  comments;
    private String  details;
    private String  justification;
    private Date    launchTime;
    private String  status;
    private String  createUser;
    private String  stepDescription;
    private String  percentageComplete;
    private Date    modTime;

    public Workflow() {};
    public Workflow(Integer wfid, String id, String label, String externalId, String pipelineName, String dataType,
                    String comments, String details, String justification, Date launchTime, String status,
                    String stepDescription, String percentageComplete, Date modTime, String createUser) {
        this.wfid = wfid;
        this.id = id;
        this.label = label;
        this.externalId = externalId;
        this.pipelineName = pipelineName;
        this.dataType = dataType;
        this.comments = comments;
        this.details = details;
        this.justification = justification;
        this.launchTime = launchTime;
        this.status = status;
        this.stepDescription = stepDescription;
        this.percentageComplete = percentageComplete;
        this.modTime = modTime;
        this.createUser = createUser;
    }

    public void setProperty(String propertyName, Object propertyValue, Class propertyClass) {
        Method method;
        String methodName = propertyName.length() == 0 ? propertyName : "set" +
                propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
        try {
            method = this.getClass().getMethod(methodName, propertyClass);
            method.invoke(this, propertyValue);
        } catch (Exception e) {
            log.error("Cannot find or invoke method " + methodName);
        }
    }

    @ApiModelProperty(value = "The workflow wfid")
    public int getWfid() {
        return wfid;
    }
    public void setWfid(final int wfid) {
        this.wfid = wfid;
    }

    @ApiModelProperty(value = "The workflow element id")
    public String getId() {
        return id;
    }
    public void setId(final String id) {
        this.id = id;
    }

    @ApiModelProperty(value = "The workflow element label")
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }

    @ApiModelProperty(value = "The workflow externalId")
    public String getExternalId() {
        return externalId;
    }
    public void setExternalId(final String externalId) {
        this.externalId = externalId;
    }

    @ApiModelProperty(value = "The workflow pipelineName")
    public String getPipelineName() {
        return pipelineName;
    }
    public void setPipelineName(final String pipelineName) {
        this.pipelineName = pipelineName;
    }

    @ApiModelProperty(value = "The workflow dataType")
    public String getDataType() {
        return dataType;
    }
    public void setDataType(final String dataType) {
        this.dataType = dataType;
    }

    @ApiModelProperty(value = "The workflow comments")
    public String getComments() {
        return comments;
    }
    public void setComments(final String comments) {
        this.comments = comments;
    }

    @ApiModelProperty(value = "The workflow details")
    public String getDetails() {
        return details;
    }
    public void setDetails(final String details) {
        this.details = details;
    }

    @ApiModelProperty(value = "The workflow justification")
    public String getJustification() {
        return justification;
    }
    public void setJustification(final String justification) {
        this.justification = justification;
    }

    @ApiModelProperty(value = "The workflow launchTime")
    public Date getLaunchTime() {
        return launchTime;
    }
    public void setLaunchTime(final Date launchTime) {
        this.launchTime = launchTime;
    }

    @ApiModelProperty(value = "The workflow status")
    public String getStatus() {
        return status;
    }
    public void setStatus(final String status) {
        this.status = status;
    }

    @ApiModelProperty(value = "The workflow createUser")
    public String getCreateUser() {
        return createUser;
    }
    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    @ApiModelProperty(value = "The workflow stepDescription")
    public String getStepDescription() {
        return stepDescription;
    }
    public void setStepDescription(final String stepDescription) {
        this.stepDescription = stepDescription;
    }

    @ApiModelProperty(value = "The workflow percentageComplete")
    public String getPercentageComplete() {
        return percentageComplete;
    }
    public void setPercentageComplete(final String percentageComplete) {
        this.percentageComplete = percentageComplete;
    }

    @ApiModelProperty(value = "The workflow last modification")
    public Date getModTime() {
        return modTime;
    }
    public void setModTime(Date modTime) {
        this.modTime = modTime;
    }
}
