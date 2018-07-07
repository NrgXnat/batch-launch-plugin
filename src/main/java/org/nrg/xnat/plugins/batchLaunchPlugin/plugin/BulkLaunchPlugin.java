/*
 * xnat-selectable-table: org.nrg.xnat.plugins.selectableTable.plugin.SelectableTablePlugin
 * XNAT http://www.xnat.org
 * Copyright (c) 2017, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnat.plugins.batchLaunchPlugin.plugin;

import org.nrg.framework.annotations.XnatPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;

@XnatPlugin(value = "batchLaunchPlugin", 
			name = "XNAT 1.7 Batch Launch Plugin", 
			description = "Enabled launching Containers/Pipelines in bulk",
			entityPackages = {"com.radiologics.bulk.launch"}
		)

@ComponentScan({
	"org.nrg.xnat.bulk.xapi"
})
public class BulkLaunchPlugin {
	
	public static Logger logger = LoggerFactory.getLogger(BulkLaunchPlugin.class);

	public BulkLaunchPlugin() {
		logger.info("Configuring XSync plugin");
	}
}
