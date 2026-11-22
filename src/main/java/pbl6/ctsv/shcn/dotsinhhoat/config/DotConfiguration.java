package pbl6.ctsv.shcn.dotsinhhoat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
import pbl6.ctsv.foundation.api.*;
import pbl6.ctsv.shcn.dotsinhhoat.reference.*;
import pbl6.ctsv.shcn.dotsinhhoat.repository.DotRepository;
import pbl6.ctsv.shcn.dotsinhhoat.service.DotService;
import pbl6.ctsv.shcn.dotsinhhoat.validation.DotRequests;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="ctsv.shcn.persistence.enabled",havingValue="true")
public class DotConfiguration {
    @Bean DotRequests shcnDotRequests(ObjectMapper json){return new DotRequests(json);}
    @Bean DotRepository shcnDotRepository(EntityManager em,JdbcTemplate jdbc,ObjectMapper json){return new DotRepository(em,jdbc,json);}
    @Bean DotReference shcnDotReference(JdbcTemplate jdbc,AcademicSemesterSource semesters,@Value("${ctsv.shcn.dot.source-code:${ctsv.shcn.phan-cong-khoa.source-code:}}")String source){return new JdbcDotReference(jdbc,semesters,source);}
    @Bean DotService shcnDotService(PlatformTransactionManager manager,FoundationAccess identity,DotReference reference,DotRepository repository){return new DotService(manager,identity,reference,repository,Clock.systemUTC());}
    @Bean FilterRegistrationBean<CorsFilter> shcnDotCors(Environment env){
        var origins=Binder.get(env).bind("ctsv.auth.origins",Bindable.listOf(String.class)).orElse(List.of());var policy=new CorsConfiguration();policy.setAllowedOrigins(origins);policy.setAllowedMethods(List.of("GET","POST","PATCH","OPTIONS"));
        policy.setAllowedHeaders(List.of("Authorization","Content-Type","Accept","If-Match","X-CSRF-Protection"));policy.setExposedHeaders(List.of("Location","X-SHCN-Can-Prepare"));policy.setAllowCredentials(true);policy.setMaxAge(600L);
        var paths=new UrlBasedCorsConfigurationSource();paths.registerCorsConfiguration("/api/shcn/dot-sinh-hoat",policy);paths.registerCorsConfiguration("/api/shcn/dot-sinh-hoat/**",policy);paths.registerCorsConfiguration("/api/shcn/tra-cuu/hoc-ky",policy);
        var filter=new FilterRegistrationBean<>(new CorsFilter(paths));filter.addUrlPatterns("/api/shcn/dot-sinh-hoat","/api/shcn/dot-sinh-hoat/*","/api/shcn/tra-cuu/hoc-ky");return filter;
    }
}
