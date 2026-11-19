package pbl6.ctsv.mghp.application;

import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApplicationModuleActivationTest {
    @TestConfiguration(proxyBeanMethods=false)
    @Import({EvaluationRepository.class,IntakeRepository.class,InboxService.class})
    static class Module {}

    private final ApplicationContextRunner context=new ApplicationContextRunner().withUserConfiguration(Module.class);

    @Test void defaultOffDoesNotRequirePersistenceOrClock() {
        context.run(c->{
            assertThat(c).hasNotFailed().doesNotHaveBean(EvaluationRepository.class)
                .doesNotHaveBean(IntakeRepository.class).doesNotHaveBean(InboxService.class);
        });
    }
    @Test void explicitOffDoesNotRequirePersistence() {
        context.withPropertyValues("ctsv.mghp.applications.enabled=false").run(c->{
            assertThat(c).hasNotFailed().doesNotHaveBean(EvaluationRepository.class)
                .doesNotHaveBean(IntakeRepository.class).doesNotHaveBean(InboxService.class);
        });
    }
    @Test void enabledCreatesAllThreeBeansWithPersistenceDependencies() {
        context.withPropertyValues("ctsv.mghp.applications.enabled=true")
            .withBean(JdbcTemplate.class,()->mock(JdbcTemplate.class))
            .withBean(EntityManagerFactory.class,()->mock(EntityManagerFactory.class))
            .withBean(Clock.class,Clock::systemUTC)
            .run(c->assertThat(c).hasNotFailed().hasSingleBean(EvaluationRepository.class)
                .hasSingleBean(IntakeRepository.class).hasSingleBean(InboxService.class));
    }
    @Test void enabledWithoutPersistenceFailsFast() {
        context.withPropertyValues("ctsv.mghp.applications.enabled=true").run(c->assertThat(c).hasFailed());
    }
}
