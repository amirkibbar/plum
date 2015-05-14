package ajk.consul4spring;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * a marker service to enable @ConditionalOnMissingBean(value = ConsulService.class)
 */
@Profile("consul")
@Service
public class ConsulService {
}
