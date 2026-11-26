package pbl6.ctsv.shcn.phancongkhoa.signing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongConsumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shared.ca.CaFailure;
import pbl6.ctsv.shared.ca.CaPdfPreflight;
import pbl6.ctsv.shared.files.ManagedFiles;
import pbl6.ctsv.shcn.foundation.PhanCongKhoaFoundationPort;
import pbl6.ctsv.shcn.foundation.PhanCongKhoaFoundationPort.Actor;
import pbl6.ctsv.shcn.foundation.PhanCongKhoaFoundationPort.Khoa;
import pbl6.ctsv.shcn.phancongkhoa.dto.PhanCongKhoaDtos.*;
import pbl6.ctsv.shcn.phancongkhoa.entity.DanhSachPhanCongKhoa;
import pbl6.ctsv.shcn.phancongkhoa.entity.PhanCongGvcnKhoa;
import pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure;
import pbl6.ctsv.shcn.phancongkhoa.pdf.PhanCongPdfGenerator;
import pbl6.ctsv.shcn.phancongkhoa.pdf.PhanCongPdfGenerator.PdfRow;
import pbl6.ctsv.shcn.phancongkhoa.pdf.PhanCongPdfGenerator.PdfSpec;
import pbl6.ctsv.shcn.phancongkhoa.repository.PhanCongKhoaAudit;
import pbl6.ctsv.shcn.phancongkhoa.repository.PhanCongKhoaRepository;
import pbl6.ctsv.shcn.phancongkhoa.service.PhanCongKhoaService;
import pbl6.ctsv.shcn.phancongkhoa.signing.PhanCongSigningRepository.*;
import pbl6.ctsv.shcn.phancongkhoa.validation.PhanCongKhoaRequests.*;
import static pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure.*;

/**
 * Orchestrates SHCN TASK-TK-02 workflows:
 * - SHCN-TK-010: Export PDF & lock NHAP -> CHO_KY (requires 100% active faculty classes)
 * - SHCN-TK-011: Read signing dossier & PDF metadata/history
 * - SHCN-TK-012: Confirm signature box & enqueue CA DUT signing
 * - SHCN-TK-013: Reconcile/recover uncertain or post-receipt signing attempts
 * - SHCN-TK-014: Submit signed assignment list DA_KY -> DA_GUI_CHUYEN_VIEN
 * - SHCN-TK-015: Cancel CHO_KY & reopen NHAP in-place (preserving id & phienBan)
 * - SHCN-TK-016: Edit administrative PDF text & re-render PDFBox while CHO_KY (Option B)
 */
public final class PhanCongSigningService {
    public record ExportResult(HoSoKyKhoaDTO dossier, boolean created) {}

    private record PreparedExport(
            long danhSachId,
            long donViId,
            String maKhoa,
            String tenKhoa,
            String namHoc,
            int phienBan,
            String existingDossierCode,
            String title,
            String fileName,
            String datasetSha256,
            NoiDungHanhChinhDTO metadata,
            PhanCongSigningOptions.ResolvedPlan signingPlan,
            PdfSpec pdfSpec,
            long actorUserId,
            HoSoKyKhoaDTO alreadyExported
    ) {}

    private record PreparedContentUpdate(
            long danhSachId,
            long oldRequestId,
            long vanBanId,
            int phienBan,
            String title,
            String fileName,
            String datasetSha256,
            NoiDungHanhChinhDTO metadata,
            PhanCongSigningOptions.ResolvedPlan signingPlan,
            PdfSpec pdfSpec,
            long actorUserId
    ) {}

    private record PreSignCheck(
            long danhSachId,
            long requestId,
            long stepId,
            long tepGocId,
            String tepGocSha256,
            String signerEmail,
            String signerDisplayName,
            long actorUserId
    ) {}

    private final TransactionTemplate tx;
    private final FoundationAccess identities;
    private final PhanCongKhoaFoundationPort foundation;
    private final PhanCongKhoaRepository listRepository;
    private final PhanCongSigningRepository signingRepository;
    private final PhanCongKhoaService phanCongService;
    private final PhanCongKhoaAudit audit;
    private final PhanCongPdfGenerator pdfGenerator;
    private final PhanCongSigningOptions signingOptions;
    private final ObjectProvider<ManagedFiles> filesProvider;
    private final LongConsumer workerTrigger;
    private final Clock clock;

