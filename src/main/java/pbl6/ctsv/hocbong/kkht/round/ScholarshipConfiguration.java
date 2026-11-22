package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.foundation.api.FoundationRoleAccess;
import pbl6.ctsv.foundation.api.FoundationUsage;
import pbl6.ctsv.foundation.api.ScholarshipScopeSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ctsv.foundation.enabled", havingValue = "true")
class ScholarshipConfiguration {
    @Bean
    RoundRepository scholarshipRoundRepository(EntityManager em, JdbcTemplate jdbc) {
        return new RoundRepository(em, jdbc);
    }

    @Bean
    RoundService scholarshipRoundService(RoundRepository repository, AcademicSemesterSource source,
            ObjectMapper json) {
        return new RoundService(repository, source, json, Clock.systemUTC());
    }

    @Bean
    CompletedAssessmentLookup scholarshipCompletedAssessments() {
        // No engine/run table exists yet. T06.2 replaces this with the full-pipeline proof provider.
        return new CompletedAssessmentLookup() {
            public java.util.Optional<Proof> latestCompleted(long roundId) { return java.util.Optional.empty(); }
            public java.util.Set<GroupKey> assessedGroups(long semesterId) { return java.util.Set.of(); }
        };
    }

    @Bean
    ScopeService scholarshipScopeService(RoundRepository repository,RoundService rounds,ScholarshipScopeSource source,
            CompletedAssessmentLookup completed,List<RoundScopeUsage> usages,ObjectMapper json) {
        return new ScopeService(repository,rounds,source,completed,usages,json,Clock.systemUTC());
    }

    @Bean
    RoundAccess scholarshipRoundAccess(PlatformTransactionManager manager, FoundationAccess identities,
            FoundationRoleAccess roles) {
        return new RoundAccess(manager, identities, roles);
    }

    @Bean
    FoundationUsage scholarshipFoundationUsage(RoundRepository repository) {
        return new FoundationUsage() {
            @Override
            public boolean semesterUsed(long sourceSemesterId) {
                return repository.semesterUsed(sourceSemesterId);
            }

            @Override
            public boolean processUsed(long processId) {
                return false;
            }
        };
    }

    @Bean
    FilterRegistrationBean<CorsFilter> scholarshipCors(Environment environment) {
        List<String> origins = Binder.get(environment)
                .bind("ctsv.auth.origins", Bindable.listOf(String.class))
                .orElseThrow(() -> new IllegalStateException("Auth origins required"));
        var policy = new CorsConfiguration();
        policy.setAllowedOrigins(origins);
        policy.setAllowCredentials(true);
        policy.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        policy.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.IF_MATCH,"Idempotency-Key"));
        policy.setExposedHeaders(List.of(HttpHeaders.ETAG, HttpHeaders.LOCATION,"Idempotency-Replayed"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(RoundController.BASE + "/**", policy);
        CorsFilter filter = new CorsFilter(source) {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
                if (StringUtils.startsWithIgnoreCase(request.getContentType(), "multipart/")) {
                    request = new HttpServletRequestWrapper(request) {
                        @Override
                        public String getContentType() {
                            // Skip Spring's eager multipart resolution. RoundHttp uses the original headers.
                            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
                        }
                    };
                }
                super.doFilterInternal(request, response, chain);
            }
        };
        var registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns(RoundController.BASE + "/*");
        return registration;
    }
}
