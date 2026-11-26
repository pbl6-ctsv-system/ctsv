package pbl6.ctsv.shcn.phancongkhoa.signing;

import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.auth.AuthFailure;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.shared.ca.CaFailure;
import pbl6.ctsv.shared.ca.CaPdfPreflight;
import pbl6.ctsv.shared.ca.CaPdfSignatureVerifier;
import pbl6.ctsv.shared.ca.CaProviderDtos.Receipt;
import pbl6.ctsv.shared.ca.CaProviderDtos.Templates;
import pbl6.ctsv.shared.ca.CaSignerBinding;
import pbl6.ctsv.shared.files.FileProblem;
import pbl6.ctsv.shared.files.ManagedFiles;
import pbl6.ctsv.shcn.foundation.PhanCongKhoaFoundationPort;
import pbl6.ctsv.shcn.phancongkhoa.entity.DanhSachPhanCongKhoa;
import pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure;
import pbl6.ctsv.shcn.phancongkhoa.repository.PhanCongKhoaAudit;
import pbl6.ctsv.shcn.phancongkhoa.repository.PhanCongKhoaRepository;
import pbl6.ctsv.shcn.phancongkhoa.signing.PhanCongSigningRepository.*;

/**
 * Durable single-signer CA DUT worker for SHCN faculty GVCN assignments (TASK-TK-02).
 * SQL transactions always end before every CA DUT or Cloudinary network call.
 */
public final class PhanCongSigningWorker {
    private static final Logger LOG = LoggerFactory.getLogger(PhanCongSigningWorker.class);

    private record Context(long danhSachId, DanhSachPhanCongKhoa root, RequestRow request, StepRow step, DocVersionRow doc) {}

    private final TransactionTemplate tx;
    private final AuthAccess auth;
    private final FoundationAccess identities;
    private final PhanCongKhoaFoundationPort foundation;
    private final PhanCongKhoaRepository listRepository;
    private final PhanCongSigningRepository signingRepository;
    private final PhanCongKhoaAudit audit;
    private final PhanCongSigningGateway ca;
    private final ObjectProvider<ManagedFiles> filesProvider;
    private final Clock clock;

    public PhanCongSigningWorker(
            PlatformTransactionManager manager,
            AuthAccess auth,
            FoundationAccess identities,
            PhanCongKhoaFoundationPort foundation,
            PhanCongKhoaRepository listRepository,
            PhanCongSigningRepository signingRepository,
            PhanCongKhoaAudit audit,
            PhanCongSigningGateway ca,
            ObjectProvider<ManagedFiles> filesProvider,
            Clock clock
    ) {
        this.tx = new TransactionTemplate(manager);
        this.auth = auth;
        this.identities = identities;
        this.foundation = foundation;
        this.listRepository = listRepository;
        this.signingRepository = signingRepository;
        this.audit = audit;
        this.ca = ca;
        this.filesProvider = filesProvider;
        this.clock = clock;
    }

    private ManagedFiles files() {
        ManagedFiles f = filesProvider.getIfAvailable();
        if (f == null) throw PhanCongKhoaFailure.unavailable();
        return f;
    }

    private Context loadContext(long requestId) {
        long danhSachId = signingRepository.danhSachIdByRequest(requestId)
                .orElseThrow(() -> PhanCongKhoaFailure.conflict("SIGNING_NOT_READY"));
        var root = listRepository.khoaDanhSach(danhSachId)
                .orElseThrow(() -> PhanCongKhoaFailure.conflict("SIGNING_NOT_READY"));
        var req = signingRepository.lockRequest(requestId);
        var step = signingRepository.singleStep(requestId);
        var doc = signingRepository.docVersion(req.phienBanVanBanId());
        return new Context(danhSachId, root, req, step, doc);
    }

