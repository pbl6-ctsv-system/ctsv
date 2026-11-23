package pbl6.ctsv.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;import java.sql.*;import java.time.*;import java.util.*;
import pbl6.ctsv.shared.ca.CaDutClient;

/** Explicit, idempotent TEST signer setup on the owner-authorized DEV only. No reset or document signing. */
public final class P11DevFixture {
 static final ObjectMapper JSON=new ObjectMapper();
 static long insert(Connection c,String sql,Object...args)throws Exception{
  try(var s=c.prepareStatement(sql+";SELECT CAST(SCOPE_IDENTITY() AS bigint)")){int i=1;for(Object a:args)s.setObject(i++,a);try(var r=s.executeQuery()){if(!r.next())throw new IllegalStateException();return r.getLong(1);}}
 }
 static void write(Connection c,String sql,Object...args)throws Exception{try(var s=c.prepareStatement(sql)){int i=1;for(Object a:args)s.setObject(i++,a);s.executeUpdate();}}
 static long one(Connection c,String sql,Object...args)throws Exception{try(var s=c.prepareStatement(sql)){int i=1;for(Object a:args)s.setObject(i++,a);try(var r=s.executeQuery()){if(!r.next())return 0;return r.getLong(1);}}}
 static long actor(Connection c,String key,String username,String title,String issuer)throws Exception{
  UUID account=null;String hash=null;
  try(var s=c.prepareStatement("SELECT ID,MatKhauHash FROM dbo.TaiKhoan WHERE TenDangNhapChuanHoa=?")){s.setString(1,username);try(var r=s.executeQuery()){if(r.next()){account=UUID.fromString(r.getString(1));hash=r.getString(2);}}}
  if(account==null){account=UUID.randomUUID();write(c,"INSERT dbo.TaiKhoan(ID,TenDangNhap,TenDangNhapChuanHoa,MatKhauHash,ThoiDiemTao,ThoiDiemCapNhat) VALUES(?,?,?,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'),TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))",account.toString(),username,username,new AuthCredentials().hash(System.getenv("P11_"+key+"_PASSWORD")));write(c,"INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'CTSV_USER'",account.toString());}
  long user=one(c,"SELECT d.nguoi_dung_id FROM dbo.dinh_danh_dang_nhap d WHERE d.ma_nha_cung_cap=? AND d.ma_chu_the_dang_nhap=?",issuer,account.toString());
  if(user==0){if(hash!=null)throw new IllegalStateException("Existing account is not owned by this fixture");user=insert(c,"INSERT dbo.nguoi_dung(dia_chi_email,ho_ten,dang_hoat_dong) VALUES(?,?,1)",username+"@example.invalid",title);write(c,"INSERT dbo.dinh_danh_dang_nhap(nguoi_dung_id,ma_nha_cung_cap,ma_chu_the_dang_nhap,thoi_diem_xac_minh) VALUES(?,?,?,SYSUTCDATETIME())",user,issuer,account.toString());}
  if(one(c,"SELECT id FROM dbo.nguoi_dung WHERE id=? AND ho_ten=? AND dia_chi_email=?",user,title,username+"@example.invalid")==0)throw new IllegalStateException("Fixture identity ownership mismatch");
  String role=key.equals("PP")?"mghp_pho_phong":"mghp_pho_hieu_truong";
  if(one(c,"SELECT p.id FROM dbo.phan_quyen_nguoi_dung p JOIN dbo.vai_tro r ON r.id=p.vai_tro_id WHERE p.nguoi_dung_id=? AND p.ma_phan_he=N'MGHP' AND p.don_vi_id IS NULL AND r.ma=?",user,role)==0)write(c,"INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he) SELECT ?,id,N'MGHP' FROM dbo.vai_tro WHERE ma=?",user,role);
  return user;
 }
 public static void main(String[] args)throws Exception{
  Path home=Path.of(Objects.requireNonNull(System.getenv("CTSV_DEV_HOME")));var props=new Properties();try(var in=Files.newInputStream(home.resolve("application-dev.properties"))){props.load(in);}
  String url=props.getProperty("spring.datasource.url");if(url==null||!url.contains("databaseName=ctsv_dev;integratedSecurity=true;"))throw new IllegalStateException("DEV target required");
  UUID category;try(var ca=new CaDutClient(java.net.URI.create("https://chukyso.dut.udn.vn"),System.getenv("CA_DUT_CLIENT_ID"),System.getenv("CA_DUT_CLIENT_SECRET"),Clock.systemUTC())){var categories=ca.categories();if(categories.isEmpty())throw new IllegalStateException("CA category unavailable");category=categories.getFirst().id();}
  long cv,pp,pht,provider;
  try(var c=DriverManager.getConnection(url)){
   try(var s=c.createStatement();var r=s.executeQuery("SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),SUSER_SNAME(owner_sid),SUSER_SNAME(),(SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties WHERE class=0 AND name=N'ctsv_dev_owner') FROM sys.databases WHERE database_id=DB_ID()")){if(!r.next()||!"ctsv_dev".equals(r.getString(1))||!"DEV2022".equals(r.getString(2))||!r.getString(3).equals(r.getString(4))||!"ctsv-local-dev-v1".equals(r.getString(5)))throw new IllegalStateException("DEV owner guard");}
   if(one(c,"SELECT MAX(TRY_CONVERT(int,version)) FROM dbo.flyway_schema_history WHERE success=1")!=24)throw new IllegalStateException("V24 required");
   c.setAutoCommit(false);
   try{
    String issuer=props.getProperty("ctsv.auth.issuer");cv=one(c,"SELECT d.nguoi_dung_id FROM dbo.TaiKhoan t JOIN dbo.dinh_danh_dang_nhap d ON d.ma_chu_the_dang_nhap=CONVERT(nvarchar(36),t.ID) AND d.ma_nha_cung_cap=? WHERE t.TenDangNhapChuanHoa=?",issuer,System.getenv("P11_CV_USERNAME"));if(cv==0)throw new IllegalStateException("Existing CV binding required");
    pp=actor(c,"PP","dev.p11.phophong","Phó phòng MGHP · P11 TEST",issuer);pht=actor(c,"PHT","dev.p11.phohieutruong","Phó Hiệu trưởng · P11 TEST",issuer);
    provider=one(c,"SELECT id FROM dbo.he_thong_ngoai WHERE ma=N'P11DEV_CA_DUT'");if(provider==0)provider=insert(c,"INSERT dbo.he_thong_ngoai(ma,ten,dang_hoat_dong) VALUES(N'P11DEV_CA_DUT',N'CA DUT · P11 DEV TEST',1)");
    c.commit();
   }catch(Exception e){c.rollback();throw e;}
  }
  var config=new LinkedHashMap<String,Object>();config.put("ctsv.mghp.signing.enabled",true);config.put("ctsv.mghp.signing.worker.enabled",true);config.put("ctsv.mghp.signing.mode","TEST");config.put("ctsv.mghp.signing.provider-id",provider);config.put("ctsv.mghp.signing.category-id",category.toString());config.put("ca-dut.enabled",true);
  for(String key:List.of("minutes-first","decision-first","last")){config.put("ctsv.mghp.signing."+key+".user-id",key.equals("minutes-first")?cv:key.equals("decision-first")?pp:pht);config.put("ctsv.mghp.signing."+key+".email",key.equals("last")?"staff.c@dut.udn.vn":"officer.e@dut.udn.vn");config.put("ctsv.mghp.signing."+key+".authority-reference","P11 DEV TEST owner-authorized two-actor browser verification 2026-11-23; not production signing authority");}
  Files.writeString(home.resolve("p11.settings.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(config));
  System.out.println("P11 DEV TEST settings saved outside Git; CA GET catalog only; no upload/sign. CV="+cv+", PP="+pp+", PHT="+pht);
 }
}
