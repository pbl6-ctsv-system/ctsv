package pbl6.ctsv.auth;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import pbl6.ctsv.mghp.configuration.RoundSequenceDatabase;
import static org.assertj.core.api.Assertions.*;

/** Only DOT03 sequencing/ETag/permissions/concurrency. HTTPS + real JWT/Auth + JPA + guarded SQL Server; no provider stubs. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, classes=RoundSequenceHttpIT.Config.class,
        properties={"ctsv.auth.enabled=true", "ctsv.auth.login-rate-limit-enabled=false", "ctsv.foundation.enabled=true",
                "ctsv.mghp.configuration.enabled=true", "server.address=127.0.0.1"})
@ActiveProfiles({"local","test"}) @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RoundSequenceHttpIT {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Instant NOW = Instant.parse("2026-11-25T03:00:00Z");
    static final String BASE = "/api/ctsv/mghp/dot-xet";
    static final String RUN = "SEQ25-" + UUID.randomUUID();
    static final String PASSWORD = "SYNTHETIC Round " + UUID.randomUUID();
    @Configuration(proxyBeanMethods=false) @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.boot.autoconfigure.domain.EntityScan({"pbl6.ctsv.foundation", "pbl6.ctsv.mghp"})
    @ComponentScan(basePackages={"pbl6.ctsv.auth", "pbl6.ctsv.foundation", "pbl6.ctsv.mghp", "pbl6.ctsv.shared"},
            excludeFilters={@ComponentScan.Filter(type=FilterType.CUSTOM, classes=org.springframework.boot.context.TypeExcludeFilter.class),
                    @ComponentScan.Filter(type=FilterType.REGEX, pattern=".*(?:Test|IT)(?:\\$.*)?")})
    static class Config {
        @Bean DataSource sequenceSource() throws Exception { return RoundSequenceDatabase.migrate(); }
        @Bean @Primary Clock sequenceClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) throws Exception { AuthHttpIT.configuration(registry); }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    HttpClient transport;
    String cv, student, admin;
    long semester1, semester2, policy;
    record Reply(int status, HttpHeaders headers, String body) {
        Reply expect(int expected) { assertThat(status).as("HTTP %s",body).isEqualTo(expected); return this; }
        JsonNode json() throws Exception { return JSON.readTree(body); }
        String tag() { return headers.firstValue("ETag").orElseThrow(); }
    }
    Reply send(String method, String path, Object body, String token, String tag) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20));
        if (token != null) request.header("Authorization", "Bearer "+token);
        if (tag != null) request.header("If-Match",tag);
        if (body != null) request.header("Content-Type","application/json");
        if (path.startsWith("/api/xac-thuc/")) request.header("Origin",AuthHttpIT.ORIGIN).header("X-CSRF-Protection","1");
        request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        var response = transport.send(request.build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(),response.headers(),response.body());
    }
    long id(String sql,Object... values) { return jdbc.queryForObject(sql+";SELECT CAST(SCOPE_IDENTITY() AS bigint)",Long.class,values); }
    String identity(String kind) throws Exception {
        var account=UUID.randomUUID();var name=RUN+"-"+kind;
        jdbc.update("INSERT dbo.TaiKhoan(ID,TenDangNhap,TenDangNhapChuanHoa,MatKhauHash,ThoiDiemTao,ThoiDiemCapNhat) VALUES(?,?,?,?,?,?)",
                account.toString(),name,name.toLowerCase(Locale.ROOT),new AuthCredentials().hash(PASSWORD),NOW.atOffset(ZoneOffset.UTC),NOW.atOffset(ZoneOffset.UTC));
        jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'CTSV_USER'",account.toString());
        if (kind.equals("admin")) jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'AUTH_ADMIN'",account.toString());
        long user=id("INSERT dbo.nguoi_dung(dia_chi_email,ho_ten,dang_hoat_dong) VALUES(?,?,1)",name+"@example.invalid","SYNTHETIC "+kind);
        jdbc.update("INSERT dbo.dinh_danh_dang_nhap(nguoi_dung_id,ma_nha_cung_cap,ma_chu_the_dang_nhap,thoi_diem_xac_minh) VALUES(?,?,?,SYSUTCDATETIME())",user,"https://auth.ctsv.test",account.toString());
        if (kind.equals("cv")) jdbc.update("INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he) SELECT ?,id,N'MGHP' FROM dbo.vai_tro WHERE ma=N'mghp_chuyen_vien'",user);
        if (kind.equals("sv")) id("INSERT dbo.sinh_vien(mssv,ho_ten,nguoi_dung_id) VALUES(?,N'SYNTHETIC sinh viên',?)",name,user);
        return send("POST","/api/xac-thuc/dang-nhap",Map.of("username",name,"password",PASSWORD),null,null).expect(200).json().path("accessToken").asText();
    }
    @BeforeAll void fixtures() throws Exception {
        transport=HttpClient.newBuilder().sslContext(AuthHttpIT.testTls).build();
        cv=identity("cv");student=identity("sv");admin=identity("admin");
        semester1=semester(1,"2026-09-01","2027-01-31");semester2=semester(2,"2027-02-01","2027-06-30");
        long parent=jdbc.queryForObject("SELECT id FROM dbo.mghp_chinh_sach WHERE ma=N'M2'",Long.class);
        int revision=jdbc.queryForObject("SELECT COALESCE(MAX(phien_ban),0)+1 FROM dbo.mghp_phien_ban_chinh_sach WHERE chinh_sach_id=?",Integer.class,parent);
        policy=id("INSERT dbo.mghp_phien_ban_chinh_sach(chinh_sach_id,phien_ban,ten_tai_thoi_diem_chot,ty_le_giam,ma_pham_vi,ngay_bat_dau_hieu_luc,trang_thai,tinh_trang_xac_nhan_quy_tac) VALUES(?,?,N'SYNTHETIC tuần tự đợt',0.5,N'hoc_phi_chuong_trinh_chinh','2026-01-01',N'draft',N'da_doi_chieu_nguon')",parent,revision);
        jdbc.update("UPDATE dbo.mghp_phien_ban_chinh_sach SET trang_thai=N'locked',thoi_diem_khoa=? WHERE id=?",NOW.atOffset(ZoneOffset.UTC),policy);
    }
    long semester(int order,String from,String until) {
        int sequence=jdbc.queryForObject("SELECT COALESCE(MAX(so_thu_tu),0)+1 FROM dbo.hoc_ky",Integer.class);
        return id("INSERT dbo.hoc_ky(ma,ten,so_thu_tu,ngay_bat_dau,ngay_ket_thuc,trang_thai_xac_minh_moc,can_cu_xac_minh_moc) VALUES(?,?,?, ?,?,N'verified',N'SYNTHETIC calendar')",RUN+"-HK"+order,"SYNTHETIC học kỳ "+order,sequence,java.sql.Date.valueOf(from),java.sql.Date.valueOf(until));
    }
    @BeforeEach void isolateOwnedFixtures() {
        // Simulate completion ONLY in this owned test DB. This does not implement or bypass production DOT05.
        jdbc.update("UPDATE dbo.mghp_dot_xet SET trang_thai=N'completed' WHERE ma_dot_xet LIKE N'SEQ25-%' AND trang_thai IN(N'open',N'closed')");
    }
    @AfterAll void close() { if (transport!=null) transport.close(); }
    String create(long semester) throws Exception {
        var body=JSON.createObjectNode().put("code",RUN+"-"+UUID.randomUUID()).put("semesterId",Long.toString(semester))
                .put("name","SYNTHETIC tuần tự đợt").putNull("description").put("opensAt",NOW.plusSeconds(3600).toString())
                .put("submissionDeadline",NOW.plusSeconds(86400).toString()).put("reviewDeadline",NOW.plusSeconds(172800).toString())
                .put("allowRetroactive",true).put("retroactiveMode","theo_phap_luat").put("midSemesterMode","khoang_ngay_chuyen_vien_xac_nhan");
        body.putArray("policyVersions").addObject().put("policyVersionId",Long.toString(policy)).putNull("applicationTemplateFileId").put("order",1);
        return BASE+"/"+send("POST",BASE,body,cv,null).expect(201).json().path("id").asText();
    }
    Reply read(String path) throws Exception { return send("GET",path,null,cv,null).expect(200); }
    Reply action(String path,String action) throws Exception { return send("POST",path+"/"+action,action.equals("mo")?null:Map.of("reason","SYNTHETIC sequence test"),cv,read(path).tag()); }
    long count(String table) { return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo."+table,Long.class); }
    void simulateCompleted(String path) { assertThat(jdbc.update("UPDATE dbo.mghp_dot_xet SET trang_thai=N'completed' WHERE id=? AND ma_dot_xet LIKE ?",Long.parseLong(path.substring(path.lastIndexOf('/')+1)),RUN+"%")).isEqualTo(1); }

    @Test void DOT_SEQ01_draftsDoNotBlockAndRepeatOpenDoesNotPublishAgain() throws Exception {
        var first=create(semester1);create(semester1);create(semester2);
        long notices=count("thong_bao"),audits=count("nhat_ky_thao_tac");
        var opened=action(first,"mo").expect(200);
        assertThat(opened.json().path("status").asText()).isEqualTo("open");
        send("POST",first+"/mo",null,cv,opened.tag()).expect(200);
        assertThat(count("thong_bao")).isEqualTo(notices+1);assertThat(count("nhat_ky_thao_tac")).isEqualTo(audits+1);
    }
    @ParameterizedTest @ValueSource(strings={"same-open","same-closed","other-open","other-closed"})
    void DOT_SEQ02_04_openOrClosedBlocksAcrossSemestersWithoutMutation(String variant) throws Exception {
        var first=create(semester1);var next=create(variant.startsWith("same")?semester1:semester2);
        action(first,"mo").expect(200);if (variant.endsWith("closed")) action(first,"dong-nhan-ho-so").expect(200);
        var before=read(next);long notices=count("thong_bao"),audits=count("nhat_ky_thao_tac");
        var rejected=send("POST",next+"/mo",null,cv,before.tag()).expect(409);
        assertThat(rejected.json().path("code").asText()).isEqualTo("ROUND_PREVIOUS_NOT_COMPLETED");
        var after=read(next);assertThat(after.body).isEqualTo(before.body);assertThat(after.tag()).isEqualTo(before.tag());
        assertThat(count("thong_bao")).isEqualTo(notices);assertThat(count("nhat_ky_thao_tac")).isEqualTo(audits);
    }
    @Test void DOT_SEQ05_completedRoundsAllowNextRoundAndNextSemester() throws Exception {
        var first=create(semester1);action(first,"mo").expect(200);simulateCompleted(first);
        var second=create(semester1);action(second,"mo").expect(200);simulateCompleted(second);
        var nextSemester=create(semester2);action(nextSemester,"mo").expect(200);
        assertThat(read(first).json().path("status").asText()).isEqualTo("completed");
    }
    @Test void DOT_SEQ06_twoConcurrentOpensAcrossSemestersHaveExactlyOneWinner() throws Exception {
        var first=create(semester1);var second=create(semester2);var firstTag=read(first).tag();var secondTag=read(second).tag();
        long notices=count("thong_bao"),audits=count("nhat_ky_thao_tac");var start=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{start.await();return send("POST",first+"/mo",null,cv,firstTag);});
            var b=pool.submit(()->{start.await();return send("POST",second+"/mo",null,cv,secondTag);});start.countDown();
            assertThat(List.of(a.get(25,TimeUnit.SECONDS).status,b.get(25,TimeUnit.SECONDS).status)).containsExactlyInAnyOrder(200,409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_dot_xet WHERE trang_thai=N'open'",Integer.class)).isEqualTo(1);
        assertThat(count("thong_bao")).isEqualTo(notices+1);assertThat(count("nhat_ky_thao_tac")).isEqualTo(audits+1);
    }
    @Test void DOT_SEQ07_permissionsAndEtagsAreNotBypassed() throws Exception {
        var path=create(semester1);var before=read(path);
        send("POST",path+"/mo",null,cv,null).expect(428);
        send("POST",path+"/mo",null,cv,"\"stale\"").expect(412);
        for (var token:List.of(student,admin)) send("POST",path+"/mo",null,token,before.tag()).expect(403);
        send("POST",path+"/mo",null,null,before.tag()).expect(401);
        assertThat(read(path).tag()).isEqualTo(before.tag());
    }
    @Test void DOT_SEQ08_completeStillRequiresPublicationAndPortalEvidence() throws Exception {
        var path=create(semester1);action(path,"mo").expect(200);action(path,"dong-nhan-ho-so").expect(200);
        assertThat(action(path,"hoan-tat").expect(409).json().path("code").asText()).isEqualTo("ROUND_STATE_CONFLICT");
        assertThat(read(path).json().path("status").asText()).isEqualTo("closed");
    }
}
