package pbl6.ctsv.shcn.phancongkhoa;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shared.ca.CaPdfPreflight;
import pbl6.ctsv.shared.ca.CaProviderDtos.Certificates;
import pbl6.ctsv.shared.files.ManagedFiles;
import pbl6.ctsv.shcn.foundation.PhanCongKhoaFoundationPort;
import pbl6.ctsv.shcn.foundation.PhanCongKhoaFoundationPort.*;
import pbl6.ctsv.shcn.phancongkhoa.controller.PhanCongKhoaController;
import pbl6.ctsv.shcn.phancongkhoa.controller.PhanCongKhoaErrors;
import pbl6.ctsv.shcn.phancongkhoa.dto.PhanCongKhoaDtos.*;
import pbl6.ctsv.shcn.phancongkhoa.entity.DanhSachPhanCongKhoa;
import pbl6.ctsv.shcn.phancongkhoa.entity.PhanCongGvcnKhoa;
import pbl6.ctsv.shcn.phancongkhoa.pdf.PhanCongPdfGenerator;
import pbl6.ctsv.shcn.phancongkhoa.repository.PhanCongKhoaAudit;
import pbl6.ctsv.shcn.phancongkhoa.repository.PhanCongKhoaRepository;
import pbl6.ctsv.shcn.phancongkhoa.service.PhanCongKhoaService;
import pbl6.ctsv.shcn.phancongkhoa.signing.*;
import pbl6.ctsv.shcn.phancongkhoa.signing.PhanCongSigningRepository.*;
import pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PhanCongSigningServiceTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-11-26T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final byte[] TOKEN_V1 = new byte[]{0, 0, 0, 0, 0, 0, 0, 1};
    private static final String TOKEN_V1_B64 = Base64.getEncoder().encodeToString(TOKEN_V1);

    private final ObjectMapper json = new ObjectMapper();
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final AuthAccess auth = mock(AuthAccess.class);
    private final FoundationAccess identities = mock(FoundationAccess.class);
    private final PhanCongKhoaFoundationPort foundation = mock(PhanCongKhoaFoundationPort.class);
    private final PhanCongKhoaRepository listRepo = mock(PhanCongKhoaRepository.class);
    private final PhanCongSigningRepository signingRepo = mock(PhanCongSigningRepository.class);
    private final PhanCongKhoaAudit audit = mock(PhanCongKhoaAudit.class);
    private final ManagedFiles managedFiles = mock(ManagedFiles.class);
    private final AtomicLong triggeredRequest = new AtomicLong(-1L);

    private MockEnvironment env;
    private PhanCongSigningOptions options;
    private PhanCongKhoaService phanCongService;
    private PhanCongSigningService signingService;
    private MockMvc mvc;
    private AuthAccess.Principal principal;

    @BeforeEach
    void setUp() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        env = new MockEnvironment()
                .withProperty("ctsv.shcn.signing.enabled", "true")
                .withProperty("ctsv.shcn.signing.mode", "TEST")
                .withProperty("ctsv.shcn.signing.provider-id", "1")
                .withProperty("ctsv.shcn.signing.category-id", "11111111-1111-1111-1111-111111111111")
                .withProperty("ctsv.shcn.signing.test-signer-email", "officer.e@dut.udn.vn");
        options = new PhanCongSigningOptions(env);
        principal = new AuthAccess.Principal("https://127.0.0.1:8443", UUID.randomUUID(), 0L);
        when(auth.verifyAccessToken("valid-token")).thenReturn(principal);
        when(identities.identify(eq(principal), anyBoolean()))
                .thenReturn(new FoundationAccess.Identity(99L, null, principal));
        when(foundation.maLuuDuoc(anyString())).thenReturn(true);
        when(foundation.nguoiDung(99L))
                .thenReturn(Optional.of(new NguoiDungKhoa(99L, "PGS.TS. Nguyễn Trưởng Khoa", "dev.truongkhoa@example.invalid")));

        ObjectProvider<ManagedFiles> filesProvider = new ObjectProvider<>() {
            @Override public ManagedFiles getObject(Object... args) { return managedFiles; }
            @Override public ManagedFiles getIfAvailable() { return managedFiles; }
            @Override public ManagedFiles getIfUnique() { return managedFiles; }
            @Override public ManagedFiles getObject() { return managedFiles; }
        };

        phanCongService = new PhanCongKhoaService(txManager, identities, foundation, listRepo, audit, CLOCK);
        signingService = new PhanCongSigningService(
                txManager, identities, foundation, listRepo, signingRepo, phanCongService,
                audit, new PhanCongPdfGenerator(), options, filesProvider, triggeredRequest::set, CLOCK);

        var controller = new PhanCongKhoaController(auth, phanCongService, signingService, new PhanCongKhoaRequests(json));
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new PhanCongKhoaErrors()).build();
    }

    private Actor fullHeadActor(String maKhoa, boolean lap, boolean kySo, boolean guiCv) {
        Khoa k = new Khoa(10L, maKhoa, "Khoa Công nghệ thông tin", true, lap, kySo, guiCv, true);
        return new Actor(99L, principal.accountId().toString(), true, lap, kySo, guiCv, List.of(k));
    }

    private DanhSachPhanCongKhoa mockRoot(long id, String maKhoa, int phienBan, String trangThai) throws Exception {
        var root = DanhSachPhanCongKhoa.nhap(
                maKhoa, "2026-2027", phienBan, null,
                OffsetDateTime.parse("2026-09-01T00:00:00+07:00"), null,
                principal.accountId().toString(), null, OffsetDateTime.now(CLOCK));
        setField(root, "id", id);
        setField(root, "trangThai", trangThai);
        setField(root, "phienBanGhi", TOKEN_V1.clone());
        return root;
    }

    private PhanCongGvcnKhoa mockLine(long id, long listId, String maLop, String maGv, String note) throws Exception {
        var row = new PhanCongGvcnKhoa(listId, maLop, maGv, note, OffsetDateTime.now(CLOCK));
        setField(row, "id", id);
        return row;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void optionsUseTestEmailInTestModeAndUserEmailInProductionMode() {
        var profile = new NguoiDungKhoa(99L, "Trưởng Khoa A", "truongkhoa.real@dut.udn.vn");
        var testPlan = options.resolve(profile);
        assertThat(testPlan.mode()).isEqualTo("TEST");
        assertThat(testPlan.signerEmail()).isEqualTo("officer.e@dut.udn.vn");

        env.setProperty("ctsv.shcn.signing.mode", "PRODUCTION");
        var prodPlan = options.resolve(profile);
        assertThat(prodPlan.mode()).isEqualTo("PRODUCTION");
        assertThat(prodPlan.signerEmail()).isEqualTo("truongkhoa.real@dut.udn.vn");
    }

    @Test
    void exportPdfBlocksIncompleteFacultyClassesWith409AndSucceedsAt100Percent() throws Exception {
        when(foundation.quyen(any())).thenReturn(fullHeadActor("CNTT", true, true, true));
        var root = mockRoot(55L, "CNTT", 1, "NHAP");
        when(listRepo.khoaDanhSach(55L)).thenReturn(Optional.of(root));

        LopDTO lop1 = new LopDTO("1", "22T_DT1", "22T_DT1", "CNTT", null, true, null);
        LopDTO lop2 = new LopDTO("2", "22T_DT2", "22T_DT2", "CNTT", null, true, null);
        GiangVienDTO gv1 = new GiangVienDTO("1", "GV01", "TS. Nguyễn Văn A", "CNTT", true, null);
        when(foundation.tatCaLopHoatDong(any())).thenReturn(List.of(lop1, lop2));
        when(foundation.lopTheoMa(any(), any())).thenReturn(Map.of("22T_DT1", lop1, "22T_DT2", lop2));
        when(foundation.giangVienTheoMa(any(), any())).thenReturn(Map.of("GV01", gv1));

        // Only 1 of 2 classes assigned -> must reject with 409 SHCN_CHUA_DU_100_PHAN_TRAM_LOP
        when(listRepo.cacPhanCong(55L)).thenReturn(List.of(mockLine(1L, 55L, "22T_DT1", "GV01", null)));
        mvc.perform(post("/api/shcn/khoa/phan-cong/55/xuat-pdf")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":\"" + TOKEN_V1_B64 + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHCN_CHUA_DU_100_PHAN_TRAM_LOP"));

        // Now assign 100% (2 of 2 classes) -> renders PDFBox, stores file, returns 201 CHO_KY
        when(listRepo.cacPhanCong(55L)).thenReturn(List.of(
                mockLine(1L, 55L, "22T_DT1", "GV01", null),
                mockLine(2L, 55L, "22T_DT2", "GV01", "Lớp CLC")
        ));
        when(managedFiles.store(any(), eq(99L), any(), anyString()))
                .thenReturn(new ManagedFiles.Info(701L, 99L, "PhanCong.pdf", 12345L, "a".repeat(64), true));
        when(managedFiles.describe(701L))
                .thenReturn(new ManagedFiles.Info(701L, 99L, "PhanCong.pdf", 12345L, "a".repeat(64), true));

        doAnswer(inv -> {
            setField(root, "trangThai", "CHO_KY");
            return 301L;
        }).when(signingRepo).createInitialExport(eq(55L), any(), any(), eq(10L), eq(1), anyString(), eq(701L), anyString(), any(), any(), eq(99L));

        var link = new LinkRow(1L, 55L, 301L,
                PhanCongPdfGenerator.defaultMetadata("Khoa Công nghệ thông tin", "2026-2027", "PGS.TS. Nguyễn Trưởng Khoa", FIXED_NOW),
                "current", OffsetDateTime.now(CLOCK));
        when(signingRepo.currentLink(55L)).thenAnswer(inv -> "CHO_KY".equals(root.getTrangThai()) ? Optional.of(link) : Optional.empty());
        when(signingRepo.allLinks(55L)).thenReturn(List.of(link));
        when(signingRepo.docVersion(301L)).thenReturn(new DocVersionRow(301L, 201L, UUID.randomUUID(), 1, "Danh sách", 701L, "b".repeat(64), OffsetDateTime.now(CLOCK)));
        when(signingRepo.requestByVersion(301L)).thenReturn(Optional.of(new RequestRow(
                401L, UUID.randomUUID(), 301L, 1L, "TEST", null, UUID.randomUUID(), "prepared", 99L, OffsetDateTime.now(CLOCK), null, null, null)));
        when(signingRepo.singleStep(401L)).thenReturn(new StepRow(
                501L, 401L, 99L, 1, "truong_khoa", "PGS.TS. Nguyễn Trưởng Khoa", "officer.e@dut.udn.vn",
                null, null, null, null, null, null, null, null, "waiting", OffsetDateTime.now(CLOCK)));

        mvc.perform(post("/api/shcn/khoa/phan-cong/55/xuat-pdf")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":\"" + TOKEN_V1_B64 + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trangThaiDanhSach").value("CHO_KY"))
                .andExpect(jsonPath("$.tenKhoaGocTraiPdf").value("KHOA CÔNG NGHỆ THÔNG TIN"))
                .andExpect(jsonPath("$.tepGoc.id").value("701"))
                .andExpect(jsonPath("$.yeuCauKy.canSign").value(true))
                .andExpect(jsonPath("$.yeuCauKy.canUpdatePdfContent").value(true))
                .andExpect(jsonPath("$.yeuCauKy.canReopenDraft").value(true));
    }

    @Test
    void reopenDraftFromChoKyKeepsSameIdAndPhienBanAndBlocksWhenSigningInProgress() throws Exception {
        when(foundation.quyen(any())).thenReturn(fullHeadActor("CNTT", true, true, true));
        var root = mockRoot(55L, "CNTT", 1, "CHO_KY");
        when(listRepo.khoaDanhSach(55L)).thenReturn(Optional.of(root));
        when(listRepo.cacPhanCong(55L)).thenReturn(List.of());

        var link = new LinkRow(1L, 55L, 301L, null, "current", OffsetDateTime.now(CLOCK));
        when(signingRepo.currentLink(55L)).thenReturn(Optional.of(link));

        // Case 1: Request is currently 'signing' -> must block reopen with 409 SHCN_DANG_XU_LY_KY_SO
        when(signingRepo.requestByVersion(301L)).thenReturn(Optional.of(new RequestRow(
                401L, UUID.randomUUID(), 301L, 1L, "TEST", null, UUID.randomUUID(), "signing", 99L, OffsetDateTime.now(CLOCK), null, null, null)));

        mvc.perform(post("/api/shcn/khoa/phan-cong/55/huy-de-sua")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":\"" + TOKEN_V1_B64 + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHCN_DANG_XU_LY_KY_SO"));

        // Case 2: Request is 'prepared' -> reopens to NHAP on the exact same id=55 & phienBan=1
        when(signingRepo.requestByVersion(301L)).thenReturn(Optional.of(new RequestRow(
                401L, UUID.randomUUID(), 301L, 1L, "TEST", null, UUID.randomUUID(), "prepared", 99L, OffsetDateTime.now(CLOCK), null, null, null)));
        doAnswer(inv -> {
            setField(root, "trangThai", "NHAP");
            return null;
        }).when(signingRepo).reopenDraftInPlace(eq(55L), any(), eq(401L), eq(99L), eq("Đổi GVCN lớp 22T_DT2"));

        mvc.perform(post("/api/shcn/khoa/phan-cong/55/huy-de-sua")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":\"" + TOKEN_V1_B64 + "\",\"lyDoHuy\":\"Đổi GVCN lớp 22T_DT2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("55"))
                .andExpect(jsonPath("$.phienBan").value(1))
                .andExpect(jsonPath("$.trangThai").value("NHAP"));
    }

    @Test
    void signValidatesCropBoxGeometryAndSpecialistSubmitRequiresVerifiedSignedPdf() throws Exception {
        when(foundation.quyen(any())).thenReturn(fullHeadActor("CNTT", true, true, true));
        var root = mockRoot(55L, "CNTT", 1, "CHO_KY");
        when(listRepo.khoaDanhSach(55L)).thenReturn(Optional.of(root));
        when(listRepo.cacPhanCong(55L)).thenReturn(List.of());

        byte[] realPdf = new PhanCongPdfGenerator().render(new PhanCongPdfGenerator.PdfSpec(
                "Khoa Công nghệ thông tin", "2026-2027", 1,
                OffsetDateTime.parse("2026-09-01T00:00:00+07:00"), null,
                PhanCongPdfGenerator.defaultMetadata("Khoa Công nghệ thông tin", "2026-2027", "Trưởng khoa", FIXED_NOW),
                List.of(new PhanCongPdfGenerator.PdfRow(1, "TS. Nguyễn A", "22T_DT1", null))
        ));
        String sha = CaPdfPreflight.sha256(realPdf);
        when(managedFiles.describe(701L)).thenReturn(new ManagedFiles.Info(701L, 99L, "goc.pdf", realPdf.length, sha, true));
        when(managedFiles.read(701L)).thenReturn(new ManagedFiles.Pdf(701L, "goc.pdf", sha, realPdf));

        var link = new LinkRow(1L, 55L, 301L, null, "current", OffsetDateTime.now(CLOCK));
        when(signingRepo.currentLink(55L)).thenReturn(Optional.of(link));
        when(signingRepo.allLinks(55L)).thenReturn(List.of(link));
        when(signingRepo.docVersion(301L)).thenReturn(new DocVersionRow(301L, 201L, UUID.randomUUID(), 1, "Danh sách", 701L, "d".repeat(64), OffsetDateTime.now(CLOCK)));
        when(signingRepo.requestByVersion(301L)).thenReturn(Optional.of(new RequestRow(
                401L, UUID.randomUUID(), 301L, 1L, "TEST", null, UUID.randomUUID(), "prepared", 99L, OffsetDateTime.now(CLOCK), null, null, null)));
        when(signingRepo.singleStep(401L)).thenReturn(new StepRow(
                501L, 401L, 99L, 1, "truong_khoa", "Trưởng khoa", "officer.e@dut.udn.vn",
                null, null, null, null, null, null, null, null, "waiting", OffsetDateTime.now(CLOCK)));

        // Out-of-bounds signature geometry -> 400 VALIDATION_ERROR
        mvc.perform(post("/api/shcn/khoa/phan-cong/55/ky-so")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":\"" + TOKEN_V1_B64 + "\",\"geometry\":{\"page\":1,\"x\":550,\"y\":100,\"width\":120,\"height\":60}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Valid signature geometry -> 202 Accepted & triggers worker
        mvc.perform(post("/api/shcn/khoa/phan-cong/55/ky-so")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":\"" + TOKEN_V1_B64 + "\",\"geometry\":{\"page\":1,\"x\":330,\"y\":90,\"width\":180,\"height\":65}}"))
                .andExpect(status().isAccepted());
        assertThat(triggeredRequest.get()).isEqualTo(401L);

        // Cannot submit to specialist while still CHO_KY -> 409 SHCN_CHUA_KY_SO
        mvc.perform(post("/api/shcn/khoa/phan-cong/55/gui-chuyen-vien")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":\"" + TOKEN_V1_B64 + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHCN_CHUA_KY_SO"));

        // Once DA_KY + verified output ready -> 200 DA_GUI_CHUYEN_VIEN
        setField(root, "trangThai", "DA_KY");
        when(signingRepo.requestByVersion(301L)).thenReturn(Optional.of(new RequestRow(
                401L, UUID.randomUUID(), 301L, 1L, "TEST", UUID.randomUUID(), UUID.randomUUID(), "completed", 99L, OffsetDateTime.now(CLOCK), OffsetDateTime.now(CLOCK), OffsetDateTime.now(CLOCK), null)));
        when(signingRepo.singleStep(401L)).thenReturn(new StepRow(
                501L, 401L, 99L, 1, "truong_khoa", "Trưởng khoa", "officer.e@dut.udn.vn",
                UUID.randomUUID(), UUID.randomUUID(), "SER123", 1, null, null, null, null, "completed", OffsetDateTime.now(CLOCK)));
        when(signingRepo.verifiedOutput(401L)).thenReturn(Optional.of(new OutputRow(702L, 801L, "c".repeat(64), "2026-11-26T02:05:00Z")));
        when(managedFiles.describe(702L)).thenReturn(new ManagedFiles.Info(702L, 99L, "da-ky.pdf", realPdf.length + 500, "c".repeat(64), true));
        doAnswer(inv -> {
            setField(root, "trangThai", "DA_GUI_CHUYEN_VIEN");
            return null;
        }).when(signingRepo).markSubmittedToSpecialist(eq(55L), any());

        mvc.perform(post("/api/shcn/khoa/phan-cong/55/gui-chuyen-vien")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":\"" + TOKEN_V1_B64 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trangThai").value("DA_GUI_CHUYEN_VIEN"));
    }

    @Test
    void separateSignAndSubmitPermissionsAndFileReadPolicyAreEnforced() throws Exception {
        var root = mockRoot(55L, "CNTT", 1, "DA_KY");
        when(listRepo.khoaDanhSach(55L)).thenReturn(Optional.of(root));

        // Actor with prepare + sign, but WITHOUT submit -> 403 FORBIDDEN on /gui-chuyen-vien
        when(foundation.quyen(any())).thenReturn(fullHeadActor("CNTT", true, true, false));
        mvc.perform(post("/api/shcn/khoa/phan-cong/55/gui-chuyen-vien")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":\"" + TOKEN_V1_B64 + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // ShcnFileReadPolicy check: specialist denied while DA_KY, allowed once DA_GUI_CHUYEN_VIEN
        var policy = new ShcnFileReadPolicy(signingRepo, foundation);
        var specialistId = new FoundationAccess.Identity(200L, null, principal);
        when(foundation.quyen(specialistId)).thenReturn(new Actor(200L, "spec", false, false, false, false, List.of()));
        when(foundation.laChuyenVienDocNguon(specialistId)).thenReturn(true);

        when(signingRepo.findFileOwners(702L)).thenReturn(List.of(new FileOwnerRow(55L, "CNTT", "DA_KY", true)));
        assertThat(policy.canRead(702L, specialistId)).isFalse();

        when(signingRepo.findFileOwners(702L)).thenReturn(List.of(new FileOwnerRow(55L, "CNTT", "DA_GUI_CHUYEN_VIEN", true)));
        assertThat(policy.canRead(702L, specialistId)).isTrue();
    }

    @Test
    void workerHandlesUncertainIntentRevokedAuthAndCertificateGuard() throws Exception {
        var ca = mock(PhanCongSigningGateway.class);
        ObjectProvider<ManagedFiles> filesProvider = new ObjectProvider<>() {
            @Override public ManagedFiles getObject(Object... args) { return managedFiles; }
            @Override public ManagedFiles getIfAvailable() { return managedFiles; }
            @Override public ManagedFiles getIfUnique() { return managedFiles; }
            @Override public ManagedFiles getObject() { return managedFiles; }
        };
        var worker = new PhanCongSigningWorker(
                txManager, auth, identities, foundation, listRepo, signingRepo, audit, ca, filesProvider, CLOCK);

        // 1. Crashed sign_intent without local receipt -> marks SIGNING_RESULT_UNKNOWN (uncertain=true), does not re-sign
        UUID oldLease = UUID.randomUUID();
        WorkRow signIntent = new WorkRow(
                901L, 401L, 501L, UUID.randomUUID(), 701L, "a".repeat(64),
                "sign_intent", true, oldLease, null, principal);
        when(signingRepo.latestWork(401L)).thenReturn(Optional.of(signIntent));
        when(signingRepo.findReceipt(901L)).thenReturn(Optional.empty());

        assertThat(worker.runRequest(401L)).isFalse();
        verify(signingRepo).recordError(signIntent, oldLease, "unknown", "SIGNING_RESULT_UNKNOWN", true);
        verifyNoInteractions(ca);

        // 2. Queued work where actor lost shcn.phanCongKhoa.sign -> marks SIGNING_AUTHORIZATION_REVOKED
        WorkRow queued = new WorkRow(
                902L, 401L, 501L, UUID.randomUUID(), 701L, "a".repeat(64),
                "queued", false, null, null, principal);
        var root = mockRoot(55L, "CNTT", 1, "CHO_KY");
        var reqRow = new RequestRow(
                401L, UUID.randomUUID(), 301L, 1L, "TEST", null, UUID.randomUUID(),
                "signing", 99L, OffsetDateTime.now(CLOCK), OffsetDateTime.now(CLOCK), null, null);
        var stepRow = new StepRow(
                501L, 401L, 99L, 1, "truong_khoa", "Trưởng khoa", "officer.e@dut.udn.vn",
                null, null, null, 1, BigDecimal.valueOf(330), BigDecimal.valueOf(90),
                BigDecimal.valueOf(180), BigDecimal.valueOf(65), "waiting", OffsetDateTime.now(CLOCK));
        var docRow = new DocVersionRow(
                301L, 201L, UUID.randomUUID(), 1, "Danh sách", 701L, "d".repeat(64), OffsetDateTime.now(CLOCK));

        when(signingRepo.latestWork(401L)).thenReturn(Optional.of(queued));
        when(signingRepo.findReceipt(902L)).thenReturn(Optional.empty());
        when(signingRepo.danhSachIdByRequest(401L)).thenReturn(Optional.of(55L));
        when(listRepo.khoaDanhSach(55L)).thenReturn(Optional.of(root));
        when(signingRepo.lockRequest(401L)).thenReturn(reqRow);
        when(signingRepo.singleStep(401L)).thenReturn(stepRow);
        when(signingRepo.docVersion(301L)).thenReturn(docRow);
        when(foundation.quyen(any())).thenReturn(fullHeadActor("CNTT", true, false, true));

        assertThat(worker.runRequest(401L)).isTrue();
        verify(signingRepo).recordError(queued, null, "failed", "SIGNING_AUTHORIZATION_REVOKED", false);
        verifyNoInteractions(ca);

        // 3. Authorized claim with 0 active CA certificates -> fails cleanly with CA_CERTIFICATE_SELECTION_REQUIRED
        when(foundation.quyen(any())).thenReturn(fullHeadActor("CNTT", true, true, true));
        when(signingRepo.currentLink(55L)).thenReturn(Optional.of(new LinkRow(1L, 55L, 301L, null, "current", OffsetDateTime.now(CLOCK))));
        byte[] pdfBytes = "dummy-pdf".getBytes(StandardCharsets.UTF_8);
        when(managedFiles.describe(701L)).thenReturn(new ManagedFiles.Info(701L, 99L, "goc.pdf", pdfBytes.length, "a".repeat(64), true));
        when(managedFiles.read(701L)).thenReturn(new ManagedFiles.Pdf(701L, "goc.pdf", "a".repeat(64), pdfBytes));
        when(signingRepo.claim(eq(queued), any())).thenReturn(true);
        when(ca.certificates("officer.e@dut.udn.vn")).thenReturn(new Certificates("officer.e@dut.udn.vn", List.of()));

        assertThat(worker.runRequest(401L)).isTrue();
        verify(signingRepo).recordError(eq(queued), any(), eq("failed"), eq("CA_CERTIFICATE_SELECTION_REQUIRED"), eq(false));
    }
}
