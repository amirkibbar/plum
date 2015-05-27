package ajk.consul4spring;

import com.orbitz.consul.model.catalog.CatalogService;

import java.util.Set;

public interface CatalogResolver {
    /**
     * resolves a service by its name
     *
     * @param name the service name to lookup
     * @return a set of unique {@link CatalogService} objects
     */
    Set<CatalogService> resolveByName(String name);

    /**
     * resolves a service by its name to a comma separated list of ip-addr:port for each of the located services in the catalog. This is a very
     * useful method for constructing a cluster definition. For example, if you want to lookup all the ip-addr:port
     * entries of a RabbitMQ service that's defined in Consul and use the result of this method as your RabbitMQ cluster
     * definition. Most clients of most services that support clusters accept this format as the location of the
     * service.
     *
     * @param name the service name to lookup
     * @return a comma separated list of ip-addr:port for each of the located services in the catalog, empty String when
     * none found
     */
    String resolveByNameAsClusterDefinition(String name);
}
