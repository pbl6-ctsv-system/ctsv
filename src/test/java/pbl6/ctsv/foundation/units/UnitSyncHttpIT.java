package pbl6.ctsv.foundation.units;

import java.net.URI;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.fasterxml.jackson.databind.ObjectMapper;
import pbl6.ctsv.foundation.units.repository.UnitSyncRepository;
import pbl6.ctsv.foundation.units.service.UnitSyncService;
import pbl6.p00.SliceATestDatabaseSupport;
import static org.assertj.core.api.Assertions.*;

/** TWO_BACKENDS_REAL + HTTP_REAL + SQL_SERVER_REAL. No SIS stub in this test. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
    "foundation.units.enabled=true","foundation.units.run-on-startup=false",
    "foundation.units.page-size=500","foundation.units.expected-database=ctsv_test_20261111",
    "foundation.units.expected-instance=DEV2022"
})
@Import(UnitSyncHttpIT.Database.class)
class UnitSyncHttpIT {
    static final String EPOCH="11111111-2222-3333-4444-555555555555";
    static final String PREFIX="P01-LIVE-"+UUID.randomUUID();
    static final String KEY=UUID.randomUUID().toString(); // Synthetic test key only; never written to evidence.
    static Process provider;
    static Path providerLog;
    static URI providerUri;
    static long sourceId,studentUnit,newUnit;
    static SimpleDriverDataSource sis;
    @LocalServerPort int ctsvPort;
    @Autowired UnitSyncService service;
    @Autowired UnitSyncRepository repository;

    @TestConfiguration(proxyBeanMethods=false)
    static class Database {
        @Bean DataSource dataSource()throws Exception {
            SliceATestDatabaseSupport.migrate();return SliceATestDatabaseSupport.dataSource();
        }
    }
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry)throws Exception {
        String port=System.getenv("P00_SQL_PORT");
        if(port==null||!port.matches("[1-9][0-9]{0,4}")||Integer.parseInt(port)>65535)
            throw new IllegalStateException("Verified SQL TCP port is required");
        var properties=new Properties();
        properties.setProperty("integratedSecurity","true");properties.setProperty("encrypt","true");
        properties.setProperty("trustServerCertificate","true");properties.setProperty("connectRetryCount","0");
        properties.setProperty("loginTimeout","5");properties.setProperty("socketTimeout","45000");
        sis=new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),
            "jdbc:sqlserver://localhost:"+port+";databaseName=sis_test_20261111;",properties);
        try(var c=sis.getConnection();var s=c.createStatement();
                var r=s.executeQuery("SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName'))")){
            if(!r.next()||!"sis_test_20261111".equals(r.getString(1))||!"DEV2022".equals(r.getString(2)))
                throw new IllegalStateException("Unexpected SIS target");
        }
        try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement(
                "INSERT dbo.he_thong_ngoai(ma,ten) VALUES(?,N'Synthetic live SIS'); SELECT CAST(SCOPE_IDENTITY() AS bigint)")){
            s.setString(1,PREFIX);try(var r=s.executeQuery()){r.next();sourceId=r.getLong(1);}
        }
        try(var c=sis.getConnection();var s=c.prepareStatement(
                "DECLARE @ids TABLE(id bigint); INSERT dbo.DonVi(MaDV,TenDV,LuuTru,IsUs) OUTPUT inserted.ID INTO @ids VALUES(?,N'Đơn vị thử nghiệm',N'raw',NULL); SELECT id FROM @ids")){
            s.setString(1,PREFIX);try(var r=s.executeQuery()){r.next();studentUnit=r.getLong(1);}
        }
        startProvider();
        registry.add("foundation.units.base-url",()->providerUri.toString());
        registry.add("foundation.units.service-key",()->KEY);
        registry.add("foundation.units.source-system-id",()->Long.toString(sourceId));
    }
    static void startProvider()throws Exception {
        Path jar=Path.of("..","daotao","target","daotao-0.0.1-SNAPSHOT.jar").toAbsolutePath().normalize();
        if(!Files.isRegularFile(jar))throw new IllegalStateException("Build the current SIS artifact before the live test");
        providerLog=Path.of("target","p01-live-sis-"+UUID.randomUUID()+".log");
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
            "-Xmx512m","-jar",jar.toString(),"--spring.profiles.active=local","--server.address=127.0.0.1","--server.port=0");
        builder.environment().put("SIS_SOURCE_EPOCH",EPOCH);builder.environment().put("SIS_SERVICE_KEY",KEY);
        builder.environment().put("SIS_JDBC_URL",sis.getUrl()+"integratedSecurity=true;encrypt=true;trustServerCertificate=true;connectRetryCount=0;");
        builder.redirectErrorStream(true).redirectOutput(providerLog.toFile());
        provider=builder.start();
        var pattern=Pattern.compile("Tomcat started on port (\\d+)");
        long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
        while(System.nanoTime()<deadline){
            if(!provider.isAlive())throw new IllegalStateException("SIS process failed; inspect "+providerLog);
            if(Files.exists(providerLog)){
                var match=pattern.matcher(Files.readString(providerLog));
                if(match.find()){providerUri=URI.create("http://127.0.0.1:"+match.group(1));return;}
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("SIS startup deadline exceeded; inspect "+providerLog);
    }
    static void stopProvider()throws Exception {
        if(provider!=null){provider.destroy();if(!provider.waitFor(10,java.util.concurrent.TimeUnit.SECONDS))provider.destroyForcibly().waitFor();}
    }
    @AfterAll static void cleanup()throws Exception {
        stopProvider();
        if(sis!=null)try(var c=sis.getConnection();var s=c.prepareStatement("DELETE dbo.DonVi WHERE MaDV IN(?,?)")){s.setString(1,PREFIX);s.setString(2,PREFIX+"-new");s.executeUpdate();}
        if(sourceId!=0)try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement("DELETE dbo.don_vi WHERE he_thong_nguon_id=?")){s.setLong(1,sourceId);s.executeUpdate();}
    }
    String sourceField(String column)throws Exception {
        if(!Set.of("luu_tru_nguon","is_us_nguon","dang_hoat_dong_nguon").contains(column))throw new IllegalArgumentException();
        try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement(
                "SELECT "+column+" FROM dbo.don_vi WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai=?")){
            s.setLong(1,sourceId);s.setString(2,Long.toString(studentUnit));try(var r=s.executeQuery()){r.next();return r.getString(1);}
        }
    }
    void crashWorker(String stage)throws Exception {
        Path output=Path.of("target","p01-live-worker-"+stage.replace('.','-')+".log");
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
            "-Xmx256m","-Dspring.profiles.active=test","-cp",System.getProperty("java.class.path"),
            ForkedUnitSyncWorker.class.getName(),Long.toString(sourceId),providerUri.toString(),stage);
        builder.environment().put("P01_FIXTURE_SERVICE_KEY",KEY);
        builder.redirectErrorStream(true).redirectOutput(output.toFile());Process worker=builder.start();
        try{
            assertThat(worker.waitFor(40,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(worker.exitValue()).as("child output: "+output).isEqualTo(86);
            assertThat(Files.readString(output)).contains("P01_CRASH_BARRIER:"+stage);
        }finally{if(worker.isAlive())worker.destroyForcibly().waitFor();}
    }
    @Test void sa002_004_011_017_020_031_sae02_twoBackendsFullUpdateDeltaReplayDeleteAndStableEpochRestart()throws Exception {
        assertThat(ctsvPort).isPositive();
        var full=service.sync();
        String oldCursor;
        try(var sourceClient=new pbl6.ctsv.foundation.units.client.SisUnitsClient(providerUri,KEY,1)){
            oldCursor=sourceClient.full().nextCursor();assertThat(oldCursor).isNotNull();
        }
        try(var c=sis.getConnection()){
            assertThat(SliceATestDatabaseSupport.scalar(c,"SELECT COUNT(*) FROM sys.dm_tran_active_snapshot_database_transactions a JOIN sys.dm_tran_database_transactions d ON d.transaction_id=a.transaction_id WHERE d.database_id=DB_ID() AND a.is_snapshot=1"))
                .as("materialized batch retains no source SNAPSHOT transaction across HTTP pages").isZero();
        }
        Map<String,Long> mapping;try(var c=SliceATestDatabaseSupport.open()){mapping=repository.mappings(c,sourceId);}
        Long localId=mapping.get(Long.toString(studentUnit));
        assertThat(localId).isNotNull().isNotEqualTo(studentUnit);
        assertThat(sourceField("luu_tru_nguon")).isEqualTo("raw");
        try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement(
                "UPDATE dbo.don_vi SET loai_don_vi=N'LOCAL-OWNED',dang_hoat_dong=1 WHERE id=?")){
            s.setLong(1,localId);s.executeUpdate();
        }
        // Provider process restart retains the configured source_epoch and DB CT lineage.
        URI original=providerUri;
        stopProvider();
        // Restart at the same port so the live consumer retains its configured endpoint.
        Path jar=Path.of("..","daotao","target","daotao-0.0.1-SNAPSHOT.jar").toAbsolutePath().normalize();
        providerLog=Path.of("target","p01-live-sis-restart-"+UUID.randomUUID()+".log");
        var pb=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
            "-Xmx512m","-jar",jar.toString(),"--spring.profiles.active=local","--server.address=127.0.0.1","--server.port="+original.getPort());
        pb.environment().put("SIS_SOURCE_EPOCH",EPOCH);pb.environment().put("SIS_SERVICE_KEY",KEY);
        pb.environment().put("SIS_JDBC_URL",sis.getUrl()+"integratedSecurity=true;encrypt=true;trustServerCertificate=true;connectRetryCount=0;");
        pb.redirectErrorStream(true).redirectOutput(providerLog.toFile());provider=pb.start();
        long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
        while(provider.isAlive()&&System.nanoTime()<deadline&&!Files.readString(providerLog).contains("Tomcat started on port"))Thread.sleep(100);
        assertThat(provider.isAlive()).isTrue();assertThat(Files.readString(providerLog)).contains("Tomcat started on port");
        try(var sourceClient=new pbl6.ctsv.foundation.units.client.SisUnitsClient(providerUri,KEY,1)){
            assertThatThrownBy(()->sourceClient.next(oldCursor)).isInstanceOfSatisfying(
                pbl6.ctsv.foundation.units.client.SisUnitsClient.RemoteFailure.class,e->assertThat(e.code()).isEqualTo("EXPORT_EXPIRED"));
        }
        try(var c=sis.getConnection();var s=c.prepareStatement("UPDATE dbo.DonVi SET LuuTru=NULL,IsUs=N'raw-classification',dang_hoat_dong=0 WHERE ID=?")){
            s.setLong(1,studentUnit);assertThat(s.executeUpdate()).isEqualTo(1);
        }
        try(var c=sis.getConnection();var s=c.prepareStatement("DECLARE @ids TABLE(id bigint); INSERT dbo.DonVi(MaDV,TenDV) OUTPUT inserted.ID INTO @ids VALUES(?,N'Synthetic newly inserted'); SELECT id FROM @ids")){
            s.setString(1,PREFIX+"-new");try(var r=s.executeQuery()){r.next();newUnit=r.getLong(1);}
        }
        crashWorker("page.afterCommit");
        try(var c=SliceATestDatabaseSupport.open()){
            assertThat(repository.checkpoint(c,sourceId)).isEqualTo(full.checkpoint());
            assertThat(repository.mappings(c,sourceId)).doesNotContainKey(Long.toString(newUnit));
        }
        assertThat(sourceField("luu_tru_nguon")).isNull(); // first page really committed before JVM exit
        var delta=service.sync();
        assertThat(delta.processed()).isEqualTo(2);
        assertThat(UnitSyncService.Checkpoint.parse(delta.checkpoint()).version()).isGreaterThan(UnitSyncService.Checkpoint.parse(full.checkpoint()).version());
        assertThat(sourceField("luu_tru_nguon")).isNull();assertThat(sourceField("is_us_nguon")).isEqualTo("raw-classification");
        assertThat(sourceField("dang_hoat_dong_nguon")).isEqualTo("0");
        var replay=service.sync();assertThat(replay.processed()).isZero();
        try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement("SELECT id,loai_don_vi,dang_hoat_dong FROM dbo.don_vi WHERE id=?")){
            s.setLong(1,localId);try(var r=s.executeQuery()){r.next();assertThat(r.getLong(1)).isEqualTo(localId);assertThat(r.getString(2)).isEqualTo("LOCAL-OWNED");assertThat(r.getBoolean(3)).isTrue();}
        }
        try(var c=sis.getConnection();var s=c.prepareStatement("UPDATE dbo.DonVi SET LuuTru=N'after-hard-crash' WHERE ID=?")){s.setLong(1,studentUnit);s.executeUpdate();}
        crashWorker("final.afterCheckpoint");
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.checkpoint(c,sourceId)).isEqualTo(delta.checkpoint());}
        assertThat(sourceField("luu_tru_nguon")).isNull(); // OS-closed SQL session rolled back the entire final transaction
        service.sync();assertThat(sourceField("luu_tru_nguon")).isEqualTo("after-hard-crash");
        try(var c=sis.getConnection();var s=c.prepareStatement("DELETE dbo.DonVi WHERE ID=?")){s.setLong(1,studentUnit);assertThat(s.executeUpdate()).isEqualTo(1);}
        var deleted=service.sync();assertThat(deleted.processed()).isEqualTo(1);
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.mappings(c,sourceId).get(Long.toString(studentUnit))).isEqualTo(localId);}
        var evidence=new LinkedHashMap<String,Object>();
        evidence.put("mode","TWO_BACKENDS_REAL_HTTP_SQL");evidence.put("sisPid",provider.pid());evidence.put("ctsvPid",ProcessHandle.current().pid());
        evidence.put("sourceId",sourceId);evidence.put("sourceUnitId",studentUnit);evidence.put("localUnitId",localId);
        evidence.put("fullCheckpoint",full.checkpoint());evidence.put("deltaCheckpoint",delta.checkpoint());evidence.put("deleteCheckpoint",deleted.checkpoint());
        evidence.put("replayCount",replay.processed());evidence.put("providerLog",providerLog.toString());
        evidence.put("consumerPageSize",500);evidence.put("crashWorkerPageSize",1);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of("target","p01-live-evidence.json").toFile(),evidence);
    }
}
