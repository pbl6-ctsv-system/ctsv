package pbl6.ctsv.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pbl6.ctsv.CtsvApplication;
import pbl6.ctsv.shared.files.PrivateFileStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes={CtsvApplication.class,P06FileHttpIT.Config.class},
    properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.files.enabled=true",
        "ctsv.mghp.configuration.enabled=true","ctsv.mghp.applications.enabled=true","server.address=127.0.0.1"})
@ActiveProfiles({"local","test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class P06FileHttpIT {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String ROOT="/api/ctsv/tep";
    private static final AtomicBoolean SQL_FAULT=new AtomicBoolean();
    private static final Store STORE=new Store();

    @TestConfiguration(proxyBeanMethods=false)
    static class Config {
        @Bean DataSource p06Source() throws Exception { return P06Database.migrate(); }
        @Bean
        @ConditionalOnProperty(name="ctsv.files.cloudinary.enabled",havingValue="false",matchIfMissing=true)
        Store p06Storage() { return STORE; }

        @Bean JdbcTemplate p06Jdbc(DataSource source) {
            return new JdbcTemplate(source) {
                @Override public int update(String sql,Object... args) {
                    int result=super.update(sql,args);
                    if (SQL_FAULT.get() && sql.contains("INSERT dbo.nhat_ky_thao_tac") && sql.contains("upload_pdf")) {
                        super.update("THROW 51706,N'Synthetic P06 fault after audit insert',1");
                    }
                    return result;
                }
            };
        }
    }

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {
        AuthHttpIT.configuration(registry);
        if (Boolean.getBoolean("p06.browser.enabled")) P05BrowserHarnessIT.browserProperties(registry);
        if (Boolean.getBoolean("p06.cloudinary.real") || Boolean.getBoolean("p06.browser.cloudinary.real")) {
            registry.add("ctsv.files.cloudinary.enabled",() -> true);
            registry.add("ctsv.files.reconciliation.enabled",() -> false);
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.context.ApplicationContext context;
    @LocalServerPort int port;
    private Actor student,other,specialist,admin;

    record Actor(long user,String token) {}

    record Reply(int status,java.net.http.HttpHeaders headers,byte[] bytes) {
        JsonNode json() throws Exception { return JSON.readTree(bytes); }
        Reply expect(int expected) {
            assertThat(status).withFailMessage("Expected %s, got %s. Response: %s",expected,status,
                new String(bytes,0,Math.min(bytes.length,512),StandardCharsets.UTF_8)).isEqualTo(expected);
            return this;
        }
        void problem(int expected,String code) throws Exception {
            expect(expected);
            assertThat(json().path("code").asText()).isEqualTo(code);
        }
        String location() { return headers.firstValue("Location").orElseThrow(); }
    }

    static final class Store implements PrivateFileStorage {
        record ObjectRef(Asset asset,byte[] bytes) {}
        final Map<String,ObjectRef> objects=new ConcurrentHashMap<>();
        final AtomicBoolean uploadFault=new AtomicBoolean();
        final AtomicBoolean corrupt=new AtomicBoolean();
        final AtomicInteger downloads=new AtomicInteger();
        @Override public String cloud() { return "p06-stub"; }
        @Override public Asset upload(String key,Path file,long size) {
            var asset=new Asset(UUID.randomUUID().toString(),1);
            try {
                assertThat(Files.size(file)).isEqualTo(size);
                objects.put(key,new ObjectRef(asset,Files.readAllBytes(file)));
            } catch (IOException failure) { throw new IllegalStateException(failure); }
            if (uploadFault.get()) {
                // Simulate the provider persisting bytes before the caller loses the response.
                throw pbl6.ctsv.shared.files.FileProblem.unavailable();
            }
            return asset;
        }
        @Override public void download(String id,Path destination,long limit) {
            downloads.incrementAndGet();
            var object=objects.values().stream().filter(o -> o.asset().id().equals(id)).findFirst().orElseThrow();
            try {
                var bytes=object.bytes();
                if (corrupt.get()) {
                    bytes=bytes.clone();
                    bytes[0]^=1;
                }
                Files.write(destination,bytes);
            } catch (IOException failure) { throw new IllegalStateException(failure); }
        }
        @Override public Optional<Asset> find(String key) { return Optional.ofNullable(objects.get(key)).map(ObjectRef::asset); }
    }

    @BeforeAll void actors() throws Exception {
        student=actor("student");
        other=actor("student");
        specialist=actor("specialist");
        admin=actor("admin");
    }

    @BeforeEach void isolateRealCloudRun(TestInfo info) {
        if (Boolean.getBoolean("p06.cloudinary.real") && !info.getTestMethod().orElseThrow().getName().equals("realPdfRoundTrip")) {
            throw new IllegalStateException("Cloudinary REAL mode requires the explicit round-trip test");
        }
    }

    private Actor actor(String type) throws Exception {
        return actor(type,"p06."+UUID.randomUUID(),"SYNTHETIC-P06-"+UUID.randomUUID());
    }

    private Actor actor(String type,String name,String password) throws Exception {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT dbo.TaiKhoan(ID,TenDangNhap,TenDangNhapChuanHoa,MatKhauHash) VALUES(?,?,?,?)",
            id.toString(),name,name,new AuthCredentials().hash(password));
        jdbc.update("INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'CTSV_USER'",id.toString());
        if ("admin".equals(type)) jdbc.update(
            "INSERT dbo.TaiKhoan_VaiTro SELECT ?,ID FROM dbo.VaiTro WHERE MaVaiTro=N'AUTH_ADMIN'",id.toString());
        long user=jdbc.queryForObject("""
            INSERT dbo.nguoi_dung(dia_chi_email,dang_hoat_dong) VALUES(?,1);
            SELECT CONVERT(bigint,SCOPE_IDENTITY());
            """,Long.class,name+"@example.invalid");
        jdbc.update("""
            INSERT dbo.dinh_danh_dang_nhap(nguoi_dung_id,ma_nha_cung_cap,ma_chu_the_dang_nhap,thoi_diem_xac_minh)
            VALUES(?,?,?,SYSUTCDATETIME())
            """,user,"https://auth.ctsv.test",id.toString());
        if ("specialist".equals(type)) jdbc.update("""
            INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he)
            SELECT ?,id,N'MGHP' FROM dbo.vai_tro WHERE ma=N'mghp_chuyen_vien'
            """,user);
        if ("student".equals(type)) jdbc.update(
            "INSERT dbo.sinh_vien(mssv,ho_ten,nguoi_dung_id) VALUES(?,N'SYNTHETIC P06 student',?)",name,user);
        var login=send(HttpRequest.newBuilder(uri("/api/xac-thuc/dang-nhap"))
            .header("Content-Type","application/json").header("Origin",AuthHttpIT.ORIGIN).header("X-CSRF-Protection","1")
            .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(Map.of("username",name,"password",password))))).expect(200);
        return new Actor(user,login.json().path("accessToken").asText());
    }

    private URI uri(String path) { return URI.create("https://127.0.0.1:"+port+path); }

    private Reply send(HttpRequest.Builder request) throws Exception {
        try (var client=HttpClient.newBuilder().sslContext(AuthHttpIT.testTls).build()) {
            var r=client.send(request.timeout(Duration.ofSeconds(45)).build(),HttpResponse.BodyHandlers.ofByteArray());
            return new Reply(r.statusCode(),r.headers(),r.body());
        }
    }

    private Reply upload(String type,Actor actor,String name,byte[] bytes) throws Exception {
        return multipart(type,actor,name,bytes,"",false);
    }

    private Reply multipart(String type,Actor actor,String name,byte[] bytes,String extra,boolean chunked) throws Exception {
        return multipart(type,actor,name,bytes,extra,chunked,"");
    }

    private Reply multipart(String type,Actor actor,String name,byte[] bytes,String extra,boolean chunked,String epilogue) throws Exception {
        String boundary="P06-"+UUID.randomUUID();
        var request=HttpRequest.newBuilder(uri(ROOT+"/"+type)).header("Content-Type","multipart/form-data; boundary="+boundary);
        if (actor!=null) request.header("Authorization","Bearer "+actor.token());
        var start=("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name
            +"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        var end=("\r\n"+extra.replace("{boundary}",boundary)+"--"+boundary+"--\r\n"+epilogue).getBytes(StandardCharsets.UTF_8);
        var combined=HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(start),
            HttpRequest.BodyPublishers.ofByteArray(bytes),HttpRequest.BodyPublishers.ofByteArray(end));
        return send(request.POST(chunked ? HttpRequest.BodyPublishers.fromPublisher(combined) : combined));
    }

    private Reply get(String path,Actor actor) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Authorization","Bearer "+actor.token()).GET());
    }

    static byte[] pdf(int bytes,boolean encrypted) throws IOException {
        try (var doc=new PDDocument(); var output=new ByteArrayOutputStream()) {
            var page=new PDPage();
            doc.addPage(page);
            if (bytes>0) {
                var stream=new PDStream(doc);
                try (var body=stream.createOutputStream()) {
                    byte[] comment="%".repeat(79).concat("\n").repeat(100).getBytes(StandardCharsets.US_ASCII);
                    for (int count=0;count<bytes;) {
                        int part=Math.min(bytes-count,comment.length);
                        body.write(comment,0,part);
                        count+=part;
                    }
                }
                page.setContents(stream);
            }
            if (encrypted) doc.protect(new StandardProtectionPolicy("SYNTHETIC-owner","SYNTHETIC-user",new AccessPermission()));
            doc.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] exactPdf(int bytes) throws IOException {
        int content=bytes-600;
        for (int attempt=0;attempt<4;attempt++) {
            byte[] result=pdf(content,false);
            if (result.length==bytes) return result;
            content+=bytes-result.length;
        }
        throw new IllegalStateException("Could not size synthetic PDF");
    }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM dbo."+table,Integer.class); }

    @Test void fileS01_S03_evidenceMetadataAndDownload() throws Exception {
        byte[] pdf=pdf(0,false);
        var reply=upload("minh-chung",student,"../../Việt Nam.pdf",pdf).expect(201);
        var view=reply.json();
        assertThat(view.size()).isEqualTo(7);
        assertThat(view.path("id").isTextual()).isTrue();
        assertThat(view.path("size").asText()).isEqualTo(Integer.toString(pdf.length));
        assertThat(view.path("fileName").asText()).isEqualTo("Việt Nam.pdf");
        assertThat(view.path("state").asText()).isEqualTo("ready");
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf));
        assertThat(view.path("sha256").asText()).isEqualTo(sha);
        long id=view.path("id").asLong();
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM dbo.tep_dinh_kem f JOIN dbo.luot_tai_tep a ON a.tep_id=f.id
            JOIN dbo.lan_kiem_tra_tep i ON i.tep_dinh_kem_id=f.id
            JOIN dbo.nhat_ky_thao_tac n ON n.ma_lien_ket_yeu_cau=a.id
            WHERE f.id=? AND f.nguoi_so_huu_id=? AND f.sha256=? AND f.trang_thai_luu_tru='available'
                AND f.kieu_phan_phoi='authenticated' AND a.trang_thai='committed' AND i.so_trang_pdf=1
                AND i.trang_thai_quet_tep IS NULL AND i.trang_thai_kiem_tra_chu_ky IS NULL
            """,Integer.class,id,student.user(),sha)).isEqualTo(1);
        var download=get(reply.location(),student).expect(200);
        assertThat(download.bytes()).isEqualTo(pdf);
        assertThat(download.headers().firstValue("Content-Type")).hasValue("application/pdf");
        assertThat(download.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(download.headers().firstValue("Content-Disposition").orElseThrow()).startsWith("attachment;").doesNotContain("../","\r","\n");
        // Exercise the same supplied synthetic PDF when the caller opts in to a local path.
        String sample=System.getenv("P06_SAMPLE_PDF");
        if (sample!=null) {
            byte[] supplied=Files.readAllBytes(Path.of(sample));
            var stored=upload("minh-chung",student,"synthetic-sample.pdf",supplied).expect(201);
            assertThat(get(stored.location(),student).expect(200).bytes()).isEqualTo(supplied);
            assertThat(stored.json().path("sha256").asText())
                .isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(supplied)));
        }
    }

    @Test void fileS02_N03_andNotReady() throws Exception {
        var created=upload("van-ban",specialist,"synthetic.pdf",pdf(0,false)).expect(201);
        get(created.location(),specialist).expect(200);
        int calls=STORE.downloads.get();
        get(created.location(),student).problem(404,"NOT_FOUND");
        get(created.location(),admin).problem(403,"FORBIDDEN");
        var evidence=upload("minh-chung",student,"synthetic.pdf",pdf(0,false)).expect(201);
        get(evidence.location(),other).problem(404,"NOT_FOUND");
        assertThat(STORE.downloads.get()).isEqualTo(calls);
        long notReady=jdbc.queryForObject("""
            INSERT dbo.tep_dinh_kem(nguoi_so_huu_id,ten_tep,trang_thai_luu_tru) VALUES(?,'not-ready.pdf','pending');
            SELECT CONVERT(bigint,SCOPE_IDENTITY());
            """,Long.class,student.user());
        get(ROOT+"/"+notReady+"/noi-dung",student).problem(409,"FILE_NOT_READY");
        assertThat(STORE.downloads.get()).isEqualTo(calls);
        upload("minh-chung",null,"invalid.pdf",new byte[0]).problem(401,"UNAUTHENTICATED");
        upload("van-ban",student,"synthetic.pdf",pdf(0,false)).problem(403,"FORBIDDEN");
    }

    @Test void fileN01_badPdfAndExtraPartsNeverCreateLedger() throws Exception {
        int ledger=count("luot_tai_tep");
        upload("minh-chung",student,"bad.pdf","plain text".getBytes(StandardCharsets.UTF_8)).problem(400,"PDF_INVALID");
        upload("minh-chung",student,"broken.pdf","%PDF-1.7\n1 0 obj".getBytes(StandardCharsets.UTF_8)).problem(400,"PDF_INVALID");
        upload("minh-chung",student,"empty.pdf",new byte[0]).problem(400,"PDF_INVALID");
        upload("minh-chung",student,"locked.pdf",pdf(0,true)).problem(400,"PDF_ENCRYPTED");
        multipart("minh-chung",student,"valid.pdf",pdf(0,false),
            "--{boundary}\r\nContent-Disposition: form-data; name=\"extra\"\r\n\r\nignored\r\n",false).problem(400,"VALIDATION_ERROR");
        assertThat(count("luot_tai_tep")).isEqualTo(ledger);
    }

    @Test void fileN02_limitsUseActualBytesEvenWithoutContentLength() throws Exception {
        for (int delta=-1;delta<=1;delta++) {
            var evidence=multipart("minh-chung",student,"evidence.pdf",exactPdf(10_000_000+delta),"",true);
            var document=multipart("van-ban",specialist,"document.pdf",exactPdf(10_000_000+delta),"",true);
            if (delta>0) {
                evidence.problem(413,"FILE_TOO_LARGE");
                document.problem(413,"FILE_TOO_LARGE");
            } else {
                evidence.expect(201);
                document.expect(201);
            }
        }
        multipart("minh-chung",student,"evidence.pdf",exactPdf(10_000_000),"",true,"x".repeat(65_537))
            .problem(413,"FILE_TOO_LARGE");
    }

    @Test void corsOnlyPermitsTheConfiguredFrontend() throws Exception {
        var ok=send(HttpRequest.newBuilder(uri(ROOT+"/minh-chung")).header("Origin",AuthHttpIT.ORIGIN)
            .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","authorization,content-type")
            .method("OPTIONS",HttpRequest.BodyPublishers.noBody())).expect(200);
        assertThat(ok.headers().firstValue("Access-Control-Allow-Origin")).hasValue(AuthHttpIT.ORIGIN);
        send(HttpRequest.newBuilder(uri(ROOT+"/minh-chung")).header("Origin","https://foreign.example.invalid")
            .header("Access-Control-Request-Method","POST").method("OPTIONS",HttpRequest.BodyPublishers.noBody())).expect(403);
    }

    @Test void applicationListAndInboxAreScopedAndRejectUnknownQuery() throws Exception {
        var isolated=actor("student");
        get("/api/ctsv/mghp/ho-so?studentId=1",isolated).problem(400,"VALIDATION_ERROR");
        var list=get("/api/ctsv/mghp/ho-so",isolated).expect(200).json();
        assertThat(list.path("items").isArray()).isTrue();
        assertThat(list.path("totalElements").asInt()).isZero();
        get("/api/ctsv/toi/thong-bao?unreadOnly=invalid",isolated).problem(400,"VALIDATION_ERROR");
        assertThat(get("/api/ctsv/toi/thong-bao",isolated).expect(200).json()
            .path("totalElements").asInt()).isZero();
    }

    record IntakeFixture(String code,long round,long process,long policy,long semester,long ethnicity,long classId,long cohort,long unit) {}

    private long insert(String sql,Object... arguments) {
        return jdbc.queryForObject(sql+"; SELECT CONVERT(bigint,SCOPE_IDENTITY());",Long.class,arguments);
    }

    private IntakeFixture intake(Actor actor,boolean future) {
        return intake(actor,future,false);
    }

    private IntakeFixture intake(Actor actor,boolean future,boolean m6) {
        var unique=UUID.randomUUID().toString();
        long studentId=jdbc.queryForObject("SELECT id FROM dbo.sinh_vien WHERE nguoi_dung_id=?",Long.class,actor.user());
        String code=jdbc.queryForObject("SELECT mssv FROM dbo.sinh_vien WHERE id=?",String.class,studentId);
        long epoch=insert("INSERT dbo.dan_toc(ten) VALUES(N'SYNTHETIC ethnic group')");
        long unit=insert("INSERT dbo.don_vi(ma,ten) VALUES(?,N'SYNTHETIC department')","TEST-P06-"+unique);
        long cohort=insert("INSERT dbo.khoa_hoc(ma) VALUES(?)","TEST-P06-"+unique);
        long cls=insert("INSERT dbo.lop_sinh_hoat(ma,khoa_hoc_id,khoa_quan_ly_id) VALUES(?,?,?)","TEST-P06-"+unique,cohort,unit);
        long place=insert("INSERT dbo.dia_phuong(ten) VALUES(N'SYNTHETIC place')");
        long sem=insert("""
            INSERT dbo.hoc_ky(ma,ten,ngay_bat_dau,ngay_ket_thuc,trang_thai_xac_minh_moc,can_cu_xac_minh_moc)
            VALUES(?,N'SYNTHETIC P06 kỳ','20260901','20261231','da_xac_minh',N'SYNTHETIC')
            ""","TEST-P06-"+unique);
        jdbc.update("""
            UPDATE dbo.sinh_vien SET so_cccd='000000123', ngay_sinh='20050820', noi_sinh_id=?,gioi_tinh=N'NAM',
                dan_toc_id=?,lop_hien_tai_id=?,khoa_hoc_hien_tai_id=?,khoa_quan_ly_hien_tai_id=?,so_dien_thoai='0900000000',
                thoi_diem_dong_bo=SYSUTCDATETIME()
            WHERE id=?
            """,place,epoch,cls,cohort,unit,studentId);
        long process=insert("INSERT dbo.qua_trinh_theo_hoc(sinh_vien_id,khoa_hoc_id,hoc_ky_bat_dau_id,ma_qua_trinh_hoc) VALUES(?,?,?,?)",
            studentId,cohort,sem,"TEST-P06-"+unique);
        long parent=jdbc.queryForObject("SELECT id FROM dbo.mghp_chinh_sach WHERE ma=?",Long.class,m6?"M6":"M2");
        int next=jdbc.queryForObject("SELECT COALESCE(MAX(phien_ban),0)+1 FROM dbo.mghp_phien_ban_chinh_sach WHERE chinh_sach_id=?",Integer.class,parent);
        long policy=insert("""
            INSERT dbo.mghp_phien_ban_chinh_sach(chinh_sach_id,phien_ban,ten_tai_thoi_diem_chot,ty_le_giam,
              ngay_bat_dau_hieu_luc,ma_pham_vi,trang_thai) VALUES(?,?,N'SYNTHETIC P06 chính sách',1,'20260101','hoc_phi','draft')
            """,parent,next);
        long group=insert("INSERT dbo.mghp_nhom_yeu_cau_giay_to(phien_ban_chinh_sach_id,ma,ten,so_yeu_cau_toi_thieu_dat) VALUES(?,'TEST','SYNTHETIC',1)",policy);
        jdbc.update("""
            INSERT dbo.mghp_giay_to_bat_buoc(nhom_id,ma,ten,loai_giay_to,chu_the_minh_chung)
            VALUES(?,'TEST','SYNTHETIC P06 PDF',?,'sinh_vien')
            """,group,m6?"xac_nhan_cu_tru":"xac_nhan_khuyet_tat");
        if(m6) {
            jdbc.update("UPDATE dbo.mghp_nhom_yeu_cau_giay_to SET so_yeu_cau_toi_thieu_dat=2 WHERE id=?",group);
            jdbc.update("""
                INSERT dbo.mghp_giay_to_bat_buoc(nhom_id,ma,ten,loai_giay_to,chu_the_minh_chung)
                VALUES(?,'TEST-PARENT','SYNTHETIC P06 PARENT','xac_nhan_cu_tru','cha_hoac_me')
                """,group);
        }
        jdbc.update("UPDATE dbo.mghp_phien_ban_chinh_sach SET trang_thai='locked',thoi_diem_khoa=SYSUTCDATETIME() WHERE id=?",policy);
        long round=insert("""
            INSERT dbo.mghp_dot_xet(ma_dot_xet,hoc_ky_id,ten,trang_thai,thoi_diem_mo,han_nop,
                han_ra_soat,cho_phep_truy_linh)
            VALUES(?,?,N'SYNTHETIC P06 đợt xét','open',DATEADD(day,?,SYSUTCDATETIME()),
                DATEADD(day,2,SYSUTCDATETIME()),DATEADD(day,4,SYSUTCDATETIME()),1)
            ""","TEST-P06-"+unique,sem,future?1:-1);
        jdbc.update("INSERT dbo.mghp_chinh_sach_dot_xet(dot_xet_id,phien_ban_chinh_sach_id) VALUES(?,?)",round,policy);
        return new IntakeFixture(code,round,process,policy,sem,epoch,cls,cohort,unit);
    }

    private Reply command(String method,String path,Actor actor,JsonNode body,String tag) throws Exception {
        var builder=HttpRequest.newBuilder(uri(path)).header("Authorization","Bearer "+actor.token());
        if(tag!=null)builder.header("If-Match",tag);
        if(body!=null)builder.header("Content-Type","application/json");
        return send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():
            HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))));
    }

    private com.fasterxml.jackson.databind.node.ObjectNode submission(IntakeFixture f,long file) {
        long req=jdbc.queryForObject("""
            SELECT r.id FROM dbo.mghp_giay_to_bat_buoc r JOIN dbo.mghp_nhom_yeu_cau_giay_to g ON g.id=r.nhom_id
            WHERE g.phien_ban_chinh_sach_id=? AND r.chu_the_minh_chung='sinh_vien'
            """,Long.class,f.policy());
        var n=JSON.createObjectNode();
        n.put("operationId",UUID.randomUUID().toString()).put("roundId",""+f.round()).put("studyProcessId",""+f.process())
            .put("policyVersionId",""+f.policy());
        n.putArray("requestedSemesterIds").add(""+f.semester());
        n.putObject("claims").put("fullName","SYNTHETIC P06 student").put("citizenId","000000123").put("birthDate","2005-08-20")
            .put("birthPlace","SYNTHETIC place").put("gender","NAM").put("ethnicityId",""+f.ethnicity())
            .put("classId",""+f.classId()).put("studentCode",f.code()).put("cohortId",""+f.cohort())
            .put("managementUnitId",""+f.unit()).put("permanentAddress","SYNTHETIC address").put("phoneNumber","0900000000");
        n.putObject("statements").putNull("firstDegree").putNull("alreadyBenefitedAtSameLevel")
            .putNull("salariedWhileStudying").put("declarationConfirmed",true);
        n.putArray("relatedPeople");
        n.putArray("documents").addObject().put("requirementId",""+req).put("fileId",""+file).putNull("documentNumber")
            .putNull("issuer").putNull("issuedOn").putNull("expiresOn").putNull("relatedPersonIndex");
        return n;
    }

    @Test void submissionHistoryReplayWithdrawReopenAndInbox() throws Exception {
        var owner=actor("student");
        var f=intake(owner,false);
        String base="/api/ctsv/mghp/ho-so";
        var form=get("/api/ctsv/mghp/toi/ngu-canh-ke-khai?dot_xet_id="+f.round(),owner).expect(200).json();
        assertThat(form.path("suggestedClaims").path("permanentAddress").isNull()).isTrue();
        assertThat(form.path("policyVersions").get(0).has("legalBasis")).isFalse();
        long file=upload("minh-chung",owner,"evidence.pdf",pdf(0,false)).expect(201).json().path("id").asLong();
        var body=submission(f,file);
        var created=command("POST",base,owner,body,null).expect(201);
        var ack=created.json();
        String id=ack.path("applicationId").asText();
        assertThat(command("POST",base,owner,body,null).expect(200).json()).isEqualTo(ack);
        var changed=body.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode)changed.path("claims")).put("permanentAddress","OTHER");
        command("POST",base,owner,changed,null).problem(409,"SUBMISSION_KEY_CONFLICT");
        changed.put("operationId",UUID.randomUUID().toString());
        command("POST",base,owner,changed,null).problem(409,"APPLICATION_EXISTS");
        get(base+"/"+id,other).problem(404,"NOT_FOUND");
        assertThat(get(created.location(),owner).expect(200).json().path("claims").path("citizenId").asText()).isEqualTo("000000123");
        var first=get(base+"/"+id,owner).expect(200);
        String initialTag=first.headers().firstValue("ETag").orElseThrow();
        assertThat(first.json().path("submittedVersions").get(0).path("documents").get(0).path("fileId").asLong()).isEqualTo(file);
        var reason=JSON.createObjectNode().put("reason","SYNTHETIC P06");
        command("POST",base+"/"+id+"/rut",owner,reason,null).problem(428,"PRECONDITION_REQUIRED");
        assertThat(command("POST",base+"/"+id+"/rut",owner,reason,initialTag).expect(200).json().path("status").asText()).isEqualTo("withdrawn");
        command("POST",base+"/"+id+"/mo-lai",specialist,reason,initialTag).problem(412,"VERSION_CONFLICT");
        String withdrawn=get(base+"/"+id,specialist).expect(200).headers().firstValue("ETag").orElseThrow();
        command("POST",base+"/"+id+"/mo-lai",specialist,reason,withdrawn).expect(200);
        String reopened=get(base+"/"+id,owner).expect(200).headers().firstValue("ETag").orElseThrow();
        var second=command("POST",base+"/"+id+"/ban-nop",owner,changed,reopened).expect(201).json();
        assertThat(second.path("revision").asInt()).isEqualTo(2);
        command("POST",base+"/"+id+"/ban-nop",owner,changed,reopened).expect(200);
        assertThat(get(base+"/"+id,owner).expect(200).json().path("submittedVersions").size()).isEqualTo(2);
        var inbox=get("/api/ctsv/toi/thong-bao?unreadOnly=true",owner).expect(200).json();
        assertThat(inbox.path("totalElements").asInt()).isEqualTo(4);
        String read="/api/ctsv/toi/thong-bao/"+inbox.path("items").get(0).path("id").asText()+"/da-doc";
        command("PUT",read,other,null,null).problem(404,"NOT_FOUND");
        var firstRead=command("PUT",read,owner,null,null).expect(200).json();
        assertThat(command("PUT",read,owner,null,null).expect(200).json()).isEqualTo(firstRead);
    }

    @Test void deadlineAndWrongFileOwnerLeaveNoSubmission() throws Exception {
        var owner=actor("student");
        var f=intake(owner,true);
        long file=upload("minh-chung",other,"other.pdf",pdf(0,false)).expect(201).json().path("id").asLong();
        String base="/api/ctsv/mghp/ho-so";
        var body=submission(f,file);
        command("POST",base,owner,body,null).problem(409,"SUBMISSION_NOT_ALLOWED");
        jdbc.update("UPDATE dbo.mghp_dot_xet SET thoi_diem_mo=DATEADD(day,-1,SYSUTCDATETIME()) WHERE id=?",f.round());
        command("POST",base,owner,body,null).problem(404,"NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_ho_so WHERE dot_xet_id=?",Integer.class,f.round())).isZero();
        assertThat(get("/api/ctsv/toi/thong-bao",owner).expect(200).json().path("totalElements").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_phien_ban_ho_so WHERE ma_thao_tac_nop=?",
            Integer.class,body.path("operationId").asText())).isZero();
    }

    @Test void concurrentSameOperationAndConfirmedAssessmentFence() throws Exception {
        var owner=actor("student");
        var f=intake(owner,false);
        long file=upload("minh-chung",owner,"evidence.pdf",pdf(0,false)).expect(201).json().path("id").asLong();
        var payload=submission(f,file);
        String base="/api/ctsv/mghp/ho-so";
        Reply a,b;
        try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var start=new java.util.concurrent.CyclicBarrier(2);
            var one=executor.submit(()->{start.await();return command("POST",base,owner,payload,null);});
            var two=executor.submit(()->{start.await();return command("POST",base,owner,payload,null);});
            a=one.get(); b=two.get();
        }
        assertThat(java.util.List.of(a.status(),b.status())).containsExactlyInAnyOrder(201,200);
        assertThat(a.json()).isEqualTo(b.json());
        long id=a.json().path("applicationId").asLong();
        jdbc.update("""
            INSERT dbo.mghp_danh_gia_dieu_kien(sinh_vien_id,qua_trinh_hoc_id,hoc_ky_ap_dung_id,
              phien_ban_chinh_sach_id,so_phien_ban_danh_gia,ma_pham_vi,ho_so_nguon_id,phien_ban_ho_so_nguon_id,
              trang_thai_xu_ly,ket_qua)
            SELECT h.sinh_vien_id,h.qua_trinh_hoc_id,?,h.phien_ban_chinh_sach_id,1,'hoc_phi',h.id,v.id,'confirmed','can_xac_minh'
            FROM dbo.mghp_ho_so h JOIN dbo.mghp_phien_ban_ho_so v ON v.ho_so_id=h.id WHERE h.id=?
            """,f.semester(),id);
        String tag=get(base+"/"+id,owner).expect(200).headers().firstValue("ETag").orElseThrow();
        command("POST",base+"/"+id+"/rut",owner,JSON.createObjectNode().put("reason","SYNTHETIC"),tag)
            .problem(409,"APPLICATION_NOT_WITHDRAWABLE");
    }

    @Test void rollbackAfterSubmissionChildrenAndNotificationLeavesOperationReusable() throws Exception {
        var owner=actor("student");
        var f=intake(owner,false);
        long file=upload("minh-chung",owner,"evidence.pdf",pdf(0,false)).expect(201).json().path("id").asLong();
        var payload=submission(f,file);
        String base="/api/ctsv/mghp/ho-so";
        int roots=count("mghp_ho_so"),versions=count("mghp_phien_ban_ho_so"),people=count("mghp_nguoi_lien_quan"),
            papers=count("mghp_giay_to_ho_so"),links=count("mghp_tep_minh_chung"),comparisons=count("mghp_doi_chieu_ke_khai"),
            semesters=count("mghp_hoc_ky_de_nghi"),audit=count("mghp_nhat_ky_ho_so"),notices=count("nguoi_nhan_thong_bao");
        try {
            jdbc.execute("""
                CREATE TRIGGER dbo.p06_synthetic_rollback ON dbo.nguoi_nhan_thong_bao AFTER INSERT AS
                BEGIN
                    IF EXISTS(SELECT 1 FROM inserted WHERE nguoi_dung_id=ACTOR_ID)
                        THROW 51707,'Synthetic P06 recipient fault',1;
                END
                """.replace("ACTOR_ID",Long.toString(owner.user())));
            command("POST",base,owner,payload,null).problem(500,"INTERNAL_ERROR");
            assertThat(count("mghp_ho_so")).isEqualTo(roots);
            assertThat(count("mghp_phien_ban_ho_so")).isEqualTo(versions);
            assertThat(count("mghp_nguoi_lien_quan")).isEqualTo(people);
            assertThat(count("mghp_giay_to_ho_so")).isEqualTo(papers);
            assertThat(count("mghp_tep_minh_chung")).isEqualTo(links);
            assertThat(count("mghp_doi_chieu_ke_khai")).isEqualTo(comparisons);
            assertThat(count("mghp_hoc_ky_de_nghi")).isEqualTo(semesters);
            assertThat(count("mghp_nhat_ky_ho_so")).isEqualTo(audit);
            assertThat(count("nguoi_nhan_thong_bao")).isEqualTo(notices);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS dbo.p06_synthetic_rollback");
        }
        assertThat(command("POST",base,owner,payload,null).expect(201).json().path("comparisonStatus").asText())
            .isEqualTo("khong_co_nguon");
    }

    @Test void m6RequiresTwoSubjectsAndRetainsBothRequirementsForOnePdf() throws Exception {
        var owner=actor("student");
        var f=intake(owner,false,true);
        long file=upload("minh-chung",owner,"residency.pdf",pdf(0,false)).expect(201).json().path("id").asLong();
        var payload=submission(f,file);
        String base="/api/ctsv/mghp/ho-so";
        command("POST",base,owner,payload,null).problem(400,"VALIDATION_ERROR");
        var parent=payload.withArray("relatedPeople").addObject().put("relation","mẹ").put("fullName","SYNTHETIC parent");
        for(String name:java.util.List.of("citizenId","ethnicityId","permanentAddress","localityCode","workplaceUnitId",
            "workplaceName","workplaceGroup","employmentStatus","statusFrom","statusUntil","matchingStaffCode")) parent.putNull(name);
        long requirement=jdbc.queryForObject("""
            SELECT r.id FROM dbo.mghp_giay_to_bat_buoc r JOIN dbo.mghp_nhom_yeu_cau_giay_to g ON g.id=r.nhom_id
            WHERE g.phien_ban_chinh_sach_id=? AND r.chu_the_minh_chung='cha_hoac_me'
            """,Long.class,f.policy());
        payload.withArray("documents").addObject().put("requirementId",""+requirement).put("fileId",""+file)
            .putNull("documentNumber").putNull("issuer").putNull("issuedOn").putNull("expiresOn").put("relatedPersonIndex",0);
        var created=command("POST",base,owner,payload,null).expect(201).json();
        var documents=get(base+"/"+created.path("applicationId").asText(),owner).expect(200).json()
            .path("submittedVersions").get(0).path("documents");
        assertThat(documents.size()).isEqualTo(2);
        assertThat(documents.get(0).path("fileId")).isEqualTo(documents.get(1).path("fileId"));
        assertThat(documents.get(0).path("requirementId")).isNotEqualTo(documents.get(1).path("requirementId"));
        assertThat(documents.get(0).path("relatedPersonIndex").isNull()).isTrue();
        assertThat(documents.get(1).path("relatedPersonIndex").asInt()).isZero();
    }

    @Test
    @EnabledIfSystemProperty(named="p06.browser.enabled",matches="true")
    void serveBrowser() throws Exception {
        Path home=Path.of(java.util.Objects.requireNonNull(System.getenv("P06_BROWSER_HOME")));
        var done=home.resolve("browser.done");
        assertThat(Files.exists(done)).isFalse();
        String browserUser="p06."+UUID.randomUUID(), browserOther="p06."+UUID.randomUUID();
        String password="SYNTHETIC-P06-"+UUID.randomUUID();
        var owner=actor("student",browserUser,password);
        actor("student",browserOther,password);
        var fixture=intake(owner,false);
        var body=Map.of("origin","https://127.0.0.1:5175","port",port,"round",Long.toString(fixture.round()),
            "username",browserUser,"other",browserOther,"password",password,
            "storage",Boolean.getBoolean("p06.browser.cloudinary.real")?"Cloudinary REAL":"Cloudinary STUB");
        Files.writeString(home.resolve("credentials.json"),JSON.writeValueAsString(body),java.nio.file.StandardOpenOption.CREATE_NEW);
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(4);
        while(!Files.exists(done) && System.nanoTime()<deadline)Thread.sleep(200);
        assertThat(done).exists();
        assertThat(Files.readString(done).trim()).isEqualTo("PASS");
    }

    @Test void fileN04_sqlFailureRollsBackMetadataAndReadOnlyReconciliationFindsOrphan() throws Exception {
        int metadata=count("tep_dinh_kem"), inspections=count("lan_kiem_tra_tep"), audits=count("nhat_ky_thao_tac");
        int assets=STORE.objects.size();
        SQL_FAULT.set(true);
        try {
            upload("minh-chung",student,"synthetic.pdf",pdf(0,false)).problem(500,"INTERNAL_ERROR");
        } finally { SQL_FAULT.set(false); }
        assertThat(count("tep_dinh_kem")).isEqualTo(metadata);
        assertThat(count("lan_kiem_tra_tep")).isEqualTo(inspections);
        assertThat(count("nhat_ky_thao_tac")).isEqualTo(audits);
        assertThat(STORE.objects).hasSize(assets+1);
        reconcileLatest();
    }

    @Test void fileN04_storageFailureLeavesOnlyRecoverableLedger() throws Exception {
        int metadata=count("tep_dinh_kem");
        STORE.uploadFault.set(true);
        try {
            upload("minh-chung",student,"synthetic.pdf",pdf(0,false)).problem(503,"STORAGE_UNAVAILABLE");
        } finally { STORE.uploadFault.set(false); }
        assertThat(count("tep_dinh_kem")).isEqualTo(metadata);
        reconcileLatest();
    }

    private void reconcileLatest() throws Exception {
        String id=jdbc.queryForObject("""
            SELECT TOP 1 CONVERT(varchar(36),id) FROM dbo.luot_tai_tep
            WHERE trang_thai='unknown' AND nguoi_dung_id=? ORDER BY thoi_diem_tao DESC
            """,String.class,student.user());
        jdbc.update("UPDATE dbo.luot_tai_tep SET doi_soat_sau=DATEADD(minute,-1,SYSUTCDATETIME()) WHERE id=?",id);
        var worker=context.getBean("fileReconciliation");
        var method=worker.getClass().getDeclaredMethod("reconcile");
        method.setAccessible(true);
        method.invoke(worker);
        assertThat(jdbc.queryForObject("SELECT trang_thai FROM dbo.luot_tai_tep WHERE id=? AND tep_id IS NULL",String.class,id))
            .isEqualTo("orphan");
    }

    @Test void fileN05_corruptDownloadFailsBeforeAnyBytes() throws Exception {
        var created=upload("minh-chung",student,"synthetic.pdf",pdf(0,false)).expect(201);
        STORE.corrupt.set(true);
        try { get(created.location(),student).problem(502,"FILE_INTEGRITY_FAILED"); }
        finally { STORE.corrupt.set(false); }
    }

    @Test void linkedFileDeniesUploaderFallbackAndOnlineRevocationDeniesOwner() throws Exception {
        var created=upload("van-ban",specialist,"synthetic.pdf",pdf(0,false)).expect(201);
        long file=created.json().path("id").asLong();
        long document=jdbc.queryForObject("""
            INSERT dbo.van_ban(ma_van_ban,ma_phan_he,tieu_de) VALUES(NEWID(),'MGHP','SYNTHETIC P06');
            SELECT CONVERT(bigint,SCOPE_IDENTITY());
            """,Long.class);
        jdbc.update("INSERT dbo.phien_ban_van_ban(van_ban_id,so_phien_ban,tep_goc_id) VALUES(?,1,?)",document,file);
        int calls=STORE.downloads.get();
        get(created.location(),specialist).problem(404,"NOT_FOUND");
        assertThat(STORE.downloads.get()).isEqualTo(calls);

        var isolated=actor("student");
        var evidence=upload("minh-chung",isolated,"synthetic.pdf",pdf(0,false)).expect(201);
        jdbc.update("""
            UPDATE a SET a.PhienBanToken=a.PhienBanToken+1 FROM dbo.TaiKhoan a
            JOIN dbo.dinh_danh_dang_nhap d ON CONVERT(varchar(36),a.ID)=d.ma_chu_the_dang_nhap
            WHERE d.nguoi_dung_id=?
            """,isolated.user());
        get(evidence.location(),isolated).problem(401,"TOKEN_REVOKED");
        assertThat(STORE.downloads.get()).isEqualTo(calls);
    }

    @Test void ledgerEnforcesOwnerHashAndReadyState() {
        String insert="""
            INSERT dbo.luot_tai_tep(id,nguoi_dung_id,ten_cloud,ma_cong_khai,loai_tep,kich_thuoc,sha256,trang_thai,doi_soat_sau)
            VALUES(NEWID(),?,'p06-stub',CONVERT(varchar(36),NEWID()),?,?,?,?,SYSUTCDATETIME())
            """;
        assertThatThrownBy(() -> jdbc.update(insert,student.user(),"evidence",1,"A".repeat(64),"started"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert,student.user(),"evidence",10_000_001,"a".repeat(64),"started"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert,specialist.user(),"document",10_000_001,"a".repeat(64),"started"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert,student.user(),"evidence",1,"a".repeat(64),"committed"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insert,-1,"evidence",1,"a".repeat(64),"started"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @EnabledIfSystemProperty(named="p06.cloudinary.real",matches="true")
    void realPdfRoundTrip() throws Exception {
        byte[] sample=Files.readAllBytes(Path.of(java.util.Objects.requireNonNull(System.getenv("P06_SAMPLE_PDF"))));
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sample));
        var result=upload("minh-chung",student,"synthetic-p06-cloudinary.pdf",sample).expect(201);
        long id=result.json().path("id").asLong();
        assertThat(result.json().path("sha256").asText()).isEqualTo(hash);
        assertThat(result.json().size()).isEqualTo(7);
        var download=get(result.location(),student).expect(200);
        assertThat(download.bytes()).isEqualTo(sample);
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(download.bytes()))).isEqualTo(hash);
        get(result.location(),other).problem(404,"NOT_FOUND");
        var cloud=jdbc.queryForMap("""
            SELECT f.ten_cloud,f.ma_cong_khai,f.phien_ban_luu_tru FROM dbo.tep_dinh_kem f
            JOIN dbo.luot_tai_tep l ON l.tep_id=f.id AND l.ma_tai_nguyen=f.ma_tai_nguyen
            JOIN dbo.lan_kiem_tra_tep k ON k.tep_dinh_kem_id=f.id AND k.sha256_thuc_te=f.sha256
            WHERE f.id=? AND f.sha256=? AND f.kieu_phan_phoi='authenticated' AND f.loai_tai_nguyen='raw'
                AND f.trang_thai_luu_tru='available' AND l.trang_thai='committed'
            """,id,hash);
        String folder=java.util.Objects.requireNonNull(System.getenv("CLOUDINARY_FOLDER"));
        String mode=java.util.Objects.requireNonNull(System.getenv("CLOUDINARY_FOLDER_MODE"));
        var unsignedStatuses=new java.util.ArrayList<Integer>(2);
        try (var publicClient=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            String key=java.util.Objects.requireNonNull(System.getenv("CLOUDINARY_API_KEY"));
            String secret=java.util.Objects.requireNonNull(System.getenv("CLOUDINARY_API_SECRET"));
            String basic=java.util.Base64.getEncoder().encodeToString((key+":"+secret).getBytes(StandardCharsets.UTF_8));
            var assetUri=URI.create("https://api.cloudinary.com/v1_1/"+cloud.get("ten_cloud")
                +"/resources/raw/authenticated/"+java.net.URLEncoder.encode(cloud.get("ma_cong_khai").toString(),StandardCharsets.UTF_8));
            var asset=publicClient.send(HttpRequest.newBuilder(assetUri).timeout(Duration.ofSeconds(20))
                .header("Authorization","Basic "+basic).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            assertThat(asset.statusCode()).as("Cloudinary authenticated asset metadata status").isEqualTo(200);
            assertThat(JSON.readTree(asset.body()).path("fixed".equals(mode) ? "folder" : "asset_folder").asText())
                .as("Cloudinary asset folder").isEqualTo(folder);
            for (String delivery : java.util.List.of("upload","authenticated")) {
                var unsigned=URI.create("https://res.cloudinary.com/"+cloud.get("ten_cloud")+"/raw/"+delivery
                    +"/v"+cloud.get("phien_ban_luu_tru")+"/"+cloud.get("ma_cong_khai"));
                var response=publicClient.send(HttpRequest.newBuilder(unsigned).timeout(Duration.ofSeconds(20))
                    .GET().build(),HttpResponse.BodyHandlers.discarding());
                assertThat(response.statusCode()).as("Unsigned Cloudinary delivery status").isIn(401,403,404);
                unsignedStatuses.add(response.statusCode());
            }
        }
        System.out.printf("P06 Cloudinary REAL: upload=201, owner=200, other=404, SQL=committed, folder=verified, bytes=%d, sha256=%s, unsigned=%s%n",
            sample.length,hash,unsignedStatuses);
    }
}
