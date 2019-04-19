package org.nrg.xnat.bulk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xnat.bulk.exceptions.FilterException;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class TimestampWorkflowFilter extends WorkflowFilter {
    @JsonIgnore private final static Pattern validRegex = Pattern.compile("^[0-9.: +\\-]+$");

    @Nullable @JsonProperty private String before;
    @Nullable @JsonProperty private String after;
    @Nullable @JsonProperty private String beforeOrOn;
    @Nullable @JsonProperty private String afterOrOn;

    @Override
    @JsonIgnore
    public String constructQueryString(String dbColumnName) throws FilterException {
        if (StringUtils.isNotBlank(before) && StringUtils.isNotBlank(beforeOrOn) ||
                StringUtils.isNotBlank(after) && StringUtils.isNotBlank(afterOrOn)) {
            throw new FilterException("Cannot have both * and *OrOn params");
        }
        List<String> filters = new ArrayList<>();
        if (StringUtils.isNotBlank(after)) {
            validate(after);
            filters.add(dbColumnName + " > '" + after + "'");
        }
        if (StringUtils.isNotBlank(afterOrOn)) {
            validate(afterOrOn);
            filters.add(dbColumnName + " >= '" + afterOrOn + "'");
        }
        if (StringUtils.isNotBlank(before)) {
            validate(before);
            filters.add(dbColumnName + " < '" + before + "'");
        }
        if (StringUtils.isNotBlank(beforeOrOn)) {
            validate(beforeOrOn);
            filters.add(dbColumnName + " <= '" + beforeOrOn + "'");
        }
        return StringUtils.join(filters, " AND ");
    }

    @Override
    @JsonIgnore
    void validate(String uiValue) throws FilterException{
        if (!validRegex.matcher(uiValue).matches()) {
            throw new FilterException("Invalid timestamp filter parameter: " + uiValue);
        }
    }

    public String getBefore() {
        return before;
    }

    public void setBefore(String before) {
        this.before = before;
    }

    public String getAfter() {
        return after;
    }

    public void setAfter(String after) {
        this.after = after;
    }

    public String getBeforeOrOn() {
        return beforeOrOn;
    }

    public void setBeforeOrOn(String beforeOrOn) {
        this.beforeOrOn = beforeOrOn;
    }

    public String getAfterOrOn() {
        return afterOrOn;
    }

    public void setAfterOrOn(String afterOrOn) {
        this.afterOrOn = afterOrOn;
    }
}