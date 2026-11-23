package pbl6.ctsv.shared.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPatternParser;
import pbl6.ctsv.foundation.api.FoundationAccess;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
@ConditionalOnProperty(name="ctsv.files.enabled",havingValue="true")
class FileConfiguration {
    @Bean
    FileService fileService(FoundationAccess access, ObjectProvider<PrivateFileStorage> storage,
                            PlatformTransactionManager manager, JdbcTemplate jdbc,ObjectProvider<FileReadPolicy> policies) {
        return new FileService(access,storage,new TransactionTemplate(manager),new FileMetadata(jdbc),new UploadLedger(jdbc),policies);
    }

    @Bean
    @ConditionalOnProperty(name="ctsv.files.cloudinary.enabled",havingValue="true")
    CloudinaryFileStorage cloudinaryFileStorage(Environment env, ObjectMapper json) {
        var names = List.of("CLOUDINARY_CLOUD_NAME","CLOUDINARY_API_KEY","CLOUDINARY_API_SECRET",
            "CLOUDINARY_FOLDER","CLOUDINARY_FOLDER_MODE");
        var values = names.stream().map(System::getenv).toArray(String[]::new);
        if (Arrays.stream(values).anyMatch(value -> value==null || value.isBlank())) {
            throw new IllegalStateException("Missing Cloudinary environment configuration");
        }
        var timeout = Binder.get(env).bind("ctsv.files.cloudinary.timeout",Duration.class).orElse(Duration.ofSeconds(30));
        return new CloudinaryFileStorage(values[0],values[1],values[2],values[3],values[4],timeout,json);
    }

    @Bean(name=DispatcherServlet.MULTIPART_RESOLVER_BEAN_NAME)
    StandardServletMultipartResolver fileMultipartResolver() {
        var evidence=PathPatternParser.defaultInstance.parse(FileController.ROOT+"/minh-chung");
        var document=PathPatternParser.defaultInstance.parse(FileController.ROOT+"/van-ban");
        return new StandardServletMultipartResolver() {
            @Override public boolean isMultipart(HttpServletRequest request) {
                if ("POST".equals(request.getMethod())) {
                    var path=ServletRequestPathUtils.hasParsedRequestPath(request) ? ServletRequestPathUtils.getParsedRequestPath(request)
                        : ServletRequestPathUtils.parseAndCache(request);
                    if (evidence.matches(path.pathWithinApplication()) || document.matches(path.pathWithinApplication())) return false;
                }
                return super.isMultipart(request);
            }
        };
    }

    @Bean
    FilterRegistrationBean<CorsFilter> fileCors(Environment env) {
        var config=new CorsConfiguration();
        config.setAllowedOrigins(Binder.get(env).bind("ctsv.auth.origins",Bindable.listOf(String.class))
            .orElseThrow(() -> new IllegalStateException("Auth origins required")));
        config.setAllowedMethods(List.of("GET","POST","OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type"));
        config.setExposedHeaders(List.of("Location","Content-Disposition"));
        var source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(FileController.ROOT+"/**",config);
        var filter=new FilterRegistrationBean<>(new CorsFilter(source));
        filter.addUrlPatterns(FileController.ROOT+"/*");
        return filter;
    }

    @Bean
    @ConditionalOnProperty(name="ctsv.files.reconciliation.enabled",havingValue="true",matchIfMissing=true)
    FileReconciliation fileReconciliation(FileService files) { return new FileReconciliation(files); }

    record FileReconciliation(FileService files) {
        @Scheduled(initialDelayString="PT1M",fixedDelayString="PT1M")
        public void reconcile() {
            try {
                files.reconcile();
            } catch (RuntimeException failure) {
                org.slf4j.LoggerFactory.getLogger(FileReconciliation.class).warn("PDF reconciliation delayed");
            }
        }
    }
}
