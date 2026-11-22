package pbl6.ctsv.mghp.application;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class DecisionSqlIT {
 @Test void upgradeV21PreservesRowsAndAddsOnlyP10FencesAndConstraints()throws Exception{
  var ds=DecisionDatabase.source();var j=new JdbcTemplate(ds);
  // Reruns never downgrade or reset; first execution proves an actual V21 upgrade.
  var exists=j.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE name=N'flyway_schema_history'",Integer.class)>0;
  boolean already=exists&&j.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='22' AND success=1",Integer.class)>0;
  if(!already)DecisionDatabase.flyway(ds,"21").migrate();
  var before=j.queryForList("SELECT id,ma_dot_xet,ten FROM dbo.mghp_dot_xet ORDER BY id");
  var f=DecisionDatabase.flyway(ds,null);f.migrate();f.validate();
  assertThat(j.queryForList("SELECT id,ma_dot_xet,ten FROM dbo.mghp_dot_xet ORDER BY id")).isEqualTo(before);
  assertThat(f.info().current().getVersion().toString()).isEqualTo("23");
  assertThat(j.queryForObject("SELECT COUNT(*) FROM sys.indexes WHERE name=N'uq_p10_one_root_per_round' AND is_unique=1",Integer.class)).isEqualTo(1);
  assertThat(j.queryForObject("SELECT COUNT(*) FROM sys.triggers WHERE name LIKE N'tr_p10_%' AND is_disabled=0",Integer.class)).isEqualTo(8);
  assertThat(j.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.mghp_phien_ban_qd') AND name IN(N'ma_qd_luc_chot',N'sha256_nguon',N'phien_ban_ky_thuat')",Integer.class)).isEqualTo(3);
 }
}