    public PhanCongSigningService(
            PlatformTransactionManager manager,
            FoundationAccess identities,
            PhanCongKhoaFoundationPort foundation,
            PhanCongKhoaRepository listRepository,
            PhanCongSigningRepository signingRepository,
            PhanCongKhoaService phanCongService,
            PhanCongKhoaAudit audit,
            PhanCongPdfGenerator pdfGenerator,
            PhanCongSigningOptions signingOptions,
            ObjectProvider<ManagedFiles> filesProvider,
            PhanCongSigningWorker worker,
            Clock clock
    ) {
        this(manager, identities, foundation, listRepository, signingRepository, phanCongService, audit,
                pdfGenerator, signingOptions, filesProvider,
                requestId -> CompletableFuture.runAsync(() -> {
                    try {
                        worker.runRequest(requestId);
                    } catch (RuntimeException ignored) {
                    }
                }),
                clock);
    }

    public PhanCongSigningService(
            PlatformTransactionManager manager,
            FoundationAccess identities,
            PhanCongKhoaFoundationPort foundation,
            PhanCongKhoaRepository listRepository,
            PhanCongSigningRepository signingRepository,
            PhanCongKhoaService phanCongService,
            PhanCongKhoaAudit audit,
            PhanCongPdfGenerator pdfGenerator,
            PhanCongSigningOptions signingOptions,
            ObjectProvider<ManagedFiles> filesProvider,
            LongConsumer workerTrigger,
            Clock clock
    ) {
        this.tx = new TransactionTemplate(manager);
        this.identities = identities;
        this.foundation = foundation;
        this.listRepository = listRepository;
        this.signingRepository = signingRepository;
        this.phanCongService = phanCongService;
        this.audit = audit;
        this.pdfGenerator = pdfGenerator;
        this.signingOptions = signingOptions;
        this.filesProvider = filesProvider;
        this.workerTrigger = workerTrigger;
        this.clock = clock;
    }

    private ManagedFiles files() {
        ManagedFiles f = filesProvider.getIfAvailable();
        if (f == null) throw PhanCongKhoaFailure.unavailable();
        return f;
    }

    public HoSoKyKhoaDTO hoSoKy(AuthAccess.Principal principal, long danhSachId) {
        return Objects.requireNonNull(tx.execute(s -> {
            var identity = identities.identify(principal, true);
            var actor = foundation.quyen(identity);
            var root = listRepository.khoaDanhSach(danhSachId).orElseThrow(PhanCongKhoaFailure::notFound);
            if ("DA_HUY".equals(root.getTrangThai())) throw notFound();

            Khoa faculty = findScopedFaculty(actor, root.getMaKhoa());
            boolean specialistAllowed = faculty == null
                    && Set.of("DA_GUI_CHUYEN_VIEN", "DA_THAY_THE").contains(root.getTrangThai())
                    && foundation.laChuyenVienDocNguon(identity);
            if (faculty == null && !specialistAllowed) {
                if (!actor.doc() && !foundation.laChuyenVienDocNguon(identity)) {
                    throw new PhanCongKhoaFailure(403, "FORBIDDEN");
                }
                throw notFound();
            }
            String tenKhoa = faculty != null ? faculty.ten() : root.getMaKhoa();
            return composeDossier(root, faculty, tenKhoa, actor);
        }));
    }

