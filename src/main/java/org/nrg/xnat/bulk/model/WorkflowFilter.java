package org.nrg.xnat.bulk.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.nrg.xnat.bulk.exceptions.FilterException;

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
    public abstract String constructQueryString(String dbColumnName) throws FilterException;
    abstract void validate(String uiValue) throws FilterException;
}

