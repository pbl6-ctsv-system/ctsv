package pbl6.ctsv.foundation.web;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import pbl6.ctsv.auth.AuthAccess;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="ctsv.foundation.enabled",havingValue="true")
class FoundationConfiguration {
 @Bean FoundationSecurity foundationSecurity(AuthAccess auth,JdbcTemplate jdbc){return new FoundationSecurity(auth,jdbc);}
 @Bean pbl6.ctsv.foundation.repository.FoundationRepository foundationRepository(jakarta.persistence.EntityManager em,JdbcTemplate jdbc){return new pbl6.ctsv.foundation.repository.FoundationRepository(em,jdbc);}
 @Bean FoundationService foundationService(FoundationSecurity security,pbl6.ctsv.foundation.repository.FoundationRepository repository,PlatformTransactionManager tx,ObjectMapper json,pbl6.ctsv.foundation.api.FoundationUsage usage){return new FoundationService(security,repository,tx,json,usage);}
 @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.CorsFilter> foundationCors(org.springframework.core.env.Environment env){
  var origins=org.springframework.boot.context.properties.bind.Binder.get(env).bind("ctsv.auth.origins",org.springframework.boot.context.properties.bind.Bindable.listOf(String.class)).orElseThrow(()->new IllegalStateException("Auth origins required"));
  var policy=new org.springframework.web.cors.CorsConfiguration();policy.setAllowedOrigins(origins);policy.setAllowCredentials(true);
  policy.setAllowedMethods(java.util.List.of("GET","POST","PUT","DELETE","OPTIONS"));policy.setAllowedHeaders(java.util.List.of("Authorization","Content-Type","If-Match"));policy.setExposedHeaders(java.util.List.of("ETag"));
  var source=new org.springframework.web.cors.UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/ctsv/nen-tang/**",policy);
  var filter=new org.springframework.web.filter.CorsFilter(source){
   @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,jakarta.servlet.FilterChain chain)throws java.io.IOException,jakarta.servlet.ServletException{
    response.setHeader("Cache-Control","no-store");super.doFilterInternal(request,response,chain);
   }
  };
  var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<org.springframework.web.filter.CorsFilter>(filter);registration.addUrlPatterns("/api/ctsv/nen-tang/*");return registration;
 }
}