    public ExportResult xuatPdf(AuthAccess.Principal principal, long danhSachId, XuatPdf input) {
        PreparedExport prep = Objects.requireNonNull(tx.execute(s -> {
            var actor = requireCapability(principal, Actor::kySo);
            var root = lockRoot(danhSachId);
            var faculty = requireFaculty(actor, root.getMaKhoa(), Khoa::kySo);
            if (!Arrays.equals(input.expectedVersion(), root.getPhienBanGhi())) {
                throw conflict("VERSION_CONFLICT");
            }
            if ("CHO_KY".equals(root.getTrangThai()) && signingRepository.currentLink(danhSachId).isPresent()) {
                return new PreparedExport(danhSachId, faculty.id(), faculty.ma(), faculty.ten(), root.getNamHoc(),
                        root.getPhienBan(), root.getMaHoSoTaiLieu(), "", "", "", null, null, null, actor.userId(),
                        composeDossier(root, faculty, faculty.ten(), actor));
            }
            if (!"NHAP".equals(root.getTrangThai())) {
                throw conflict("SHCN_KHONG_CON_LA_NHAP");
            }
            var verified = verifyCompleteFacultyCoverage(faculty, danhSachId);
            var profile = foundation.nguoiDung(actor.userId()).orElse(null);
            var plan = signingOptions.resolve(profile);
            NoiDungHanhChinhDTO meta = input.noiDungHanhChinh() != null
                    ? input.noiDungHanhChinh()
                    : PhanCongPdfGenerator.defaultMetadata(
                            faculty.ten(),
                            root.getNamHoc(),
                            profile == null ? "" : profile.hoTen(),
                            clock.instant());
            String hash = datasetHash(root, verified);
            String title = documentTitle(faculty.ten(), root.getNamHoc(), root.getPhienBan());
            String fileName = safeFileName(faculty.ma(), root.getNamHoc(), root.getPhienBan());
            var spec = new PdfSpec(faculty.ten(), root.getNamHoc(), root.getPhienBan(),
                    root.getHieuLucTu(), root.getHieuLucDen(), meta, verified);
            return new PreparedExport(danhSachId, faculty.id(), faculty.ma(), faculty.ten(), root.getNamHoc(),
                    root.getPhienBan(), root.getMaHoSoTaiLieu(), title, fileName, hash, meta, plan, spec, actor.userId(), null);
        }));

        if (prep.alreadyExported() != null) {
            return new ExportResult(prep.alreadyExported(), false);
        }

        // Render PDFBox and store in Cloudinary strictly outside SQL transactions.
        byte[] pdfBytes = pdfGenerator.render(prep.pdfSpec());
        var stored = files().store(UUID.randomUUID(), prep.actorUserId(), pdfBytes, prep.fileName());

        HoSoKyKhoaDTO created = Objects.requireNonNull(tx.execute(s -> {
            var actor = requireCapability(principal, Actor::kySo);
            var root = lockRoot(danhSachId);
            var faculty = requireFaculty(actor, root.getMaKhoa(), Khoa::kySo);
            if (!Arrays.equals(input.expectedVersion(), root.getPhienBanGhi())) {
                throw conflict("VERSION_CONFLICT");
            }
            if (!"NHAP".equals(root.getTrangThai())) {
                throw conflict("SHCN_KHONG_CON_LA_NHAP");
            }
            var verifiedAgain = verifyCompleteFacultyCoverage(faculty, danhSachId);
            if (!prep.datasetSha256().equals(datasetHash(root, verifiedAgain))) {
                throw conflict("VERSION_CONFLICT");
            }
            signingRepository.createInitialExport(
                    danhSachId, input.expectedVersion(), prep.existingDossierCode(),
                    faculty.id(), root.getPhienBan(), prep.title(), stored.id(),
                    prep.datasetSha256(), prep.metadata(), prep.signingPlan(), actor.userId());
            audit.ghi(actor.userId(), "SHCN_KHOA_EXPORT_PDF", danhSachId, "Chốt nháp và xuất PDF chờ Trưởng khoa ký số");
            var refreshed = lockRoot(danhSachId);
            return composeDossier(refreshed, faculty, faculty.ten(), actor);
        }));
        return new ExportResult(created, true);
    }

    public HoSoKyKhoaDTO capNhatNoiDungPdf(AuthAccess.Principal principal, long danhSachId, CapNhatNoiDungPdf input) {
        PreparedContentUpdate prep = Objects.requireNonNull(tx.execute(s -> {
            var actor = requireCapability(principal, Actor::kySo);
            var root = lockRoot(danhSachId);
            var faculty = requireFaculty(actor, root.getMaKhoa(), Khoa::kySo);
            if (!Arrays.equals(input.expectedVersion(), root.getPhienBanGhi())) {
                throw conflict("VERSION_CONFLICT");
            }
            if (!"CHO_KY".equals(root.getTrangThai())) {
                throw conflict("SHCN_KHONG_O_TRANG_THAI_CHO_KY");
            }
            var link = signingRepository.currentLink(danhSachId)
                    .orElseThrow(() -> conflict("SHCN_CHUA_CO_HO_SO_KY"));
            var doc = signingRepository.docVersion(link.phienBanVanBanId());
            var req = signingRepository.requestByVersion(doc.id())
                    .orElseThrow(() -> conflict("SHCN_CHUA_CO_HO_SO_KY"));
            signingRepository.lockRequest(req.id());
            var step = signingRepository.singleStep(req.id());
            if (!"prepared".equals(req.state()) || !"waiting".equals(step.state())) {
                throw conflict("SHCN_KHONG_THE_SUA_PDF");
            }
            var work = signingRepository.latestWork(req.id()).orElse(null);
            if (work != null && !"awaiting_consent".equals(work.phase())) {
                throw conflict("SHCN_KHONG_THE_SUA_PDF");
            }
            var pdfRows = loadLockedPdfRows(faculty, danhSachId);
            var profile = foundation.nguoiDung(actor.userId()).orElse(null);
            var plan = signingOptions.resolve(profile);
            String title = documentTitle(faculty.ten(), root.getNamHoc(), root.getPhienBan());
            String fileName = safeFileName(faculty.ma(), root.getNamHoc(), root.getPhienBan());
            var spec = new PdfSpec(faculty.ten(), root.getNamHoc(), root.getPhienBan(),
                    root.getHieuLucTu(), root.getHieuLucDen(), input.noiDungHanhChinh(), pdfRows);
            return new PreparedContentUpdate(danhSachId, req.id(), doc.vanBanId(), root.getPhienBan(),
                    title, fileName, datasetHash(root, pdfRows), input.noiDungHanhChinh(), plan, spec, actor.userId());
        }));

        byte[] pdfBytes = pdfGenerator.render(prep.pdfSpec());
        var stored = files().store(UUID.randomUUID(), prep.actorUserId(), pdfBytes, prep.fileName());

        return Objects.requireNonNull(tx.execute(s -> {
            var actor = requireCapability(principal, Actor::kySo);
            var root = lockRoot(danhSachId);
            var faculty = requireFaculty(actor, root.getMaKhoa(), Khoa::kySo);
            if (!Arrays.equals(input.expectedVersion(), root.getPhienBanGhi())) {
                throw conflict("VERSION_CONFLICT");
            }
            if (!"CHO_KY".equals(root.getTrangThai())) {
                throw conflict("SHCN_KHONG_O_TRANG_THAI_CHO_KY");
            }
            signingRepository.lockRequest(prep.oldRequestId());
            signingRepository.replaceUnsignedPdf(
                    danhSachId, input.expectedVersion(), prep.oldRequestId(), prep.vanBanId(),
                    prep.phienBan(), prep.title(), stored.id(), prep.datasetSha256(),
                    prep.metadata(), prep.signingPlan(), actor.userId());
            audit.ghi(actor.userId(), "SHCN_KHOA_UPDATE_PDF_CONTENT", danhSachId,
                    "Cập nhật nội dung hành chính và sinh lại PDF trước khi ký");
            var refreshed = lockRoot(danhSachId);
            return composeDossier(refreshed, faculty, faculty.ten(), actor);
        }));
    }

