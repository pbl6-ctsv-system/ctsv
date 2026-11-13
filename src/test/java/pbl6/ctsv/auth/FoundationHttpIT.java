package pbl6.ctsv.auth;
import com.fasterxml.jackson.databind.*;
import java.net.*;import java.net.http.*;import java.time.*;import java.util.*;import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 classes={pbl6.ctsv.CtsvApplication.class,FoundationHttpIT.Config.class},
 properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","server.address=127.0.0.1"})
@ActiveProfiles({"local","test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FoundationHttpIT {
 static final java.util.concurrent.atomic.AtomicReference<String> FAULT=new java.util.concurrent.atomic.AtomicReference<>();
 static final java.util.concurrent.atomic.AtomicBoolean AUTH_UNAVAILABLE=new java.util.concurrent.atomic.AtomicBoolean();
 static class FaultJdbc extends JdbcTemplate{
  FaultJdbc(DataSource source){super(source);}
  @Override public int update(String sql,Object...args){int count=super.update(sql,args);if(FAULT.get()!=null&&sql.contains(FAULT.get()))super.update("THROW 51620,N'P04 injected transaction fault',1;");return count;}
 }
 @TestConfiguration(proxyBeanMethods=false) static class Config{
  @Bean JdbcTemplate p04Jdbc(DataSource ds){return new FaultJdbc(ds);}
  @Bean @org.springframework.context.annotation.Primary AuthAccess p04AuthAccess(AuthService service){return new AuthAccess(){
   public Principal verifyAccessToken(String token){return service.verifyAccessToken(token);}
   public State requireCtsvAccess(Principal principal,UUID mapped){if(AUTH_UNAVAILABLE.get())throw new AuthFailure(503,"DEPENDENCY_UNAVAILABLE");return service.requireCtsvAccess(principal,mapped);}
  };}
  @Bean DataSource p04Source()throws Exception{
   if(!Boolean.getBoolean("p04.test.enabled"))throw new IllegalStateException("P04 opt-in required");
   String port=System.getenv("P00_SQL_PORT");
   if(port==null||!port.matches("[0-9]{1,5}"))throw new IllegalStateException("SQL port missing");
   var props=new Properties();props.setProperty("integratedSecurity","true");props.setProperty("encrypt","true");props.setProperty("trustServerCertificate","true");
   var ds=new org.springframework.jdbc.datasource.SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),
    "jdbc:sqlserver://localhost:"+port+";databaseName=p04_ctsv_test_20261112;loginTimeout=5;socketTimeout=30000",props);
   try(var c=ds.getConnection();var s=c.createStatement();var r=s.executeQuery("""
     SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),IS_MEMBER('db_owner'),
     (SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties WHERE class=0 AND name='p04_test_owner'),
     SUSER_SNAME(owner_sid),SUSER_SNAME() FROM sys.databases WHERE database_id=DB_ID()
     """)){
    if(!r.next()||!"p04_ctsv_test_20261112".equals(r.getString(1))||!"DEV2022".equals(r.getString(2))||r.getInt(3)!=1
     ||!"ctsv-mghp-p04-20261112".equals(r.getString(4))||!r.getString(5).equals(r.getString(6)))throw new IllegalStateException("P04 target mismatch");
   }
   var flyway=org.flywaydb.core.Flyway.configure().dataSource(ds).cleanDisabled(true).baselineOnMigrate(false).load();flyway.migrate();flyway.validate();return ds;
  }
 }
 @DynamicPropertySource static void tls(DynamicPropertyRegistry r)throws Exception{AuthHttpIT.configuration(r);}
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 @Autowired AuthAdministration admin;
 @Autowired AuthAccess access;
 @Autowired AuthService authService;
 String cvToken,svToken,managerToken;long cv,sv,manager,semester,process,student,role;
 static final String PASSWORD="Synthetic Foundation "+UUID.randomUUID();
 record Reply(int status,HttpHeaders headers,String body){
  JsonNode json()throws Exception{return new ObjectMapper().readTree(body);}
  String tag(){return headers.firstValue("ETag").orElseThrow();}
  void status(int wanted){assertThat(status).as("HTTP status").isEqualTo(wanted);}
 }
 Reply send(String method,String path,Object body,String token,String tag)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
  if(token!=null)b.header("Authorization","Bearer "+token);
  if(tag!=null)b.header("If-Match",tag);
  if(body!=null)b.header("Content-Type","application/json");
  if(path.startsWith("/api/xac-thuc/"))b.header("Origin",AuthHttpIT.ORIGIN).header("X-CSRF-Protection","1");
  b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(body)));
  try(var client=HttpClient.newBuilder().sslContext(AuthHttpIT.testTls).build()){
   var r=client.send(b.build(),HttpResponse.BodyHandlers.ofString());return new Reply(r.statusCode(),r.headers(),r.body());
  }
 }
 long insert(String sql,Object...args){return jdbc.queryForObject(sql+";SELECT CAST(SCOPE_IDENTITY() AS bigint)",Long.class,args);}
 String[] identity(String localRole,boolean student)throws Exception{
  String name="p04."+UUID.randomUUID();UUID account=UUID.randomUUID();
  var credentials=new AuthCredentials();
  jdbc.update("INSERT dbo.TaiKhoan(ID,TenDangNhap,TenDangNhapChuanHoa,MatKhauHash) VALUES(?,?,?,?)",account.toString(),name,name,credentials.hash(PASSWORD));
  jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'CTSV_USER'",account.toString());
  long user=insert("INSERT dbo.nguoi_dung(dia_chi_email,dang_hoat_dong) VALUES(?,1)",name+"@example.invalid");
  jdbc.update("INSERT dbo.dinh_danh_dang_nhap(nguoi_dung_id,ma_nha_cung_cap,ma_chu_the_dang_nhap,thoi_diem_xac_minh) VALUES(?,?,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))",user,"https://auth.ctsv.test",account.toString());
  if(localRole!=null)jdbc.update("INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he) SELECT ?,id,? FROM dbo.vai_tro WHERE ma=?",user,localRole.equals("ctsv.roles.manage")?"CTSV":"MGHP",localRole);
  long studentId=student?insert("INSERT dbo.sinh_vien(mssv,ho_ten,nguoi_dung_id,ma_dinh_danh_ben_ngoai) VALUES(?,?,?,?)","TEST-"+UUID.randomUUID(),"Sinh viên giả",user,"101"):0;
  var login=send("POST","/api/xac-thuc/dang-nhap",Map.of("username",name,"password",PASSWORD),null,null);login.status(200);
  return new String[]{Long.toString(user),login.json().path("accessToken").asText(),Long.toString(studentId)};
 }
 @BeforeAll void fixture()throws Exception{
  var c=identity("mghp_chuyen_vien",false);cv=Long.parseLong(c[0]);cvToken=c[1];
  jdbc.update("INSERT dbo.can_bo(ma_cb,nguoi_dung_id,ho_ten) VALUES(?,?,N'Chuyên viên giả lập P04')","P04-CV-"+UUID.randomUUID(),cv);
  var s=identity(null,true);sv=Long.parseLong(s[0]);svToken=s[1];student=Long.parseLong(s[2]);
  var m=identity("ctsv.roles.manage",false);manager=Long.parseLong(m[0]);managerToken=m[1];
  semester=insert("INSERT dbo.hoc_ky(ma) VALUES(?)","P04-"+UUID.randomUUID());
  process=insert("INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,ma_qua_trinh_hoc) VALUES(?,?)",student,"QT-"+UUID.randomUUID());
  role=jdbc.queryForObject("SELECT id FROM dbo.vai_tro WHERE ma='mghp_chuyen_vien'",Long.class);
 }
 static final String BASE="/api/ctsv/nen-tang";
 @Test void identityFreshStateAndLocalPermissionsAreEnforced()throws Exception{
  send("GET",BASE+"/sinh-vien",null,null,null).status(401);
  send("GET",BASE+"/sinh-vien",null,svToken,null).status(403);
  send("GET",BASE+"/danh-muc?kind=semesters",null,svToken,null).status(200);
  jdbc.update("UPDATE dbo.nguoi_dung SET dang_hoat_dong=NULL WHERE id=?",sv);
  try{send("GET",BASE+"/danh-muc?kind=semesters",null,svToken,null).status(403);}
  finally{jdbc.update("UPDATE dbo.nguoi_dung SET dang_hoat_dong=1 WHERE id=?",sv);}
 }
 @Test void studentProjectionAndPagingHaveLocalIdsAndMatchingCount()throws Exception{
  var list=send("GET",BASE+"/sinh-vien?q=TEST-&size=100",null,cvToken,null);list.status(200);
  assertThat(list.headers.firstValue("Cache-Control")).contains("no-store");
  assertThat(list.json().path("totalElements").asInt()).isGreaterThan(0);
  var detail=send("GET",BASE+"/sinh-vien/"+student,null,cvToken,null);detail.status(200);
  assertThat(detail.json().path("id").asText()).isEqualTo(""+student);
  assertThat(detail.json().path("sourceId").asText()).isEqualTo("101");
  assertThat(detail.json().has("cccd")).isFalse();
  send("GET",BASE+"/danh-muc?kind=unknown",null,svToken,null).status(400);
  send("GET",BASE+"/danh-muc?kind=semesters&size=101",null,svToken,null).status(400);
 }
 @Test void semesterVerificationUsesStrongTagAndNoOp()throws Exception{
  String path=BASE+"/hoc-ky/"+semester;
  var old=send("GET",path,null,cvToken,null);old.status(200);
  int order=jdbc.queryForObject("SELECT COALESCE(MAX(so_thu_tu),0)+1 FROM dbo.hoc_ky",Integer.class);
  var body=Map.of("order",order,"startDate","2026-09-01","endDate","2027-01-31","evidence","TEST-CALENDAR-01");
  send("PUT",path+"/xac-minh",body,cvToken,null).status(428);
  var update=send("PUT",path+"/xac-minh",body,cvToken,old.tag());update.status(200);
  assertThat(update.headers.firstValue("ETag")).isEmpty();
  send("PUT",path+"/xac-minh",body,cvToken,old.tag()).status(412);
  var current=send("GET",path,null,cvToken,null);
  send("PUT",path+"/xac-minh",body,cvToken,current.tag()).status(200);
  assertThat(send("GET",path,null,cvToken,null).tag()).isEqualTo(current.tag());
 }
 @Test void historyAppendPreservesProcessCodeAndRejectsOverlap()throws Exception{
  String path=BASE+"/qua-trinh-hoc/"+process;
  var old=send("GET",path+"/lich-su",null,cvToken,null);old.status(200);
  String code=old.json().path("code").asText();
  var body=new LinkedHashMap<String,Object>();for(String key:List.of("programId","cohortId","startSemesterId","endSemesterId","actualStart","expectedEnd","actualEnd","evidenceFileId"))body.put(key,null);
  body.put("actualStart","2026-09-01");body.put("evidence","TEST-VERIFICATION");
  send("PUT",path+"/xac-minh",body,cvToken,old.tag()).status(200);
  var current=send("GET",path+"/lich-su",null,cvToken,null);
  var status=Map.of("semesterId",""+semester,"status","SOURCE_CONFIRMED","from","2026-09-01","to","2026-12-31","decisionNumber","TEST","decisionDate","2026-08-01","evidence","TEST-HISTORY");
  var added=send("POST",path+"/lich-su-trang-thai",status,cvToken,current.tag());added.status(201);
  assertThat(added.json().path("code").asText()).isEqualTo(code);
  send("POST",path+"/lich-su-trang-thai",status,cvToken,added.tag()).status(409);
 }
 @Test void localRoleRevokeDoesNotRevokeAuthAndKeepsHistory()throws Exception{
  var target=identity(null,false);long user=Long.parseLong(target[0]);
  String path=BASE+"/nguoi-dung/"+user+"/vai-tro-mghp";
  var old=send("GET",path,null,managerToken,null);old.status(200);
  var grant=send("PUT",path+"/"+role,null,managerToken,old.tag());grant.status(200);
  assertThat(grant.headers.firstValue("ETag")).isEmpty();
  var current=send("GET",path,null,managerToken,null);
  send("PUT",path+"/"+role,null,managerToken,current.tag()).status(200);
  assertThat(send("GET",path,null,managerToken,null).tag()).isEqualTo(current.tag());
  var revoke=send("DELETE",path+"/"+role,null,managerToken,current.tag());revoke.status(200);
  assertThat(revoke.json().path("roles")).isEmpty();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.phan_quyen_nguoi_dung WHERE nguoi_dung_id=? AND hieu_luc_den IS NOT NULL",Integer.class,user)).isEqualTo(1);
  send("GET","/api/xac-thuc/toi",null,target[1],null).status(200);
 }
 @Test void classHistoryAppendUsesLocalFkAndRejectsOverlap()throws Exception{
  long p=insert("INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,ma_qua_trinh_hoc) VALUES(?,?)",student,"QT-CLASS-"+UUID.randomUUID());
  long c=insert("INSERT dbo.lop_sinh_hoat(ma) VALUES(?)","CLASS-"+UUID.randomUUID());
  String path=BASE+"/qua-trinh-hoc/"+p;
  var old=send("GET",path+"/lich-su",null,cvToken,null);
  var content=new LinkedHashMap<String,Object>();content.put("classId",""+c);content.put("from","2026-09-01");content.put("until",null);content.put("evidence","TEST-CLASS");
  var added=send("POST",path+"/lich-su-lop",content,cvToken,old.tag());added.status(201);
  assertThat(added.json().path("classHistory").get(0).path("classId").asText()).isEqualTo(""+c);
  send("POST",path+"/lich-su-lop",content,cvToken,added.tag()).status(409);
 }
 @Test void localRevocationHasImmediateEffectWhileAuthAccountStaysValid()throws Exception{
  var target=identity("mghp_chuyen_vien",false);String token=target[1];
  send("GET",BASE+"/sinh-vien",null,token,null).status(200);
  String path=BASE+"/nguoi-dung/"+target[0]+"/vai-tro-mghp";
  var state=send("GET",path,null,managerToken,null);
  send("DELETE",path+"/"+role,null,managerToken,state.tag()).status(200);
  send("GET",BASE+"/sinh-vien",null,token,null).status(403);
  send("GET","/api/xac-thuc/toi",null,token,null).status(200);
 }
 @Test void ordinaryLookupRemainsLocalButSensitiveReadRejectsRevokedJwt()throws Exception{
  var target=identity("mghp_chuyen_vien",true);
  var principal=access.verifyAccessToken(target[1]);
  var auth=authService;
  auth.transaction(()->{auth.revoke(auth.db.account(principal.accountId(),true),null,"Synthetic P04 revoke",null,auth.now(),UUID.randomUUID());return null;});
  send("GET",BASE+"/danh-muc?kind=semesters",null,target[1],null).status(200);
  send("GET",BASE+"/sinh-vien",null,target[1],null).status(401);
 }
 @Test void grantAuditFailureRollsBackAssignmentAndRootTag()throws Exception{
  var target=identity(null,false);String path=BASE+"/nguoi-dung/"+target[0]+"/vai-tro-mghp";
  var state=send("GET",path,null,managerToken,null);int before=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.nhat_ky_thao_tac WHERE doi_tuong_id=? AND loai_doi_tuong='nguoi_dung'",Integer.class,target[0]);
  FAULT.set("INSERT dbo.nhat_ky_thao_tac");
  try{send("PUT",path+"/"+role,null,managerToken,state.tag()).status(500);}finally{FAULT.set(null);}
  var actual=send("GET",path,null,managerToken,null);assertThat(actual.tag()).isEqualTo(state.tag());assertThat(actual.json().path("roles")).isEmpty();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.nhat_ky_thao_tac WHERE doi_tuong_id=? AND loai_doi_tuong='nguoi_dung'",Integer.class,target[0])).isEqualTo(before);
 }
 @Test void twoRoleWritersCannotCommitWithTheSameAggregateTag()throws Exception{
  var target=identity(null,false);String path=BASE+"/nguoi-dung/"+target[0]+"/vai-tro-mghp";
  var state=send("GET",path,null,managerToken,null);
  try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var start=new java.util.concurrent.CountDownLatch(1);
   var a=pool.submit(()->{start.await();return send("PUT",path+"/"+role,null,managerToken,state.tag());});
   var b=pool.submit(()->{start.await();return send("PUT",path+"/"+role,null,managerToken,state.tag());});
   start.countDown();assertThat(List.of(a.get().status,b.get().status)).containsExactlyInAnyOrder(200,412);
  }
 }
 @Test void validationUnknownQueriesBodyAndForbiddenRoleAreRejected()throws Exception{
  for(String query:List.of("kind=semesters&size=0","kind=semesters&q="+"x".repeat(129),"kind=semesters&page=100001","kind=semesters&unexpected=1","kind=semesters&kind=units"))
   send("GET",BASE+"/danh-muc?"+query,null,svToken,null).status(400);
  send("GET",BASE+"/sinh-vien/0",null,cvToken,null).status(400);
  send("GET",BASE+"/danh-muc?kind=semesters",Map.of("extra",true),svToken,null).status(400);
  String path=BASE+"/nguoi-dung/"+cv+"/vai-tro-mghp";var state=send("GET",path,null,managerToken,null);
  long forbidden=jdbc.queryForObject("SELECT id FROM dbo.vai_tro WHERE ma='ctsv.roles.manage'",Long.class);
  send("PUT",path+"/"+forbidden,null,managerToken,state.tag()).status(409);
  send("PUT",path+"/"+role,Map.of("unitId","1"),managerToken,state.tag()).status(400);
 }
 @Test void nullableLookupKeepsNullCodeAndCohortHasNoInventedName()throws Exception{
  jdbc.update("INSERT dbo.dan_toc(ma,ten) VALUES(NULL,N'TEST NULL CODE')");
  var emptyCode=send("GET",BASE+"/danh-muc?kind=ethnicities&q=TEST%20NULL%20CODE&size=100",null,svToken,null);emptyCode.status(200);
  assertThat(emptyCode.json().path("items").get(0).path("code").isNull()).isTrue();
  long cohort=insert("INSERT dbo.khoa_hoc(ma) VALUES(?)","P04-COHORT-"+UUID.randomUUID());
  var cohorts=send("GET",BASE+"/danh-muc?kind=cohorts&q=P04-COHORT&size=100",null,svToken,null);cohorts.status(200);
  assertThat(cohorts.json().path("items").get(0).path("name").isNull()).isTrue();
 }
 @Test void migrationHasThreeConcurrencyRootsAndCurrentRoleUniqueness(){
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE success=1",Integer.class)).isEqualTo(7);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.columns WHERE OBJECT_SCHEMA_NAME(object_id)='dbo' AND OBJECT_NAME(object_id) IN ('hoc_ky','nguoi_dung','qua_trinh_theo_hoc') AND name='phien_ban' AND system_type_id=189",Integer.class)).isEqualTo(3);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.indexes WHERE name='ux_foundation_current_role' AND is_unique=1 AND has_filter=1",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.vai_tro WHERE ma IN ('mghp_chuyen_vien','mghp_pho_phong','ctsv.roles.manage')",Integer.class)).isEqualTo(3);
 }
 @Test void semesterAuditFaultLeavesDatesAndTagUnchanged()throws Exception{
  long h=insert("INSERT dbo.hoc_ky(ma) VALUES(?)","P04-FAULT-"+UUID.randomUUID());String path=BASE+"/hoc-ky/"+h;
  var old=send("GET",path,null,cvToken,null);
  int order=jdbc.queryForObject("SELECT COALESCE(MAX(so_thu_tu),0)+1 FROM dbo.hoc_ky",Integer.class);
  var body=Map.of("order",order,"startDate","2026-09-01","endDate","2027-01-31","evidence","TEST-FAULT");
  FAULT.set("INSERT dbo.nhat_ky_thao_tac");try{send("PUT",path+"/xac-minh",body,cvToken,old.tag()).status(500);}finally{FAULT.set(null);}
  var actual=send("GET",path,null,cvToken,null);assertThat(actual.tag()).isEqualTo(old.tag());assertThat(actual.json().path("startDate").isNull()).isTrue();
 }
 @Test void localIdentityBindingCannotBeTakenFromClientOrEmail()throws Exception{
  var target=identity(null,true);
  jdbc.update("UPDATE dbo.dinh_danh_dang_nhap SET thoi_diem_xac_minh=NULL WHERE nguoi_dung_id=?",Long.parseLong(target[0]));
  send("GET",BASE+"/danh-muc?kind=semesters",null,target[1],null).status(403);
  AUTH_UNAVAILABLE.set(true);try{send("GET",BASE+"/sinh-vien",null,target[1],null).status(403);}finally{AUTH_UNAVAILABLE.set(false);}
  send("GET",BASE+"/danh-muc?kind=semesters&accountId="+access.verifyAccessToken(cvToken).accountId(),null,svToken,null).status(400);
 }
 @Test void unavailableAuthFailsClosedForSensitiveGetButNotOrdinaryLookup()throws Exception{
  AUTH_UNAVAILABLE.set(true);
  try{
   send("GET",BASE+"/sinh-vien",null,cvToken,null).status(503);
   send("GET",BASE+"/danh-muc?kind=semesters",null,svToken,null).status(200);
  }finally{AUTH_UNAVAILABLE.set(false);}
 }
 @Test void historyAuditFailureRollsBackChildAndRoot()throws Exception{
  long p=insert("INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,ma_qua_trinh_hoc) VALUES(?,?)",student,"QT-FAULT-"+UUID.randomUUID());
  String path=BASE+"/qua-trinh-hoc/"+p;
  var old=send("GET",path+"/lich-su",null,cvToken,null);
  var body=new LinkedHashMap<String,Object>();body.put("semesterId",""+semester);body.put("status","TEST");body.put("from","2026-09-01");body.put("to",null);body.put("decisionNumber",null);body.put("decisionDate",null);body.put("evidence","TEST-FAULT");
  FAULT.set("INSERT dbo.nhat_ky_thao_tac");try{send("POST",path+"/lich-su-trang-thai",body,cvToken,old.tag()).status(500);}finally{FAULT.set(null);}
  var actual=send("GET",path+"/lich-su",null,cvToken,null);assertThat(actual.tag()).isEqualTo(old.tag());assertThat(actual.json().path("statusHistory")).isEmpty();
 }
 @Test void corsAllowsConfiguredOriginAndRejectsOthers()throws Exception{
  for(String origin:List.of(AuthHttpIT.ORIGIN,"https://attacker.invalid")){
   var request=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+BASE+"/sinh-vien"))
    .header("Origin",origin).header("Access-Control-Request-Method","GET").header("Access-Control-Request-Headers","Authorization")
    .method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build();
   try(var client=HttpClient.newBuilder().sslContext(AuthHttpIT.testTls).build()){
    var response=client.send(request,HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(origin.equals(AuthHttpIT.ORIGIN)?200:403);
    if(origin.equals(AuthHttpIT.ORIGIN))assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(origin);
   }
  }
 }
 @Test void confirmedFactsBlockSemesterAndProcessRewrites()throws Exception{
  long h=insert("INSERT dbo.hoc_ky(ma) VALUES(?)","P04-USED-"+UUID.randomUUID());
  long p=insert("INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,ma_qua_trinh_hoc) VALUES(?,?)",student,"QT-USED-"+UUID.randomUUID());
  jdbc.update("IF NOT EXISTS(SELECT 1 FROM dbo.mghp_chinh_sach WHERE ma=N'M2') INSERT dbo.mghp_chinh_sach(ma) VALUES(N'M2')");
  long policy=jdbc.queryForObject("SELECT id FROM dbo.mghp_chinh_sach WHERE ma=N'M2'",Long.class);
  int next=jdbc.queryForObject("SELECT COALESCE(MAX(phien_ban),0)+1 FROM dbo.mghp_phien_ban_chinh_sach WHERE chinh_sach_id=?",Integer.class,policy);
  long revision=insert("INSERT dbo.mghp_phien_ban_chinh_sach(chinh_sach_id,phien_ban,ty_le_giam,ma_pham_vi) VALUES(?,?,1,N'hoc_phi_chuong_trinh_chinh')",policy,next);
  long benefit=insert("INSERT dbo.mghp_quyen_huong(ma_quyen_huong,sinh_vien_id,qua_trinh_hoc_id,ma_pham_vi,chinh_sach_id) VALUES(?,?,?,N'hoc_phi_chuong_trinh_chinh',?)","P04-BEN-"+UUID.randomUUID(),student,p,policy);
  jdbc.update("INSERT dbo.mghp_danh_gia_dieu_kien(sinh_vien_id,qua_trinh_hoc_id,hoc_ky_ap_dung_id,phien_ban_chinh_sach_id,quyen_huong_nguon_id,so_phien_ban_danh_gia,ma_pham_vi,trang_thai_xu_ly) VALUES(?,?,?,?,?,1,N'hoc_phi_chuong_trinh_chinh',N'confirmed')",student,p,h,revision,benefit);
  String path=BASE+"/hoc-ky/"+h;var state=send("GET",path,null,cvToken,null);
  int order=jdbc.queryForObject("SELECT COALESCE(MAX(so_thu_tu),0)+1 FROM dbo.hoc_ky",Integer.class);
  send("PUT",path+"/xac-minh",Map.of("order",order,"startDate","2026-09-01","endDate","2027-01-31","evidence","TEST-IN-USE"),cvToken,state.tag()).status(409);
  path=BASE+"/qua-trinh-hoc/"+p;state=send("GET",path+"/lich-su",null,cvToken,null);
  var body=new LinkedHashMap<String,Object>();for(String field:List.of("programId","cohortId","startSemesterId","endSemesterId","actualStart","expectedEnd","actualEnd","evidenceFileId"))body.put(field,null);body.put("evidence","TEST-IN-USE");
  send("PUT",path+"/xac-minh",body,cvToken,state.tag()).status(409);
 }
 @Test void semesterInvalidDatesAndEvidenceCannotChangeRoot()throws Exception{
  long h=insert("INSERT dbo.hoc_ky(ma) VALUES(?)","P04-INVALID-"+UUID.randomUUID());String path=BASE+"/hoc-ky/"+h;
  var original=send("GET",path,null,cvToken,null);
  for(var body:List.of(Map.of("order",0,"startDate","2026-09-01","endDate","2027-01-31","evidence","TEST"),
   Map.of("order",1,"startDate","2027-09-01","endDate","2026-01-31","evidence","TEST"),
   Map.of("order",1,"startDate","2026-09-01","endDate","2027-01-31","evidence","")))
    send("PUT",path+"/xac-minh",body,cvToken,original.tag()).status(400);
  assertThat(send("GET",path,null,cvToken,null).tag()).isEqualTo(original.tag());
 }
 @Test void authAdminAndTeacherDoNotAcquireMghpRightsFromTheirAccountOrProfile()throws Exception{
  var a=identity(null,false);var principal=access.verifyAccessToken(a[1]);
  jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'AUTH_ADMIN'",principal.accountId().toString());
  send("GET",BASE+"/sinh-vien",null,a[1],null).status(403);
  var teacher=identity(null,false);
  jdbc.update("INSERT dbo.can_bo(ma_cb,nguoi_dung_id,ho_ten,la_giang_vien) VALUES(?,?,N'Giảng viên giả lập P04',1)","P04-GV-"+UUID.randomUUID(),Long.parseLong(teacher[0]));
  send("GET",BASE+"/sinh-vien",null,teacher[1],null).status(403);
 }
}
