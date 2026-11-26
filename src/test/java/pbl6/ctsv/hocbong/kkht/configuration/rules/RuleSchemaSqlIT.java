package pbl6.ctsv.hocbong.kkht.configuration.rules;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pbl6.ctsv.hocbong.kkht.round.ScholarshipTestDatabase;
import static org.assertj.core.api.Assertions.assertThat;

class RuleSchemaSqlIT {
    @Test void HB_RULE_10_migrationCreatesRuleTableAndTrustedRoundFkWithoutOrphans() throws Exception {
        var jdbc=new JdbcTemplate(ScholarshipTestDatabase.migrate());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE name='kkht_phien_ban_quy_tac' AND schema_id=SCHEMA_ID('dbo')",Integer.class)).as("T03.3 rule table").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.foreign_keys WHERE name='fk_kkht_dot_xet_rule_version' AND is_disabled=0 AND is_not_trusted=0",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.kkht_dot_xet r LEFT JOIN dbo.kkht_phien_ban_quy_tac p ON p.id=r.quy_tac_id WHERE r.quy_tac_id IS NOT NULL AND p.id IS NULL",Long.class)).isZero();
    }
}
