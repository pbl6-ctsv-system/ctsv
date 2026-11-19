package pbl6.ctsv.shcn.phancongkhoa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shcn.foundation.*;
import pbl6.ctsv.shcn.phancongkhoa.repository.*;
import pbl6.ctsv.shcn.phancongkhoa.service.PhanCongKhoaService;
import pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="ctsv.shcn.persistence.enabled",havingValue="true")
@EnableConfigurationProperties(PhanCongKhoaSettings.class)
public class PhanCongKhoaConfiguration {
    @Bean PhanCongKhoaRequests shcnRequests(ObjectMapper json){return new PhanCongKhoaRequests(json);}
    @Bean PhanCongKhoaFoundationPort shcnFoundationRead(JdbcTemplate jdbc,PhanCongKhoaSettings settings){return new JdbcPhanCongKhoaFoundation(jdbc,settings,Clock.systemUTC());}
    @Bean PhanCongKhoaAudit shcnFacultyAudit(JdbcTemplate jdbc){return new PhanCongKhoaAudit(jdbc);}
    @Bean PhanCongKhoaService shcnFacultyService(PlatformTransactionManager manager,FoundationAccess identity,
            PhanCongKhoaFoundationPort read,PhanCongKhoaRepository repository,PhanCongKhoaAudit audit){
        return new PhanCongKhoaService(manager,identity,read,repository,audit,Clock.systemUTC());
    }
    @Bean FilterRegistrationBean<CorsFilter> shcnFacultyCors(Environment env){
        var origins=Binder.get(env).bind("ctsv.auth.origins",Bindable.listOf(String.class)).orElse(List.of());
        var policy=new CorsConfiguration();policy.setAllowedOrigins(origins);
        policy.setAllowedMethods(List.of("GET","POST","PUT","OPTIONS"));
        policy.setAllowedHeaders(List.of("Authorization","Content-Type","If-Match"));policy.setExposedHeaders(List.of("Location"));
        policy.setAllowCredentials(true);policy.setMaxAge(600L);
        var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/shcn/khoa/**",policy);
        var registration=new FilterRegistrationBean<>(new CorsFilter(source));registration.addUrlPatterns("/api/shcn/khoa/*");return registration;
    }
}
