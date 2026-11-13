package pbl6.ctsv.foundation.units;

import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import pbl6.ctsv.foundation.units.client.SisUnitsClient;
import pbl6.ctsv.foundation.units.dto.*;
import pbl6.ctsv.foundation.units.service.*;
import pbl6.p01.FaultDatabase;
import static org.assertj.core.api.Assertions.*;

/** Opt-in FT: real provider HTTP + real production consumer service, only one isolated physical DB. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UnitFaultRecoveryFT {
    static Connection suiteLock;
    @BeforeAll static void setup()throws Exception{suiteLock=FaultAdmin.acquireSuite();FaultSyncFixture.projection();}
    @AfterAll static void unlock()throws Exception{if(suiteLock!=null)suiteLock.close();}
    @Test @Order(1) void sa003_emptyRealProviderAndConsumerCommitAnEngineCheckpoint()throws Exception{
        try(var f=new FaultSyncFixture()){
            f.resetSource();f.start();var page=f.ok("?limit=500");
            assertThat(page.path("items").isEmpty()).isTrue();
            try(var client=f.client(500)){
                long source=f.emptyConsumer();var result=f.service(client,source,500).sync();
                assertThat(result.processed()).isZero();assertThat(f.checkpoint(source)).isEqualTo(f.epoch+":"+client.lastFull.toVersion());
                FaultSyncFixture.record("PASS:SA-003",Map.of("sourceId",source,"checkpoint",result.checkpoint(),"items",0));
            }
        }
    }
    @Test @Order(2) void sa023to030_realRetentionCleanupFaultsAndAtomicFullRecovery()throws Exception{
        try(var f=new FaultSyncFixture()){
            f.resetSource();long a=f.add("A","old-A"),b=f.add("B","old-B"),removed=f.add("C","old-C");f.start();
            UnitExportResponse historical;
            try(var client=f.client(500)){historical=client.full();}
            long lower=Long.parseLong(historical.toVersion());
            FaultSyncFixture.destructive("SHORT_RETENTION","ALTER DATABASE ["+FaultDatabase.NAME+"] SET CHANGE_TRACKING(CHANGE_RETENTION=1 MINUTES,AUTO_CLEANUP=ON);");
            f.update(a,"new-A");f.update(b,"new-B");FaultSyncFixture.destructive("DELETE_SOURCE_C","DELETE dbo.DonVi WHERE ID="+removed+";");
            long start=System.nanoTime(),deadline=start+Duration.ofMinutes(5).toNanos();
            try{
                // Age real engine commits; do not reset CT, edit its tables or use a simulated version.
                while(Duration.ofNanos(System.nanoTime()-start).toSeconds()<70)Thread.sleep(5000);
                long minimum=0;
                while(System.nanoTime()<deadline){
                    FaultSyncFixture.destructive("RETENTION_CLEANUP","CHECKPOINT; EXEC sys.sp_flush_CT_internal_table_on_demand;");
                    minimum=f.minimum();
                    System.out.println("P01 retention actual L="+lower+", minValid="+minimum+", elapsedSeconds="+Duration.ofNanos(System.nanoTime()-start).toSeconds());
                    if(minimum>lower)break;Thread.sleep(5000);
                }
                assertThat(minimum).as("real retention must invalidate the captured engine checkpoint").isGreaterThan(lower);
                FaultSyncFixture.destructive("FREEZE_CLEANUP_BOUNDARY","ALTER DATABASE ["+FaultDatabase.NAME+"] SET CHANGE_TRACKING(AUTO_CLEANUP=OFF);");
                minimum=f.minimum();
                f.error("?since_version="+(minimum-1)+"&source_epoch="+f.epoch,f.key,409,"FULL_SYNC_REQUIRED");
                assertThat(f.ok("?since_version="+minimum+"&source_epoch="+f.epoch+"&limit=500").path("mode").asText()).isEqualTo("incremental");
                FaultSyncFixture.record("PASS:SA-030",Map.of("capturedL",lower,"minValid",minimum,"current",f.current(),"elapsedSeconds",Duration.ofNanos(System.nanoTime()-start).toSeconds()));
                for(String stage:List.of("betweenPages","final.afterReconcile","final.afterCheckpoint")){
                    f.start();var consumer=f.seedConsumer(historical);String scenario=stage.equals("betweenPages")?"SA-027":stage.equals("final.afterReconcile")?"SA-025":"SA-026";
                    try(var client=f.client(1)){
                        var guard=new UnitSyncGuard(FaultDatabase.dataSource(),FaultDatabase.NAME,FaultDatabase.INSTANCE);
                        var fault=new UnitSyncService(guard,f.repository,client,consumer.source(),1){
                            @Override protected void fault(String point,Connection c)throws SQLException{
                                if(stage.equals("betweenPages")&&point.equals("page.afterCommit"))try{f.stop();}catch(Exception e){throw new SQLException(e);}
                                if(point.equals(stage)){
                                    if(stage.equals("final.afterCheckpoint"))c.close();
                                    throw new SQLException("Owned fault barrier: "+stage);
                                }
                            }
                        };
                        assertThatThrownBy(fault::sync).isInstanceOf(IllegalStateException.class);
                        assertThat(f.checkpoint(consumer.source())).isEqualTo(consumer.checkpoint());
                        assertThat(f.active(consumer.source(),Long.toString(removed))).isTrue();
                        assertThat(f.raw(consumer.source(),Long.toString(b))).isEqualTo("old-B");
                        f.preserve(consumer);assertThat(client.fullCalls).isEqualTo(1);
                        FaultSyncFixture.record("PASS:"+scenario+":failure",Map.of("barrier",stage,"sourceId",consumer.source(),"checkpoint",consumer.checkpoint(),"removedStillActive",true,"fullCalls",client.fullCalls));
                    }
                    f.start();
                    try(var client=f.client(1)){
                        var result=f.service(client,consumer.source(),1).sync();
                        assertThat(result.checkpoint()).isEqualTo(f.epoch+":"+client.lastFull.toVersion());
                        assertThat(f.raw(consumer.source(),Long.toString(a))).isEqualTo("new-A");
                        assertThat(f.raw(consumer.source(),Long.toString(b))).isEqualTo("new-B");
                        assertThat(f.active(consumer.source(),Long.toString(removed))).isFalse();f.preserve(consumer);
                        FaultSyncFixture.record("PASS:"+scenario+":replay",Map.of("sourceId",consumer.source(),"oldCheckpoint",consumer.checkpoint(),"checkpoint",result.checkpoint(),"fullCalls",client.fullCalls,"minValid",minimum,"mapping",consumer.mapping()));
                    }
                    f.cleanupConsumer(consumer); // Only this owned fault fixture; keep audit/run history.
                }
                f.start();var consumer=f.seedConsumer(historical);
                try(var client=f.client(1)){
                    var result=f.service(client,consumer.source(),1).sync();
                    assertThat(f.active(consumer.source(),Long.toString(removed))).isFalse();f.preserve(consumer);
                    try(var c=FaultDatabase.open()){
                        assertThat(FaultSyncFixture.scalar(c,"SELECT COUNT(*) FROM dbo.dot_dong_bo WHERE he_thong_nguon_id="+consumer.source()+" AND trang_thai=N'success'")).isEqualTo(2);
                    }
                    FaultSyncFixture.record("PASS:SA-023,SA-024",Map.of("sourceId",consumer.source(),"oldL",lower,"minValid",minimum,"checkpoint",result.checkpoint(),"mapping",consumer.mapping(),"localOnly",consumer.local(),"childFK",consumer.child(),"history",consumer.audit()));
                }
            }finally{
                FaultSyncFixture.destructive("RESTORE_RETENTION_DEFAULT","ALTER DATABASE ["+FaultDatabase.NAME+"] SET CHANGE_TRACKING(CHANGE_RETENTION=90 DAYS,AUTO_CLEANUP=ON);");
            }
        }
    }
    @Test @Order(3) void sa032to035_realBackupRestoreEpochIdentityAndScopedConfirmation()throws Exception{
        try(var f=new FaultSyncFixture()){
            f.resetSource();long a=f.add("A","original-A");f.add("B","original-B");f.start();
            UnitExportResponse baseline;try(var client=f.client(500)){baseline=client.full();}
            var consumer=f.seedConsumer(baseline);long lower=Long.parseLong(baseline.toVersion());UUID e1=f.epoch;
            f.update(a,"changed-before-backup");f.update(a,"baseline-at-backup");
            String digest=f.projectionHash(consumer);String oldCursor=f.ok("?limit=1").path("next_cursor").asText();
            f.stop();var backup=FaultAdmin.backup();
            f.update(a,"discarded-after-backup");FaultAdmin.restore(backup);
            assertThat(f.projectionHash(consumer)).isEqualTo(digest);assertThat(f.checkpoint(consumer.source())).isEqualTo(consumer.checkpoint());
            assertThat(f.current()).as("restore test includes the case where version is not lower").isGreaterThanOrEqualTo(lower);
            UUID e2=UUID.randomUUID();f.epoch=e2;f.start();
            f.error("?cursor="+oldCursor,f.key,409,"EXPORT_EXPIRED");
            f.error("?since_version="+lower+"&source_epoch="+e1,f.key,409,"SOURCE_EPOCH_CHANGED");
            BaselineApproval stale;
            try(var client=f.client(500)){
                var service=f.service(client,consumer.source(),500);
                assertThatThrownBy(service::sync).isInstanceOf(SisUnitsClient.RemoteFailure.class);
                assertThat(client.fullCalls).isZero();assertThat(f.projectionHash(consumer)).isEqualTo(digest);
                var scope=service.preview();
                stale=new BaselineApproval(consumer.source(),scope.previousEpoch(),scope.sourceEpoch(),scope.sourceIdentities(),scope.targetMappings(),"fixture-reviewer","Reviewed original identities",Instant.now());
            }
            FaultSyncFixture.record("PASS:SA-035:restore",Map.of("backup",backup.path(),"capturedL",lower,"restoredCurrent",f.current(),"projectionHash",digest,"epochBefore",e1.toString(),"epochAfter",e2.toString()));
            f.stop();
            FaultSyncFixture.destructive("RESET_REUSE_SOURCE_PK","DELETE dbo.DonVi WHERE ID="+a+"; SET IDENTITY_INSERT dbo.DonVi ON; INSERT dbo.DonVi(ID,MaDV,TenDV,LuuTru) VALUES("+a+",N'"+f.prefix+"-REUSED',N'Different synthetic identity',N'reused'); SET IDENTITY_INSERT dbo.DonVi OFF;");
            UUID e3=UUID.randomUUID();f.epoch=e3;f.start();
            try(var client=f.client(500)){
                var service=f.service(client,consumer.source(),500);
                assertThatThrownBy(service::sync).isInstanceOf(SisUnitsClient.RemoteFailure.class);
                assertThatThrownBy(()->service.sync(stale)).isInstanceOf(IllegalStateException.class);
                assertThat(f.projectionHash(consumer)).isEqualTo(digest);assertThat(f.checkpoint(consumer.source())).isEqualTo(consumer.checkpoint());
                var scope=service.preview();
                var changedScope=new BaselineApproval(consumer.source(),scope.previousEpoch(),scope.sourceEpoch(),scope.sourceIdentities(),scope.targetMappings(),"fixture-reviewer","Stale scope must not apply",Instant.now());
                FaultSyncFixture.destructive("CHANGE_REVIEWED_SOURCE_CODE","UPDATE dbo.DonVi SET MaDV=MaDV+N'-R' WHERE ID<>"+a+";");
                assertThatThrownBy(()->service.sync(changedScope)).hasMessage("BASELINE_CONFIRMATION_SCOPE_CHANGED");
                assertThat(f.projectionHash(consumer)).isEqualTo(digest);
            }
            FaultSyncFixture.record("PASS:SA-032,SA-034",Map.of("sourceId",consumer.source(),"reusedSourceId",a,"checkpointUnchanged",consumer.checkpoint(),"projectionHash",digest,"epoch3",e3.toString()));
            // Restore the known original identities before positive approval; never approve an unrelated reused ID.
            f.stop();FaultAdmin.restore(backup);f.epoch=UUID.randomUUID();f.start();
            assertThat(f.projectionHash(consumer)).isEqualTo(digest);
            try(var client=f.client(500)){
                var service=f.service(client,consumer.source(),500);var scope=service.preview();
                assertThat(scope.sourceIdentities().get(Long.toString(a))).isEqualTo(f.prefix+"-A");
                var approval=new BaselineApproval(consumer.source(),scope.previousEpoch(),scope.sourceEpoch(),scope.sourceIdentities(),scope.targetMappings(),"fixture-reviewer","Backup identities and local mappings checked",Instant.now());
                var result=service.sync(approval);assertThat(result.checkpoint()).isEqualTo(f.epoch+":"+client.lastFull.toVersion());
                f.preserve(consumer);
                try(var c=FaultDatabase.open()){
                    assertThat(FaultSyncFixture.scalar(c,"SELECT COUNT(*) FROM dbo.nhat_ky_thao_tac WHERE he_thong_ngoai_id="+consumer.source()+" AND hanh_dong=N'CONFIRM_BASELINE'")).isEqualTo(1);
                }
                FaultSyncFixture.record("PASS:SA-033",Map.of("sourceId",consumer.source(),"checkpoint",result.checkpoint(),"previousEpoch",e1.toString(),"approvedEpoch",f.epoch.toString(),"mapping",consumer.mapping(),"approvedBy",approval.approvedBy(),"reason",approval.reason(),"approvedAt",approval.approvedAt().toString()));
            }
        }
    }
}
