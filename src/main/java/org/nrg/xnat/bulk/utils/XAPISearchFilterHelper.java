package org.nrg.xnat.bulk.utils;

import org.apache.commons.lang3.StringUtils;

public class XAPISearchFilterHelper {
    public static String constructQueryFilter(Class columnClass, String column, String filterParam) {
        switch (columnClass.getSimpleName()) {
            case "String":
                return String.format("%s ILIKE '%%%s%%'", column, sanitizeFilterString(filterParam));
            case "Timestamp":
                String filters = "";
                String[] datePrms = filterParam.split("THRU");
                if (StringUtils.isNotBlank(datePrms[0])) {
                    filters = column + " >= '"+sanitizeFilterDate(datePrms[0]) + "'";
                }
                if (StringUtils.isNotBlank(datePrms[1])) {
                    filters += StringUtils.isNotBlank(filters) ? " AND " : "";
                    filters += column + " <= '"+sanitizeFilterDate(datePrms[1]) + "'";
                }
                return filters;
        }
        return "";
    }

    private static String sanitizeFilterString(String uiValue) {
        return uiValue.replaceAll("[^A-Za-z0-9_.\\-]", "");
    }
    private static String sanitizeFilterDate(String uiValue) {
        return uiValue.replaceAll("[^0-9.: +\\-]", "");
    }
}
