package ajk.consul4spring;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * mark any (single) class in your application with this annotation to have this library register it with Consul as the
 * default configuration of your application
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface DefaultProperties {
    /**
     * the version property replaces the "overrideExisting". This property is more flexible - it only replaces the
     * "current" configuration if the versions don't match. If the versions match, then the existing configuration
     * will not be replaced. This is because otherwise the Consul config is always overridden with the application's
     * "default" version if anything was changed in Consul, which effectively means that the Consul changes can only be
     * used if the configuration can change online, and each time the application is restarted the configuration resets
     * itself back to the default.
     *
     * The version is stored in Consul under <code>{serviceName}/{serviceId}/config/current-version</code>
     *
     * @return the version of the application's configuration
     */
    String version();
}
