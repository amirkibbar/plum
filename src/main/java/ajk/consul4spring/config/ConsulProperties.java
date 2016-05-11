package ajk.consul4spring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * the consul properties is loaded from your application.yml (or any other means supported by the {@code @ConfigurationProperties}
 * spring boot annotation)
 */
@Data
@SuppressWarnings("unused")
@ConfigurationProperties(prefix = "consul")
public class ConsulProperties {
    /**
     * the Consul hostname
     */
    private String hostname;

    /**
     * the HTTP port to access the Consul HTTP API
     */
    private int httpPort;

    /**
     * the DNS port to access the Consul DNS API
     */
    private int dnsPort;

    /**
     * the service ID used to register your service in Consul. This ID could be the service name, for example
     */
    private String serviceId;

    /**
     * the name used to register your service in Consul. This should be a DNS resolvable name
     */
    private String serviceName;

    /**
     * the tags used to publish your service
     */
    private String[] tags;

    private Integer heartbeatRate;

    public String getBaseKey() {
        return serviceName + "/" + serviceId;
    }
}
