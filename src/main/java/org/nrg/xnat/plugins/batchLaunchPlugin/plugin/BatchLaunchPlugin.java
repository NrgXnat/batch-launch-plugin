package org.nrg.xnat.plugins.batchLaunchPlugin.plugin;

import org.nrg.framework.annotations.XnatPlugin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;

@XnatPlugin(value = "batchLaunchPlugin", 
			name = "XNAT 1.7 Batch Launch Plugin", 
			description = "Enabled launching Containers/Pipelines in bulk")

@ComponentScan({
	"org.nrg.xnat.bulk.*"
})
@Slf4j
public class BatchLaunchPlugin {
	public BatchLaunchPlugin() {
		log.info("Configuring batch launch plugin");
	}

	@Bean(name = "batchLaunchThreadPoolExecutorFactoryBean")
	public ThreadPoolExecutorFactoryBean batchLaunchThreadPoolExecutorFactoryBean() {
		ThreadPoolExecutorFactoryBean tBean = new ThreadPoolExecutorFactoryBean();
		tBean.setCorePoolSize(5);
		tBean.setThreadNamePrefix("batch-launch-");
		return tBean;
	}
}
