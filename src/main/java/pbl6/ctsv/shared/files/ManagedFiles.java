package pbl6.ctsv.shared.files;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Internal application boundary. Caller must authorize its business object before reading bytes.
 * Uses the existing private storage and upload ledger, never public URLs or SQL file bodies. */
@Service @ConditionalOnProperty(name="ctsv.files.enabled",havingValue="true")
public final class ManagedFiles {
 public record Info(long id,Long owner,String name,long size,String sha256,boolean ready){}
 public record Pdf(long id,String name,String sha256,byte[] bytes){
  public Pdf{bytes=bytes.clone();}
  @Override public byte[] bytes(){return bytes.clone();}
 }
 private final JdbcTemplate jdbc;private final FileMetadata metadata;private final UploadLedger ledger;
 private final ObjectProvider<PrivateFileStorage> storage;private final TransactionTemplate tx;private final PdfSpool spool=new PdfSpool();
 public ManagedFiles(JdbcTemplate jdbc,ObjectProvider<PrivateFileStorage> storage,PlatformTransactionManager manager){
  this.jdbc=jdbc;this.storage=storage;this.tx=new TransactionTemplate(manager);this.metadata=new FileMetadata(jdbc);this.ledger=new UploadLedger(jdbc);
 }
 public Info describe(long id){return info(metadata.get(id).orElseThrow(FileProblem::notFound));}
 /** Recover a committed output without consulting the provider that originally signed it. */
 public Optional<Info> committed(UUID operation,long owner,String hash){
  var rows=jdbc.queryForList("SELECT tep_id FROM dbo.luot_tai_tep WHERE id=? AND nguoi_dung_id=? AND sha256=? AND trang_thai='committed' AND tep_id IS NOT NULL",Long.class,operation.toString(),owner,hash);
  if(rows.isEmpty())return Optional.empty();var f=describe(rows.getFirst());if(!f.ready()||!Objects.equals(f.owner(),owner)||!hash.equals(f.sha256()))throw FileProblem.integrity();return Optional.of(f);
 }
 private Info info(FileMetadata.Stored f){return new Info(f.id(),f.owner(),f.name(),f.bytes(),f.hash(),!f.deleted()&&"available".equals(f.state())&&"authenticated".equals(f.delivery())&&"raw".equals(f.resource()));}
 private PrivateFileStorage remote(){var r=storage.getIfAvailable();if(r==null)throw FileProblem.unavailable();return r;}
 public Pdf read(long id){
  noNetworkTransaction();var file=metadata.get(id).orElseThrow(FileProblem::notFound);if(!info(file).ready())throw new IllegalStateException("FILE_NOT_READY");
  var r=remote();if(!r.cloud().equals(file.cloud())||file.asset()==null||file.bytes()<1||file.bytes()>PdfSpool.DOWNLOAD_LIMIT)throw FileProblem.integrity();
  try(var lease=spool.open()){
   r.download(file.asset(),lease.path,file.bytes());if(Files.size(lease.path)!=file.bytes()||!PdfSpool.sha256(lease.path).equals(file.hash()))throw FileProblem.integrity();
   return new Pdf(id,file.name(),file.hash(),Files.readAllBytes(lease.path));
  }catch(IOException e){throw FileProblem.unavailable();}
 }
 public Info store(UUID operation,long owner,byte[] bytes,String name){
  noNetworkTransaction();Objects.requireNonNull(operation);Objects.requireNonNull(bytes);String label=FileService.label(name);
  if(bytes.length==0||bytes.length>PdfSpool.UPLOAD_LIMIT)throw FileProblem.tooLarge();
  var r=remote();try(var lease=spool.open()){
   Files.write(lease.path,bytes);int pages=PdfValidation.pages(lease.path);String hash=PdfSpool.sha256(lease.path);
   var a=new UploadLedger.Attempt(operation,owner,r.cloud(),r.publicId("ctsv-result-"+operation+".pdf"),"document",bytes.length,hash);
   var existing=tx.execute(s->{
    lock(operation);var rows=jdbc.queryForList("SELECT nguoi_dung_id owner,ten_cloud cloud,ma_cong_khai objectKey,kich_thuoc size,sha256 hash,tep_id fileId FROM dbo.luot_tai_tep WITH(UPDLOCK,HOLDLOCK) WHERE id=?",operation.toString());
    if(rows.isEmpty()){ledger.start(a);return null;}
    var row=rows.getFirst();if(((Number)row.get("owner")).longValue()!=owner||!a.cloud().equals(row.get("cloud"))||!a.key().equals(row.get("objectKey"))||((Number)row.get("size")).longValue()!=bytes.length||!hash.equals(row.get("hash")))throw new IllegalStateException("FILE_OPERATION_CONFLICT");
    return row;
   });
   if(existing!=null&&existing.get("fileId")!=null)return describe(((Number)existing.get("fileId")).longValue());
   try{
    // Unknown uploads are only adopted by the deterministic, owner-bound key. Never POST again blindly.
    var asset=existing==null?r.upload(a.key(),lease.path,a.bytes()):r.find(a.key()).orElseThrow(()->new IllegalStateException("FILE_UPLOAD_PENDING"));
    r.download(asset.id(),lease.path,a.bytes());if(Files.size(lease.path)!=a.bytes()||!PdfSpool.sha256(lease.path).equals(hash))throw FileProblem.integrity();
    return tx.execute(s->{lock(operation);
     var linked=jdbc.queryForObject("SELECT tep_id FROM dbo.luot_tai_tep WHERE id=?",Long.class,operation.toString());if(linked!=null)return describe(linked);
     var view=metadata.save(a,asset,pages,label);ledger.complete(a,Long.parseLong(view.id()),asset);return describe(Long.parseLong(view.id()));
    });
   }catch(RuntimeException e){tx.executeWithoutResult(s->ledger.uncertain(a));throw e;}
  }catch(IOException e){throw FileProblem.unavailable();}
 }
 private void lock(UUID id){
  // JDBC defers BEGIN until a table statement; start the owner-bound range lock before applock.
  jdbc.queryForObject("SELECT COUNT(*) FROM dbo.luot_tai_tep WITH(UPDLOCK,HOLDLOCK) WHERE id=?",Integer.class,id.toString());
  Integer result=jdbc.queryForObject("DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=?,@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000; SELECT @r",Integer.class,"ctsv-file-operation:"+id);
  if(result==null||result<0)throw FileProblem.unavailable();
 }
 private static void noNetworkTransaction(){if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("File network call cannot run inside a SQL transaction");}
}
