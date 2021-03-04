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
