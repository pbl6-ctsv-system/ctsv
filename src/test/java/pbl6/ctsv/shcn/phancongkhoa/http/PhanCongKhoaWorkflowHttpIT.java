package pbl6.ctsv.shcn.phancongkhoa.http;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.networknt.schema.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTPS -> real Auth/fresh account state -> real Foundation identity/RBAC -> real SQL/JPA.
 * Only identities/catalogue fixtures and signing/TLS keys are synthetic. No production endpoint is mocked. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes={pbl6.ctsv.CtsvApplication.class,PhanCongKhoaWorkflowHttpIT.Database.class},properties={
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
    "spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=validate","spring.jpa.open-in-view=false",
    "spring.jpa.properties.hibernate.hbm2ddl.jdbc_metadata_extraction_strategy=individually",
    "ctsv.auth.enabled=true","ctsv.auth.bootstrap-enabled=false","ctsv.foundation.enabled=true",
    "ctsv.shcn.persistence.enabled=true","server.address=127.0.0.1"})
class PhanCongKhoaWorkflowHttpIT {
    static final ShcnApiTestKeys KEYS=new ShcnApiTestKeys();
    static final AtomicBoolean FAIL_AUDIT=new AtomicBoolean();
    @TestConfiguration(proxyBeanMethods=false) static class Database {
        @Bean DataSource shcnApiSource()throws Exception{return ShcnApiDatabase.source();}
        @Bean JdbcTemplate jdbcTemplate(DataSource ds){return new JdbcTemplate(ds){
            @Override public int update(String sql,Object... args){
                int count=super.update(sql,args);
                if(sql.contains("INSERT dbo.nhat_ky_thao_tac")&&FAIL_AUDIT.compareAndSet(true,false))
                    super.execute("THROW 51999,'Synthetic SHCN audit failure after insert',1");
                return count;
            }
        };}
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        KEYS.properties(r);
        // No role-list authorization configuration: permissions must come exclusively from DB.
        r.add("ctsv.shcn.phan-cong-khoa.source-code",()->ShcnApiFixtures.SOURCE);
        r.add("ctsv.shcn.phan-cong-khoa.staff-mapping-verified",()->true);
        r.add("ctsv.shcn.phan-cong-khoa.staff-code-column",()->"MA_CB");
        r.add("ctsv.shcn.phan-cong-khoa.employment-statuses[0]",()->"SYNTH_ACTIVE");
        r.add("ctsv.shcn.phan-cong-khoa.employment-end-inclusive",()->true);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper json=new ObjectMapper();
    ShcnApiFixtures f;
    HttpClient client;
    JsonNode schemas;
    @BeforeAll void fixture()throws Exception{
        f=new ShcnApiFixtures(jdbc,KEYS);f.seed();
        client=HttpClient.newBuilder().sslContext(KEYS.tls).connectTimeout(Duration.ofSeconds(10)).build();
        try(var input=getClass().getResourceAsStream("/shcn/phan-cong-khoa.openapi.json")){
            schemas=json.readTree(input).path("components").path("schemas");
        }
    }
    record Reply(int status,JsonNode body,HttpHeaders headers){}
    Reply call(String method,String path,ShcnApiFixtures.Actor actor,JsonNode body)throws Exception{
        return raw(method,path,actor==null?null:actor.token(),body==null?null:json.writeValueAsString(body),"application/json");
    }
    Reply raw(String method,String path,String token,String body,String contentType)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+"/api/shcn/khoa"+path)).timeout(Duration.ofSeconds(30));
        if(token!=null)b.header("Authorization","Bearer "+token);
        if(body!=null)b.header("Content-Type",contentType);
        b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8));
        var result=client.send(b.build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(result.statusCode(),result.body().isBlank()?NullNode.getInstance():json.readTree(result.body()),result.headers());
    }
    void error(Reply r,int status,String code){
        assertThat(r.status()).as(r.body().toString()).isEqualTo(status);
        assertThat(r.body().path("code").asText()).isEqualTo(code);
        assertThat(r.body().path("status").asInt()).isEqualTo(status);
        for(String key:List.of("type","title","detail","instance","traceId"))assertThat(r.body().hasNonNull(key)).as(key).isTrue();
        assertThat(r.headers().firstValue("content-type").orElse("")).startsWith("application/problem+json");
        assertThat(r.headers().firstValue("cache-control").orElse("")).contains("no-store");
        if(status==400)assertThat(r.body().path("errors").isArray()).isTrue();
    }
    void schema(String name,JsonNode body)throws Exception{
        var wrapper=json.createObjectNode().put("$schema","https://json-schema.org/draft/2020-12/schema").put("$ref","#/$defs/"+name);
        wrapper.set("$defs",json.readTree(schemas.toString().replace("#/components/schemas/","#/$defs/")));
        var config=new SchemaValidatorsConfig();config.setFormatAssertionsEnabled(true);
        assertThat(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(wrapper,config).validate(body)).isEmpty();
    }
    String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    String at(String resource,String faculty){return "/"+resource+"?maKhoa="+enc(faculty);}
    ObjectNode createBody(String faculty,String year){
        return json.createObjectNode().put("maKhoa",faculty).put("namHoc",year).put("phienBanDuKien",1).putNull("maDanhSachTruoc")
            .put("hieuLucTu","2026-09-01T07:00:00+07:00").putNull("hieuLucDen").putNull("lyDoDieuChinh");
    }
    JsonNode draft()throws Exception{return create(f.a,createBody(f.khoaA,f.year()));}
    JsonNode create(ShcnApiFixtures.Actor actor,ObjectNode body)throws Exception{
        var r=call("POST","/phan-cong",actor,body);assertThat(r.status()).as(r.body().toString()).isEqualTo(201);
        assertThat(r.headers().firstValue("Location")).contains("/api/shcn/khoa/phan-cong/"+r.body().path("id").asText());
        schema("DanhSachDTO",r.body());return r.body();
    }
    ObjectNode line(String cls,String gv,String note){var row=json.createObjectNode().put("maLop",cls).put("maGiaoVien",gv);if(note==null)row.putNull("ghiChu");else row.put("ghiChu",note);return row;}
    ObjectNode saveBody(JsonNode root,JsonNode... lines){
        var body=json.createObjectNode().put("expectedVersion",root.path("version").asText()).put("hieuLucTu","2026-09-01T00:00:00Z").putNull("hieuLucDen").putNull("lyDoDieuChinh");
        var rows=body.putArray("cacPhanCong");for(var row:lines)rows.add(row);return body;
    }
    Reply save(JsonNode root,ObjectNode body)throws Exception{return call("PUT","/phan-cong/"+root.path("id").asText(),f.a,body);}
    JsonNode saved(JsonNode root,JsonNode... lines)throws Exception{
        var r=save(root,saveBody(root,lines));assertThat(r.status()).as(r.body().toString()).isEqualTo(200);schema("DanhSachDTO",r.body());return r.body();
    }
    JsonNode detail(JsonNode root)throws Exception{
        var r=call("GET","/phan-cong/"+root.path("id").asText(),f.a,null);assertThat(r.status()).isEqualTo(200);return r.body();
    }
    long audit(JsonNode root){return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.nhat_ky_thao_tac WHERE loai_doi_tuong=N'shcn_danh_sach_phan_cong_khoa' AND doi_tuong_id=?",Long.class,root.path("id").asText());}
    @Test void bearerAndFreshAccountStateAreEnforcedOnAllRoutes()throws Exception{
        for(String path:List.of("/pham-vi",at("chuong-trinh",f.khoaA),at("lop",f.khoaA),at("giang-vien",f.khoaA),at("phan-cong",f.khoaA)+"&namHoc=Y","/phan-cong/1")){
            error(call("GET",path,null,null),401,"UNAUTHENTICATED");
            assertThat(raw("GET",path,"invalid",null,"application/json").status()).isEqualTo(401);
        }
        error(call("POST","/phan-cong",null,createBody(f.khoaA,f.year())),401,"UNAUTHENTICATED");
        error(call("PUT","/phan-cong/1",null,json.createObjectNode()),401,"UNAUTHENTICATED");
        assertThat(raw("GET","/pham-vi",KEYS.token(f.a.account(),0,Instant.now().minusSeconds(1000)),null,"application/json").status()).isEqualTo(401);
        var stale=f.actor("REVOKED",ShcnApiFixtures.READ,f.facultyA);
        jdbc.update("UPDATE dbo.TaiKhoan SET PhienBanToken=1 WHERE ID=?",stale.account().toString());
        assertThat(call("GET","/pham-vi",stale,null).status()).isEqualTo(401);
    }
    @Test void scopeAndCapabilitiesComeFromExplicitRbac()throws Exception{
        var r=call("GET","/pham-vi",f.a,null);assertThat(r.status()).isEqualTo(200);schema("PhamViDTO",r.body());
        assertThat(r.body().path("khoa").size()).isEqualTo(1);assertThat(r.body().path("khoa").get(0).path("maKhoa").asText()).isEqualTo(f.khoaA);
        error(call("GET",at("lop",f.khoaB),f.a,null),404,"NOT_FOUND");
        error(call("GET","/pham-vi",f.admin,null),403,"FORBIDDEN");
        assertThat(call("GET","/pham-vi",f.noScope,null).body().path("khoa").size()).isZero();
        var root=draft();assertThat(call("GET","/phan-cong/"+root.path("id").asText(),f.reader,null).status()).isEqualTo(200);
        error(call("POST","/phan-cong",f.reader,createBody(f.khoaA,f.year())),403,"FORBIDDEN");
        error(call("PUT","/phan-cong/"+root.path("id").asText(),f.reader,saveBody(root)),403,"FORBIDDEN");
    }
    @Test void objectOutsideScopeAndAbsentHaveSame404()throws Exception{
        var root=create(f.b,createBody(f.khoaB,f.year()));
        for(String id:List.of(root.path("id").asText(),"9223372036854775807")){
            error(call("GET","/phan-cong/"+id,f.a,null),404,"NOT_FOUND");
            error(call("PUT","/phan-cong/"+id,f.a,saveBody(root)),404,"NOT_FOUND");
        }
    }
    @Test void programmesDeduplicateExistingClassLinksAndClassFilterCounts()throws Exception{
        var p=call("GET",at("chuong-trinh",f.khoaA),f.a,null);assertThat(p.status()).isEqualTo(200);schema("PageChuongTrinhDTO",p.body());
        assertThat(p.body().path("totalElements").asLong()).isEqualTo(2);
        assertThat(p.body().path("items").findValuesAsText("id")).containsExactly(Long.toString(f.programX),Long.toString(f.programY));
        var c=call("GET",at("lop",f.khoaA)+"&chuongTrinhId="+f.programX,f.a,null);schema("PageLopDTO",c.body());
        assertThat(c.body().path("totalElements").asLong()).isEqualTo(2);
        assertThat(c.body().path("items").findValuesAsText("maLop")).containsExactly(f.lopA1,f.lopA2);
        var last=call("GET",at("lop",f.khoaA)+"&page=100&size=25",f.a,null);assertThat(last.body().path("items").size()).isZero();assertThat(last.body().path("totalElements").asLong()).isGreaterThanOrEqualTo(3);
    }
    @Test void lecturerScopeAndMappingAreExplicitAndUnknownFacultyFieldRejected()throws Exception{
        var r=call("GET",at("giang-vien",f.khoaA),f.a,null);assertThat(r.status()).as(r.body().toString()).isEqualTo(200);schema("PageGiangVienDTO",r.body());
        var items=r.body().path("items");assertThat(items.findValuesAsText("maGiaoVien")).contains(f.gvA,f.gvNull).doesNotContain(f.gvB,"OTHER_"+f.gvA);
        assertThat(items.get(0).path("coTheChon").asBoolean()).isTrue();assertThat(items.get(1).path("coTheChon").asBoolean()).isFalse();
        error(call("GET",at("giang-vien",f.khoaA)+"&maNganh=X",f.a,null),400,"VALIDATION_ERROR");
        error(call("GET",at("giang-vien",f.khoaA)+"&chuongTrinhId=1",f.a,null),400,"VALIDATION_ERROR");
    }
    @Test void unknownDuplicateAndOverflowQueriesAre400()throws Exception{
        for(String suffix:List.of("&page=-1","&page=1.5","&page=2147483648","&size=0","&size=101","&page=2147483647&size=100","&page=100001&size=1","&maKhoa="+f.khoaA,"&other=x","&chuongTrinhId=9223372036854775808","&chuongTrinhId=01"))
            error(call("GET",at("lop",f.khoaA)+suffix,f.a,null),400,"VALIDATION_ERROR");
        error(call("GET","/phan-cong/01",f.a,null),400,"VALIDATION_ERROR");
        error(call("GET",at("lop",f.khoaA)+"&q="+"a".repeat(101),f.a,null),400,"VALIDATION_ERROR");
    }
    @Test void searchUsesLiteralCharactersAndNeverSqlWildcard()throws Exception{
        for(String q:List.of("%","_","' OR 1=1 --")){
            var r=call("GET",at("lop",f.khoaA)+"&q="+enc(q),f.a,null);assertThat(r.status()).isEqualTo(200);assertThat(r.body().path("items").size()).isZero();
        }
    }
    @Test void leadingZerosInvalidAndAmbiguousClassCodesStayVisibleWithoutTruncation()throws Exception{
        String zero="001"+ShcnApiFixtures.RUN,longCode="L".repeat(33),dup="DUP"+ShcnApiFixtures.RUN;
        f.lop(zero,"Synthetic zero",f.facultyA,null);f.lop(longCode,"Synthetic long",f.facultyA,null);f.lop(null,"Synthetic null",f.facultyA,null);
        f.lop(dup,"Synthetic duplicate A",f.facultyA,null);f.lop(dup,"Synthetic duplicate B",f.facultyA,null);
        var rows=call("GET",at("lop",f.khoaA)+"&size=100",f.a,null).body().path("items");
        for(JsonNode row:rows){String code=row.path("maLop").asText();
            if(code.equals(zero))assertThat(row.path("coTheChon").asBoolean()).isTrue();
            if(code.equals(longCode)||code.equals(dup)||row.path("maLop").isNull())assertThat(row.path("coTheChon").asBoolean()).isFalse();
        }
        var root=draft();var after=saved(root,line(zero,f.gvA,null));assertThat(after.path("cacPhanCong").get(0).path("maLop").asText()).isEqualTo(zero);
    }
    @Test void createReplayAndReconciliationNeverDuplicateDraft()throws Exception{
        var body=createBody(f.khoaA,f.year());var root=create(f.a,body);
        assertThat(root.path("trangThai").asText()).isEqualTo("NHAP");assertThat(root.path("soLop").asInt()).isZero();
        assertThat(root.path("maNguoiLap").asText()).isEqualTo(f.a.account().toString());assertThat(root.path("hieuLucTu").asText()).isEqualTo("2026-09-01T00:00:00Z");
        error(call("POST","/phan-cong",f.a,body),409,"SHCN_PHIEN_BAN_KHONG_HOP_LE");
        var list=call("GET",at("phan-cong",f.khoaA)+"&namHoc="+enc(body.path("namHoc").asText())+"&phienBan=1",f.a,null);schema("PageDanhSachTomTatDTO",list.body());
        assertThat(list.body().path("totalElements").asLong()).isEqualTo(1);assertThat(list.body().path("items").get(0).path("id")).isEqualTo(root.path("id"));
        assertThat(audit(root)).isEqualTo(1);
    }
    @Test void fullReplaceRetainsIdsDeletesOmittedRowsAndAdvancesEvenIdenticalPayload()throws Exception{
        var root=draft();var first=saved(root,line(f.lopA1,f.gvA,"one"),line(f.lopA2,f.gvA,null));
        var retained=first.path("cacPhanCong").get(0).path("id");
        var second=saved(first,line(f.lopA1,f.gvA,"changed"));assertThat(second.path("cacPhanCong").size()).isEqualTo(1);
        assertThat(second.path("cacPhanCong").get(0).path("id")).isEqualTo(retained);
        var same=saved(second,line(f.lopA1,f.gvA,"changed"));assertThat(same.path("version")).isNotEqualTo(second.path("version"));
        var empty=saved(same);assertThat(empty.path("cacPhanCong").size()).isZero();assertThat(detail(empty)).isEqualTo(empty);
        assertThat(audit(root)).isEqualTo(5);
    }
    @Test void duplicateClassesIncludingSqlCollationRollBackAll()throws Exception{
        var root=saved(draft(),line(f.lopA1,f.gvA,"original"));long before=audit(root);
        for(String duplicate:List.of(f.lopA1,f.lopA1.toLowerCase(Locale.ROOT))){
            error(save(root,saveBody(root,line(f.lopA1,f.gvA,"changed"),line(duplicate,f.gvA,null))),400,"VALIDATION_ERROR");
            assertThat(detail(root)).isEqualTo(root);assertThat(audit(root)).isEqualTo(before);
        }
    }
    @Test void invalidClassOrTeacherNeverPartiallyUpdates()throws Exception{
        var root=saved(draft(),line(f.lopA1,f.gvA,"original"));long before=audit(root);
        for(var bad:List.of(line(f.lopA2,f.gvB,null),line(f.lopB,f.gvA,null),line(f.lopA2,f.gvNull,null),line("MISSING",f.gvA,null))){
            error(save(root,saveBody(root,line(f.lopA1,f.gvA,"changed"),bad)),409,"SHCN_THAM_CHIEU_KHONG_CON_HOP_LE");
            assertThat(detail(root)).isEqualTo(root);assertThat(audit(root)).isEqualTo(before);
        }
    }
    @Test void staleTokenAndNonDraftChecksHaveApprovedOrder()throws Exception{
        var old=draft();var root=saved(old,line(f.lopA1,f.gvA,null));
        error(save(old,saveBody(old)),409,"VERSION_CONFLICT");
        jdbc.update("UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET trang_thai='CHO_KY' WHERE id=?",root.path("id").asLong());
        error(save(root,saveBody(root)),409,"VERSION_CONFLICT");
        var current=detail(root);error(save(current,saveBody(current)),409,"SHCN_KHONG_CON_LA_NHAP");
    }
    @Test void inheritImmutablePreviousYearCreatesNewIdsWithoutMutatingSource()throws Exception{
        var src=saved(draft(),line(f.lopA1,f.gvA,"copied"),line(f.lopA2,f.gvA,null));
        jdbc.update("UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET trang_thai='DA_GUI_CHUYEN_VIEN' WHERE id=?",src.path("id").asLong());
        var before=detail(src);var body=createBody(f.khoaA,f.year()).put("maDanhSachTruoc",src.path("id").asText()).putNull("lyDoDieuChinh");
        var copy=create(f.a,body);assertThat(copy.path("trangThai").asText()).isEqualTo("NHAP");assertThat(copy.path("hieuLucDen").isNull()).isTrue();
        assertThat(copy.path("lyDoDieuChinh").isNull()).isTrue();assertThat(copy.path("cacPhanCong").size()).isEqualTo(2);
        var oldLineIds=new ArrayList<String>();before.path("cacPhanCong").forEach(row->oldLineIds.add(row.path("id").asText()));
        for(JsonNode row:copy.path("cacPhanCong"))assertThat(oldLineIds).doesNotContain(row.path("id").asText());
        assertThat(detail(src)).isEqualTo(before);
    }
    @Test void invalidInheritanceScopeAndStateAreRejected()throws Exception{
        for(String state:List.of("NHAP","CHO_KY","DA_HUY")){
            var src=draft();if(!state.equals("NHAP"))jdbc.update("UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET trang_thai=? WHERE id=?",state,src.path("id").asLong());
            var body=createBody(f.khoaA,f.year()).put("maDanhSachTruoc",src.path("id").asText()).put("lyDoDieuChinh","Copy");
            error(call("POST","/phan-cong",f.a,body),409,"SHCN_NGUON_KE_THUA_KHONG_HOP_LE");
            body.putNull("lyDoDieuChinh");error(call("POST","/phan-cong",f.a,body),409,"SHCN_NGUON_KE_THUA_KHONG_HOP_LE");
        }
        var other=create(f.b,createBody(f.khoaB,f.year()));var body=createBody(f.khoaA,f.year()).put("maDanhSachTruoc",other.path("id").asText()).put("lyDoDieuChinh","Copy");
        error(call("POST","/phan-cong",f.a,body),404,"NOT_FOUND");
        error(call("POST","/phan-cong",f.multi,body),409,"SHCN_NGUON_KE_THUA_KHONG_HOP_LE");
    }
    @Test void twoConcurrentPostsAllocateOnlyExpectedVersion()throws Exception{
        var body=createBody(f.khoaA,f.year());var r=race(()->call("POST","/phan-cong",f.a,body),()->call("POST","/phan-cong",f.a2,body));
        assertThat(r.stream().map(Reply::status)).containsExactlyInAnyOrder(201,409);
        var list=call("GET",at("phan-cong",f.khoaA)+"&namHoc="+enc(body.path("namHoc").asText()),f.a,null);assertThat(list.body().path("totalElements").asLong()).isEqualTo(1);
    }
    @Test void twoConcurrentPutsCommitOneWholePayload()throws Exception{
        var root=draft();var a=saveBody(root,line(f.lopA1,f.gvA,"writer A"));var b=saveBody(root,line(f.lopA2,f.gvA,"writer B"));String path="/phan-cong/"+root.path("id").asText();
        var results=race(()->call("PUT",path,f.a,a),()->call("PUT",path,f.a2,b));
        assertThat(results.stream().map(Reply::status)).containsExactlyInAnyOrder(200,409);
        error(results.stream().filter(r->r.status()!=200).findFirst().orElseThrow(),409,"VERSION_CONFLICT");
        var winner=results.stream().filter(r->r.status()==200).findFirst().orElseThrow();assertThat(detail(root)).isEqualTo(winner.body());assertThat(audit(root)).isEqualTo(2);
    }
    List<Reply> race(Callable<Reply> one,Callable<Reply> two)throws Exception{
        try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await();return one.call();});var b=pool.submit(()->{start.await();return two.call();});start.countDown();
            return List.of(a.get(45,TimeUnit.SECONDS),b.get(45,TimeUnit.SECONDS));
        }
    }
    @Test void realSqlFaultAfterAuditInsertRollsBackRootChildrenAndAudit()throws Exception{
        var root=saved(draft(),line(f.lopA1,f.gvA,"original"));long count=audit(root);FAIL_AUDIT.set(true);
        try{error(save(root,saveBody(root,line(f.lopA2,f.gvA,"must roll back"))),500,"INTERNAL_ERROR");}finally{FAIL_AUDIT.set(false);}
        assertThat(detail(root)).isEqualTo(root);assertThat(audit(root)).isEqualTo(count);
    }
    @Test void missingSourceMeans503ButConfirmedMissingClassPreservesStoredCodes()throws Exception{
        var root=saved(draft(),line(f.lopA1,f.gvA,null));
        jdbc.update("UPDATE dbo.lop_sinh_hoat SET dang_hien_dien_nguon=0 WHERE id=?",f.classA1);
        try{var view=detail(root);assertThat(view.path("cacPhanCong").get(0).path("lopHienTai").isNull()).isTrue();assertThat(view.path("cacPhanCong").get(0).path("maLop").asText()).isEqualTo(f.lopA1);assertThat(view.path("version")).isEqualTo(root.path("version"));}
        finally{jdbc.update("UPDATE dbo.lop_sinh_hoat SET dang_hien_dien_nguon=1 WHERE id=?",f.classA1);}
        jdbc.update("UPDATE dbo.he_thong_ngoai SET dang_hoat_dong=0 WHERE id=?",f.source);
        try{error(call("GET",at("lop",f.khoaA),f.a,null),503,"DEPENDENCY_UNAVAILABLE");error(call("GET","/phan-cong/"+root.path("id").asText(),f.a,null),503,"DEPENDENCY_UNAVAILABLE");}
        finally{jdbc.update("UPDATE dbo.he_thong_ngoai SET dang_hoat_dong=1 WHERE id=?",f.source);}
    }
    @Test void strictBodiesRejectUnknownMissingDuplicateAndTrailingJson()throws Exception{
        var root=draft();var body=saveBody(root);
        for(String extra:List.of("maKhoa","phienBan","trangThai","maNguoiLap","maHoSoTaiLieu"))error(save(root,body.deepCopy().put(extra,"injected")),400,"VALIDATION_ERROR");
        var missing=body.deepCopy();missing.remove("expectedVersion");error(save(root,missing),400,"VALIDATION_ERROR");
        error(save(root,body.deepCopy().put("expectedVersion","AAAAAAAAAAA")),400,"VALIDATION_ERROR");
        var path="/phan-cong/"+root.path("id").asText();String valid=json.writeValueAsString(body);
        error(raw("PUT",path,f.a.token(),valid+" {}","application/json"),400,"VALIDATION_ERROR");
        error(raw("PUT",path,f.a.token(),valid.substring(0,valid.length()-1)+",\"expectedVersion\":\"AAAAAAAAAAA=\"}","application/json"),400,"VALIDATION_ERROR");
        error(raw("PUT",path,f.a.token(),valid,"text/plain"),415,"UNSUPPORTED_MEDIA_TYPE");
        error(raw("PUT",path+"?ignored=1",f.a.token(),valid,"application/json"),400,"VALIDATION_ERROR");
    }
    @Test void utf16DateAndRowLimitsAreEnforced()throws Exception{
        var body=createBody(f.khoaA,f.year());
        for(var invalid:List.of(body.deepCopy().put("hieuLucTu","2026-09-01T00:00:00"),body.deepCopy().put("hieuLucTu","2026-09-01T00:00:00.12345678Z"),body.deepCopy().put("hieuLucDen","2026-09-01T00:00:00Z"),body.deepCopy().put("maKhoa"," "+f.khoaA),body.deepCopy().put("lyDoDieuChinh","😀".repeat(2001))))
            error(call("POST","/phan-cong",f.a,invalid),400,"VALIDATION_ERROR");
        create(f.a,body.deepCopy().put("lyDoDieuChinh","😀".repeat(2000)).put("hieuLucTu","2026-09-01T00:00:00.1234567Z"));
        var root=draft();var rows=saveBody(root);for(int i=0;i<2001;i++)((ArrayNode)rows.path("cacPhanCong")).add(line("L"+i,f.gvA,null));error(save(root,rows),400,"VALIDATION_ERROR");
    }
    @Test void actualUtf8BodyBytesAreLimitedIncludingChunkedPublishers()throws Exception{
        String jsonBody=json.writeValueAsString(createBody(f.khoaA,f.year()));
        int bytes=jsonBody.getBytes(StandardCharsets.UTF_8).length;
        for(int size:List.of(1048575,1048576,1048577)){
            String payload=jsonBody+" ".repeat(size-bytes);
            var r=raw("POST","/phan-cong",f.a.token(),payload,"application/json");
            if(size==1048575)assertThat(r.status()).as(r.body().toString()).isEqualTo(201);
            else if(size==1048576)error(r,409,"SHCN_PHIEN_BAN_KHONG_HOP_LE");else error(r,413,"PAYLOAD_TOO_LARGE");
        }
        byte[] oversized=(jsonBody+" ".repeat(1048577-bytes)).getBytes(StandardCharsets.UTF_8);
        var request=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+"/api/shcn/khoa/phan-cong")).timeout(Duration.ofSeconds(30))
            .header("Authorization","Bearer "+f.a.token()).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofInputStream(()->new java.io.ByteArrayInputStream(oversized))).build();
        assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(413);
    }
    @Test void corsPreflightExposesLocationOnlyForConfiguredOrigin()throws Exception{
        var request=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+"/api/shcn/khoa/phan-cong"))
            .header("Origin",ShcnApiTestKeys.ORIGIN).header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","Authorization,Content-Type").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build();
        var r=client.send(request,HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).isEqualTo(200);assertThat(r.headers().firstValue("access-control-allow-origin")).contains(ShcnApiTestKeys.ORIGIN);
    }
    @Test void expiredRevokedAndChildScopeGrantsUseExistingRbacFields()throws Exception{
        var actor=f.actor("CHANGING",ShcnApiFixtures.PREPARE,f.facultyA);
        jdbc.update("UPDATE dbo.phan_quyen_nguoi_dung SET hieu_luc_den='2021-01-01' WHERE nguoi_dung_id=?",actor.user());
        error(call("GET","/pham-vi",actor,null),403,"FORBIDDEN");
        jdbc.update("UPDATE dbo.phan_quyen_nguoi_dung SET hieu_luc_den=NULL,hieu_luc_tu='2099-01-01' WHERE nguoi_dung_id=?",actor.user());
        error(call("GET","/pham-vi",actor,null),403,"FORBIDDEN");
        jdbc.update("UPDATE dbo.phan_quyen_nguoi_dung SET hieu_luc_tu='2020-01-01',bao_gom_don_vi_con=1 WHERE nguoi_dung_id=?",actor.user());
        long child=f.id("INSERT dbo.don_vi(ma,ten,loai_don_vi,don_vi_cha_id,dang_hoat_dong,he_thong_nguon_id) VALUES(?,N'Synthetic child',N'khoa',?,1,?)","CH"+ShcnApiFixtures.RUN,f.facultyA,f.source);
        var scope=call("GET","/pham-vi",actor,null);assertThat(scope.status()).isEqualTo(200);assertThat(scope.body().path("khoa").findValuesAsText("id")).contains(Long.toString(child));
        jdbc.update("DELETE dbo.phan_quyen_nguoi_dung WHERE nguoi_dung_id=?",actor.user());
        error(call("GET","/pham-vi",actor,null),403,"FORBIDDEN");
    }
    @Test void permissionsComeFromDatabaseAndRevocationIsEffectiveWithSameToken()throws Exception{
        var actor=f.actor("DYNAMIC",null,null);String code="SYNTH_DYNAMIC_"+UUID.randomUUID().toString().substring(0,8);
        long role=f.role(code);
        jdbc.update("INSERT dbo.VaiTro(MaVaiTro,TenVaiTro,TrangThai) VALUES(?,?,1)",code,"Synthetic dynamic role");
        long authRole=jdbc.queryForObject("SELECT ID FROM dbo.VaiTro WHERE MaVaiTro=?",Long.class,code);
        jdbc.update("INSERT dbo.TaiKhoan_VaiTro(TaiKhoanID,VaiTroID) VALUES(?,?)",actor.account().toString(),authRole);
        f.grant(actor.user(),role,f.facultyA,"SHCN");
        jdbc.update("INSERT dbo.VaiTro_ChucNang(VaiTroID,ChucNangID) SELECT ?,ID FROM dbo.ChucNang WHERE MaChucNang=N'shcn.phanCongKhoa.read'",authRole);
        assertThat(call("GET","/pham-vi",actor,null).status()).isEqualTo(200);
        var body=createBody(f.khoaA,f.year());error(call("POST","/phan-cong",actor,body),403,"FORBIDDEN");
        jdbc.update("INSERT dbo.VaiTro_ChucNang(VaiTroID,ChucNangID) SELECT ?,ID FROM dbo.ChucNang WHERE MaChucNang=N'shcn.phanCongKhoa.prepare'",authRole);
        var root=create(actor,body);
        jdbc.update("DELETE e FROM dbo.VaiTro_ChucNang e JOIN dbo.ChucNang c ON c.ID=e.ChucNangID WHERE e.VaiTroID=? AND c.MaChucNang=N'shcn.phanCongKhoa.prepare'",authRole);
        error(call("PUT","/phan-cong/"+root.path("id").asText(),actor,saveBody(root)),403,"FORBIDDEN");
        assertThat(call("GET","/phan-cong/"+root.path("id").asText(),actor,null).status()).isEqualTo(200);
        jdbc.update("DELETE FROM dbo.VaiTro_ChucNang WHERE VaiTroID=?",authRole);
        error(call("GET","/pham-vi",actor,null),403,"FORBIDDEN");
    }
    @Test void prepareCannotBeBorrowedFromAnotherRoleOrFaculty()throws Exception{
        var actor=f.actor("SCOPE_ROLES",ShcnApiFixtures.READ,f.facultyA);
        long prepare=jdbc.queryForObject("SELECT ID FROM dbo.VaiTro WHERE MaVaiTro=?",Long.class,ShcnApiFixtures.PREPARE);
        jdbc.update("INSERT dbo.TaiKhoan_VaiTro(TaiKhoanID,VaiTroID) VALUES(?,?)",actor.account().toString(),prepare);
        error(call("POST","/phan-cong",actor,createBody(f.khoaA,f.year())),403,"FORBIDDEN");
        f.grant(actor.user(),f.role(ShcnApiFixtures.PREPARE),f.facultyB,"SHCN");
        error(call("POST","/phan-cong",actor,createBody(f.khoaA,f.year())),404,"NOT_FOUND");
        create(actor,createBody(f.khoaB,f.year()));
    }
    @Test void disabledPermissionRoleAndRevokedAccountAssignmentAreEnforced()throws Exception{
        var actor=f.actor("REVOKE_RBAC",ShcnApiFixtures.PREPARE,f.facultyA);
        long role=jdbc.queryForObject("SELECT ID FROM dbo.VaiTro WHERE MaVaiTro=?",Long.class,ShcnApiFixtures.PREPARE);
        long permission=jdbc.queryForObject("SELECT ID FROM dbo.ChucNang WHERE MaChucNang=N'shcn.phanCongKhoa.prepare'",Long.class);
        try{
            jdbc.update("UPDATE dbo.ChucNang SET TrangThai=0 WHERE ID=?",permission);
            assertThat(call("GET","/pham-vi",actor,null).status()).isEqualTo(200);
            error(call("POST","/phan-cong",actor,createBody(f.khoaA,f.year())),403,"FORBIDDEN");
        }finally{jdbc.update("UPDATE dbo.ChucNang SET TrangThai=1 WHERE ID=?",permission);}
        try{
            jdbc.update("UPDATE dbo.VaiTro SET TrangThai=0 WHERE ID=?",role);
            error(call("GET","/pham-vi",actor,null),403,"FORBIDDEN");
        }finally{jdbc.update("UPDATE dbo.VaiTro SET TrangThai=1 WHERE ID=?",role);}
        jdbc.update("DELETE dbo.TaiKhoan_VaiTro WHERE TaiKhoanID=? AND VaiTroID=?",actor.account().toString(),role);
        error(call("GET","/pham-vi",actor,null),403,"FORBIDDEN");
    }
    @Test void migrationSeedsOfficialPermissionsWithoutDevAccountsOrNewTables(){
        for(String role:List.of("shcn_truong_khoa","shcn_giang_vien")){
            long count=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.VaiTro_ChucNang e JOIN dbo.VaiTro r ON r.ID=e.VaiTroID JOIN dbo.ChucNang c ON c.ID=e.ChucNangID WHERE r.MaVaiTro=? AND c.MaChucNang IN(N'shcn.phanCongKhoa.read',N'shcn.phanCongKhoa.prepare')",Long.class,role);
            assertThat(count).isEqualTo(role.equals("shcn_truong_khoa")?2:0);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='15' AND success=1",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.TaiKhoan WHERE TenDangNhap IN(N'dev.giangvien',N'dev.truongkhoa')",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID(N'shcn')",Integer.class)).isEqualTo(2);
    }
    @Test void listVersionsAreDescendingWithRealLineCountsAndVersionExhaustion()throws Exception{
        String year=f.year();var first=create(f.a,createBody(f.khoaA,year));saved(first,line(f.lopA1,f.gvA,null),line(f.lopA2,f.gvA,null));
        var second=create(f.a,createBody(f.khoaA,year).put("phienBanDuKien",2));saved(second,line(f.lopA3,f.gvA,null));
        var list=call("GET",at("phan-cong",f.khoaA)+"&namHoc="+enc(year),f.a,null);schema("PageDanhSachTomTatDTO",list.body());
        assertThat(list.body().path("items").get(0).path("phienBan").asInt()).isEqualTo(2);assertThat(list.body().path("items").get(0).path("soLop").asInt()).isEqualTo(1);
        assertThat(list.body().path("items").get(1).path("soLop").asInt()).isEqualTo(2);
        String full=f.year();f.id("INSERT shcn.shcn_danh_sach_phan_cong_khoa(ma_khoa,nam_hoc,phien_ban,hieu_luc_tu,trang_thai,ma_nguoi_lap,ngay_tao,ngay_cap_nhat) VALUES(?,?,2147483647,'2026-01-01','NHAP',?,SYSUTCDATETIME(),SYSUTCDATETIME())",f.khoaA,full,f.a.account().toString());
        error(call("POST","/phan-cong",f.a,createBody(f.khoaA,full)),409,"SHCN_HET_MIEN_PHIEN_BAN");
    }
    @Test void staleInheritedCodesStayDraftAndOversizedSourceRequiresReview()throws Exception{
        for(String state:List.of("DA_KY","DA_THAY_THE")){
            var src=draft();jdbc.update("INSERT shcn.shcn_phan_cong_gvcn_khoa(ma_danh_sach_khoa,ma_lop,ma_giao_vien,ngay_tao,ngay_cap_nhat) VALUES(?,'OLD_CLASS','OLD_TEACHER',SYSUTCDATETIME(),SYSUTCDATETIME())",src.path("id").asLong());
            jdbc.update("UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET trang_thai=? WHERE id=?",state,src.path("id").asLong());
            var copy=create(f.a,createBody(f.khoaA,f.year()).put("maDanhSachTruoc",src.path("id").asText()).put("lyDoDieuChinh","Review stale codes"));
            assertThat(copy.path("cacPhanCong").get(0).path("lopHienTai").isNull()).isTrue();assertThat(copy.path("cacPhanCong").get(0).path("giangVienHienTai").isNull()).isTrue();
            error(save(copy,saveBody(copy,line("OLD_CLASS","OLD_TEACHER",null))),409,"SHCN_THAM_CHIEU_KHONG_CON_HOP_LE");
        }
        var oversized=draft();jdbc.update(";WITH n AS (SELECT TOP(2001) ROW_NUMBER() OVER(ORDER BY (SELECT NULL)) x FROM sys.all_columns) INSERT shcn.shcn_phan_cong_gvcn_khoa(ma_danh_sach_khoa,ma_lop,ma_giao_vien,ngay_tao,ngay_cap_nhat) SELECT ?,CONCAT('OLD_',x),'OLD_TEACHER',SYSUTCDATETIME(),SYSUTCDATETIME() FROM n",oversized.path("id").asLong());
        jdbc.update("UPDATE shcn.shcn_danh_sach_phan_cong_khoa SET trang_thai='DA_KY' WHERE id=?",oversized.path("id").asLong());
        error(call("POST","/phan-cong",f.a,createBody(f.khoaA,f.year()).put("maDanhSachTruoc",oversized.path("id").asText()).put("lyDoDieuChinh","Oversized source")),409,"SHCN_CAN_REVIEW_GIOI_HAN");
    }
}
