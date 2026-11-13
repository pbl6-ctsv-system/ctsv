package pbl6.ctsv.auth;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.*;import org.springframework.boot.test.context.*;import org.springframework.test.context.*;import static org.assertj.core.api.Assertions.*;
/** Explicit bounded test host only, never a development launcher. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,classes={pbl6.ctsv.CtsvApplication.class,P05HttpIT.Config.class},properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.mghp.configuration.enabled=true","server.address=127.0.0.1","server.port=9445"})
class P05BrowserHarnessIT extends P05HttpIT {
 static void browserProperties(DynamicPropertyRegistry r)throws Exception{
  String pfx=Objects.requireNonNull(System.getenv("P05_BROWSER_PFX")),password=Objects.requireNonNull(System.getenv("P05_BROWSER_PFX_PASSWORD"));
  r.add("server.ssl.key-store",()->Path.of(pfx).toUri().toString());r.add("server.ssl.key-store-password",()->password);r.add("server.port",()->9445);r.add("ctsv.auth.origins[0]",()->"https://127.0.0.1:5175");r.add("ctsv.auth.origins[1]",()->AuthHttpIT.ORIGIN);
  var store=java.security.KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(Path.of(pfx))){store.load(in,password.toCharArray());}var trust=java.security.KeyStore.getInstance("PKCS12");trust.load(null,null);trust.setCertificateEntry("own-dev-leaf",store.getCertificate(store.aliases().nextElement()));var tm=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());tm.init(trust);AuthHttpIT.testTls=javax.net.ssl.SSLContext.getInstance("TLS");AuthHttpIT.testTls.init(null,tm.getTrustManagers(),null);
 }
 @Test void serve()throws Exception{
  Path home=Path.of(Objects.requireNonNull(System.getenv("P05_BROWSER_HOME")));Files.createDirectories(home);Path done=home.resolve("browser.done");if(Files.exists(done))throw new IllegalStateException("Old browser.done: archive/remove exact owned marker before a new run");
  var credentials=new LinkedHashMap<String,Object>();credentials.put("origin","https://127.0.0.1:5175");credentials.put("backend","https://127.0.0.1:"+port);credentials.put("password",PASSWORD);credentials.put("database",pbl6.ctsv.mghp.configuration.P05Database.NAME);credentials.put("semesterId",Long.toString(semester));credentials.put("policyId",Long.toString(policyM2));credentials.put("lockedVersionId",Long.toString(lockedM2));
  for(var entry:Map.of("cv",cv,"student",sv).entrySet())credentials.put(entry.getKey(),jdbc.queryForObject("SELECT t.TenDangNhap FROM dbo.TaiKhoan t JOIN dbo.dinh_danh_dang_nhap d ON d.ma_chu_the_dang_nhap=CONVERT(varchar(36),t.ID) WHERE d.nguoi_dung_id=?",String.class,entry.getValue()));
  Files.writeString(home.resolve("credentials.json"),JSON.writeValueAsString(credentials));Files.writeString(home.resolve("ready.json"),"{\"port\":"+port+",\"database\":\"p05_ctsv_test_20261113\"}");
  long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(12);while(!Files.exists(done)&&System.nanoTime()<deadline)Thread.sleep(250);
  assertThat(Files.exists(done)).as("P05 real Browser E2E completed before timeout").isTrue();assertThat(Files.readString(done).trim()).isEqualTo("PASS");
 }
}
