package pbl6.ctsv.foundation.sync;
import java.sql.*;import java.util.*;import java.time.*;import javax.sql.DataSource;
import org.junit.jupiter.api.*;import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.node.*;
import static org.assertj.core.api.Assertions.*;

/** REAL SQL Server; source envelopes are SYNTHETIC FIXTURES, not provider HTTP/CT proof. */
class DurableStoreIT {
 static DataSource ds;static final ObjectMapper json=new ObjectMapper();
 static final UUID EPOCH=UUID.fromString("11111111-aaaa-4444-bbbb-111111111111");
 @BeforeAll static void setup()throws Exception{
  P02Database.migrate();ds=P02Database.source();try(var c=ds.getConnection();var s=c.createStatement()){
   s.execute("IF OBJECT_ID('dbo.p02_fixture_projection') IS NULL CREATE TABLE dbo.p02_fixture_projection(id bigint IDENTITY PRIMARY KEY,source_id bigint NULL,external_id bigint NULL,code nvarchar(128) NOT NULL,name nvarchar(512) NULL,present bit NOT NULL DEFAULT 1,local_note nvarchar(128) NULL,parent_id bigint NULL REFERENCES dbo.p02_fixture_projection(id),UNIQUE(source_id,code))");
   s.execute("DECLARE @name sysname=(SELECT TOP(1) i.name FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id JOIN sys.columns x ON x.object_id=ic.object_id AND x.column_id=ic.column_id WHERE i.object_id=OBJECT_ID('dbo.p02_fixture_projection') AND i.is_unique_constraint=1 AND x.name=N'external_id');IF @name IS NOT NULL BEGIN DECLARE @drop nvarchar(max)=N'ALTER TABLE dbo.p02_fixture_projection DROP CONSTRAINT '+QUOTENAME(@name);EXEC sys.sp_executesql @drop;END;IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.p02_fixture_projection') AND name=N'uq_p02_source') CREATE UNIQUE INDEX uq_p02_source ON dbo.p02_fixture_projection(source_id,external_id) WHERE source_id IS NOT NULL AND external_id IS NOT NULL;");
  }
 }
 static long source()throws Exception {try(var c=ds.getConnection();var s=c.prepareStatement("INSERT dbo.he_thong_ngoai(ma,ten) VALUES(?,N'P02 SYNTHETIC fixture');SELECT CAST(SCOPE_IDENTITY() AS bigint)")){s.setString(1,"P02-"+UUID.randomUUID());try(var r=s.executeQuery()){r.next();return r.getLong(1);}}}
 static DurableSyncStore store(){return new DurableSyncStore(ds,P02Database.NAME,"DEV2022");}
 static ObjectNode change(long id,String code){var n=json.createObjectNode();n.put("source_id",Long.toString(id));n.put("operation","upsert");n.putNull("change_version");n.set("data",json.createObjectNode().put("code",code).put("name","Dữ liệu giả "+code));return n;}
 static ObjectNode page(UUID export,long upper,String cursor,JsonNode... rows){var n=json.createObjectNode();n.put("mode","full");n.put("source_epoch",EPOCH.toString());n.put("export_id",export.toString());n.putNull("from_version");n.put("to_version",Long.toString(upper));n.put("as_of","2026-11-12T01:00:00.123456789Z");var a=json.createArrayNode();for(var row:rows)a.add(row);n.set("items",a);if(cursor==null)n.putNull("next_cursor");else n.put("next_cursor",cursor);return n;}
 static long scalar(String sql)throws Exception{try(var c=ds.getConnection()){return P02Database.scalar(c,sql);}}
 static final class Target implements SyncTarget {
  public String resource(){return "ethnicities";}
  public boolean hasMappings(Connection c,long source)throws SQLException{return P02Database.scalar(c,"SELECT COUNT(*) FROM dbo.p02_fixture_projection WHERE source_id="+source)>0;}
  public Long apply(Connection c,long source,JsonNode item)throws SQLException{
   long id=CtSchema.number(item.path("source_id"));Long local=null;
   try(var s=c.prepareStatement("SELECT id FROM dbo.p02_fixture_projection WITH(UPDLOCK,HOLDLOCK) WHERE source_id=? AND external_id=?")){s.setLong(1,source);s.setLong(2,id);try(var r=s.executeQuery()){if(r.next())local=r.getLong(1);}}
   if(item.path("operation").asText().equals("delete")){markAbsent(c,source,id);return local;}
   if(local==null)try(var s=c.prepareStatement("INSERT dbo.p02_fixture_projection(source_id,external_id,code,name) VALUES(?,?,?,?);SELECT CAST(SCOPE_IDENTITY() AS bigint)")){s.setLong(1,source);s.setLong(2,id);s.setString(3,item.path("data").path("code").asText());s.setString(4,item.path("data").path("name").asText());try(var r=s.executeQuery()){r.next();return r.getLong(1);}}
   try(var s=c.prepareStatement("UPDATE dbo.p02_fixture_projection SET code=?,name=?,present=1 WHERE id=?")){s.setString(1,item.path("data").path("code").asText());s.setString(2,item.path("data").path("name").asText());s.setLong(3,local);s.executeUpdate();}return local;
  }
  public void markAbsent(Connection c,long source,long id)throws SQLException{try(var s=c.prepareStatement("UPDATE dbo.p02_fixture_projection SET present=0 WHERE source_id=? AND external_id=?")){s.setLong(1,source);s.setLong(2,id);s.executeUpdate();}}
 }
 static UUID baseline(DurableSyncStore store,DurableSyncStore.Lease lease)throws Exception{var p=page(UUID.randomUUID(),10,null,change(2,"A"),change(10,"B"));var id=store.beginRun(lease,p);store.page(lease,id,1,null,p,new Target());return id;}
 @Test void durableSeenStagingReplayAndAbsenceAreScopedToOneCompletedExport()throws Exception{
  long source=source();var s=store();try(var lease=s.acquire(source,"ethnicities")){
   UUID baseline=baseline(s,lease);long original=scalar("SELECT id FROM dbo.p02_fixture_projection WHERE source_id="+source+" AND external_id=10");
   try(var c=ds.getConnection();var q=c.createStatement()){q.execute("UPDATE dbo.p02_fixture_projection SET local_note=N'LOCAL' WHERE id="+original+";INSERT dbo.p02_fixture_projection(code,parent_id) VALUES(N'LOCAL-"+UUID.randomUUID()+"',"+original+")");}
   UUID export=UUID.randomUUID();var p1=page(export,20,"next",change(2,"A-new"));var run=s.beginRun(lease,p1);assertThat(s.page(lease,run,1,null,p1,new Target()).complete()).isFalse();
   assertThat(s.checkpoint(lease).version()).isEqualTo(10);assertThat(scalar("SELECT COUNT(*) FROM dbo.dong_bo_da_thay WHERE luot_id='"+run+"'")).isEqualTo(1);
   assertThat(s.page(lease,run,1,null,p1,new Target()).processed()).isEqualTo(1);assertThat(scalar("SELECT COUNT(*) FROM dbo.p02_fixture_projection WHERE source_id="+source)).isEqualTo(2);
   var p2=page(export,20,null,change(11,"C"));var broken=new DurableSyncStore(ds,P02Database.NAME,"DEV2022"){@Override protected void fault(String p,Connection c)throws Exception{if(p.equals("final.afterReconcile"))throw new SQLException("Injected final rollback");}};
   assertThatThrownBy(()->broken.page(lease,run,2,"next",p2,new Target())).isInstanceOf(SQLException.class);
   assertThat(s.checkpoint(lease).version()).isEqualTo(10);assertThat(scalar("SELECT present FROM dbo.p02_fixture_projection WHERE id="+original)).isEqualTo(1);
   assertThat(scalar("SELECT COUNT(*) FROM dbo.p02_fixture_projection WHERE source_id="+source+" AND external_id=11")).isZero();
   assertThat(s.page(lease,run,2,"next",p2,new Target()).complete()).isTrue();assertThat(s.checkpoint(lease).version()).isEqualTo(20);
   assertThat(scalar("SELECT COUNT(*) FROM dbo.p02_fixture_projection WHERE id="+original+" AND present=0 AND local_note=N'LOCAL'")).isEqualTo(1);
   assertThat(scalar("SELECT COUNT(*) FROM dbo.p02_fixture_projection WHERE parent_id="+original)).isEqualTo(1);
   s.cleanupOwned(lease,run);assertThat(scalar("SELECT COUNT(*) FROM dbo.dong_bo_da_thay WHERE luot_id='"+run+"'")).isZero();
   assertThat(scalar("SELECT COUNT(*) FROM dbo.dong_bo_da_thay WHERE luot_id='"+baseline+"'")).isEqualTo(2);
  }
 }
 @ParameterizedTest @ValueSource(strings={"page.afterApply","final.afterCheckpoint"})
 void pageAndFinalConnectionLossRollBackWithoutAdvancingCheckpoint(String point)throws Exception{
  long source=source();var s=store();try(var lease=s.acquire(source,"ethnicities")){
   baseline(s,lease);var p=page(UUID.randomUUID(),20,null,change(2,"CHANGED"),change(11,"NEW"));var run=s.beginRun(lease,p);
   var broken=new DurableSyncStore(ds,P02Database.NAME,"DEV2022"){@Override protected void fault(String p,Connection c)throws Exception{if(p.equals(point)){c.close();throw new SQLException("Injected lost connection before commit");}}};
   assertThatThrownBy(()->broken.page(lease,run,1,null,p,new Target())).isInstanceOf(SQLException.class);
  }
  try(var lease=s.acquire(source,"ethnicities")){
   assertThat(s.checkpoint(lease).version()).isEqualTo(10);assertThat(scalar("SELECT COUNT(*) FROM dbo.p02_fixture_projection WHERE source_id="+source+" AND external_id=11")).isZero();
   var p=page(UUID.randomUUID(),30,null,change(2,"REPLAY"));UUID run=s.beginRun(lease,p);assertThat(s.page(lease,run,1,null,p,new Target()).complete()).isTrue();
  }
 }
 @Test void sessionGuardAndGenerationRejectStaleWorkerAndForeignCleanup()throws Exception{
  long source=source();var s=store();try(var old=s.acquire(source,"ethnicities")){
   var p=page(UUID.randomUUID(),10,null,change(2,"A"));UUID run=s.beginRun(old,p);
   assertThatThrownBy(()->s.acquire(source,"ethnicities")).hasMessage("SYNC_BUSY");
   try(var q=old.connection.prepareStatement("EXEC sys.sp_releaseapplock @Resource=?,@LockOwner='Session'")){q.setString(1,old.lock);q.execute();}
   try(var next=s.acquire(source,"ethnicities")){
    assertThat(next.generation).isGreaterThan(old.generation);assertThatThrownBy(()->s.page(old,run,1,null,p,new Target())).hasMessage("STALE_WORKER");
    assertThatThrownBy(()->s.cleanupOwned(next,run)).isInstanceOf(IllegalStateException.class);assertThat(scalar("SELECT COUNT(*) FROM dbo.p02_fixture_projection WHERE source_id="+source)).isZero();
   }
  }
 }
 @Test void lostFinalAcknowledgementReadsCommittedOutcomeInsteadOfRepeatingWrites()throws Exception{
  long source=source();var s=new DurableSyncStore(ds,P02Database.NAME,"DEV2022"){@Override protected void fault(String p,Connection c)throws Exception{if(p.equals("page.afterCommit")){c.close();throw new SQLException("Lost final acknowledgement");}}};
  try(var lease=s.acquire(source,"ethnicities")){var p=page(UUID.randomUUID(),10,null,change(2,"A"));UUID run=s.beginRun(lease,p);assertThat(s.page(lease,run,1,null,p,new Target()).complete()).isTrue();assertThat(s.outcome(run).complete()).isTrue();}
  assertThat(scalar("SELECT COUNT(*) FROM dbo.p02_fixture_projection WHERE source_id="+source)).isEqualTo(1);
 }
 @Test void uniquenessFailureRollsBackWholePageAndPreservesPriorCheckpoint()throws Exception{
  long source=source();var s=store();try(var lease=s.acquire(source,"ethnicities")){
   baseline(s,lease);var p=page(UUID.randomUUID(),20,null,change(11,"NEW"),change(12,"B"));UUID run=s.beginRun(lease,p);
   assertThatThrownBy(()->s.page(lease,run,1,null,p,new Target())).isInstanceOf(SQLException.class);
   assertThat(s.checkpoint(lease).version()).isEqualTo(10);assertThat(scalar("SELECT COUNT(*) FROM dbo.p02_fixture_projection WHERE source_id="+source+" AND external_id=11")).isZero();
   assertThat(scalar("SELECT COUNT(*) FROM dbo.dong_bo_da_thay WHERE luot_id='"+run+"'")).isZero();
  }
 }

 @Test void httpBoundaryRejectsManualSqlTransactionEvenWhenJdbcAutoCommitIsTrue()throws Exception {
  long source=source();var s=store();try(var lease=s.acquire(source,"ethnicities")){
   try(var q=lease.connection.createStatement()){q.execute("BEGIN TRANSACTION");}
   try{assertThat(lease.connection.getAutoCommit()).isTrue();assertThatThrownBy(lease::beforeHttp).isInstanceOf(IllegalStateException.class);}
   finally{try(var q=lease.connection.createStatement()){q.execute("IF @@TRANCOUNT>0 ROLLBACK TRANSACTION");}}
  }
 }
}
