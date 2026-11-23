package pbl6.ctsv.mghp.application;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.core.env.Environment;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;

/** The new shared-document URLs need the same reviewed origin allowlist as MGHP URLs. */
@Configuration(proxyBeanMethods=false) @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningWebConfiguration {
 @Bean FilterRegistrationBean<CorsFilter> signingCors(Environment env){
  var policy=new CorsConfiguration();policy.setAllowedOrigins(Binder.get(env).bind("ctsv.auth.origins",Bindable.listOf(String.class)).orElseThrow(()->new IllegalStateException("Auth origins required")));
  policy.setAllowedMethods(List.of("GET","POST","OPTIONS"));policy.setAllowedHeaders(List.of("Authorization","Content-Type","If-Match"));policy.setExposedHeaders(List.of("ETag","Location"));
  var source=new UrlBasedCorsConfigurationSource();var filter=new FilterRegistrationBean<>(new CorsFilter(source));
  for(String root:List.of("phien-ban-van-ban","yeu-cau-ky","buoc-ky","can-cu-gui-ky")){source.registerCorsConfiguration("/api/ctsv/"+root+"/**",policy);filter.addUrlPatterns("/api/ctsv/"+root+"/*");}
  source.registerCorsConfiguration("/api/ctsv/tai-lieu-ky",policy);filter.addUrlPatterns("/api/ctsv/tai-lieu-ky");return filter;
 }
}
