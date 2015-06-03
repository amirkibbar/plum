package ajk.consul4spring;

/**
 * use this service to change the state of checks in Consul. If you are changing the state of a non-existing check, this
 * check will be created for you.
 */
public interface CheckService {
    /**
     * pass a check
     *
     * @param checkName the check name
     * @param ttl       time to live in milliseconds. when this time period passes Consul will mark the check as failed
     *                  unless you change the state of the check to "pass" again
     */
    void pass(String checkName, long ttl);

    /**
     * pass a check
     *
     * @param checkName the check name
     * @param ttl       time to live in milliseconds. when this time period passes Consul will mark the check as failed
     *                  unless you change the state of the check to "pass" again
     * @param note      a note that will appear in the Consul UI/API next to this check
     */
    void pass(String checkName, long ttl, String note);

    /**
     * fail a check
     *
     * @param checkName the check name
     * @param ttl       time to live in milliseconds. when this time period passes Consul will mark the check as failed
     *                  unless you change the state of the check to "pass" again
     */
    void fail(String checkName, long ttl);

    /**
     * fail a check
     *
     * @param checkName the check name
     * @param ttl       time to live in milliseconds. when this time period passes Consul will mark the check as failed
     *                  unless you change the state of the check to "pass" again
     * @param note      a note that will appear in the Consul UI/API next to this check
     */
    void fail(String checkName, long ttl, String note);

    /**
     * convert the logical check name you provide to the unique name used to register it in Consul
     *
     * @param nonUniqueName your logical check name
     * @return the unique name used to register the check in Consul
     */
    String toUniqueName(String nonUniqueName);

    void keepAlive();
}
