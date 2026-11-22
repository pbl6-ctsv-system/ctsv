package pbl6.ctsv.auth;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

/** Opt-in bounded acceptance host on the guarded timeline DB, never the daily DEV launcher. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,classes=TimelineHttpIT.Config.class,
 properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.mghp.configuration.enabled=true","ctsv.mghp.applications.enabled=true","server.address=127.0.0.1","server.port=9445"})
class TimelineBrowserIT extends TimelineHttpIT {
 @Test void serveTimelineBrowser()throws Exception{
  var home=Path.of(Objects.requireNonNull(System.getenv("TIMELINE_BROWSER_HOME")));Files.createDirectories(home);var done=home.resolve("browser.done");
  if(Files.exists(done))throw new IllegalStateException("Use a new owned evidence directory for each browser run");
  var credentials=new LinkedHashMap<String,Object>();credentials.put("origin","https://127.0.0.1:"+Objects.requireNonNullElse(System.getenv("TIMELINE_BROWSER_FRONTEND_PORT"),"5175"));credentials.put("database",pbl6.ctsv.mghp.configuration.TimelineDatabase.NAME);
  credentials.put("password",PASSWORD);credentials.put("semesterId",""+semester);credentials.put("policyVersionId",""+policy);
  credentials.put("semesterCode",jdbc.queryForObject("SELECT ma FROM dbo.hoc_ky WHERE id=?",String.class,semester));
  for(var entry:Map.of("specialist",cvToken,"student",svToken,"admin",adminToken).entrySet()){
   long user=entry.getKey().equals("specialist")?actor:entry.getKey().equals("student")?svUser:adminUser;
   credentials.put(entry.getKey(),jdbc.queryForObject("SELECT t.TenDangNhap FROM dbo.TaiKhoan t JOIN dbo.dinh_danh_dang_nhap d ON d.ma_chu_the_dang_nhap=CONVERT(varchar(36),t.ID) WHERE d.nguoi_dung_id=?",String.class,user));
  }
  Files.writeString(home.resolve("credentials.json"),JSON.writeValueAsString(credentials));Files.writeString(home.resolve("ready.json"),"{\"database\":\"ctsv_timeline_test_20261120\",\"backend\":\"https://127.0.0.1:9445\"}");
  long until=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(15);while(!Files.exists(done)&&System.nanoTime()<until)Thread.sleep(250);
  assertThat(Files.exists(done)).as("Browser acceptance completed before timeout").isTrue();assertThat(Files.readString(done).trim()).isEqualTo("PASS");
 }
}
