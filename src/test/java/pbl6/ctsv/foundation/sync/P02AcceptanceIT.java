package pbl6.ctsv.foundation.sync;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import java.net.http.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import pbl6.ctsv.CtsvApplication;
import pbl6.p01.FaultDatabase;
import com.fasterxml.jackson.databind.*;
import static org.assertj.core.api.Assertions.*;

/** P02-99 technical acceptance on an isolated, non-parent resource; not independent G8 review. */
@SpringBootTest(classes=CtsvApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class P02AcceptanceIT {
    static final ObjectMapper JSON=new ObjectMapper();
    static final String RESOURCE="room-usage",PATH="/api/dao-tao/lich-su-dung-phong";
    final List<Map<String,Object>> evidence=new ArrayList<>();
    P02ResourceProvider provider;DurableSyncStore store;long source,baseline;
    static void sourceSql(String sql)throws Exception{try(var c=FaultDatabase.open();var s=c.createStatement()){FaultDatabase.guard(c);s.execute(sql);}}
    static long sourceNumber(String sql)throws Exception{return FaultDatabase.number(sql);}
    long targetNumber(String sql)throws Exception{try(var c=P02Database.open()){return P02Database.scalar(c,sql);}}
    void event(String test,Map<String,Object> details)throws Exception{
        var row=new LinkedHashMap<String,Object>();row.put("test",test);row.put("at",Instant.now().toString());row.put("sourceDB",FaultDatabase.NAME);row.put("targetDB",P02Database.NAME);row.put("SQL_HTTP","REAL two Boot backends, production resource adapters");row.putAll(details);evidence.add(row);
        JSON.writerWithDefaultPrettyPrinter().writeValue(P02ResourceProvider.OUT.resolve("acceptance-evidence.json").toFile(),evidence);
    }
    @BeforeAll void prepare()throws Exception{
        P02Database.migrate();DurableStoreIT.ds=P02Database.source();source=DurableStoreIT.source();store=DurableStoreIT.store();
        assertThat(sourceNumber("SELECT COUNT(*) FROM sys.change_tracking_tables")).isEqualTo(24);
        // Only reserved synthetic IDs; no table-wide reset/delete.
        sourceSql("SET IDENTITY_INSERT dbo.LichSuDungPhong ON;WITH n AS(SELECT TOP(1000) ROW_NUMBER() OVER(ORDER BY a.object_id,b.object_id) AS n FROM sys.all_objects a CROSS JOIN sys.all_objects b) INSERT dbo.LichSuDungPhong(ID,MalopHP,Phong,Ngay) SELECT 1800000+n,CONCAT('P02-PERF-',n),'P02-UNRESOLVED','2026-11-12' FROM n WHERE NOT EXISTS(SELECT 1 FROM dbo.LichSuDungPhong t WHERE t.ID=1800000+n);SET IDENTITY_INSERT dbo.LichSuDungPhong OFF;");
        provider=new P02ResourceProvider();provider.start();
    }
    @AfterAll void close()throws Exception{
        if(provider!=null)provider.close();
        sourceSql("ALTER DATABASE [p01_sis_fault_20261111] SET CHANGE_TRACKING(CHANGE_RETENTION=90 DAYS,AUTO_CLEANUP=ON)");
    }
    DurableSyncStore.Outcome sync(DurableSyncStore selected)throws Exception{
        try(var client=new CtHttpClient(provider.uri,RESOURCE,provider.key,500)){return new CtSyncService(selected,client,new JdbcFoundationTarget(RESOURCE),source,500,1000000).sync();}
    }
    JsonNode call(String query,int status,String code)throws Exception{
        try(var http=HttpClient.newHttpClient()){
            var request=HttpRequest.newBuilder(provider.uri.resolve(PATH+query)).GET().header("X-Service-Key",provider.key).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
            assertThat(response.body()).doesNotContain(provider.key,"jdbc:sqlserver");var node=JSON.readTree(response.body());
            if(code!=null)assertThat(node.path("code").asText()).isEqualTo(code);else new CtSchema(RESOURCE).validate(node);return node;
        }
    }
    @Test @Order(1) void measureActualThousandRowExportWithoutClaimingFullScaleCapacity()throws Exception{
        long start=System.nanoTime();long heapBefore=java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();var result=sync(store);baseline=result.version();
        assertThat(result.processed()).isGreaterThanOrEqualTo(1000);assertThat(result.complete()).isTrue();
        long disk=0;try(var files=Files.walk(provider.spool)){for(var p:files.filter(Files::isRegularFile).toList())disk+=Files.size(p);}
        var cmd=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","jcmd.exe").toString(),Long.toString(provider.process.pid()),"GC.heap_info").redirectErrorStream(true).start();
        assertThat(cmd.waitFor(10,TimeUnit.SECONDS)).isTrue();String sourceHeap=new String(cmd.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertThat(cmd.exitValue()).isZero();
        event("Measured bounded 1000-row profile",Map.of("rows",result.processed(),"elapsedMs",Duration.ofNanos(System.nanoTime()-start).toMillis(),"consumerHeapBefore",heapBefore,"consumerHeapAfter",java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),"sourceHeap",sourceHeap,"privateSpoolBytes",disk,"sourceVersionStoreKB",sourceNumber("SELECT COALESCE((SELECT reserved_page_count*8 FROM sys.dm_tran_version_store_space_usage WHERE database_id=DB_ID()),0)"),"fullScaleBenchmark","NOT VERIFIED; no SLA or increased quota"));
    }
    @Test @Order(2) void realRetentionRecoveryRollsBackFinalFailureAndReconcilesOnlyAfterSuccess()throws Exception{
        assertThat(baseline).isPositive();provider.start();
        try{
            sourceSql("ALTER DATABASE [p01_sis_fault_20261111] SET CHANGE_TRACKING(CHANGE_RETENTION=1 MINUTES,AUTO_CLEANUP=ON);UPDATE dbo.LichSuDungPhong SET MalopHP='P02-PERF-UPDATED' WHERE ID=1800001;DELETE dbo.LichSuDungPhong WHERE ID=1801000 AND MalopHP='P02-PERF-1000';");
            Thread.sleep(70000);long deadline=System.nanoTime()+Duration.ofMinutes(4).toNanos(),min=0;
            while(System.nanoTime()<deadline){sourceSql("CHECKPOINT;EXEC sys.sp_flush_CT_internal_table_on_demand;");min=sourceNumber("SELECT CHANGE_TRACKING_MIN_VALID_VERSION(OBJECT_ID('dbo.LichSuDungPhong'))");if(min>baseline)break;Thread.sleep(5000);}
            assertThat(min).isGreaterThan(baseline);call("?since_version="+baseline+"&source_epoch="+provider.epoch,409,"FULL_SYNC_REQUIRED");
            var broken=new DurableSyncStore(P02Database.source(),P02Database.NAME,"DEV2022"){
                @Override protected void fault(String point,Connection c)throws Exception{if(point.equals("final.afterCheckpoint")){c.close();throw new SQLException("Injected pre-commit lost connection");}}
            };
            assertThatThrownBy(()->sync(broken)).isInstanceOf(IllegalStateException.class);
            try(var lease=store.acquire(source,RESOURCE)){assertThat(store.checkpoint(lease).version()).isEqualTo(baseline);}
            assertThat(targetNumber("SELECT dang_hien_dien_nguon FROM dbo.lich_su_dung_phong WHERE he_thong_nguon_id="+source+" AND ma_dinh_danh_ben_ngoai=N'1801000'")).isEqualTo(1);
            provider.start();var recovered=sync(store);assertThat(recovered.complete()).isTrue();
            assertThat(targetNumber("SELECT dang_hien_dien_nguon FROM dbo.lich_su_dung_phong WHERE he_thong_nguon_id="+source+" AND ma_dinh_danh_ben_ngoai=N'1801000'")).isZero();
            event("SYNC03/04 retention + final rollback/recovery",Map.of("source",source,"L",baseline,"minValid",min,"recoveredU",recovered.version(),"oldCheckpointPreservedOnFailure",true,"absenceOnlyAfterFinalSuccess",true));
        }finally{sourceSql("ALTER DATABASE [p01_sis_fault_20261111] SET CHANGE_TRACKING(CHANGE_RETENTION=90 DAYS,AUTO_CLEANUP=ON)");}
    }
    @Test @Order(3) void sourceEpochCursorQuotaAndMissingTrackingFailClosed()throws Exception{
        provider.start();String cursor=call("?limit=1",200,null).path("next_cursor").asText();call("?limit=1",200,null);call("?limit=1",503,"EXPORT_CAPACITY_EXCEEDED");
        provider.start();call("?cursor="+cursor,409,"EXPORT_EXPIRED");
        DurableSyncStore.Checkpoint old;try(var lease=store.acquire(source,RESOURCE)){old=store.checkpoint(lease);}
        provider.close();provider=new P02ResourceProvider();provider.start();
        assertThatThrownBy(()->sync(store)).isInstanceOfSatisfying(CtHttpClient.RemoteFailure.class,e->assertThat(e.code).isEqualTo("SOURCE_EPOCH_CHANGED"));
        try(var lease=store.acquire(source,RESOURCE)){assertThat(store.checkpoint(lease)).isEqualTo(old);}
        call("?since_version=9223372036854775807&source_epoch="+provider.epoch,409,"SOURCE_LINEAGE_INVALID");
        sourceSql("ALTER TABLE dbo.LichSuDungPhong DISABLE CHANGE_TRACKING");
        try{call("",503,"SYNC_NOT_READY");}finally{sourceSql("ALTER TABLE dbo.LichSuDungPhong ENABLE CHANGE_TRACKING WITH(TRACK_COLUMNS_UPDATED=OFF)");}
        event("SYNC05/07 epoch, restart, quota, readiness",Map.of("checkpointUnchanged",true,"quota",2,"oldCursorStatus",409,"epochStatus",409,"missingCtStatus",503,"trackingRestored",sourceNumber("SELECT COUNT(*) FROM sys.change_tracking_tables WHERE object_id=OBJECT_ID('dbo.LichSuDungPhong')")));
    }
}
