package pbl6.ctsv.mghp.application;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named="signing.support.test.enabled",matches="true")
class SigningSupportSchemaIT {
 @Test void additiveTablesRoleAndHistoryGuardsExistOnApprovedSqlTarget()throws Exception{
  var beforeJdbc=new JdbcTemplate(SigningSupportDatabase.source());
  boolean hasBaseline=beforeJdbc.queryForObject("SELECT IIF(OBJECT_ID(N'dbo.phan_quyen_nguoi_dung',N'U') IS NULL,0,1)",Integer.class)==1;
  long before=hasBaseline?beforeJdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.phan_quyen_nguoi_dung p JOIN dbo.vai_tro r ON r.id=p.vai_tro_id WHERE r.ma=N'mghp_quan_tri_ky'",Long.class):0;
  var jdbc=new JdbcTemplate(SigningSupportDatabase.migrate());
  for(String table:List.of("mghp_su_co_ky","mghp_su_co_ky_lich_su","mghp_theo_doi_chung_thu","mghp_chung_thu_lich_su","mghp_yeu_cau_gia_han"))
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE name=?",Integer.class,table)).as(table).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.vai_tro WHERE ma=N'mghp_quan_tri_ky' AND dang_hoat_dong=1",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.triggers WHERE name IN(N'tr_mghp_support_history_immutable',N'tr_mghp_certificate_history_immutable') AND is_disabled=0",Integer.class)).isEqualTo(2);
  assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.phan_quyen_nguoi_dung p JOIN dbo.vai_tro r ON r.id=p.vai_tro_id WHERE r.ma=N'mghp_quan_tri_ky'",Long.class)).isEqualTo(before);
 }
}
