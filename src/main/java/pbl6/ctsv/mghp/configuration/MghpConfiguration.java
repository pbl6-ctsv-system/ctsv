package pbl6.ctsv.mghp.configuration;
import com.fasterxml.jackson.databind.ObjectMapper;import java.time.Clock;import java.util.*;import org.springframework.context.annotation.*;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.PlatformTransactionManager;import pbl6.ctsv.foundation.api.FoundationAccess;
@Configuration(proxyBeanMethods=false) @ConditionalOnProperty(name="ctsv.mghp.configuration.enabled",havingValue="true")
class MghpConfiguration {
 @Bean ConfigurationRepository mghpConfigurationRepository(JdbcTemplate jdbc,ObjectMapper json){return new ConfigurationRepository(jdbc,json);}
 @Bean ConfigurationAccess mghpConfigurationAccess(PlatformTransactionManager tx,FoundationAccess access,ConfigurationRepository r){return new ConfigurationAccess(tx,access,r);}
 @Bean PolicyService mghpPolicyService(ConfigurationRepository r,ObjectMapper json,Clock clock){return new PolicyService(r,json,clock);}
 @Bean RoundService mghpRoundService(ConfigurationRepository r,ObjectMapper json,Clock clock){return new RoundService(r,json,clock);}
 @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.CorsFilter> mghpConfigurationCors(org.springframework.core.env.Environment env){
  var origins=org.springframework.boot.context.properties.bind.Binder.get(env).bind("ctsv.auth.origins",org.springframework.boot.context.properties.bind.Bindable.listOf(String.class)).orElseThrow(()->new IllegalStateException("Auth origins required"));var p=new org.springframework.web.cors.CorsConfiguration();p.setAllowedOrigins(origins);p.setAllowCredentials(true);p.setAllowedMethods(List.of("GET","POST","PUT","OPTIONS"));p.setAllowedHeaders(List.of("Authorization","Content-Type","If-Match"));p.setExposedHeaders(List.of("ETag","Location"));var s=new org.springframework.web.cors.UrlBasedCorsConfigurationSource();s.registerCorsConfiguration("/api/ctsv/mghp/**",p);var r=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new org.springframework.web.filter.CorsFilter(s));r.addUrlPatterns("/api/ctsv/mghp/*");return r;
 }
}
