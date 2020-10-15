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
    private Date    itemTime;
    private String  externalId;
    private String  pipelineName;
    private String  dataType;
    private String  comments;
    private String  details;
    private String  justification;
    private String  description;
    private String  src;
    private String  type;
    private String  category;
    private Date    currentStepLaunchTime;
    private Date    launchTime;
    private String  currentStepId;
    private String  status;
    private String  createUser;
    private String  nextStepId;
    private String  stepDescription;
    private String  percentageComplete;
    private String  jobId;
    private Date    modTime;

    public Workflow() {};
    public Workflow(Integer wfid, String id, String label, Date itemTime, String externalId, String pipelineName, String dataType, String comments,
                    String details, String justification, String description, String src, String type, String category,
                    Date currentStepLaunchTime, Date launchTime, String currentStepId, String status, String createUser,
                    String nextStepId, String stepDescription, String percentageComplete, String jobId, Date modTime) {
        this.wfid = wfid;
        this.id = id;
        this.label = label;
        this.itemTime = itemTime;
        this.externalId = externalId;
        this.pipelineName = pipelineName;
        this.dataType = dataType;
        this.comments = comments;
        this.details = details;
        this.justification = justification;
        this.description = description;
        this.src = src;
        this.type = type;
        this.category = category;
        this.currentStepLaunchTime = currentStepLaunchTime;
        this.launchTime = launchTime;
        this.currentStepId = currentStepId;
        this.status = status;
        this.createUser = createUser;
        this.nextStepId = nextStepId;
        this.stepDescription = stepDescription;
        this.percentageComplete = percentageComplete;
        this.jobId = jobId;
        this.modTime = modTime;
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

    @ApiModelProperty(value = "The workflow element time")
    public Date getItemTime() {
        return itemTime;
    }
    public void setItemTime(Date itemTime) {
        this.itemTime = itemTime;
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

    @ApiModelProperty(value = "The workflow description")
    public String getDescription() {
        return description;
    }
    public void setDescription(final String description) {
        this.description = description;
    }

    @ApiModelProperty(value = "The workflow src")
    public String getSrc() {
        return src;
    }
    public void setSrc(final String src) {
        this.src = src;
    }

    @ApiModelProperty(value = "The workflow type")
    public String getType() {
        return type;
    }
    public void setType(final String type) {
        this.type = type;
    }

    @ApiModelProperty(value = "The workflow category")
    public String getCategory() {
        return category;
    }
    public void setCategory(final String category) {
        this.category = category;
    }

    @ApiModelProperty(value = "The workflow currentStepLaunchTime")
    public Date getCurrentStepLaunchTime() {
        return currentStepLaunchTime;
    }
    public void setCurrentStepLaunchTime(final Date currentStepLaunchTime) {
        this.currentStepLaunchTime = currentStepLaunchTime;
    }

    @ApiModelProperty(value = "The workflow launchTime")
    public Date getLaunchTime() {
        return launchTime;
    }
    public void setLaunchTime(final Date launchTime) {
        this.launchTime = launchTime;
    }

    @ApiModelProperty(value = "The workflow currentStepId")
    public String getCurrentStepId() {
        return currentStepId;
    }
    public void setCurrentStepId(final String currentStepId) {
        this.currentStepId = currentStepId;
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

    @ApiModelProperty(value = "The workflow nextStepId")
    public String getNextStepId() {
        return nextStepId;
    }
    public void setNextStepId(final String nextStepId) {
        this.nextStepId = nextStepId;
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

    @ApiModelProperty(value = "The workflow jobId")
    public String getJobId() {
        return jobId;
    }
    public void setJobId(final String jobId) {
        this.jobId = jobId;
    }

    @ApiModelProperty(value = "The workflow last modification")
    public Date getModTime() {
        return modTime;
    }
    public void setModTime(Date modTime) {
        this.modTime = modTime;
    }
}
