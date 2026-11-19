package pbl6.ctsv.foundation.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationUsage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FoundationUsageWiringTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(FoundationConfiguration.class)
        .withBean(AuthAccess.class, () -> mock(AuthAccess.class))
        .withBean(JdbcTemplate.class, () -> jdbc)
        .withBean(EntityManager.class, () -> mock(EntityManager.class))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withPropertyValues("ctsv.foundation.enabled=true", "ctsv.auth.origins[0]=https://ctsv.test");

    @Test
    void preservesMghpUsageAndAddsScholarshipUsage() {
        var scholarshipUsage = mock(FoundationUsage.class);
        when(scholarshipUsage.semesterUsed(21L)).thenReturn(true);
        when(jdbc.queryForObject(contains("dbo.mghp_danh_gia_dieu_kien"), eq(Integer.class), anyLong(), anyLong()))
            .thenReturn(0);
        when(jdbc.queryForObject(contains("dbo.mghp_danh_gia_dieu_kien"), eq(Integer.class), eq(22L), eq(22L)))
            .thenReturn(1);

        context.withUserConfiguration(MghpUsageConfiguration.class)
            .withBean("scholarshipUsage", FoundationUsage.class, () -> scholarshipUsage)
            .run(application -> {
                assertThat(application).hasNotFailed();
                assertThat(application.getBeansOfType(FoundationUsage.class)).hasSize(2);
                var service = application.getBean(FoundationService.class);
                var usage = (FoundationUsage) ReflectionTestUtils.getField(service, "usage");
                assertThat(usage.semesterUsed(21L)).isTrue();
                assertThat(usage.semesterUsed(22L)).isTrue();
                assertThat(usage.semesterUsed(23L)).isFalse();
                assertThat(usage.processUsed(22L)).isTrue();
                assertThat(usage.processUsed(23L)).isFalse();
            });
    }

    @Test
    void startsWithAnEmptyUsageList() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            var service = application.getBean(FoundationService.class);
            var usage = (FoundationUsage) ReflectionTestUtils.getField(service, "usage");
            assertThat(usage.semesterUsed(21L)).isFalse();
            assertThat(usage.processUsed(21L)).isFalse();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan("pbl6.ctsv.mghp.foundation")
    static class MghpUsageConfiguration {}
}
