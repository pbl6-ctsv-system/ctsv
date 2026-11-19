package pbl6.ctsv.mghp.application;

import java.time.Clock;
import java.util.Map;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** REAL SQL Server, read-only verification on the existing guarded P08 test DB. No Flyway/seed/DDL. */
@EnabledIfSystemProperty(named="mghp.wiring.sql.enabled",matches="true")
class ApplicationModuleSqlIT {
    @TestConfiguration(proxyBeanMethods=false)
    @Import({EvaluationRepository.class,IntakeRepository.class,InboxService.class})
    static class Wiring {
        @Bean JdbcTemplate jdbc(DataSource source) {return new JdbcTemplate(source);}
        @Bean Clock clock() {return Clock.systemUTC();}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory=new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("pbl6.ctsv.mghp.application");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate",
                "hibernate.hbm2ddl.jdbc_metadata_extraction_strategy","individually"));
            return factory;
        }
    }

    @Test void enabledRepositoriesWireAndReadThroughRealJpaAndJdbcWithoutMigrating() {
        String port=System.getenv("P00_SQL_PORT");
        if(port==null || !port.matches("[1-9][0-9]{0,4}") || Integer.parseInt(port)>65535)
            throw new IllegalStateException("Verified DEV2022 TCP port required");
        var source=new DriverManagerDataSource("jdbc:sqlserver://localhost:"+port+
            ";databaseName=ctsv_p08_test_20261118;integratedSecurity=true;encrypt=true;trustServerCertificate=true;connectRetryCount=0;loginTimeout=5;socketTimeout=30000");
        var jdbc=new JdbcTemplate(source);
        var target=jdbc.queryForMap("""
            SELECT DB_NAME() AS db,CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')) AS instance,
              IIF(owner_sid=SUSER_SID(),1,0) AS owned,compatibility_level AS compatibility,
              collation_name AS collation,
              (SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties
               WHERE class=0 AND name='p08_test_owner') AS marker
            FROM sys.databases WHERE database_id=DB_ID()
            """);
        assertThat(target.get("db")).isEqualTo("ctsv_p08_test_20261118");
        assertThat(target.get("instance")).isEqualTo("DEV2022");
        assertThat(((Number)target.get("owned")).intValue()).isEqualTo(1);
        assertThat(((Number)target.get("compatibility")).intValue()).isEqualTo(160);
        assertThat(target.get("collation")).isEqualTo("Vietnamese_100_CI_AS_SC");
        assertThat(target.get("marker")).isEqualTo("ctsv-mghp-p08-20261118");
        var history=jdbc.queryForList("SELECT installed_rank,version,checksum,success FROM dbo.flyway_schema_history ORDER BY installed_rank");
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("wiring",Map.of("ctsv.mghp.applications.enabled","true")));
            context.registerBean(DataSource.class,()->source);context.register(Wiring.class);context.refresh();
            var evaluation=context.getBean(EvaluationRepository.class);
            var intake=context.getBean(IntakeRepository.class);
            assertThat(context.getBean(InboxService.class)).isNotNull();
            var transaction=new TransactionTemplate(new JpaTransactionManager(context.getBean(EntityManagerFactory.class)));
            transaction.setReadOnly(true);
            transaction.executeWithoutResult(status->{
                assertThat(evaluation.events(-1)).isEmpty();
                assertThat(intake.rows("SELECT TOP (0) id FROM dbo.mghp_ho_so")).isEmpty();
                assertThat(intake.find(ApplicationEntities.Application.class,-1L)).isNull();
            });
        }
        assertThat(jdbc.queryForList("SELECT installed_rank,version,checksum,success FROM dbo.flyway_schema_history ORDER BY installed_rank")).isEqualTo(history);
        System.out.println("MGHP wiring REAL SQL PASS: guarded ctsv_p08_test_20261118/DEV2022; JPA validate and SELECT only, Flyway history unchanged.");
    }
}
