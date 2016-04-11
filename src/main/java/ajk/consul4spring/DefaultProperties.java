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
     * when true Consul4Spring will compare the existing properties with the ones provided by the application, if they're
     * not equal then the existing properties will be moved to a backup location in Consul and the properties provided
     * by the application will be written instead. This is a convenient way to upgrade the "current" configuration
     *
     * @return whether or not to override the default properties that already exist in Consul
     */
    boolean overrideExisting() default false;
}
