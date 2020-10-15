
package org.nrg.xnatx.plugins.batch;

import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.annotations.XnatPlugin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.concurrent.ThreadPoolExecutorFactoryBean;

@XnatPlugin(value = "batchLaunchPlugin",
            name = "XNAT Batch Launch Plugin",
            logConfigurationFile = "META-INF/resources/batch_launch-logback.xml",
            description = "Enable launching Containers/Pipelines in bulk")
@ComponentScan("org.nrg.xnatx.plugins.batch")
@Slf4j
public class BatchLaunchPlugin {
    @Bean(name = "batchLaunchThreadPoolExecutorFactoryBean")
    public ThreadPoolExecutorFactoryBean batchLaunchThreadPoolExecutorFactoryBean() {
        final ThreadPoolExecutorFactoryBean tBean = new ThreadPoolExecutorFactoryBean();
        tBean.setCorePoolSize(5);
        tBean.setThreadNamePrefix("batch-launch-");
        return tBean;
    }
}
