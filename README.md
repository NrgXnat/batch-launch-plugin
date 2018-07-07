# XNAT Batch Launch Plugin #

This is the XNAT 1.7 Batch Launch plugin. Its purpose is to enable monitoring and launching jobs on a batch

This plugin was primarily developed for the Container Service to allow users to run commands on a batch.



# Building & Installing #

To build :

1. If you haven't already, clone this repository and cd to the newly cloned folder.
1. Build the plugin: `./gradlew jar` (on Windows, you can use the batch file: `gradlew.bat jar`). This should build the plugin in the file **build/libs/batch-launch-plugin-1.0.0.jar** (the version may differ based on updates to the code).
1. Copy the plugin jar to your plugins folder: `cp build/libs/batch-launch-plugin-1.0.0.jar /data/xnat/home/plugins`
1. Restart Tomcat and your plugin will become active in XNAT. 


