package pbl6.ctsv.foundation.sync;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import javax.sql.DataSource;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class FoundationConfigurationTest {
    @Configuration(proxyBeanMethods=false)
    @Import(FoundationSyncConfiguration.class)
    static class Config { @Bean DataSource source(){return mock(DataSource.class);} }
    @Test void explicitInternalConsumerWiresWithoutAnyPublicTriggerOrDatabaseCall(){
        new ApplicationContextRunner().withUserConfiguration(Config.class).withPropertyValues(
            "foundation.resources.enabled=true", "foundation.resources.source-system-id=1",
            "foundation.resources.base-url=http://localhost:18081", "foundation.resources.service-key=TEST-ONLY-KEY",
            "foundation.resources.expected-database=p02_ctsv_test_20261112", "foundation.resources.expected-instance=DEV2022",
            "foundation.resources.resource=ethnicities").run(context->{
                assertThat(context).hasNotFailed();assertThat(context).hasSingleBean(CtSyncService.class);
                assertThat(context.getBeansOfType(org.springframework.boot.ApplicationRunner.class)).isEmpty();
            });
    }
}
