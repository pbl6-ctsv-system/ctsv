package pbl6.ctsv.auth;
import com.fasterxml.jackson.databind.*;import java.net.*;import java.net.http.*;import java.time.*;import java.util.*;import javax.sql.DataSource;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.*;import org.springframework.boot.test.web.server.LocalServerPort;import org.springframework.context.annotation.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.ValueSource;import com.fasterxml.jackson.databind.node.ObjectNode;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,classes={pbl6.ctsv.CtsvApplication.class,P05HttpIT.Config.class},properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.mghp.configuration.enabled=true","server.address=127.0.0.1"})
@ActiveProfiles({"local","test"}) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class P05HttpIT {
 static final ObjectMapper JSON=new ObjectMapper();static final String BASE="/api/ctsv/mghp";static final String PASSWORD="SYNTHETIC P05 "+UUID.randomUUID();
 static final java.util.concurrent.atomic.AtomicReference<String> FAULT=new java.util.concurrent.atomic.AtomicReference<>();
 static final java.util.concurrent.atomic.AtomicBoolean AUTH_UNAVAILABLE=new java.util.concurrent.atomic.AtomicBoolean();
 static class FaultJdbc extends JdbcTemplate {FaultJdbc(DataSource s){super(s);}@Override public int update(String sql,Object...a){int rows=super.update(sql,a);if(FAULT.get()!=null&&sql.contains(FAULT.get()))super.update("THROW 51750,N'P05 test rollback fault',1");return rows;}}
 @TestConfiguration(proxyBeanMethods=false) static class Config {
  @Bean DataSource p05Source()throws Exception{return pbl6.ctsv.mghp.configuration.P05Database.migrate();}
  @Bean JdbcTemplate p05Jdbc(DataSource ds){return new FaultJdbc(ds);}
  @Bean @Primary Clock p05Clock(){return Clock.fixed(Instant.parse("2026-11-02T02:00:00Z"),ZoneOffset.UTC);}
  @Bean @Primary AuthAccess p05Access(AuthService service){return new AuthAccess(){public Principal verifyAccessToken(String token){return service.verifyAccessToken(token);}public State requireCtsvAccess(Principal p,UUID mapped){if(AUTH_UNAVAILABLE.get())throw new AuthFailure(503,"DEPENDENCY_UNAVAILABLE");return service.requireCtsvAccess(p,mapped);}};}
 }
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r)throws Exception{AuthHttpIT.configuration(r);if(System.getenv("P05_BROWSER_HOME")!=null)P05BrowserHarnessIT.browserProperties(r);}
 @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired AuthAccess auth;@Autowired AuthSettings authSettings;
 String cvToken,svToken;long cv,sv;JsonNode aliases;long policyM2,lockedM2,semester;
 record Reply(int status,HttpHeaders headers,String body){JsonNode json()throws Exception{return JSON.readTree(body);}String tag(){return headers.firstValue("ETag").orElseThrow();}void status(int n){assertThat(status).as(body).isEqualTo(n);}}
 Reply send(String method,String path,Object body,String token,String tag)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));if(token!=null)b.header("Authorization","Bearer "+token);if(tag!=null)b.header("If-Match",tag);if(body!=null)b.header("Content-Type","application/json");
  if(path.startsWith("/api/xac-thuc/"))b.header("Origin",AuthHttpIT.ORIGIN).header("X-CSRF-Protection","1");
  b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
  try(var c=HttpClient.newBuilder().sslContext(AuthHttpIT.testTls).build()){var r=c.send(b.build(),HttpResponse.BodyHandlers.ofString());return new Reply(r.statusCode(),r.headers(),r.body());}
 }
 long insert(String sql,Object...a){return jdbc.queryForObject(sql+";SELECT CAST(SCOPE_IDENTITY() AS bigint)",Long.class,a);}
 String[] identity(boolean specialist)throws Exception{
  UUID id=UUID.randomUUID();String name="p05."+id;var credentials=new AuthCredentials();jdbc.update("INSERT dbo.TaiKhoan(ID,TenDangNhap,TenDangNhapChuanHoa,MatKhauHash) VALUES(?,?,?,?)",id.toString(),name,name,credentials.hash(PASSWORD));jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'CTSV_USER'",id.toString());
  long user=insert("INSERT dbo.nguoi_dung(dia_chi_email,dang_hoat_dong) VALUES(?,1)",name+"@example.invalid");jdbc.update("INSERT dbo.dinh_danh_dang_nhap(nguoi_dung_id,ma_nha_cung_cap,ma_chu_the_dang_nhap,thoi_diem_xac_minh) VALUES(?,?,?,SYSUTCDATETIME())",user,"https://auth.ctsv.test",id.toString());
  if(specialist)jdbc.update("INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he) SELECT ?,id,N'MGHP' FROM dbo.vai_tro WHERE ma=N'mghp_chuyen_vien'",user);
  else insert("INSERT dbo.sinh_vien(mssv,ho_ten,nguoi_dung_id) VALUES(?,N'SYNTHETIC P05 student',?)",name,user);
  var login=send("POST","/api/xac-thuc/dang-nhap",Map.of("username",name,"password",PASSWORD),null,null);login.status(200);return new String[]{Long.toString(user),login.json().path("accessToken").asText()};
 }
 @BeforeAll void identities()throws Exception{var a=identity(true);cv=Long.parseLong(a[0]);cvToken=a[1];var b=identity(false);sv=Long.parseLong(b[0]);svToken=b[1];aliases=pbl6.ctsv.mghp.configuration.P05Fixtures.seed(pbl6.ctsv.mghp.configuration.P05Database.source());policyM2=alias("mghp_chinh_sach",2);lockedM2=alias("mghp_phien_ban_chinh_sach",102);semester=alias("semesters",901);}
 long alias(String table,long source){return pbl6.ctsv.mghp.configuration.P05Fixtures.alias(aliases,table,source);}
 String policyPath(long id){return BASE+"/phien-ban-chinh-sach/"+id;}
 String roundPath(long id){return BASE+"/dot-xet/"+id;}
 ObjectNode content(long source)throws Exception{var n=(ObjectNode)send("GET",policyPath(source),null,cvToken,null).json();for(var key:new String[]{"id","policyId","version","status","ruleVerificationStatus","lockedAt"})n.remove(key);for(var c:n.path("criteria"))((ObjectNode)c).remove("id");for(var g:n.path("documentGroups")){((ObjectNode)g).remove("id");for(var d:g.path("documents"))((ObjectNode)d).remove("id");}return n;}
 Reply newPolicy(ObjectNode body)throws Exception{return send("POST",BASE+"/chinh-sach/"+policyM2+"/phien-ban",body,cvToken,null);}
 long createdPolicy()throws Exception{var r=newPolicy(content(lockedM2));r.status(201);long id=r.json().path("id").asLong();assertThat(r.headers().firstValue("Location")).hasValue(policyPath(id));assertThat(r.tag()).isNotBlank();assertThat(jdbc.queryForObject("SELECT chinh_sach_id FROM dbo.mghp_phien_ban_chinh_sach WHERE id=?",Long.class,id)).isEqualTo(policyM2);return id;}
 ObjectNode roundContent(long policy){var n=JSON.createObjectNode();n.put("code","TEST-P05-"+UUID.randomUUID());n.put("semesterId",""+semester);n.put("name","SYNTHETIC P05 round");n.putNull("description");n.put("opensAt","2026-11-03T00:00:00Z");n.put("submissionDeadline","2026-11-05T16:59:59Z");n.put("reviewDeadline","2026-11-15T16:59:59Z");n.put("declarationGuidance","SYNTHETIC guidance");n.putNull("lateSubmissionGuidance");n.put("allowRetroactive",true);n.put("retroactiveMode","theo_phap_luat");n.put("midSemesterMode","khoang_ngay_chuyen_vien_xac_nhan");n.putArray("policyVersions").addObject().put("policyVersionId",""+policy).putNull("applicationTemplateFileId").put("order",1);return n;}
 long createdRound()throws Exception{var r=send("POST",BASE+"/dot-xet",roundContent(lockedM2),cvToken,null);r.status(201);return r.json().path("id").asLong();}
 Reply readRound(long id)throws Exception{return send("GET",roundPath(id),null,cvToken,null);}
 Reply action(long id,String action)throws Exception{return send("POST",roundPath(id)+"/"+action,action.equals("mo")?null:Map.of("reason","SYNTHETIC TEST-P05"),cvToken,readRound(id).tag());}
 @Test void POL_S01_catalog_is_real_and_scoped()throws Exception{var r=send("GET",BASE+"/chinh-sach",null,cvToken,null);r.status(200);assertThat(r.json().size()).isEqualTo(9);}
 @Test void common_authentication_and_permission()throws Exception{send("GET",BASE+"/chinh-sach",null,null,null).status(401);send("GET",BASE+"/chinh-sach",null,svToken,null).status(403);}
 @Test void POL_S02_S03_N07_versions_and_detail_are_exact()throws Exception{var list=send("GET",BASE+"/chinh-sach/"+policyM2+"/phien-ban",null,cvToken,null);list.status(200);int previous=Integer.MAX_VALUE;for(var n:list.json()){assertThat(n.path("version").asInt()).isLessThanOrEqualTo(previous);previous=n.path("version").asInt();}var old=send("GET",policyPath(lockedM2),null,cvToken,null);old.status(200);assertThat(old.json().path("rate").asText()).isEqualTo("1.000000000");assertThat(old.json().path("status").asText()).isEqualTo("locked");assertThat(old.tag()).startsWith("\"");assertThat(old.json().has("phien_ban_ky_thuat")).isFalse();}
 @Test void POL_S04_S05_S06_N08_draft_update_confirm_replay()throws Exception{long id=createdPolicy();var p=policyPath(id);var old=send("GET",p,null,cvToken,null);var n=content(id);n.put("name","SYNTHETIC updated policy");var write=send("PUT",p,n,cvToken,old.tag());write.status(200);assertThat(write.headers().firstValue("ETag")).isEmpty();var fresh=send("GET",p,null,cvToken,null);assertThat(fresh.tag()).isNotEqualTo(old.tag());var confirmed=send("POST",p+"/xac-nhan",Map.of("evidence","SYNTHETIC TEST-POL-01"),cvToken,fresh.tag());confirmed.status(200);assertThat(confirmed.json().path("ruleVerificationStatus").asText()).isEqualTo("da_doi_chieu_nguon");send("POST",p+"/xac-nhan",Map.of("evidence","SYNTHETIC TEST-POL-01"),cvToken,confirmed.tag()).status(200);send("POST",p+"/xac-nhan",Map.of("evidence","SYNTHETIC TEST-POL-01"),cvToken,fresh.tag()).status(412);send("PUT",p,n,cvToken,confirmed.tag()).status(409);}
 @ParameterizedTest @ValueSource(strings={"70","-0.1","1.0000000001"})void POL_N01_bad_rate_no_commit(String rate)throws Exception{var n=content(lockedM2);n.put("rate",rate);int before=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_phien_ban_chinh_sach",Integer.class);newPolicy(n).status(400);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_phien_ban_chinh_sach",Integer.class)).isEqualTo(before);}
 @Test void POL_N02_N03_immutable_and_missing_confirmation_inputs()throws Exception{var locked=send("GET",policyPath(lockedM2),null,cvToken,null);send("PUT",policyPath(lockedM2),content(lockedM2),cvToken,locked.tag()).status(409);var unchanged=send("GET",policyPath(lockedM2),null,cvToken,null);assertThat(unchanged.body()).isEqualTo(locked.body());assertThat(unchanged.tag()).isEqualTo(locked.tag());var n=content(lockedM2);n.putArray("criteria");var r=newPolicy(n);r.status(201);long id=r.json().path("id").asLong();send("POST",policyPath(id)+"/xac-nhan",Map.of("evidence","SYNTHETIC missing criteria"),cvToken,r.tag()).status(409);assertThat(send("GET",policyPath(id),null,cvToken,null).json().path("status").asText()).isEqualTo("draft");}
 @Test void POL_N04_M6_two_subjects_required()throws Exception{long m6=alias("mghp_chinh_sach",6),v6=alias("mghp_phien_ban_chinh_sach",106);var n=content(v6);for(var g:n.path("documentGroups"))for(var d:g.path("documents"))if(d.path("evidenceSubject").asText().equals("cha_hoac_me"))((ObjectNode)d).put("evidenceSubject","sinh_vien");var r=send("POST",BASE+"/chinh-sach/"+m6+"/phien-ban",n,cvToken,null);r.status(201);send("POST",policyPath(r.json().path("id").asLong())+"/xac-nhan",Map.of("evidence","SYNTHETIC missing parent"),cvToken,r.tag()).status(409);}
 @Test void POL_N06_transaction_rolls_back_children_root_and_audit()throws Exception{long id=createdPolicy();String p=policyPath(id);var old=send("GET",p,null,cvToken,null);var n=content(id);n.put("name","MUST ROLLBACK");long audit=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.nhat_ky_thao_tac",Long.class);FAULT.set("DELETE dbo.mghp_nhom_yeu_cau_giay_to");try{send("PUT",p,n,cvToken,old.tag()).status(500);}finally{FAULT.set(null);}var after=send("GET",p,null,cvToken,null);assertThat(after.body()).isEqualTo(old.body());assertThat(after.tag()).isEqualTo(old.tag());assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.nhat_ky_thao_tac",Long.class)).isEqualTo(audit);}
 @Test void common_preconditions_body_limits_and_strict_requests()throws Exception{long id=createdPolicy();var n=content(id);send("PUT",policyPath(id),n,cvToken,null).status(428);send("PUT",policyPath(id),n,cvToken,"W/\"bad\"").status(400);send("PUT",policyPath(id),n,cvToken,"\"stale\"").status(412);n.put("legalBasis","a".repeat(1048577));send("PUT",policyPath(id),n,cvToken,"\"stale\"").status(413);send("GET",BASE+"/chinh-sach?x=1",null,cvToken,null).status(400);send("GET",BASE+"/chinh-sach",Map.of("x",1),cvToken,null).status(400);send("GET",policyPath(Long.MAX_VALUE),null,cvToken,null).status(404);}
 @Test void DOT_S01_to_S07_N02_N05_N09_public_future_round_lifecycle()throws Exception{long id=createdRound();String p=roundPath(id);send("GET",p,null,svToken,null).status(404);var own=readRound(id);var n=roundContent(lockedM2);n.put("code",own.json().path("code").asText());n.put("name","SYNTHETIC changed round");var updated=send("PUT",p,n,cvToken,own.tag());updated.status(200);assertThat(updated.headers().firstValue("ETag")).isEmpty();var opened=action(id,"mo");opened.status(200);var publicView=send("GET",p,null,svToken,null);publicView.status(200);assertThat(publicView.json().has("configLockedAt")).isFalse();assertThat(publicView.headers().firstValue("ETag")).isEmpty();send("PUT",p,n,cvToken,opened.tag()).status(409);var list=send("GET",BASE+"/dot-xet?semesterId="+semester+"&page=0&size=100",null,svToken,null);list.status(200);for(var row:list.json().path("items"))assertThat(row.path("status").asText()).isNotEqualTo("draft");action(id,"dong-nhan-ho-so").status(200);action(id,"hoan-tat").status(200);send("GET",p,null,svToken,null).status(200);}
 @Test void DOT_N01_duplicate_code_and_N03_draft_policy_not_ready()throws Exception{var n=roundContent(lockedM2);var r=send("POST",BASE+"/dot-xet",n,cvToken,null);r.status(201);send("POST",BASE+"/dot-xet",n,cvToken,null).status(409);long draft=createdPolicy();n=roundContent(draft);r=send("POST",BASE+"/dot-xet",n,cvToken,null);r.status(201);action(r.json().path("id").asLong(),"mo").status(409);}
 @ParameterizedTest @ValueSource(booleans={false,true})void DOT_N10_retroactive_flag_gate(boolean absent)throws Exception{var n=roundContent(lockedM2);if(absent)n.putNull("allowRetroactive");else n.put("allowRetroactive",false);var r=send("POST",BASE+"/dot-xet",n,cvToken,null);r.status(201);action(r.json().path("id").asLong(),"mo").status(409);}
 @Test void DOT_N06_open_fault_rolls_back_notice_and_lock()throws Exception{long id=createdRound();var before=readRound(id);long notices=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.thong_bao",Long.class);long audit=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.nhat_ky_thao_tac",Long.class);FAULT.set("INSERT dbo.nguoi_nhan_thong_bao");try{send("POST",roundPath(id)+"/mo",null,cvToken,before.tag()).status(500);}finally{FAULT.set(null);}var after=readRound(id);assertThat(after.body()).isEqualTo(before.body());assertThat(after.tag()).isEqualTo(before.tag());assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.thong_bao",Long.class)).isEqualTo(notices);assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.nhat_ky_thao_tac",Long.class)).isEqualTo(audit);}
 @Test void DOT_N08_task_blocks_completion_without_mutating_applications()throws Exception{long id=createdRound();action(id,"mo").status(200);action(id,"dong-nhan-ho-so").status(200);long task=insert("INSERT dbo.cong_viec(ma_phan_he,loai_doi_tuong,doi_tuong_id,trang_thai) VALUES(N'MGHP',N'mghp_dot_xet',?,N'pending')",Long.toString(id));var before=readRound(id);action(id,"hoan-tat").status(409);assertThat(readRound(id).tag()).isEqualTo(before.tag());jdbc.update("UPDATE dbo.cong_viec SET trang_thai=N'completed' WHERE id=?",task);action(id,"hoan-tat").status(200);}
 @Test void common_round_pagination_and_access_validation()throws Exception{for(var q:List.of("page=-1","size=0","size=101","page=100000&size=100","page=0&page=1","unknown=1"))send("GET",BASE+"/dot-xet?"+q,null,cvToken,null).status(400);send("GET",BASE+"/dot-xet?semesterId="+Long.MAX_VALUE,null,cvToken,null).status(404);send("POST",BASE+"/dot-xet",roundContent(lockedM2),svToken,null).status(403);}
 @Test void POL_N05_parallel_revisions_and_CAS_are_not_lost()throws Exception{
  var second=identity(true);var n=content(lockedM2);var start=new java.util.concurrent.CountDownLatch(1);try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var a=pool.submit(()->{start.await();return newPolicy(n);});var b=pool.submit(()->{start.await();return send("POST",BASE+"/chinh-sach/"+policyM2+"/phien-ban",n,second[1],null);});start.countDown();var ra=a.get();var rb=b.get();ra.status(201);rb.status(201);assertThat(ra.json().path("version").asInt()).isNotEqualTo(rb.json().path("version").asInt());
   long id=ra.json().path("id").asLong();var fresh=send("GET",policyPath(id),null,cvToken,null);var barrier=new java.util.concurrent.CountDownLatch(1);
   var x=pool.submit(()->{barrier.await();return send("PUT",policyPath(id),n,cvToken,fresh.tag());});var y=pool.submit(()->{barrier.await();return send("PUT",policyPath(id),n,second[1],fresh.tag());});barrier.countDown();assertThat(List.of(x.get().status(),y.get().status())).containsExactlyInAnyOrder(200,412);
  }
 }
 @Test void child_sql_mutation_changes_strong_validator_and_fence()throws Exception{long id=createdPolicy();var old=send("GET",policyPath(id),null,cvToken,null);long criterion=old.json().path("criteria").get(0).path("id").asLong();byte[] fence=jdbc.queryForObject("SELECT phien_ban_ky_thuat FROM dbo.mghp_phien_ban_chinh_sach WHERE id=?",byte[].class,id);jdbc.update("UPDATE dbo.mghp_tieu_chi_chinh_sach SET ten=N'SYNTHETIC changed child' WHERE id=?",criterion);var changed=send("GET",policyPath(id),null,cvToken,null);assertThat(changed.tag()).isNotEqualTo(old.tag());assertThat(jdbc.queryForObject("SELECT phien_ban_ky_thuat FROM dbo.mghp_phien_ban_chinh_sach WHERE id=?",byte[].class,id)).isNotEqualTo(fence);send("PUT",policyPath(id),content(id),cvToken,old.tag()).status(412);}
 @Test void fresh_auth_failure_is_fail_closed_but_public_student_get_is_local()throws Exception{long id=createdRound();action(id,"mo").status(200);AUTH_UNAVAILABLE.set(true);try{send("GET",BASE+"/chinh-sach",null,cvToken,null).status(503);send("POST",BASE+"/dot-xet",roundContent(lockedM2),cvToken,null).status(503);send("GET",roundPath(id),null,svToken,null).status(200);}finally{AUTH_UNAVAILABLE.set(false);}}
 @Test void DOT_N07_N08_supplement_lifecycle_is_not_closed_by_closing_round()throws Exception{
  long id=createdRound();action(id,"mo").status(200);long student=jdbc.queryForObject("SELECT id FROM dbo.sinh_vien WHERE nguoi_dung_id=?",Long.class,sv);long process=insert("INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,ma_qua_trinh_hoc) VALUES(?,?)",student,"SYNTHETIC-P05-"+UUID.randomUUID());
  long application=insert("INSERT dbo.mghp_ho_so(ma_ho_so,dot_xet_id,sinh_vien_id,qua_trinh_hoc_id,chinh_sach_id,phien_ban_chinh_sach_id,trang_thai) VALUES(?,?,?,?,?,?,N'processing')","SYNTHETIC-P05-"+UUID.randomUUID(),id,student,process,policyM2,lockedM2);
  long ethnicity=insert("INSERT dbo.dan_toc(ma,ten) VALUES(?,N'SYNTHETIC')","P05-"+UUID.randomUUID()),cohort=insert("INSERT dbo.khoa_hoc(ma) VALUES(?)","P05-"+UUID.randomUUID()),unit=insert("INSERT dbo.don_vi(ma,ten) VALUES(?,N'SYNTHETIC')","P05-"+UUID.randomUUID()),clazz=insert("INSERT dbo.lop_sinh_hoat(ma,ten) VALUES(?,N'SYNTHETIC')","P05-"+UUID.randomUUID());
  var values=new LinkedHashMap<String,Object>();values.put("ho_so_id",application);values.put("lan_sua_doi",1);values.put("nguoi_nop_id",sv);values.put("thoi_diem_nop",OffsetDateTime.parse("2026-11-02T02:00:00Z"));values.put("loai_lan_nop","initial");values.put("dot_tiep_nhan_id",id);values.put("phien_ban_chinh_sach_ke_khai_id",lockedM2);values.put("ma_thao_tac_nop",UUID.randomUUID().toString());values.put("ma_phien_ban_mau","SYNTHETIC-P05");values.put("ho_ten_khai_bao","SYNTHETIC");values.put("so_cccd_khai_bao","SYNTHETIC-ONLY");values.put("ngay_sinh_khai_bao",java.sql.Date.valueOf("2000-01-01"));values.put("noi_sinh_khai_bao","SYNTHETIC");values.put("gioi_tinh_khai_bao","SYNTHETIC");values.put("dan_toc_khai_bao_id",ethnicity);values.put("lop_khai_bao_id",clazz);values.put("mssv_khai_bao","SYNTHETIC-P05");values.put("khoa_hoc_khai_bao_id",cohort);values.put("khoa_quan_ly_khai_bao_id",unit);values.put("dia_chi_thuong_tru_khai_bao","SYNTHETIC");values.put("so_dien_thoai_khai_bao","SYNTHETIC");long version=pbl6.ctsv.mghp.configuration.P05Fixtures.id(jdbc,"mghp_phien_ban_ho_so",values);
  long supplement=insert("INSERT dbo.mghp_yeu_cau_bo_sung(ho_so_id,phien_ban_lam_co_so_id,so_vong,trang_thai,han_xu_ly,dot_tiep_nhan_id) VALUES(?,?,1,N'open',?,?)",application,version,OffsetDateTime.parse("2026-11-10T00:00:00Z"),id);
  action(id,"dong-nhan-ho-so").status(200);assertThat(jdbc.queryForObject("SELECT trang_thai FROM dbo.mghp_yeu_cau_bo_sung WHERE id=?",String.class,supplement)).isEqualTo("open");action(id,"hoan-tat").status(409);
  jdbc.update("UPDATE dbo.mghp_yeu_cau_bo_sung SET trang_thai=N'completed' WHERE id=?",supplement);long item=insert("INSERT dbo.mghp_noi_dung_bo_sung(yeu_cau_bo_sung_id,loai_yeu_cau,ket_qua_xu_ly) VALUES(?,N'SYNTHETIC',N'needs_verification')",supplement);action(id,"hoan-tat").status(409);jdbc.update("UPDATE dbo.mghp_noi_dung_bo_sung SET ket_qua_xu_ly=N'satisfied' WHERE id=?",item);action(id,"hoan-tat").status(200);assertThat(jdbc.queryForObject("SELECT trang_thai FROM dbo.mghp_ho_so WHERE id=?",String.class,application)).isEqualTo("processing");
 }
 @Test void JSON_boundary_1MiB_is_accepted_and_larger_is_not()throws Exception{var n=content(lockedM2);n.put("legalBasis","");int overhead=JSON.writeValueAsBytes(n).length;n.put("legalBasis","a".repeat(1048576-overhead));assertThat(JSON.writeValueAsBytes(n)).hasSize(1048576);newPolicy(n).status(201);n.put("legalBasis",n.path("legalBasis").asText()+"a");newPolicy(n).status(413);}
 @Test void DOT_open_exact_deadline_and_after_deadline()throws Exception{var n=roundContent(lockedM2);n.put("opensAt","2026-11-02T02:00:00Z");n.put("submissionDeadline","2026-11-02T02:00:00Z");var r=send("POST",BASE+"/dot-xet",n,cvToken,null);r.status(201);action(r.json().path("id").asLong(),"mo").status(200);n.put("code","TEST-P05-"+UUID.randomUUID());n.put("opensAt","2026-11-01T00:00:00Z");n.put("submissionDeadline","2026-11-02T01:59:59.999Z");r=send("POST",BASE+"/dot-xet",n,cvToken,null);r.status(201);action(r.json().path("id").asLong(),"mo").status(409);}

 @Test void template_binding_is_shared_by_authorized_managers_but_unbound_file_is_private()throws Exception{
  long file=insert("INSERT dbo.tep_dinh_kem(ten_tep,kieu_noi_dung,trang_thai_luu_tru,nguoi_so_huu_id,nguoi_tai_len_id) VALUES(N'SYNTHETIC.pdf',N'application/pdf',N'available',?,?)",cv,cv);
  var other=identity(true);var n=roundContent(lockedM2);
  ((ObjectNode)n.path("policyVersions").get(0)).put("applicationTemplateFileId",Long.toString(file));
  send("POST",BASE+"/dot-xet",n,other[1],null).status(404);
  var created=send("POST",BASE+"/dot-xet",n,cvToken,null);created.status(201);
  long id=created.json().path("id").asLong();
  send("POST",roundPath(id)+"/mo",null,other[1],created.tag()).status(200);
 }

 @ParameterizedTest @ValueSource(strings={"minimumRequired","groupCode","documentCode"})
 void POL_N01_nested_shape_errors_do_not_commit(String variant)throws Exception{
  var n=content(lockedM2);var groups=n.withArray("documentGroups");
  if(variant.equals("minimumRequired"))((ObjectNode)groups.get(0)).put("minimumRequired",999);
  else if(variant.equals("groupCode"))groups.add(groups.get(0).deepCopy());
  else {var docs=((ObjectNode)groups.get(0)).withArray("documents");docs.add(docs.get(0).deepCopy());}
  long before=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.mghp_phien_ban_chinh_sach",Long.class);
  var reply=newPolicy(n);reply.status(400);assertThat(reply.json().path("code").asText()).isEqualTo("VALIDATION_ERROR");
  assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.mghp_phien_ban_chinh_sach",Long.class)).isEqualTo(before);
 }

 @Test void M9_confirmation_remains_project_convention()throws Exception{
  long policy=alias("mghp_chinh_sach",9),source=alias("mghp_phien_ban_chinh_sach",109);
  var created=send("POST",BASE+"/chinh-sach/"+policy+"/phien-ban",content(source),cvToken,null);created.status(201);
  var confirmed=send("POST",policyPath(created.json().path("id").asLong())+"/xac-nhan",Map.of("evidence","SYNTHETIC M9 convention only"),cvToken,created.tag());
  confirmed.status(200);assertThat(confirmed.json().path("ruleVerificationStatus").asText()).isEqualTo("quy_uoc_do_an");
 }

 @ParameterizedTest @ValueSource(strings={"missingTimes","unsupportedMode","expiredPolicy","unverifiedCalendar","unreadyFile"})
 void DOT_not_ready_variants_keep_draft_and_no_notice(String variant)throws Exception{
  var n=roundContent(lockedM2);
  switch(variant){
   case "missingTimes" -> n.putNull("opensAt");
   case "unsupportedMode" -> n.put("midSemesterMode","client_formula");
   case "expiredPolicy" -> {var p=content(lockedM2);p.put("effectiveUntil","2026-01-01");p.put("effectiveFrom","2025-01-01");var created=newPolicy(p);created.status(201);long id=created.json().path("id").asLong();send("POST",policyPath(id)+"/xac-nhan",Map.of("evidence","SYNTHETIC expired period"),cvToken,created.tag()).status(200);((ObjectNode)n.path("policyVersions").get(0)).put("policyVersionId",Long.toString(id));}
   case "unverifiedCalendar" -> {long term=insert("INSERT dbo.hoc_ky(ma,ten,trang_thai_xac_minh_moc) VALUES(?,N'SYNTHETIC',N'pending')","TEST-P05-"+UUID.randomUUID());n.put("semesterId",Long.toString(term));}
   case "unreadyFile" -> {long file=insert("INSERT dbo.tep_dinh_kem(ten_tep,kieu_noi_dung,trang_thai_luu_tru,nguoi_so_huu_id) VALUES(N'SYNTHETIC.pdf',N'application/pdf',N'uploading',?)",cv);((ObjectNode)n.path("policyVersions").get(0)).put("applicationTemplateFileId",Long.toString(file));}
  }
  long notices=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.thong_bao",Long.class);
  var created=send("POST",BASE+"/dot-xet",n,cvToken,null);
  if(variant.equals("unsupportedMode"))created.status(400);
  else if(variant.equals("unreadyFile"))created.status(409);
  else {created.status(201);long id=created.json().path("id").asLong();var before=readRound(id);action(id,"mo").status(409);assertThat(readRound(id).body()).isEqualTo(before.body());}
  assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.thong_bao",Long.class)).isEqualTo(notices);
 }

 @Test void round_commands_current_noop_stale_and_race_are_atomic()throws Exception{
  long id=createdRound();var before=readRound(id);var opened=action(id,"mo");opened.status(200);
  send("POST",roundPath(id)+"/mo",null,cvToken,opened.tag()).status(200);
  send("POST",roundPath(id)+"/mo",null,cvToken,before.tag()).status(412);
  send("POST",roundPath(id)+"/dong-nhan-ho-so",Map.of("reason","SYNTHETIC"),cvToken,null).status(428);
  var second=identity(true);var barrier=new java.util.concurrent.CountDownLatch(1);
  try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
   var a=pool.submit(()->{barrier.await();return send("POST",roundPath(id)+"/dong-nhan-ho-so",Map.of("reason","SYNTHETIC"),cvToken,opened.tag());});
   var b=pool.submit(()->{barrier.await();return send("POST",roundPath(id)+"/dong-nhan-ho-so",Map.of("reason","SYNTHETIC"),second[1],opened.tag());});barrier.countDown();
   assertThat(List.of(a.get().status(),b.get().status())).containsExactlyInAnyOrder(200,412);
  }
  var closed=readRound(id);send("POST",roundPath(id)+"/dong-nhan-ho-so",Map.of("reason","SYNTHETIC"),cvToken,closed.tag()).status(200);
 }

 @Test void page_total_is_scoped_stable_and_empty_last_page_is_valid()throws Exception{
  var path=BASE+"/dot-xet?semesterId="+semester+"&page=0&size=100";
  var publicPage=send("GET",path,null,svToken,null);publicPage.status(200);
  long count=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.mghp_dot_xet WHERE hoc_ky_id=? AND trang_thai IN(N'open',N'closed',N'completed')",Long.class,semester);
  assertThat(publicPage.json().path("totalElements").asLong()).isEqualTo(count);
  assertThat(send("GET",path,null,svToken,null).body()).isEqualTo(publicPage.body());
  var empty=send("GET",BASE+"/dot-xet?page=999&size=100",null,svToken,null);empty.status(200);assertThat(empty.json().path("items").isEmpty()).isTrue();
 }

 Reply raw(String method,String path,String body,String media)throws Exception{
  var request=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+path)).header("Authorization","Bearer "+cvToken);
  if(media!=null)request.header("Content-Type",media);
  request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
  try(var client=HttpClient.newBuilder().sslContext(AuthHttpIT.testTls).build()){var r=client.send(request.build(),HttpResponse.BodyHandlers.ofString());return new Reply(r.statusCode(),r.headers(),r.body());}
 }
 @Test void strict_media_method_json_and_error_projection()throws Exception{
  String path=BASE+"/chinh-sach/"+policyM2+"/phien-ban";
  raw("POST",path,"{}","text/plain").status(415);
  raw("POST",path,"{}","application/json; charset=invalid-charset").status(415);
  for(String body:List.of("{", "{\"name\":\"one\",\"name\":\"two\"}","{} {}"))raw("POST",path,body,"application/json").status(400);
  raw("DELETE",BASE+"/chinh-sach",null,null).status(405);
  var failure=send("GET",BASE+"/dot-xet?page=-1",null,cvToken,null);failure.status(400);
  assertThat(failure.headers().firstValue("Cache-Control")).hasValue("no-store");
  assertThat(failure.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
  assertThat(failure.json().path("traceId").asText()).isNotBlank();assertThat(failure.body()).doesNotContain("SQLException","SELECT ","pbl6.",PASSWORD,cvToken);
 }

 @ParameterizedTest @ValueSource(strings={"signature","expired","issuer","audience"})
 void CTSV_C01_bad_JWT_cannot_read_or_write(String variant)throws Exception{
  var parts=cvToken.split("\\.");var decoder=Base64.getUrlDecoder();var encoder=Base64.getUrlEncoder().withoutPadding();
  if(variant.equals("signature")){byte[] signature=decoder.decode(parts[2]);signature[0]^=1;parts[2]=encoder.encodeToString(signature);}
  else{
   var claims=(ObjectNode)JSON.readTree(decoder.decode(parts[1]));
   switch(variant){case "expired" -> {claims.put("iat",Instant.parse("2026-11-01T00:00:00Z").getEpochSecond());claims.put("exp",Instant.parse("2026-11-01T00:15:00Z").getEpochSecond());}case "issuer" -> claims.put("iss","https://wrong.example.invalid");case "audience" -> claims.put("aud","wrong-audience");}
   parts[1]=encoder.encodeToString(JSON.writeValueAsBytes(claims));
   String pem=java.nio.file.Files.readString(authSettings.privateKey()).replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");
   var key=java.security.KeyFactory.getInstance("RSA").generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
   var signer=java.security.Signature.getInstance("SHA256withRSA");signer.initSign(key);signer.update((parts[0]+"."+parts[1]).getBytes(java.nio.charset.StandardCharsets.US_ASCII));parts[2]=encoder.encodeToString(signer.sign());
  }
  String invalid=String.join(".",parts);long before=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.mghp_dot_xet",Long.class);
  send("GET",BASE+"/chinh-sach",null,invalid,null).status(401);send("POST",BASE+"/dot-xet",roundContent(lockedM2),invalid,null).status(401);
  assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.mghp_dot_xet",Long.class)).isEqualTo(before);
 }

 @Test void CORS_preflight_allows_only_configured_https_origin()throws Exception{
  for(var origin:List.of(AuthHttpIT.ORIGIN,"https://untrusted.example.invalid")){
   var req=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+BASE+"/dot-xet")).header("Origin",origin).header("Access-Control-Request-Method","PUT").header("Access-Control-Request-Headers","authorization,content-type,if-match").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build();
   try(var client=HttpClient.newBuilder().sslContext(AuthHttpIT.testTls).build()){
    var reply=client.send(req,HttpResponse.BodyHandlers.ofString());
    if(origin.equals(AuthHttpIT.ORIGIN)){assertThat(reply.statusCode()).isEqualTo(200);assertThat(reply.headers().firstValue("Access-Control-Allow-Origin")).hasValue(origin);assertThat(reply.headers().firstValue("Access-Control-Allow-Credentials")).hasValue("true");}
    else assertThat(reply.statusCode()).isEqualTo(403);
   }
  }
 }
}
