package org.nrg.xnatx.plugins.batch.utils;

import lombok.extern.slf4j.Slf4j;
import org.nrg.xdat.collections.DisplayFieldCollection;
import org.nrg.xdat.display.DisplayField;
import org.nrg.xdat.display.DisplayFieldElement;
import org.nrg.xdat.display.ElementDisplay;
import org.nrg.xdat.display.SQLQueryField;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.schema.SchemaElement;
import org.nrg.xdat.security.ElementSecurity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class DynamicAddSqlQueryFieldsToDataTypes {
    public static void addFields() {
        if (addedSQLQueryFieldToDataTypes) {
            return;
        }
        synchronized(DynamicAddSqlQueryFieldsToDataTypes.class){
            if (addedSQLQueryFieldToDataTypes) {
                return;
            }
            try {
                for (ElementSecurity es : ElementSecurity.GetSecureElements()) {
                    SchemaElement se = es.getSchemaElement();

                    if(se == null){
                        log.error("Registered data type is missing schema: "+ es.getElementName());
                        continue;
                    }

                    // We only care about experiments and scans
                    if (!(se.instanceOf(XnatExperimentdata.SCHEMA_ELEMENT_NAME) ||
                            se.instanceOf(XnatImagescandata.SCHEMA_ELEMENT_NAME))) {
                        continue;
                    }
                    // add display field queries to all configured experiments and scans
                    ElementDisplay ed = se.getDisplay();
                    if(ed != null) {
                        for (DisplayFieldInfo dfi : displayFieldQueries) {
                            addQueryField(dfi, ed, se);
                        }
                        // only add scan type count to image sessions
                        if (se.instanceOf(XnatImagesessiondata.SCHEMA_ELEMENT_NAME)) {
                            addQueryField(scanTypeCountQuery, ed, se, false);
                        }
                        // if no URI field, add one
                        if (ed.getDisplayField("URI") == null) {
                            addUriField(ed, se);
                        }
                    }else{
                        log.error("Registered data type is missing display document: "+ es.getElementName());
                    }
                }
                addedSQLQueryFieldToDataTypes = true;
            } catch (Exception e) {
                log.error("Unable to add query fields to datatypes", e);
            }
        }
    }

    private static void addUriField(ElementDisplay ed, SchemaElement se) {
        DisplayField df = new DisplayField(ed);
        df.setId("URI");
        df.setHeader("uri");
        df.setVisible(true);
        df.setSearchable(false);
        df.setDataType("string");
        df.setContent(se.instanceOf(XnatImagescandata.SCHEMA_ELEMENT_NAME) ? URI_SQL_SCAN : URI_SQL_EXPT);

        DisplayFieldElement dfe = new DisplayFieldElement();
        dfe.setName("Field1");
        dfe.setSchemaElementName(se.getFullXMLName() + ".ID");
        df.addDisplayFieldElement(dfe);

        if (se.instanceOf(XnatImagescandata.SCHEMA_ELEMENT_NAME)) {
            DisplayFieldElement dfe2 = new DisplayFieldElement();
            dfe2.setName("Field2");
            dfe2.setSchemaElementName(se.getFullXMLName() + ".image_session_id");
            df.addDisplayFieldElement(dfe2);
        }

        try {
            ed.addDisplayFieldWException(df);
        } catch (DisplayFieldCollection.DuplicateDisplayFieldException e) {
            log.error(df.getParentDisplay().getElementName() + "." + df.getId());
            log.error("", e);
        }
    }

    private static void addQueryField(DisplayFieldInfo dfi, ElementDisplay ed, SchemaElement se) {
        addQueryField(dfi, ed, se, true);
    }

    private static void addQueryField(DisplayFieldInfo dfi, ElementDisplay ed, SchemaElement se, boolean doReplacement) {
        SQLQueryField sqf = new SQLQueryField(ed);
        sqf.setId(dfi.id);
        sqf.setHeader(dfi.id);
        sqf.setVisible(true);
        sqf.setSearchable(false);
        sqf.setDataType(dfi.dataType);
        Map<String, String> content = new HashMap<>();
        content.put("sql", dfi.column);
        sqf.setContent(content);
        if (se.instanceOf(XnatImagescandata.SCHEMA_ELEMENT_NAME)) {
            sqf.setSubQuery(doReplacement ? String.format(dfi.query, dfi.scanReplacementFields) : dfi.query);
            sqf.addMappingColumn( se.getFullXMLName() + ".xnat_imagescandata_id", dfi.mappingColumn);
        } else {
            sqf.setSubQuery(doReplacement ? String.format(dfi.query, dfi.exptReplacementFields) : dfi.query);
            sqf.addMappingColumn(se.getFullXMLName() + ".id", dfi.mappingColumn);
        }

        try {
            ed.addDisplayFieldWException(sqf);
        } catch (DisplayFieldCollection.DuplicateDisplayFieldException e) {
            log.error(sqf.getParentDisplay().getElementName() + "." + sqf.getId());
            log.error("", e);
        }
    }

    private static class DisplayFieldInfo {
        public String id;
        public String dataType;
        public String column;
        public String mappingColumn;
        public String query;
        public Object[] scanReplacementFields;
        public Object[] exptReplacementFields;

        public DisplayFieldInfo(String id, String dataType, String column) {
            this(id, dataType, column, "id",
                    "SELECT item.%s AS id, label, " + column + " FROM xnat_abstractResource abst " +
                            "LEFT JOIN %s item ON abst.%s = item.%s WHERE label='@WHERE'",
                    true);
        }

        public DisplayFieldInfo(String id, String dataType, String column, String mappingColumn, String query) {
            this(id, dataType, column, mappingColumn, query, false);
        }

        public DisplayFieldInfo(String id, String dataType, String column, String mappingColumn, String query, boolean resourceRelated) {
            this.id = id;
            this.dataType = dataType;
            this.column = column;
            this.mappingColumn = mappingColumn;
            this.query = query;
            if (resourceRelated) {
                this.scanReplacementFields = new String[]{
                        "xnat_imagescandata_id",
                        "xnat_imageScanData",
                        "xnat_imagescandata_xnat_imagescandata_id",
                        "xnat_imagescandata_id"
                };
                this.exptReplacementFields = new String[]{
                        "xnat_experimentdata_id",
                        "xnat_experimentdata_resource",
                        "xnat_abstractresource_id",
                        "xnat_abstractresource_xnat_abstractresource_id"
                };
            } else {
                this.scanReplacementFields = new String[] {"w.src::int"};
                this.exptReplacementFields = new String[] {"w.id"};
            }
        }
    }

    private static final String VIEW_SQL = "select distinct on (itemId) %s as itemId, w.externalid, w.wrk_workflowdata_id, w.pipeline_name, w.status, w.status || '#' || w.wrk_workflowdata_id AS widstatus, to_char(w.launch_time,'YYYY-MM-DD HH24:MI:SS') AS launch_time, CASE WHEN w.justification='Container launch' THEN w.comments ELSE NULL END AS container_id from wrk_workflowdata w where replace(replace(w.pipeline_name, '.', '_'),' ','_')='@WHERE' order by itemId, launch_time desc";

    private static boolean addedSQLQueryFieldToDataTypes = false;
    private static final DisplayFieldInfo scanTypeCountQuery = new DisplayFieldInfo("SCAN_TYPE_COUNT", "integer", "cnt", "image_session_id", "SELECT image_session_id, COUNT(*) AS file_count FROM xnat_imageScanData scan WHERE TYPE='@WHERE' GROUP BY image_session_id");
    private static final List<DisplayFieldInfo> displayFieldQueries = Arrays.asList(
            new DisplayFieldInfo("WRK_STATUS", "string", "widstatus", "itemId",
                    VIEW_SQL),
            new DisplayFieldInfo("WRK_STATUS_LAUNCH", "date", "launch_time", "itemId",
                    VIEW_SQL),
            new DisplayFieldInfo("WRK_STATUS_LASTMOD", "date", "last_modified", "itemId",
                    "select distinct on (itemId) %s as itemId, w.externalid, w.wrk_workflowdata_id, w.pipeline_name, to_char(meta.last_modified,'YYYY-MM-DD HH24:MI:SS') AS last_modified FROM wrk_workflowdata w LEFT JOIN wrk_workflowdata_meta_data meta ON w.workflowData_info=meta.meta_data_id where replace(replace(w.pipeline_name, '.', '_'),' ','_')='@WHERE' order by itemId, launch_time desc"),
            new DisplayFieldInfo("WRK_STATUS_NUMRUNS", "integer", "NUM_WRKS", "itemId",
                    "select distinct on (itemId) %s as itemId, COUNT(*) AS NUM_WRKS from wrk_workflowdata w where replace(replace(w.pipeline_name, '.', '_'),' ','_')='@WHERE' GROUP BY itemId"),
            new DisplayFieldInfo("WRK_STATUS_CID", "string", "container_id", "itemId",
                    VIEW_SQL),
            new DisplayFieldInfo("RES_FILE_SIZE", "integer", "file_size"),
            new DisplayFieldInfo("RES_FILE_COUNT", "integer", "file_count")
    );

    private static final Map<String, String> URI_SQL_EXPT = new HashMap<>();
    static {
        URI_SQL_EXPT.put("sql", "'/archive/experiments/' || @Field1");
    };

    private static final Map<String, String> URI_SQL_SCAN = new HashMap<>();
    static {
        URI_SQL_SCAN.put("sql", "'/archive/experiments/' || @Field2 || '/scans/' || @Field1");
    };
}
