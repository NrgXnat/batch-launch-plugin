package org.nrg.xnat.bulk.xapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.nrg.xnat.bulk.exceptions.FilterException;
import org.nrg.xnat.bulk.model.WorkflowFilter;
import org.nrg.xnat.bulk.repositories.PageableRepository;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
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
     * @return query suffix
     * @throws FilterException if filter parameters are invalid
     */
    public String getQuerySuffix() throws FilterException {
        StringBuilder suffix = new StringBuilder();

        //add filter
        if (filtersMap != null) {
            addFilterSuffix(suffix);
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
     * @throws FilterException if filter parameters are invalid
     */
    private void addFilterSuffix(StringBuilder suffix) throws FilterException {
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
                suffix.append(filter.constructQueryString(column));
            } else {
                log.debug("Skipping filter on column {}, which is not allowed", column);
            }
        }
    }
}
