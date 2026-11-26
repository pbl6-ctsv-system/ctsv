package pbl6.ctsv.auth;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import pbl6.ctsv.mghp.application.SigningProvider;
import pbl6.ctsv.mghp.application.SigningWorker;
import pbl6.ctsv.shared.files.ManagedFiles;
import static org.assertj.core.api.Assertions.*;

/** REAL HTTPS/Auth/JPA/SQL on approved support DB; CA and Cloudinary explicitly STUB. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,classes=SigningHttpIT.Config.class,
 properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.mghp.configuration.enabled=true","ctsv.mghp.applications.enabled=true","ctsv.files.enabled=true","ctsv.files.reconciliation.enabled=false","ctsv.mghp.signing.enabled=true","ctsv.mghp.signing.worker.enabled=false","ctsv.mghp.signing.support.monitor.enabled=false","server.address=127.0.0.1"})
@ActiveProfiles({"local","test"}) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfSystemProperty(named="signing.support.test.enabled",matches="true")
class SigningSupportHttpIT {
 static final String BASE="/api/ctsv/mghp/quan-tri-ky";
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r)throws Exception{
  AuthHttpIT.configuration(r);
  r.add("ctsv.mghp.signing.result-spool-dir",()->System.getProperty("java.io.tmpdir")+"/ctsv-signing-support-it-"+ProcessHandle.current().pid());
 }
 @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired ManagedFiles files;@Autowired SigningWorker worker;@Autowired SigningProvider provider;
 @Autowired org.springframework.context.ApplicationContext context;
 SigningHttpIT f;String operator;long operatorId;
 @BeforeAll void setup()throws Exception{
  f=new SigningHttpIT();f.port=port;f.jdbc=jdbc;f.files=files;f.worker=worker;f.provider=provider;f.setup();
  var account=f.identity("mghp_quan_tri_ky");operatorId=Long.parseLong(account[0]);operator=account[1];
  String suffix=UUID.randomUUID().toString();for(String key:List.of("minutes-first","decision-first","last"))System.setProperty("ctsv.mghp.signing."+key+".email",(key.equals("last")?"signer-b+":"signer-a+")+suffix+"@example.invalid");
 }
 @Test void certificateCheckPreservesLastGoodAndRenewSendsOnlyOnceThenReadsReplacement()throws Exception{
  var p=(SigningHttpIT.ProviderStub)provider;
  var saved=new HashMap<String,String>();for(String key:List.of("minutes-first","decision-first","last")){String property="ctsv.mghp.signing."+key+".email";saved.put(property,System.getProperty(property));System.setProperty(property,"cert-"+key+"-"+UUID.randomUUID()+"@example.invalid");}
  try{
  var list=f.call("GET",BASE+"/chung-thu",null,operator,null).expect(200);String id=list.body().path("items").get(0).path("id").asText(),path=BASE+"/chung-thu/"+id;
  var checked=f.call("POST",path+"/kiem-tra",Map.of(),operator,null).expect(200);String old=checked.body().path("certificateId").asText();assertThat(old).isNotBlank();
  p.certificateCheckFails=true;try{var failed=f.call("POST",path+"/kiem-tra",Map.of(),operator,null).expect(200);assertThat(failed.body().path("checkFailed").asBoolean()).isTrue();assertThat(failed.body().path("certificateId").asText()).isEqualTo(old);assertThat(failed.body().path("lastSuccessfulAt")).isEqualTo(checked.body().path("lastSuccessfulAt"));}finally{p.certificateCheckFails=false;}
  p.certificateUnavailable=true;try{f.call("POST",path+"/kiem-tra",Map.of(),operator,null).expect(200);}finally{p.certificateUnavailable=false;}
  // An authoritative empty active list is not a transport failure and must not invent expiry.
  var current=f.call("POST",path+"/kiem-tra",Map.of(),operator,null).expect(200);
  int before=p.renews;var sent=f.call("POST",path+"/gia-han",Map.of("reason","SYNTHETIC đề nghị gia hạn"),operator,current.tag()).expect(201);
  assertThat(sent.body().path("renewal").path("status").asText()).isEqualTo("pending");assertThat(p.renews).isEqualTo(before+1);
  f.call("POST",path+"/gia-han",Map.of("reason","SYNTHETIC retry"),operator,sent.tag()).expect(200);assertThat(p.renews).isEqualTo(before+1);
  p.renewalState="approved";p.replacement=true;
  try{var approved=f.call("POST",path+"/kiem-tra-gia-han",Map.of(),operator,null).expect(200);assertThat(approved.body().path("certificateId").asText()).isNotEqualTo(old);assertThat(approved.body().path("renewal").path("status").asText()).isEqualTo("approved");}
  finally{p.replacement=false;p.renewalState="pending";}
  }finally{saved.forEach(System::setProperty);}
 }
 @Autowired pbl6.ctsv.shared.files.PrivateFileStorage storage;
 @Test void downloadedPdfSurvivesCloudFailureAndAdminDeleteDoesNotMarkJobComplete()throws Exception{
  var p=(SigningHttpIT.ProviderStub)provider;var store=(SigningHttpIT.StorageStub)storage;
  var request=f.prepare(f.document(f.bundle(true),true));long id=request.body().path("id").asLong();String path="/api/ctsv/yeu-cau-ky/"+id;int before=p.signs;
  f.queue(path,f.cv,false);store.failUpload=true;try{assertThat(worker.runRequest(id)).isTrue();}finally{store.failUpload=false;}
  var list=f.call("GET",BASE+"/tep-luu-cho",null,operator,null).expect(200);
  var file=java.util.stream.StreamSupport.stream(list.body().path("items").spliterator(),false).filter(i->i.path("requestId").asText().equals(""+id)).findFirst().orElseThrow();
  assertThat(file.path("verified").asBoolean()).isTrue();assertThat(file.path("completed").asBoolean()).isFalse();assertThat(file.path("hadError").asBoolean()).isTrue();
  String trigger="tr_support_delete_"+UUID.randomUUID().toString().replace("-","");
  jdbc.execute("CREATE TRIGGER dbo."+trigger+" ON dbo.nhat_ky_thao_tac AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE hanh_dong=N'SIGNING_SPOOL_DELETE_INTENT') THROW 51997,N'Synthetic delete intent audit failure',1; END");
  try{f.call("POST",BASE+"/tep-luu-cho/"+file.path("operationId").asText()+"/xoa",Map.of("confirm",true),operator,null).expect(500);}finally{jdbc.execute("DROP TRIGGER dbo."+trigger);}
  var kept=f.call("GET",BASE+"/tep-luu-cho",null,operator,null).expect(200);assertThat(kept.body().path("items").toString()).contains(file.path("operationId").asText());
  f.call("POST",BASE+"/tep-luu-cho/"+file.path("operationId").asText()+"/xoa",Map.of("confirm",true),operator,null).expect(200);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.nhat_ky_thao_tac WHERE hanh_dong=N'SIGNING_SPOOL_DELETE_INTENT' AND doi_tuong_id=? AND nguoi_dung_id=?",Integer.class,file.path("operationId").asText(),operatorId)).isEqualTo(1);
  assertThat(f.call("GET",path,null,f.cv,null).expect(200).body().path("status").asText()).isEqualTo("needs_support");assertThat(p.signs).isEqualTo(before+1);assertThat(worker.runRequest(id)).isFalse();
 }
 @Test void diskErrorFencesWorkerEvenWhenSqlErrorTransactionFailsAndReplayStopsJob()throws Exception{
  var p=(SigningHttpIT.ProviderStub)provider;var q=f.prepare(f.document(f.bundle(true),true));long id=q.body().path("id").asLong();String path="/api/ctsv/yeu-cau-ky/"+id;f.queue(path,f.cv,false);
  String trigger="tr_support_error_"+UUID.randomUUID().toString().replace("-","");jdbc.execute("CREATE TRIGGER dbo."+trigger+" ON dbo.yeu_cau_ky AFTER UPDATE AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE trang_thai=N'failed') THROW 51998,N'Synthetic error transaction unavailable',1; END");
  p.downloadFails=true;try{assertThatThrownBy(()->worker.runRequest(id)).isInstanceOf(RuntimeException.class);}finally{p.downloadFails=false;jdbc.execute("DROP TRIGGER dbo."+trigger);}
  jdbc.update("UPDATE dbo.lan_goi_dich_vu_ky SET lease_den=NULL WHERE yeu_cau_ky_id=?",id);int signs=p.signs,downloads=p.downloads;
  assertThat(worker.runRequest(id)).isFalse();assertThat(p.signs).isEqualTo(signs);assertThat(p.downloads).isEqualTo(downloads);
  Class<?> type=Class.forName("pbl6.ctsv.mghp.application.SigningSupportJournal");var replay=type.getDeclaredMethod("replay");replay.setAccessible(true);replay.invoke(context.getBean(type));replay.invoke(context.getBean(type));
  assertThat(f.call("GET",path,null,f.cv,null).expect(200).body().path("status").asText()).isEqualTo("needs_support");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_su_co_ky i JOIN dbo.lan_goi_dich_vu_ky a ON a.id=i.lan_goi_id WHERE a.yeu_cau_ky_id=?",Integer.class,id)).isEqualTo(1);assertThat(p.downloads).isEqualTo(downloads);
 }
 @AfterAll void close(){if(f!=null)f.close();}
 @Test void inputStorageFailureIsRecordedBeforeAnyCaCall()throws Exception{
  var p=(SigningHttpIT.ProviderStub)provider;var store=(SigningHttpIT.StorageStub)storage;
  var q=f.prepare(f.document(f.bundle(true),true));String path="/api/ctsv/yeu-cau-ky/"+q.body().path("id").asText();int before=p.signs;
  store.failDownload=true;try{var result=f.queue(path,f.cv,false);assertThat(result.body().path("status").asText()).isEqualTo("needs_support");}finally{store.failDownload=false;}
  assertThat(p.signs).isEqualTo(before);
 }
 @Test void lostSignResponseCreatesOneIncidentAndNeverRepeatsPost()throws Exception{
  var p=(SigningHttpIT.ProviderStub)provider;var q=f.prepare(f.document(f.bundle(true),true));long id=q.body().path("id").asLong();String path="/api/ctsv/yeu-cau-ky/"+id;int before=p.signs;
  f.queue(path,f.cv,false);p.unknownSign=true;assertThat(worker.runRequest(id)).isTrue();assertThat(worker.runRequest(id)).isFalse();
  assertThat(p.signs).isEqualTo(before+1);assertThat(f.call("GET",path,null,f.cv,null).expect(200).body().path("status").asText()).isEqualTo("needs_support");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_su_co_ky i JOIN dbo.lan_goi_dich_vu_ky a ON a.id=i.lan_goi_id WHERE a.yeu_cau_ky_id=?",Integer.class,id)).isEqualTo(1);
 }
 @Test void existingSigningHappyPathPermissionAckAndConcurrencyStillWork()throws Exception{
  f.twoActorsStoreEveryPdfBeforeNextStepAndDecisionRequiresAtomicPpAck();
  f.concurrentDocumentBindingAndSignCommandHaveExactlyOneWinner();
  f.workerKeepsAcceptedConsentAfterSessionVersionChanges();
  f.browserPreflightAllowsOnlyConfiguredOriginAndExposesEtag();
 }
 @Test void sqlFailureAfterCloudKeepsPdfAndIncidentHistoryFailureRollsBack()throws Exception{
  var q=f.prepare(f.document(f.bundle(true),true));long id=q.body().path("id").asLong();String path="/api/ctsv/yeu-cau-ky/"+id;
  String trigger="tr_support_test_"+UUID.randomUUID().toString().replace("-","");f.queue(path,f.cv,false);
  jdbc.execute("CREATE TRIGGER dbo."+trigger+" ON dbo.nhat_ky_thao_tac AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE hanh_dong=N'SIGNING_STEP_COMPLETED') THROW 51995,N'Synthetic signing SQL failure',1; END");
  try{assertThat(worker.runRequest(id)).isTrue();}finally{jdbc.execute("DROP TRIGGER dbo."+trigger);}
  var all=f.call("GET",BASE+"/su-co?status=all&size=100",null,operator,null).expect(200);
  var issue=java.util.stream.StreamSupport.stream(all.body().path("items").spliterator(),false).filter(i->i.path("requestId").asText().equals(""+id)).findFirst().orElseThrow();String detailPath=BASE+"/su-co/"+issue.path("id").asText();
  var detail=f.call("GET",detailPath,null,operator,null).expect(200);assertThat(detail.body().path("code").asText()).isEqualTo("SIGNING_DATABASE_UNAVAILABLE");assertThat(detail.body().path("stage").asText()).isEqualTo("database");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.tep_ket_qua_ky WHERE yeu_cau_ky_id=?",Integer.class,id)).isZero();
  var list=f.call("GET",BASE+"/tep-luu-cho",null,operator,null).expect(200);assertThat(list.body().path("items").toString()).contains("\"requestId\":\""+id+"\"");
  jdbc.execute("CREATE TRIGGER dbo."+trigger+" ON dbo.nhat_ky_thao_tac AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE hanh_dong=N'SIGNING_SUPPORT_START') THROW 51996,N'Synthetic Admin audit failure',1; END");
  try{f.call("POST",detailPath+"/bat-dau",Map.of(),operator,detail.tag()).expect(500);}finally{jdbc.execute("DROP TRIGGER dbo."+trigger);}
  var unchanged=f.call("GET",detailPath,null,operator,null).expect(200);assertThat(unchanged.tag()).isEqualTo(detail.tag());assertThat(unchanged.body().path("history").size()).isEqualTo(detail.body().path("history").size());
 }
 @Test void separateGrantIsRequiredAndDoesNotGrantSigningOrPdfAccess()throws Exception{
  f.call("GET",BASE+"/su-co",null,null,null).expect(401);
  for(String token:List.of(f.sv,f.cv,f.pp,f.pht,f.admin))f.call("GET",BASE+"/su-co",null,token,null).expect(403);
  f.call("GET",BASE+"/su-co",null,operator,null).expect(200);
  var access=f.call("GET","/api/ctsv/nen-tang/toi/quyen-giao-dien",null,operator,null).expect(200);
  assertThat(access.body().path("mghpSigningAdmin").asBoolean()).isTrue();assertThat(access.body().path("mghpSigning").asBoolean()).isFalse();
  f.call("GET","/api/ctsv/tai-lieu-ky",null,operator,null).expect(403);
 }
 @Test void adminStartsNotesAndFinishesWithoutChangingFailedSigningFacts()throws Exception{
  var p=(SigningHttpIT.ProviderStub)provider;var bundle=f.bundle(true);var q=f.prepare(f.document(bundle,true));
  long request=q.body().path("id").asLong();String path="/api/ctsv/yeu-cau-ky/"+request;
  int signs=p.signs;p.downloadFails=true;
  try{f.queue(path,f.cv,false);assertThat(worker.runRequest(request)).isTrue();}finally{p.downloadFails=false;}
  var publicView=f.call("GET",path,null,f.cv,null).expect(200);
  assertThat(publicView.body().path("status").asText()).isEqualTo("needs_support");
  assertThat(publicView.body().has("lastErrorCode")).isFalse();assertThat(publicView.body().path("allowedActions").toString()).doesNotContain("reconcile");
  f.call("POST",path+"/doi-soat",Map.of("reason","retired"),f.cv,publicView.tag()).expect(410);
  var list=f.call("GET",BASE+"/su-co?status=all",null,operator,null).expect(200);
  var incident=java.util.stream.StreamSupport.stream(list.body().path("items").spliterator(),false).filter(i->i.path("requestId").asText().equals(""+request)).findFirst().orElseThrow();
  String issue=BASE+"/su-co/"+incident.path("id").asText();var detail=f.call("GET",issue,null,operator,null).expect(200);
  assertThat(detail.body().path("actorRole").asText()).isEqualTo("chuyen_vien");assertThat(detail.body().path("httpStatus").asInt()).isEqualTo(503);
  String state=jdbc.queryForObject("SELECT trang_thai FROM dbo.yeu_cau_ky WHERE id=?",String.class,request);
  long receipts=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.bien_nhan_ky WHERE yeu_cau_ky_id=?",Long.class,request);
  f.call("POST",issue+"/bat-dau",Map.of(),operator,null).expect(428);
  var started=f.call("POST",issue+"/bat-dau",Map.of(),operator,detail.tag()).expect(200);
  assertThat(started.body().path("status").asText()).isEqualTo("working");
  f.call("POST",issue+"/ket-thuc",Map.of(),operator,detail.tag()).expect(412);
  var noted=f.call("POST",issue+"/ghi-chu",Map.of("text","Đã trao đổi CA bên ngoài","reference","SYNTHETIC support"),operator,started.tag()).expect(200);
  var done=f.call("POST",issue+"/ket-thuc",Map.of(),operator,noted.tag()).expect(200);
  assertThat(done.body().path("status").asText()).isEqualTo("resolved");assertThat(done.body().path("closedAt").isNull()).isFalse();
  f.call("GET",issue,null,operator,null).expect(200);
  assertThat(jdbc.queryForObject("SELECT trang_thai FROM dbo.yeu_cau_ky WHERE id=?",String.class,request)).isEqualTo(state);
  assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.bien_nhan_ky WHERE yeu_cau_ky_id=?",Long.class,request)).isEqualTo(receipts);
  assertThat(p.signs).isEqualTo(signs+1);assertThat(worker.runRequest(request)).isFalse();
  assertThat(f.call("GET",path,null,f.cv,null).expect(200).body().toString()).doesNotContain("Đã trao đổi CA bên ngoài","CA_DOWNLOAD_UNAVAILABLE");
 }
}
