# XNAT Batch Launch Plugin #

This is the XNAT 1.7 Batch Launch plugin. Its purpose is to enable monitoring and launching jobs on a batch

This plugin was primarily developed for the Container Service to allow users to run commands on a batch.

# Changelog #

** October 11, 2019 **

1. The Processing Dashboard is now capable of launching traditional XNAT pipelines. 

2. The pipelines available with the site for a datatype are available to select and launch. If the pipeline is configured for the pipeline, 
the configured values of the  parameters are rendered. If not, values in the pipeline XML are rendered. All the schema link parameters are resolved behind the scene and not exposed. 
The bulk launcher does not work if there are any parameter values which are not identical across sessions.

3. The pipelines can be launched across projects (if sessions are selected via a search)

4. A new shell script in PIPELINE_HOME/bin/killxnatpipeline is required for the button Terminate Job when a pipeline is to be terminated. The following parameters are passed to this script:

-id <XNAT_ACCESSION_ID> -host <XNAT_PIPELINE_URL> -u <ALIAS_TOKEN> -pwd <TOKEN_PASSWORD> -workflowId <WORKFLOW_ID_OF_WORKFLOW_TO_BE_TERMINATED> -jobId <JOB_ID_IF_ANY_FROM_WORKKFLOWID> -pipelinePath <ABSOLUTE_PATH_TO_PIPELINE> -project <PROJECT_ID> -label <XNAT_LABEL>

5. If the sessions are shared into a project, the bulk launcher does not work on the shared project (as of this changelog note).

6. Dependent changes - xnat-data-models (version 1.7.6.RAD-SNAPSHOT-PIPELINE), xnat-pipeline-engine, xnatopen-web (1.7.6.RAD-SNAPSHOT)

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