    private void verifyDispatchAuthorization(WorkRow w) {
        auth.requireAcceptedWorkAccess(w.principal(), w.principal().accountId());
        var identity = identities.identify(w.principal(), false);
        var actor = foundation.quyen(identity);
        Context c = loadContext(w.requestId());
        if (actor.userId() != c.step().userId()) {
            throw new PhanCongKhoaFailure(403, "FORBIDDEN");
        }
        boolean facultyCanSign = actor.khoa().stream()
                .anyMatch(k -> k.ma().equals(c.root().getMaKhoa()) && k.kySo() && k.dangHoatDong());
        if (!facultyCanSign) {
            throw new PhanCongKhoaFailure(403, "FORBIDDEN");
        }
        if (!"CHO_KY".equals(c.root().getTrangThai())) {
            throw PhanCongKhoaFailure.conflict("SIGNING_NOT_READY");
        }
        var currentLink = signingRepository.currentLink(c.danhSachId())
                .orElseThrow(() -> PhanCongKhoaFailure.conflict("SIGNING_NOT_READY"));
        if (currentLink.phienBanVanBanId() != c.doc().id() || c.doc().tepGocId() != w.inputFileId()) {
            throw PhanCongKhoaFailure.conflict("SIGNING_NOT_READY");
        }
        if (!files().describe(w.inputFileId()).sha256().equals(w.inputHash())) {
            throw PhanCongKhoaFailure.conflict("SIGNING_NOT_READY");
        }
    }

