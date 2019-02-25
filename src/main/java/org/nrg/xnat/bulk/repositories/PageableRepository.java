package org.nrg.xnat.bulk.repositories;

import java.util.List;
import java.util.Map;

public interface PageableRepository {
    class ColumnDataType {
        public String columnName;
        public Class dataType;
        ColumnDataType(String columnName, Class dataType) {
            this.columnName = columnName;
            this.dataType = dataType;
        }
    }
    List<String> getAllowableSortColumns();
    List<String> getAllowableFilterColumns();

    Map<String,ColumnDataType> getColumnMapping();
}
