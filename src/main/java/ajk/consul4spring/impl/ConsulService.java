package ajk.consul4spring.impl;

import ajk.consul4spring.CheckService;
import ajk.consul4spring.config.ConsulProperties;
import ajk.consul4spring.ConsulTemplate;
import ajk.consul4spring.DefaultProperties;
import ajk.consul4spring.DistributedLock;
import ajk.consul4spring.DnsResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.NotRegisteredException;
import com.orbitz.consul.SessionClient;
import com.orbitz.consul.model.State;
import com.orbitz.consul.model.agent.Registration;
import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import static com.orbitz.consul.Consul.newClient;
import static com.orbitz.consul.model.State.FAIL;
import static com.orbitz.consul.model.State.PASS;
import static com.orbitz.consul.option.QueryOptionsBuilder.builder;
import static org.apache.commons.logging.LogFactory.getLog;

@Service
@Profile("consul")
public class ConsulService implements CheckService, DistributedLock, ConsulTemplate {
    private Log log = getLog(getClass());

    @Autowired
    private ConsulProperties consulProperties;

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

    @PostConstruct
    private void register() throws Exception {
        log.info(consulProperties);
        registerMyself();
        writeDefaultProperties();
    }

    private void writeDefaultProperties() {
        Object defaultProperties = getDefaultProperties();

        if(defaultProperties == null) {
            log.info("can't find the @DefaultProperties object, skipping configuration registration");
            return;
        }

        KeyValueClient kvClient = newClient(consulProperties.getHostname(), consulProperties.getHttpPort()).keyValueClient();

        // only add the default values if they are not already there
        if (!kvClient.getValue(consulProperties.getBaseKey() + "/config/current", builder().blockSeconds(1, 60).build()).isPresent()) {
            try {
                log.info("writing configuration to consul using default values: " + defaultProperties);
                kvClient.putValue(consulProperties.getBaseKey() + "/config/current", mapper.writeValueAsString(defaultProperties));
                kvClient.putValue(consulProperties.getBaseKey() + "/config/defaults", mapper.writeValueAsString(defaultProperties));
            } catch (JsonProcessingException e) {
                log.fatal("unable to write default configuration to consul", e);
                throw new IllegalStateException("unable to write default configuration to consul", e);
            }
        } else {
            log.info("configuration already exist in consul, not overwriting with defaults");
        }
    }

    private Object getDefaultProperties() {
        Map<String, Object> defaultPropertiesMap = ctx.getBeansWithAnnotation(DefaultProperties.class);

        if (defaultPropertiesMap.size() ==0) {
            return null;
        }

        if (defaultPropertiesMap.size() > 1) {
            log.fatal("expecting no more than 1 object with @DefaultProperties annotation, found: " + defaultPropertiesMap.size());
            throw new IllegalStateException("expecting exactly 1 object with @DefaultProperties annotation, found: " + defaultPropertiesMap.size());
        }

        return defaultPropertiesMap.values().iterator().next();
    }

    private void registerMyself() throws NotRegisteredException, IOException {
        Consul consul = newClient(consulProperties.getHostname(), consulProperties.getHttpPort());
        AgentClient agentClient = consul.agentClient();

        if (!agentClient.isRegistered(consulProperties.getServiceId())) {
            Registration registration = new Registration();
            registration.setPort(serverProperties.getPort());
            registration.setAddress(dnsResolver.readNonLoopbackLocalAddress());
            registration.setId(toUniqueName("heartbeat"));
            registration.setName(consulProperties.getServiceName());
            registration.setTags(consulProperties.getTags());
            Registration.Check check = new Registration.Check();
            check.setTtl(String.format("%ss", 20));
            registration.setCheck(check);
            agentClient.register(registration);

            log.info("writing service access properties");
            KeyValueClient kvClient = consul.keyValueClient();
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
    }

    @Scheduled(fixedRate = 10000)
    private void keepAlive() throws IOException, NotRegisteredException {
        // the heartbeat is the service itself, not a check
        AgentClient agentClient = newClient(consulProperties.getHostname(), consulProperties.getHttpPort()).agentClient();
        agentClient.pass(toUniqueName("heartbeat"));
        log.info("[check heartbeat]: PASS");
    }

    @Override
    public void pass(String checkName, long ttl) {
        pass(checkName, ttl, null);
    }

    @Override
    public void pass(String checkName, long ttl, String note) {
        check(checkName, ttl, PASS, note);
    }

    @Override
    public void fail(String checkName, long ttl) {
        fail(checkName, ttl, null);
    }

    @Override
    public void fail(String checkName, long ttl, String note) {
        check(checkName, ttl, FAIL, note);
    }

    private void check(String checkName, long ttl, State state, String note) {
        try {
            log.info("[check " + checkName + "]: " + state);
            AgentClient agentClient = newClient(consulProperties.getHostname(), consulProperties.getHttpPort()).agentClient();
            agentClient.registerCheck(toUniqueName(checkName), consulProperties.getServiceName() + " " + checkName, ttl);
            agentClient.check(toUniqueName(checkName), state, note);
        } catch (NotRegisteredException e) {
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
        KeyValueClient kvClient = newClient(consulProperties.getHostname(), consulProperties.getHttpPort()).keyValueClient();
        kvClient.putValue(fullKey, value);
    }

    private Optional<String> findInternal(String key) {
        KeyValueClient kvClient = newClient(consulProperties.getHostname(), consulProperties.getHttpPort()).keyValueClient();
        return kvClient.getValueAsString(key);
    }

    @Override
    public String acquire() {
        Consul consul = newClient(consulProperties.getHostname(), consulProperties.getHttpPort());
        SessionClient sessionClient = consul.sessionClient();
        String sessionId = sessionClient.createSession("{\"ttl\": \"3600s\"}").get();
        KeyValueClient kvClient = consul.keyValueClient();
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
        Consul consul = newClient(consulProperties.getHostname(), consulProperties.getHttpPort());
        KeyValueClient kvClient = consul.keyValueClient();
        kvClient.releaseLock(consulProperties.getBaseKey() + "/lock", lockId);
        SessionClient sessionClient = consul.sessionClient();
        sessionClient.destroySession(lockId);
    }
}
