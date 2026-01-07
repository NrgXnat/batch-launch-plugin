# Changelog #
**Note:** The Batch Launch Plugin has version requirements with both XNAT and the Container Service plugin. Only minimum version compatibility requirements are listed in this document. See [Batch Launch Plugin Compatibility Matrix](https://wiki.xnat.org/xnat-tools/batch-launch-plugin-version-compatibility-notes) for a full set of details.

## 0.9.0 ##

Requires XNAT 1.9.2.2 and Container Service 3.7.0

* **Improvement:** [BLP-94](https://radiologics.atlassian.net/browse/BLP-94) - Mirror support for displaying PET tracers in the Recent Activity table (requires XNAT 1.9.3 to display)
* **Bugfix:** [BLP-95](https://radiologics.atlassian.net/browse/BLP-95) - Refactor query that checks workflow history on image session report page to remedy slow load times on high-volume XNATs


## 0.8.1 ##
**[Released Sep 2025](https://bitbucket.org/xnatx/xnatx-batch-launch-plugin/src/0.8.1/)**

Requires XNAT 1.9.2.2 and Container Service 3.7.0+

* **Bugfix:** [BLP-81](https://radiologics.atlassian.net/browse/BLP-81) - Fix an issue causing undefined labels in the processing dashboard when working with non-MR sessions
* **Improvement:** [BLP-89](https://radiologics.atlassian.net/browse/BLP-89) - Improve loading time of dashboard when many thousands of data rows are in context. Requires support for SQL subqueries as introduced in XNAT 1.9.2.2
* **Improvement:** [BLP-91](https://radiologics.atlassian.net/browse/BLP-91) - Improve communication to user when loading many thousands of rows
* **Improvement:** [BLP-92](https://radiologics.atlassian.net/browse/BLP-92) - Remove unnecessary project command check when loading a processing dashboard for a single project


## 0.8.0 ##
**[Released June 2025](https://bitbucket.org/xnatx/xnatx-batch-launch-plugin/src/0.8.0/)**

Requires XNAT 1.9.2 and Container Service 3.7.0

* **Improvement:** [BLP-76](https://radiologics.atlassian.net/browse/BLP-76), [BLP-79](https://radiologics.atlassian.net/browse/BLP-79) - Improve display and performance of processing dashboard with many containers in context
* **Improvement:** [BLP-80](https://radiologics.atlassian.net/browse/BLP-80) - Improve performance of the Recent Data Activity table on XNAT's home page on very large instances
* **Bugfix:** [BLP-82](https://radiologics.atlassian.net/browse/BLP-82) - Fix display bug affecting non-MR sessions in processing dashboard


## 0.7.1 ##
**[Released Feb 2025](https://bitbucket.org/xnatx/xnatx-batch-launch-plugin/src/0.7.1/)**

Requires XNAT 1.9.0 and Container Service 3.6.0

* **Bugfix:** [BLP-77](https://radiologics.atlassian.net/browse/BLP-77) - Similarly named containers can break the processing dashboard display
* **Bugfix:** [BLP-78](https://radiologics.atlassian.net/browse/BLP-78) - Misconfigured datatypes should be ignored


## 0.7.0 ##
**[Released Sep 2024](https://bitbucket.org/xnatx/xnatx-batch-launch-plugin/src/0.7.0/)**

Requires XNAT 1.9.0 and Container Service 3.6.0. Note: Running Pipelines now requires installing the Pipeline Engine Plugin 1.0.0

* **Improvement:** [BLP-74](https://radiologics.atlassian.net/browse/BLP-74) - Update multiple dependencies for compatibility with XNAT 1.9.0. See [XNAT 1.9.0 Release Notes](https://wiki.xnat.org/documentation/xnat-1-9-0-release-notes) for more details


## 0.6.0 ##
**[Released Aug 2022](https://bitbucket.org/xnatx/xnatx-batch-launch-plugin/src/0.6.0/)**

Requires XNAT 1.8.5 and Container Service 3.1 

* **Improvement:** [BLP-50](https://radiologics.atlassian.net/browse/BLP-50) - Improve loading time of User Dashboard on large scale XNAT instances
* **Improvement:** [BLP-57](https://radiologics.atlassian.net/browse/BLP-57), [BLP-58](https://radiologics.atlassian.net/browse/BLP-58), [BLP-59](https://radiologics.atlassian.net/browse/BLP-59), [BLP-62](https://radiologics.atlassian.net/browse/BLP-62), [BLP-66](https://radiologics.atlassian.net/browse/BLP-66) - Multiple improvements to BLP handling of files in build directory
* **Bugfix:** [BLP-60](https://radiologics.atlassian.net/browse/BLP-60) - Fix regression in display of image assessors on processing dashboard

## 0.5.0 ##
**[Released Oct 2021](https://bitbucket.org/xnatx/xnatx-batch-launch-plugin/src/0.5.0/)**

Requires XNAT 1.8.3 and Container Service 3.1

* **Bugfix:** [BLP-53](https://radiologics.atlassian.net/browse/BLP-53) - Fix Processing Dashboard menu appearance


## 0.4.0 ##
**[Released Mar 2021](https://bitbucket.org/xnatx/xnatx-batch-launch-plugin/src/0.4.0/)**

Requires XNAT 1.8.0 and Container Service 3.0

1. The Processing Dashboard is now capable of launching traditional XNAT pipelines. 

2. The pipelines available with the site for a datatype are available to select and launch. If the pipeline is configured for the pipeline, 
the configured values of the  parameters are rendered. If not, values in the pipeline XML are rendered. All the schema link parameters are resolved behind the scene and not exposed. 
The bulk launcher does not work if there are any parameter values which are not identical across sessions.

3. The pipelines can be launched across projects (if sessions are selected via a search)

4. A new shell script in PIPELINE_HOME/bin/killxnatpipeline is required for the button Terminate Job when a pipeline is to be terminated. The following parameters are passed to this script:

````
id <XNAT_ACCESSION_ID> -host <XNAT_PIPELINE_URL> -u <ALIAS_TOKEN> -pwd <TOKEN_PASSWORD> -workflowId <WORKFLOW_ID_OF_WORKFLOW_TO_BE_TERMINATED> -jobId <JOB_ID_IF_ANY_FROM_WORKKFLOWID> -pipelinePath <ABSOLUTE_PATH_TO_PIPELINE> -project <PROJECT_ID> -label <XNAT_LABEL>
````

5. If the sessions are shared into a project, the bulk launcher does not work on the shared project (as of this changelog note).

6. Dependent changes - xnat-data-models (version 1.7.6.RAD-SNAPSHOT-PIPELINE), xnat-pipeline-engine, xnatopen-web (1.7.6.RAD-SNAPSHOT)
