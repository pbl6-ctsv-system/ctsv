package pbl6.ctsv.foundation.sync;
import java.sql.*;import java.time.*;import java.util.*;import java.nio.file.*;import java.net.*;import java.net.http.*;import java.util.concurrent.*;import java.util.regex.*;
import org.junit.jupiter.api.*;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.boot.test.context.TestConfiguration;import org.springframework.context.annotation.Bean;import org.springframework.beans.factory.annotation.Autowired;
import pbl6.ctsv.CtsvApplication;import pbl6.p01.FaultDatabase;import com.fasterxml.jackson.databind.*;
import static org.assertj.core.api.Assertions.*;

/** Two REAL Spring Boot backends, REAL HTTP, separate SIS fault and CTSV P02 databases. Test-only resource adapters. */
@SpringBootTest(classes={CtsvApplication.class,CtHttpEngineIT.ConsumerConfiguration.class},webEnvironment=SpringBootTest.WebEnvironment.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CtHttpEngineIT {
 @TestConfiguration(proxyBeanMethods=false) static class ConsumerConfiguration {@Bean DurableSyncStore p02Store()throws Exception{return new DurableSyncStore(P02Database.source(),P02Database.NAME,"DEV2022");}}
 @Autowired DurableSyncStore store;
 static final ObjectMapper JSON=new ObjectMapper();static final List<Map<String,Object>> evidence=new ArrayList<>();
 static final Path OUT=Path.of("target","p02");
 @BeforeAll static void prepare()throws Exception{DurableStoreIT.setup();Files.createDirectories(OUT);}
 static void event(String test,Map<String,Object> values)throws Exception{var row=new LinkedHashMap<String,Object>();row.put("test",test);row.put("at",Instant.now().toString());row.put("sourceSQL","REAL:p01_sis_fault_20261111");row.put("targetSQL","REAL:"+P02Database.NAME);row.put("HTTP","REAL");row.put("backend","REAL SIS child process + CTSV Spring Boot context");row.put("adapter","TEST_ONLY — not P02-03 implementation");row.putAll(values);evidence.add(row);JSON.writerWithDefaultPrettyPrinter().writeValue(OUT.resolve("ct-engine-http-evidence.json").toFile(),evidence);}
 static void sourceSql(String sql)throws Exception{try(var c=FaultDatabase.open()){FaultDatabase.guard(c);try(var s=c.createStatement()){s.execute(sql);}}}
 static long scalarSource(String sql)throws Exception{try(var c=FaultDatabase.open();var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
 static long seed(String label)throws Exception{try(var c=FaultDatabase.open();var s=c.prepareStatement("INSERT dbo.DanToc(MaDanToc,TenDanToc) VALUES(?,N'P02 giả lập');SELECT CAST(SCOPE_IDENTITY() AS bigint)")){s.setString(1,"P02-"+UUID.randomUUID()+"-"+label);try(var r=s.executeQuery()){r.next();return r.getLong(1);}}}
 static final class Provider implements AutoCloseable {
  final String key=UUID.randomUUID().toString(),denied=UUID.randomUUID().toString();UUID epoch=UUID.randomUUID();Process process;URI uri;final Path spool=OUT.resolve("spool-"+UUID.randomUUID());
  void start()throws Exception{
   close();FaultDatabase.dataSource();
   String cp=Path.of("..","daotao","target","test-classes").toAbsolutePath()+java.io.File.pathSeparator+Path.of("..","daotao","target","classes").toAbsolutePath()+java.io.File.pathSeparator+System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
   Path log=OUT.resolve("provider-"+UUID.randomUUID()+".log");
   var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Xmx512m","-cp",cp,"pbl6.p02.CtFixtureProvider","--spring.profiles.active=local","--server.address=127.0.0.1","--server.port=0","--ct26.enabled=true");
   String jdbc=((org.springframework.jdbc.datasource.SimpleDriverDataSource)FaultDatabase.dataSource()).getUrl()+"integratedSecurity=true;encrypt=true;trustServerCertificate=true;connectRetryCount=0;";
   builder.environment().put("SIS_JDBC_URL",jdbc);builder.environment().put("SIS_SOURCE_EPOCH",epoch.toString());builder.environment().put("SIS_SERVICE_KEY",key);
   builder.environment().put("SPRING_APPLICATION_JSON",JSON.writeValueAsString(Map.of("sis",Map.of("ct26",Map.of("sourceEpoch",epoch.toString(),"spoolDirectory",spool.toAbsolutePath().toString(),"serviceKeys",Map.of("ctsv",key,"denied",denied),"serviceResources",Map.of("ctsv",List.of("ethnicities"),"denied",List.of()))))));
   builder.redirectErrorStream(true).redirectOutput(log.toFile());process=builder.start();long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
   while(process.isAlive()&&System.nanoTime()<deadline){if(Files.exists(log)){var m=Pattern.compile("Tomcat started on port (\\d+)").matcher(Files.readString(log));if(m.find()){uri=URI.create("http://127.0.0.1:"+m.group(1));assertThat(Files.readString(log)).doesNotContain(key,denied);return;}}Thread.sleep(100);}
   throw new IllegalStateException("Fixture SIS startup failed; inspect "+log);
  }
  JsonNode call(String query,String credential,int status,String code)throws Exception{
   var q=HttpRequest.newBuilder(uri.resolve("/api/dao-tao/dan-toc"+query)).timeout(Duration.ofSeconds(40)).GET();if(credential!=null)q.header("X-Service-Key",credential);
   try(var client=HttpClient.newHttpClient()){
    var response=client.send(q.build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(status);assertThat(response.body()).doesNotContain(key,denied,"jdbc:sqlserver","SQLServerException");var n=JSON.readTree(response.body());
    if(status==200){new CtSchema("ethnicities").validate(n);assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");}
    else{assertThat(n.path("code").asText()).isEqualTo(code);assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");}
    return n;
   }
  }
  CtHttpClient client(int limit){return new CtHttpClient(uri,"ethnicities",key,limit);}
  public void close()throws Exception{if(process!=null&&process.isAlive()){process.destroy();if(!process.waitFor(10,TimeUnit.SECONDS))process.destroyForcibly().waitFor();}}
 }
 DurableSyncStore.Outcome sync(Provider p,long source,int limit){try(var client=p.client(limit)){return new CtSyncService(store,client,new DurableStoreIT.Target(),source,limit,10000).sync();}}
 @Test @Order(1) void realFullDeltaReplayDeleteAndPrivateSecurityContract()throws Exception{
  sourceSql("DELETE dbo.DanToc");long a=seed("A"),b=seed("B"),source=DurableStoreIT.source();
  try(var p=new Provider()){
   p.start();
   try(var http=HttpClient.newHttpClient()){
    var unitUri=p.uri.resolve("/api/dao-tao/don-vi");
    assertThat(http.send(HttpRequest.newBuilder(unitUri).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    var units=http.send(HttpRequest.newBuilder(unitUri).header("X-Service-Key",p.key).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(units.statusCode()).isEqualTo(200);new pbl6.ctsv.foundation.units.validation.UnitContractValidator().validate(JSON.readTree(units.body()));
   }
   p.call("",null,401,"INVALID_SERVICE_KEY");p.call("",p.denied,403,"SERVICE_FORBIDDEN");p.call("?limit=501",p.key,400,"INVALID_REQUEST");
   var first=sync(p,source,1);assertThat(first.processed()).isEqualTo(2);long local=DurableStoreIT.scalar("SELECT id FROM dbo.p02_fixture_projection WHERE source_id="+source+" AND external_id="+a);
   assertThat(local).isNotEqualTo(a);p.start();sourceSql("UPDATE dbo.DanToc SET TenDanToc=N'Changed raw' WHERE DanTocID="+a);long added=seed("C");
   var delta=sync(p,source,1);assertThat(delta.processed()).isEqualTo(2);p.start();var replay=sync(p,source,1);assertThat(replay.processed()).isZero();
   p.start();sourceSql("DELETE dbo.DanToc WHERE DanTocID="+b);var deleted=sync(p,source,1);assertThat(deleted.processed()).isEqualTo(1);
   assertThat(DurableStoreIT.scalar("SELECT present FROM dbo.p02_fixture_projection WHERE source_id="+source+" AND external_id="+b)).isZero();assertThat(DurableStoreIT.scalar("SELECT id FROM dbo.p02_fixture_projection WHERE source_id="+source+" AND external_id="+a)).isEqualTo(local);
   event("SYNC-01/02/06/07 full-delta-replay-delete",Map.of("source",source,"sourceId",a,"localId",local,"insertedId",added,"fullU",first.version(),"deltaU",delta.version(),"deleteU",deleted.version(),"replayRows",replay.processed()));
  }
 }
 @Test @Order(2) void actualRetentionForcesBoundedFullRecoveryAndFinalRollbackKeepsOldCheckpoint()throws Exception{
  sourceSql("DELETE dbo.DanToc");long a=seed("RET-A"),b=seed("RET-B"),source=DurableStoreIT.source();
  try(var p=new Provider()){
   p.start();var first=sync(p,source,1);long lower=first.version();p.start();
   sourceSql("ALTER DATABASE [p01_sis_fault_20261111] SET CHANGE_TRACKING(CHANGE_RETENTION=1 MINUTES,AUTO_CLEANUP=ON);UPDATE dbo.DanToc SET TenDanToc=N'Retention new' WHERE DanTocID="+a+";DELETE dbo.DanToc WHERE DanTocID="+b);
   long deadline=System.nanoTime()+Duration.ofMinutes(5).toNanos();Thread.sleep(70000);long min=0;
   while(System.nanoTime()<deadline){sourceSql("CHECKPOINT;EXEC sys.sp_flush_CT_internal_table_on_demand;");min=scalarSource("SELECT CHANGE_TRACKING_MIN_VALID_VERSION(OBJECT_ID('dbo.DanToc'))");if(min>lower)break;Thread.sleep(5000);}
   assertThat(min).isGreaterThan(lower);p.call("?since_version="+lower+"&source_epoch="+p.epoch,p.key,409,"FULL_SYNC_REQUIRED");
   var broken=new DurableSyncStore(P02Database.source(),P02Database.NAME,"DEV2022"){@Override protected void fault(String point,Connection c)throws Exception{if(point.equals("final.afterCheckpoint")){c.close();throw new SQLException("Final fault");}}};
   try(var client=p.client(1)){var service=new CtSyncService(broken,client,new DurableStoreIT.Target(),source,1,10000);assertThatThrownBy(service::sync).isInstanceOf(IllegalStateException.class);}
   try(var lease=store.acquire(source,"ethnicities")){assertThat(store.checkpoint(lease).version()).isEqualTo(lower);}
   assertThat(DurableStoreIT.scalar("SELECT present FROM dbo.p02_fixture_projection WHERE source_id="+source+" AND external_id="+b)).isEqualTo(1);
   p.start();var recovered=sync(p,source,1);assertThat(recovered.complete()).isTrue();assertThat(DurableStoreIT.scalar("SELECT present FROM dbo.p02_fixture_projection WHERE source_id="+source+" AND external_id="+b)).isZero();
   event("SYNC-03/04 real retention and rollback/recovery",Map.of("source",source,"L",lower,"minValid",min,"recoveredU",recovered.version(),"checkpointPreservedOnFault",true));
  }finally{sourceSql("ALTER DATABASE [p01_sis_fault_20261111] SET CHANGE_TRACKING(CHANGE_RETENTION=90 DAYS,AUTO_CLEANUP=ON)");}
 }
 @Test @Order(3) void lineageResetStopsWritesAndOldCursorExpiresWithoutChangingCheckpoint()throws Exception{
  sourceSql("DELETE dbo.DanToc");seed("EPOCH-A");seed("EPOCH-B");long source=DurableStoreIT.source();
  try(var p=new Provider()){
   p.start();var first=sync(p,source,1);p.start();String cursor=p.call("?limit=1",p.key,200,null).path("next_cursor").asText();p.close();
   sourceSql("ALTER TABLE dbo.DanToc DISABLE CHANGE_TRACKING;ALTER TABLE dbo.DanToc ENABLE CHANGE_TRACKING WITH(TRACK_COLUMNS_UPDATED=OFF)");UUID old=p.epoch;p.epoch=UUID.randomUUID();p.start();
   p.call("?cursor="+cursor,p.key,409,"EXPORT_EXPIRED");p.call("?since_version="+first.version()+"&source_epoch="+old,p.key,409,"SOURCE_EPOCH_CHANGED");
   assertThatThrownBy(()->sync(p,source,1)).isInstanceOfSatisfying(CtHttpClient.RemoteFailure.class,e->assertThat(e.code).isEqualTo("SOURCE_EPOCH_CHANGED"));
   try(var lease=store.acquire(source,"ethnicities")){assertThat(store.checkpoint(lease).epoch()).isEqualTo(old);assertThat(store.checkpoint(lease).version()).isEqualTo(first.version());}
   p.call("?since_version=9223372036854775807&source_epoch="+p.epoch,p.key,409,"SOURCE_LINEAGE_INVALID");
   event("SYNC-05 lineage reset",Map.of("source",source,"checkpointL",first.version(),"oldEpoch",old.toString(),"newEpoch",p.epoch.toString(),"writesStopped",true));
  }
 }
 @Test @Order(4) void twoExportQuotaAndReadinessAreRealHttpFailures()throws Exception{
  sourceSql("DELETE dbo.DanToc");seed("QUOTA-A");seed("QUOTA-B");
  try(var p=new Provider()){
   p.start();p.call("?limit=1",p.key,200,null);p.call("?limit=1",p.key,200,null);p.call("?limit=1",p.key,503,"EXPORT_CAPACITY_EXCEEDED");p.start();
   sourceSql("ALTER TABLE dbo.DanToc DISABLE CHANGE_TRACKING");try{p.call("",p.key,503,"SYNC_NOT_READY");}finally{sourceSql("ALTER TABLE dbo.DanToc ENABLE CHANGE_TRACKING WITH(TRACK_COLUMNS_UPDATED=OFF)");}
   event("SYNC-07/08 real quota/readiness",Map.of("capacityStatus",503,"activeExportLimit",2,"missingCTStatus",503,"noPartial200",true));
  }
 }
}
