// Copyright 2019 Radiologics, Inc
// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnat.bulk.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.nrg.xnat.bulk.exceptions.FilterException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @Type(value = StringWorkflowFilter.class, name = "string"),
        @Type(value = TimestampWorkflowFilter.class, name = "datetime"),
        @Type(value = NumericWorkflowFilter.class, name = "number")
})
public abstract class WorkflowFilter {
    /**
     * Construct SQL query string from column name and parameters
     * @param dbColumnName column name
     * @param namedParams parameters
     * @return the SQL query
     * @throws FilterException if parameters clash
     */
    public abstract String constructQueryString(String dbColumnName, MapSqlParameterSource namedParams)
            throws FilterException;

    /**
     * Validate UI filter input
     * @param uiValue the value
     * @throws FilterException if UI value isn't permitted
     */
    abstract void validate(String uiValue) throws FilterException;
}

