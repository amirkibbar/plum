package ajk.consul4spring;

/**
 * a convenient way to use Consul's distributed lock
 */
public interface DistributedLock {
    /**
     * acquire a lock
     *
     * @return a lock ID, or null if the lock could not be acquired
     */
    String acquire();

    /**
     * release a lock by its ID
     *
     * @param lockId the lock ID to release
     */
    void release(String lockId);
}
