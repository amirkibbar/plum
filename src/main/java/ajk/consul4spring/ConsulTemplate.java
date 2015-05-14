package ajk.consul4spring;

public interface ConsulTemplate {
    void write(String key, String value);

    String find(String key);

    <T> T findAndConvert(Class<T> clazz, String key);
}
