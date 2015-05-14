package ajk.consul4spring;

public interface DistributedLock {
    String acquire();

    void release(String lockId);
}
