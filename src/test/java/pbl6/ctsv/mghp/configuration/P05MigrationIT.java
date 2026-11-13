package pbl6.ctsv.mghp.configuration;
import org.junit.jupiter.api.*;import org.springframework.jdbc.core.JdbcTemplate;import static org.assertj.core.api.Assertions.*;
class P05MigrationIT {
 @Test void P05_01_root_versions_exist_on_real_sql()throws Exception{
  var j=new JdbcTemplate(P05Database.migrate());
  assertThat(j.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.mghp_phien_ban_chinh_sach') AND name='phien_ban_ky_thuat' AND system_type_id=189",Integer.class)).isEqualTo(1);
  assertThat(j.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.mghp_dot_xet') AND name='phien_ban' AND system_type_id=189",Integer.class)).isEqualTo(1);
  var aliases=P05Fixtures.seed(P05Database.source());long locked=P05Fixtures.alias(aliases,"mghp_phien_ban_chinh_sach",102);
  assertThatThrownBy(()->j.update("UPDATE dbo.mghp_phien_ban_chinh_sach SET trang_thai=N'draft' WHERE id=?",locked)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->j.update("UPDATE dbo.mghp_tieu_chi_chinh_sach SET ten=N'MUST NOT COMMIT' WHERE phien_ban_chinh_sach_id=?",locked)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThat(j.queryForObject("SELECT trang_thai FROM dbo.mghp_phien_ban_chinh_sach WHERE id=?",String.class,locked)).isEqualTo("locked");
 }
 @Test void CFG_F_source_fixture_is_available(){assertThat(getClass().getResource("/fixtures/p05/configuration.json")).isNotNull();}
 @Test void CFG_F_159_rows_seven_tables_keep_valid_FKs_and_are_repeatable()throws Exception{
  var ds=P05Database.migrate();var j=new JdbcTemplate(ds);var aliases=P05Fixtures.seed(ds);int total=0;
  for(var table:P05Fixtures.TABLES){var ids=aliases.path(table);int count=0;for(var v:ids){assertThat(j.queryForObject("SELECT COUNT(*) FROM dbo."+table+" WHERE id=?",Integer.class,v.asLong())).isEqualTo(1);count++;}total+=count;}
  assertThat(total).isEqualTo(159);assertThat(P05Fixtures.seed(ds)).isEqualTo(aliases);
  assertThat(j.queryForObject("SELECT COUNT(*) FROM sys.foreign_keys WHERE is_disabled=1 OR is_not_trusted=1",Integer.class)).isZero();
 }
}
