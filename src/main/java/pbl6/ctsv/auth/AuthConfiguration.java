package pbl6.ctsv.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.security.KeyFactory;
import java.security.interfaces.*;
import java.security.spec.*;
import java.time.Clock;
import java.util.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="ctsv.auth.enabled",havingValue="true")
@EnableConfigurationProperties(AuthSettings.class)
class AuthConfiguration {
    @Bean AuthCredentials authCredentials(){return new AuthCredentials();}
    @Bean AuthLoginLimiter authLoginLimiter(){return new AuthLoginLimiter();}
    @Bean AuthRepository authRepository(JdbcTemplate jdbc){return new AuthRepository(jdbc);}
    @Bean Clock authClock(){return Clock.systemUTC();}
    @Bean AuthJwt authJwt(AuthSettings settings,Clock authClock) {
        try{
            KeyFactory factory=KeyFactory.getInstance("RSA");
            var pub=new HashMap<String,RSAPublicKey>();
            for(var entry:settings.publicKeys().entrySet())
                pub.put(entry.getKey(),(RSAPublicKey)factory.generatePublic(new X509EncodedKeySpec(pem(entry.getValue(),"PUBLIC KEY"))));
            var pk=(RSAPrivateKey)factory.generatePrivate(new PKCS8EncodedKeySpec(pem(settings.privateKey(),"PRIVATE KEY")));
            return new AuthJwt(settings.issuer(),settings.audience(),settings.signingKid(),pk,pub,authClock);
        }catch(java.io.IOException|java.security.GeneralSecurityException|IllegalArgumentException e){
            throw new IllegalStateException("Auth key configuration could not be loaded");
        }
    }
    @Bean AuthService authService(AuthRepository db,AuthCredentials passwords,Clock authClock,AuthJwt jwt,
                                  AuthLoginLimiter limiter,PlatformTransactionManager manager){
        return new AuthService(db,passwords,authClock,jwt,limiter,manager);
    }
    @Bean AuthAdministration authAdministration(AuthService service,AuthSettings settings,ObjectMapper json){
        return new AuthAdministration(service,settings.trustedSources(),json);
    }
    @Bean FilterRegistrationBean<OncePerRequestFilter> authCors(AuthSettings settings){
        var config=new CorsConfiguration();
        config.setAllowedOrigins(List.copyOf(settings.origins()));
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type","If-Match","X-CSRF-Protection"));
        config.setExposedHeaders(List.of("ETag","Location","Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(600L);
        var processor=new DefaultCorsProcessor();
        OncePerRequestFilter filter=new OncePerRequestFilter(){
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
                    throws java.io.IOException,ServletException {
                response.setHeader("Cache-Control","no-store");
                if(CorsUtils.isPreFlightRequest(request)){
                    processor.processRequest(config,request,response);
                    return;
                }
                if(settings.origins().contains(Objects.toString(request.getHeader("Origin"),""))
                    && !processor.processRequest(config,request,response)) return;
                chain.doFilter(request,response);
            }
        };
        var registration=new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/xac-thuc/*");
        return registration;
    }
    @Bean
    @ConditionalOnProperty(name="ctsv.auth.bootstrap-enabled",havingValue="true")
    ApplicationRunner authBootstrap(AuthAdministration admin,Environment env){
        return args->admin.bootstrap(env.getRequiredProperty("CTSV_AUTH_BOOTSTRAP_USERNAME"),env.getRequiredProperty("CTSV_AUTH_BOOTSTRAP_PASSWORD"));
    }

    private static byte[] pem(Path path,String label)throws java.io.IOException{
        if(Files.size(path)>32768) throw new IllegalArgumentException();
        String data=Files.readString(path).strip();
        String start="-----BEGIN "+label+"-----",end="-----END "+label+"-----";
        if(!data.startsWith(start)||!data.endsWith(end)) throw new IllegalArgumentException();
        return Base64.getDecoder().decode(data.substring(start.length(),data.length()-end.length()).replaceAll("\\s",""));
    }
}