    public boolean runRequest(long requestId) {
        WorkRow pending = tx.execute(s -> signingRepository.latestWork(requestId).orElse(null));
        if (pending == null || !Set.of("queued", "uploaded", "download", "storage", "upload_intent", "sign_intent").contains(pending.phase())) {
            return false;
        }
        UUID lease = UUID.randomUUID();
        Boolean claimed;
        try {
            claimed = tx.execute(s -> {
                signingRepository.beginSqlScope();
                boolean recovery = signingRepository.findReceipt(pending.id()).isPresent();
                if (!recovery && !Set.of("upload_intent", "sign_intent").contains(pending.phase())) {
                    verifyDispatchAuthorization(pending);
                }
                signingRepository.lockRequest(requestId);
                WorkRow latest = signingRepository.latestWork(requestId).orElse(null);
                if (latest == null || latest.id() != pending.id() || !Objects.equals(latest.phase(), pending.phase())
                        || (latest.leaseUntil() != null && latest.leaseUntil().toInstant().isAfter(clock.instant()))) {
                    return false;
                }
                if (Set.of("upload_intent", "sign_intent").contains(latest.phase())) {
                    signingRepository.recordError(latest, latest.lease(), "unknown", "SIGNING_RESULT_UNKNOWN", true);
                    return false;
                }
                if (!signingRepository.claim(latest, lease)) return false;
                signingRepository.markSigningActive(requestId);
                return true;
            });
        } catch (FoundationAccess.Failure | AuthFailure | PhanCongKhoaFailure rejected) {
            tx.executeWithoutResult(s -> {
                signingRepository.beginSqlScope();
                signingRepository.lockRequest(requestId);
                var w = signingRepository.latestWork(requestId).orElse(null);
                if (w != null && w.id() == pending.id() && Set.of("queued", "uploaded").contains(w.phase())
                        && (w.leaseUntil() == null || !w.leaseUntil().toInstant().isAfter(clock.instant()))) {
                    signingRepository.recordError(w, null, "failed", "SIGNING_AUTHORIZATION_REVOKED", false);
                }
            });
            return true;
        }
        if (!Boolean.TRUE.equals(claimed)) return false;

        String phase = pending.phase();
        try {
            Context c = Objects.requireNonNull(tx.execute(s -> loadContext(pending.requestId())));
            var box = new CaPdfPreflight.Box(
                    c.step().page(),
                    c.step().x().doubleValue(),
                    c.step().y().doubleValue(),
                    c.step().width().doubleValue(),
                    c.step().height().doubleValue());

            ManagedFiles managedFiles = files();
            var input = managedFiles.read(pending.inputFileId());
            if (!input.sha256().equals(pending.inputHash())) {
                throw new CaFailure("CA_INPUT_HASH_MISMATCH");
            }

            Receipt receipt = tx.execute(s -> signingRepository.findReceipt(pending.id()).orElse(null));
            UUID external = c.request().externalId();

            if (receipt == null) {
                var certificates = ca.certificates(c.step().email());
                var active = certificates.items().stream()
                        .filter(cert -> "active".equals(cert.status()) && cert.valid() && cert.hasPrivateKey()
                                && cert.validFrom() != null && cert.validTo() != null
                                && !clock.instant().isBefore(cert.validFrom()) && clock.instant().isBefore(cert.validTo()))
                        .toList();
                if (active.size() != 1) throw new CaFailure("CA_CERTIFICATE_SELECTION_REQUIRED");
                var cert = active.getFirst();
                var signer = CaSignerBinding.bind(
                        c.step().email(), cert.user(), cert.id(), null,
                        certificates, new Templates(c.step().email(), java.util.List.of()), clock.instant());
                CaSignerBinding.requireConsent(
                        new CaSignerBinding.Consent("CTSV SHCN confirmed operation " + pending.operation(),
                                c.step().email(), cert.id(), pending.inputHash(), 1),
                        signer, pending.inputHash(), 1);

                tx.executeWithoutResult(s -> {
                    verifyDispatchAuthorization(pending);
                    signingRepository.beginSqlScope();
                    signingRepository.lockRequest(requestId);
                    signingRepository.owned(pending.id(), lease, true);
                    signingRepository.recordCertificate(pending.stepId(), cert);
                });

                if (external == null) {
                    phase = "upload_intent";
                    transitionPhase(pending, lease, phase, true);
                    var uploaded = ca.upload(CaPdfPreflight.inspect(input.bytes(), box), c.doc().title(), c.request().categoryId());
                    if (!uploaded.fileHash().equals(pending.inputHash()) || !Objects.equals(uploaded.category(), c.request().categoryId())) {
                        throw new CaFailure("CA_UPLOAD_MISMATCH", 201, true);
                    }
                    external = uploaded.id();
                    UUID capturedExternal = external;
                    tx.executeWithoutResult(s -> {
                        signingRepository.beginSqlScope();
                        signingRepository.lockRequest(requestId);
                        signingRepository.recordUploaded(pending, lease, capturedExternal);
                    });
                }

                phase = "sign_intent";
                transitionPhase(pending, lease, phase, true);
                receipt = ca.sign(external, cert.id(), signer.email(), signer.name(), box);
                if (!Objects.equals(receipt.document(), external)
                        || !Objects.equals(receipt.certificate(), cert.id())
                        || !Objects.equals(receipt.certificateSerial(), cert.serialNumber())
                        || !signer.email().equals(receipt.signerEmail())
                        || !signer.name().equals(receipt.signerName())
                        || !pending.inputHash().equals(receipt.documentHash())
                        || !"valid".equals(receipt.status())) {
                    throw new CaFailure("CA_RECEIPT_MISMATCH", 201, true);
                }
                Receipt capturedReceipt = receipt;
                tx.executeWithoutResult(s -> {
                    signingRepository.beginSqlScope();
                    signingRepository.lockRequest(requestId);
                    signingRepository.recordReceipt(pending, lease, c.request().providerId(), capturedReceipt);
                });
            }

            phase = "download";
            transitionPhase(pending, lease, phase, false);
            var alreadyCommitted = managedFiles.committed(pending.operation(), c.step().userId(), receipt.signedPdfHash());
            byte[] signedBytes = alreadyCommitted.isPresent()
                    ? managedFiles.read(alreadyCommitted.get().id()).bytes()
                    : ca.download(receipt.document());

            phase = "storage";
            transitionPhase(pending, lease, phase, false);
            var stored = verifyAndStoreSignedArtifact(
                    managedFiles, pending.operation(), c.step().userId(),
                    input.bytes(), signedBytes, receipt, c.doc().title() + " — đã ký.pdf");

            tx.executeWithoutResult(s -> {
                signingRepository.beginSqlScope();
                var freshRoot = listRepository.khoaDanhSach(c.danhSachId())
                        .orElseThrow(() -> PhanCongKhoaFailure.conflict("SIGNING_NOT_READY"));
                signingRepository.lockRequest(requestId);
                if (!managedFiles.describe(stored.id()).ready()) {
                    throw PhanCongKhoaFailure.conflict("FILE_NOT_READY");
                }
                signingRepository.completeSigning(pending, lease, freshRoot.getId(), stored.id());
                audit.ghi(c.step().userId(), "SHCN_KHOA_SIGN_COMPLETED", freshRoot.getId(),
                        "Đã xác thực chữ ký số Trưởng khoa và lưu PDF tại CTSV");
            });
            return true;
        } catch (RuntimeException failure) {
            boolean intent = Set.of("upload_intent", "sign_intent").contains(phase);
            boolean uncertain = intent && (!(failure instanceof CaFailure caFailure) || caFailure.uncertain());
            String safeCode = failure instanceof CaFailure e ? e.code()
                    : failure instanceof FileProblem ? failure.getMessage()
                    : failure instanceof PhanCongKhoaFailure pf ? pf.code()
                    : "SIGNING_INTERNAL_ERROR";
            String resumePhase = Set.of("download", "storage").contains(phase) ? phase : "failed";
            tx.executeWithoutResult(s -> {
                signingRepository.beginSqlScope();
                signingRepository.lockRequest(requestId);
                signingRepository.recordError(pending, lease, resumePhase, safeCode, uncertain);
            });
            LOG.warn("SHCN signing operation {} stopped at {} ({})", pending.operation(), phase, safeCode);
            return true;
        }
    }

