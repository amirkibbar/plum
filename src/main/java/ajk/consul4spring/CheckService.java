package ajk.consul4spring;

public interface CheckService {
    void pass(String checkName, long ttl);

    void pass(String checkName, long ttl, String note);

    void fail(String checkName, long ttl);

    void fail(String checkName, long ttl, String note);

    String toUniqueName(String nonUniqueName);
}
