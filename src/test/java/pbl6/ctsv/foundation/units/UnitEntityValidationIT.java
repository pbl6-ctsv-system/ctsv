package pbl6.ctsv.foundation.units;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import pbl6.p00.SliceATestDatabaseSupport;
import static org.assertj.core.api.Assertions.assertThat;

/** Hibernate ddl-auto=validate against the migrated, guarded CTSV SQL Server database. */
@SpringBootTest(properties={
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(UnitEntityValidationIT.Database.class)
class UnitEntityValidationIT {
    @TestConfiguration(proxyBeanMethods=false)
    static class Database {
        @Bean DataSource dataSource() throws Exception {
            SliceATestDatabaseSupport.migrate();
            return SliceATestDatabaseSupport.dataSource();
        }
    }
    @Autowired EntityManagerFactory factory;
    @Test void sa001_foundationEntityIsMappedAndValidatedOnRealSchema() {
        assertThat(factory.getMetamodel().getEntities()).extracting(e->e.getName()).contains("DonVi");
        var manager=factory.createEntityManager();
        try { assertThat(manager.createQuery("select d from DonVi d").setMaxResults(1).getResultList()).isNotNull(); }
        finally { manager.close(); }
    }
}
