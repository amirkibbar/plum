package ajk.consul4spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

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

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int getDnsPort() {
        return dnsPort;
    }

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
