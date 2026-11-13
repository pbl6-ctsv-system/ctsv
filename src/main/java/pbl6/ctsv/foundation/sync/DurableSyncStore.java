package pbl6.ctsv.foundation.sync;

import java.sql.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.*;
import javax.sql.DataSource;import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.node.ObjectNode;

/** Durable source/resource ownership, page state and atomic final checkpoint. */
public class DurableSyncStore {
 private final DataSource source;private final String database,instance;private final ObjectMapper json=new ObjectMapper();private final java.util.concurrent.ConcurrentHashMap<String,CtSchema> schemas=new java.util.concurrent.ConcurrentHashMap<>();
 public record Checkpoint(UUID epoch,long version) {}
 public record Outcome(UUID run,boolean complete,long processed,UUID epoch,long version) {}
 public DurableSyncStore(DataSource source,String database,String instance){this.source=source;this.database=database;this.instance=instance;}
 public final class Lease implements AutoCloseable {
  public final Connection connection;public final long sourceId,generation;public final String resource,lock;public final UUID owner;
  private Lease(Connection c,long s,String r,String lock,UUID owner,long gen){this.connection=c;this.sourceId=s;this.resource=r;this.lock=lock;this.owner=owner;this.generation=gen;}
  public void beforeHttp()throws SQLException {if(connection.isClosed()||!connection.getAutoCommit())throw new IllegalStateException("HTTP must be outside a SQL transaction");requireSession(this);}
  public void close()throws SQLException {
   try{if(!connection.isClosed()){
    if(!connection.getAutoCommit()){connection.rollback();connection.setAutoCommit(true);}
    try(var s=connection.prepareStatement("IF APPLOCK_MODE('public',?,'Session')='Exclusive' EXEC sys.sp_releaseapplock @Resource=?,@LockOwner='Session'")){s.setString(1,lock);s.setString(2,lock);s.execute();}
   }}finally{connection.close();}
  }
 }
 private void verify(Connection c)throws SQLException {
  try(var s=c.createStatement();var r=s.executeQuery("SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName'))")){
   if(!r.next()||!database.equals(r.getString(1))||!instance.equalsIgnoreCase(r.getString(2)))throw new IllegalStateException("Unexpected durable-sync database");
  }
  if(!c.getMetaData().getURL().toLowerCase(Locale.ROOT).contains("connectretrycount=0"))throw new IllegalStateException("Idle reconnect must be disabled");
 }
 public Lease acquire(long sourceId,String resource)throws SQLException {
  if(sourceId<1||resource==null||!resource.matches("[a-z]+(?:-[a-z]+)*")||resource.equals("units"))throw new IllegalArgumentException("Invalid non-units binding");
  Connection c=source.getConnection();String lock="ctsv.foundation.ct26:"+sourceId+":"+resource;UUID owner=UUID.randomUUID();
  try{
   verify(c);if(!c.getAutoCommit())throw new IllegalStateException("Dedicated connection required");
   try(var s=c.prepareStatement("DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=?,@LockMode='Exclusive',@LockOwner='Session',@LockTimeout=0; SELECT @r")){
    s.setString(1,lock);try(var r=s.executeQuery()){if(!r.next()||r.getInt(1)<0)throw new IllegalStateException("SYNC_BUSY");}
   }
   c.setAutoCommit(false);
   try(var s=c.prepareStatement("IF NOT EXISTS(SELECT 1 FROM dbo.dong_bo_trang_thai WITH(UPDLOCK,HOLDLOCK) WHERE he_thong_nguon_id=? AND tai_nguyen=?) INSERT dbo.dong_bo_trang_thai(he_thong_nguon_id,tai_nguyen) VALUES(?,?); UPDATE dbo.dong_bo_trang_thai SET the_he=the_he+1,chu_so_huu=? OUTPUT inserted.the_he WHERE he_thong_nguon_id=? AND tai_nguyen=?;")){
    s.setLong(1,sourceId);s.setString(2,resource);s.setLong(3,sourceId);s.setString(4,resource);s.setObject(5,owner);s.setLong(6,sourceId);s.setString(7,resource);
    try(var r=s.executeQuery()){r.next();long generation=r.getLong(1);c.commit();c.setAutoCommit(true);return new Lease(c,sourceId,resource,lock,owner,generation);}
   }
  }catch(Exception e){try{if(!c.getAutoCommit())c.rollback();c.abort(Runnable::run);}finally{c.close();}throw e;}
 }
 private void requireSession(Lease l)throws SQLException {
  try(var s=l.connection.prepareStatement("SELECT APPLOCK_MODE('public',?,'Session'),@@TRANCOUNT")){s.setString(1,l.lock);try(var r=s.executeQuery()){if(!r.next()||!"Exclusive".equals(r.getString(1)))throw new IllegalStateException("STALE_WORKER");if(l.connection.getAutoCommit()&&r.getInt(2)!=0)throw new IllegalStateException("SQL transaction open before HTTP");}}
 }
 private void fence(Lease l)throws SQLException {
  requireSession(l);
  try(var s=l.connection.prepareStatement("SELECT the_he,chu_so_huu FROM dbo.dong_bo_trang_thai WITH(UPDLOCK,HOLDLOCK) WHERE he_thong_nguon_id=? AND tai_nguyen=?")){
   s.setLong(1,l.sourceId);s.setString(2,l.resource);try(var r=s.executeQuery()){if(!r.next()||r.getLong(1)!=l.generation||!l.owner.toString().equalsIgnoreCase(r.getString(2)))throw new IllegalStateException("STALE_WORKER");}
  }
 }
 public Checkpoint checkpoint(Lease l)throws SQLException {
  try(var s=l.connection.prepareStatement("SELECT source_epoch,ct_version FROM dbo.dong_bo_trang_thai WHERE he_thong_nguon_id=? AND tai_nguyen=?")){
   s.setLong(1,l.sourceId);s.setString(2,l.resource);try(var r=s.executeQuery()){r.next();return r.getString(1)==null?null:new Checkpoint(UUID.fromString(r.getString(1)),r.getLong(2));}
  }
 }
 public UUID beginRun(Lease l,JsonNode first)throws Exception {
  schemas.computeIfAbsent(l.resource,CtSchema::new).validate(first);Checkpoint cp=checkpoint(l);UUID epoch=UUID.fromString(first.path("source_epoch").asText());long upper=CtSchema.number(first.path("to_version"));
  if(cp!=null&&(!cp.epoch.equals(epoch)||upper<cp.version))throw new IllegalStateException(!cp.epoch.equals(epoch)?"SOURCE_EPOCH_CHANGED":"SOURCE_LINEAGE_INVALID");
  if(first.path("mode").asText().equals("incremental")&&(cp==null||CtSchema.number(first.path("from_version"))!=cp.version))throw new IllegalArgumentException("Invalid lower checkpoint");
  UUID run=UUID.randomUUID();var anchor=((ObjectNode)first.deepCopy());anchor.remove(List.of("items","next_cursor"));
  l.connection.setAutoCommit(false);
  try{
   fence(l);
   try(var s=l.connection.prepareStatement("UPDATE dbo.dong_bo_luot SET trang_thai='INTERRUPTED' WHERE he_thong_nguon_id=? AND tai_nguyen=? AND trang_thai='RUNNING' AND the_he<?")){s.setLong(1,l.sourceId);s.setString(2,l.resource);s.setLong(3,l.generation);s.executeUpdate();}
   try(var s=l.connection.prepareStatement("INSERT dbo.dong_bo_luot(id,he_thong_nguon_id,tai_nguyen,the_he,chu_so_huu,export_id,source_epoch,che_do,tu_version,den_version,as_of,trang_thai,anchor_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,'RUNNING',?)")){
    s.setObject(1,run);s.setLong(2,l.sourceId);s.setString(3,l.resource);s.setLong(4,l.generation);s.setObject(5,l.owner);s.setObject(6,UUID.fromString(first.path("export_id").asText()));s.setObject(7,epoch);s.setString(8,first.path("mode").asText());
    if(first.path("from_version").isNull())s.setNull(9,Types.BIGINT);else s.setLong(9,CtSchema.number(first.path("from_version")));s.setLong(10,upper);s.setObject(11,java.time.OffsetDateTime.parse(first.path("as_of").asText()));s.setString(12,anchor.toString());s.executeUpdate();
   }l.connection.commit();return run;
  }catch(Exception e){l.connection.rollback();throw e;}finally{l.connection.setAutoCommit(true);}
 }
 protected void fault(String point,Connection c)throws Exception {}
 public Outcome page(Lease l,UUID run,int pageNo,String usedCursor,JsonNode page,SyncTarget target)throws Exception {
  if(!target.resource().equals(l.resource))throw new IllegalArgumentException("Wrong resource target");new CtSchema(l.resource).validate(page);
  String payloadHash=hash(page.toString()),cursorHash=hash(usedCursor==null?"INITIAL":usedCursor);boolean finalPage=page.path("next_cursor").isNull();Connection c=l.connection;
  c.setAutoCommit(false);boolean committing=false;
  try{
   fence(l);long processed,previous;int completedPages;JsonNode anchor;
   try(var s=c.prepareStatement("SELECT so_ban_ghi,source_id_cuoi,so_trang,anchor_json,the_he,chu_so_huu,trang_thai FROM dbo.dong_bo_luot WITH(UPDLOCK) WHERE id=? AND he_thong_nguon_id=? AND tai_nguyen=?")){
    s.setObject(1,run);s.setLong(2,l.sourceId);s.setString(3,l.resource);try(var r=s.executeQuery()){
     if(!r.next()||r.getLong(5)!=l.generation||!l.owner.toString().equalsIgnoreCase(r.getString(6)))throw new IllegalStateException("Run is not owned");
     processed=r.getLong(1);previous=r.getLong(2);completedPages=r.getInt(3);anchor=json.readTree(r.getString(4));
     var actual=(ObjectNode)page.deepCopy();actual.remove(List.of("items","next_cursor"));if(!anchor.equals(actual))throw new IllegalArgumentException("Export metadata changed");
    }
   }
   try(var s=c.prepareStatement("SELECT payload_hash FROM dbo.dong_bo_trang_tam WHERE luot_id=? AND so_trang=?")){
    s.setObject(1,run);s.setInt(2,pageNo);try(var r=s.executeQuery()){if(r.next()){if(!payloadHash.equals(r.getString(1)))throw new IllegalArgumentException("Replay payload changed");c.rollback();c.setAutoCommit(true);return outcome(run);}}
   }
   if(pageNo!=completedPages+1)throw new IllegalArgumentException("Page sequence changed");
   try(var s=c.prepareStatement("INSERT dbo.dong_bo_trang_tam(luot_id,so_trang,cursor_hash,payload_hash,payload) VALUES(?,?,?,?,?)")){
    s.setObject(1,run);s.setInt(2,pageNo);s.setString(3,cursorHash);s.setString(4,payloadHash);s.setString(5,page.toString());s.executeUpdate();
   }
   boolean full=page.path("mode").asText().equals("full");
   for(var item:page.path("items")){
    long id=CtSchema.number(item.path("source_id"));if(id<=previous)throw new IllegalArgumentException("Source order changed across pages");previous=id;
    Long local=target.apply(c,l.sourceId,item);
    if(local!=null){boolean present=item.path("operation").asText().equals("upsert");
     try(var s=c.prepareStatement("UPDATE dbo.dong_bo_hien_dien SET local_id=?,dang_hien_dien=?,sha256=? WHERE he_thong_nguon_id=? AND tai_nguyen=? AND source_id=?; IF @@ROWCOUNT=0 INSERT dbo.dong_bo_hien_dien(he_thong_nguon_id,tai_nguyen,source_id,local_id,dang_hien_dien,sha256) VALUES(?,?,?,?,?,?)")){
      s.setLong(1,local);s.setBoolean(2,present);s.setString(3,hash(item.toString()));s.setLong(4,l.sourceId);s.setString(5,l.resource);s.setLong(6,id);s.setLong(7,l.sourceId);s.setString(8,l.resource);s.setLong(9,id);s.setLong(10,local);s.setBoolean(11,present);s.setString(12,hash(item.toString()));s.executeUpdate();
     }
    }
    if(full)try(var s=c.prepareStatement("INSERT dbo.dong_bo_da_thay(luot_id,source_id) VALUES(?,?)")){s.setObject(1,run);s.setLong(2,id);s.executeUpdate();}
   }
   fault("page.afterApply",c);processed+=page.path("items").size();
   if(finalPage){
    if(full){
     try(var s=c.prepareStatement("SELECT h.source_id FROM dbo.dong_bo_hien_dien h WHERE h.he_thong_nguon_id=? AND h.tai_nguyen=? AND h.dang_hien_dien=1 AND NOT EXISTS(SELECT 1 FROM dbo.dong_bo_da_thay v WHERE v.luot_id=? AND v.source_id=h.source_id)")){
      s.setLong(1,l.sourceId);s.setString(2,l.resource);s.setObject(3,run);s.setFetchSize(250);try(var r=s.executeQuery()){while(r.next())target.markAbsent(c,l.sourceId,r.getLong(1));}
     }
     try(var s=c.prepareStatement("UPDATE h SET dang_hien_dien=0 FROM dbo.dong_bo_hien_dien h WHERE h.he_thong_nguon_id=? AND h.tai_nguyen=? AND NOT EXISTS(SELECT 1 FROM dbo.dong_bo_da_thay v WHERE v.luot_id=? AND v.source_id=h.source_id)")){s.setLong(1,l.sourceId);s.setString(2,l.resource);s.setObject(3,run);s.executeUpdate();}
    }
    fault("final.afterReconcile",c);
    try(var s=c.prepareStatement("UPDATE dbo.dong_bo_trang_thai SET source_epoch=?,ct_version=?,luot_thanh_cong=? WHERE he_thong_nguon_id=? AND tai_nguyen=? AND the_he=? AND chu_so_huu=?")){
     s.setObject(1,UUID.fromString(page.path("source_epoch").asText()));s.setLong(2,CtSchema.number(page.path("to_version")));s.setObject(3,run);s.setLong(4,l.sourceId);s.setString(5,l.resource);s.setLong(6,l.generation);s.setObject(7,l.owner);if(s.executeUpdate()!=1)throw new IllegalStateException("STALE_WORKER");
    }fault("final.afterCheckpoint",c);
   }
   try(var s=c.prepareStatement("UPDATE dbo.dong_bo_luot SET so_ban_ghi=?,source_id_cuoi=?,so_trang=?,trang_thai=?,thoi_diem_ket_thuc=CASE WHEN ?=1 THEN SYSUTCDATETIME() ELSE NULL END WHERE id=?")){
    s.setLong(1,processed);s.setLong(2,previous);s.setInt(3,pageNo);s.setString(4,finalPage?"SUCCESS":"RUNNING");s.setBoolean(5,finalPage);s.setObject(6,run);s.executeUpdate();
   }
   fault("page.beforeCommit",c);committing=true;c.commit();fault("page.afterCommit",c);c.setAutoCommit(true);return outcome(run);
  }catch(Exception e){
   if(!c.isClosed()){try{c.rollback();c.setAutoCommit(true);}catch(SQLException ignored){}}
   if(committing){Outcome known=outcome(run);if(known.complete)return known;throw new IllegalStateException("Commit acknowledgement unknown; checkpoint/run inspected; resume from last success",e);}
   throw e;
  }
 }
 public Outcome outcome(UUID run)throws SQLException {
  try(var c=source.getConnection()){
   verify(c);try(var s=c.prepareStatement("SELECT trang_thai,so_ban_ghi,source_epoch,den_version FROM dbo.dong_bo_luot WHERE id=?")){s.setObject(1,run);try(var r=s.executeQuery()){if(!r.next())throw new IllegalStateException("Unknown run");return new Outcome(run,"SUCCESS".equals(r.getString(1)),r.getLong(2),UUID.fromString(r.getString(3)),r.getLong(4));}}
  }
 }
 public void cleanupOwned(Lease l,UUID run)throws SQLException {
  l.connection.setAutoCommit(false);try{fence(l);
   try(var s=l.connection.prepareStatement("SELECT COUNT(*) FROM dbo.dong_bo_luot WHERE id=? AND he_thong_nguon_id=? AND tai_nguyen=? AND the_he=? AND chu_so_huu=? AND trang_thai IN('SUCCESS','FAILED')")){
    s.setObject(1,run);s.setLong(2,l.sourceId);s.setString(3,l.resource);s.setLong(4,l.generation);s.setObject(5,l.owner);try(var r=s.executeQuery()){r.next();if(r.getInt(1)!=1)throw new IllegalStateException("Cannot clean an unowned or active run");}
   }
   for(String table:List.of("dong_bo_trang_tam","dong_bo_da_thay"))try(var s=l.connection.prepareStatement("DELETE dbo."+table+" WHERE luot_id=?")){s.setObject(1,run);s.executeUpdate();}
   l.connection.commit();
  }catch(Exception e){l.connection.rollback();throw e;}finally{l.connection.setAutoCommit(true);}
 }
 public void fail(Lease l,UUID run){
  try{if(l.connection.isClosed())return;if(!l.connection.getAutoCommit()){l.connection.rollback();l.connection.setAutoCommit(true);}
   l.connection.setAutoCommit(false);fence(l);
   try(var q=l.connection.prepareStatement("UPDATE dbo.dong_bo_luot SET trang_thai='FAILED',thoi_diem_ket_thuc=SYSUTCDATETIME() WHERE id=? AND the_he=? AND chu_so_huu=? AND trang_thai='RUNNING'")){q.setObject(1,run);q.setLong(2,l.generation);q.setObject(3,l.owner);q.executeUpdate();}l.connection.commit();l.connection.setAutoCommit(true);
  }catch(Exception ignored){try{if(!l.connection.isClosed()){l.connection.rollback();l.connection.setAutoCommit(true);}}catch(SQLException ignoredAgain){}}
 }
 public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
