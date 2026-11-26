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
import pbl6.ctsv.mghp.application.DecisionDatabase;
import static org.assertj.core.api.Assertions.*;

/** Real HTTPS, JWT/AUTH11, JPA and guarded SQL Server. No API/storage/provider response stubs. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 classes={DecisionHttpIT.Config.class},
 properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.mghp.configuration.enabled=true",
 "ctsv.mghp.applications.enabled=true","server.address=127.0.0.1"})
@ActiveProfiles({"local","test"}) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DecisionHttpIT {
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
  @Bean DataSource timelineSource() throws Exception {return Boolean.getBoolean("p11.test.enabled")?pbl6.ctsv.mghp.application.SigningDatabase.migrate():Boolean.getBoolean("review.lifecycle.test.enabled")?pbl6.ctsv.mghp.application.ReviewLifecycleDatabase.migrate():DecisionDatabase.migrate();}
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
 record Source(long root,long sub,long process,long doc,ObjectNode evaluation) {}
 Source source(String path)throws Exception{return source(path,null);}
 Source source(String path,Long existingProcess)throws Exception{
  long round=Long.parseLong(path.substring(path.lastIndexOf('/')+1));String code="TL-"+UUID.randomUUID();
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


 record Prepared(String round,Source source,Reply draft){}
 Prepared prepared()throws Exception{return prepared(1,null);}
 Prepared prepared(int count,Long process)throws Exception{
  String round=create();send("POST",round+"/mo",null,cvToken,read(round).tag()).expect(200);Source f=null;
  for(int i=0;i<count;i++){f=source(round,process);assess(f,"2026-10-31");}
  closeReview(round);return new Prepared(round,f,null);
 }
 void assess(Source f,String until)throws Exception{
  for(var kind:List.of("form","content","validity","subject"))send("POST","/api/ctsv/mghp/giay-to-da-nop/"+f.doc+"/kiem-tra",Map.of("kind",kind,"result","pass","note","Đã đối chiếu fixture P10","replacesCheckId",JSON.nullNode()),cvToken,read("/api/ctsv/mghp/ho-so/"+f.root).tag()).expect(201);
  approveOnline(f);
  send("POST","/api/ctsv/mghp/giay-to-da-nop/"+f.doc+"/xac-nhan-ban-cung",JSON.createObjectNode().put("received",true).putNull("note"),cvToken,read("/api/ctsv/mghp/ho-so/"+f.root).tag()).expect(201);
  var body=f.evaluation.deepCopy();body.put("result","dat").put("reason","Đủ căn cứ synthetic P10").put("timeEvidence","Chỉ hưởng trong khoảng nguồn");
  ((ObjectNode)body.path("criteria").get(0)).put("result","dat");
  ((ObjectNode)body.path("evidence").get(0)).put("result","dat").put("from","2026-09-01").put("to",until);
  body.withArray("eligiblePeriods").addObject().put("from","2026-09-01").put("to",until).put("evidence","Đúng khoảng căn cứ, không lấp phần kỳ");
  var e=send("POST","/api/ctsv/mghp/danh-gia",body,cvToken,null).expect(201);
  send("POST","/api/ctsv/mghp/danh-gia/"+e.json().path("id").asText()+"/xac-nhan",Map.of("reason","Đã kiểm chứng P10"),cvToken,e.tag()).expect(200);
 }
 void closeReview(String round)throws Exception{
  action(round,"/dong-nhan-ho-so").expect(200);
  send("POST",round+"/dong-xet",Map.of("reason","Đủ căn cứ cho Hội đồng P10"),cvToken,read(round+"/dong-xet").tag()).expect(200);
 }
 ObjectNode decision(String round){return JSON.createObjectNode().put("roundId",round.substring(round.lastIndexOf('/')+1)).put("code","P10-"+UUID.randomUUID()).put("type","initial").putNull("number").putNull("date").putNull("issuingUnitId");}
 ObjectNode meeting(){return JSON.createObjectNode().put("minutesCode","P10-BB-"+UUID.randomUUID()).putNull("reference").put("meetingAt",NOW.minusSeconds(60).toString()).put("place","Phòng họp thử nghiệm").put("reason","Hội đồng đồng ý toàn bộ nguồn hợp lệ");}
 String vp(Reply r)throws Exception{return "/api/ctsv/mghp/phien-ban-quyet-dinh/"+r.json().path("id").asText();}
 Reply draft(Prepared p)throws Exception{return send("POST","/api/ctsv/mghp/quyet-dinh",decision(p.round),cvToken,null).expect(201);}
 @Test void requiresClosedReviewAndRealEligibleSource()throws Exception{
  send("POST","/api/ctsv/mghp/quyet-dinh",decision(create()),cvToken,null).expect(409);
  String empty=create();send("POST",empty+"/mo",null,cvToken,read(empty).tag()).expect(200);action(empty,"/dong-nhan-ho-so").expect(200);
  send("POST",empty+"/dong-xet",Map.of("reason","Không có nguồn"),cvToken,read(empty+"/dong-xet").tag()).expect(200);
  send("POST","/api/ctsv/mghp/quyet-dinh",decision(empty),cvToken,null).expect(409);
 }
 @Test void completeWorkflowClonesFksPreservesSnapshotsAndLocksSameVersion()throws Exception{
  var p=prepared();var b=decision(p.round);var d=send("POST","/api/ctsv/mghp/quyet-dinh",b,cvToken,null).expect(201);
  assertThat(d.json().path("revision").asInt()).isEqualTo(1);
  var line=d.json().path("lines").path("items").get(0);assertThat(line.path("periods").get(0).path("to").asText()).isEqualTo("2026-10-31");
  assertThat(line.path("rate").asText()).isEqualTo("0.500000000");
  send("POST","/api/ctsv/mghp/quyet-dinh",b,cvToken,null).expect(200);
  var m=meeting();m.putObject("metadata").put("number","123/P10").put("date","2026-11-19").putNull("issuingUnitId");
  var a=send("POST",vp(d)+"/ket-luan-hop",m,cvToken,d.tag()).expect(201);
  send("POST",vp(d)+"/ket-luan-hop",m,cvToken,d.tag()).expect(200);
  send("POST",vp(a)+"/ket-luan-hop",m,cvToken,a.tag()).expect(200);
  assertThat(a.json().path("revision").asInt()).isEqualTo(2);
  assertThat(a.json().path("lines").path("items").get(0).path("candidateId").asText()).isNotEqualTo(line.path("candidateId").asText());
  assertThat(read(vp(d)).json().path("number").isNull()).isTrue();
  var locked=send("POST",vp(a)+"/xac-nhan",Map.of("reason","Chốt đúng bộ dữ liệu"),cvToken,a.tag()).expect(200);
  assertThat(locked.json().path("status").asText()).isEqualTo("locked");assertThat(locked.json().path("revision").asInt()).isEqualTo(2);
  send("POST",vp(a)+"/xac-nhan",Map.of("reason","Retry"),cvToken,locked.tag()).expect(200);
  send("POST",vp(a)+"/ket-luan-hop",meeting(),cvToken,locked.tag()).expect(409);
  String root="/api/ctsv/mghp/quyet-dinh/"+d.json().path("decisionId").asText();
  var h=read(root+"/lich-su?page=0&size=1").json().path("versions");
  assertThat(h.path("totalElements").asInt()).isEqualTo(2);assertThat(h.path("hasMore").asBoolean()).isTrue();
  assertThat(read(root+"/lich-su?page=1&size=1").json().path("versions").path("items").get(0).path("number").isNull()).isTrue();
  assertThat(read(vp(a)+"/ung-vien?size=1").json().path("totalElements").asInt()).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_khoang_huong g JOIN dbo.mghp_dong_qd l ON l.id=g.dong_qd_id JOIN dbo.mghp_ung_vien_phien_ban_qd c ON c.id=g.ung_vien_id WHERE l.phien_ban_qd_id=? AND c.phien_ban_qd_id<>l.phien_ban_qd_id",Integer.class,a.json().path("id").asLong())).isZero();
  assertThatThrownBy(()->jdbc.update("UPDATE dbo.mghp_phien_ban_qd SET so_qd_luc_chot=N'wrong' WHERE id=?",a.json().path("id").asLong())).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE dbo.mghp_bien_ban SET dia_diem=N'wrong' WHERE phien_ban_qd_nguon_id=?",a.json().path("id").asLong())).isInstanceOf(org.springframework.dao.DataAccessException.class);
 }
 @Test void authorizationAndStrictRequests()throws Exception{
  for(var token:List.of(svToken,adminToken))for(var path:List.of("/api/ctsv/mghp/quyet-dinh","/api/ctsv/mghp/quyet-dinh/999999","/api/ctsv/mghp/phien-ban-quyet-dinh/999999"))
   send("GET",path,null,token,null).expect(403);
  send("GET","/api/ctsv/mghp/quyet-dinh",null,null,null).expect(401);
  send("POST","/api/ctsv/mghp/quyet-dinh",decision(create()),svToken,null).expect(403);
  send("GET","/api/ctsv/mghp/quyet-dinh?page=-1",null,cvToken,null).expect(400);
  send("GET","/api/ctsv/mghp/quyet-dinh?size=101",null,cvToken,null).expect(400);
  send("GET","/api/ctsv/mghp/quyet-dinh?foo=1",null,cvToken,null).expect(400);
  send("GET","/api/ctsv/mghp/phien-ban-quyet-dinh/999999",null,cvToken,null).expect(404);
  var b=decision(create());b.putArray("selections");send("POST","/api/ctsv/mghp/quyet-dinh",b,cvToken,null).expect(400);
 }
 @Test void missingStaleTagAndFutureActualDoNotWrite()throws Exception{
  var p=prepared();var d=draft(p);
  send("POST",vp(d)+"/ket-luan-hop",meeting(),cvToken,null).expect(428);
  send("POST",vp(d)+"/ket-luan-hop",meeting(),cvToken,"\"stale\"").expect(412);
  var future=meeting().put("meetingAt",NOW.plusSeconds(1).toString());send("POST",vp(d)+"/ket-luan-hop",future,cvToken,d.tag()).expect(400);
  send("POST",vp(d)+"/xac-nhan",Map.of("reason","Chưa họp"),cvToken,d.tag()).expect(409);
  assertThat(read(vp(d)).json().path("revision").asInt()).isEqualTo(1);
 }
 @Test void changedSourceDigestCannotConfirmOrRefresh()throws Exception{
  var p=prepared();var d=draft(p);
  jdbc.update("UPDATE dbo.sinh_vien SET ho_ten=N'Nguồn thay đổi sau chốt' WHERE nguoi_dung_id=?",svUser);
  send("POST",vp(d)+"/ket-luan-hop",meeting(),cvToken,d.tag()).expect(409);
 }
 @Test void auditFailureRollsBackRootAndMeetingAtomically()throws Exception{
  var p=prepared();var b=decision(p.round);
  jdbc.execute("CREATE TRIGGER dbo.tr_p10_audit_fault ON dbo.nhat_ky_thao_tac AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE hanh_dong IN(N'DECISION_CREATE',N'DECISION_MEETING',N'DECISION_CONFIRM')) THROW 51939,N'P10 synthetic audit failure',1; END");
  try{
   send("POST","/api/ctsv/mghp/quyet-dinh",b,cvToken,null).expect(500);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_quyet_dinh WHERE ma_qd=?",Integer.class,b.path("code").asText())).isZero();
  }finally{jdbc.execute("DROP TRIGGER dbo.tr_p10_audit_fault");}
  var d=send("POST","/api/ctsv/mghp/quyet-dinh",b,cvToken,null).expect(201);
  jdbc.execute("CREATE TRIGGER dbo.tr_p10_audit_fault ON dbo.nhat_ky_thao_tac AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE hanh_dong=N'DECISION_MEETING') THROW 51939,N'P10 synthetic audit failure',1; END");
  try{
   send("POST",vp(d)+"/ket-luan-hop",meeting(),cvToken,d.tag()).expect(500);
   assertThat(read("/api/ctsv/mghp/quyet-dinh/"+d.json().path("decisionId").asText()+"/lich-su").json().path("versions").path("totalElements").asInt()).isEqualTo(1);
  }finally{jdbc.execute("DROP TRIGGER dbo.tr_p10_audit_fault");}
 }
 @Test void concurrentCreationAndMeetingHaveOneWinnerAndNoDuplicateVersion()throws Exception{
  var p=prepared();var b=decision(p.round);
  var results=race(()->send("POST","/api/ctsv/mghp/quyet-dinh",b,cvToken,null));
  assertThat(results.stream().map(Reply::status)).containsExactlyInAnyOrder(201,200);
  var d=results.getFirst();String path=vp(d);String tag=read(path).tag();
  var m=meeting();var meetings=race(()->send("POST",path+"/ket-luan-hop",m,cvToken,tag));
  assertThat(meetings.stream().map(Reply::status)).containsExactlyInAnyOrder(201,200);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_phien_ban_qd WHERE quyet_dinh_id=?",Integer.class,d.json().path("decisionId").asLong())).isEqualTo(2);
 }
 @Test void concurrentConfirmLocksOnlyOnceAndWritesActualCouncilTime()throws Exception{
  var p=prepared();var d=draft(p);var after=send("POST",vp(d)+"/ket-luan-hop",meeting(),cvToken,d.tag()).expect(201);
  var replies=race(()->send("POST",vp(after)+"/xac-nhan",Map.of("reason","Chốt từ hai tab"),cvToken,after.tag()));
  assertThat(replies.stream().map(Reply::status)).containsExactlyInAnyOrder(200,412);
  long round=Long.parseLong(p.round.substring(p.round.lastIndexOf('/')+1));
  assertThat(jdbc.queryForObject("SELECT thoi_diem_hoi_dong_chot FROM dbo.mghp_dot_xet WHERE id=?",OffsetDateTime.class,round).toInstant()).isEqualTo(NOW);
  var progress=read(p.round+"/tien-do").json().path("stages");
  JsonNode council=null;for(var stage:progress)if(stage.path("code").asText().equals("council"))council=stage;
  assertThat(council).isNotNull();assertThat(council.path("status").asText()).isEqualTo("completed");
  assertThat(council.path("actualTime").asText()).isEqualTo(NOW.toString());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.nhat_ky_thao_tac WHERE hanh_dong=N'DECISION_CONFIRM' AND doi_tuong_id=?",Integer.class,after.json().path("id").asText())).isEqualTo(1);
 }
 @Test void confirmAuditFaultRollsBackRoundActualAndVersionLock()throws Exception{
  var p=prepared();var d=draft(p);var after=send("POST",vp(d)+"/ket-luan-hop",meeting(),cvToken,d.tag()).expect(201);
  jdbc.execute("CREATE TRIGGER dbo.tr_p10_confirm_fault ON dbo.nhat_ky_thao_tac AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE hanh_dong=N'DECISION_CONFIRM') THROW 51939,N'P10 synthetic confirm failure',1; END");
  try{
   send("POST",vp(after)+"/xac-nhan",Map.of("reason","Lỗi audit không được chốt dở"),cvToken,after.tag()).expect(500);
   assertThat(read(vp(after)).tag()).isEqualTo(after.tag());
   assertThat(read(p.round).json().path("councilClosedAt").isNull()||read(p.round).json().path("councilClosedAt").isMissingNode()).isTrue();
   assertThat(jdbc.queryForMap("SELECT thoi_diem_hoi_dong_chot FROM dbo.mghp_dot_xet WHERE id=?",p.round.substring(p.round.lastIndexOf('/')+1)).get("thoi_diem_hoi_dong_chot")).isNull();
  }finally{jdbc.execute("DROP TRIGGER dbo.tr_p10_confirm_fault");}
 }
 @Test void draftHistoryCannotBeOverwrittenAndDuplicateMinutesIsConflict()throws Exception{
  var d=draft(prepared());long id=d.json().path("id").asLong();
  assertThatThrownBy(()->jdbc.update("UPDATE dbo.mghp_dong_qd SET ho_ten_luc_chot=N'Wrong' WHERE phien_ban_qd_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE dbo.mghp_ung_vien_phien_ban_qd SET trang_thai_lua_chon=N'not_selected' WHERE phien_ban_qd_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
  var m=meeting();var a=send("POST",vp(d)+"/ket-luan-hop",m,cvToken,d.tag()).expect(201);
  m.put("reason","Một cuộc họp khác nhưng trùng mã không được tạo dở");
  send("POST",vp(a)+"/ket-luan-hop",m,cvToken,a.tag()).expect(409);
 }
 @Test void revokedCapabilityAndInvalidatedJwtAreDeniedWithoutLeakingData()throws Exception{
  var cv=identity("cv");long user=Long.parseLong(cv[0]);
  send("GET","/api/ctsv/mghp/quyet-dinh",null,cv[1],null).expect(200);
  jdbc.update("DELETE FROM dbo.phan_quyen_nguoi_dung WHERE nguoi_dung_id=?",user);
  send("GET","/api/ctsv/mghp/quyet-dinh",null,cv[1],null).expect(403);
  var another=identity("cv");
  jdbc.update("UPDATE a SET PhienBanToken=PhienBanToken+1 FROM dbo.TaiKhoan a JOIN dbo.dinh_danh_dang_nhap d ON d.ma_chu_the_dang_nhap=CONVERT(nvarchar(36),a.ID) WHERE d.nguoi_dung_id=?",another[0]);
  send("GET","/api/ctsv/mghp/quyet-dinh",null,another[1],null).expect(401);
 }
 @Test void allSourcesAreSelectedBeyondFirstReadPage()throws Exception{
  var p=prepared(21,null);var d=draft(p);
  assertThat(d.json().path("lineCount").asInt()).isEqualTo(21);
  assertThat(d.json().path("lines").path("items").size()).isEqualTo(20);
  assertThat(d.json().path("lines").path("totalElements").isIntegralNumber()).isTrue();
  assertThat(d.json().path("lines").path("totalElements").asInt()).isEqualTo(21);
  assertThat(d.json().path("lines").path("hasMore").asBoolean()).isTrue();
  var next=read(vp(d)+"/dong?page=1&size=20").json();assertThat(next.path("items").size()).isEqualTo(1);
  assertThat(read(vp(d)+"/ung-vien?page=1&size=20").json().path("items").size()).isEqualTo(1);
  assertThat(read("/api/ctsv/mghp/quyet-dinh?roundId="+p.round.substring(p.round.lastIndexOf('/')+1)).json().path("items").size()).isEqualTo(1);
 }
 @Test void alreadyPublishedGroupCannotEnterAnotherRound()throws Exception{
  var first=prepared();var d=draft(first);
  // Historical publication fixture only: this is not evidence of P12/CA publication.
  jdbc.update("INSERT dbo.mghp_cong_bo(phien_ban_qd_id,ma_cong_bo,sha256_tap_du_lieu) VALUES(?,?,?)",d.json().path("id").asLong(),"P10-HIST-"+UUID.randomUUID(),d.json().path("datasetSha256").asText());
  var second=prepared(1,first.source.process);
  send("POST","/api/ctsv/mghp/quyet-dinh",decision(second.round),cvToken,null).expect(409);
 }
 @Test void sqlHttpMaxChoosesSingleHigherMWithoutFillingMonths()throws Exception{
  long original=policy,originalRequirement=requirement,originalCriterion=criterion;
  long parent=jdbc.queryForObject("SELECT id FROM dbo.mghp_chinh_sach WHERE ma=N'M9'",Long.class);
  long low=id("INSERT dbo.mghp_phien_ban_chinh_sach(chinh_sach_id,phien_ban,ten_tai_thoi_diem_chot,ty_le_giam,ma_pham_vi,ngay_bat_dau_hieu_luc,trang_thai,tinh_trang_xac_nhan_quy_tac) SELECT ?,COALESCE(MAX(phien_ban),0)+1,N'SYNTHETIC P10 mức thấp cả kỳ',0.25,N'hoc_phi_chuong_trinh_chinh','2026-01-01',N'draft',N'da_doi_chieu_nguon' FROM dbo.mghp_phien_ban_chinh_sach WHERE chinh_sach_id=?",parent,parent);
  long g=id("INSERT dbo.mghp_nhom_yeu_cau_giay_to(phien_ban_chinh_sach_id,ma,ten,so_yeu_cau_toi_thieu_dat) VALUES(?,N'P10',N'Giấy synthetic P10',1)",low);
  long req=id("INSERT dbo.mghp_giay_to_bat_buoc(nhom_id,ma,ten,loai_giay_to,chu_the_minh_chung) VALUES(?,N'P10',N'Giấy synthetic',N'xac_nhan',N'sinh_vien')",g);
  long cr=id("INSERT dbo.mghp_tieu_chi_chinh_sach(phien_ban_chinh_sach_id,ma,ten,bat_buoc,co_dieu_kien_ap_dung) VALUES(?,N'P10',N'Căn cứ synthetic',1,0)",low);
  jdbc.update("UPDATE dbo.mghp_phien_ban_chinh_sach SET trang_thai=N'locked',thoi_diem_khoa=? WHERE id=?",NOW.atOffset(ZoneOffset.UTC),low);
  var n=content();n.withArray("policyVersions").addObject().put("policyVersionId",""+low).putNull("applicationTemplateFileId").put("order",2);
  String round=BASE+"/"+send("POST",BASE,n,cvToken,null).expect(201).json().path("id").asText();send("POST",round+"/mo",null,cvToken,read(round).tag()).expect(200);
  var high=source(round);assess(high,"2026-10-31");
  Source lower;try{policy=low;requirement=req;criterion=cr;lower=source(round,high.process);assess(lower,"2027-01-31");}
  finally{policy=original;requirement=originalRequirement;criterion=originalCriterion;}
  closeReview(round);var d=send("POST","/api/ctsv/mghp/quyet-dinh",decision(round),cvToken,null).expect(201).json();
  assertThat(d.path("candidates").path("totalElements").asInt()).isEqualTo(2);assertThat(d.path("lineCount").asInt()).isEqualTo(1);
  var l=d.path("lines").path("items").get(0);assertThat(l.path("policyVersionId").asLong()).isEqualTo(original);
  assertThat(l.path("rate").asText()).isEqualTo("0.500000000");assertThat(l.path("periods").size()).isEqualTo(1);assertThat(l.path("periods").get(0).path("to").asText()).isEqualTo("2026-10-31");
  var head=read("/api/ctsv/mghp/phien-ban-quyet-dinh/"+d.path("id").asText());
  var after=send("POST",vp(head)+"/ket-luan-hop",meeting(),cvToken,head.tag()).expect(201);
  send("POST",vp(after)+"/xac-nhan",Map.of("reason","Chốt mức cao nhất"),cvToken,after.tag()).expect(200);
  assertThat(read("/api/ctsv/mghp/ho-so/"+high.root).json().path("councilResult").asText()).isEqualTo("selected");
  assertThat(read("/api/ctsv/mghp/ho-so/"+lower.root).json().path("councilResult").asText()).isEqualTo("not_selected");
 }
 @Test void documentMetadataAndEtagStayBoundToTheirOwnVersion()throws Exception{
  var d=draft(prepared());var after=send("POST",vp(d)+"/ket-luan-hop",meeting(),cvToken,d.tag()).expect(201);
  // Metadata-only fixtures, not uploaded PDFs and not P11 signing evidence.
  long doc=id("INSERT dbo.van_ban(ma_van_ban,ma_phan_he,tieu_de) VALUES(NEWID(),N'MGHP',N'SYNTHETIC P10 metadata')");
  long first=id("INSERT dbo.phien_ban_van_ban(van_ban_id,so_phien_ban,sha256_tap_du_lieu) VALUES(?,1,?)",doc,d.json().path("datasetSha256").asText());
  long second=id("INSERT dbo.phien_ban_van_ban(van_ban_id,so_phien_ban,sha256_tap_du_lieu) VALUES(?,2,?)",doc,after.json().path("datasetSha256").asText());
  jdbc.update("INSERT dbo.mghp_lien_ket_phien_ban_van_ban(loai_van_ban,phien_ban_qd_id,phien_ban_van_ban_dung_chung_id) VALUES(N'quyet_dinh',?,?)",d.json().path("id").asLong(),first);
  jdbc.update("INSERT dbo.mghp_lien_ket_phien_ban_van_ban(loai_van_ban,phien_ban_qd_id,phien_ban_van_ban_dung_chung_id) VALUES(N'quyet_dinh',?,?)",after.json().path("id").asLong(),second);
  var a=read(vp(d));var b=read(vp(after));assertThat(a.tag()).isNotEqualTo(d.tag());
  assertThat(a.json().path("documents").size()).isEqualTo(1);assertThat(a.json().path("documents").get(0).path("documentVersionId").asLong()).isEqualTo(first);
  assertThat(b.json().path("documents").get(0).path("documentVersionId").asLong()).isEqualTo(second);
  var history=read("/api/ctsv/mghp/quyet-dinh/"+d.json().path("decisionId").asText()+"/lich-su").json().path("versions").path("items");
  assertThat(history.get(0).path("documents").get(0).path("documentVersionId").asLong()).isEqualTo(second);
  assertThat(history.get(1).path("documents").get(0).path("documentVersionId").asLong()).isEqualTo(first);
 }
 @Test void confirmedUnknownIsExcludedWithoutChangingItsConclusion()throws Exception{
  String round=create();send("POST",round+"/mo",null,cvToken,read(round).tag()).expect(200);assess(source(round),"2026-10-31");
  var unknown=source(round);approveOnline(unknown);var e=send("POST","/api/ctsv/mghp/danh-gia",unknown.evaluation,cvToken,null).expect(201);
  String path="/api/ctsv/mghp/danh-gia/"+e.json().path("id").asText();
  send("POST",path+"/xac-nhan",Map.of("reason","Giữ nhận định cần xác minh, không tự không đạt"),cvToken,e.tag()).expect(200);
  closeReview(round);var d=send("POST","/api/ctsv/mghp/quyet-dinh",decision(round),cvToken,null).expect(201).json();
  assertThat(d.path("lineCount").asInt()).isEqualTo(1);assertThat(d.path("candidates").path("totalElements").asInt()).isEqualTo(1);
  assertThat(read(path).json().path("result").asText()).isEqualTo("can_xac_minh");
 }
 @Test void confirmRacingSourceRevisionDoesNotChangeClosedSource()throws Exception{
  var p=prepared();var d=draft(p);var after=send("POST",vp(d)+"/ket-luan-hop",meeting(),cvToken,d.tag()).expect(201);
  String ep="/api/ctsv/mghp/danh-gia/"+d.json().path("lines").path("items").get(0).path("evaluationId").asText(),tag=read(ep).tag();
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var gate=new java.util.concurrent.CyclicBarrier(2);
   var confirm=pool.submit(()->{gate.await();return send("POST",vp(after)+"/xac-nhan",Map.of("reason","Chốt nguồn bất biến"),cvToken,after.tag());});
   var revision=pool.submit(()->{gate.await();return send("POST",ep+"/phien-ban",Map.of("reason","Không được thay nguồn sau đóng xét"),cvToken,tag);});
   confirm.get(40,java.util.concurrent.TimeUnit.SECONDS).expect(200);revision.get(40,java.util.concurrent.TimeUnit.SECONDS).expect(409);
  }
  assertThat(read(ep).tag()).isEqualTo(tag);assertThat(read(vp(after)).json().path("status").asText()).isEqualTo("locked");
 }
 List<Reply> race(java.util.concurrent.Callable<Reply> command)throws Exception{
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){var gate=new java.util.concurrent.CyclicBarrier(2);var tasks=new ArrayList<java.util.concurrent.Future<Reply>>();for(int i=0;i<2;i++)tasks.add(pool.submit(()->{gate.await();return command.call();}));return List.of(tasks.get(0).get(40,java.util.concurrent.TimeUnit.SECONDS),tasks.get(1).get(40,java.util.concurrent.TimeUnit.SECONDS));}
 }

 void approveOnline(Source f)throws Exception {
  String app="/api/ctsv/mghp/ho-so/"+f.root;
  send("POST","/api/ctsv/mghp/giay-to-da-nop/"+f.doc+"/kiem-tra",JSON.createObjectNode().put("kind","content").put("result","pass").putNull("note").putNull("replacesCheckId"),cvToken,read(app).tag()).expect(201);
  send("POST",app+"/ket-luan",Map.of("result","approved"),cvToken,read(app).tag()).expect(200);
 }
 @Test void reviewLifecycleRejectsUnapprovedSourcesWithoutWriting()throws Exception {
  String round=create();send("POST",round+"/mo",null,cvToken,read(round).tag()).expect(200);
  for(String status:List.of("submitted","processing","supplementing","supplement_responded","reopened","withdrawn","rejected")){
   var f=source(round);jdbc.update("UPDATE dbo.mghp_ho_so SET trang_thai=? WHERE id=?",status,f.root);
   send("POST","/api/ctsv/mghp/danh-gia",f.evaluation,cvToken,null).expect(409);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_danh_gia_dieu_kien WHERE ho_so_nguon_id=?",Integer.class,f.root)).isZero();
  }
 }
 @Test void reviewLifecycleApprovedProgressAndDraftDecisionIsNotOfficial()throws Exception {
  String round=create();send("POST",round+"/mo",null,cvToken,read(round).tag()).expect(200);var f=source(round);
  approveOnline(f);String app="/api/ctsv/mghp/ho-so/"+f.root;
  assertThat(read(app).json().path("onlineStatus").asText()).isEqualTo("approved");
  assertThat(read(app).json().path("status").asText()).isEqualTo("processing");
  assess(f,"2026-10-31");assertThat(read(app).json().path("hardCopyStatus").asText()).isEqualTo("approved");
  var filtered=read("/api/ctsv/mghp/ho-so?roundId="+round.substring(round.lastIndexOf('/')+1)+"&status=approved&size=1").json();
  assertThat(filtered.path("totalElements").asInt()).isEqualTo(1);assertThat(filtered.path("items").get(0).path("id").asText()).isEqualTo(""+f.root);
  closeReview(round);assertThat(read(app).json().path("status").asText()).isEqualTo("approved");
  var d=send("POST","/api/ctsv/mghp/quyet-dinh",decision(round),cvToken,null).expect(201);
  assertThat(read(app).json().path("councilResult").isNull()).isTrue();
  var after=send("POST",vp(d)+"/ket-luan-hop",meeting(),cvToken,d.tag()).expect(201);
  String before=read(app).tag();send("POST",vp(after)+"/xac-nhan",Map.of("reason","Xác nhận kết quả Hội đồng"),cvToken,after.tag()).expect(200);
  assertThat(read(app).json().path("councilResult").asText()).isEqualTo("selected");assertThat(read(app).tag()).isNotEqualTo(before);
  assertThat(send("GET",app,null,svToken,null).expect(200).json().path("councilResult").isNull()).isTrue();
  String list="/api/ctsv/mghp/ho-so?roundId="+round.substring(round.lastIndexOf('/')+1);
  send("GET",list+"&status=selected",null,svToken,null).expect(400);
  assertThat(send("GET",list+"&status=approved",null,svToken,null).expect(200).json().path("totalElements").asInt()).isEqualTo(1);
  // SQL-only publication fixture tests the read predicate; not a P12 publication integration claim.
  jdbc.update("INSERT dbo.mghp_cong_bo(phien_ban_qd_id,ma_cong_bo,thoi_diem_cong_bo,sha256_tap_du_lieu) VALUES(?,?,?,?)",after.json().path("id").asLong(),"RL-PUB-"+UUID.randomUUID(),NOW.atOffset(ZoneOffset.UTC),after.json().path("datasetSha256").asText());
  assertThat(send("GET",app,null,svToken,null).expect(200).json().path("councilResult").asText()).isEqualTo("selected");
  assertThat(send("GET",list+"&status=approved",null,svToken,null).expect(200).json().path("totalElements").asInt()).isEqualTo(1);
 }
 @Test void reviewLifecycleApprovalDoesNotRequireReason()throws Exception {
  String round=create();send("POST",round+"/mo",null,cvToken,read(round).tag()).expect(200);var f=source(round);approveOnline(f);
  String app="/api/ctsv/mghp/ho-so/"+f.root;
  for(var kind:List.of("form","validity","subject"))send("POST","/api/ctsv/mghp/giay-to-da-nop/"+f.doc+"/kiem-tra",JSON.createObjectNode().put("kind",kind).put("result","pass").putNull("note").putNull("replacesCheckId"),cvToken,read(app).tag()).expect(201);
  send("POST","/api/ctsv/mghp/giay-to-da-nop/"+f.doc+"/xac-nhan-ban-cung",JSON.createObjectNode().put("received",true).putNull("note"),cvToken,read(app).tag()).expect(201);
  var body=f.evaluation.deepCopy();body.put("result","dat").putNull("reason").put("timeEvidence","Khoảng đã kiểm chứng synthetic");
  ((ObjectNode)body.path("criteria").get(0)).put("result","dat");
  ((ObjectNode)body.path("evidence").get(0)).put("result","dat").put("from","2026-09-01").put("to","2026-10-31").putNull("reason");
  body.withArray("eligiblePeriods").addObject().put("from","2026-09-01").put("to","2026-10-31").put("evidence","Nguồn đã xác minh");
  var e=send("POST","/api/ctsv/mghp/danh-gia",body,cvToken,null).expect(201);String path="/api/ctsv/mghp/danh-gia/"+e.json().path("id").asText();
  send("POST",path+"/xac-nhan",JSON.createObjectNode().putNull("reason"),cvToken,e.tag()).expect(200);
  assertThat(read(app).json().path("hardCopyStatus").asText()).isEqualTo("approved");
  assertThat(read(path).json().path("reason").isNull()).isTrue();
 }
 @Test void reviewLifecycleHardCopyFailureIsTerminal()throws Exception {
  String round=create();send("POST",round+"/mo",null,cvToken,read(round).tag()).expect(200);var f=source(round);approveOnline(f);
  var body=f.evaluation.deepCopy();body.put("result","khong_dat").put("reason","Bản cứng không đáp ứng nội dung bắt buộc");
  ((ObjectNode)body.path("criteria").get(0)).put("result","khong_dat").put("note","Nội dung giấy không đúng");
  ((ObjectNode)body.path("evidence").get(0)).put("result","khong_dat").put("reason","Giấy không đáp ứng");
  var e=send("POST","/api/ctsv/mghp/danh-gia",body,cvToken,null).expect(201);String path="/api/ctsv/mghp/danh-gia/"+e.json().path("id").asText();
  send("POST",path+"/xac-nhan",JSON.createObjectNode().putNull("reason"),cvToken,e.tag()).expect(400);
  assertThat(read(path).json().path("status").asText()).isEqualTo("draft");
  send("POST",path+"/xac-nhan",Map.of("reason","Kết thúc hồ sơ bản cứng không đáp ứng"),cvToken,e.tag()).expect(200);
  String app="/api/ctsv/mghp/ho-so/"+f.root;assertThat(read(app).json().path("hardCopyStatus").asText()).isEqualTo("rejected");
  assertThat(read(app).json().path("evaluationAllowed").asBoolean()).isFalse();
  send("POST",path+"/phien-ban",Map.of("reason","Không được đối chiếu lại"),cvToken,read(path).tag()).expect(409);
  send("POST","/api/ctsv/mghp/giay-to-da-nop/"+f.doc+"/xac-nhan-ban-cung",JSON.createObjectNode().put("received",true).putNull("note"),cvToken,read(app).tag()).expect(409);
 }
 @Test void reviewLifecycleMutationsRecheckApprovalInsideTransaction()throws Exception {
  String round=create();send("POST",round+"/mo",null,cvToken,read(round).tag()).expect(200);var f=source(round);approveOnline(f);
  var e=send("POST","/api/ctsv/mghp/danh-gia",f.evaluation,cvToken,null).expect(201);String path="/api/ctsv/mghp/danh-gia/"+e.json().path("id").asText();
  jdbc.update("UPDATE dbo.mghp_ho_so SET trang_thai=N'submitted' WHERE id=?",f.root);
  send("PUT",path,f.evaluation,cvToken,e.tag()).expect(409);
  send("POST",path+"/xac-nhan",Map.of("reason","Không được vượt bước V1"),cvToken,e.tag()).expect(409);
  send("POST",path+"/phien-ban",Map.of("reason","Không được vượt bước V1"),cvToken,e.tag()).expect(409);
  assertThat(read(path).json().path("status").asText()).isEqualTo("draft");
 }
}