    public HoSoKyKhoaDTO kySo(AuthAccess.Principal principal, long danhSachId, KySo input) {
        PreSignCheck pre = Objects.requireNonNull(tx.execute(s -> {
            var actor = requireCapability(principal, Actor::kySo);
            var root = lockRoot(danhSachId);
            var faculty = requireFaculty(actor, root.getMaKhoa(), Khoa::kySo);
            if (!Arrays.equals(input.expectedVersion(), root.getPhienBanGhi())) {
                throw conflict("VERSION_CONFLICT");
            }
            if (!"CHO_KY".equals(root.getTrangThai())) {
                throw conflict("SHCN_KHONG_O_TRANG_THAI_CHO_KY");
            }
            var link = signingRepository.currentLink(danhSachId)
                    .orElseThrow(() -> conflict("SHCN_CHUA_CO_HO_SO_KY"));
            var doc = signingRepository.docVersion(link.phienBanVanBanId());
            var req = signingRepository.requestByVersion(doc.id())
                    .orElseThrow(() -> conflict("SHCN_CHUA_CO_HO_SO_KY"));
            signingRepository.lockRequest(req.id());
            var step = signingRepository.singleStep(req.id());
            if (!"prepared".equals(req.state()) || !"waiting".equals(step.state())) {
                throw conflict("SIGNING_NOT_READY");
            }
            var fileInfo = files().describe(doc.tepGocId());
            if (!fileInfo.ready()) throw conflict("FILE_NOT_READY");
            var profile = foundation.nguoiDung(actor.userId()).orElse(null);
            var plan = signingOptions.resolve(profile);
            return new PreSignCheck(danhSachId, req.id(), step.id(), doc.tepGocId(),
                    fileInfo.sha256(), plan.signerEmail(), plan.signerDisplayName(), actor.userId());
        }));

        // Download & inspect CropBox geometry strictly outside SQL transactions.
        var pdf = files().read(pre.tepGocId());
        try {
            CaPdfPreflight.inspect(pdf.bytes(), input.geometry());
        } catch (CaFailure invalidBox) {
            throw invalid("geometry");
        }

        HoSoKyKhoaDTO enqueued = Objects.requireNonNull(tx.execute(s -> {
            var actor = requireCapability(principal, Actor::kySo);
            var root = lockRoot(danhSachId);
            var faculty = requireFaculty(actor, root.getMaKhoa(), Khoa::kySo);
            if (!Arrays.equals(input.expectedVersion(), root.getPhienBanGhi())) {
                throw conflict("VERSION_CONFLICT");
            }
            if (!"CHO_KY".equals(root.getTrangThai())) {
                throw conflict("SHCN_KHONG_O_TRANG_THAI_CHO_KY");
            }
            var link = signingRepository.currentLink(danhSachId)
                    .orElseThrow(() -> conflict("SIGNING_NOT_READY"));
            var doc = signingRepository.docVersion(link.phienBanVanBanId());
            if (doc.tepGocId() != pre.tepGocId() || !files().describe(pre.tepGocId()).sha256().equals(pre.tepGocSha256())) {
                throw conflict("VERSION_CONFLICT");
            }
            signingRepository.lockRequest(pre.requestId());
            signingRepository.enqueueSigningAttempt(
                    danhSachId, input.expectedVersion(), pre.requestId(), pre.stepId(),
                    actor.userId(), pre.signerEmail(), pre.signerDisplayName(),
                    input.geometry(), principal, pre.tepGocId(), pre.tepGocSha256());
            audit.ghi(actor.userId(), "SHCN_KHOA_SIGN_CONFIRM", danhSachId,
                    "Trưởng khoa xác nhận vùng ký trang " + input.geometry().page() + " và gửi ký số");
            var refreshed = lockRoot(danhSachId);
            return composeDossier(refreshed, faculty, faculty.ten(), actor);
        }));

        workerTrigger.accept(pre.requestId());
        return enqueued;
    }

