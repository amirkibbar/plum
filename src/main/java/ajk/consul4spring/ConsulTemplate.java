package ajk.consul4spring;

/**
 * a convenience template to access the Consul key value store
 */
public interface ConsulTemplate {
    /**
     * writes a value to Consul
     *
     * @param key   the key
     * @param value the value. This could be anything, including a JSON representation of any object
     */
    void write(String key, String value);

    /**
     * retrieves a value from Consul
     *
     * @param key the key
     * @return the value as a string, or null if the key was not found
     */
    String find(String key);

    /**
     * retrieves a value from Consul converted to any object using a {@code com.fasterxml.jackson.databind.ObjectMapper}
     * available in the spring context
     *
     * @param clazz the target class for the conversion
     * @param key   the key
     * @param <T>   the target type for the conversion
     * @return the value converted to {@code clazz}, or null if the key was not found
     */
    <T> T findAndConvert(Class<T> clazz, String key);

    /**
     * recursively deletes a key if it exists
     *
     * @param key the key to delete
     */
    void delete(String key);
}
