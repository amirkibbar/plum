package ajk.consul4spring;

import ajk.consul4spring.config.ConsulProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.CatalogClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.NotRegisteredException;
import com.orbitz.consul.SessionClient;
import com.orbitz.consul.model.State;
import com.orbitz.consul.model.agent.Check;
import com.orbitz.consul.model.agent.Registration;
import com.orbitz.consul.model.catalog.CatalogService;
import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.TimeoutRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.google.common.base.Optional.absent;
import static com.orbitz.consul.Consul.newClient;
import static com.orbitz.consul.model.State.FAIL;
import static com.orbitz.consul.model.State.PASS;
import static com.orbitz.consul.option.QueryOptionsBuilder.builder;
import static java.lang.String.format;
import static java.time.LocalDateTime.now;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.springframework.util.StringUtils.isEmpty;

@SuppressWarnings("Guava")
@Service
@Profile("consul")
public class Consul4Spring implements CheckService, DistributedLock, ConsulTemplate, CatalogResolver {
    public static int DEFAULT_HEARTBEAT_RATE = 900;

    private Log log = getLog(getClass());

    @Autowired
    private ConsulProperties consulProperties;

    // the following is here to make sure the server port has been set before we try to read it, otherwise the server
    // port will not yet be defined
    @SuppressWarnings("unused")
    @Autowired
    private EmbeddedWebApplicationContext server;

    @Autowired
    private ServerProperties serverProperties;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private DnsResolver dnsResolver;

    @Autowired
    private ConfigurableApplicationContext ctx;

    private Consul consul;

    @PostConstruct
    private void register() throws Exception {
        log.info(consulProperties);
        registerMyself();
        writeDefaultProperties();
    }

    private Consul getConsul() {
        // test that the client is active - the purpose of this test is to make sure that the instance of consul in the
        // cluster is alive, is it's not we'll create a new one before giving up. The assumption is that the hostname of
        // consul is really a consul service that hides multiple instances of consul servers.
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(new FixedBackOffPolicy());
        TimeoutRetryPolicy retryPolicy = new TimeoutRetryPolicy();
        retryPolicy.setTimeout(10000);
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate.execute(context -> {
            if (consul == null || consul.statusClient() == null || isEmpty(consul.statusClient().getLeader())) {
                // if we can't find a leader this means that the consul client is not usable - we'll release it
                log.warn("couldn't verify connection to Consul");
                log.info("creating a new Consul client");
                consul = newClient(consulProperties.getHostname(), consulProperties.getHttpPort());

                // throwing this exception to retry according to the policies. The last retry will throw this exception
                throw new IllegalStateException("Consul connection could not be verified");
            } else {
                log.info("Consul connection verified");
                return consul;
            }
        });
    }

    private void writeDefaultProperties() {
        Object defaultProperties = getDefaultProperties();

        if (defaultProperties == null) {
            log.info("can't find the @DefaultProperties object, skipping configuration registration");
            return;
        }

        KeyValueClient kvClient = getConsul().keyValueClient();

        // only add the current values if they are not already there
        Optional<String> currentValue = findInternal(consulProperties.getBaseKey() + "/config/current");
        Optional<String> currentVersion = findInternal(consulProperties.getBaseKey() + "/config/current-version");
        String appConfigVersion = defaultProperties.getClass().getAnnotation(DefaultProperties.class).version();

        kvClient.getValue(consulProperties.getBaseKey() + "/config/current", builder().blockSeconds(1, 60).build());
        if (!currentValue.isPresent()) {
            try {
                log.info("writing configuration to consul using default values: " + defaultProperties);
                kvClient.putValue(consulProperties.getBaseKey() + "/config/current", mapper.writeValueAsString(defaultProperties));
                kvClient.putValue(consulProperties.getBaseKey() + "/config/current-version", appConfigVersion);
            } catch (JsonProcessingException e) {
                log.fatal("unable to write default configuration to consul", e);
                throw new IllegalStateException("unable to write default configuration to consul", e);
            }
        } else {
            log.info("configuration already exists in consul");
            try {
                if (!currentVersion.isPresent() || !currentValue.get().equals(appConfigVersion)) {
                    String backupKey = consulProperties.getBaseKey() + "/config/backup-" + ISO_LOCAL_DATE_TIME.format(now());
                    log.info("backing up current config to " + backupKey);
                    kvClient.putValue(backupKey, currentValue.get());

                    log.info("writing configuration to consul using default values: " + defaultProperties);
                    kvClient.deleteKey(consulProperties.getBaseKey() + "/config/current");
                    kvClient.putValue(consulProperties.getBaseKey() + "/config/current", mapper.writeValueAsString(defaultProperties));
                    kvClient.putValue(consulProperties.getBaseKey() + "/config/current-version", appConfigVersion);
                } else {
                    log.info("no difference found between the current configuration version and the Consul configuration version, no action taken");
                }
            } catch (IOException e) {
                log.error("unable to load current properties from " + consulProperties.getBaseKey() +
                        "/config/current as " + defaultProperties.getClass().getName(), e);
            }
        }
    }

