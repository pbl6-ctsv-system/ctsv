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
import pbl6.ctsv.mghp.configuration.TimelineDatabase;
import static org.assertj.core.api.Assertions.*;

/** Real HTTPS, JWT/AUTH11, JPA and guarded SQL Server. No API/storage/provider response stubs. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 classes={TimelineHttpIT.Config.class},
 properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.mghp.configuration.enabled=true",
 "ctsv.mghp.applications.enabled=true","server.address=127.0.0.1"})
@ActiveProfiles({"local","test"}) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimelineHttpIT {
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
  @Bean DataSource timelineSource() throws Exception {return TimelineDatabase.migrate();}
  @Bean @Primary Clock timelineClock(){return new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return Clock.fixed(instant(),z);}public Instant instant(){return TIME.get();}};}
 }
 @DynamicPropertySource static void configuration(DynamicPropertyRegistry r)throws Exception {AuthHttpIT.configuration(r);if(System.getenv("TIMELINE_BROWSER_HOME")!=null){P05BrowserHarnessIT.browserProperties(r);String port=System.getenv("TIMELINE_BROWSER_FRONTEND_PORT");if(port!=null){if(!port.matches("[1-9][0-9]{3,4}"))throw new IllegalArgumentException("Invalid test frontend port");r.add("ctsv.auth.origins[0]",()->"https://127.0.0.1:"+port);}}}
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
  semester=id("INSERT dbo.hoc_ky(ma,ten,so_thu_tu,ngay_bat_dau,ngay_ket_thuc,trang_thai_xac_minh_moc,can_cu_xac_minh_moc) VALUES(?,N'Học kỳ thử nghiệm lịch MGHP',?,?,?,N'verified',N'SYNTHETIC timeline calendar')","TL-"+UUID.randomUUID(),10000+new Random().nextInt(1000000),java.sql.Date.valueOf("2026-09-01"),java.sql.Date.valueOf("2027-01-31"));
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
 ObjectNode content(){var n=JSON.createObjectNode();n.put("code","TL-"+UUID.randomUUID());n.put("semesterId",""+semester);n.put("name","Đợt hỗ trợ học phí học kỳ I — thử nghiệm");n.putNull("description");n.put("opensAt",NOW.minusSeconds(86400).toString());n.put("submissionDeadline",NOW.plusSeconds(10).toString());n.put("reviewDeadline",NOW.plusSeconds(172800).toString());n.put("allowRetroactive",true);n.put("retroactiveMode","theo_phap_luat");n.put("midSemesterMode","khoang_ngay_chuyen_vien_xac_nhan");n.putArray("policyVersions").addObject().put("policyVersionId",""+policy).putNull("applicationTemplateFileId").put("order",1);return n;}
 String create()throws Exception{return BASE+"/"+send("POST",BASE,content(),cvToken,null).expect(201).json().path("id").asText();}
 Reply read(String path)throws Exception{return send("GET",path,null,cvToken,null).expect(200);}
 Reply action(String path,String suffix)throws Exception{return send("POST",path+suffix,Map.of("reason","Kiểm tra lịch SYNTHETIC"),cvToken,read(path).tag());}
 @Test void createWithScheduleAndLegacyPublicProjection()throws Exception{
  var n=content();var p=plan();p.put("councilPlannedDate","2026-11-25");n.set("schedule",p);
  var r=send("POST",BASE,n,cvToken,null).expect(201);String path=BASE+"/"+r.json().path("id").asText();
  assertThat(r.json().path("schedule").path("councilPlannedDate").asText()).isEqualTo("2026-11-25");
  assertThat(read(path).json().path("reviewClosedAt").isNull()).isTrue();
  send("POST",path+"/mo",null,cvToken,r.tag()).expect(200);
  var publicView=send("GET",path,null,svToken,null).expect(200).json();
  assertThat(publicView.has("schedule")).isFalse();assertThat(publicView.has("reviewSourceSha256")).isFalse();
 }
 @Test void updatePlanUsesStrongTagAndKeepsLockedBusinessConfiguration()throws Exception{
  String path=create();send("POST",path+"/mo",null,cvToken,read(path).tag()).expect(200);var old=read(path);
  var p=plan();p.put("councilPlannedDate","2026-11-25");p.put("reviewDeadline",NOW.plusSeconds(259200).toString());
  var body=Map.of("schedule",p,"reason","Điều chỉnh kế hoạch họp thử nghiệm");
  send("PUT",path+"/lich-trinh",body,cvToken,null).expect(428);
  send("PUT",path+"/lich-trinh",body,cvToken,old.tag()).expect(200);
  send("PUT",path+"/lich-trinh",body,cvToken,old.tag()).expect(412);
  assertThat(read(path).json().path("schedule").path("councilPlannedDate").asText()).isEqualTo("2026-11-25");
  send("PUT",path,content(),cvToken,read(path).tag()).expect(409);
 }
 @Test void extensionWorksAfterOldDeadlineButNeverReopensClosed()throws Exception{
  String path=create();send("POST",path+"/mo",null,cvToken,read(path).tag()).expect(200);TIME.set(NOW.plusSeconds(20));
  var body=Map.of("submissionDeadline",NOW.plusSeconds(3600).toString(),"reason","Gia hạn thử nghiệm sau hạn cũ");
  send("POST",path+"/gia-han-nhan-ho-so",body,cvToken,read(path).tag()).expect(200);
  action(path,"/dong-nhan-ho-so").expect(200);
  send("POST",path+"/gia-han-nhan-ho-so",body,cvToken,read(path).tag()).expect(409);
 }
 @Test void progressIsServerDerivedWithoutAWriteTag()throws Exception{
  String path=create();var r=read(path+"/tien-do");assertThat(r.headers.firstValue("ETag")).isEmpty();
  assertThat(r.json().path("asOf").asText()).isEqualTo(NOW.toString());
  assertThat(r.json().path("warnings").isArray()).isTrue();
 }
 @Test void studentAndAuthAdminCannotReadPrivateTimelineOrMutate()throws Exception{
  String path=create();for(String token:List.of(svToken,adminToken)){
   send("GET",path+"/tien-do",null,token,null).expect(403);
   send("GET",path+"/dong-xet",null,token,null).expect(403);
   send("GET",path+"/lich-trinh/lich-su",null,token,null).expect(403);
   send("POST",path+"/gia-han-nhan-ho-so",Map.of("submissionDeadline",NOW.plusSeconds(50).toString(),"reason","Không được phép"),token,null).expect(403);
  }
 }
 @Test void historyIsAppendOnlyAndFaultRollsBackSchedule()throws Exception{
  String path=create();String id=path.substring(path.lastIndexOf('/')+1);var old=read(path);
  int before=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_lich_su_lich_trinh WHERE dot_xet_id=?",Integer.class,id);
  jdbc.execute("CREATE TRIGGER dbo.tr_tl_test_fault ON dbo.mghp_lich_su_lich_trinh AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE ly_do=N'TL-FAULT') THROW 51919,N'Synthetic history fault',1; END");
  try{var p=plan();p.put("reviewDeadline",NOW.plusSeconds(259200).toString());
   send("PUT",path+"/lich-trinh",Map.of("schedule",p,"reason","TL-FAULT"),cvToken,old.tag()).expect(500);
   assertThat(read(path).tag()).isEqualTo(old.tag());
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_lich_su_lich_trinh WHERE dot_xet_id=?",Integer.class,id)).isEqualTo(before);
  }finally{jdbc.execute("DROP TRIGGER dbo.tr_tl_test_fault");}
  assertThatThrownBy(()->jdbc.update("UPDATE dbo.mghp_lich_su_lich_trinh SET ly_do=N'Không được sửa' WHERE dot_xet_id=?",id)).hasMessageContaining("append-only");
 }
 @Test void closeReviewIsActualAndNeverCompletesUnpublishedRound()throws Exception{
  String path=create();send("POST",path+"/mo",null,cvToken,read(path).tag()).expect(200);action(path,"/dong-nhan-ho-so").expect(200);
  var pre=read(path+"/dong-xet");assertThat(pre.json().path("blockerCount").asInt()).isZero();
  send("POST",path+"/dong-xet",Map.of("reason","Đã rà soát tập nguồn thử nghiệm"),cvToken,read(path).tag()).expect(412);
  var closed=send("POST",path+"/dong-xet",Map.of("reason","Đã rà soát tập nguồn thử nghiệm"),cvToken,pre.tag()).expect(200);
  assertThat(closed.json().path("reviewClosedAt").asText()).isEqualTo(NOW.toString());
  action(path,"/hoan-tat").expect(409);
 }

 record Source(long root,long sub,long process,long doc,ObjectNode evaluation) {}
 Source source(String path)throws Exception{
  long round=Long.parseLong(path.substring(path.lastIndexOf('/')+1));String code="TL-"+UUID.randomUUID();
  long sv=jdbc.queryForObject("SELECT id FROM dbo.sinh_vien WHERE nguoi_dung_id=?",Long.class,svUser);
  long ethnic=id("INSERT dbo.dan_toc(ten) VALUES(N'Dân tộc thử nghiệm')"),unit=id("INSERT dbo.don_vi(ma,ten) VALUES(?,N'Khoa thử nghiệm')",code);
  long cohort=id("INSERT dbo.khoa_hoc(ma) VALUES(?)",code),cls=id("INSERT dbo.lop_sinh_hoat(ma,khoa_hoc_id,khoa_quan_ly_id) VALUES(?,?,?)",code,cohort,unit);
  long process=id("INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,khoa_hoc_id,hoc_ky_bat_dau_id,ma_qua_trinh_hoc,trang_thai_doi_chieu,thoi_diem_bat_dau) VALUES(?,?,?,?,N'verified','20260901')",sv,cohort,semester,code);
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
 @Test void sourcePreflightExcludesUnknownAndGuardsRevisionAfterClosing()throws Exception{
  String path=create();send("POST",path+"/mo",null,cvToken,read(path).tag()).expect(200);var f=source(path);
  var evaluation=send("POST","/api/ctsv/mghp/danh-gia",f.evaluation,cvToken,null).expect(201);
  String ep="/api/ctsv/mghp/danh-gia/"+evaluation.json().path("id").asText();
  send("POST",ep+"/xac-nhan",Map.of("reason","Đã xác định phần còn thiếu"),cvToken,evaluation.tag()).expect(200);
  action(path,"/dong-nhan-ho-so").expect(200);
  new org.springframework.transaction.support.TransactionTemplate(manager).execute(s->reviewSources.read(Long.parseLong(path.substring(path.lastIndexOf('/')+1))));
  var context=read(path+"/dong-xet");
  assertThat(context.json().path("excludedCount").asInt()).isEqualTo(1);assertThat(context.json().path("eligibleCount").asInt()).isZero();
  send("POST",path+"/dong-xet",Map.of("reason","Loại khỏi tập đủ điều kiện, giữ nguyên nhận định"),cvToken,context.tag()).expect(200);
  send("POST",ep+"/phien-ban",Map.of("reason","Không được sửa tập đã đóng"),cvToken,read(ep).tag()).expect(409);
  assertThat(read(ep).json().path("result").asText()).isEqualTo("can_xac_minh");
  String stranger=identity("sv")[1];
  send("POST","/api/ctsv/mghp/ho-so/"+f.root+"/rut",Map.of("reason","Không được lộ trạng thái hồ sơ người khác"),stranger,read("/api/ctsv/mghp/ho-so/"+f.root).tag()).expect(404);
  send("POST","/api/ctsv/mghp/giay-to-da-nop/"+f.doc+"/kiem-tra",Map.of("kind","content","result","pass","note","Không được sửa nguồn đã chốt","replacesCheckId",JSON.nullNode()),cvToken,read("/api/ctsv/mghp/ho-so/"+f.root).tag()).expect(409);
  send("POST","/api/ctsv/mghp/ho-so/"+f.root+"/yeu-cau-bo-sung",supplement(f,path),cvToken,read("/api/ctsv/mghp/ho-so/"+f.root).tag()).expect(409);
  String another=create();send("POST",another+"/mo",null,cvToken,read(another).tag()).expect(200);var otherSource=source(another);
  send("POST","/api/ctsv/mghp/danh-gia",otherSource.evaluation,cvToken,null).expect(201);
 }
 @Test void preflightDigestCoversEverySourceBeyondDisplayPage()throws Exception{
  String path=create();send("POST",path+"/mo",null,cvToken,read(path).tag()).expect(200);
  Source last=null;for(int i=0;i<21;i++)last=source(path);
  action(path,"/dong-nhan-ho-so").expect(200);var old=read(path+"/dong-xet");assertThat(old.json().path("blockerCount").asInt()).isEqualTo(21);
  assertThat(old.json().path("blockers").size()).isEqualTo(20);assertThat(old.json().path("hasMore").asBoolean()).isTrue();
  jdbc.update("UPDATE dbo.mghp_ho_so SET trang_thai=N'withdrawn' WHERE id=?",last.root);
  assertThat(read(path+"/dong-xet").tag()).isNotEqualTo(old.tag());
  send("POST",path+"/dong-xet",Map.of("reason","Tag cũ phải thất bại"),cvToken,old.tag()).expect(412);
 }
 @Test void verifiedPassIsEligibleAfterSoftReviewDeadlineAndAuditFailureCannotClose()throws Exception{
  String path=create();send("POST",path+"/mo",null,cvToken,read(path).tag()).expect(200);var f=source(path);
  // The deadline has passed but source evaluation is still allowed. Time passage never creates a legal conclusion.
  jdbc.update("UPDATE dbo.mghp_dot_xet SET han_ra_soat=? WHERE id=?",NOW.plusSeconds(10).atOffset(ZoneOffset.UTC),path.substring(path.lastIndexOf('/')+1));TIME.set(NOW.plusSeconds(20));
  for(var kind:List.of("form","content","validity","subject"))send("POST","/api/ctsv/mghp/giay-to-da-nop/"+f.doc+"/kiem-tra",Map.of("kind",kind,"result","pass","note","Đối chiếu fixture SQL","replacesCheckId",JSON.nullNode()),cvToken,read("/api/ctsv/mghp/ho-so/"+f.root).tag()).expect(201);
  var body=f.evaluation.deepCopy();body.put("result","dat").put("reason","Đã kiểm tra đủ căn cứ synthetic").put("timeEvidence","Khoảng thời gian được kiểm chứng trong fixture");
  ((ObjectNode)body.path("criteria").get(0)).put("result","dat");((ObjectNode)body.path("evidence").get(0)).put("result","dat").put("from","2026-09-01").put("to","2027-01-31");
  body.withArray("eligiblePeriods").addObject().put("from","2026-09-01").put("to","2027-01-31").put("evidence","Kỳ học và quá trình đã xác minh");
  var e=send("POST","/api/ctsv/mghp/danh-gia",body,cvToken,null).expect(201);String ep="/api/ctsv/mghp/danh-gia/"+e.json().path("id").asText();
  send("POST",ep+"/xac-nhan",Map.of("reason","Đủ căn cứ dù quá hạn kế hoạch"),cvToken,e.tag()).expect(200);action(path,"/dong-nhan-ho-so").expect(200);
  var pre=read(path+"/dong-xet");assertThat(pre.json().path("eligibleCount").asInt()).isEqualTo(1);assertThat(pre.json().path("blockerCount").asInt()).isZero();
  jdbc.execute("CREATE TRIGGER dbo.tr_tl_close_fault ON dbo.nhat_ky_thao_tac AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE hanh_dong=N'ROUND_TIMELINE_CLOSE_REVIEW') THROW 51921,N'Synthetic close audit failure',1;END");
  try{send("POST",path+"/dong-xet",Map.of("reason","Kiểm tra rollback marker"),cvToken,pre.tag()).expect(500);assertThat(read(path).json().path("reviewClosedAt").isNull()).isTrue();assertThat(read(path+"/dong-xet").tag()).isEqualTo(pre.tag());}
  finally{jdbc.execute("DROP TRIGGER dbo.tr_tl_close_fault");}
  send("POST",path+"/dong-xet",Map.of("reason","Chốt tập đủ căn cứ"),cvToken,pre.tag()).expect(200);
 }

 ObjectNode submission(Source f,String path){
  var row=jdbc.queryForMap("SELECT * FROM dbo.mghp_phien_ban_ho_so WHERE id=?",f.sub);
  long file=jdbc.queryForObject("SELECT tep_dinh_kem_id FROM dbo.mghp_tep_minh_chung WHERE giay_to_ho_so_id=?",Long.class,f.doc);
  String code=jdbc.queryForObject("SELECT s.mssv FROM dbo.sinh_vien s JOIN dbo.mghp_ho_so h ON h.sinh_vien_id=s.id WHERE h.id=?",String.class,f.root);
  var b=JSON.createObjectNode().put("operationId",UUID.randomUUID().toString()).put("roundId",path.substring(path.lastIndexOf('/')+1)).put("studyProcessId",""+f.process).put("policyVersionId",""+policy);
  b.putArray("requestedSemesterIds").add(""+semester);var c=b.putObject("claims");
  c.put("fullName","Sinh viên kiểm thử").put("citizenId","000000000000").put("birthDate","2005-08-20").put("birthPlace","Đà Nẵng").put("gender","NAM").put("studentCode",code)
   .put("ethnicityId",row.get("dan_toc_khai_bao_id").toString()).put("classId",row.get("lop_khai_bao_id").toString()).put("cohortId",row.get("khoa_hoc_khai_bao_id").toString())
   .put("managementUnitId",row.get("khoa_quan_ly_khai_bao_id").toString()).put("permanentAddress","Địa chỉ giả lập").put("phoneNumber","0900000000");
  b.putObject("statements").putNull("firstDegree").putNull("alreadyBenefitedAtSameLevel").putNull("salariedWhileStudying").put("declarationConfirmed",true);
  b.putArray("relatedPeople");b.putArray("documents").addObject().put("requirementId",""+requirement).put("fileId",""+file).putNull("documentNumber").putNull("issuer").putNull("issuedOn").putNull("expiresOn").putNull("relatedPersonIndex");return b;
 }
 ObjectNode supplement(Source f,String path){
  var b=JSON.createObjectNode().put("sourceVersionId",""+f.sub).put("receivingRoundId",path.substring(path.lastIndexOf('/')+1)).putNull("benefitId").put("updateType","initial_supplement").put("reason","Kiểm tra khai báo thử nghiệm").put("deadline",NOW.plusSeconds(3600).toString());
  b.putArray("items").addObject().putNull("requirementId").putNull("documentId").putNull("semesterId").putNull("evaluationId").put("declarationField","phoneNumber").put("type","declaration").put("reason","Kiểm tra số liên hệ");return b;
 }
 record Command(String method,String path,Object body,String token,String tag) {}
 @Test void legacyClientPreservesSavedPlanAndLegacyFirstChangeHasObservedBaseline()throws Exception{
  var n=content();n.set("schedule",plan().put("councilPlannedDate","2026-11-25"));var created=send("POST",BASE,n,cvToken,null).expect(201);
  String path=BASE+"/"+created.json().path("id").asText();n.remove("schedule");
  send("PUT",path,n,cvToken,created.tag()).expect(200);assertThat(read(path).json().path("schedule").path("councilPlannedDate").asText()).isEqualTo("2026-11-25");
  String code="TL-LEGACY-"+UUID.randomUUID();long old=id("INSERT dbo.mghp_dot_xet(ma_dot_xet,hoc_ky_id,ten,trang_thai,thoi_diem_tao) VALUES(?,?,N'Đợt cũ chưa lập lịch',N'draft','20200101')",code,semester);
  String legacy=BASE+"/"+old;assertThat(read(legacy).json().path("schedule").path("councilPlannedDate").isNull()).isTrue();
  var p=plan();p.putNull("reviewDeadline");send("PUT",legacy+"/lich-trinh",Map.of("schedule",p,"reason","Lập lịch cho đợt cũ"),cvToken,read(legacy).tag()).expect(200);
  var h=read(legacy+"/lich-trinh/lich-su").json();assertThat(h.path("totalElements").asInt()).isEqualTo(2);assertThat(h.path("items").get(1).path("revision").asInt()).isZero();
  assertThat(h.path("items").get(1).path("recordedAt").asText()).isEqualTo(NOW.toString());
 }
 @Test void extensionValidationAndScheduleOnlyWhitelistAreAtomic()throws Exception{
  String path=create();send("POST",path+"/mo",null,cvToken,read(path).tag()).expect(200);String tag=read(path).tag();
  for(var t:List.of(NOW,NOW.plusSeconds(10),NOW.plusSeconds(300000)))send("POST",path+"/gia-han-nhan-ho-so",Map.of("submissionDeadline",t.toString(),"reason","Kiểm tra hạn mới"),cvToken,tag).expect(400);
  var p=plan().put("reviewDeadline",NOW.plusSeconds(172800).toString()).put("opensAt",NOW.toString());
  send("PUT",path+"/lich-trinh",Map.of("schedule",p,"reason","Không được đổi cửa mở"),cvToken,tag).expect(400);
  send("GET",path+"/lich-trinh/lich-su?size=101",null,cvToken,null).expect(400);
  assertThat(read(path).tag()).isEqualTo(tag);
  send("GET",path+"/tien-do",null,null,null).expect(401);
 }
 /** Hold the real SQL transaction fence until both HTTP requests are blocked in SQL, then release together. */
 List<Reply> race(Command first,Command second)throws Exception{
  var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var started=new java.util.concurrent.CountDownLatch(2);
  // Warm the two real TLS connections before taking a short SQL barrier. Certificate handshake is not the race under test.
  for(var warm:pool.invokeAll(List.<java.util.concurrent.Callable<Reply>>of(()->send("GET",BASE,null,cvToken,null),()->send("GET",BASE,null,cvToken,null))))warm.get().expect(200);
  try(var barrier=jdbc.getDataSource().getConnection()){
   // Explicit BEGIN/COMMIT on this control connection; mixing JDBC implicit + explicit nested TX retains the fence.
   try(var st=barrier.createStatement()){st.execute("BEGIN TRANSACTION; DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=N'ctsv.mghp.configuration',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;IF @r<0 THROW 51920,N'Barrier failed',1;");}
   var tasks=List.of(first,second).stream().map(c->pool.submit(()->{started.countDown();return send(c.method,c.path,c.body,c.token,c.tag);})).toList();
   assertThat(started.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
   boolean waiting=false;long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
   while(System.nanoTime()<until){try(var st=barrier.createStatement();var rs=st.executeQuery("SELECT COUNT(*) FROM sys.dm_exec_requests WHERE database_id=DB_ID() AND session_id<>@@SPID AND blocking_session_id<>0")){rs.next();if(rs.getInt(1)>=2){waiting=true;break;}}Thread.sleep(15);}
   try(var st=barrier.createStatement()){st.execute("COMMIT TRANSACTION");}
   assertThat(waiting).as("Both actual requests reached the SQL barrier").isTrue();
   return List.of(tasks.get(0).get(20,java.util.concurrent.TimeUnit.SECONDS),tasks.get(1).get(20,java.util.concurrent.TimeUnit.SECONDS));
  }finally{pool.shutdownNow();}
 }
 @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"confirm","update","createBS","extendBS","respondBS","extendIntake","closeIntake"})
 void sevenSqlBarrierRaces(String variant)throws Exception{
  String path=create();send("POST",path+"/mo",null,cvToken,read(path).tag()).expect(200);var f=source(path);
  Command first,second;
  if(Set.of("extendIntake","closeIntake").contains(variant)){
   jdbc.update("UPDATE dbo.mghp_ho_so SET trang_thai=N'reopened' WHERE id=?",f.root);
   if(variant.equals("extendIntake"))TIME.set(NOW.plusSeconds(20));
   first=new Command("POST",path+(variant.equals("extendIntake")?"/gia-han-nhan-ho-so":"/dong-nhan-ho-so"),variant.equals("extendIntake")?Map.of("submissionDeadline",NOW.plusSeconds(3600).toString(),"reason","Gia hạn đồng thời"):Map.of("reason","Đóng nhận đồng thời"),cvToken,read(path).tag());
   String app="/api/ctsv/mghp/ho-so/"+f.root;
   second=new Command("POST",app+"/ban-nop",submission(f,path),svToken,send("GET",app,null,svToken,null).expect(200).tag());
   var replies=race(first,second);replies.get(0).expect(200);assertThat(replies.get(1).status).isIn(201,409);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_phien_ban_ho_so WHERE ho_so_id=?",Integer.class,f.root)).isEqualTo(replies.get(1).status==201?2:1);
  }else{
   String eval=null;Reply draft=null;Reply bs=null;String sp=null;
   if(Set.of("confirm","update").contains(variant)){draft=send("POST","/api/ctsv/mghp/danh-gia",f.evaluation,cvToken,null).expect(201);eval="/api/ctsv/mghp/danh-gia/"+draft.json().path("id").asText();}
   if(Set.of("extendBS","respondBS").contains(variant)){bs=send("POST","/api/ctsv/mghp/ho-so/"+f.root+"/yeu-cau-bo-sung",supplement(f,path),cvToken,read("/api/ctsv/mghp/ho-so/"+f.root).tag()).expect(201);sp="/api/ctsv/mghp/yeu-cau-bo-sung/"+bs.json().path("id").asText();}
   action(path,"/dong-nhan-ho-so").expect(200);
   first=new Command("POST",path+"/dong-xet",Map.of("reason","Đóng xét đồng thời"),cvToken,read(path+"/dong-xet").tag());
   second=switch(variant){
    case "confirm"->new Command("POST",eval+"/xac-nhan",Map.of("reason","Kết luận cần xác minh"),cvToken,draft.tag());
    case "update"->new Command("PUT",eval,f.evaluation,cvToken,draft.tag());
    case "createBS"->new Command("POST","/api/ctsv/mghp/ho-so/"+f.root+"/yeu-cau-bo-sung",supplement(f,path),cvToken,read("/api/ctsv/mghp/ho-so/"+f.root).tag());
    case "extendBS"->new Command("PUT",sp+"/han-nop",Map.of("deadline",NOW.plusSeconds(7200).toString(),"reason","Gia hạn vòng đồng thời"),cvToken,bs.tag());
    default->new Command("POST",sp+"/phan-hoi",submission(f,path),svToken,send("GET",sp+"/phan-hoi",null,svToken,null).expect(200).tag());
   };
   var replies=race(first,second);assertThat(replies.get(0).status).isIn(409,412);replies.get(1).expect(Set.of("createBS","respondBS").contains(variant)?201:200);
   assertThat(read(path).json().path("reviewClosedAt").isNull()).isTrue();
  }
 }
}
