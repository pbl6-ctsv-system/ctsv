package pbl6.ctsv.mghp.application;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named="p11.test.enabled",matches="true")
class SigningSchemaIT {
 @Test void additiveSchemaHasDurableFencingAndImmutablePerStepOutputs()throws Exception{
  var ds=SigningDatabase.source();var prior=new JdbcTemplate(ds);long grantsBefore=prior.queryForObject("SELECT COUNT_BIG(*) FROM dbo.phan_quyen_nguoi_dung",Long.class);
  var jdbc=new JdbcTemplate(SigningDatabase.migrate());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.yeu_cau_ky') AND name='phien_ban_ky_thuat'",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID('dbo.lan_goi_dich_vu_ky') AND name IN('giai_doan_xu_ly','lease_token','lease_den','chu_the_auth','phien_ban_auth','tep_dau_ra_id')",Integer.class)).isEqualTo(6);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.vai_tro WHERE ma=N'mghp_pho_hieu_truong'",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.phan_quyen_nguoi_dung",Long.class)).isEqualTo(grantsBefore);
 }
}