    private Object getDefaultProperties() {
        Map<String, Object> defaultPropertiesMap = ctx.getBeansWithAnnotation(DefaultProperties.class);

        if (defaultPropertiesMap.size() == 0) {
            return null;
        }

        if (defaultPropertiesMap.size() > 1) {
            log.fatal("expecting no more than 1 object with @DefaultProperties annotation, found: " + defaultPropertiesMap.size());
            throw new IllegalStateException("expecting exactly 1 object with @DefaultProperties annotation, found: " + defaultPropertiesMap.size());
        }

        return defaultPropertiesMap.values().iterator().next();
    }

    private void registerMyself() throws NotRegisteredException, IOException {
        AgentClient agentClient = getConsul().agentClient();

        if (!agentClient.isRegistered(consulProperties.getServiceId())) {
            registerHeartbeat();
        }

        log.info("writing service access properties");
        KeyValueClient kvClient = getConsul().keyValueClient();
        Map<String, String> accessProperties = new HashMap<>();
        String serverName = InetAddress.getLocalHost().getHostName();
        accessProperties.put("hostname", serverName);
        accessProperties.put("ip", dnsResolver.readNonLoopbackLocalAddress());
        String port = String.valueOf(serverProperties.getPort());
        accessProperties.put("port", port);
        accessProperties.put("username", securityProperties.getUser().getName());
        accessProperties.put("password", securityProperties.getUser().getPassword());

        // read current access values and add ourselves
        String accessKey = consulProperties.getServiceName() + "/access/" + serverName + ":" + port;
        kvClient.putValue(accessKey, mapper.writeValueAsString(accessProperties));
    }

    private void registerHeartbeat() {
        log.info("registering heartbeat");
        AgentClient agentClient = getConsul().agentClient();
        Registration registration = new Registration();
        registration.setPort(serverProperties.getPort());
        registration.setAddress(dnsResolver.readNonLoopbackLocalAddress());
        registration.setId(toUniqueName("heartbeat"));
        registration.setName(consulProperties.getServiceName());
        registration.setTags(consulProperties.getTags());
        Registration.Check check = new Registration.Check();
        check.setTtl(format("%ss", 2 * (consulProperties.getHeartbeatRate() == null ? DEFAULT_HEARTBEAT_RATE : consulProperties.getHeartbeatRate())));
        registration.setCheck(check);
        agentClient.register(registration);
    }

    /**
     * changes the heartbeat check to PASS in a configurable rate. The default rate is 15 minutes. The heartbeat check
     * is defined with a grace period of 2 heartbeats before it sets itself to FAIL.
     */
    @Override
    public void keepAlive() {
        try {
            AgentClient agentClient = getConsul().agentClient();
            // the heartbeat is the service itself, not a check - that's why we "pass" it and not "check" it
            agentClient.pass(toUniqueName("heartbeat"));
            log.info("[check heartbeat]: PASS");
        } catch (NotRegisteredException e) {
            log.error("[check heartbeat]: FAIL " + e.getMessage());
            log.error("can't mark heartbeat as PASS", e);
            registerHeartbeat();
        }
    }

    @Override
    @Async
    public void pass(String checkName, long ttl) {
        pass(checkName, ttl, null);
    }

    @Override
    @Async
    public void pass(String checkName, long ttl, String note) {
        check(checkName, ttl, PASS, note);
    }

    @Override
    @Async
    public void fail(String checkName, long ttl) {
        fail(checkName, ttl, null);
    }

    @Override
    @Async
    public void fail(String checkName, long ttl, String note) {
        check(checkName, ttl, FAIL, note);
    }

