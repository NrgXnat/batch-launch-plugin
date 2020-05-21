// Copyright 2019 Radiologics, Inc
// Developer: Kate Alpert <kate@radiologics.com>

package org.nrg.xnatx.plugins.batch.repositories;

import java.util.Map;
import java.util.Set;

public interface PageableRepository {
    class ColumnDataType {
        public String columnName;
        public Class dataType;
        ColumnDataType(String columnName, Class dataType) {
            this.columnName = columnName;
            this.dataType = dataType;
        }
    }
    Set<String> getAllowableSortColumns();
    Set<String> getAllowableFilterColumns();

    Map<String,ColumnDataType> getColumnMapping();
}
