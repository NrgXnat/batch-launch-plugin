# XNAT Batch Launch Plugin #

This is the XNAT 1.7 Batch Launch plugin. Its purpose is to enable monitoring and launching jobs on a batch

This plugin was primarily developed for the Container Service to allow users to run commands on a batch.

# Building & Installing #

To build :

1. If you haven't already, clone this repository and cd to the newly cloned folder.
1. Build the plugin: `./gradlew jar` (on Windows, you can use the batch file: `gradlew.bat jar`). This should build the plugin in the file **build/libs/batch-launch-plugin-1.0.0.jar** (the version may differ based on updates to the code).
1. Copy the plugin jar to your plugins folder: `cp build/libs/batch-launch-plugin-1.0.0.jar /data/xnat/home/plugins`
1. Restart Tomcat and your plugin will become active in XNAT. 

# Documentation for querying workflow listings #

Sample query:
```
curl -X POST --header 'Content-Type: application/json' --header 'Accept: application/json' -d '{  \ 
   "data_type": "xdat:user",  \ 
   "filters": { \ 
     "label":{"type":"string", "like":"08"}, \ 
     "modTime":{"type":"datetime", "after":"2019-04-07"}, \ 
     "wfid": {"type":"number", "ge": 8623} \ 
   } \ 
 }' 'http://localhost:8080/xapi/workflows'
```

Sample JSON is available in swagger UI. Note that results are paginated, so you need to mind the page & size params.

Data type is the XNAT data type you wish to query. `"xdat:user"` means all workflows that the user has launched or for items within projects to which the user has at least read access, `"xnat:projectdata"` is all workflows for items in the project (specify project to `"id"` param), and `"xnat:mrsessiondata"` etc is all workflows for an mr session with matching `"id"` param (and same for other experiment data types).

Sort columns should match the keys returned in the JSON from the query & sort direction is ASC or DESC (case insensitive).

Filters names should match the keys returned in the JSON from the query. Be sure to include the type (needed for JSON deserialization, sadly): one of `"string"`, `"datetime"`, or `"number"`.

If `"type":"string"`: you can specify `"like":my_string` where `my_string` is the string you want to match on. Matching will be case insensitive and have wildcards at start and end (though i can make these options if needed). And you can specify `"not":boolean`, which is a boolean if you want to invert the search (not=true means anything that doesn’t match the `"like"` string will be returned), it defaults to false.

If `"type":"datetime"`: you can specify `"before":date`, `"after":date`, `"beforeOrOn":date`, and `"afterOrOn":date`, where `date` is a date-time string that postgresql can understand (i have some validation in addn to java’s SQL injection protection which can be loosened if needed, so let me know if date queries are behaving poorly). Any of these can be omitted, and you cannot use both `"after" and `"afterOrOn" (same for befores).

If `"type":"number"`, you can specify `"gt":9`, `"lt":10`, `"ge":9`, `"le":10` for greater than, less than, greater than or equal to, and less than or equal to. As with datetime, any of these can be omitted and you can’t use both `"gt"` and `"ge"` / `"le"` and `"lt"`.
