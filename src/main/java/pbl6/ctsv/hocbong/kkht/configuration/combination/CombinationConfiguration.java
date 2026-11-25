package pbl6.ctsv.hocbong.kkht.configuration.combination;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import pbl6.ctsv.foundation.api.ScholarshipCombinationSource;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="ctsv.foundation.enabled",havingValue="true")
class CombinationConfiguration {
    @Bean CombinationRepository scholarshipCombinationRepository(EntityManager em, JdbcTemplate jdbc) {
        return new CombinationRepository(em,jdbc);
    }
    @Bean CombinationService scholarshipCombinationService(CombinationRepository repository, ScholarshipCombinationSource source,
            List<CombinationUsage> usages,ObjectMapper json) {
        return new CombinationService(repository,source,usages,json,Clock.systemUTC());
    }
}