    private void check(String checkName, long ttl, State state, String note) {
        try {
            log.info("[check " + checkName + "]: " + state + (isEmpty(note) ? "" : " " + note));
            AgentClient agentClient = getConsul().agentClient();
            Check check = new Check();
            check.setId(toUniqueName(checkName));
            check.setName(consulProperties.getServiceName() + " " + checkName);
            check.setServiceId(toUniqueName("heartbeat"));
            check.setTtl(format("%ss", ttl));
            agentClient.registerCheck(check);
            // end of patch

            agentClient.check(toUniqueName(checkName), state, note);
        } catch (Exception e) {
            log.error("[check " + checkName + "]: FAIL " + e.getMessage());
            log.fatal("can't change check" + checkName + " to state " + state, e);
        }
    }

    @Override
    public String toUniqueName(String nonUniqueName) {
        try {
            return consulProperties.getServiceId() + "-" + nonUniqueName + "@" + InetAddress.getLocalHost().getHostName() + ":" + serverProperties.getPort();
        } catch (UnknownHostException e) {
            log.fatal("can't generate unique check name for " + nonUniqueName, e);
            throw new RuntimeException("can't generate unique check name for " + nonUniqueName, e);
        }
    }

    @Override
    public <T> T findAndConvert(Class<T> clazz, String key) {
        String fullKey = consulProperties.getBaseKey() + key;
        Optional<String> value = findInternal(fullKey);
        if (value.isPresent()) {
            try {
                return mapper.readValue(value.get(), clazz);
            } catch (IOException e) {
                log.info("unable to convert value read from " + fullKey + " in the consul k/v store", e);
                return null;
            }
        } else {
            log.info(fullKey + " not found in the consul k/v store");
            return null;
        }
    }

    @Override
    public void delete(String key) {
        String fullKey = consulProperties.getBaseKey() + key;
        KeyValueClient keyValueClient = getConsul().keyValueClient();
        keyValueClient.deleteKeys(fullKey);
        log.info("deleted " + fullKey);
    }

    @Override
    public String find(String key) {
        String fullKey = consulProperties.getBaseKey() + key;
        Optional<String> value = findInternal(fullKey);
        if (value.isPresent()) {
            return value.get();
        } else {
            log.info(fullKey + " not found in the consul k/v store");
            return null;
        }
    }

    @Override
    public void write(String key, String value) {
        String fullKey = consulProperties.getBaseKey() + key;
        KeyValueClient kvClient = getConsul().keyValueClient();
        kvClient.putValue(fullKey, value);
    }

    private Optional<String> findInternal(String key) {
        try {
            KeyValueClient kvClient = getConsul().keyValueClient();
            return kvClient.getValueAsString(key);
        } catch (NullPointerException npe) {
            return absent();
        }
    }

    @Override
    public String acquire() {
        SessionClient sessionClient = getConsul().sessionClient();
        String sessionId = sessionClient.createSession("{\"ttl\": \"3600s\"}").get();
        KeyValueClient kvClient = getConsul().keyValueClient();
        if (kvClient.acquireLock(consulProperties.getBaseKey() + "/lock", sessionId)) {
            log.info("lock " + sessionId + " acquired");
            return sessionId;
        }

        log.warn("could not acquire lock");
        return null;
    }

    @Override
    public void release(String lockId) {
        log.info("releasing lock " + lockId);
        KeyValueClient kvClient = getConsul().keyValueClient();
        kvClient.releaseLock(consulProperties.getBaseKey() + "/lock", lockId);
        SessionClient sessionClient = getConsul().sessionClient();
        sessionClient.destroySession(lockId);
    }

    @Override
    public Set<CatalogService> resolveByName(String name) {
        CatalogClient catalogClient = getConsul().catalogClient();

        List<CatalogService> catalogServices = catalogClient.getService(name).getResponse();
        Set<CatalogService> result = new TreeSet<>((o1, o2) -> o1.getServiceName().compareToIgnoreCase(o2.getServiceName()));
        result.addAll(catalogServices);

        return result;
    }

    @Override
    public String resolveByNameAsClusterDefinition(String name) {
        Set<CatalogService> services = resolveByName(name);
        return services.stream().map(cs -> cs.getServiceAddress() + ":" + cs.getServicePort()).collect(joining(","));
    }
}
