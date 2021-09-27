// Copyright 2019 Radiologics, Inc
// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnatx.plugins.batch.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;
import java.util.Map;

public class WorkflowListingRequest {
    @JsonProperty(value = "data_type", required = true) String dataType;
    @Nullable @JsonProperty(value = "id") String id;
    @JsonProperty(value = "sort_col", defaultValue = "wfid") String sortColumn = "wfid";
    @JsonProperty(value = "sort_dir", defaultValue = "DESC") String sortDir = "DESC";
    @JsonProperty(value = "page", defaultValue = "1") int page = 1;
    @JsonProperty(value = "size", defaultValue = "50") int size = 50;
    @JsonProperty(value = "sortable", defaultValue = "false") boolean sortable = false;
    @JsonProperty(value = "days", defaultValue = "90") int days = 90;
    @Nullable @JsonProperty(value = "filters") Map<String, WorkflowFilter> filtersMap;

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    @Nullable
    public String getId() {
        return id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

    public String getSortColumn() {
        return sortColumn;
    }

    public void setSortColumn(String sortColumn) {
        this.sortColumn = sortColumn;
    }

    public String getSortDir() {
        return sortDir;
    }

    public void setSortDir(String sortDir) {
        this.sortDir = sortDir;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public int getDays() { return days;}

    public void setDays() { this.days=days;}

    public void setSize(int size) {
        this.size = size;
    }

    public boolean getSortable() {
        return sortable;
    }

    public void setSortable(boolean sortable) {
        this.sortable = sortable;
    }

    @Nullable
    public Map<String, WorkflowFilter> getFiltersMap() {
        return filtersMap;
    }

    public void setFiltersMap(@Nullable Map<String, WorkflowFilter> filtersMap) {
        this.filtersMap = filtersMap;
    }
}
