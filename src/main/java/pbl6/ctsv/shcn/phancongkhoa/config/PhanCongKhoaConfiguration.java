package pbl6.ctsv.shcn.phancongkhoa.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shared.ca.CaDutClient;
import pbl6.ctsv.shared.files.FileReadPolicy;
import pbl6.ctsv.shared.files.ManagedFiles;
import pbl6.ctsv.shcn.foundation.*;
import pbl6.ctsv.shcn.phancongkhoa.pdf.PhanCongPdfGenerator;
import pbl6.ctsv.shcn.phancongkhoa.repository.*;
import pbl6.ctsv.shcn.phancongkhoa.service.PhanCongKhoaService;
import pbl6.ctsv.shcn.phancongkhoa.signing.*;
import pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
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
    @Bean PhanCongPdfGenerator shcnFacultyPdfGenerator(){return new PhanCongPdfGenerator();}
    @Bean PhanCongSigningOptions shcnFacultySigningOptions(Environment env){return new PhanCongSigningOptions(env);}
    @Bean PhanCongSigningRepository shcnFacultySigningRepository(JdbcTemplate jdbc,ObjectMapper json){
        return new PhanCongSigningRepository(jdbc,json);
    }
    @Bean FileReadPolicy shcnFacultyFileReadPolicy(PhanCongSigningRepository signingRepo,PhanCongKhoaFoundationPort read){
        return new ShcnFileReadPolicy(signingRepo,read);
    }
    @Bean PhanCongSigningGateway shcnFacultyCaGateway(ObjectProvider<CaDutClient> clients){
        return PhanCongSigningGateway.fromProvider(clients);
    }
    @Bean PhanCongSigningWorker shcnFacultySigningWorker(PlatformTransactionManager manager,AuthAccess auth,
            FoundationAccess identity,PhanCongKhoaFoundationPort read,PhanCongKhoaRepository listRepo,
            PhanCongSigningRepository signingRepo,PhanCongKhoaAudit audit,PhanCongSigningGateway ca,
            ObjectProvider<ManagedFiles> files){
        return new PhanCongSigningWorker(manager,auth,identity,read,listRepo,signingRepo,audit,ca,files,Clock.systemUTC());
    }
    @Bean PhanCongSigningService shcnFacultySigningService(PlatformTransactionManager manager,FoundationAccess identity,
            PhanCongKhoaFoundationPort read,PhanCongKhoaRepository listRepo,PhanCongSigningRepository signingRepo,
            PhanCongKhoaService phanCongService,PhanCongKhoaAudit audit,PhanCongPdfGenerator pdf,
            PhanCongSigningOptions options,ObjectProvider<ManagedFiles> files,PhanCongSigningWorker worker){
        return new PhanCongSigningService(manager,identity,read,listRepo,signingRepo,phanCongService,audit,pdf,options,files,worker,Clock.systemUTC());
    }
    @Bean
    @ConditionalOnProperty(name="ctsv.shcn.signing.enabled",havingValue="true")
    PhanCongSigningScheduler shcnFacultySigningScheduler(PhanCongSigningWorker worker){
        return new PhanCongSigningScheduler(worker);
    }
    @Bean FilterRegistrationBean<CorsFilter> shcnFacultyCors(Environment env){
        var origins=Binder.get(env).bind("ctsv.auth.origins",Bindable.listOf(String.class)).orElse(List.of());
        var policy=new CorsConfiguration();policy.setAllowedOrigins(origins);
        policy.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        policy.setAllowedHeaders(List.of("Authorization","Content-Type","If-Match"));
        policy.setExposedHeaders(List.of("Location","ETag"));
        policy.setAllowCredentials(true);policy.setMaxAge(600L);
        var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/shcn/khoa/**",policy);
        var registration=new FilterRegistrationBean<>(new CorsFilter(source));registration.addUrlPatterns("/api/shcn/khoa/*");return registration;
    }
}