    public HoSoKyKhoaDTO doiSoat(AuthAccess.Principal principal, long danhSachId, DoiSoatKy input) {
        record Reconciled(HoSoKyKhoaDTO dossier, Long triggerRequestId) {}
        Reconciled result = Objects.requireNonNull(tx.execute(s -> {
            var actor = requireCapability(principal, Actor::kySo);
            var root = lockRoot(danhSachId);
            var faculty = requireFaculty(actor, root.getMaKhoa(), Khoa::kySo);
            if (!Arrays.equals(input.expectedVersion(), root.getPhienBanGhi())) {
                throw conflict("VERSION_CONFLICT");
            }
            if (!"CHO_KY".equals(root.getTrangThai())) {
                throw conflict("SHCN_KHONG_O_TRANG_THAI_CHO_KY");
            }
            var link = signingRepository.currentLink(danhSachId)
                    .orElseThrow(() -> conflict("RECONCILIATION_UNAVAILABLE"));
            var req = signingRepository.requestByVersion(link.phienBanVanBanId())
                    .orElseThrow(() -> conflict("RECONCILIATION_UNAVAILABLE"));
            signingRepository.lockRequest(req.id());
            var work = signingRepository.latestWork(req.id())
                    .orElseThrow(() -> conflict("RECONCILIATION_UNAVAILABLE"));
            if (!signingRepository.canReconcile(work)) {
                throw conflict("RECONCILIATION_UNAVAILABLE");
            }
            Long resumeRequestId = null;
            if (signingRepository.safePreDispatch(work)) {
                signingRepository.releaseForNewConsent(danhSachId, input.expectedVersion(), work);
            } else {
                signingRepository.resumePostReceipt(danhSachId, input.expectedVersion(), work);
                resumeRequestId = req.id();
            }
            audit.ghi(actor.userId(), "SHCN_KHOA_SIGN_RECONCILE", danhSachId, input.reason());
            var refreshed = lockRoot(danhSachId);
            return new Reconciled(composeDossier(refreshed, faculty, faculty.ten(), actor), resumeRequestId);
        }));

        if (result.triggerRequestId() != null) {
            workerTrigger.accept(result.triggerRequestId());
        }
        return result.dossier();
    }

    public DanhSachDTO huyDeSua(AuthAccess.Principal principal, long danhSachId, HuyDeSua input) {
        return Objects.requireNonNull(tx.execute(s -> {
            var actor = requireCapability(principal, Actor::lap);
            var root = lockRoot(danhSachId);
            requireFaculty(actor, root.getMaKhoa(), Khoa::lap);
            if (!Arrays.equals(input.expectedVersion(), root.getPhienBanGhi())) {
                throw conflict("VERSION_CONFLICT");
            }
            if (!"CHO_KY".equals(root.getTrangThai())) {
                throw conflict("SHCN_KHONG_O_TRANG_THAI_CHO_KY");
            }
            Long cancelRequestId = null;
            var link = signingRepository.currentLink(danhSachId).orElse(null);
            if (link != null) {
                var req = signingRepository.requestByVersion(link.phienBanVanBanId()).orElse(null);
                if (req != null) {
                    signingRepository.lockRequest(req.id());
                    cancelRequestId = req.id();
                    if (!"prepared".equals(req.state())) {
                        var work = signingRepository.latestWork(req.id()).orElse(null);
                        if (work == null || !signingRepository.safePreDispatch(work)) {
                            throw conflict("SHCN_DANG_XU_LY_KY_SO");
                        }
                    }
                }
            }
            signingRepository.reopenDraftInPlace(danhSachId, input.expectedVersion(), cancelRequestId, actor.userId(), input.lyDoHuy());
            audit.ghi(actor.userId(), "SHCN_KHOA_REOPEN_DRAFT", danhSachId,
                    input.lyDoHuy() == null ? "Hủy chờ ký để chỉnh sửa trên bản nháp hiện tại" : input.lyDoHuy());
            return phanCongService.chiTiet(actor, danhSachId);
        }));
    }

