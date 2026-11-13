package pbl6.ctsv.foundation.web;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import org.springframework.jdbc.core.JdbcTemplate;
final class FoundationSecurity implements FoundationAccess {
 private final AuthAccess auth; private final JdbcTemplate jdbc;
 FoundationSecurity(AuthAccess auth,JdbcTemplate jdbc){this.auth=auth;this.jdbc=jdbc;}
 public Identity authorize(AuthAccess.Principal p,boolean online,String capability){
  if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Foundation authorization requires a local transaction");
  // Resolve the trusted binding before Auth, without retaining a local lock across its independent transaction.
  var mapped=jdbc.query("""
    SELECT u.id FROM dbo.dinh_danh_dang_nhap d JOIN dbo.nguoi_dung u ON u.id=d.nguoi_dung_id
    WHERE d.ma_nha_cung_cap COLLATE Latin1_General_100_BIN2=? AND d.ma_chu_the_dang_nhap COLLATE Latin1_General_100_BIN2=?
     AND d.thoi_diem_xac_minh IS NOT NULL AND u.dang_hoat_dong=1 AND u.thoi_diem_xoa IS NULL
    """,(r,n)->r.getLong(1),p.issuer(),p.accountId().toString());
  if(mapped.size()!=1)throw new FoundationFailure(403,"FORBIDDEN");
  if(online)auth.requireCtsvAccess(p,p.accountId());
  var rows=jdbc.query("""
    SELECT u.id FROM dbo.dinh_danh_dang_nhap d
    JOIN dbo.nguoi_dung u WITH(UPDLOCK,HOLDLOCK) ON u.id=d.nguoi_dung_id
    WHERE d.ma_nha_cung_cap COLLATE Latin1_General_100_BIN2=? AND d.ma_chu_the_dang_nhap COLLATE Latin1_General_100_BIN2=?
      AND d.thoi_diem_xac_minh IS NOT NULL AND u.dang_hoat_dong=1 AND u.thoi_diem_xoa IS NULL
    """,(r,n)->r.getLong(1),p.issuer(),p.accountId().toString());
  if(rows.size()!=1||!rows.getFirst().equals(mapped.getFirst()))throw new FoundationFailure(403,"FORBIDDEN");
  long user=rows.getFirst();
  var students=jdbc.query("SELECT id FROM dbo.sinh_vien WHERE nguoi_dung_id=?",(r,n)->r.getLong(1),user);
  if(students.size()>1)throw new FoundationFailure(503,"DEPENDENCY_UNAVAILABLE");
  if(capability==null){if(students.isEmpty()&&!role(user,"mghp_chuyen_vien"))throw new FoundationFailure(403,"FORBIDDEN");}
  else if(!role(user,capability))throw new FoundationFailure(403,"FORBIDDEN");
  return new Identity(user,students.isEmpty()?null:students.getFirst(),p);
 }
 private boolean role(long user,String code){
  return jdbc.queryForObject("""
    SELECT COUNT(*) FROM dbo.phan_quyen_nguoi_dung p WITH(UPDLOCK,HOLDLOCK)
    JOIN dbo.vai_tro r ON r.id=p.vai_tro_id
    WHERE p.nguoi_dung_id=? AND r.ma=? AND r.dang_hoat_dong=1 AND p.don_vi_id IS NULL
      AND (p.hieu_luc_tu IS NULL OR p.hieu_luc_tu<=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
      AND (p.hieu_luc_den IS NULL OR p.hieu_luc_den>TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
      AND ((?='ctsv.roles.manage' AND p.ma_phan_he='CTSV') OR (?<>'ctsv.roles.manage' AND p.ma_phan_he='MGHP'))
    """,Integer.class,user,code,code,code)>0;
 }
}
