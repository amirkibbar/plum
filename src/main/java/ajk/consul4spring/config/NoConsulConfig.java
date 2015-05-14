package ajk.consul4spring.config;

import ajk.consul4spring.CheckService;
import ajk.consul4spring.Consul4Spring;
import ajk.consul4spring.ConsulTemplate;
import ajk.consul4spring.DistributedLock;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.file.Files.createFile;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.notExists;
import static java.nio.file.Paths.get;
import static org.apache.commons.logging.LogFactory.getLog;

@ConditionalOnMissingBean(value = Consul4Spring.class)
@Configuration
public class NoConsulConfig {
    @Bean
    public DistributedLock noConsulService() {
        return new NoConsulDistributedLock();
    }

    @Bean
    public CheckService noConsulCheckService() {
        return new NoConsulCheckService();
    }

    @Bean
    public ConsulTemplate noConsulTemplate() {
        return new NoConsulTemplate();
    }

    private static class NoConsulTemplate implements ConsulTemplate {
        private Log log = getLog(getClass());

        @Override
        public void write(String key, String value) {
            Path keyFile = get(System.getProperty("java.io.tmpdir", "/tmp"), key);
            try {
                deleteIfExists(keyFile);
                createFile(keyFile);
                Files.write(keyFile, value.getBytes());
            } catch (IOException e) {
                log.error("error writing " + keyFile.toAbsolutePath(), e);
            }
        }

        @Override
        public String find(String key) {
            Path keyFile = get(System.getProperty("java.io.tmpdir", "/tmp"), key);
            if (notExists(keyFile)) {
                return null;
            }

            try {
                return IOUtils.toString(keyFile.toUri());
            } catch (IOException e) {
                log.error("error reading " + keyFile.toAbsolutePath(), e);
                return null;
            }
        }

        @Override
        public <T> T findAndConvert(Class<T> clazz, String key) {
            throw new UnsupportedOperationException();
        }
    }

    private static class NoConsulDistributedLock implements DistributedLock {
        private ReentrantLock lock = new ReentrantLock();

        @Override
        public String acquire() {
            if (lock.isLocked()) {
                return null;
            }
            lock.lock();
            return "locked";
        }

        @Override
        public void release(String lockId) {
            lock.unlock();
        }
    }

    private static class NoConsulCheckService implements CheckService {
        @Override
        public void pass(String checkName, long ttl) {
        }

        @Override
        public void pass(String checkName, long ttl, String note) {
        }

        @Override
        public void fail(String checkName, long ttl) {
        }

        @Override
        public void fail(String checkName, long ttl, String note) {
        }

        @Override
        public String toUniqueName(String nonUniqueName) {
            return nonUniqueName;
        }
    }
}
