package ajk.consul4spring.config;

import ajk.consul4spring.CheckService;
import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import static ajk.consul4spring.Consul4Spring.DEFAULT_HEARTBEAT_RATE;
import static org.apache.commons.logging.LogFactory.getLog;

@Profile("consul")
@Configuration
public class ConsulConfig implements SchedulingConfigurer {
    private Log log = getLog(getClass());

    @Autowired
    private ConsulProperties consulProperties;

    @Autowired
    private CheckService consulService;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // default is every 15 minutes
        int heartbeatRate = consulProperties.getHeartbeatRate() == null ? DEFAULT_HEARTBEAT_RATE : consulProperties.getHeartbeatRate();

        log.info("scheduling the heartbeat every " + heartbeatRate + " seconds");

        taskRegistrar.addFixedRateTask(consulService::keepAlive, heartbeatRate * 1000);
    }
}