    public DanhSachDTO guiChuyenVien(AuthAccess.Principal principal, long danhSachId, byte[] expectedVersion) {
        return Objects.requireNonNull(tx.execute(s -> {
            var actor = requireCapability(principal, Actor::guiChuyenVien);
            var root = lockRoot(danhSachId);
            requireFaculty(actor, root.getMaKhoa(), Khoa::guiChuyenVien);
            if (!Arrays.equals(expectedVersion, root.getPhienBanGhi())) {
                throw conflict("VERSION_CONFLICT");
            }
            if (!"DA_KY".equals(root.getTrangThai())) {
                throw conflict("SHCN_CHUA_KY_SO");
            }
            var link = signingRepository.currentLink(danhSachId)
                    .orElseThrow(() -> conflict("SHCN_TEP_KY_CHUA_SAN_SANG"));
            var req = signingRepository.requestByVersion(link.phienBanVanBanId())
                    .orElseThrow(() -> conflict("SHCN_TEP_KY_CHUA_SAN_SANG"));
            var step = signingRepository.singleStep(req.id());
            var output = signingRepository.verifiedOutput(req.id())
                    .orElseThrow(() -> conflict("SHCN_TEP_KY_CHUA_SAN_SANG"));
            if (!"completed".equals(req.state()) || !"completed".equals(step.state()) || !files().describe(output.fileId()).ready()) {
                throw conflict("SHCN_TEP_KY_CHUA_SAN_SANG");
            }
            signingRepository.markSubmittedToSpecialist(danhSachId, expectedVersion);
            audit.ghi(actor.userId(), "SHCN_KHOA_SUBMIT_SPECIALIST", danhSachId,
                    "Gửi danh sách phân công GVCN đã ký số cho Chuyên viên CTSV");
            return phanCongService.chiTiet(actor, danhSachId);
        }));
    }

    private Actor requireCapability(AuthAccess.Principal principal, java.util.function.Predicate<Actor> check) {
        var actor = foundation.quyen(identities.identify(principal, true));
        if (!check.test(actor)) throw new PhanCongKhoaFailure(403, "FORBIDDEN");
        return actor;
    }

    private DanhSachPhanCongKhoa lockRoot(long danhSachId) {
        var root = listRepository.khoaDanhSach(danhSachId).orElseThrow(PhanCongKhoaFailure::notFound);
        if ("DA_HUY".equals(root.getTrangThai())) throw notFound();
        return root;
    }

    private Khoa findScopedFaculty(Actor actor, String maKhoa) {
        var matches = actor.khoa().stream().filter(k -> k.ma().equals(maKhoa) && k.doc()).toList();
        if (matches.isEmpty()) return null;
        if (matches.size() != 1) throw unavailable();
        return matches.getFirst();
    }

    private Khoa requireFaculty(Actor actor, String maKhoa, java.util.function.Predicate<Khoa> check) {
        var allForCode = actor.khoa().stream().filter(k -> k.ma().equals(maKhoa)).toList();
        if (allForCode.isEmpty()) throw notFound();
        if (allForCode.size() != 1) throw unavailable();
        var faculty = allForCode.getFirst();
        if (!check.test(faculty)) throw new PhanCongKhoaFailure(403, "FORBIDDEN");
        if (!faculty.dangHoatDong() || !foundation.maLuuDuoc(faculty.ma())) {
            throw conflict("SHCN_THAM_CHIEU_KHONG_CON_HOP_LE");
        }
        return faculty;
    }

