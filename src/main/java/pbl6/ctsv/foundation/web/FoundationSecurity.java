package pbl6.ctsv.foundation.web;
import java.util.Optional;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.foundation.api.FoundationRoleAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
final class FoundationSecurity implements FoundationAccess, FoundationRoleAccess {
 private final AuthAccess auth; private final JdbcTemplate jdbc;
 FoundationSecurity(AuthAccess auth,JdbcTemplate jdbc){this.auth=auth;this.jdbc=jdbc;}
 record MenuAccess(boolean student,boolean mghpSpecialist,boolean ctsvRoleAdmin,boolean mghpSigning){
  MenuAccess(boolean student,boolean mghpSpecialist,boolean ctsvRoleAdmin){this(student,mghpSpecialist,ctsvRoleAdmin,false);}
 }
 MenuAccess menuAccess(AuthAccess.Principal principal){
  var identity=identify(principal,true);
  long user=identity.userId();
  boolean specialist=role(user,"mghp_chuyen_vien");
  return new MenuAccess(identity.studentId()!=null,specialist,role(user,"ctsv.roles.manage"),
   specialist||role(user,"mghp_pho_phong")||role(user,"mghp_pho_hieu_truong"));
 }
 public Identity authorize(AuthAccess.Principal p,boolean online,String capability){
  var identity=identify(p,online);
  long user=identity.userId();
  if(capability==null){if(identity.studentId()==null&&!role(user,"mghp_chuyen_vien"))throw new FoundationFailure(403,"FORBIDDEN");}
  else if(!role(user,capability))throw new FoundationFailure(403,"FORBIDDEN");
  return identity;
 }
 public Identity identify(AuthAccess.Principal p,boolean online){
  if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Foundation authorization requires a local transaction");
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
  return new Identity(user,students.isEmpty()?null:students.getFirst(),p);
 }

 @Override
 public Optional<ActiveRole> findActiveGlobalRole(long userId, String moduleCode, String roleCode) {
  if (!TransactionSynchronizationManager.isActualTransactionActive()) {
   throw new IllegalStateException("Foundation role lookup requires a local transaction");
  }
  return jdbc.query("""
    SELECT DISTINCT r.id FROM dbo.phan_quyen_nguoi_dung p WITH(UPDLOCK,HOLDLOCK)
    JOIN dbo.vai_tro r WITH(HOLDLOCK) ON r.id=p.vai_tro_id
    WHERE p.nguoi_dung_id=? AND p.ma_phan_he=? AND r.ma=? AND r.dang_hoat_dong=1 AND p.don_vi_id IS NULL
      AND (p.hieu_luc_tu IS NULL OR p.hieu_luc_tu<=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
      AND (p.hieu_luc_den IS NULL OR p.hieu_luc_den>TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
    """, (row, index) -> new ActiveRole(row.getLong(1)), userId, moduleCode, roleCode).stream().findFirst();
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
