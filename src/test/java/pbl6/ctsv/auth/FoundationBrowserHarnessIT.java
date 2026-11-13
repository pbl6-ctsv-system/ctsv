package pbl6.ctsv.auth;
import java.nio.file.*;import java.security.*;import java.util.*;import java.time.*;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;
/** Browser-only test host. Does not rerun or alter the five verified Foundation tasks. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,
 classes={pbl6.ctsv.CtsvApplication.class,FoundationHttpIT.Config.class},
 properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","server.address=127.0.0.1","server.port=8544"})
@ActiveProfiles({"local","test"})
class FoundationBrowserHarnessIT {
 static final ObjectMapper JSON=new ObjectMapper();
 static final Path DIRECTORY=Path.of(Objects.requireNonNull(System.getenv("P04_BROWSER_DIR"),"P04 browser directory required"));
 @DynamicPropertySource static void settings(DynamicPropertyRegistry r)throws Exception{
  JsonNode local=JSON.readTree(Files.readString(DIRECTORY.resolve("private-runtime.json")));
  r.add("server.ssl.enabled",()->true);
  r.add("server.ssl.key-store",()->DIRECTORY.resolve("test.pfx").toUri().toString());
  r.add("server.ssl.key-store-type",()->"PKCS12");r.add("server.ssl.key-store-password",()->local.get("pfxPassword").asText());
  var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);var key=g.generateKeyPair();
  Path pk=AuthHttpIT.pem("PRIVATE KEY",key.getPrivate().getEncoded()),pub=AuthHttpIT.pem("PUBLIC KEY",key.getPublic().getEncoded());
  r.add("ctsv.auth.issuer",()->"https://auth.ctsv.test");r.add("ctsv.auth.audience",()->"ctsv");
  r.add("ctsv.auth.signing-kid",()->"browser-test");r.add("ctsv.auth.private-key",pk::toString);
  r.add("ctsv.auth.public-keys[browser-test]",pub::toString);
  r.add("ctsv.auth.origins[0]",()->"https://127.0.0.1:5174");
  r.add("ctsv.auth.trusted-sources[0]",()->"SIS_MOCK");
 }
 @Autowired JdbcTemplate jdbc;@LocalServerPort int port;
 long insert(String sql,Object...args){return jdbc.queryForObject(sql+";SELECT CAST(SCOPE_IDENTITY() AS bigint)",Long.class,args);}
 Map<String,Object> seed(String kind,String password){
  String name="browser."+kind+"."+UUID.randomUUID();UUID account=UUID.randomUUID();
  jdbc.update("INSERT dbo.TaiKhoan(ID,TenDangNhap,TenDangNhapChuanHoa,MatKhauHash) VALUES(?,?,?,?)",account.toString(),name,name,new AuthCredentials().hash(password));
  jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro='CTSV_USER'",account.toString());
  if(kind.equals("admin"))jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro='AUTH_ADMIN'",account.toString());
  long user=insert("INSERT dbo.nguoi_dung(dia_chi_email,dang_hoat_dong) VALUES(?,1)",name+"@example.invalid");
  jdbc.update("INSERT dbo.dinh_danh_dang_nhap(nguoi_dung_id,ma_nha_cung_cap,ma_chu_the_dang_nhap,thoi_diem_xac_minh) VALUES(?,?,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))",user,"https://auth.ctsv.test",account.toString());
  if(!kind.equals("student")){
   jdbc.update("INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he) SELECT ?,id,'MGHP' FROM dbo.vai_tro WHERE ma='mghp_chuyen_vien'",user);
   jdbc.update("INSERT dbo.can_bo(ma_cb,nguoi_dung_id,ho_ten) VALUES(?,?,N'Browser TEST specialist')","BROWSER-"+UUID.randomUUID(),user);
  }
  if(kind.equals("admin"))jdbc.update("INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he) SELECT ?,id,'CTSV' FROM dbo.vai_tro WHERE ma='ctsv.roles.manage'",user);
  long student=kind.equals("student")?insert("INSERT dbo.sinh_vien(mssv,nguoi_dung_id,ho_ten,ma_dinh_danh_ben_ngoai) VALUES(?,?,N'Sinh viên Browser TEST',N'BROWSER-SOURCE')","BROWSER-"+UUID.randomUUID(),user):0;
  return Map.of("username",name,"accountId",account.toString(),"userId",""+user,"studentId",""+student);
 }
 @Test void hostRealAuthFoundationForBrowserOnly()throws Exception{
  assertThat(port).isEqualTo(8544);
  // Test-only timeout makes a real SQL lock fault observable before the browser's 20s timeout.
  ((org.springframework.jdbc.datasource.SimpleDriverDataSource)jdbc.getDataSource())
   .getConnectionProperties().setProperty("queryTimeout","3");
  Files.deleteIfExists(DIRECTORY.resolve("browser-stop"));
  for(String marker:List.of("auth-lock-request","auth-lock-held","auth-lock-release"))Files.deleteIfExists(DIRECTORY.resolve(marker));
  String password="Synthetic browser "+UUID.randomUUID();
  var student=seed("student",password);var cv=seed("specialist",password);var admin=seed("admin",password);
  long semester=insert("INSERT dbo.hoc_ky(ma) VALUES(?)","BROWSER-"+UUID.randomUUID());
  long process=insert("INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,ma_qua_trinh_hoc) VALUES(?,?)",Long.parseLong(student.get("studentId").toString()),"BROWSER-PROCESS-"+UUID.randomUUID());
  long role=jdbc.queryForObject("SELECT id FROM dbo.vai_tro WHERE ma='mghp_chuyen_vien'",Long.class);
  int order=jdbc.queryForObject("SELECT COALESCE(MAX(so_thu_tu),0)+1 FROM dbo.hoc_ky",Integer.class);
  var config=Map.of("password",password,"student",student,"specialist",cv,"admin",admin,"semesterId",""+semester,"processId",""+process,"roleId",""+role,"semesterOrder",order);
  Files.writeString(DIRECTORY.resolve("browser-fixture.json"),JSON.writeValueAsString(config));
  Instant until=Instant.now().plusSeconds(1800);
  while(!Files.exists(DIRECTORY.resolve("browser-stop"))&&Instant.now().isBefore(until)){
   if(Files.exists(DIRECTORY.resolve("auth-lock-request")))holdSyntheticAuthRow(cv.get("accountId").toString());
   Thread.sleep(200);
  }
  assertThat(Files.exists(DIRECTORY.resolve("browser-stop"))).as("Browser runner must signal completion").isTrue();
 }
 void holdSyntheticAuthRow(String account)throws Exception{
  // Guarded P04 datasource; no schema changes, committed writes, stub Auth state, or DB reset.
  try(var c=jdbc.getDataSource().getConnection()){
   c.setAutoCommit(false);
   try{
    try(var s=c.prepareStatement("SELECT ID FROM dbo.TaiKhoan WITH(XLOCK,ROWLOCK,HOLDLOCK) WHERE ID=?")){
     s.setString(1,account);try(var rows=s.executeQuery()){assertThat(rows.next()).isTrue();}
    }
    Files.writeString(DIRECTORY.resolve("auth-lock-held"),"Real SQL row lock on synthetic specialist; rollback only");
    Instant deadline=Instant.now().plusSeconds(15);
    while(!Files.exists(DIRECTORY.resolve("auth-lock-release"))&&!Files.exists(DIRECTORY.resolve("browser-stop"))&&Instant.now().isBefore(deadline))Thread.sleep(100);
   }finally{c.rollback();}
  }finally{
   for(String marker:List.of("auth-lock-request","auth-lock-held","auth-lock-release"))Files.deleteIfExists(DIRECTORY.resolve(marker));
  }
 }
}
