package ajk.consul4spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * the consul properties is loaded from your application.yml (or any other means supported by the {@code @ConfigurationProperties}
 * spring boot annotation)
 */
@SuppressWarnings("unused")
@ConfigurationProperties(prefix = "consul")
public class ConsulProperties {
    private String hostname;

    private int httpPort;

    private int dnsPort;

    private String serviceId;

    private String serviceName;

    private String[] tags;

    public String getBaseKey() {
        return serviceName + "/" + serviceId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String[] getTags() {
        return tags;
    }

    /**
     * the tags used to publish your service
     *
     * @param tags an array of Consul tags
     */
    public void setTags(String[] tags) {
        this.tags = tags;
    }

    /**
     * the name used to register your service in Consul. This should be a DNS resolvable name
     *
     * @param serviceName the service name, for example MyService
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceId() {
        return serviceId;
    }

    /**
     * the service ID used to register your service in Consul. This ID could be the service name, for example
     *
     * @param serviceId the ID with which the service is registered in Consul
     */
    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getHostname() {
        return hostname;
    }

    /**
     * the Consul hostname
     *
     * @param hostname the Consul hostname or IP address
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getHttpPort() {
        return httpPort;
    }

    /**
     * the HTTP port to access the Consul HTTP API
     *
     * @param httpPort Consul HTTP port
     */
    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int getDnsPort() {
        return dnsPort;
    }

    /**
     * the DNS port to access the Consul DNS API
     *
     * @param dnsPort Consul DNS port
     */
    public void setDnsPort(int dnsPort) {
        this.dnsPort = dnsPort;
    }

    @Override
    public String toString() {
        return "ConsulProperties{" +
                "hostname='" + hostname + '\'' +
                ", httpPort=" + httpPort +
                ", dnsPort=" + dnsPort +
                ", serviceId='" + serviceId + '\'' +
                '}';
    }
}
