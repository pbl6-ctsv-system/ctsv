package pbl6.ctsv.shared.files;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import pbl6.ctsv.mghp.application.SigningDatabase;
import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named="p11.test.enabled",matches="true")
class ManagedFilesIT {
 static class Storage implements PrivateFileStorage {
  Map<String,byte[]> bytes=new HashMap<>();int uploads;boolean lostReply;
  public String cloud(){return "p11-private-stub";}
  public Asset upload(String key,Path file,long size){try{uploads++;bytes.put(key,Files.readAllBytes(file));if(lostReply){lostReply=false;throw new IllegalStateException("STUB_LOST_REPLY");}return new Asset(key,1);}catch(java.io.IOException e){throw new IllegalStateException(e);}}
  public void download(String id,Path path,long size){try{Files.write(path,bytes.get(id));}catch(java.io.IOException e){throw new IllegalStateException(e);}}
  public Optional<Asset> find(String key){return bytes.containsKey(key)?Optional.of(new Asset(key,1)):Optional.empty();}
 }
 @Test void immutableOutputsRoundTripAndReplayDoesNotUploadAgain()throws Exception{
  var ds=SigningDatabase.migrate();var jdbc=new JdbcTemplate(ds);var remote=new Storage();var beans=new StaticListableBeanFactory(Map.of("private",remote));
  var files=new ManagedFiles(jdbc,beans.getBeanProvider(PrivateFileStorage.class),new DataSourceTransactionManager(ds));
  long owner=jdbc.queryForObject("INSERT dbo.nguoi_dung(dia_chi_email,ho_ten,dang_hoat_dong) VALUES(?,N'P11 file fixture',1);SELECT CAST(SCOPE_IDENTITY() AS bigint)",Long.class,"p11-"+UUID.randomUUID()+"@example.invalid");
  byte[] input;try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()){pdf.addPage(new PDPage());pdf.save(out);input=out.toByteArray();}
  var operation=UUID.randomUUID();var one=files.store(operation,owner,input,"Sau người ký 1.pdf");
  assertThat(files.store(operation,owner,input,"Sau người ký 1.pdf").id()).isEqualTo(one.id());
  var two=files.store(UUID.randomUUID(),owner,input,"Sau người ký 2.pdf");
  assertThat(one.id()).isNotEqualTo(two.id());assertThat(remote.uploads).isEqualTo(2);
  assertThat(files.read(one.id()).bytes()).containsExactly(input);assertThat(files.read(two.id()).sha256()).isEqualTo(one.sha256());
  assertThatThrownBy(()->files.store(operation,owner+1,input,"Sai.pdf")).hasMessage("FILE_OPERATION_CONFLICT");
 }
 @Test void lostCloudReplyUsesOwnedLedgerAndAdoptsInsteadOfUploadAgain()throws Exception{
  var ds=SigningDatabase.migrate();var jdbc=new JdbcTemplate(ds);var remote=new Storage();remote.lostReply=true;
  var files=new ManagedFiles(jdbc,new StaticListableBeanFactory(Map.of("private",remote)).getBeanProvider(PrivateFileStorage.class),new DataSourceTransactionManager(ds));
  long owner=jdbc.queryForObject("INSERT dbo.nguoi_dung(dia_chi_email,ho_ten,dang_hoat_dong) VALUES(?,N'P11 recovery fixture',1);SELECT CAST(SCOPE_IDENTITY() AS bigint)",Long.class,"p11-"+UUID.randomUUID()+"@example.invalid");
  byte[] input;try(var pdf=new PDDocument();var out=new ByteArrayOutputStream()){pdf.addPage(new PDPage());pdf.save(out);input=out.toByteArray();}
  var operation=UUID.randomUUID();assertThatThrownBy(()->files.store(operation,owner,input,"Kết quả.pdf")).hasMessage("STUB_LOST_REPLY");
  assertThat(jdbc.queryForObject("SELECT trang_thai FROM dbo.luot_tai_tep WHERE id=?",String.class,operation.toString())).isEqualTo("unknown");
  var ready=files.store(operation,owner,input,"Kết quả.pdf");assertThat(remote.uploads).isEqualTo(1);assertThat(files.read(ready.id()).bytes()).containsExactly(input);
 }
}
