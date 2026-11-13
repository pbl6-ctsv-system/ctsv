package pbl6.ctsv.foundation.units;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pbl6.ctsv.foundation.units.config.UnitSyncConfiguration;
import pbl6.ctsv.foundation.units.service.UnitSyncService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UnitSyncConfigurationTest {
    final DataSource dataSource=mock(DataSource.class);
    ApplicationContextRunner context() {
        return new ApplicationContextRunner().withUserConfiguration(UnitSyncConfiguration.class)
            .withBean(DataSource.class,()->dataSource)
            .withPropertyValues("spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
                "foundation.units.source-system-id=1","foundation.units.base-url=http://127.0.0.1:9",
                "foundation.units.service-key=synthetic-unit-test-only","foundation.units.expected-database=ctsv_test_20261111",
                "foundation.units.expected-instance=DEV2022");
    }
    @Test void defaultDoesNotCreateOrRunConsumer(){
        context().run(c->{assertThat(c).doesNotHaveBean(UnitSyncService.class);verifyNoInteractions(dataSource);});
    }
    @Test void explicitOptInCreatesConsumerWithoutAutomaticallyCallingSourceOrDatabase(){
        context().withPropertyValues("foundation.units.enabled=true").run(c->{
            assertThat(c).hasSingleBean(UnitSyncService.class);verifyNoInteractions(dataSource);
        });
    }
    @Test void incompleteOptInFailsClosed(){
        context().withPropertyValues("foundation.units.enabled=true","foundation.units.source-system-id=0").run(c->assertThat(c).hasFailed());
    }
}
