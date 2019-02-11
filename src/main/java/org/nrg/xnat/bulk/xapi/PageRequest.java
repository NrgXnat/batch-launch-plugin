package org.nrg.xnat.bulk.xapi;

import org.nrg.xnat.bulk.repositories.PageableRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PageRequest {
    private final List<String> ALLOWABLE_SORT_DIRECTIONS = Arrays.asList("asc", "desc", "ASC", "DESC");
    private int offset;
    private int limit;
    private String sortColumn;
    private String sortDir;
    private Map<String, String> filterMap;
    private PageableRepository repo;

    public PageRequest(PageableRepository repo, String sortColumn, String sortDir,
                       Map<String, String> filterMap,
                       Integer page, Integer size) {
        this.repo = repo;
        this.sortColumn = getFilterColumnFromMapping(sortColumn);
        this.sortDir = sortDir;
        this.filterMap = filterMap;

        this.limit = (size != null && size > 0) ? size : 0;
        this.offset = (page != null && page > 1) ? (page - 1) * limit : 0;
    }

    public String getFilterColumnFromMapping(String uiColumn) {
        Map<String, PageableRepository.ColumnDataType> repoMapping = repo.getColumnMapping();
        for (String key : repoMapping.keySet()) {
            if (repoMapping.get(key).columnName.equals(uiColumn)) {
                return key;
            }
        }
        return null;
    }

    public String sanitizeFilterString(String uiValue) {
        return uiValue.replaceAll("[^A-Za-z0-9_.\\-]", "");
    }

    public String getQuerySuffix() {
        StringBuilder suffix = new StringBuilder();

        //add filter
        if (filterMap != null) {
            boolean needsWhere = true;
            for (String key : filterMap.keySet()) {
                // From UI to DB column name
                String column = getFilterColumnFromMapping(key);
                // Allowed to filter?
                if (repo.getAllowableFilterColumns().contains(column)) {
                    if (needsWhere) {
                        suffix.append(" WHERE ");
                        needsWhere = false;
                    } else {
                        suffix.append(" AND ");
                    }
                    suffix.append(String.format("%s ILIKE '%%%s%%'", column, sanitizeFilterString(filterMap.get(key))));
                }
            }
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
}
