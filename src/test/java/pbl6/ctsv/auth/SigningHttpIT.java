package pbl6.ctsv.auth;

import com.fasterxml.jackson.databind.*;
import java.net.*;import java.net.http.*;import java.time.*;import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import pbl6.ctsv.mghp.application.SigningDatabase;
import pbl6.ctsv.shared.files.PrivateFileStorage;
import static org.assertj.core.api.Assertions.*;

/** REAL HTTPS/Auth/JPA/SQL. Cloudinary/provider fault tests explicitly use STUB. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,classes=SigningHttpIT.Config.class,
 properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.mghp.configuration.enabled=true","ctsv.mghp.applications.enabled=true","ctsv.files.enabled=true","ctsv.files.reconciliation.enabled=false","ctsv.mghp.signing.enabled=true","ctsv.mghp.signing.worker.enabled=false","server.address=127.0.0.1"})
@ActiveProfiles({"local","test"}) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named="p11.test.enabled",matches="true")
class SigningHttpIT {
 static final ObjectMapper JSON=new ObjectMapper();
 static final String PASSWORD="P11-Synthetic-only!42";
 @Test void menuCapabilityComesFromLiveBusinessGrants()throws Exception{
  for(String token:List.of(cv,pp,pht))assertThat(call("GET","/api/ctsv/nen-tang/toi/quyen-giao-dien",null,token,null).expect(200).body.path("mghpSigning").asBoolean()).isTrue();
  for(String token:List.of(sv,admin))assertThat(call("GET","/api/ctsv/nen-tang/toi/quyen-giao-dien",null,token,null).expect(200).body.path("mghpSigning").asBoolean()).isFalse();
 }
 @Test void listViewsAreServerFilteredAndRejectUnknownView()throws Exception{
  var b=bundle(true);String doc=document(b,true);var q=call("POST","/api/ctsv/phien-ban-van-ban/"+doc+"/yeu-cau-ky",Map.of(),cv,null).expect(201);
  var waiting=call("GET","/api/ctsv/tai-lieu-ky?view=waiting",null,cv,null).expect(200);
  assertThat(waiting.body.path("items").findValuesAsText("id")).contains(q.body.path("id").asText());
  assertThat(call("GET","/api/ctsv/tai-lieu-ky?view=handled",null,cv,null).expect(200).body.path("items").findValuesAsText("id")).doesNotContain(q.body.path("id").asText());
  call("GET","/api/ctsv/tai-lieu-ky?view=invalid",null,cv,null).expect(400);
  call("GET","/api/ctsv/tai-lieu-ky?view=waiting",null,admin,null).expect(403);
 }
 @Configuration(proxyBeanMethods=false) @org.springframework.boot.autoconfigure.EnableAutoConfiguration
 @org.springframework.boot.autoconfigure.domain.EntityScan({"pbl6.ctsv.foundation","pbl6.ctsv.mghp"})
 @ComponentScan(basePackages={"pbl6.ctsv.auth","pbl6.ctsv.foundation","pbl6.ctsv.mghp","pbl6.ctsv.shared"},excludeFilters={@ComponentScan.Filter(type=FilterType.CUSTOM,classes=org.springframework.boot.context.TypeExcludeFilter.class),@ComponentScan.Filter(type=FilterType.REGEX,pattern=".*(?:Test|IT)(?:\\$.*)?")})
 static class Config {
  @Bean DataSource source()throws Exception{return SigningDatabase.migrate();}
  @Bean PrivateFileStorage privateStorage(){return new PrivateFileStorage(){
   final Map<String,byte[]> data=new java.util.concurrent.ConcurrentHashMap<>();
   public String cloud(){return "p11-http-stub";}
   public Asset upload(String key,java.nio.file.Path file,long size){try{data.put(key,java.nio.file.Files.readAllBytes(file));return new Asset(key,1);}catch(Exception e){throw new IllegalStateException(e);}}
   public void download(String id,java.nio.file.Path dest,long size){try{java.nio.file.Files.write(dest,data.get(id));}catch(Exception e){throw new IllegalStateException(e);}}
   public Optional<Asset> find(String key){return data.containsKey(key)?Optional.of(new Asset(key,1)):Optional.empty();}
  };}
  @Bean @Primary pbl6.ctsv.mghp.application.SigningProvider testSigningProvider(){return new ProviderStub();}
 }
 static class ProviderStub implements pbl6.ctsv.mghp.application.SigningProvider {
  final Map<UUID,byte[]> documents=new java.util.concurrent.ConcurrentHashMap<>();int signs,uploads;boolean downloadFails,unknownSign,downloadGone,certificateUnavailable;
  public pbl6.ctsv.shared.ca.CaProviderDtos.Certificates certificates(String email){int serial=email.startsWith("signer-a")?101:202;return new pbl6.ctsv.shared.ca.CaProviderDtos.Certificates(email,certificateUnavailable?List.of():List.of(new pbl6.ctsv.shared.ca.CaProviderDtos.Certificate(UUID.nameUUIDFromBytes(email.getBytes()),UUID.nameUUIDFromBytes((email+"user").getBytes()),"P11 Synthetic signer "+serial,""+serial,"active",Instant.now().minusSeconds(60),Instant.now().plusSeconds(3600),true,true)));}
  public pbl6.ctsv.shared.ca.CaProviderDtos.Uploaded upload(pbl6.ctsv.shared.ca.CaPdfPreflight.Input input,String title,UUID category){assertOutside();uploads++;UUID id=UUID.randomUUID();documents.put(id,input.bytes());return new pbl6.ctsv.shared.ca.CaProviderDtos.Uploaded(id,input.sha256(),category,1,"uploaded",false);}
  public pbl6.ctsv.shared.ca.CaProviderDtos.Receipt sign(UUID doc,UUID cert,String email,String name,pbl6.ctsv.shared.ca.CaPdfPreflight.Box box){
   assertOutside();signs++;byte[] input=documents.get(doc);try{int serial=email.startsWith("signer-a")?101:202;var output=pbl6.ctsv.ca.CaPdfSignatureVerifierTest.signed(input,serial);documents.put(doc,output);if(unknownSign){unknownSign=false;throw new pbl6.ctsv.shared.ca.CaFailure("CA_TIMEOUT",0,true);}return new pbl6.ctsv.shared.ca.CaProviderDtos.Receipt(UUID.randomUUID(),doc,cert,""+serial,email,name,pbl6.ctsv.shared.ca.CaPdfPreflight.sha256(input),pbl6.ctsv.shared.ca.CaPdfPreflight.sha256(output),Instant.now(),"valid");}catch(pbl6.ctsv.shared.ca.CaFailure e){throw e;}catch(Exception e){throw new IllegalStateException(e);}
  }
  public byte[] download(UUID doc){assertOutside();if(downloadGone)throw new pbl6.ctsv.shared.ca.CaFailure("CA_DOCUMENT_NOT_FOUND",404,false);if(downloadFails)throw new pbl6.ctsv.shared.ca.CaFailure("CA_DOWNLOAD_UNAVAILABLE",503,false);return documents.get(doc);}
  void assertOutside(){assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();}
 }
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r)throws Exception{AuthHttpIT.configuration(r);}
 @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired pbl6.ctsv.shared.files.ManagedFiles files;
 @Autowired pbl6.ctsv.mghp.application.SigningWorker worker;@Autowired pbl6.ctsv.mghp.application.SigningProvider provider;
 HttpClient http;String cv,sv,admin,pp,pht;long cvId,ppId,phtId;byte[] pdf;
 record Reply(int status,String tag,JsonNode body,String location){Reply expect(int expected){assertThat(status).as("HTTP %s",body).isEqualTo(expected);return this;}}
 Reply call(String method,String path,Object body,String token,String tag)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(25));
  if(token!=null)b.header("Authorization","Bearer "+token);if(tag!=null)b.header("If-Match",tag);
  if(body!=null)b.header("Content-Type","application/json");if(path.startsWith("/api/xac-thuc/"))b.header("Origin",AuthHttpIT.ORIGIN).header("X-CSRF-Protection","1");
  b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
  var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());return new Reply(r.statusCode(),r.headers().firstValue("ETag").orElse(null),r.body().isBlank()?JSON.nullNode():JSON.readTree(r.body()),r.headers().firstValue("Location").orElse(null));
 }
 long id(String sql,Object...args){return jdbc.queryForObject(sql+";SELECT CAST(SCOPE_IDENTITY() AS bigint)",Long.class,args);}
 String[] identity(String role)throws Exception{
  UUID account=UUID.randomUUID();String username="p11."+account;var now=OffsetDateTime.now(ZoneOffset.UTC);
  jdbc.update("INSERT dbo.TaiKhoan(ID,TenDangNhap,TenDangNhapChuanHoa,MatKhauHash,ThoiDiemTao,ThoiDiemCapNhat) VALUES(?,?,?,?,?,?)",account.toString(),username,username,new AuthCredentials().hash(PASSWORD),now,now);
  jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'CTSV_USER'",account.toString());
  if(role.equals("admin"))jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'AUTH_ADMIN'",account.toString());
  long user=id("INSERT dbo.nguoi_dung(dia_chi_email,ho_ten,dang_hoat_dong) VALUES(?,N'P11 SYNTHETIC actor',1)",username+"@example.invalid");
  jdbc.update("INSERT dbo.dinh_danh_dang_nhap(nguoi_dung_id,ma_nha_cung_cap,ma_chu_the_dang_nhap,thoi_diem_xac_minh) VALUES(?,?,?,SYSUTCDATETIME())",user,"https://auth.ctsv.test",account.toString());
  if(role.startsWith("mghp_"))jdbc.update("INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he) SELECT ?,id,N'MGHP' FROM dbo.vai_tro WHERE ma=?",user,role);
  if(role.equals("sv"))id("INSERT dbo.sinh_vien(mssv,ho_ten,nguoi_dung_id) VALUES(?,N'P11 sinh viên synthetic',?)",username,user);
  return new String[]{""+user,call("POST","/api/xac-thuc/dang-nhap",Map.of("username",username,"password",PASSWORD),null,null).expect(200).body.path("accessToken").asText()};
 }
 @BeforeAll void setup()throws Exception{
  http=HttpClient.newBuilder().sslContext(AuthHttpIT.testTls).build();var a=identity("mghp_chuyen_vien");cvId=Long.parseLong(a[0]);cv=a[1];sv=identity("sv")[1];admin=identity("admin")[1];
  a=identity("mghp_pho_phong");ppId=Long.parseLong(a[0]);pp=a[1];a=identity("mghp_pho_hieu_truong");phtId=Long.parseLong(a[0]);pht=a[1];
  System.setProperty("ctsv.mghp.signing.mode","TEST");
  for(String key:List.of("minutes-first","decision-first","last")){
   long user=key.equals("minutes-first")?cvId:key.equals("decision-first")?ppId:phtId;
   System.setProperty("ctsv.mghp.signing."+key+".user-id",""+user);
   System.setProperty("ctsv.mghp.signing."+key+".email",key.equals("last")?"signer-b@example.invalid":"signer-a@example.invalid");
   System.setProperty("ctsv.mghp.signing."+key+".authority-reference","P11 SYNTHETIC TEST consent configuration");
  }
  System.setProperty("ctsv.mghp.signing.category-id",UUID.randomUUID().toString());
  System.setProperty("ctsv.mghp.signing.provider-id",""+id("INSERT dbo.he_thong_ngoai(ma,ten,dang_hoat_dong) VALUES(?,N'P11 provider STUB',1)","P11-"+UUID.randomUUID()));
  try(var d=new org.apache.pdfbox.pdmodel.PDDocument();var out=new java.io.ByteArrayOutputStream()){d.addPage(new org.apache.pdfbox.pdmodel.PDPage());d.save(out);pdf=out.toByteArray();}
 }
 @AfterAll void close(){if(http!=null)http.close();System.clearProperty("ctsv.mghp.signing.mode");System.clearProperty("ctsv.mghp.signing.category-id");System.clearProperty("ctsv.mghp.signing.provider-id");for(String key:List.of("minutes-first","decision-first","last"))for(String field:List.of("user-id","email","authority-reference"))System.clearProperty("ctsv.mghp.signing."+key+"."+field);}
 record Bundle(long version,long minutes,String versionTag,String minutesTag){}
 Bundle bundle(boolean locked)throws Exception{
  String code="P11-"+UUID.randomUUID();long semester=id("INSERT dbo.hoc_ky(ma,ten) VALUES(?,N'P11 kỳ synthetic')",code);
  long round=id("INSERT dbo.mghp_dot_xet(ma_dot_xet,ten,hoc_ky_id,trang_thai) VALUES(?,N'P11 đợt synthetic',?,N'closed')",code,semester);
  long root=id("INSERT dbo.mghp_quyet_dinh(dot_xet_id,ma_qd,loai_qd,nguoi_tao_id) VALUES(?,?,N'initial',?)",round,code,cvId);
  long v=id("INSERT dbo.mghp_phien_ban_qd(quyet_dinh_id,lan_sua_doi,trang_thai,giai_doan,sha256_tap_du_lieu,so_dong,nguoi_tao_id,thoi_diem_chot) VALUES(?,1,?,N'after_meeting',?,0,?,?)",root,locked?"locked":"draft","a".repeat(64),cvId,locked?OffsetDateTime.now(ZoneOffset.UTC):null);
  long bb=id("INSERT dbo.mghp_bien_ban(dot_xet_id,ma_bb,phien_ban_qd_nguon_id,sha256_ds_hop,nguoi_tao_id) VALUES(?,?,?,?,?)",round,code,v,"a".repeat(64),cvId);
  var view=call("GET","/api/ctsv/mghp/phien-ban-quyet-dinh/"+v,null,cv,null).expect(200);
  return new Bundle(v,bb,view.tag,view.body.path("minutes").path("etag").asText());
 }
 @Test void documentBindingValidatesSourceAndPersistsPrivateVersion()throws Exception{
  var b=bundle(true);var file=files.store(UUID.randomUUID(),cvId,pdf,"P11 Biên bản.pdf");String path="/api/ctsv/mghp/bien-ban/"+b.minutes+"/van-ban";
  var body=Map.of("fileId",""+file.id(),"title","Biên bản xét học phí","evidence","P11 synthetic đã đối chiếu dữ liệu");
  call("POST",path,body,cv,null).expect(428);call("POST",path,body,sv,b.minutesTag).expect(403);call("POST",path,body,admin,b.minutesTag).expect(403);
  var saved=call("POST",path,body,cv,b.minutesTag).expect(201);assertThat(saved.body.path("file").path("sha256").asText()).isEqualTo(file.sha256());
  assertThat(saved.location).isEqualTo("/api/ctsv/phien-ban-van-ban/"+saved.body.path("id").asText());
  var read=call("GET","/api/ctsv/phien-ban-van-ban/"+saved.body.path("id").asText(),null,cv,null).expect(200);assertThat(read.body.path("datasetSha256").asText()).isEqualTo("a".repeat(64));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_lien_ket_phien_ban_van_ban WHERE bien_ban_id=?",Integer.class,b.minutes)).isEqualTo(1);
 }
 @Test void draftSourceCannotReceiveDecisionPdf()throws Exception{
  var b=bundle(false);var f=files.store(UUID.randomUUID(),cvId,pdf,"P11 Quyết định.pdf");
  call("POST","/api/ctsv/mghp/phien-ban-quyet-dinh/"+b.version+"/van-ban",Map.of("fileId",""+f.id(),"title","Quyết định","evidence","P11 synthetic"),cv,b.versionTag).expect(409);
 }
 @Test void missingFileIs404AndNeverAnInternalServerError()throws Exception{
  var b=bundle(true);call("POST","/api/ctsv/mghp/bien-ban/"+b.minutes+"/van-ban",Map.of("fileId","9223372036854775807","title","Biên bản","evidence","P11 missing file"),cv,b.minutesTag).expect(404);
 }
 @Test void boundPdfIsReadableByManagerButNeverStudentOrAuthAdmin()throws Exception{
  var b=bundle(true);var f=files.store(UUID.randomUUID(),cvId,pdf,"P11 riêng tư.pdf");
  call("POST","/api/ctsv/mghp/bien-ban/"+b.minutes+"/van-ban",Map.of("fileId",""+f.id(),"title","Biên bản riêng tư","evidence","P11 synthetic"),cv,b.minutesTag).expect(201);
  for(String token:List.of(sv,admin)){
   var res=http.send(HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+"/api/ctsv/tep/"+f.id()+"/noi-dung")).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
   assertThat(res.statusCode()).isIn(403,404);
  }
  var res=http.send(HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+"/api/ctsv/tep/"+f.id()+"/noi-dung")).header("Authorization","Bearer "+cv).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
  assertThat(res.statusCode()).isEqualTo(200);assertThat(res.body()).containsExactly(pdf);
 }
 @Test void changingPdfPreservesOldVersionAndRejectsStaleSourceTag()throws Exception{
  var b=bundle(true);String path="/api/ctsv/mghp/bien-ban/"+b.minutes+"/van-ban";
  var first=files.store(UUID.randomUUID(),cvId,pdf,"P11 gốc.pdf");var body=Map.of("fileId",""+first.id(),"title","Biên bản","evidence","P11 synthetic");
  var saved=call("POST",path,body,cv,b.minutesTag).expect(201);
  call("POST",path,body,cv,b.minutesTag).expect(412);
  var updated=call("GET","/api/ctsv/mghp/phien-ban-quyet-dinh/"+b.version,null,cv,null).expect(200);
  var next=files.store(UUID.randomUUID(),cvId,pdf,"P11 bản sửa.pdf");
  var replacement=call("POST",path,Map.of("fileId",""+next.id(),"title","Biên bản sửa","evidence","P11 sửa bản chuẩn bị"),cv,updated.body.path("minutes").path("etag").asText()).expect(201);
  assertThat(replacement.body.path("documentId")).isEqualTo(saved.body.path("documentId"));assertThat(replacement.body.path("version").asInt()).isEqualTo(2);
  assertThat(replacement.body.path("isCurrent").asBoolean()).isTrue();
  assertThat(call("GET","/api/ctsv/phien-ban-van-ban/"+saved.body.path("id").asText(),null,cv,null).expect(200).body.path("isCurrent").asBoolean()).isFalse();
  assertThat(call("GET","/api/ctsv/phien-ban-van-ban/"+saved.body.path("id").asText(),null,cv,null).expect(200).body.path("file").path("id").asText()).isEqualTo(""+first.id());
 }
 String document(Bundle b,boolean minutes)throws Exception{
  var f=files.store(UUID.randomUUID(),cvId,pdf,"P11 văn bản.pdf");var source=call("GET","/api/ctsv/mghp/phien-ban-quyet-dinh/"+b.version,null,cv,null).expect(200);return call("POST",minutes?"/api/ctsv/mghp/bien-ban/"+b.minutes+"/van-ban":"/api/ctsv/mghp/phien-ban-quyet-dinh/"+b.version+"/van-ban",Map.of("fileId",""+f.id(),"title",minutes?"Biên bản":"Quyết định","evidence","P11 synthetic"),cv,minutes?source.body.path("minutes").path("etag").asText():source.tag).expect(201).body.path("id").asText();
 }
 @Test void signingPlanAndCommandEnforceOwnStepGeometryEtagAndNoDuplicate()throws Exception{
  var b=bundle(true);String doc=document(b,true),prepare="/api/ctsv/phien-ban-van-ban/"+doc+"/yeu-cau-ky";
  call("POST",prepare,Map.of(),sv,null).expect(403);var request=call("POST",prepare,Map.of(),cv,null).expect(201);String path="/api/ctsv/yeu-cau-ky/"+request.body.path("id").asText();
  assertThat(request.body.path("steps").size()).isEqualTo(2);assertThat(request.body.path("steps").get(0).path("role").asText()).isEqualTo("chuyen_vien");
  call("GET",path,null,admin,null).expect(404);call("GET",path,null,pp,null).expect(404);call("GET",path,null,pht,null).expect(200);
  var action=Map.of("geometry",Map.of("page",1,"x",50,"y",80,"width",150,"height",45),"confirmReviewed",false);
  call("POST",path+"/gui-ky",action,cv,null).expect(428);call("POST",path+"/gui-ky",action,pht,call("GET",path,null,pht,null).expect(200).tag).expect(409);
  var queued=call("POST",path+"/gui-ky",action,cv,request.tag).expect(202);assertThat(queued.body.path("phase").asText()).isEqualTo("queued");
  call("POST",path+"/gui-ky",action,cv,request.tag).expect(412);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.lan_goi_dich_vu_ky WHERE yeu_cau_ky_id=?",Integer.class,request.body.path("id").asLong())).isEqualTo(1);
  call("POST","/api/ctsv/mghp/bien-ban/"+b.minutes+"/van-ban",Map.of("fileId",queued.body.path("inputFile").path("id").asText(),"title","Đổi khi đang ký","evidence","P11 synthetic"),cv,call("GET","/api/ctsv/mghp/phien-ban-quyet-dinh/"+b.version,null,cv,null).expect(200).body.path("minutes").path("etag").asText()).expect(409);
 }
 @Test void decisionCannotBeSentUntilMinutesStoredAndSignedByBothPeople()throws Exception{
  var b=bundle(true);String doc=document(b,false);call("POST","/api/ctsv/phien-ban-van-ban/"+doc+"/yeu-cau-ky",Map.of(),cv,null).expect(409);
 }
 @Test void concurrentDocumentBindingAndSignCommandHaveExactlyOneWinner()throws Exception{
  var b=bundle(true);var f=files.store(UUID.randomUUID(),cvId,pdf,"P11 concurrency.pdf");String path="/api/ctsv/mghp/bien-ban/"+b.minutes+"/van-ban";var body=Map.of("fileId",""+f.id(),"title","Biên bản","evidence","P11 race");
  try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var one=executor.submit(()->call("POST",path,body,cv,b.minutesTag));var two=executor.submit(()->call("POST",path,body,cv,b.minutesTag));var a=one.get();var c=two.get();assertThat(List.of(a.status,c.status)).containsExactlyInAnyOrder(201,412);
   var doc=(a.status==201?a:c).body.path("id").asText();var q=call("POST","/api/ctsv/phien-ban-van-ban/"+doc+"/yeu-cau-ky",Map.of(),cv,null).expect(201);String req="/api/ctsv/yeu-cau-ky/"+q.body.path("id").asText();
   var action=Map.of("geometry",Map.of("page",1,"x",30,"y",50,"width",150,"height",40),"confirmReviewed",false);
   var x=executor.submit(()->call("POST",req+"/gui-ky",action,cv,q.tag));var y=executor.submit(()->call("POST",req+"/gui-ky",action,cv,q.tag));assertThat(List.of(x.get().status,y.get().status)).containsExactlyInAnyOrder(202,412);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.lan_goi_dich_vu_ky WHERE yeu_cau_ky_id=?",Integer.class,q.body.path("id").asLong())).isEqualTo(1);
  }
 }
 @Test void bindingAuditFailureRollsBackDocumentLinkAndTechnicalFence()throws Exception{
  var b=bundle(true);var f=files.store(UUID.randomUUID(),cvId,pdf,"P11 rollback.pdf");String trigger="tr_p11_test_audit_"+UUID.randomUUID().toString().replace("-","");
  jdbc.execute("CREATE TRIGGER dbo."+trigger+" ON dbo.nhat_ky_thao_tac AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE hanh_dong=N'SIGNING_DOCUMENT_BIND') THROW 51990,N'P11 injected audit failure',1; END");
  try{call("POST","/api/ctsv/mghp/bien-ban/"+b.minutes+"/van-ban",Map.of("fileId",""+f.id(),"title","Biên bản","evidence","P11 rollback"),cv,b.minutesTag).expect(500);}finally{jdbc.execute("DROP TRIGGER dbo."+trigger);}
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_lien_ket_phien_ban_van_ban WHERE bien_ban_id=?",Integer.class,b.minutes)).isZero();
  assertThat(call("GET","/api/ctsv/mghp/phien-ban-quyet-dinh/"+b.version,null,cv,null).expect(200).body.path("minutes").path("etag").asText()).isEqualTo(b.minutesTag);
 }
 Reply prepare(String doc)throws Exception{return call("POST","/api/ctsv/phien-ban-van-ban/"+doc+"/yeu-cau-ky",Map.of(),cv,null).expect(201);}
 Reply queue(String path,String token,boolean ack)throws Exception{return call("POST",path+"/gui-ky",Map.of("geometry",Map.of("page",1,"x",40,"y",60,"width",150,"height",45),"confirmReviewed",ack),token,call("GET",path,null,token,null).expect(200).tag).expect(202);}
 Reply runStep(String path,String token,boolean ack)throws Exception{queue(path,token,ack);assertThat(worker.runRequest(Long.parseLong(path.substring(path.lastIndexOf('/')+1)))).isTrue();return call("GET",path,null,token,null).expect(200);}
 @Test void twoActorsStoreEveryPdfBeforeNextStepAndDecisionRequiresAtomicPpAck()throws Exception{
  var b=bundle(true);String bbDoc=document(b,true);var bb=prepare(bbDoc);String path="/api/ctsv/yeu-cau-ky/"+bb.body.path("id").asText();
  var first=runStep(path,cv,false);assertThat(first.body.path("status").asText()).isEqualTo("waiting_next");assertThat(first.body.path("steps").get(0).path("outputFile").path("state").asText()).isEqualTo("ready");assertThat(first.body.path("steps").get(1).path("status").asText()).isEqualTo("waiting");
  var inbox=call("GET","/api/ctsv/toi/thong-bao?size=100",null,pht,null).expect(200);assertThat(inbox.body.toString()).contains("/mghp/ky-so?yeu-cau="+bb.body.path("id").asText());
  long firstFile=first.body.path("steps").get(0).path("outputFile").path("id").asLong();var second=runStep(path,pht,false);assertThat(second.body.path("status").asText()).isEqualTo("completed");long finalFile=second.body.path("steps").get(1).path("outputFile").path("id").asLong();assertThat(finalFile).isNotEqualTo(firstFile);
  assertThat(pbl6.ctsv.shared.ca.CaPdfSignatureVerifier.verify(files.read(firstFile).bytes()).signatures()).hasSize(1);assertThat(pbl6.ctsv.shared.ca.CaPdfSignatureVerifier.verify(files.read(finalFile).bytes()).signatures()).hasSize(2);
  var qd=prepare(document(b,false));String qpath="/api/ctsv/yeu-cau-ky/"+qd.body.path("id").asText();
  call("GET","/api/ctsv/phien-ban-van-ban/"+bbDoc,null,pp,null).expect(200);
  call("POST",qpath+"/gui-ky",Map.of("geometry",Map.of("page",1,"x",40,"y",60,"width",150,"height",45),"confirmReviewed",false),pp,call("GET",qpath,null,pp,null).expect(200).tag).expect(400);
  runStep(qpath,pp,true);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.ghi_nhan_xem_van_ban WHERE phien_ban_van_ban_id=? AND nguoi_nhan_xet_id=?",Integer.class,qd.body.path("documentVersionId").asLong(),ppId)).isEqualTo(1);
  assertThat(runStep(qpath,pht,false).body.path("status").asText()).isEqualTo("completed");
 }
 @Test void downloadFailureAfterReceiptResumesWithoutSigningAgain()throws Exception{
  var p=(ProviderStub)provider;var b=bundle(true);var q=prepare(document(b,true));String path="/api/ctsv/yeu-cau-ky/"+q.body.path("id").asText();int signs=p.signs;p.downloadFails=true;
  try{queue(path,cv,false);worker.runRequest(q.body.path("id").asLong());var pending=call("GET",path,null,cv,null).expect(200);assertThat(pending.body.path("status").asText()).isEqualTo("failed");assertThat(pending.body.path("steps").get(1).path("status").asText()).isEqualTo("waiting");assertThat(p.signs).isEqualTo(signs+1);}finally{p.downloadFails=false;}
  call("POST",path+"/doi-soat",Map.of("reason","P11 thử tải lại kết quả"),cv,call("GET",path,null,cv,null).expect(200).tag).expect(202);worker.runRequest(q.body.path("id").asLong());assertThat(p.signs).isEqualTo(signs+1);assertThat(call("GET",path,null,cv,null).expect(200).body.path("status").asText()).isEqualTo("waiting_next");
 }
 @Test void unknownSignatureIsNeverPostedAgainOrAdvanced()throws Exception{
  var p=(ProviderStub)provider;var q=prepare(document(bundle(true),true));String path="/api/ctsv/yeu-cau-ky/"+q.body.path("id").asText();queue(path,cv,false);p.unknownSign=true;int signs=p.signs;
  worker.runRequest(q.body.path("id").asLong());worker.runRequest(q.body.path("id").asLong());var result=call("GET",path,null,cv,null).expect(200);assertThat(result.body.path("status").asText()).isEqualTo("unknown");assertThat(p.signs).isEqualTo(signs+1);
  call("POST",path+"/doi-soat",Map.of("reason","Không có receipt để đối soát"),cv,result.tag).expect(409);assertThat(p.signs).isEqualTo(signs+1);
 }
 @Test void refusalIsOutOfP11ScopeForEveryRole()throws Exception{
  var q=prepare(document(bundle(true),true));String path="/api/ctsv/yeu-cau-ky/"+q.body.path("id").asText();
  for(String token:List.of(cv,pp,pht))call("POST",path+"/tu-choi",Map.of("reason","Owner removed refusal"),token,q.tag).expect(404);
  assertThat(q.body.path("allowedActions").toString()).doesNotContain("refuse");
 }
 @Test void listAndHistoryAreScopedAndKeepHandledDocuments()throws Exception{
  var b=bundle(true);var q=prepare(document(b,true));String id=q.body.path("id").asText();
  var phtList=call("GET","/api/ctsv/tai-lieu-ky?mine=true&size=100",null,pht,null).expect(200);
  assertThat(phtList.body.path("items").toString()).contains("\"id\":\""+id+"\"");
  var item=phtList.body.path("items").get(0);assertThat(item.has("inputFile")&&item.has("progress")&&item.has("allowedActions")).isTrue();
  assertThat(call("GET","/api/ctsv/tai-lieu-ky",null,admin,null).expect(403).body.path("code").asText()).isEqualTo("FORBIDDEN");
  call("GET","/api/ctsv/tai-lieu-ky?size=101",null,cv,null).expect(400);
  String path="/api/ctsv/yeu-cau-ky/"+id;runStep(path,cv,false);runStep(path,pht,false);
  assertThat(call("GET","/api/ctsv/tai-lieu-ky?status=completed&mine=true&size=100",null,pht,null).expect(200).body.path("items").toString()).contains("\"id\":\""+id+"\"");
  assertThat(call("GET",path,null,cv,null).expect(200).body.path("history").size()).isGreaterThanOrEqualTo(3);
 }
 @Test void authorizationRevocationStopsQueuedStepAndExpiredIntentDoesNotDispatchAgain()throws Exception{
  var q=prepare(document(bundle(true),true));String path="/api/ctsv/yeu-cau-ky/"+q.body.path("id").asText();queue(path,cv,false);var p=(ProviderStub)provider;int signs=p.signs;
  jdbc.update("UPDATE dbo.uy_quyen_ky SET trang_thai=N'revoked',thoi_diem_thu_hoi=SYSUTCDATETIME(),ly_do_thu_hoi=N'P11 test revoke' WHERE buoc_ky_id=?",q.body.path("steps").get(0).path("id").asLong());
  worker.runRequest(q.body.path("id").asLong());assertThat(p.signs).isEqualTo(signs);
  var next=prepare(document(bundle(true),true));String npath="/api/ctsv/yeu-cau-ky/"+next.body.path("id").asText();queue(npath,cv,false);
  jdbc.update("UPDATE dbo.lan_goi_dich_vu_ky SET giai_doan_xu_ly=N'sign_intent',lease_token=NEWID(),lease_den=DATEADD(minute,-1,SYSDATETIMEOFFSET()) WHERE yeu_cau_ky_id=?",next.body.path("id").asLong());
  worker.runRequest(next.body.path("id").asLong());assertThat(p.signs).isEqualTo(signs);assertThat(call("GET",npath,null,cv,null).expect(200).body.path("status").asText()).isEqualTo("unknown");
 }
 @Test void authorityCollectionCasAndRevocationCannotChangeSignerOrDispatchUnknown()throws Exception{
  var q=prepare(document(bundle(true),true));String step=q.body.path("steps").get(0).path("id").asText(),path="/api/ctsv/buoc-ky/"+step+"/can-cu-gui-ky";
  var before=call("GET",path,null,cv,null).expect(200);call("GET",path,null,sv,null).expect(403);
  var row=Map.of("type","send_for_selected_signer","authorizedSignerEmail","signer-a@example.invalid","evidenceReference","P11 supplemental consent","effectiveAt",Instant.now().minusSeconds(10).toString());
  call("POST",path,row,cv,null).expect(428);var added=call("POST",path,row,cv,before.tag).expect(201);
  call("POST",path,row,cv,before.tag).expect(412);
  call("POST","/api/ctsv/can-cu-gui-ky/"+added.body.path("id").asText()+"/thu-hoi",Map.of("reason","P11 bỏ căn cứ mới"),cv,added.tag).expect(200);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.uy_quyen_ky WHERE buoc_ky_id=? AND trang_thai=N'active'",Integer.class,Long.parseLong(step))).isEqualTo(1);
 }
 @Test void committedCloudOutputRecoversLinkEvenWhenCaLosesDocument()throws Exception{
  var p=(ProviderStub)provider;var q=prepare(document(bundle(true),true));String path="/api/ctsv/yeu-cau-ky/"+q.body.path("id").asText();queue(path,cv,false);
  String trigger="tr_p11_link_fault_"+UUID.randomUUID().toString().replace("-","");
  jdbc.execute("CREATE TRIGGER dbo."+trigger+" ON dbo.nhat_ky_thao_tac AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE hanh_dong=N'SIGNING_STEP_COMPLETED') THROW 51991,N'P11 link rollback fault',1; END");
  try{worker.runRequest(q.body.path("id").asLong());}finally{jdbc.execute("DROP TRIGGER dbo."+trigger);}
  int signs=p.signs;assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.luot_tai_tep u JOIN dbo.lan_goi_dich_vu_ky w ON w.ma_lien_ket_yeu_cau=u.id WHERE w.yeu_cau_ky_id=? AND u.trang_thai=N'committed'",Integer.class,q.body.path("id").asLong())).isEqualTo(1);
  p.downloadGone=true;try{call("POST",path+"/doi-soat",Map.of("reason","P11 khôi phục file CTSV đã lưu"),cv,call("GET",path,null,cv,null).expect(200).tag).expect(202);worker.runRequest(q.body.path("id").asLong());assertThat(call("GET",path,null,cv,null).expect(200).body.path("status").asText()).isEqualTo("waiting_next");}finally{p.downloadGone=false;}
  assertThat(p.signs).isEqualTo(signs);
 }
 @Test void twoWorkersHaveOneDispatchAndUnassignedPhtCannotReadFiles()throws Exception{
  var q=prepare(document(bundle(true),true));String path="/api/ctsv/yeu-cau-ky/"+q.body.path("id").asText();var stranger=identity("mghp_pho_hieu_truong");call("GET",path,null,stranger[1],null).expect(404);
  long file=q.body.path("inputFile").path("id").asLong();var response=http.send(HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+"/api/ctsv/tep/"+file+"/noi-dung")).header("Authorization","Bearer "+stranger[1]).GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertThat(response.statusCode()).isIn(403,404);
  queue(path,cv,false);var p=(ProviderStub)provider;int signs=p.signs;
  try(var ex=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){var a=ex.submit(()->worker.runRequest(q.body.path("id").asLong()));var b=ex.submit(()->worker.runRequest(q.body.path("id").asLong()));a.get();b.get();}
  assertThat(p.signs).isEqualTo(signs+1);assertThat(call("GET",path,null,cv,null).expect(200).body.path("status").asText()).isEqualTo("waiting_next");
 }
 @Test void roleManagerCanDiscoverPhtWithoutImplicitGrant()throws Exception{
  var manager=identity("admin");jdbc.update("INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he) SELECT ?,id,N'CTSV' FROM dbo.vai_tro WHERE ma=N'ctsv.roles.manage'",Long.parseLong(manager[0]));
  var choices=call("GET","/api/ctsv/nen-tang/vai-tro-mghp",null,manager[1],null).expect(200);assertThat(choices.body.toString()).contains("mghp_pho_hieu_truong");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.phan_quyen_nguoi_dung p JOIN dbo.vai_tro r ON r.id=p.vai_tro_id WHERE p.nguoi_dung_id=? AND r.ma=N'mghp_pho_hieu_truong'",Integer.class,Long.parseLong(manager[0]))).isZero();
 }
 @Test void workerKeepsAcceptedConsentAfterSessionVersionChanges()throws Exception{
  var own=identity("mghp_chuyen_vien");String old=System.getProperty("ctsv.mghp.signing.minutes-first.user-id");
  System.setProperty("ctsv.mghp.signing.minutes-first.user-id",own[0]);try{
   var q=prepare(document(bundle(true),true));String path="/api/ctsv/yeu-cau-ky/"+q.body.path("id").asText();queue(path,own[1],false);var p=(ProviderStub)provider;
   var uploaded=p.upload(pbl6.ctsv.shared.ca.CaPdfPreflight.inspect(pdf,new pbl6.ctsv.shared.ca.CaPdfPreflight.Box(1,30,40,150,45)),"P11 crash checkpoint",UUID.randomUUID());
   jdbc.update("UPDATE dbo.yeu_cau_ky SET van_ban_ben_ngoai_id=?,trang_thai=N'signing' WHERE id=?",uploaded.id().toString(),q.body.path("id").asLong());jdbc.update("UPDATE dbo.lan_goi_dich_vu_ky SET giai_doan_xu_ly=N'uploaded',lease_den=NULL WHERE yeu_cau_ky_id=?",q.body.path("id").asLong());
   int signs=p.signs,uploads=p.uploads;
   jdbc.update("UPDATE dbo.TaiKhoan SET PhienBanToken=PhienBanToken+1 WHERE ID=(SELECT ma_chu_the_dang_nhap FROM dbo.dinh_danh_dang_nhap WHERE nguoi_dung_id=?)",Long.parseLong(own[0]));
   worker.runRequest(q.body.path("id").asLong());assertThat(p.signs).isEqualTo(signs+1);assertThat(p.uploads).isEqualTo(uploads);assertThat(call("GET",path,null,cv,null).expect(200).body.path("status").asText()).isEqualTo("waiting_next");
  }finally{System.setProperty("ctsv.mghp.signing.minutes-first.user-id",old);}
 }
 @Test void browserPreflightAllowsOnlyConfiguredOriginAndExposesEtag()throws Exception{
  var req=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+"/api/ctsv/yeu-cau-ky/1/gui-ky")).header("Origin",AuthHttpIT.ORIGIN).header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","authorization,content-type,if-match").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build();
  var allowed=http.send(req,HttpResponse.BodyHandlers.ofString());assertThat(allowed.statusCode()).isEqualTo(200);assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains(AuthHttpIT.ORIGIN);assertThat(allowed.headers().firstValue("Access-Control-Expose-Headers").orElse("")).contains("ETag");
  var bad=HttpRequest.newBuilder(req.uri()).header("Origin","https://untrusted.example.invalid").header("Access-Control-Request-Method","POST").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build();assertThat(http.send(bad,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
 }
 @Test void knownCertificateFailureCanBeReleasedButSignerMustConfirmAgain()throws Exception{
  var p=(ProviderStub)provider;var q=prepare(document(bundle(true),true));String path="/api/ctsv/yeu-cau-ky/"+q.body.path("id").asText();queue(path,cv,false);int signs=p.signs,uploads=p.uploads;p.certificateUnavailable=true;
  try{worker.runRequest(q.body.path("id").asLong());}finally{p.certificateUnavailable=false;}
  assertThat(p.signs).isEqualTo(signs);assertThat(p.uploads).isEqualTo(uploads);var failed=call("GET",path,null,cv,null).expect(200);
  call("POST",path+"/doi-soat",Map.of("reason","CA đã khắc phục chứng thư; yêu cầu người ký xác nhận lại"),cv,failed.tag).expect(202);
  assertThat(worker.runRequest(q.body.path("id").asLong())).isFalse();assertThat(p.signs).isEqualTo(signs);
  assertThat(call("GET",path,null,cv,null).expect(200).body.path("allowedActions").toString()).contains("sign");
  runStep(path,cv,false);assertThat(p.signs).isEqualTo(signs+1);
 }
}
