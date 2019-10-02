// Copyright 2019 Radiologics, Inc
// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnat.bulk.xapi;

import lombok.extern.slf4j.Slf4j;
import org.nrg.xnat.bulk.exceptions.FilterException;
import org.nrg.xnat.bulk.model.WorkflowFilter;
import org.nrg.xnat.bulk.repositories.PageableRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class PageRequest {
    private final List<String> ALLOWABLE_SORT_DIRECTIONS = Arrays.asList("asc", "desc", "ASC", "DESC");
    private int offset;
    private int limit;
    private String sortColumn;
    private String sortDir;
    private Map<String, WorkflowFilter> filtersMap;
    private PageableRepository repo;

    public PageRequest(PageableRepository repo,String sortColumn, String sortDir,
                       Map<String, WorkflowFilter> filtersMap, Integer page, Integer size) {
        this.repo = repo;
        this.filtersMap = filtersMap;
        this.sortColumn = getDbColumnFromMapping(sortColumn);
        this.sortDir = sortDir;
        this.limit = (size != null && size > 0) ? size : 0;
        this.offset = (page != null && page > 1) ? (page - 1) * limit : 0;
    }

    /**
     * Construct query suffix with filters, sorting, and limits
     * @param namedParams the named parameters object
     * @return query suffix
     * @throws FilterException if filter parameters are invalid
     */
    public String getQuerySuffix(MapSqlParameterSource namedParams) throws FilterException {
        StringBuilder suffix = new StringBuilder();

        //add filter
        if (filtersMap != null) {
            addFilterSuffix(suffix, namedParams);
        }

        //add sort
        if (repo.getAllowableSortColumns().contains(sortColumn)) {
            suffix.append(String.format(" ORDER BY %s ", sortColumn));
            if (ALLOWABLE_SORT_DIRECTIONS.contains(sortDir)) {
                suffix.append(sortDir);
            }
        }

        //add pagination
        if (limit > 0) {
            suffix.append(String.format(" LIMIT %d", limit));
        }
        if (offset > 0) {
            suffix.append(String.format(" OFFSET %d", offset));
        }

        return suffix.toString();
    }

    /**
     * Get database column name from Ui column name
     * @param uiColumn the ui column name
     * @return the db column name
     */
    private String getDbColumnFromMapping(String uiColumn) {
        Map<String, PageableRepository.ColumnDataType> repoMapping = repo.getColumnMapping();
        for (String key : repoMapping.keySet()) {
            if (repoMapping.get(key).columnName.equals(uiColumn)) {
                return key;
            }
        }
        return null;
    }

    /**
     * Append filters to query suffix
     * @param suffix string builder of query suffix
     * @param namedParams the named params object
     * @throws FilterException if filter parameters are invalid
     */
    private void addFilterSuffix(StringBuilder suffix, MapSqlParameterSource namedParams) throws FilterException {
        boolean needsWhere = true;
        for (String key : filtersMap.keySet()) {
            // From UI to DB column name
            String column = getDbColumnFromMapping(key);
            // Allowed to filter?
            if (repo.getAllowableFilterColumns().contains(column)) {
                if (needsWhere) {
                    suffix.append(" WHERE ");
                    needsWhere = false;
                } else {
                    suffix.append(" AND ");
                }
                WorkflowFilter filter = filtersMap.get(key);
                suffix.append(filter.constructQueryString(column, namedParams));
            } else {
                log.debug("Skipping filter on column {}, which is not allowed", column);
            }
        }
    }
}
