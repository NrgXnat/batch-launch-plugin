// Copyright 2019 Radiologics, Inc
// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnat.bulk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.nrg.xnat.bulk.exceptions.FilterException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

public class StringWorkflowFilter extends WorkflowFilter {
    @JsonIgnore private final static Pattern validRegex = Pattern.compile("^[A-Za-z0-9_.\\- ]+$");
    @JsonProperty private String like;
    @Nullable @JsonProperty private Boolean not;

    @Override
    @JsonIgnore
    public String constructQueryString(String dbColumnName, MapSqlParameterSource namedParams) throws FilterException {
        String notStr = (not == null || !not) ? "" : " NOT";
        validate(like);
        namedParams.addValue(dbColumnName + "str", "%" + like + "%");
        return dbColumnName + notStr + " ILIKE :" + dbColumnName + "str";
    }

    @Override
    @JsonIgnore
    void validate(String uiValue) throws FilterException{
        if (!validRegex.matcher(uiValue).matches()) {
            throw new FilterException("Invalid string filter parameter: " + uiValue);
        }
    }

    public String getLike() {
        return like;
    }

    public void setLike(String like) {
        this.like = like;
    }

    public Boolean getNot() {
        return not;
    }

    public void setNot(Boolean not) {
        this.not = not;
    }
}