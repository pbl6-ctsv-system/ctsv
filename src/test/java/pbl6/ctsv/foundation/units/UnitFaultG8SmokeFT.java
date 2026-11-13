package pbl6.ctsv.foundation.units;

import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import pbl6.p01.FaultDatabase;
import static org.assertj.core.api.Assertions.*;

/** Easy independent flow; PASS here is technical execution, never automatic G8 acceptance. */
class UnitFaultG8SmokeFT {
    static Connection suiteLock;
    @BeforeAll static void setup()throws Exception{suiteLock=FaultAdmin.acquireSuite();FaultSyncFixture.projection();}
    @AfterAll static void unlock()throws Exception{if(suiteLock!=null)suiteLock.close();}
    @Test void fullUpdateInsertDeltaReplayDeletePreservesIdentityLocalColumnsAndHistory()throws Exception{
        try(var f=new FaultSyncFixture()){
            f.resetSource();long a=f.add("G8-A","old"),b=f.add("G8-B",null);f.start();long source=f.emptyConsumer();
            long local;
            try(var c=FaultDatabase.open();var s=c.prepareStatement("INSERT dbo.don_vi(ma,dang_hoat_dong) VALUES(?,1); SELECT CAST(SCOPE_IDENTITY() AS bigint)")){
                s.setString(1,f.prefix+"-LOCAL");try(var r=s.executeQuery()){r.next();local=r.getLong(1);}
                if(local+1==a){s.setString(1,f.prefix+"-PADDING");s.executeQuery().close();}
            }
            {
                f.runConsumerBackend(source);String full=f.checkpoint(source);
                Map<String,Long> mapping;try(var c=FaultDatabase.open()){mapping=f.repository.mappings(c,source);}
                long localA=mapping.get(Long.toString(a)),localB=mapping.get(Long.toString(b));
                assertThat(localA).isNotEqualTo(a);assertThat(mapping).hasSize(2);
                long child,audit;
                try(var c=FaultDatabase.open()){
                    c.setAutoCommit(false);
                    try(var s=c.prepareStatement("UPDATE dbo.don_vi SET loai_don_vi=N'LOCAL',don_vi_cha_id=?,dang_hoat_dong=1 WHERE id=?")){s.setLong(1,local);s.setLong(2,localA);s.executeUpdate();}
                    try(var s=c.prepareStatement("INSERT dbo.don_vi(ma,don_vi_cha_id,dang_hoat_dong) VALUES(?,?,1); SELECT CAST(SCOPE_IDENTITY() AS bigint)")){
                        s.setString(1,f.prefix+"-CHILD");s.setLong(2,localB);try(var r=s.executeQuery()){r.next();child=r.getLong(1);}
                    }
                    audit=new pbl6.ctsv.shared.audit.AuditWriter().append(c,source,"G8_FIXTURE_HISTORY","don_vi",Long.toString(localB),"Synthetic independent review fixture",Map.of("fixture",f.prefix));
                    c.commit();
                }
                f.update(a,"new-raw");long added=f.add("G8-C","inserted");
                f.runConsumerBackend(source);String delta=f.checkpoint(source);assertThat(f.raw(source,Long.toString(a))).isEqualTo("new-raw");
                f.runConsumerBackend(source);
                assertThat(FaultDatabase.number("SELECT TOP(1) tong_so_ban_ghi FROM dbo.dot_dong_bo WHERE he_thong_nguon_id="+source+" AND trang_thai=N'success' ORDER BY id DESC")).isZero();
                FaultSyncFixture.destructive("G8_DELETE_SOURCE_B","DELETE dbo.DonVi WHERE ID="+b);
                f.runConsumerBackend(source);String deleted=f.checkpoint(source);
                assertThat(FaultDatabase.number("SELECT TOP(1) tong_so_ban_ghi FROM dbo.dot_dong_bo WHERE he_thong_nguon_id="+source+" AND trang_thai=N'success' ORDER BY id DESC")).isEqualTo(1);
                assertThat(f.active(source,Long.toString(b))).isFalse();
                try(var c=FaultDatabase.open()){
                    var after=f.repository.mappings(c,source);assertThat(after.get(Long.toString(a))).isEqualTo(localA);assertThat(after.get(Long.toString(b))).isEqualTo(localB);
                    assertThat(after).containsKey(Long.toString(added)).hasSize(3);
                    assertThat(FaultSyncFixture.scalar(c,"SELECT COUNT(*) FROM dbo.don_vi WHERE id="+localA+" AND don_vi_cha_id="+local+" AND loai_don_vi=N'LOCAL' AND dang_hoat_dong=1")).isEqualTo(1);
                    assertThat(FaultSyncFixture.scalar(c,"SELECT COUNT(*) FROM dbo.don_vi WHERE id="+child+" AND don_vi_cha_id="+localB)).isEqualTo(1);
                    assertThat(FaultSyncFixture.scalar(c,"SELECT COUNT(*) FROM dbo.nhat_ky_thao_tac WHERE id="+audit)).isEqualTo(1);
                }
                FaultSyncFixture.record("G8_FLOW_EXECUTED",Map.of("sourceId",source,"sourceA",a,"sourceB",b,"localA",localA,"localB",localB,
                    "fullCheckpoint",full,"deltaCheckpoint",delta,"deleteCheckpoint",deleted,"replayCount",0,"independentAcceptance","NOT_AUTOMATIC"));
            }
        }
    }
}