    private List<PdfRow> verifyCompleteFacultyCoverage(Khoa faculty, long danhSachId) {
        var activeClasses = foundation.tatCaLopHoatDong(faculty).stream().filter(LopDTO::coTheChon).toList();
        if (activeClasses.isEmpty()) {
            throw conflict("SHCN_DANH_SACH_LOP_KHOA_TRONG");
        }
        var rows = listRepository.cacPhanCong(danhSachId);
        if (rows.isEmpty()) {
            throw conflict("SHCN_CHUA_DU_100_PHAN_TRAM_LOP");
        }
        var classByCode = foundation.lopTheoMa(faculty, rows.stream().map(PhanCongGvcnKhoa::getMaLop).toList());
        var teacherByCode = foundation.giangVienTheoMa(faculty, rows.stream().map(PhanCongGvcnKhoa::getMaGiaoVien).toList());
        var assignedCodes = new HashSet<String>();
        var pdfRows = new ArrayList<PdfRow>(rows.size());
        int stt = 1;
        for (var row : rows) {
            var cls = classByCode.get(row.getMaLop());
            var teacher = teacherByCode.get(row.getMaGiaoVien());
            if (cls == null || teacher == null || !cls.coTheChon() || !teacher.coTheChon()) {
                throw conflict("SHCN_THAM_CHIEU_KHONG_CON_HOP_LE");
            }
            assignedCodes.add(row.getMaLop());
            pdfRows.add(new PdfRow(stt++, teacher.hoTen(), row.getMaLop(), row.getGhiChu()));
        }
        for (var requiredClass : activeClasses) {
            if (!assignedCodes.contains(requiredClass.maLop())) {
                throw conflict("SHCN_CHUA_DU_100_PHAN_TRAM_LOP");
            }
        }
        return pdfRows;
    }

    private List<PdfRow> loadLockedPdfRows(Khoa faculty, long danhSachId) {
        var rows = listRepository.cacPhanCong(danhSachId);
        var teacherByCode = foundation.giangVienTheoMa(faculty, rows.stream().map(PhanCongGvcnKhoa::getMaGiaoVien).toList());
        var pdfRows = new ArrayList<PdfRow>(rows.size());
        int stt = 1;
        for (var row : rows) {
            var teacher = teacherByCode.get(row.getMaGiaoVien());
            String hoTen = teacher != null && teacher.hoTen() != null ? teacher.hoTen() : row.getMaGiaoVien();
            pdfRows.add(new PdfRow(stt++, hoTen, row.getMaLop(), row.getGhiChu()));
        }
        return pdfRows;
    }

