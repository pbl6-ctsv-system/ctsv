package pbl6.ctsv.foundation.web;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationUsage;
import pbl6.ctsv.foundation.api.ScholarshipCombinationSource;
import pbl6.ctsv.foundation.repository.FoundationRepository;
import pbl6.ctsv.foundation.repository.ScholarshipCombinationRepository;
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="ctsv.foundation.enabled",havingValue="true")
class FoundationConfiguration {
 @Bean FoundationSecurity foundationSecurity(AuthAccess auth,JdbcTemplate jdbc){return new FoundationSecurity(auth,jdbc);}
 @Bean pbl6.ctsv.foundation.repository.FoundationRepository foundationRepository(jakarta.persistence.EntityManager em,JdbcTemplate jdbc){return new pbl6.ctsv.foundation.repository.FoundationRepository(em,jdbc);}
 @Bean pbl6.ctsv.foundation.api.ScholarshipScopeSource scholarshipScopeSource(JdbcTemplate jdbc,FoundationRepository repository,ObjectMapper json) {
  return new pbl6.ctsv.foundation.repository.ScholarshipScopeRepository(jdbc,repository,json);
 }
 @Bean ScholarshipCombinationSource scholarshipCombinationSource(EntityManager em,JdbcTemplate jdbc) {
  return new ScholarshipCombinationRepository(em,jdbc);
 }
 @Bean
 FoundationService foundationService(FoundationSecurity security, FoundationRepository repository,
         PlatformTransactionManager tx, ObjectMapper json, List<FoundationUsage> usages) {
  return new FoundationService(security, repository, tx, json, new CompositeFoundationUsage(usages));
 }

 private record CompositeFoundationUsage(List<FoundationUsage> usages) implements FoundationUsage {
  private CompositeFoundationUsage {
   usages = List.copyOf(usages);
  }

  @Override
  public boolean semesterUsed(long id) {
   return usages.stream().anyMatch(usage -> usage.semesterUsed(id));
  }

  @Override
  public boolean processUsed(long id) {
   return usages.stream().anyMatch(usage -> usage.processUsed(id));
  }
 }

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
