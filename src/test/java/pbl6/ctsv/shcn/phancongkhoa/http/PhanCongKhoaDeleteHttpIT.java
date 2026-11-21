package pbl6.ctsv.shcn.phancongkhoa.http;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import static org.assertj.core.api.Assertions.*;
/** Reuses guarded API test DB. Foundation/Auth SQL real; only SHCN entities managed here.
 * No Flyway migration or schema creation. Run only draftDelete* methods, not inherited suite. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
 classes={PhanCongKhoaDeleteHttpIT.Application.class,PhanCongKhoaWorkflowHttpIT.Database.class},properties={
 "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
 "spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.open-in-view=false",
 "spring.jpa.properties.hibernate.hbm2ddl.jdbc_metadata_extraction_strategy=individually",
 "ctsv.auth.enabled=true","ctsv.auth.bootstrap-enabled=false","ctsv.foundation.enabled=true",
 "ctsv.shcn.persistence.enabled=true","server.address=127.0.0.1"})
@ActiveProfiles("shcn-delete-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhanCongKhoaDeleteHttpIT {
 @LocalServerPort int port;
 @Autowired JdbcTemplate jdbc;
 final PhanCongKhoaWorkflowHttpIT h=new PhanCongKhoaWorkflowHttpIT();
 final ObjectMapper json=new ObjectMapper();
 ShcnApiFixtures f;
 static final java.util.concurrent.atomic.AtomicBoolean FAIL_AUDIT=PhanCongKhoaWorkflowHttpIT.FAIL_AUDIT;
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){PhanCongKhoaWorkflowHttpIT.properties(r);}
 @BeforeAll void fixture()throws Exception{h.port=port;h.jdbc=jdbc;h.fixture();f=h.f;}
 JsonNode draft()throws Exception{return h.draft();}
 JsonNode saved(JsonNode root,JsonNode... lines)throws Exception{return h.saved(root,lines);}
 ObjectNode line(String cls,String gv,String note){return h.line(cls,gv,note);}
 ObjectNode createBody(String faculty,String year){return h.createBody(faculty,year);}
 JsonNode create(ShcnApiFixtures.Actor actor,ObjectNode body)throws Exception{return h.create(actor,body);}
 PhanCongKhoaWorkflowHttpIT.Reply call(String method,String path,ShcnApiFixtures.Actor actor,JsonNode body)throws Exception{return h.call(method,path,actor,body);}
 void error(PhanCongKhoaWorkflowHttpIT.Reply reply,int status,String code){h.error(reply,status,code);}

 @Configuration(proxyBeanMethods=false) @Profile("shcn-delete-test") @EnableAutoConfiguration
 @EntityScan("pbl6.ctsv.shcn.phancongkhoa.entity")
 @ComponentScan(basePackages={"pbl6.ctsv.auth","pbl6.ctsv.foundation","pbl6.ctsv.shcn","pbl6.ctsv.shared"},excludeFilters=@ComponentScan.Filter(type=FilterType.CUSTOM,classes=TypeExcludeFilter.class))
 static class Application {}

 com.fasterxml.jackson.databind.node.ObjectNode request(com.fasterxml.jackson.databind.JsonNode root){return json.createObjectNode().put("expectedVersion",root.path("version").asText());}
 @Test void draftDeleteSuccessKeepsHistoryAndMonotonicVersions()throws Exception{
  var created=draft();var root=saved(created,line(f.lopA1,f.gvA,null));String id=root.path("id").asText();
  assertThat(call("DELETE","/phan-cong/"+id,f.a,request(root)).status()).isEqualTo(204);
  error(call("GET","/phan-cong/"+id,f.a,null),404,"NOT_FOUND");
  assertThat(jdbc.queryForObject("SELECT trang_thai FROM shcn.shcn_danh_sach_phan_cong_khoa WHERE id=?",String.class,Long.valueOf(id))).isEqualTo("DA_HUY");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shcn.shcn_phan_cong_gvcn_khoa WHERE ma_danh_sach_khoa=?",Integer.class,Long.valueOf(id))).isEqualTo(1);
  var list=call("GET","/phan-cong?maKhoa="+f.khoaA+"&namHoc="+root.path("namHoc").asText(),f.a,null);assertThat(list.status()).isEqualTo(200);assertThat(list.body().path("totalElements").asInt()).isZero();assertThat(list.body().path("phienBanLonNhat").asInt()).isEqualTo(1);
  var next=create(f.a,createBody(f.khoaA,root.path("namHoc").asText()).put("phienBanDuKien",2));assertThat(next.path("phienBan").asInt()).isEqualTo(2);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.nhat_ky_thao_tac WHERE hanh_dong='SHCN_KHOA_DELETE_DRAFT' AND doi_tuong_id=?",Integer.class,id)).isEqualTo(1);
 }
 @Test void draftDeleteChecksCapabilityScopeAndAuthentication()throws Exception{var root=draft();String path="/phan-cong/"+root.path("id").asText();error(call("DELETE",path,f.reader,request(root)),403,"FORBIDDEN");error(call("DELETE",path,f.b,request(root)),404,"NOT_FOUND");error(call("DELETE",path,null,request(root)),401,"UNAUTHENTICATED");assertThat(call("GET",path,f.a,null).status()).isEqualTo(200);}
 @Test void draftDeleteRejectsStaleToken()throws Exception{var root=draft();saved(root,line(f.lopA1,f.gvA,null));error(call("DELETE","/phan-cong/"+root.path("id").asText(),f.a,request(root)),409,"VERSION_CONFLICT");}
 @Test void draftDeleteRejectsClosedRoot()throws Exception{var root=draft();jdbc.update("UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET trang_thai='DA_KY' WHERE id=?",root.path("id").asLong());var fresh=call("GET","/phan-cong/"+root.path("id").asText(),f.a,null).body();error(call("DELETE","/phan-cong/"+root.path("id").asText(),f.a,request(fresh)),409,"SHCN_KHONG_CON_LA_NHAP");}
 @Test void draftDeleteRejectsInvalidBody()throws Exception{var root=draft();String path="/phan-cong/"+root.path("id").asText();error(call("DELETE",path,f.a,request(root).put("maKhoa",f.khoaB)),400,"VALIDATION_ERROR");error(call("DELETE",path,f.a,request(root).put("expectedVersion","wrong")),400,"VALIDATION_ERROR");}
 @Test void draftDeleteAuditFailureRollsBack()throws Exception{var root=draft();String path="/phan-cong/"+root.path("id").asText();FAIL_AUDIT.set(true);try{error(call("DELETE",path,f.a,request(root)),500,"INTERNAL_ERROR");}finally{FAIL_AUDIT.set(false);}assertThat(call("GET",path,f.a,null).body().path("trangThai").asText()).isEqualTo("NHAP");}
 @Test void draftDeleteRepeatedRequestCannotAffectAnotherRecord()throws Exception{var root=draft();String path="/phan-cong/"+root.path("id").asText();assertThat(call("DELETE",path,f.a,request(root)).status()).isEqualTo(204);error(call("DELETE",path,f.a,request(root)),404,"NOT_FOUND");}
}