    private HoSoKyKhoaDTO composeDossier(DanhSachPhanCongKhoa root, Khoa faculty, String tenKhoa, Actor actor) {
        String versionToken = Base64.getEncoder().encodeToString(root.getPhienBanGhi());
        String submittedAt = root.getNgayGuiChuyenVien() == null ? null : root.getNgayGuiChuyenVien().toInstant().toString();
        String tenKhoaPdf = PhanCongPdfGenerator.formatFacultyHeading(tenKhoa);

        var currentLinkOpt = signingRepository.currentLink(root.getId());
        var allLinks = signingRepository.allLinks(root.getId());
        var pdfVersions = new ArrayList<PhienBanPdfKhoaDTO>();
        for (var l : allLinks) {
            var dv = signingRepository.docVersion(l.phienBanVanBanId());
            var reqOpt = signingRepository.requestByVersion(dv.id());
            TepTaiLieuDTO goc = describeFileSafe(dv.tepGocId());
            TepTaiLieuDTO daKy = reqOpt.flatMap(q -> signingRepository.verifiedOutput(q.id()))
                    .map(o -> describeFileSafe(o.fileId())).orElse(null);
            pdfVersions.add(new PhienBanPdfKhoaDTO(
                    Long.toString(dv.id()),
                    dv.soPhienBan(),
                    "current".equals(l.trangThai()),
                    dv.title(),
                    iso(dv.createdAt()),
                    goc,
                    daKy));
        }

        if (currentLinkOpt.isEmpty()) {
            return new HoSoKyKhoaDTO(
                    Long.toString(root.getId()), root.getMaKhoa(), tenKhoa, tenKhoaPdf,
                    root.getNamHoc(), root.getPhienBan(), root.getTrangThai(), submittedAt, versionToken,
                    null, null, null, null, null, null, null, pdfVersions, signingRepository.history(root.getId()));
        }

        var currentLink = currentLinkOpt.get();
        var doc = signingRepository.docVersion(currentLink.phienBanVanBanId());
        TepTaiLieuDTO tepGoc = describeFileSafe(doc.tepGocId());
        var reqOpt = signingRepository.requestByVersion(doc.id());
        TepTaiLieuDTO tepDaKy = null;
        YeuCauKyKhoaDTO yeuCauDto = null;

        if (reqOpt.isPresent()) {
            var req = reqOpt.get();
            var step = signingRepository.singleStep(req.id());
            var outputOpt = signingRepository.verifiedOutput(req.id());
            if (outputOpt.isPresent()) {
                tepDaKy = describeFileSafe(outputOpt.get().fileId());
            }
            var workOpt = signingRepository.latestWork(req.id());
            boolean canSign = faculty != null && faculty.kySo()
                    && "CHO_KY".equals(root.getTrangThai())
                    && "prepared".equals(req.state())
                    && "waiting".equals(step.state())
                    && tepGoc != null && tepGoc.ready();
            boolean canReconcile = faculty != null && faculty.kySo()
                    && "CHO_KY".equals(root.getTrangThai())
                    && workOpt.map(signingRepository::canReconcile).orElse(false);
            boolean canReopenDraft = faculty != null && faculty.lap()
                    && "CHO_KY".equals(root.getTrangThai())
                    && ("prepared".equals(req.state()) || workOpt.map(signingRepository::safePreDispatch).orElse(false));
            boolean canUpdatePdfContent = canSign
                    && (workOpt.isEmpty() || "awaiting_consent".equals(workOpt.get().phase()));
            boolean canSendSpecialist = faculty != null && faculty.guiChuyenVien()
                    && "DA_KY".equals(root.getTrangThai())
                    && "completed".equals(req.state())
                    && "completed".equals(step.state())
                    && tepDaKy != null && tepDaKy.ready();

            var stepDto = new BuocKyKhoaDTO(
                    step.state(),
                    step.name(),
                    step.email(),
                    outputOpt.map(OutputRow::signedAtIso).orElse(null),
                    step.serial(),
                    step.page(),
                    step.x() == null ? null : step.x().doubleValue(),
                    step.y() == null ? null : step.y().doubleValue(),
                    step.width() == null ? null : step.width().doubleValue(),
                    step.height() == null ? null : step.height().doubleValue());

            yeuCauDto = new YeuCauKyKhoaDTO(
                    Long.toString(req.id()),
                    req.state(),
                    req.mode(),
                    req.error(),
                    canSign,
                    canReconcile,
                    canSendSpecialist,
                    canReopenDraft,
                    canUpdatePdfContent,
                    stepDto);
        }

        return new HoSoKyKhoaDTO(
                Long.toString(root.getId()),
                root.getMaKhoa(),
                tenKhoa,
                tenKhoaPdf,
                root.getNamHoc(),
                root.getPhienBan(),
                root.getTrangThai(),
                submittedAt,
                versionToken,
                Long.toString(doc.vanBanId()),
                Long.toString(doc.id()),
                doc.datasetSha256(),
                currentLink.noiDungHanhChinh(),
                tepGoc,
                tepDaKy,
                yeuCauDto,
                pdfVersions,
                signingRepository.history(root.getId()));
    }

    private TepTaiLieuDTO describeFileSafe(long fileId) {
        ManagedFiles f = filesProvider.getIfAvailable();
        if (f == null) return new TepTaiLieuDTO(Long.toString(fileId), "phan-cong.pdf", 0L, "", false);
        var info = f.describe(fileId);
        return new TepTaiLieuDTO(Long.toString(info.id()), info.name(), info.size(), info.sha256(), info.ready());
    }

    private static String documentTitle(String tenKhoa, String namHoc, int phienBan) {
        return "Danh sách phân công GVCN — " + tenKhoa.strip() + " — Năm học " + namHoc + " (Phiên bản " + phienBan + ")";
    }

    private static String safeFileName(String maKhoa, String namHoc, int phienBan) {
        String cleanCode = maKhoa.replaceAll("[^A-Za-z0-9_-]", "_");
        String cleanYear = namHoc.replaceAll("[^A-Za-z0-9_-]", "_");
        return "PhanCongGVCN_" + cleanCode + "_" + cleanYear + "_v" + phienBan + ".pdf";
    }

    private static String iso(OffsetDateTime value) {
        return value == null ? null : value.toInstant().toString();
    }

    private static String datasetHash(DanhSachPhanCongKhoa root, List<PdfRow> rows) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            update(md, root.getMaKhoa());
            update(md, root.getNamHoc());
            update(md, Integer.toString(root.getPhienBan()));
            update(md, root.getHieuLucTu().toInstant().toString());
            update(md, root.getHieuLucDen() == null ? "" : root.getHieuLucDen().toInstant().toString());
            for (var r : rows) {
                update(md, Integer.toString(r.stt()));
                update(md, r.maLop());
                update(md, r.hoTenGvcn());
                update(md, r.ghiChu() == null ? "" : r.ghiChu());
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void update(MessageDigest md, String part) {
        byte[] bytes = (part == null ? "" : part).getBytes(StandardCharsets.UTF_8);
        md.update(bytes);
        md.update((byte) 0x1F);
    }
}
