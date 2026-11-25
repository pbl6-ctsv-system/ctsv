package pbl6.ctsv.auth;
 
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

import static org.assertj.core.api.Assertions.*;

/** Real HTTPS, JWT/AUTH11, JPA and guarded SQL Server. No API/storage/provider response stubs. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 classes={StageHandoffHttpIT.Config.class},
 properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.mghp.configuration.enabled=true",
 "ctsv.mghp.applications.enabled=true","server.address=127.0.0.1"})
@ActiveProfiles({"local","test"}) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StageHandoffHttpIT {
 static final ObjectMapper JSON=new ObjectMapper();
 static final Instant NOW=Instant.parse("2026-11-20T02:00:00Z");
 static final AtomicReference<Instant> TIME=new AtomicReference<>(NOW);
 static final String BASE="/api/ctsv/mghp/dot-xet";
 static final String PASSWORD="SYNTHETIC Timeline "+UUID.randomUUID();
 @Configuration(proxyBeanMethods=false)
 @org.springframework.boot.autoconfigure.EnableAutoConfiguration
 @org.springframework.boot.autoconfigure.domain.EntityScan({"pbl6.ctsv.foundation","pbl6.ctsv.mghp"})
 @ComponentScan(basePackages={"pbl6.ctsv.auth","pbl6.ctsv.foundation","pbl6.ctsv.mghp","pbl6.ctsv.shared"},
  excludeFilters={@ComponentScan.Filter(type=FilterType.CUSTOM,classes=org.springframework.boot.context.TypeExcludeFilter.class),
   @ComponentScan.Filter(type=FilterType.REGEX,pattern=".*(?:Test|IT)(?:\\$.*)?")})
 static class Config {
  @Bean DataSource timelineSource() throws Exception {return pbl6.ctsv.mghp.configuration.StageHandoffDatabase.migrate();}
  @Bean @Primary Clock timelineClock(){return new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return Clock.fixed(instant(),z);}public Instant instant(){return TIME.get();}};}
 }
 @DynamicPropertySource static void configuration(DynamicPropertyRegistry r)throws Exception {AuthHttpIT.configuration(r);}
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired pbl6.ctsv.mghp.application.ReviewSourceAccess reviewSources;
 @Autowired org.springframework.transaction.PlatformTransactionManager manager;
 String cvToken,svToken,adminToken;long actor,semester,policy,svUser,adminUser,requirement,criterion;
 HttpClient transport;

 record Reply(int status,HttpHeaders headers,String body){
  Reply expect(int code){assertThat(status).as("HTTP response %s",body).isEqualTo(code);return this;}
  JsonNode json()throws Exception{return JSON.readTree(body);}
  String tag(){return headers.firstValue("ETag").orElseThrow();}
 }
 Reply send(String method,String path,Object body,String token,String tag)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
  if(token!=null)b.header("Authorization","Bearer "+token);if(tag!=null)b.header("If-Match",tag);
  if(body!=null)b.header("Content-Type","application/json");
  if(path.startsWith("/api/xac-thuc/"))b.header("Origin",AuthHttpIT.ORIGIN).header("X-CSRF-Protection","1");
  b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
  var r=transport.send(b.build(),HttpResponse.BodyHandlers.ofString());return new Reply(r.statusCode(),r.headers(),r.body());
 }
 long id(String sql,Object...args){return jdbc.queryForObject(sql+";SELECT CAST(SCOPE_IDENTITY() AS bigint)",Long.class,args);}
 String[] identity(String kind)throws Exception{
  UUID account=UUID.randomUUID();String name="timeline."+account;
  var created=NOW.atOffset(ZoneOffset.UTC);
  jdbc.update("INSERT dbo.TaiKhoan(ID,TenDangNhap,TenDangNhapChuanHoa,MatKhauHash,ThoiDiemTao,ThoiDiemCapNhat) VALUES(?,?,?,?,?,?)",account.toString(),name,name,new AuthCredentials().hash(PASSWORD),created,created);
  jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'CTSV_USER'",account.toString());
  if(kind.equals("admin"))jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'AUTH_ADMIN'",account.toString());
  long user=id("INSERT dbo.nguoi_dung(dia_chi_email,ho_ten,dang_hoat_dong) VALUES(?,?,1)",name+"@example.invalid",kind.equals("cv")?"Chuyên viên học phí thử nghiệm":"Người dùng thử nghiệm");
  jdbc.update("INSERT dbo.dinh_danh_dang_nhap(nguoi_dung_id,ma_nha_cung_cap,ma_chu_the_dang_nhap,thoi_diem_xac_minh) VALUES(?,?,?,SYSUTCDATETIME())",user,"https://auth.ctsv.test",account.toString());
  if(kind.equals("cv"))jdbc.update("INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he) SELECT ?,id,N'MGHP' FROM dbo.vai_tro WHERE ma=N'mghp_chuyen_vien'",user);
  if(kind.equals("sv"))id("INSERT dbo.sinh_vien(mssv,ho_ten,nguoi_dung_id) VALUES(?,N'Sinh viên lịch trình thử nghiệm',?)",name,user);
  var login=send("POST","/api/xac-thuc/dang-nhap",Map.of("username",name,"password",PASSWORD),null,null).expect(200);
  return new String[]{Long.toString(user),login.json().path("accessToken").asText()};
 }
 @BeforeAll void fixture()throws Exception{
  transport=HttpClient.newBuilder().sslContext(AuthHttpIT.testTls).build();
  var cv=identity("cv");actor=Long.parseLong(cv[0]);cvToken=cv[1];var sv=identity("sv");svUser=Long.parseLong(sv[0]);svToken=sv[1];var admin=identity("admin");adminUser=Long.parseLong(admin[0]);adminToken=admin[1];
  semester=id("INSERT dbo.hoc_ky(ma,ten,so_thu_tu,ngay_bat_dau,ngay_ket_thuc,trang_thai_xac_minh_moc,can_cu_xac_minh_moc) VALUES(?,N'Học kỳ thử nghiệm lịch MGHP',?,?,?,N'verified',N'SYNTHETIC timeline calendar')","HAND25-"+UUID.randomUUID(),10000+new Random().nextInt(1000000),java.sql.Date.valueOf("2026-09-01"),java.sql.Date.valueOf("2027-01-31"));
  long parent=jdbc.queryForObject("SELECT id FROM dbo.mghp_chinh_sach WHERE ma=N'M2'",Long.class);
  int revision=jdbc.queryForObject("SELECT COALESCE(MAX(phien_ban),0)+1 FROM dbo.mghp_phien_ban_chinh_sach WHERE chinh_sach_id=?",Integer.class,parent);
  policy=id("INSERT dbo.mghp_phien_ban_chinh_sach(chinh_sach_id,phien_ban,ten_tai_thoi_diem_chot,ty_le_giam,ma_pham_vi,ngay_bat_dau_hieu_luc,trang_thai,tinh_trang_xac_nhan_quy_tac) VALUES(?,?,N'SYNTHETIC chính sách kiểm thử lịch',0.5,N'hoc_phi_chuong_trinh_chinh','2026-01-01',N'draft',N'da_doi_chieu_nguon')",parent,revision);
  long group=id("INSERT dbo.mghp_nhom_yeu_cau_giay_to(phien_ban_chinh_sach_id,ma,ten,so_yeu_cau_toi_thieu_dat) VALUES(?,N'TL',N'Giấy thử nghiệm',1)",policy);
  requirement=id("INSERT dbo.mghp_giay_to_bat_buoc(nhom_id,ma,ten,loai_giay_to,chu_the_minh_chung) VALUES(?,N'TL',N'Giấy thử nghiệm',N'xac_nhan_khuyet_tat',N'sinh_vien')",group);
  criterion=id("INSERT dbo.mghp_tieu_chi_chinh_sach(phien_ban_chinh_sach_id,ma,ten,bat_buoc,co_dieu_kien_ap_dung) VALUES(?,N'TL',N'Đối chiếu căn cứ',1,0)",policy);
  jdbc.update("UPDATE dbo.mghp_phien_ban_chinh_sach SET trang_thai=N'locked',thoi_diem_khoa=? WHERE id=?",NOW.atOffset(ZoneOffset.UTC),policy);
 }
 @BeforeEach void restoreTime(){TIME.set(NOW);}
 @AfterAll void closeTransport(){if(transport!=null)transport.close();}
 ObjectNode plan(){var n=JSON.createObjectNode();for(var f:List.of("supplementPlannedStartAt","supplementPlannedEndAt","reviewPlannedStartAt","councilPlannedDate","signingPlannedStartAt","signingPlannedEndAt","publicationPlannedStartAt","publicationPlannedEndAt"))n.putNull(f);return n;}
 ObjectNode content(){var n=JSON.createObjectNode();n.put("code","HAND25-"+UUID.randomUUID());n.put("semesterId",""+semester);n.put("name","Đợt hỗ trợ học phí học kỳ I — thử nghiệm");n.putNull("description");n.put("opensAt",NOW.minusSeconds(86400).toString());n.put("submissionDeadline",NOW.plusSeconds(10).toString());n.put("reviewDeadline",NOW.plusSeconds(172800).toString());n.put("allowRetroactive",true);n.put("retroactiveMode","theo_phap_luat");n.put("midSemesterMode","khoang_ngay_chuyen_vien_xac_nhan");n.putArray("policyVersions").addObject().put("policyVersionId",""+policy).putNull("applicationTemplateFileId").put("order",1);return n;}
 String create()throws Exception{return BASE+"/"+send("POST",BASE,content(),cvToken,null).expect(201).json().path("id").asText();}
 Reply read(String path)throws Exception{return send("GET",path,null,cvToken,null).expect(200);}
 Reply action(String path,String suffix)throws Exception{return send("POST",path+suffix,Map.of("reason","Kiểm tra lịch SYNTHETIC"),cvToken,read(path).tag());}
 record Source(long root,long sub,long process,long doc,ObjectNode evaluation) {}
 Source source(String path)throws Exception{return source(path,null);}
 Source source(String path,Long existingProcess)throws Exception{
  long round=Long.parseLong(path.substring(path.lastIndexOf('/')+1));String code="HAND25-"+UUID.randomUUID();
  long sv=jdbc.queryForObject("SELECT id FROM dbo.sinh_vien WHERE nguoi_dung_id=?",Long.class,svUser);
  long ethnic=id("INSERT dbo.dan_toc(ten) VALUES(N'Dân tộc thử nghiệm')"),unit=id("INSERT dbo.don_vi(ma,ten) VALUES(?,N'Khoa thử nghiệm')",code);
  long cohort=id("INSERT dbo.khoa_hoc(ma) VALUES(?)",code),cls=id("INSERT dbo.lop_sinh_hoat(ma,khoa_hoc_id,khoa_quan_ly_id) VALUES(?,?,?)",code,cohort,unit);
  long process=existingProcess==null?id("INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,khoa_hoc_id,hoc_ky_bat_dau_id,ma_qua_trinh_hoc,trang_thai_doi_chieu,thoi_diem_bat_dau) VALUES(?,?,?,?,N'verified','20260901')",sv,cohort,semester,code):existingProcess;
  long parent=jdbc.queryForObject("SELECT chinh_sach_id FROM dbo.mghp_phien_ban_chinh_sach WHERE id=?",Long.class,policy);
  long root=id("INSERT dbo.mghp_ho_so(ma_ho_so,dot_xet_id,sinh_vien_id,qua_trinh_hoc_id,chinh_sach_id,phien_ban_chinh_sach_id,trang_thai) VALUES(?,?,?,?,?,?,N'submitted')",code,round,sv,process,parent,policy);
  long sub=id("""
   INSERT dbo.mghp_phien_ban_ho_so(ho_so_id,lan_sua_doi,nguoi_nop_id,thoi_diem_nop,loai_lan_nop,dot_tiep_nhan_id,phien_ban_chinh_sach_ke_khai_id,ma_thao_tac_nop,
    ho_ten_khai_bao,so_cccd_khai_bao,ngay_sinh_khai_bao,noi_sinh_khai_bao,gioi_tinh_khai_bao,dan_toc_khai_bao_id,lop_khai_bao_id,mssv_khai_bao,
    khoa_hoc_khai_bao_id,khoa_quan_ly_khai_bao_id,dia_chi_thuong_tru_khai_bao,so_dien_thoai_khai_bao)
   VALUES(?,1,?,?,N'initial',?,?,NEWID(),N'Sinh viên kiểm thử',N'000000000000','20050820',N'Đà Nẵng',N'NAM',?,?,N'TL',?,?,N'Địa chỉ giả lập',N'0900000000')
   """,root,svUser,NOW.atOffset(ZoneOffset.UTC),round,policy,ethnic,cls,cohort,unit);
  jdbc.update("INSERT dbo.mghp_hoc_ky_de_nghi(phien_ban_ho_so_id,hoc_ky_id,phien_ban_chinh_sach_id) VALUES(?,?,?)",sub,semester,policy);
  long file=id("INSERT dbo.tep_dinh_kem(ten_tep,kieu_noi_dung,trang_thai_luu_tru,nguoi_so_huu_id,kieu_phan_phoi,kich_thuoc_tep,sha256,pham_vi_hien_thi) VALUES(N'SYNTHETIC-metadata-only.pdf',N'application/pdf',N'available',?,N'authenticated',100,REPLICATE('a',64),N'minh_chung')",svUser);
  // SQL-only metadata fixture for concurrency. No claim of real PDF upload or Cloudinary delivery.
  jdbc.update("INSERT dbo.luot_tai_tep(id,nguoi_dung_id,ten_cloud,ma_cong_khai,loai_tep,kich_thuoc,sha256,trang_thai,tep_id,ma_tai_nguyen,doi_soat_sau) VALUES(NEWID(),?,N'synthetic-no-storage',?,N'evidence',100,REPLICATE('a',64),N'committed',?,?,?)",svUser,"synthetic-timeline-"+file,file,"synthetic-"+file,NOW.atOffset(ZoneOffset.UTC));
  long doc=id("INSERT dbo.mghp_giay_to_ho_so(phien_ban_ho_so_id,giay_to_bat_buoc_id,nguoi_nop_id) VALUES(?,?,?)",sub,requirement,svUser);
  jdbc.update("INSERT dbo.mghp_tep_minh_chung(giay_to_ho_so_id,tep_dinh_kem_id) VALUES(?,?)",doc,file);
  var e=JSON.createObjectNode().put("applicationId",""+root).put("submissionId",""+sub).putNull("benefitId").putNull("updateSubmissionId")
   .put("processId",""+process).put("semesterId",""+semester).put("policyVersionId",""+policy).put("scopeCode","hoc_phi_chuong_trinh_chinh")
   .put("result","can_xac_minh").put("reason","Cần kiểm tra thêm căn cứ synthetic").putNull("timeEvidence").putNull("ageReferenceDate").putNull("birthDateUsed").putNull("statusHistoryId");
  e.putArray("criteria").addObject().put("criterionId",""+criterion).put("result","can_xac_minh").putNull("reasonCode").put("note","Chờ xác minh");
  e.putArray("evidence").addObject().put("documentId",""+doc).putNull("criterionId").putNull("from").putNull("to").put("result","can_xac_minh").put("reason","Chờ xác minh");
  e.putArray("eligiblePeriods");return new Source(root,sub,process,doc,e);
 }

 String start() throws Exception {
  String path=create();
  // SQL fixture starts an independently owned receiving round; DOT03 global sequencing is tested separately.
  jdbc.update("UPDATE dbo.mghp_dot_xet SET trang_thai=N'open',thoi_diem_khoa_cau_hinh=? WHERE id=?",NOW.atOffset(ZoneOffset.UTC),roundId(path));
  return path;
 }
 long roundId(String path){return Long.parseLong(path.substring(path.lastIndexOf('/')+1));}
 String context(String path,boolean online){return path+(online?"/chot-ho-so-online":"/dong-xet")+"?semesterId="+semester;}
 Reply seal(String path,boolean online,String tag) throws Exception {
  return send("POST",path+(online?"/chot-ho-so-online":"/dong-xet"),Map.of("semesterId",Long.toString(semester),"reason","SYNTHETIC chốt đúng đợt"),cvToken,tag);
 }
 void approve(Source f) throws Exception {
  var root="/api/ctsv/mghp/ho-so/"+f.root;
  send("POST","/api/ctsv/mghp/giay-to-da-nop/"+f.doc+"/kiem-tra",JSON.createObjectNode().put("kind","content").put("result","pass").putNull("note").putNull("replacesCheckId"),cvToken,read(root).tag()).expect(201);
  send("POST",root+"/ket-luan",Map.of("result","approved"),cvToken,read(root).tag()).expect(200);
 }
 @Test void STAGE01_oneUnresolvedApplicationBlocksWholeRoundWithoutClosingIntake() throws Exception {
  String path=start();var first=source(path);approve(first);source(path);
  var before=read(context(path,true));
  assertThat(before.json().path("eligibleCount").asInt()).isEqualTo(1);
  assertThat(before.json().path("blockerCount").asInt()).isEqualTo(1);
  seal(path,true,before.tag()).expect(409);
  assertThat(read(path).json().path("status").asText()).isEqualTo("open");
 }
 @Test void STAGE02_supplementBlocksEvenWhenAllRootsHaveTerminalIntakeStatus() throws Exception {
  String path=start();var f=source(path);approve(f);
  long request=id("INSERT dbo.mghp_yeu_cau_bo_sung(ho_so_id,so_vong,han_xu_ly,trang_thai,ly_do,phien_ban_lam_co_so_id,dot_tiep_nhan_id) VALUES(?,1,?,N'open',N'SYNTHETIC pending',?,?)",f.root,NOW.plusSeconds(86400).atOffset(ZoneOffset.UTC),f.sub,roundId(path));
  assertThat(request).isPositive();
  var c=read(context(path,true));assertThat(c.json().path("blockerCount").asInt()).isEqualTo(1);
  seal(path,true,c.tag()).expect(409);
 }
 @Test void STAGE03_sealClosesIntakePersistsHistoryAndIsIdempotentAfterReadback() throws Exception {
  String path=start();var f=source(path);approve(f);
  var c=read(context(path,true));assertThat(c.json().path("canSeal").asBoolean()).isTrue();
  var sealed=seal(path,true,c.tag()).expect(200);
  assertThat(read(path).json().path("status").asText()).isEqualTo("closed");
  assertThat(sealed.json().path("onlineClosedAt").asText()).isEqualTo(NOW.toString());
  assertThat(sealed.json().path("intakeSealed").asBoolean()).isTrue();
  long history=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_lich_su_lich_trinh WHERE dot_xet_id=? AND loai_thay_doi=N'CLOSE_ONLINE'",Long.class,roundId(path));
  seal(path,true,sealed.tag()).expect(200);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_lich_su_lich_trinh WHERE dot_xet_id=? AND loai_thay_doi=N'CLOSE_ONLINE'",Long.class,roundId(path))).isEqualTo(history).isEqualTo(1);
  assertThatThrownBy(()->jdbc.update("UPDATE dbo.mghp_dot_xet SET thoi_diem_chot_online=NULL WHERE id=?",roundId(path))).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
 @Test void STAGE04_scopePermissionAndEtagsAreRequiredAtBothHandoffs() throws Exception {
  String path=start();var c=read(context(path,true));
  for(String suffix:List.of("/chot-ho-so-online","/dong-xet")){
   send("GET",path+suffix,null,cvToken,null).expect(400);
   send("GET",path+suffix+"?semesterId="+(semester+10000000),null,cvToken,null).expect(400);
   for(var token:List.of(svToken,adminToken))send("GET",path+suffix+"?semesterId="+semester,null,token,null).expect(403);
   send("POST",path+suffix,Map.of("semesterId",""+semester,"reason","Test"),null,c.tag()).expect(401);
   send("POST",path+suffix,Map.of("reason","Test"),cvToken,c.tag()).expect(400);
   send("POST",path+suffix,Map.of("semesterId",semester,"reason","Test"),cvToken,c.tag()).expect(400);
  }
  seal(path,true,null).expect(428);seal(path,true,"\"stale\"").expect(412);
  send("POST",path+"/chot-ho-so-online",Map.of("semesterId",""+(semester+10000000),"reason","Test"),cvToken,c.tag()).expect(400);
 }
 @Test void STAGE05_unsealedDoesNotLeakIntoHardCopyListOrAllowDirectCommands() throws Exception {
  String path=start();var f=source(path);approve(f);
  var list=read("/api/ctsv/mghp/ho-so?stage=hard-copy&roundId="+roundId(path)+"&semesterId="+semester).json();
  assertThat(list.path("totalElements").asLong()).isZero();
  assertThat(read("/api/ctsv/mghp/ho-so/"+f.root).json().path("evaluationAllowed").asBoolean()).isFalse();
  send("POST","/api/ctsv/mghp/danh-gia",f.evaluation,cvToken,null).expect(409);
  send("POST","/api/ctsv/mghp/giay-to-da-nop/"+f.doc+"/xac-nhan-ban-cung",JSON.createObjectNode().put("received",true).putNull("note"),cvToken,read("/api/ctsv/mghp/ho-so/"+f.root).tag()).expect(409);
  send("GET","/api/ctsv/mghp/ho-so?stage=hard-copy",null,svToken,null).expect(403);
 }
 @Test void STAGE06_intakeWritesFreezeButPaperReviewRemainsAvailable() throws Exception {
  String path=start();var f=source(path);approve(f);seal(path,true,read(context(path,true)).tag()).expect(200);
  String root="/api/ctsv/mghp/ho-so/"+f.root;var current=read(root);
  assertThat(current.json().path("evaluationAllowed").asBoolean()).isTrue();
  send("POST",root+"/ket-luan",Map.of("result","rejected","reason","Should not change"),cvToken,current.tag()).expect(409);
  send("POST",root+"/yeu-cau-bo-sung",Map.of("deadline",NOW.plusSeconds(3600).toString(),"reason","No reopening"),cvToken,current.tag()).expect(409);
  send("POST","/api/ctsv/mghp/giay-to-da-nop/"+f.doc+"/xac-nhan-ban-cung",JSON.createObjectNode().put("received",true).putNull("note"),cvToken,current.tag()).expect(201);
  send("POST","/api/ctsv/mghp/danh-gia",f.evaluation,cvToken,null).expect(201);
 }
 String assess(Source f,String result) throws Exception {
  for(String kind:List.of("form","validity","subject"))send("POST","/api/ctsv/mghp/giay-to-da-nop/"+f.doc+"/kiem-tra",JSON.createObjectNode().put("kind",kind).put("result","pass").putNull("note").putNull("replacesCheckId"),cvToken,read("/api/ctsv/mghp/ho-so/"+f.root).tag()).expect(201);
  var body=f.evaluation.deepCopy().put("result",result).put("reason","SYNTHETIC confirmed").put("timeEvidence","Verified source interval");
  ((ObjectNode)body.path("criteria").get(0)).put("result",result);
  if(result.equals("dat")){
   ((ObjectNode)body.path("evidence").get(0)).put("result","dat").put("from","2026-09-01").put("to","2026-10-31");
   body.withArray("eligiblePeriods").addObject().put("from","2026-09-01").put("to","2026-10-31").put("evidence","SYNTHETIC verified interval");
  }
  var saved=send("POST","/api/ctsv/mghp/danh-gia",body,cvToken,null).expect(201);
  String evaluation="/api/ctsv/mghp/danh-gia/"+saved.json().path("id").asText();
  send("POST",evaluation+"/xac-nhan",Map.of("reason","SYNTHETIC confirmed"),cvToken,saved.tag()).expect(200);return evaluation;
 }
 @Test void STAGE07_paperSealUsesExistingClosureAndPreventsFurtherMutations() throws Exception {
  String path=start();var f=source(path);approve(f);seal(path,true,read(context(path,true)).tag()).expect(200);String e=assess(f,"dat");
  var c=read(context(path,false));assertThat(c.json().path("eligibleCount").asInt()).isEqualTo(1);
  seal(path,false,c.tag()).expect(200);
  assertThat(read("/api/ctsv/mghp/ho-so/"+f.root).json().path("workflowStatus").asText()).isEqualTo("awaiting_council");
  send("POST",e+"/phien-ban",Map.of("reason","Attempt reopen"),cvToken,read(e).tag()).expect(409);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_quyet_dinh WHERE dot_xet_id=?",Integer.class,roundId(path))).isZero();
 }
 @Test void STAGE08_changedSourceInvalidatesPreflightAndConcurrentSealHasOneCommit() throws Exception {
  String path=start();var f=source(path);approve(f);var old=read(context(path,true));
  var another=source(path);seal(path,true,old.tag()).expect(412);approve(another);
  var fresh=read(context(path,true));var latch=new java.util.concurrent.CountDownLatch(1);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var a=pool.submit(()->{latch.await();return seal(path,true,fresh.tag());});var b=pool.submit(()->{latch.await();return seal(path,true,fresh.tag());});latch.countDown();
   assertThat(List.of(a.get(25,java.util.concurrent.TimeUnit.SECONDS).status,b.get(25,java.util.concurrent.TimeUnit.SECONDS).status)).containsExactlyInAnyOrder(200,412);
  }
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_lich_su_lich_trinh WHERE dot_xet_id=? AND loai_thay_doi=N'CLOSE_ONLINE'",Integer.class,roundId(path))).isEqualTo(1);
 }
 @Test void STAGE09_auditFaultRollsBackAutomaticCloseAndMarker() throws Exception {
  String path=start();var f=source(path);approve(f);var c=read(context(path,true));
  jdbc.execute("CREATE TRIGGER dbo.tr_handoff_audit_fault ON dbo.nhat_ky_thao_tac AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE hanh_dong=N'ROUND_TIMELINE_CLOSE_ONLINE') THROW 51989,N'SYNTHETIC handoff audit fault',1; END");
  try{seal(path,true,c.tag()).expect(500);}finally{jdbc.execute("DROP TRIGGER dbo.tr_handoff_audit_fault");}
  var after=read(path).json();assertThat(after.path("status").asText()).isEqualTo("open");assertThat(after.path("onlineClosedAt").isNull()).isTrue();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_lich_su_lich_trinh WHERE dot_xet_id=? AND loai_thay_doi=N'CLOSE_ONLINE'",Integer.class,roundId(path))).isZero();
 }
 @Test void STAGE10_otherRoundAndLegacyHistoryRemainIndependent() throws Exception {
  String first=start(),second=start();var a=source(first);approve(a);var b=source(second);approve(b);
  seal(first,true,read(context(first,true)).tag()).expect(200);
  assertThat(read("/api/ctsv/mghp/ho-so/"+b.root).json().path("evaluationAllowed").asBoolean()).isFalse();
  send("POST","/api/ctsv/mghp/ho-so/"+b.root+"/ket-luan",Map.of("result","approved"),cvToken,read("/api/ctsv/mghp/ho-so/"+b.root).tag()).expect(200);
  jdbc.update("UPDATE dbo.mghp_dot_xet SET trang_thai=N'closed',nguoi_dong_xet_id=?,thoi_diem_dong_xet=?,sha256_nguon_dong_xet=REPLICATE('a',64) WHERE id=?",actor,NOW.atOffset(ZoneOffset.UTC),roundId(second));
  var legacy=read(context(second,true)).json();assertThat(legacy.path("intakeSealed").asBoolean()).isTrue();assertThat(legacy.path("onlineClosedAt").isNull()).isTrue();
 }
 @Test void STAGE11_hardCopyFilterCountsAndPagesUseSameSealedPool() throws Exception {
  String path=start();var a=source(path);var b=source(path);approve(a);approve(b);seal(path,true,read(context(path,true)).tag()).expect(200);
  String query="/api/ctsv/mghp/ho-so?stage=hard-copy&semesterId="+semester+"&roundId="+roundId(path)+"&size=1";
  var first=read(query+"&page=0").json();var second=read(query+"&page=1").json();
  assertThat(first.path("totalElements").asInt()).isEqualTo(2);assertThat(second.path("totalElements").asInt()).isEqualTo(2);
  assertThat(first.path("items").get(0).path("id").asText()).isNotEqualTo(second.path("items").get(0).path("id").asText());
 }
 @Test void STAGE12_rejectedWithdrawnAndUnknownAreExcludedWithoutRewritingConclusions() throws Exception {
  String path=start();var a=source(path);approve(a);var rejected=source(path);var withdrawn=source(path);
  jdbc.update("UPDATE dbo.mghp_ho_so SET trang_thai=N'rejected' WHERE id=?",rejected.root);
  jdbc.update("UPDATE dbo.mghp_ho_so SET trang_thai=N'withdrawn' WHERE id=?",withdrawn.root);
  var online=read(context(path,true));assertThat(online.json().path("excludedCount").asInt()).isEqualTo(2);seal(path,true,online.tag()).expect(200);
  String evaluation=assess(a,"can_xac_minh");var c=read(context(path,false));
  assertThat(c.json().path("eligibleCount").asInt()).isZero();assertThat(c.json().path("excludedCount").asInt()).isEqualTo(3);
  seal(path,false,c.tag()).expect(200);assertThat(read(evaluation).json().path("result").asText()).isEqualTo("can_xac_minh");
 }
 @Test void STAGE13_draftFutureAndCompletedRoundsDoNotOfferANewSeal() throws Exception {
  String path=create();assertThat(read(context(path,true)).json().path("canSeal").asBoolean()).isFalse();
  seal(path,true,read(context(path,true)).tag()).expect(409);
  jdbc.update("UPDATE dbo.mghp_dot_xet SET trang_thai=N'open',thoi_diem_mo=? WHERE id=?",NOW.plusSeconds(5).atOffset(ZoneOffset.UTC),roundId(path));
  seal(path,true,read(context(path,true)).tag()).expect(409);
  jdbc.update("UPDATE dbo.mghp_dot_xet SET trang_thai=N'completed' WHERE id=?",roundId(path));
  seal(path,true,read(context(path,true)).tag()).expect(409);
 }
 @Test void STAGE14_sourceAndClosureStillProduceAnImmutableCouncilDecision() throws Exception {
  String path=start();var f=source(path);approve(f);seal(path,true,read(context(path,true)).tag()).expect(200);assess(f,"dat");seal(path,false,read(context(path,false)).tag()).expect(200);
  var body=JSON.createObjectNode().put("roundId",""+roundId(path)).put("code","HAND25-QD-"+UUID.randomUUID()).put("type","initial").putNull("number").putNull("date").putNull("issuingUnitId");
  var draft=send("POST","/api/ctsv/mghp/quyet-dinh",body,cvToken,null).expect(201);
  String version="/api/ctsv/mghp/phien-ban-quyet-dinh/"+draft.json().path("id").asText();
  var meeting=JSON.createObjectNode().put("minutesCode","HAND25-BB-"+UUID.randomUUID()).putNull("reference").put("meetingAt",NOW.minusSeconds(1).toString()).put("place","SYNTHETIC meeting").put("reason","SYNTHETIC approved source");
  var recorded=send("POST",version+"/ket-luan-hop",meeting,cvToken,draft.tag()).expect(201);
  String current="/api/ctsv/mghp/phien-ban-quyet-dinh/"+recorded.json().path("id").asText();
  var locked=send("POST",current+"/xac-nhan",Map.of("reason","SYNTHETIC lock"),cvToken,recorded.tag()).expect(200);
  assertThat(locked.json().path("status").asText()).isEqualTo("locked");
  assertThat(read("/api/ctsv/mghp/ho-so/"+f.root).json().path("workflowStatus").asText()).isEqualTo("selected");
  assertThat(send("GET","/api/ctsv/mghp/ho-so/"+f.root,null,svToken,null).expect(200).json().path("workflowStatus").asText()).isEqualTo("awaiting_council");
 }
}


