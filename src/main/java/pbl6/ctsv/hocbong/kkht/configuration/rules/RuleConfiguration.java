package pbl6.ctsv.hocbong.kkht.configuration.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="ctsv.foundation.enabled",havingValue="true")
class RuleConfiguration {
    @Bean RuleRepository scholarshipRuleRepository(EntityManager em,JdbcTemplate jdbc){return new RuleRepository(em,jdbc);}
    @Bean RuleService scholarshipRuleService(RuleRepository repository,ObjectMapper json){return new RuleService(repository,json,Clock.systemUTC());}
}
