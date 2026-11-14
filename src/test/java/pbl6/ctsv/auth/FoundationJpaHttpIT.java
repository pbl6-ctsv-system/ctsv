package pbl6.ctsv.auth;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import pbl6.ctsv.foundation.persistence.RefactorDatabase;
import static org.assertj.core.api.Assertions.*;

/** Reuse the existing FND HTTP/authorization/fault/race assertions on a separate refactor DB. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes={pbl6.ctsv.CtsvApplication.class,FoundationJpaHttpIT.Config.class},
    properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","server.address=127.0.0.1"})
class FoundationJpaHttpIT extends FoundationHttpIT {
    @TestConfiguration(proxyBeanMethods=false) static class Config extends FoundationHttpIT.Config {
        @Override @Bean DataSource p04Source() throws Exception { return RefactorDatabase.source(); }
    }
    @Override @Test void migrationHasThreeConcurrencyRootsAndCurrentRoleUniqueness() {
        // This regression uses the current full schema V8, rather than the historical P04-only V7 baseline.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success=1",Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE OBJECT_SCHEMA_NAME(object_id)='dbo' AND OBJECT_NAME(object_id) IN ('hoc_ky','nguoi_dung','qua_trinh_theo_hoc') AND name='phien_ban' AND system_type_id=189",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.indexes WHERE name='ux_foundation_current_role' AND is_unique=1 AND has_filter=1",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.vai_tro WHERE ma IN ('mghp_chuyen_vien','mghp_pho_phong','ctsv.roles.manage')",Integer.class)).isEqualTo(3);
    }
}
