package pbl6.ctsv.auth;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

/** Real HTTPS/Auth/JPA/SQL. PDF fixture is metadata only: no storage/CA claim. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 classes=ApplicationStageStatusHttpIT.Config.class,
 properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.mghp.configuration.enabled=true",
 "ctsv.mghp.applications.enabled=true","ctsv.mghp.signing.support.monitor.enabled=false","server.address=127.0.0.1"})
@ActiveProfiles({"local","test"}) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApplicationStageStatusHttpIT {
 @Configuration(proxyBeanMethods=false)
 @org.springframework.boot.autoconfigure.EnableAutoConfiguration
 @org.springframework.boot.autoconfigure.domain.EntityScan({"pbl6.ctsv.foundation","pbl6.ctsv.mghp"})
 @ComponentScan(basePackages={"pbl6.ctsv.auth","pbl6.ctsv.foundation","pbl6.ctsv.mghp","pbl6.ctsv.shared"},
  excludeFilters={@ComponentScan.Filter(type=FilterType.CUSTOM,classes=org.springframework.boot.context.TypeExcludeFilter.class),
   @ComponentScan.Filter(type=FilterType.REGEX,pattern=".*(?:Test|IT)(?:\\$.*)?")})
 static class Config {
  @Bean DataSource statusSource() throws Exception{return pbl6.ctsv.mghp.application.ReviewLifecycleDatabase.migrate();}
  @Bean @Primary Clock statusClock(){return Clock.fixed(StageHandoffHttpIT.NOW,ZoneOffset.UTC);}
 }
 @DynamicPropertySource static void configuration(DynamicPropertyRegistry r)throws Exception{
  AuthHttpIT.configuration(r);
  if(Boolean.getBoolean("application.stage.browser.enabled"))r.add("ctsv.auth.origins[1]",()->"https://127.0.0.1:5193");
 }
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired pbl6.ctsv.mghp.application.ReviewSourceAccess reviewSources;
 @Autowired org.springframework.transaction.PlatformTransactionManager manager;
 final StageHandoffHttpIT f=new StageHandoffHttpIT();
 @BeforeAll void setup()throws Exception{f.port=port;f.jdbc=jdbc;f.reviewSources=reviewSources;f.manager=manager;f.fixture();}
 @AfterAll void close(){f.closeTransport();}
 String root(StageHandoffHttpIT.Source source){return "/api/ctsv/mghp/ho-so/"+source.root();}
 com.fasterxml.jackson.databind.JsonNode read(String path)throws Exception{return f.read(path).json();}

 @Test void STATUS01_commonLifecycleAndBothStageInboxesStayConsistent()throws Exception{
  String round=f.start();var a=f.source(round);var b=f.source(round);var c=f.source(round);
  assertThat(read(root(a)).path("status").asText()).isEqualTo("submitted");
  f.action(round,"/dong-nhan-ho-so").expect(200);
  assertThat(read(root(a)).path("status").asText()).isEqualTo("processing");
  for(var source:List.of(a,b,c))f.approve(source);
  assertThat(read(root(a)).path("status").asText()).isEqualTo("processing");
  assertThat(read(root(a)).path("onlineStatus").asText()).isEqualTo("approved");
  String query="/api/ctsv/mghp/ho-so?roundId="+f.roundId(round)+"&semesterId="+f.semester;
  assertThat(read(query+"&stage=hard-copy").path("totalElements").asInt()).isZero();
  f.seal(round,true,f.read(f.context(round,true)).tag()).expect(200);
  assertThat(read(root(a)).path("hardCopyStatus").asText()).isEqualTo("pending");
  f.send("POST","/api/ctsv/mghp/danh-gia",a.evaluation(),f.cvToken,null).expect(201);
  assertThat(read(root(a)).path("hardCopyStatus").asText()).isEqualTo("in_progress");
  f.assess(b,"dat");f.assess(c,"khong_dat");
  assertThat(read(root(b)).path("status").asText()).isEqualTo("approved");
  var declined=read(root(c));assertThat(declined.path("status").asText()).isEqualTo("rejected");
  assertThat(declined.path("onlineStatus").asText()).isEqualTo("approved");
  assertThat(declined.path("rejectionStage").asText()).isEqualTo("hard_copy");
  assertThat(declined.path("rejectionReason").asText()).isEqualTo("SYNTHETIC confirmed");
  assertThat(read(query+"&stage=online&status=approved&size=1").path("totalElements").asInt()).isEqualTo(3);
  var first=read(query+"&stage=online&status=approved&size=1&page=0");
  var second=read(query+"&stage=online&status=approved&size=1&page=1");
  assertThat(first.path("items").get(0).path("id").asText()).isNotEqualTo(second.path("items").get(0).path("id").asText());
  assertThat(read(query+"&stage=online&status=pending").path("totalElements").asInt()).isZero();
  for(String result:List.of("pending","approved","rejected"))assertThat(read(query+"&stage=hard-copy&status="+result).path("totalElements").asInt()).isEqualTo(1);
  assertThat(read(query+"&status=rejected").path("totalElements").asInt()).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT trang_thai FROM dbo.mghp_ho_so WHERE id=?",String.class,c.root())).isEqualTo("approved");
  f.send("POST",root(c)+"/ket-luan",Map.of("result","approved"),f.cvToken,f.read(root(c)).tag()).expect(409);
 }
 @Test void STATUS02_supplementAndRetiredReopenPreserveHistoryAndOwnership()throws Exception{
  String round=f.start();var a=f.source(round);String root=root(a);
  f.send("POST",root+"/yeu-cau-bo-sung",Map.of("reason","SYNTHETIC bổ sung giấy rõ hơn","deadline",StageHandoffHttpIT.NOW.plusSeconds(3600).toString()),f.cvToken,f.read(root).tag()).expect(201);
  assertThat(read(root).path("status").asText()).isEqualTo("supplementing");
  // Synthetic receiving-state fixture; submission protocol is unchanged and tested in BS03.
  jdbc.update("UPDATE dbo.mghp_ho_so SET trang_thai=N'supplement_responded' WHERE id=?",a.root());
  assertThat(read(root).path("status").asText()).isEqualTo("processing");
  assertThat(read(root).path("onlineStatus").asText()).isEqualTo("supplement_responded");
  int versions=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_phien_ban_ho_so WHERE ho_so_id=?",Integer.class,a.root());
  f.send("POST",root+"/mo-lai",Map.of("reason","SYNTHETIC retired"),f.cvToken,f.read(root).tag()).expect(410);
  f.send("POST",root+"/ban-nop",Map.of(),f.svToken,null).expect(410);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_phien_ban_ho_so WHERE ho_so_id=?",Integer.class,a.root())).isEqualTo(versions);
  String other=f.identity("sv")[1];
  f.send("GET",root,null,other,null).expect(404);
  f.send("POST",root+"/ban-nop",Map.of(),other,null).expect(404);
  for(String stage:List.of("online","hard-copy"))f.send("GET","/api/ctsv/mghp/ho-so?stage="+stage,null,f.svToken,null).expect(403);
  f.send("GET","/api/ctsv/mghp/ho-so?stage=online",null,f.adminToken,null).expect(403);
 }
 @Test void STATUS03_councilResultDoesNotReplaceStatusOrLeakBeforePublication()throws Exception{
  String round=f.start();var a=f.source(round);f.approve(a);f.seal(round,true,f.read(f.context(round,true)).tag()).expect(200);f.assess(a,"dat");
  f.seal(round,false,f.read(f.context(round,false)).tag()).expect(200);
  var json=StageHandoffHttpIT.JSON;
  var body=json.createObjectNode().put("roundId",""+f.roundId(round)).put("code","STATUS25-QD-"+UUID.randomUUID()).put("type","initial").putNull("number").putNull("date").putNull("issuingUnitId");
  var draft=f.send("POST","/api/ctsv/mghp/quyet-dinh",body,f.cvToken,null).expect(201);
  String version="/api/ctsv/mghp/phien-ban-quyet-dinh/"+draft.json().path("id").asText();
  var meeting=json.createObjectNode().put("minutesCode","STATUS25-BB-"+UUID.randomUUID()).putNull("reference").put("meetingAt",StageHandoffHttpIT.NOW.minusSeconds(1).toString()).put("place","SYNTHETIC").put("reason","SYNTHETIC status projection");
  var recorded=f.send("POST",version+"/ket-luan-hop",meeting,f.cvToken,draft.tag()).expect(201);
  String current="/api/ctsv/mghp/phien-ban-quyet-dinh/"+recorded.json().path("id").asText();
  f.send("POST",current+"/xac-nhan",Map.of("reason","SYNTHETIC chốt"),f.cvToken,recorded.tag()).expect(200);
  var staff=read(root(a));assertThat(staff.path("status").asText()).isEqualTo("approved");assertThat(staff.path("councilResult").asText()).isEqualTo("selected");
  var student=f.send("GET",root(a),null,f.svToken,null).expect(200).json();
  assertThat(student.path("status").asText()).isEqualTo("approved");assertThat(student.path("councilResult").isNull()).isTrue();
  assertThat(student.has("intakeSealed")).isFalse();
  f.send("GET","/api/ctsv/mghp/ho-so?status=selected",null,f.svToken,null).expect(400);
  // Read projection only: no P12 publication command is implemented or claimed here.
  var finalVersion=f.read(current).json();
  jdbc.update("INSERT dbo.mghp_cong_bo(phien_ban_qd_id,ma_cong_bo,thoi_diem_cong_bo,sha256_tap_du_lieu) VALUES(?,?,?,?)",finalVersion.path("id").asLong(),"STATUS25-PUB-"+UUID.randomUUID(),StageHandoffHttpIT.NOW.atOffset(ZoneOffset.UTC),finalVersion.path("datasetSha256").asText());
  var published=f.send("GET",root(a),null,f.svToken,null).expect(200).json();
  assertThat(published.path("status").asText()).isEqualTo("approved");assertThat(published.path("councilResult").asText()).isEqualTo("selected");
 }
 @Test void STATUS04_studentWithdrawalUsesOwnProjectionEtagAndOnlineRefusalHasOwnReason()throws Exception{
  String round=f.start();var a=f.source(round);String root=root(a);
  var current=f.send("GET",root,null,f.svToken,null).expect(200);
  f.send("POST",root+"/rut",Map.of("reason","SYNTHETIC tự rút"),f.svToken,null).expect(428);
  f.send("POST",root+"/rut",Map.of("reason","SYNTHETIC tự rút"),f.svToken,"\"stale\"").expect(412);
  var withdrawn=f.send("POST",root+"/rut",Map.of("reason","SYNTHETIC tự rút"),f.svToken,current.tag()).expect(200).json();
  assertThat(withdrawn.path("status").asText()).isEqualTo("withdrawn");
  assertThat(withdrawn.path("onlineStatus").asText()).isEqualTo("withdrawn");
  assertThat(read(root).path("submittedVersions").size()).isEqualTo(1);
  var b=f.source(round);root=root(b);
  var refused=f.send("POST",root+"/ket-luan",Map.of("result","rejected","reason","SYNTHETIC không đúng đối tượng"),f.cvToken,f.read(root).tag()).expect(200).json();
  assertThat(refused.path("status").asText()).isEqualTo("rejected");
  assertThat(refused.path("onlineStatus").asText()).isEqualTo("rejected");
  assertThat(refused.path("rejectionStage").asText()).isEqualTo("online");
  assertThat(refused.path("rejectionReason").asText()).isEqualTo("SYNTHETIC không đúng đối tượng");
 }
 @Test void STATUS05_browserShowsEachStageAndStudentProgress()throws Exception{
  Assumptions.assumeTrue(Boolean.getBoolean("application.stage.browser.enabled"),"Opt-in browser harness; SQL/HTTP tests remain separate");
  String round=f.start();var pending=f.source(round);var approved=f.source(round);var rejected=f.source(round);
  for(var source:List.of(pending,approved,rejected))f.approve(source);
  f.seal(round,true,f.read(f.context(round,true)).tag()).expect(200);
  f.assess(approved,"dat");f.assess(rejected,"khong_dat");
  var accounts=new LinkedHashMap<String,Object>();
  for(var entry:Map.of("specialist",f.actor,"student",f.svUser,"admin",f.adminUser).entrySet()){
   String name=jdbc.queryForObject("SELECT TenDangNhap FROM dbo.TaiKhoan WHERE ID=CONVERT(uniqueidentifier,(SELECT ma_chu_the_dang_nhap FROM dbo.dinh_danh_dang_nhap WHERE nguoi_dung_id=? AND ma_nha_cung_cap=N'https://auth.ctsv.test'))",String.class,entry.getValue());
   accounts.put(entry.getKey(),Map.of("username",name,"password",StageHandoffHttpIT.PASSWORD));
  }
  var payload=Map.of("backend","https://127.0.0.1:"+port,"database","ctsv_review_lifecycle_test_20261122","accounts",accounts,"semesterId",""+f.semester,"roundId",""+f.roundId(round),"pendingId",""+pending.root(),"approvedId",""+approved.root(),"rejectedId",""+rejected.root());
  var log=java.nio.file.Path.of("target/application-stage-browser.log");
  var child=new ProcessBuilder("node","frontend/e2e/application-stage-status.cjs").redirectErrorStream(true).redirectOutput(log.toFile()).start();
  try(var input=child.getOutputStream()){input.write(StageHandoffHttpIT.JSON.writeValueAsBytes(payload));}
  if(!child.waitFor(120,java.util.concurrent.TimeUnit.SECONDS)){child.destroy();throw new AssertionError("Browser timeout; inspect target/application-stage-browser.log");}
  assertThat(child.exitValue()).as("Browser evidence: target/application-stage-browser.log").isZero();
 }
}
