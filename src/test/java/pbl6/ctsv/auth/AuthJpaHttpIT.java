package pbl6.ctsv.auth;

import javax.sql.DataSource;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import pbl6.ctsv.foundation.persistence.RefactorDatabase;

/** Select inherited Auth HTTPS regressions against the shared JPA transaction infrastructure. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes={pbl6.ctsv.CtsvApplication.class,AuthJpaHttpIT.Config.class},
    properties={"ctsv.auth.enabled=true","server.address=127.0.0.1","server.forward-headers-strategy=none"})
class AuthJpaHttpIT extends AuthHttpIT {
    @TestConfiguration(proxyBeanMethods=false) static class Config extends AuthHttpIT.TestDataSource {
        @Override @Bean DataSource testDataSource() throws Exception { return RefactorDatabase.source(); }
    }
}
