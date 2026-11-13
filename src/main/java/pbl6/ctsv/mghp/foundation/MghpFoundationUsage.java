package pbl6.ctsv.mghp.foundation;
import pbl6.ctsv.foundation.api.FoundationUsage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/** P04 read-only guard; no evaluation/decision behavior or endpoints are implemented here. */
@Component
@ConditionalOnProperty(name="ctsv.foundation.enabled",havingValue="true")
final class MghpFoundationUsage implements FoundationUsage {
 private final JdbcTemplate jdbc;
 MghpFoundationUsage(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public boolean semesterUsed(long id){return used("hoc_ky_ap_dung_id",id);}
 public boolean processUsed(long id){return used("qua_trinh_hoc_id",id);}
 private boolean used(String column,long id){return jdbc.queryForObject("""
  SELECT (SELECT COUNT(*) FROM dbo.mghp_danh_gia_dieu_kien WITH(HOLDLOCK) WHERE %s=? AND trang_thai_xu_ly='confirmed')
   +(SELECT COUNT(*) FROM dbo.mghp_dong_qd d WITH(HOLDLOCK) JOIN dbo.mghp_phien_ban_qd v WITH(HOLDLOCK) ON v.id=d.phien_ban_qd_id WHERE d.%s=? AND v.thoi_diem_chot IS NOT NULL)
  """.formatted(column,column),Integer.class,id,id)>0;}
}