    public void tick() {
        for (long requestId : signingRepository.dueRequests()) {
            try {
                runRequest(requestId);
            } catch (RuntimeException e) {
                LOG.warn("SHCN signing job deferred ({})", e.getClass().getSimpleName());
            }
        }
    }

    private void transitionPhase(WorkRow w, UUID lease, String phase, boolean recheckDispatch) {
        tx.executeWithoutResult(s -> {
            if (recheckDispatch) verifyDispatchAuthorization(w);
            signingRepository.beginSqlScope();
            signingRepository.lockRequest(w.requestId());
            signingRepository.updatePhase(w, lease, phase);
        });
    }

    private static ManagedFiles.Info verifyAndStoreSignedArtifact(
            ManagedFiles files,
            UUID operation,
            long actorUserId,
            byte[] input,
            byte[] output,
            Receipt receipt,
            String fileName
    ) {
        if (!CaPdfPreflight.sha256(input).equals(receipt.documentHash())
                || !CaPdfPreflight.sha256(output).equals(receipt.signedPdfHash())) {
            throw new CaFailure("CA_SIGNED_HASH_MISMATCH");
        }
        if (output.length > CaPdfPreflight.MAX_BYTES) {
            throw new CaFailure("PDF_TOO_LARGE");
        }
        if (output.length <= input.length || !Arrays.equals(input, Arrays.copyOf(output, input.length))) {
            throw new CaFailure("CA_REVISION_MISMATCH");
        }
        var before = CaPdfSignatureVerifier.verify(input);
        var after = CaPdfSignatureVerifier.verify(output);
        if ((!before.signatures().isEmpty() && !before.cryptoVerified())
                || !after.cryptoVerified()
                || after.signatures().size() != before.signatures().size() + 1
                || !after.signatures().subList(0, before.signatures().size()).equals(before.signatures())) {
            throw new CaFailure("CA_CRYPTO_NOT_VERIFIED");
        }
        return files.store(operation, actorUserId, output, fileName);
    }
}
