package pbl6.ctsv.shcn.phancongkhoa.http;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,classes={pbl6.ctsv.CtsvApplication.class,PhanCongKhoaHttpIT.Database.class},properties={
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
    "spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.open-in-view=false",
    "ctsv.auth.enabled=true","ctsv.auth.bootstrap-enabled=false","ctsv.foundation.enabled=true",
    "ctsv.shcn.persistence.enabled=true","server.address=127.0.0.1","server.ssl.enabled=false"})
class PhanCongKhoaHttpIT {
    @TestConfiguration(proxyBeanMethods=false) static class Database {
        @Bean DataSource shcnApiSource()throws Exception{return ShcnApiDatabase.source();}
    }
    @LocalServerPort int port;
    @DynamicPropertySource static void keys(DynamicPropertyRegistry registry)throws Exception{
        var gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);var pair=gen.generateKeyPair();
        Path privateKey=pem("PRIVATE KEY",pair.getPrivate().getEncoded()),publicKey=pem("PUBLIC KEY",pair.getPublic().getEncoded());
        registry.add("ctsv.auth.issuer",()->"https://auth.shcn.test");registry.add("ctsv.auth.audience",()->"ctsv");
        registry.add("ctsv.auth.signing-kid",()->"shcn-test");registry.add("ctsv.auth.private-key",privateKey::toString);
        registry.add("ctsv.auth.public-keys[shcn-test]",publicKey::toString);registry.add("ctsv.auth.origins[0]",()->"https://web.shcn.test");
    }
    private static Path pem(String label,byte[] bytes)throws Exception{
        Path path=Files.createTempFile("shcn-api-key-",".pem");path.toFile().deleteOnExit();
        Files.writeString(path,"-----BEGIN "+label+"-----\n"+Base64.getEncoder().encodeToString(bytes)+"\n-----END "+label+"-----");return path;
    }
    @Test void scopeEndpointRejectsMissingBearerWith401()throws Exception{
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/shcn/khoa/pham-vi")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
    }
}
