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
}
