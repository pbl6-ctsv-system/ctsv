package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;import java.sql.*;import java.time.*;import java.util.*;

/** Explicit owner-authorized DEV demo. No CA calls, no fake receipts, no existing row changes. */
public final class SigningSupportDevFixture {
 static final String MARK="SUPPORT-DEV-20261125";
 static long one(Connection c,String sql,Object...args)throws Exception{try(var s=c.prepareStatement(sql)){int i=1;for(var a:args)s.setObject(i++,a);try(var r=s.executeQuery()){return r.next()?r.getLong(1):0;}}}
 static long insert(Connection c,String sql,Object...args)throws Exception{return one(c,sql+";SELECT CAST(SCOPE_IDENTITY() AS bigint)",args);}
 static void write(Connection c,String sql,Object...args)throws Exception{try(var s=c.prepareStatement(sql)){int i=1;for(var a:args)s.setObject(i++,a);s.executeUpdate();}}
 public static void main(String[] args)throws Exception{
  Path home=Path.of(Objects.requireNonNull(System.getenv("CTSV_DEV_HOME")));var props=new Properties();try(var in=Files.newInputStream(home.resolve("application-dev.properties"))){props.load(in);}
  String url=props.getProperty("spring.datasource.url");if(url==null||!url.contains("databaseName=ctsv_dev;integratedSecurity=true;"))throw new IllegalStateException("DEV target required");
  var json=new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  try(var c=DriverManager.getConnection(url)){
   if(one(c,"SELECT IIF(DB_NAME()=N'ctsv_dev' AND CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName'))=N'DEV2022' AND owner_sid=SUSER_SID() AND EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=0 AND name=N'ctsv_dev_owner' AND CONVERT(nvarchar(128),value)=N'ctsv-local-dev-v1'),1,0) FROM sys.databases WHERE database_id=DB_ID()")!=1||one(c,"SELECT MAX(TRY_CONVERT(int,version)) FROM dbo.flyway_schema_history WHERE success=1")!=32)throw new IllegalStateException("DEV owner/schema guard");
   long exists=one(c,"SELECT id FROM dbo.van_ban WHERE tieu_de=?",MARK+" · Biên bản SYNTHETIC");if(exists>0){System.out.println("Synthetic support fixture already exists; no replay/reset.");return;}
   long cv=one(c,"SELECT d.nguoi_dung_id FROM dbo.TaiKhoan t JOIN dbo.dinh_danh_dang_nhap d ON d.ma_chu_the_dang_nhap=CONVERT(nvarchar(36),t.ID) WHERE t.TenDangNhapChuanHoa=N'dev.chuyenvien' AND d.ma_nha_cung_cap=?",props.getProperty("ctsv.auth.issuer"));
   long last=one(c,"SELECT TOP(1) nguoi_dung_noi_bo_id FROM dbo.buoc_ky WHERE vai_tro_nguoi_ky=N'pho_hieu_truong' ORDER BY id");
   long original=one(c,"SELECT TOP(1) v.id FROM dbo.phien_ban_van_ban v JOIN dbo.tep_dinh_kem f ON f.id=v.tep_goc_id JOIN dbo.van_ban d ON d.id=v.van_ban_id WHERE d.ma_phan_he=N'MGHP' AND f.trang_thai_luu_tru=N'available' ORDER BY v.id");
   if(cv==0||last==0||original==0)throw new IllegalStateException("Existing DEV signer/file required");
   c.setAutoCommit(false);UUID operation=UUID.randomUUID(),event=UUID.randomUUID();Instant now=Instant.now();
   try{
    long semester=insert(c,"INSERT dbo.hoc_ky(ma,ten) VALUES(?,N'SYNTHETIC · Quản trị ký số')",MARK);
    // Completed synthetic shell must not block opening real rounds. No publication/signing evidence is fabricated.
    long round=insert(c,"INSERT dbo.mghp_dot_xet(ma_dot_xet,ten,hoc_ky_id,trang_thai) VALUES(?,N'SYNTHETIC · Thực hành Admin ký số',?,N'completed')",MARK,semester);
    long root=insert(c,"INSERT dbo.mghp_quyet_dinh(dot_xet_id,ma_qd,loai_qd,nguoi_tao_id) VALUES(?,?,N'initial',?)",round,MARK,cv);
    long version=insert(c,"INSERT dbo.mghp_phien_ban_qd(quyet_dinh_id,lan_sua_doi,trang_thai,giai_doan,sha256_tap_du_lieu,so_dong,nguoi_tao_id,thoi_diem_chot) VALUES(?,1,N'locked',N'after_meeting',?,0,?,SYSUTCDATETIME())",root,"a".repeat(64),cv);
    long minutes=insert(c,"INSERT dbo.mghp_bien_ban(dot_xet_id,ma_bb,phien_ban_qd_nguon_id,sha256_ds_hop,nguoi_tao_id) VALUES(?,?,?,?,?)",round,MARK,version,"a".repeat(64),cv);
    String title=MARK+" · Biên bản SYNTHETIC";
    long document=insert(c,"INSERT dbo.van_ban(ma_van_ban,ma_phan_he,loai_van_ban,tieu_de,nguoi_tao_id,thoi_diem_tao) VALUES(NEWID(),N'MGHP',N'bien_ban',?,?,SYSUTCDATETIME())",title,cv);
    long v=insert(c,"INSERT dbo.phien_ban_van_ban(van_ban_id,so_phien_ban,tieu_de_tai_thoi_diem_chot,tep_goc_id,ma_tham_chieu_nghiep_vu,lan_sua_doi_nghiep_vu,sha256_tap_du_lieu,trang_thai_noi_dung,nguoi_chot_id,thoi_diem_chot,nguoi_tao_id,thoi_diem_tao) SELECT ?,1,?,tep_goc_id,?,1,?,N'locked',?,SYSUTCDATETIME(),?,SYSUTCDATETIME() FROM dbo.phien_ban_van_ban WHERE id=?",document,title,MARK,"a".repeat(64),cv,cv,original);
    write(c,"INSERT dbo.mghp_lien_ket_phien_ban_van_ban(loai_van_ban,bien_ban_id,phien_ban_van_ban_dung_chung_id,trang_thai) VALUES(N'bien_ban',?,?,N'current')",minutes,v);
    long q=insert(c,"INSERT dbo.yeu_cau_ky(ma_yeu_cau,phien_ban_van_ban_id,he_thong_cung_cap_id,che_do_thuc_hien,quy_tac_uy_quyen,danh_muc_ca_id,trang_thai,nguoi_yeu_cau_id,thoi_diem_yeu_cau,ma_loi_gan_nhat,phien_ban_adapter) SELECT NEWID(),?,he_thong_cung_cap_id,N'TEST',N'send_for_selected_signer',danh_muc_ca_id,N'failed',?,SYSUTCDATETIME(),N'CA_DOWNLOAD_UNAVAILABLE',N'ctsv-p11-v1' FROM dbo.yeu_cau_ky WHERE id=(SELECT MIN(id) FROM dbo.yeu_cau_ky)",v,cv);
    long step=insert(c,"INSERT dbo.buoc_ky(yeu_cau_ky_id,nguoi_dung_noi_bo_id,thu_tu_ky,vai_tro_nguoi_ky,email_nguoi_ky,trang_thai,thoi_diem_tao) VALUES(?,?,1,N'chuyen_vien',N'officer.e@dut.udn.vn',N'failed',SYSUTCDATETIME())",q,cv);
    write(c,"INSERT dbo.buoc_ky(yeu_cau_ky_id,nguoi_dung_noi_bo_id,thu_tu_ky,vai_tro_nguoi_ky,email_nguoi_ky,trang_thai,thoi_diem_tao) VALUES(?,?,2,N'pho_hieu_truong',N'staff.c@dut.udn.vn',N'waiting',SYSUTCDATETIME())",q,last);
    long attempt=insert(c,"INSERT dbo.lan_goi_dich_vu_ky(yeu_cau_ky_id,buoc_ky_id,loai_thao_tac,so_thu_tu_lan_goi,ma_lien_ket_yeu_cau,sha256_dau_vao_du_kien,ket_luan,co_the_da_thay_doi_ben_ngoai,thoi_diem_bat_dau,phien_ban_adapter,tep_dau_vao_id,giai_doan_xu_ly) SELECT ?,?,N'sign',1,?,f.sha256,N'rejected',0,SYSUTCDATETIME(),N'ctsv-p11-v1',f.id,N'failed' FROM dbo.phien_ban_van_ban v JOIN dbo.tep_dinh_kem f ON f.id=v.tep_goc_id WHERE v.id=?",q,step,operation.toString(),original);
    var context=new SigningResultSpool.Context(operation,attempt,q,step,cv,"chuyen_vien","dev.chuyenvien",title,"SYNTHETIC · Thực hành Admin ký số",1,"a".repeat(64),"bien_ban","SYNTHETIC · Quản trị ký số",null);
    var policy=SigningFailurePolicy.classify("download",new pbl6.ctsv.shared.ca.CaFailure("CA_DOWNLOAD_UNAVAILABLE",503,false));
    var spool=new SigningResultSpool(Path.of(System.getProperty("user.home"),".ctsv","signing-results"),Clock.systemUTC());spool.start(context);
    try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var out=new java.io.ByteArrayOutputStream()){pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());pdf.save(out);spool.pdf(operation,out.toByteArray());}
    var entry=spool.failure(operation,policy);event=entry.errorEvent();String snapshot=json.writeValueAsString(entry);
    long incident=insert(c,"INSERT dbo.mghp_su_co_ky(lan_goi_id,su_kien_loi_id,du_lieu_loi,trang_thai,thoi_diem_phat_sinh,thoi_diem_cap_nhat) VALUES(?,?,?,N'waiting',SYSUTCDATETIME(),SYSUTCDATETIME())",attempt,event.toString(),snapshot);
    write(c,"INSERT dbo.mghp_su_co_ky_lich_su(su_co_id,su_kien_id,loai,noi_dung,du_lieu_loi,thoi_diem) VALUES(?,?,N'error',N'SYNTHETIC — lỗi mô phỏng để thực hành UI Admin. Không gọi CA, không tạo chữ ký/biên nhận.',?,SYSUTCDATETIME())",incident,event.toString(),snapshot);
    c.commit();spool.reported(operation,event);System.out.println("Synthetic support fixture: incident="+incident+", request="+q+"; no CA/cloud write, existing signed data unchanged.");
   }catch(Exception failure){c.rollback();throw failure;}
  }
 }
}
