package pbl6.ctsv.foundation.units;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.*;
import pbl6.ctsv.foundation.units.client.SisUnitsClient;
import pbl6.ctsv.foundation.units.dto.*;
import pbl6.ctsv.foundation.units.mapper.UnitSourceMapper;
import pbl6.ctsv.foundation.units.repository.UnitSyncRepository;
import pbl6.ctsv.foundation.units.service.*;
import pbl6.p00.SliceATestDatabaseSupport;
import static org.assertj.core.api.Assertions.*;

/** SQL_SERVER_REAL, EXTERNAL_SIS=STUB. Live two-backend acceptance is separate. */
class UnitSyncRecoveryIT {
    static final String EPOCH="11111111-2222-3333-4444-555555555555";
    long source;String prefix="P01-SYNC-"+UUID.randomUUID();
    UnitSyncRepository repository=new UnitSyncRepository(new UnitSourceMapper());
    UnitSyncGuard guard;
    StubClient client;
    static class StubClient extends SisUnitsClient {
        UnitExportResponse first,second,delta;
        RuntimeException incrementalFailure;
        RuntimeException nextFailure;
        int fullCalls;
        StubClient(){super(URI.create("http://127.0.0.1:9"),"fixture-key",1);}
        @Override public UnitExportResponse full(){fullCalls++;return first;}
        @Override public UnitExportResponse incremental(UUID epoch,long lower){
            if(incrementalFailure!=null)throw incrementalFailure;
            assertThat(epoch.toString()).isEqualTo(EPOCH);assertThat(lower).isEqualTo(123);return delta;
        }
        @Override public UnitExportResponse next(String cursor){assertThat(cursor).isEqualTo("p2");if(nextFailure!=null)throw nextFailure;return second;}
    }
    UnitChangeDto item(String id,String raw){return new UnitChangeDto(id,"upsert",null,
        new UnitDataDto(prefix+"-"+id,"Synthetic",null,"2026-11-11T00:00:00Z",true,raw,"raw-class"));}
    UnitExportResponse page(String mode,String lower,String upper,List<UnitChangeDto> rows,String cursor) {
        return new UnitExportResponse(mode,EPOCH,"22222222-2222-3333-4444-555555555555",lower,upper,"2026-11-11T00:00:00Z",rows,cursor);
    }
    @BeforeAll static void migrate()throws Exception{SliceATestDatabaseSupport.migrate();}
    @BeforeEach void seed()throws Exception {
        try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement("INSERT dbo.he_thong_ngoai(ma,ten) VALUES(?,N'Synthetic P01'); SELECT CAST(SCOPE_IDENTITY() AS bigint)")){
            s.setString(1,prefix);try(var r=s.executeQuery()){r.next();source=r.getLong(1);}
        }
        guard=new UnitSyncGuard(SliceATestDatabaseSupport.dataSource(),"ctsv_test_20261111","DEV2022");
        client=new StubClient();client.first=page("full",null,"123",List.of(item("1","old")),"p2");
        client.second=page("full",null,"123",List.of(item("2","old")),null);
    }
    @AfterEach void cleanup()throws Exception {
        client.close();
        try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement("DELETE dbo.don_vi WHERE he_thong_nguon_id=?")){s.setLong(1,source);s.executeUpdate();}
    }
    UnitSyncService service(){return new UnitSyncService(guard,repository,client,source,1);}
    @Test void sa002_011_013_018_fullThenDeltaCheckpointAndReplayAreAtomic()throws Exception {
        assertThatCode(()->{
            var result=service().sync();assertThat(result.checkpoint()).isEqualTo(EPOCH+":123");assertThat(result.processed()).isEqualTo(2);
            try(var c=SliceATestDatabaseSupport.open()){
                assertThat(repository.checkpoint(c,source)).isEqualTo(EPOCH+":123");
                assertThat(repository.mappings(c,source)).hasSize(2);
                assertThat(repository.mappings(c,source).get("1")).isNotEqualTo(1L);
            }
            var updated=new UnitChangeDto("1","upsert","125",new UnitDataDto(prefix+"-1","Synthetic",null,"2026-11-11T01:00:00Z",false,"new","raw-class"));
            client.delta=page("incremental","123","125",List.of(updated),null);
            result=service().sync();assertThat(result.checkpoint()).isEqualTo(EPOCH+":125");
            try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.mappings(c,source)).hasSize(2);}
        }).doesNotThrowAnyException();
    }
    String raw(String id)throws Exception {
        try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement("SELECT luu_tru_nguon FROM dbo.don_vi WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai=?")){
            s.setLong(1,source);s.setString(2,id);try(var r=s.executeQuery()){r.next();return r.getString(1);}
        }
    }
    boolean active(String id)throws Exception {
        try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement("SELECT dang_hoat_dong_nguon FROM dbo.don_vi WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai=?")){
            s.setLong(1,source);s.setString(2,id);try(var r=s.executeQuery()){r.next();return r.getBoolean(1);}
        }
    }
    UnitChangeDto deltaItem(String id,String raw,String version){var r=item(id,raw);return new UnitChangeDto(r.sourceId(),r.operation(),version,r.data());}
    @Test void sa015_017_partialPageCommitReplaysFromLastSuccessfulCheckpoint()throws Exception {
        service().sync();client.delta=page("incremental","123","125",List.of(deltaItem("1","new","124")),"p2");
        client.second=page("incremental","123","125",List.of(deltaItem("2","new","125")),null);
        client.nextFailure=new SisUnitsClient.RemoteFailure(503,"SOURCE_UNAVAILABLE");
        assertThatThrownBy(()->service().sync()).isInstanceOf(SisUnitsClient.RemoteFailure.class);
        assertThat(raw("1")).isEqualTo("new");assertThat(raw("2")).isEqualTo("old");
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.checkpoint(c,source)).isEqualTo(EPOCH+":123");}
        client.nextFailure=null;assertThat(service().sync().checkpoint()).isEqualTo(EPOCH+":125");
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.mappings(c,source)).hasSize(2);}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"final.afterReconcile","final.afterCheckpoint"})
    void sa025_026_sae06_finalTransactionRollsBackRawReconciliationAndCheckpoint(String stage)throws Exception {
        service().sync();client.incrementalFailure=new SisUnitsClient.RemoteFailure(409,"FULL_SYNC_REQUIRED");
        client.first=page("full",null,"200",List.of(item("1","new")),null);
        var faulty=new UnitSyncService(guard,repository,client,source,1){
            @Override protected void fault(String point,java.sql.Connection c)throws java.sql.SQLException {
                if(point.equals(stage))throw new java.sql.SQLException("Injected P01 finalization fault");
            }
        };
        assertThatThrownBy(faulty::sync).isInstanceOf(IllegalStateException.class);
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.checkpoint(c,source)).isEqualTo(EPOCH+":123");}
        assertThat(raw("1")).isEqualTo("old");assertThat(active("2")).isTrue();
    }
    @Test void sa023_024_sameEpochFullRecoveryMarksOnlyMissingMappedRows()throws Exception {
        service().sync();client.incrementalFailure=new SisUnitsClient.RemoteFailure(409,"FULL_SYNC_REQUIRED");
        client.first=page("full",null,"200",List.of(item("1","new")),null);
        assertThat(service().sync().checkpoint()).isEqualTo(EPOCH+":200");
        assertThat(active("2")).isFalse();assertThat(raw("1")).isEqualTo("new");
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.mappings(c,source)).hasSize(2);}
    }
    @Test void sa032_epochErrorNeverAutomaticallyFallsBackToFull()throws Exception {
        service().sync();int previousCalls=client.fullCalls;
        client.incrementalFailure=new SisUnitsClient.RemoteFailure(409,"SOURCE_EPOCH_CHANGED");
        assertThatThrownBy(()->service().sync()).isInstanceOf(SisUnitsClient.RemoteFailure.class);
        assertThat(client.fullCalls).isEqualTo(previousCalls);
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.checkpoint(c,source)).isEqualTo(EPOCH+":123");}
    }
    @Test void sa036_existingMappingsWithoutCheckpointRequireConfirmationBeforeAnyUpsert()throws Exception {
        try(var c=SliceATestDatabaseSupport.open()){c.setAutoCommit(false);repository.upsertPage(c,source,List.of(item("1","old")));c.commit();}
        client.first=page("full",null,"200",List.of(item("1","new")),"p2");
        assertThatThrownBy(()->service().sync()).hasMessage("BASELINE_CONFIRMATION_REQUIRED");
        assertThat(raw("1")).isEqualTo("old");
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.checkpoint(c,source)).isNull();assertThat(repository.mappings(c,source)).hasSize(1);}
    }
    @Test void sa010_noTransactionAcrossAnyHttpBoundary()throws Exception {
        var checked=new UnitSyncService(guard,repository,client,source,1){
            @Override protected void fault(String point,java.sql.Connection c)throws java.sql.SQLException {
                if(point.equals("http.beforeRequest")){
                    assertThat(c.getAutoCommit()).isTrue();assertThat(SliceATestDatabaseSupport.scalar(c,"SELECT @@TRANCOUNT")).isZero();
                }
            }
        };
        assertThat(checked.sync().processed()).isEqualTo(2);
    }
    @Test void sa033_confirmedEpochAndExactIdentityScopeAllowsControlledFullRecovery()throws Exception {
        service().sync();String nextEpoch="aaaaaaaa-2222-3333-4444-555555555555";
        client.first=new UnitExportResponse("full",nextEpoch,"33333333-2222-3333-4444-555555555555",null,"200","2026-11-11T00:00:00Z",List.of(item("1","new")),"p2");
        client.second=new UnitExportResponse("full",nextEpoch,"33333333-2222-3333-4444-555555555555",null,"200","2026-11-11T00:00:00Z",List.of(item("2","new")),null);
        Map<String,Long> identities;try(var c=SliceATestDatabaseSupport.open()){identities=repository.mappings(c,source);}
        var approval=new BaselineApproval(source,UUID.fromString(EPOCH),UUID.fromString(nextEpoch),
                Map.of("1",prefix+"-1","2",prefix+"-2"),identities,"fixture-operator","Confirmed same unit identities",java.time.Instant.now());
        assertThatCode(()->{
            var result=service().sync(approval);assertThat(result.checkpoint()).isEqualTo(nextEpoch+":200");
            try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement("SELECT COUNT(*) FROM dbo.nhat_ky_thao_tac WHERE he_thong_ngoai_id=? AND hanh_dong=N'CONFIRM_BASELINE'")){
                s.setLong(1,source);try(var r=s.executeQuery()){r.next();assertThat(r.getInt(1)).isEqualTo(1);}
            }
            assertThat(raw("1")).isEqualTo("new");
        }).doesNotThrowAnyException();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"identity","previousEpoch","newEpoch","targetMapping"})
    void sa034_staleScopedConfirmationNeverWritesProjection(String mismatch)throws Exception {
        service().sync();Map<String,Long> mapping;
        try(var c=SliceATestDatabaseSupport.open()){mapping=repository.mappings(c,source);}
        UUID old=UUID.fromString(EPOCH),next=UUID.fromString("aaaaaaaa-2222-3333-4444-555555555555");
        var identities=new HashMap<>(Map.of("1",prefix+"-1","2",prefix+"-2"));
        if(mismatch.equals("identity"))identities.put("1","incorrect-code");
        if(mismatch.equals("targetMapping")){mapping=new HashMap<>(mapping);mapping.put("1",Long.MAX_VALUE);}
        var approval=new BaselineApproval(source,mismatch.equals("previousEpoch")?next:old,
            mismatch.equals("newEpoch")?next:old,identities,mapping,"fixture-reviewer","Synthetic stale scope",java.time.Instant.now());
        assertThatThrownBy(()->service().sync(approval)).isInstanceOf(IllegalStateException.class);
        assertThat(raw("1")).isEqualTo("old");
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.checkpoint(c,source)).isEqualTo(EPOCH+":123");}
    }
    @Test void sa036_confirmedMappedBaselineWithoutCheckpointCanRecover()throws Exception {
        try(var c=SliceATestDatabaseSupport.open()){c.setAutoCommit(false);repository.upsertPage(c,source,List.of(item("1","old")));c.commit();}
        var scope=service().preview();
        var approval=new BaselineApproval(source,null,scope.sourceEpoch(),scope.sourceIdentities(),scope.targetMappings(),
            "fixture-reviewer","Existing identity mapping independently checked",java.time.Instant.now());
        assertThat(service().sync(approval).checkpoint()).isEqualTo(EPOCH+":123");
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.mappings(c,source)).hasSize(2);}
    }
    @Test void sa019_twoSimultaneousTriggersShareOneSqlApplicationLock()throws Exception {
        var reached=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var slow=new UnitSyncService(guard,repository,client,source,1){
            @Override protected void fault(String point,java.sql.Connection c)throws java.sql.SQLException {
                if(point.equals("http.beforeRequest"))try{
                    reached.countDown();if(!release.await(15,java.util.concurrent.TimeUnit.SECONDS))throw new java.sql.SQLException("Test barrier timeout");
                }catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.sql.SQLException(e);}
            }
        };
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()){
            var first=executor.submit(()->slow.sync());
            try{
                assertThat(reached.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(()->service().sync()).isInstanceOf(UnitSyncGuard.BusyException.class);
            }finally{release.countDown();}
            assertThat(first.get(15,java.util.concurrent.TimeUnit.SECONDS).checkpoint()).isEqualTo(EPOCH+":123");
        }
    }
    @Test void sa017_026_physicalConnectionLossBeforeFinalCommitRollsBackAndNextWorkerReplays()throws Exception {
        service().sync();client.delta=page("incremental","123","125",List.of(deltaItem("1","new","125")),null);
        var crash=new UnitSyncService(guard,repository,client,source,1){
            @Override protected void fault(String point,java.sql.Connection c)throws java.sql.SQLException {
                if(point.equals("final.afterCheckpoint")){c.close();throw new java.sql.SQLException("Simulated worker connection loss");}
            }
        };
        assertThatThrownBy(crash::sync).isInstanceOf(IllegalStateException.class);
        assertThat(raw("1")).isEqualTo("old");
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.checkpoint(c,source)).isEqualTo(EPOCH+":123");}
        assertThat(service().sync().checkpoint()).isEqualTo(EPOCH+":125");
        try(var c=SliceATestDatabaseSupport.open();var s=c.prepareStatement("SELECT COUNT(*) FROM dbo.dot_dong_bo WHERE he_thong_nguon_id=? AND trang_thai=N'interrupted'")){
            s.setLong(1,source);try(var r=s.executeQuery()){r.next();assertThat(r.getInt(1)).isEqualTo(1);}
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"metadata","duplicate","outOfRange","exportId","epoch","mode","lower","asOf","pageSize","cursorLoop"})
    void sa042_044_045_invalidNextPageDoesNotAdvanceCheckpoint(String invalid)throws Exception {
        service().sync();client.delta=page("incremental","123","125",List.of(deltaItem("1","new","124")),"p2");
        client.second=invalid.equals("metadata")?page("incremental","123","126",List.of(deltaItem("2","new","125")),null)
            :page("incremental","123","125",List.of(deltaItem(invalid.equals("duplicate")?"1":"2","new",invalid.equals("outOfRange")?"126":"125")),null);
        var rows=List.of(deltaItem("2","new","125"));
        switch(invalid){
            case "exportId"->client.second=new UnitExportResponse("incremental",EPOCH,UUID.randomUUID().toString(),"123","125","2026-11-11T00:00:00Z",rows,null);
            case "epoch"->client.second=new UnitExportResponse("incremental",UUID.randomUUID().toString(),client.second.exportId(),"123","125",client.second.asOf(),rows,null);
            case "mode"->client.second=page("full",null,"125",List.of(item("2","new")),null);
            case "lower"->client.second=page("incremental","122","125",rows,null);
            case "asOf"->client.second=new UnitExportResponse("incremental",EPOCH,client.second.exportId(),"123","125","2026-11-11T00:01:00Z",rows,null);
            case "pageSize"->client.second=page("incremental","123","125",List.of(deltaItem("2","new","125"),deltaItem("3","new","125")),null);
            case "cursorLoop"->client.second=page("incremental","123","125",rows,"p2");
        }
        assertThatThrownBy(()->service().sync()).isInstanceOf(IllegalArgumentException.class);
        assertThat(raw("2")).isEqualTo(invalid.equals("cursorLoop")?"new":"old"); // loop's prior page may commit, never the checkpoint
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.checkpoint(c,source)).isEqualTo(EPOCH+":123");}
    }
    @Test void sa018_checkpointIgnoresNewerFailuresAndDifferentResource()throws Exception {
        service().sync();
        try(var c=SliceATestDatabaseSupport.open()){
            long failed=repository.startRun(c,source,"incremental",EPOCH+":123");repository.failRun(c,failed,"Synthetic failure");
            long other=repository.startRun(c,source,"full",null);
            try(var s=c.prepareStatement("UPDATE dbo.dot_dong_bo SET loai_doi_tuong=N'other-test-resource',trang_thai=N'success',con_tro_sau=?,thoi_diem_ket_thuc=SYSDATETIMEOFFSET() WHERE id=?")){
                s.setString(1,EPOCH+":9999");s.setLong(2,other);s.executeUpdate();
            }
            assertThat(repository.checkpoint(c,source)).isEqualTo(EPOCH+":123");
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={2500,10001})
    void sa028_057_fullSeenIdsUseBatchesAndRefuseOverflowWithoutCheckpoint(int count)throws Exception {
        service().sync();
        var generated=new StubClient(){
            UnitExportResponse generatedPage(int start){
                var rows=new ArrayList<UnitChangeDto>();int end=Math.min(start+500,count+1);
                for(int id=start;id<end;id++)rows.add(item(Integer.toString(id),"generated"));
                return page("full",null,"200",rows,end<=count?Integer.toString(end):null);
            }
            @Override public UnitExportResponse full(){return generatedPage(1);}
            @Override public UnitExportResponse incremental(UUID e,long l){throw new SisUnitsClient.RemoteFailure(409,"FULL_SYNC_REQUIRED");}
            @Override public UnitExportResponse next(String cursor){return generatedPage(Integer.parseInt(cursor));}
        };
        try(generated){
            var sync=new UnitSyncService(guard,repository,generated,source,500);
            if(count<=10000){assertThat(sync.sync().processed()).isEqualTo(count);}
            else {assertThatThrownBy(sync::sync).isInstanceOf(IllegalArgumentException.class);}
            try(var c=SliceATestDatabaseSupport.open()){
                assertThat(repository.checkpoint(c,source)).isEqualTo(EPOCH+(count<=10000?":200":":123"));
                assertThat(repository.mappings(c,source)).hasSize(Math.min(count,10000));
            }
        }
    }
    @Test void sa003_emptyFullAndEmptySameEpochRecoveryStillCommitValidCheckpoint()throws Exception {
        client.first=page("full",null,"123",List.of(),null);
        assertThat(service().sync().processed()).isZero();
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.checkpoint(c,source)).isEqualTo(EPOCH+":123");}
    }
    @Test void sa035_fullRecoveryMustNotAcceptLowerVersionInSameEpoch()throws Exception {
        service().sync();client.incrementalFailure=new SisUnitsClient.RemoteFailure(409,"FULL_SYNC_REQUIRED");
        client.first=page("full",null,"100",List.of(item("1","unsafe-lower-version")),null);
        assertThatThrownBy(()->service().sync()).isInstanceOf(IllegalStateException.class).hasMessage("SOURCE_LINEAGE_INVALID");
        assertThat(raw("1")).isEqualTo("old");
        try(var c=SliceATestDatabaseSupport.open()){assertThat(repository.checkpoint(c,source)).isEqualTo(EPOCH+":123");}
    }
}

