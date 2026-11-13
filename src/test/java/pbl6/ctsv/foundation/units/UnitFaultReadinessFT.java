package pbl6.ctsv.foundation.units;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pbl6.p01.FaultDatabase;
import static org.assertj.core.api.Assertions.*;

class UnitFaultReadinessFT {
    static java.sql.Connection suiteLock;
    @BeforeAll static void setup()throws Exception{suiteLock=FaultAdmin.acquireSuite();FaultSyncFixture.projection();}
    @AfterAll static void unlock()throws Exception{if(suiteLock!=null)suiteLock.close();}
    @ParameterizedTest
    @ValueSource(strings={"snapshot","tableCT","databaseCT","timestampColumn","activeColumn","selectPermission","viewCTPermission"})
    void sa029_eachRealReadinessFaultReturns503BeforeAnyConsumerCommit(String variant)throws Exception{
        try(var f=new FaultSyncFixture()){
            f.resetSource();f.add("READINESS","raw");f.start();
            String restore=null;boolean lineageReset=false;
            FaultSyncFixture.Consumer existing=null;String request="";
            try{
                switch(variant){
                    case "snapshot"->{
                        FaultSyncFixture.destructive("SNAPSHOT_OFF","ALTER DATABASE ["+FaultDatabase.NAME+"] SET ALLOW_SNAPSHOT_ISOLATION OFF;");
                        restore="ALTER DATABASE ["+FaultDatabase.NAME+"] SET ALLOW_SNAPSHOT_ISOLATION ON;";
                    }
                    case "tableCT"->{
                        FaultSyncFixture.destructive("TABLE_CT_OFF","ALTER TABLE dbo.DonVi DISABLE CHANGE_TRACKING;");
                        restore="ALTER TABLE dbo.DonVi ENABLE CHANGE_TRACKING WITH(TRACK_COLUMNS_UPDATED=OFF);";lineageReset=true;
                    }
                    case "databaseCT"->{
                        FaultSyncFixture.destructive("DATABASE_CT_OFF","ALTER TABLE dbo.DonVi DISABLE CHANGE_TRACKING; ALTER DATABASE ["+FaultDatabase.NAME+"] SET CHANGE_TRACKING=OFF;");
                        restore="ALTER DATABASE ["+FaultDatabase.NAME+"] SET CHANGE_TRACKING=ON(CHANGE_RETENTION=90 DAYS,AUTO_CLEANUP=ON); ALTER TABLE dbo.DonVi ENABLE CHANGE_TRACKING WITH(TRACK_COLUMNS_UPDATED=OFF);";lineageReset=true;
                    }
                    case "timestampColumn"->{
                        FaultSyncFixture.destructive("HIDE_TIMESTAMP_COLUMN","EXEC sys.sp_rename N'dbo.DonVi.thoi_diem_cap_nhat',N'p01_saved_timestamp',N'COLUMN';");
                        restore="EXEC sys.sp_rename N'dbo.DonVi.p01_saved_timestamp',N'thoi_diem_cap_nhat',N'COLUMN';";
                    }
                    case "activeColumn"->{
                        FaultSyncFixture.destructive("HIDE_ACTIVE_COLUMN","EXEC sys.sp_rename N'dbo.DonVi.dang_hoat_dong',N'p01_saved_active',N'COLUMN';");
                        restore="EXEC sys.sp_rename N'dbo.DonVi.p01_saved_active',N'dang_hoat_dong',N'COLUMN';";
                    }
                    case "selectPermission"->{
                        FaultSyncFixture.destructive("CREATE_NO_SELECT_PROBE","IF DATABASE_PRINCIPAL_ID(N'p01_fault_no_select') IS NULL CREATE USER [p01_fault_no_select] WITHOUT LOGIN; GRANT CONNECT TO [p01_fault_no_select]; GRANT VIEW CHANGE TRACKING ON OBJECT::dbo.DonVi TO [p01_fault_no_select]; DENY SELECT ON OBJECT::dbo.DonVi TO [p01_fault_no_select];");
                        f.start("p01_fault_no_select");
                    }
                    case "viewCTPermission"->{
                        // Full SELECT does not need VIEW CHANGE TRACKING. Seed a real full checkpoint,
                        // then exercise CHANGETABLE through the incremental request that needs that grant.
                        try(var ownerClient=f.client(500)){existing=f.seedConsumer(ownerClient.full());}
                        request="?since_version="+existing.checkpoint().split(":")[1]+"&source_epoch="+f.epoch;
                        FaultSyncFixture.destructive("CREATE_NO_CT_PERMISSION_PROBE","IF DATABASE_PRINCIPAL_ID(N'p01_fault_no_ct') IS NULL CREATE USER [p01_fault_no_ct] WITHOUT LOGIN; GRANT CONNECT TO [p01_fault_no_ct]; GRANT SELECT ON OBJECT::dbo.DonVi TO [p01_fault_no_ct]; DENY VIEW CHANGE TRACKING ON OBJECT::dbo.DonVi TO [p01_fault_no_ct];");
                        f.start("p01_fault_no_ct");
                    }
                }
                f.error(request,f.key,503,"SYNC_NOT_READY");
                long source=existing==null?f.emptyConsumer():existing.source();
                String initial=existing==null?null:existing.checkpoint();
                String initialHash=existing==null?null:f.projectionHash(existing);
                try(var client=f.client(500)){
                    assertThatThrownBy(()->f.service(client,source,500).sync()).isInstanceOfSatisfying(
                        pbl6.ctsv.foundation.units.client.SisUnitsClient.RemoteFailure.class,e->assertThat(e.code()).isEqualTo("SYNC_NOT_READY"));
                    assertThat(f.checkpoint(source)).isEqualTo(initial);
                    if(existing==null)assertThat(FaultDatabase.number("SELECT COUNT(*) FROM dbo.don_vi WHERE he_thong_nguon_id="+source)).isZero();
                    else assertThat(f.projectionHash(existing)).isEqualTo(initialHash);
                }
                FaultSyncFixture.record("PASS:SA-029:"+variant,Map.of("status",503,"checkpoint",initial==null?"NONE":initial,"checkpointUnchanged",true,"projectionWrites",0));
            }finally{
                f.stop();
                if(restore!=null)FaultSyncFixture.destructive("RESTORE_READINESS:"+variant,restore);
                if(lineageReset)f.epoch=UUID.randomUUID();
            }
            f.start();assertThat(f.ok("?limit=500").path("items").size()).isEqualTo(1);
        }
    }
    @Test void sa049_sourceUnavailableIsARealOfflineDatabaseNotAStub()throws Exception{
        try(var f=new FaultSyncFixture()){
            f.resetSource();f.add("OFFLINE","raw");f.start();f.ok("");
            FaultAdmin.offline(()->f.error("",f.key,503,"SOURCE_UNAVAILABLE"));
            f.start();assertThat(f.ok("").path("items").size()).isEqualTo(1);
            FaultSyncFixture.record("PASS:SA-049:SOURCE_UNAVAILABLE",Map.of("fault","REAL_DATABASE_OFFLINE","onlineRestored",true));
        }
    }
    @Test void sa049_remainingHttpCodesUseTheRealProviderAndActualLimits()throws Exception{
        try(var f=new FaultSyncFixture()){
            f.resetSource();f.add("A","raw");f.add("B","raw");f.start();
            f.error("?limit=0",f.key,400,"INVALID_REQUEST");
            f.error("",null,401,"INVALID_SERVICE_KEY");f.error("","invalid-fixture-key",401,"INVALID_SERVICE_KEY");
            f.error("",f.deniedKey,403,"SERVICE_FORBIDDEN");
            f.error("?since_version=0&source_epoch="+UUID.randomUUID(),f.key,409,"SOURCE_EPOCH_CHANGED");
            f.error("?since_version=9223372036854775807&source_epoch="+f.epoch,f.key,409,"SOURCE_LINEAGE_INVALID");
            String cursor=f.ok("?limit=1").path("next_cursor").asText();f.start();
            f.error("?cursor="+cursor,f.key,409,"EXPORT_EXPIRED");
            f.resetSource();
            try(var c=FaultDatabase.open();var s=c.prepareStatement("INSERT dbo.DonVi(MaDV,TenDV) VALUES(?,N'Synthetic error quota')")){
                c.setAutoCommit(false);
                for(int i=0;i<10001;i++){s.setString(1,f.prefix+"-"+i);s.addBatch();if(i%250==249)s.executeBatch();}
                s.executeBatch();c.commit();
            }
            f.error("?limit=500",f.key,503,"EXPORT_LIMIT_EXCEEDED");
            f.resetSource();f.add("CAP-A",null);f.add("CAP-B",null);f.start();
            f.ok("?limit=1");f.ok("?limit=1");f.error("?limit=1",f.key,503,"EXPORT_CAPACITY_EXCEEDED");
            FaultSyncFixture.record("PASS:SA-049:code-matrix",Map.of("allExpectedCodes",List.of("INVALID_REQUEST","INVALID_SERVICE_KEY","SERVICE_FORBIDDEN","EXPORT_EXPIRED","SOURCE_EPOCH_CHANGED","SOURCE_LINEAGE_INVALID","EXPORT_LIMIT_EXCEEDED","EXPORT_CAPACITY_EXCEEDED"),
                "otherCases","FULL_SYNC_REQUIRED retention; SYNC_NOT_READY readiness; SOURCE_UNAVAILABLE offline"));
        }
    }
}
