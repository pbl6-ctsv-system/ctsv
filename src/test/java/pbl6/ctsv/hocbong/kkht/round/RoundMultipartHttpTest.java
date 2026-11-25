package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.DispatcherServlet;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.foundation.api.FoundationRoleAccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = RoundMultipartHttpTest.Fixture.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "server.ssl.enabled=false", "ctsv.foundation.enabled=true",
                "ctsv.auth.origins[0]=https://ctsv.example.test", "spring.servlet.multipart.resolve-lazily=false"})
@ActiveProfiles("test")
class RoundMultipartHttpTest extends RoundMultipartCases {
    // This explicit test root must be excluded from the application's component scan.
    @TestComponent
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class})
    @Import({RoundController.class, RoundErrors.class, RoundErrors.MethodErrors.class})
    static class Fixture {
        @Bean AuthAccess auth() { return mock(AuthAccess.class); }
        @Bean FoundationAccess identities() { return mock(FoundationAccess.class); }
        @Bean FoundationRoleAccess roles() { return mock(FoundationRoleAccess.class); }
        @Bean PlatformTransactionManager manager() { return mock(PlatformTransactionManager.class); }
        @Bean RoundService roundService() { return mock(RoundService.class); }
        @Bean ScopeService scopeService() { return mock(ScopeService.class); }
        @Bean JdbcTemplate jdbc() { return mock(JdbcTemplate.class); }
        @Bean AtomicInteger multipartParsingAttempts() { return new AtomicInteger(); }

        @Bean RoundAccess roundAccess(PlatformTransactionManager manager, FoundationAccess identities,
                FoundationRoleAccess roles) {
            return new RoundAccess(manager, identities, roles);
        }

        @Bean FilterRegistrationBean<CorsFilter> scholarshipCors(Environment environment) {
            return new ScholarshipConfiguration().scholarshipCors(environment);
        }

        @Bean FilterRegistrationBean<jakarta.servlet.Filter> multipartProbe(AtomicInteger multipartParsingAttempts) {
            var registration = new FilterRegistrationBean<jakarta.servlet.Filter>((request, response, chain) ->
                    chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest) request) {
                        @Override
                        public Collection<Part> getParts() throws IOException, ServletException {
                            multipartParsingAttempts.incrementAndGet();
                            return super.getParts();
                        }

                        @Override
                        public Part getPart(String name) throws IOException, ServletException {
                            multipartParsingAttempts.incrementAndGet();
                            return super.getPart(name);
                        }
                    }, response));
            registration.setDispatcherTypes(DispatcherType.REQUEST);
            registration.addUrlPatterns(RoundController.BASE + "/*");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }
    }
}

@SpringBootTest(classes = {RoundMultipartHttpTest.Fixture.class, RoundFileResolverHttpTest.FilesConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "server.ssl.enabled=false", "ctsv.foundation.enabled=true",
                "ctsv.auth.origins[0]=https://ctsv.example.test", "spring.servlet.multipart.resolve-lazily=false",
                "ctsv.files.enabled=true", "ctsv.files.reconciliation.enabled=false",
                "ctsv.files.cloudinary.enabled=false"})
@ActiveProfiles("test")
class RoundFileResolverHttpTest extends RoundMultipartCases {
    @TestComponent
    @Configuration(proxyBeanMethods = false)
    @Import(FileConfigurationImport.class)
    static class FilesConfiguration {}

    static class FileConfigurationImport implements ImportSelector {
        @Override
        public String[] selectImports(AnnotationMetadata metadata) {
            return new String[]{"pbl6.ctsv.shared.files.FileConfiguration"};
        }
    }
}

/** HB-T00-001, 005 and 008 over loopback HTTP/Tomcat. Auth, Foundation, transaction and HB service are MOCK. */
abstract class RoundMultipartCases {
    private static final String ROUNDS = RoundController.BASE + "/kkht/dot-xet";
    private static final AuthAccess.Principal STUDENT = new AuthAccess.Principal(
            "synthetic", UUID.fromString("23456789-1234-1234-1234-123456789012"), 0);
    private static final AuthAccess.Principal SPECIALIST = new AuthAccess.Principal(
            "synthetic", UUID.fromString("23456789-1234-1234-1234-123456789013"), 0);

    @LocalServerPort int port;
    @Autowired AuthAccess auth;
    @Autowired FoundationAccess identities;
    @Autowired FoundationRoleAccess roles;
    @Autowired PlatformTransactionManager manager;
    @Autowired RoundService service;
    @Autowired ObjectMapper json;
    @Autowired AtomicInteger multipartParsingAttempts;
    @Autowired @Qualifier(DispatcherServlet.MULTIPART_RESOLVER_BEAN_NAME)
    StandardServletMultipartResolver multipartResolver;

    @BeforeEach
    void fixture() {
        reset(auth, identities, roles, manager, service);
        multipartParsingAttempts.set(0);
        when(auth.verifyAccessToken("student")).thenReturn(STUDENT);
        when(auth.verifyAccessToken("specialist")).thenReturn(SPECIALIST);
        when(identities.identify(STUDENT, true)).thenReturn(new FoundationAccess.Identity(301, 71L, STUDENT));
        when(identities.identify(SPECIALIST, true)).thenReturn(new FoundationAccess.Identity(302, null, SPECIALIST));
        when(roles.findActiveGlobalRole(301, "HOC_BONG", "hoc_bong_chuyen_vien")).thenReturn(Optional.empty());
        when(roles.findActiveGlobalRole(302, "HOC_BONG", "hoc_bong_chuyen_vien"))
                .thenReturn(Optional.of(new FoundationRoleAccess.ActiveRole(74)));
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
    }

    @ParameterizedTest
    @CsvSource({"POST, , 401, UNAUTHENTICATED", "POST, student, 403, FORBIDDEN",
            "POST, specialist, 415, UNSUPPORTED_MEDIA_TYPE", "PUT, , 401, UNAUTHENTICATED",
            "PUT, student, 403, FORBIDDEN", "PUT, specialist, 415, UNSUPPORTED_MEDIA_TYPE"})
    void deniesMalformedMultipartWithoutAskingTomcatToParseParts(String method, String bearer, int status, String code)
            throws Exception {
        String path = method.equals("POST") ? ROUNDS : ROUNDS + "/1001";
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data")
                .method(method, HttpRequest.BodyPublishers.ofString("--invalid--\r\n"));
        if (bearer != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build()) {
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(status);
            assertThat(response.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
            assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE)).contains("application/problem+json");
            assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo(code);
        }
        assertThat(multipartParsingAttempts).hasValue(0);
        verifyNoInteractions(service);
    }
}
