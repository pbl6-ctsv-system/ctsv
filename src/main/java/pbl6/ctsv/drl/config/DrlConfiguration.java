package pbl6.ctsv.drl.config;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import pbl6.ctsv.drl.repository.DrlRepository;

@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration(DrlRepositoryBeans.class)
public class DrlConfiguration {
    @Bean
    PhysicalNamingStrategy drlNamingStrategy() {
        return new DrlPhysicalNamingStrategy();
    }

    @Bean
    HibernatePropertiesCustomizer drlHibernateDefaults() {
        return properties -> properties.putIfAbsent(
                "hibernate.hbm2ddl.jdbc_metadata_extraction_strategy", "individually");
    }
}

// Imported by Boot after the shared JPA and JDBC bean definitions are available.
@AutoConfigureAfter({HibernateJpaAutoConfiguration.class, JdbcTemplateAutoConfiguration.class})
@ConditionalOnBean({EntityManagerFactory.class, JdbcTemplate.class})
class DrlRepositoryBeans {
    @Bean
    DrlRepository drlRepository(EntityManagerFactory factory, JdbcTemplate jdbc) {
        return new DrlRepository(SharedEntityManagerCreator.createSharedEntityManager(factory), jdbc);
    }
}
