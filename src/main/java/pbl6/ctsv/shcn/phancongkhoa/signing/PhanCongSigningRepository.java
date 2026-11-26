package pbl6.ctsv.shcn.phancongkhoa.signing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.shared.ca.CaPdfPreflight;
import pbl6.ctsv.shared.ca.CaProviderDtos.Certificate;
import pbl6.ctsv.shared.ca.CaProviderDtos.Receipt;
import pbl6.ctsv.shcn.phancongkhoa.dto.PhanCongKhoaDtos.MucLichSuKyDTO;
import pbl6.ctsv.shcn.phancongkhoa.dto.PhanCongKhoaDtos.NoiDungHanhChinhDTO;
import pbl6.ctsv.shcn.phancongkhoa.exception.PhanCongKhoaFailure;
import pbl6.ctsv.shcn.phancongkhoa.repository.PhanCongKhoaRepository.XungDot;

/**
 * JDBC persistence for SHCN TASK-TK-02 signing/document links.
 * Uses explicit SQL locks/CAS without adding extra JPA entities to the TK-01 persistence unit.
 */
public class PhanCongSigningRepository {
    public static final String ADAPTER = "ctsv-shcn-tk02-v1";
    public static final String STEP_ROLE = "truong_khoa";

    public record LinkRow(long id, long danhSachId, long phienBanVanBanId,
                          NoiDungHanhChinhDTO noiDungHanhChinh, String trangThai, OffsetDateTime createdAt) {}
    public record DocVersionRow(long id, long vanBanId, UUID vanBanCode, int soPhienBan, String title,
                                long tepGocId, String datasetSha256, OffsetDateTime createdAt) {}
    public record RequestRow(long id, UUID code, long phienBanVanBanId, long providerId, String mode,
                             UUID externalId, UUID categoryId, String state, long actorId,
                             OffsetDateTime createdAt, OffsetDateTime startedAt, OffsetDateTime completedAt, String error) {}
    public record StepRow(long id, long requestId, long userId, int order, String role, String name, String email,
                          UUID caUser, UUID certificate, String serial, Integer page,
                          BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height,
                          String state, OffsetDateTime updatedAt) {}
    public record WorkRow(long id, long requestId, long stepId, UUID operation, long inputFileId, String inputHash,
                          String phase, boolean externalMayHaveChanged, UUID lease, OffsetDateTime leaseUntil,
                          AuthAccess.Principal principal) {}
    public record OutputRow(long fileId, long receiptId, String sha256, String signedAtIso) {}
    public record FileOwnerRow(long danhSachId, String maKhoa, String trangThaiDanhSach, boolean isSignedOutput) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PhanCongSigningRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    private void requireTx() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("SHCN signing repository requires an active transaction");
        }
    }

    public boolean schemaReady() {
        Integer id = jdbc.queryForObject(
                "SELECT CASE WHEN OBJECT_ID(N'shcn.shcn_lien_ket_van_ban_khoa') IS NULL THEN 0 ELSE 1 END",
                Integer.class);
        return id != null && id == 1;
    }

    public void beginSqlScope() {
        requireTx();
        jdbc.query("SELECT TOP(0) id FROM dbo.lan_goi_dich_vu_ky", (org.springframework.jdbc.core.RowCallbackHandler) r -> {});
    }

    public Optional<LinkRow> currentLink(long danhSachId) {
        requireTx();
        if (!schemaReady()) return Optional.empty();
        return jdbc.query("""
            SELECT id, ma_danh_sach_khoa, phien_ban_van_ban_id, noi_dung_hanh_chinh_json, trang_thai, thoi_diem_tao
            FROM shcn.shcn_lien_ket_van_ban_khoa
            WHERE ma_danh_sach_khoa=? AND trang_thai='current'
            """, (r, n) -> mapLink(r), danhSachId).stream().findFirst();
    }

    public List<LinkRow> allLinks(long danhSachId) {
        requireTx();
        if (!schemaReady()) return List.of();
        return jdbc.query("""
            SELECT id, ma_danh_sach_khoa, phien_ban_van_ban_id, noi_dung_hanh_chinh_json, trang_thai, thoi_diem_tao
            FROM shcn.shcn_lien_ket_van_ban_khoa
            WHERE ma_danh_sach_khoa=?
            ORDER BY id DESC
            """, (r, n) -> mapLink(r), danhSachId);
    }

    public Optional<Long> danhSachIdByRequest(long requestId) {
        requireTx();
        if (!schemaReady()) return Optional.empty();
        return jdbc.query("""
            SELECT l.ma_danh_sach_khoa
            FROM dbo.yeu_cau_ky q
            JOIN shcn.shcn_lien_ket_van_ban_khoa l ON l.phien_ban_van_ban_id=q.phien_ban_van_ban_id
            WHERE q.id=? AND q.phien_ban_adapter=? AND l.trang_thai='current'
            """, (r, n) -> r.getLong(1), requestId, ADAPTER).stream().findFirst();
    }

    public DocVersionRow docVersion(long phienBanVanBanId) {
        requireTx();
        return jdbc.query("""
            SELECT v.id, v.van_ban_id, d.ma_van_ban, v.so_phien_ban, v.tieu_de_tai_thoi_diem_chot,
                   v.tep_goc_id, v.sha256_tap_du_lieu, v.thoi_diem_tao
            FROM dbo.phien_ban_van_ban v
            JOIN dbo.van_ban d ON d.id=v.van_ban_id
            WHERE v.id=?
            """, (r, n) -> new DocVersionRow(
                r.getLong(1), r.getLong(2), UUID.fromString(r.getString(3)), r.getInt(4),
                r.getString(5), r.getLong(6), r.getString(7), r.getObject(8, OffsetDateTime.class)
            ), phienBanVanBanId).stream().findFirst().orElseThrow(PhanCongKhoaFailure::notFound);
    }

    public Optional<RequestRow> requestByVersion(long phienBanVanBanId) {
        requireTx();
        return jdbc.query("""
            SELECT id, ma_yeu_cau, phien_ban_van_ban_id, he_thong_cung_cap_id, che_do_thuc_hien,
                   van_ban_ben_ngoai_id, danh_muc_ca_id, trang_thai, nguoi_yeu_cau_id,
                   thoi_diem_yeu_cau, thoi_diem_bat_dau, thoi_diem_hoan_tat, ma_loi_gan_nhat
            FROM dbo.yeu_cau_ky
            WHERE phien_ban_van_ban_id=? AND phien_ban_adapter=?
            """, (r, n) -> mapRequest(r), phienBanVanBanId, ADAPTER).stream().findFirst();
    }

    public RequestRow lockRequest(long requestId) {
        requireTx();
        return jdbc.query("""
            SELECT id, ma_yeu_cau, phien_ban_van_ban_id, he_thong_cung_cap_id, che_do_thuc_hien,
                   van_ban_ben_ngoai_id, danh_muc_ca_id, trang_thai, nguoi_yeu_cau_id,
                   thoi_diem_yeu_cau, thoi_diem_bat_dau, thoi_diem_hoan_tat, ma_loi_gan_nhat
            FROM dbo.yeu_cau_ky WITH(UPDLOCK,HOLDLOCK)
            WHERE id=? AND phien_ban_adapter=?
            """, (r, n) -> mapRequest(r), requestId, ADAPTER).stream().findFirst().orElseThrow(PhanCongKhoaFailure::notFound);
    }

    public StepRow singleStep(long requestId) {
        requireTx();
        return jdbc.query("""
            SELECT id, yeu_cau_ky_id, nguoi_dung_noi_bo_id, thu_tu_ky, vai_tro_nguoi_ky, ten_nguoi_ky, email_nguoi_ky,
                   nguoi_dung_ca_id, chung_thu_id, so_seri_chung_thu, so_trang, toa_do_x, toa_do_y, chieu_rong, chieu_cao,
                   trang_thai, thoi_diem_cap_nhat
            FROM dbo.buoc_ky
            WHERE yeu_cau_ky_id=?
            ORDER BY thu_tu_ky, id
            """, (r, n) -> new StepRow(
                r.getLong(1), r.getLong(2), r.getLong(3), r.getInt(4), r.getString(5), r.getString(6), r.getString(7),
                uuid(r.getString(8)), uuid(r.getString(9)), r.getString(10), r.getObject(11, Integer.class),
                r.getBigDecimal(12), r.getBigDecimal(13), r.getBigDecimal(14), r.getBigDecimal(15),
                r.getString(16), r.getObject(17, OffsetDateTime.class)
            ), requestId).stream().findFirst().orElseThrow(PhanCongKhoaFailure::notFound);
    }

    public Optional<OutputRow> verifiedOutput(long requestId) {
        requireTx();
        return jdbc.query("""
            SELECT a.tep_dinh_kem_id, n.id, f.sha256, CONVERT(varchar(42), n.thoi_diem_ky, 127)
            FROM dbo.bien_nhan_ky n
            JOIN dbo.tep_ket_qua_ky a ON a.bien_nhan_ky_id=n.id
            JOIN dbo.tep_dinh_kem f ON f.id=a.tep_dinh_kem_id
            WHERE n.yeu_cau_ky_id=? AND a.trang_thai_toan_ven=N'verified'
            ORDER BY a.id DESC
            """, (r, n) -> new OutputRow(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4)), requestId)
                .stream().findFirst();
    }

    public long createInitialExport(
            long danhSachId,
            byte[] expectedVersion,
            String existingDossierCode,
            long donViId,
            int businessRevision,
            String title,
            long tepGocId,
            String datasetSha256,
            NoiDungHanhChinhDTO meta,
            PhanCongSigningOptions.ResolvedPlan plan,
            long actorUserId
    ) {
        requireTx();
        ensureSchemaOrUnavailable();
        UUID docCode = existingDossierCode != null && !existingDossierCode.isBlank()
                ? UUID.fromString(existingDossierCode)
                : UUID.randomUUID();
        Long vanBanId = jdbc.query("SELECT id FROM dbo.van_ban WHERE ma_van_ban=?", (r, n) -> r.getLong(1), docCode.toString())
                .stream().findFirst().orElse(null);
        if (vanBanId == null) {
            vanBanId = Objects.requireNonNull(jdbc.queryForObject("""
                INSERT dbo.van_ban(ma_van_ban, ma_phan_he, loai_van_ban, tieu_de, don_vi_so_huu_id, nguoi_tao_id, thoi_diem_tao, thoi_diem_cap_nhat)
                VALUES(?, N'SHCN', N'phan_cong_gvcn_khoa', ?, ?, ?, SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET());
                SELECT CAST(SCOPE_IDENTITY() AS bigint);
                """, Long.class, docCode.toString(), title, donViId, actorUserId));
        } else {
            jdbc.update("UPDATE dbo.van_ban SET tieu_de=?, thoi_diem_cap_nhat=SYSDATETIMEOFFSET() WHERE id=?", title, vanBanId);
        }

        int nextDocVer = Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COALESCE(MAX(so_phien_ban), 0) + 1 FROM dbo.phien_ban_van_ban WITH(UPDLOCK,HOLDLOCK) WHERE van_ban_id=?",
                Integer.class, vanBanId));

        long phienBanVanBanId = insertDocVersionAndPreparedRequest(
                danhSachId, vanBanId, nextDocVer, businessRevision, title, tepGocId, datasetSha256, meta, plan, actorUserId);

        int updated = jdbc.update("""
            UPDATE shcn.shcn_danh_sach_phan_cong_khoa
            SET trang_thai='CHO_KY', ma_ho_so_tai_lieu=?, ngay_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')
            WHERE id=? AND phien_ban_ghi=? AND trang_thai='NHAP'
            """, docCode.toString(), danhSachId, expectedVersion);
        if (updated != 1) throw new XungDot("VERSION_CONFLICT");
        return phienBanVanBanId;
    }

    public long replaceUnsignedPdf(
            long danhSachId,
            byte[] expectedVersion,
            long oldRequestId,
            long vanBanId,
            int businessRevision,
            String title,
            long newTepGocId,
            String datasetSha256,
            NoiDungHanhChinhDTO meta,
            PhanCongSigningOptions.ResolvedPlan plan,
            long actorUserId
    ) {
        requireTx();
        ensureSchemaOrUnavailable();
        int cancelled = jdbc.update("""
            UPDATE dbo.yeu_cau_ky
            SET trang_thai=N'cancelled', nguoi_huy_id=?, thoi_diem_huy=SYSDATETIMEOFFSET(),
                ly_do_huy=N'Cập nhật nội dung hành chính PDF trước khi ký', thoi_diem_cap_nhat=SYSDATETIMEOFFSET()
            WHERE id=? AND trang_thai=N'prepared' AND phien_ban_adapter=?
            """, actorUserId, oldRequestId, ADAPTER);
        if (cancelled != 1) throw PhanCongKhoaFailure.conflict("SHCN_KHONG_THE_SUA_PDF");

        int nextDocVer = Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COALESCE(MAX(so_phien_ban), 0) + 1 FROM dbo.phien_ban_van_ban WITH(UPDLOCK,HOLDLOCK) WHERE van_ban_id=?",
                Integer.class, vanBanId));

        long phienBanVanBanId = insertDocVersionAndPreparedRequest(
                danhSachId, vanBanId, nextDocVer, businessRevision, title, newTepGocId, datasetSha256, meta, plan, actorUserId);

        int touched = jdbc.update("""
            UPDATE shcn.shcn_danh_sach_phan_cong_khoa
            SET ngay_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')
            WHERE id=? AND phien_ban_ghi=? AND trang_thai='CHO_KY'
            """, danhSachId, expectedVersion);
        if (touched != 1) throw new XungDot("VERSION_CONFLICT");
        return phienBanVanBanId;
    }

    private long insertDocVersionAndPreparedRequest(
            long danhSachId,
            long vanBanId,
            int docRevision,
            int businessRevision,
            String title,
            long tepGocId,
            String datasetSha256,
            NoiDungHanhChinhDTO meta,
            PhanCongSigningOptions.ResolvedPlan plan,
            long actorUserId
    ) {
        long phienBanVanBanId = Objects.requireNonNull(jdbc.queryForObject("""
            INSERT dbo.phien_ban_van_ban(van_ban_id, so_phien_ban, tieu_de_tai_thoi_diem_chot, tep_goc_id,
                ma_tham_chieu_nghiep_vu, lan_sua_doi_nghiep_vu, sha256_tap_du_lieu, trang_thai_noi_dung,
                nguoi_chot_id, thoi_diem_chot, nguoi_tao_id, thoi_diem_tao)
            VALUES(?, ?, ?, ?, ?, ?, ?, N'locked', ?, SYSDATETIMEOFFSET(), ?, SYSDATETIMEOFFSET());
            SELECT CAST(SCOPE_IDENTITY() AS bigint);
            """, Long.class, vanBanId, docRevision, title, tepGocId,
                "shcn_danh_sach_phan_cong_khoa:" + danhSachId, businessRevision, datasetSha256, actorUserId, actorUserId));

        jdbc.update("""
            UPDATE shcn.shcn_lien_ket_van_ban_khoa
            SET trang_thai='superseded', thoi_diem_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')
            WHERE ma_danh_sach_khoa=? AND trang_thai='current'
            """, danhSachId);

        jdbc.update("""
            INSERT shcn.shcn_lien_ket_van_ban_khoa(ma_danh_sach_khoa, phien_ban_van_ban_id, noi_dung_hanh_chinh_json, trang_thai, thoi_diem_tao, thoi_diem_cap_nhat)
            VALUES(?, ?, ?, 'current', TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'), TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
            """, danhSachId, phienBanVanBanId, toJson(meta));

        long requestId = Objects.requireNonNull(jdbc.queryForObject("""
            INSERT dbo.yeu_cau_ky(ma_yeu_cau, phien_ban_van_ban_id, he_thong_cung_cap_id, che_do_thuc_hien,
                quy_tac_uy_quyen, danh_muc_ca_id, trang_thai, nguoi_yeu_cau_id, thoi_diem_yeu_cau, thoi_diem_tao, thoi_diem_cap_nhat, phien_ban_adapter)
            VALUES(NEWID(), ?, ?, ?, N'self_faculty_head', ?, N'prepared', ?, SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET(), ?);
            SELECT CAST(SCOPE_IDENTITY() AS bigint);
            """, Long.class, phienBanVanBanId, plan.providerId(), plan.mode(), plan.categoryId().toString(), actorUserId, ADAPTER));

        long stepId = Objects.requireNonNull(jdbc.queryForObject("""
            INSERT dbo.buoc_ky(yeu_cau_ky_id, nguoi_dung_noi_bo_id, thu_tu_ky, vai_tro_nguoi_ky, ten_nguoi_ky,
                email_nguoi_ky, chuc_danh_nguoi_ky, trang_thai, thoi_diem_tao, thoi_diem_cap_nhat)
            VALUES(?, ?, 1, ?, ?, ?, N'TRƯỞNG KHOA', N'waiting', SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET());
            SELECT CAST(SCOPE_IDENTITY() AS bigint);
            """, Long.class, requestId, actorUserId, STEP_ROLE, plan.signerDisplayName(), plan.signerEmail()));

        jdbc.update("""
            INSERT dbo.uy_quyen_ky(buoc_ky_id, loai_uy_quyen, email_nguoi_duoc_uy_quyen_ky, tham_chieu_minh_chung,
                trang_thai, thoi_diem_uy_quyen, nguoi_ghi_nhan_id, thoi_diem_ghi_nhan)
            VALUES(?, N'self_faculty_head', ?, ?, N'active', SYSDATETIMEOFFSET(), ?, SYSDATETIMEOFFSET())
            """, stepId, plan.signerEmail(), "SHCN-TK-02:" + danhSachId, actorUserId);

        return phienBanVanBanId;
    }

    public void reopenDraftInPlace(long danhSachId, byte[] expectedVersion, Long requestId, long actorUserId, String reason) {
        requireTx();
        ensureSchemaOrUnavailable();
        if (requestId != null) {
            jdbc.update("""
                UPDATE dbo.yeu_cau_ky
                SET trang_thai=N'cancelled', nguoi_huy_id=?, thoi_diem_huy=SYSDATETIMEOFFSET(),
                    ly_do_huy=?, thoi_diem_cap_nhat=SYSDATETIMEOFFSET()
                WHERE id=? AND phien_ban_adapter=? AND trang_thai IN(N'prepared', N'failed')
                """, actorUserId, reason == null ? "Hủy chờ ký để chỉnh sửa nháp" : reason, requestId, ADAPTER);
        }
        jdbc.update("""
            UPDATE shcn.shcn_lien_ket_van_ban_khoa
            SET trang_thai='superseded', thoi_diem_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')
            WHERE ma_danh_sach_khoa=? AND trang_thai='current'
            """, danhSachId);

        int updated = jdbc.update("""
            UPDATE shcn.shcn_danh_sach_phan_cong_khoa
            SET trang_thai='NHAP', ly_do_huy=?, ngay_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')
            WHERE id=? AND phien_ban_ghi=? AND trang_thai='CHO_KY'
            """, reason, danhSachId, expectedVersion);
        if (updated != 1) throw new XungDot("VERSION_CONFLICT");
    }

    public long enqueueSigningAttempt(
            long danhSachId,
            byte[] expectedVersion,
            long requestId,
            long stepId,
            long actorUserId,
            String signerEmail,
            String signerDisplayName,
            CaPdfPreflight.Box box,
            AuthAccess.Principal principal,
            long inputFileId,
            String inputSha256
    ) {
        requireTx();
        ensureSchemaOrUnavailable();
        int stepChanged = jdbc.update("""
            UPDATE dbo.buoc_ky
            SET nguoi_dung_noi_bo_id=?, email_nguoi_ky=?, ten_nguoi_ky=?,
                so_trang=?, toa_do_x=?, toa_do_y=?, chieu_rong=?, chieu_cao=?,
                trang_thai=N'queued', thoi_diem_cap_nhat=SYSDATETIMEOFFSET()
            WHERE id=? AND yeu_cau_ky_id=? AND trang_thai=N'waiting'
            """, actorUserId, signerEmail, signerDisplayName,
                box.page(), BigDecimal.valueOf(box.x()), BigDecimal.valueOf(box.y()),
                BigDecimal.valueOf(box.width()), BigDecimal.valueOf(box.height()), stepId, requestId);
        if (stepChanged != 1) throw PhanCongKhoaFailure.conflict("SIGNING_STEP_NOT_READY");

        jdbc.update("""
            UPDATE dbo.uy_quyen_ky SET email_nguoi_duoc_uy_quyen_ky=?
            WHERE buoc_ky_id=? AND trang_thai=N'active'
            """, signerEmail, stepId);

        int reqChanged = jdbc.update("""
            UPDATE dbo.yeu_cau_ky
            SET trang_thai=N'queued', ma_loi_gan_nhat=NULL,
                thoi_diem_bat_dau=COALESCE(thoi_diem_bat_dau, SYSDATETIMEOFFSET()),
                thoi_diem_cap_nhat=SYSDATETIMEOFFSET()
            WHERE id=? AND phien_ban_adapter=? AND trang_thai=N'prepared'
            """, requestId, ADAPTER);
        if (reqChanged != 1) throw PhanCongKhoaFailure.conflict("SIGNING_NOT_READY");

        int nextAttempt = Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COALESCE(MAX(so_thu_tu_lan_goi), 0) + 1 FROM dbo.lan_goi_dich_vu_ky WHERE yeu_cau_ky_id=?",
                Integer.class, requestId));

        long attemptId = Objects.requireNonNull(jdbc.queryForObject("""
            DECLARE @ids TABLE(id bigint);
            INSERT dbo.lan_goi_dich_vu_ky(yeu_cau_ky_id, buoc_ky_id, loai_thao_tac, so_thu_tu_lan_goi, ma_lien_ket_yeu_cau,
                sha256_dau_vao_du_kien, ket_luan, co_the_da_thay_doi_ben_ngoai, thoi_diem_bat_dau, phien_ban_adapter,
                tep_dau_vao_id, giai_doan_xu_ly, nha_phat_hanh_auth, chu_the_auth, phien_ban_auth)
            OUTPUT inserted.id INTO @ids
            VALUES(?, ?, N'sign', ?, NEWID(), ?, N'queued', 0, SYSDATETIMEOFFSET(), ?, ?, N'queued', ?, ?, ?);
            SELECT id FROM @ids;
            """, Long.class, requestId, stepId, nextAttempt, inputSha256, ADAPTER, inputFileId,
                principal.issuer(), principal.accountId().toString(), principal.tokenVersion()));

        int touched = jdbc.update("""
            UPDATE shcn.shcn_danh_sach_phan_cong_khoa
            SET ngay_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')
            WHERE id=? AND phien_ban_ghi=? AND trang_thai='CHO_KY'
            """, danhSachId, expectedVersion);
        if (touched != 1) throw new XungDot("VERSION_CONFLICT");
        return attemptId;
    }

    public Optional<WorkRow> latestWork(long requestId) {
        requireTx();
        return jdbc.query("""
            SELECT TOP(1) id, yeu_cau_ky_id, buoc_ky_id, ma_lien_ket_yeu_cau, tep_dau_vao_id, sha256_dau_vao_du_kien,
                   giai_doan_xu_ly, co_the_da_thay_doi_ben_ngoai, lease_token, lease_den,
                   nha_phat_hanh_auth, chu_the_auth, phien_ban_auth
            FROM dbo.lan_goi_dich_vu_ky
            WHERE yeu_cau_ky_id=? AND phien_ban_adapter=?
            ORDER BY id DESC
            """, (r, n) -> new WorkRow(
                r.getLong(1), r.getLong(2), r.getLong(3), UUID.fromString(r.getString(4)),
                r.getLong(5), r.getString(6), r.getString(7), Boolean.TRUE.equals(r.getObject(8, Boolean.class)),
                uuid(r.getString(9)), r.getObject(10, OffsetDateTime.class),
                new AuthAccess.Principal(r.getString(11), UUID.fromString(r.getString(12)), r.getLong(13))
            ), requestId, ADAPTER).stream().findFirst();
    }

    public List<Long> dueRequests() {
        if (!schemaReady()) return List.of();
        return jdbc.queryForList("""
            SELECT TOP(8) yeu_cau_ky_id
            FROM dbo.lan_goi_dich_vu_ky
            WHERE phien_ban_adapter=? AND giai_doan_xu_ly IN(N'queued', N'uploaded', N'download', N'storage', N'upload_intent', N'sign_intent')
              AND (lease_den IS NULL OR lease_den < SYSDATETIMEOFFSET())
            ORDER BY id
            """, Long.class, ADAPTER);
    }

    public boolean claim(WorkRow w, UUID lease) {
        requireTx();
        return jdbc.update("""
            UPDATE dbo.lan_goi_dich_vu_ky
            SET lease_token=?, lease_den=DATEADD(minute, 5, SYSDATETIMEOFFSET())
            WHERE id=? AND giai_doan_xu_ly=? AND (lease_den IS NULL OR lease_den < SYSDATETIMEOFFSET())
            """, lease.toString(), w.id(), w.phase()) == 1;
    }

    public void markSigningActive(long requestId) {
        requireTx();
        jdbc.update("UPDATE dbo.yeu_cau_ky SET trang_thai=N'signing', thoi_diem_cap_nhat=SYSDATETIMEOFFSET() WHERE id=?", requestId);
    }

    public void owned(long workId, UUID lease, boolean requireUnexpired) {
        requireTx();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.lan_goi_dich_vu_ky WITH(UPDLOCK,HOLDLOCK) WHERE id=? AND lease_token=?"
                        + (requireUnexpired ? " AND lease_den > SYSDATETIMEOFFSET()" : ""),
                Integer.class, workId, lease.toString());
        if (count == null || count != 1) throw PhanCongKhoaFailure.conflict("SIGNING_LEASE_LOST");
    }

    public void updatePhase(WorkRow w, UUID lease, String phase) {
        requireTx();
        owned(w.id(), lease, true);
        boolean externalIntent = Set.of("upload_intent", "sign_intent").contains(phase);
        jdbc.update("""
            UPDATE dbo.lan_goi_dich_vu_ky
            SET giai_doan_xu_ly=?, lease_den=DATEADD(minute, 5, SYSDATETIMEOFFSET()), co_the_da_thay_doi_ben_ngoai=?
            WHERE id=?
            """, phase, externalIntent, w.id());
    }

    public void recordCertificate(long stepId, Certificate c) {
        requireTx();
        Long checkId = jdbc.queryForObject("""
            INSERT dbo.lan_kiem_tra_chung_thu(buoc_ky_id, chung_thu_ca_id, so_seri, nguoi_dung_ca_id, ten_nguoi_so_huu,
                hieu_luc_tu, hieu_luc_den, trang_thai_ca, hop_le_theo_ca, khoa_rieng_san_sang_theo_ca, thoi_diem_kiem_tra, ket_qua)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSDATETIMEOFFSET(), N'verified');
            SELECT CAST(SCOPE_IDENTITY() AS bigint);
            """, Long.class, stepId, c.id().toString(), c.serialNumber(), c.user().toString(), c.userDisplay(),
                c.validFrom().atOffset(ZoneOffset.UTC), c.validTo().atOffset(ZoneOffset.UTC),
                c.status(), c.valid(), c.hasPrivateKey());
        jdbc.update("""
            UPDATE dbo.buoc_ky
            SET nguoi_dung_ca_id=?, chung_thu_id=?, so_seri_chung_thu=?, ten_nguoi_ky=?,
                lan_kiem_tra_chung_thu_id=?, thoi_diem_kiem_tra_chung_thu=SYSDATETIMEOFFSET(), thoi_diem_cap_nhat=SYSDATETIMEOFFSET()
            WHERE id=?
            """, c.user().toString(), c.id().toString(), c.serialNumber(), c.userDisplay(), checkId, stepId);
    }

    public void recordUploaded(WorkRow w, UUID lease, UUID externalDocumentId) {
        requireTx();
        owned(w.id(), lease, false);
        jdbc.update("UPDATE dbo.yeu_cau_ky SET van_ban_ben_ngoai_id=?, thoi_diem_cap_nhat=SYSDATETIMEOFFSET() WHERE id=?",
                externalDocumentId.toString(), w.requestId());
        jdbc.update("""
            UPDATE dbo.lan_goi_dich_vu_ky
            SET van_ban_ca_phan_hoi_id=?, giai_doan_xu_ly=N'uploaded', ket_luan=N'uploaded', co_the_da_thay_doi_ben_ngoai=0
            WHERE id=?
            """, externalDocumentId.toString(), w.id());
    }

    public Optional<Receipt> findReceipt(long attemptId) {
        requireTx();
        return jdbc.query("""
            SELECT chu_ky_ben_ngoai_id, van_ban_ben_ngoai_id, chung_thu_id, so_seri_chung_thu, email_nguoi_ky, ten_nguoi_ky,
                   sha256_van_ban, sha256_pdf_da_ky, thoi_diem_ky, trang_thai_chu_ky_phia_ca
            FROM dbo.bien_nhan_ky
            WHERE lan_goi_id=? AND duoc_chon=1
            """, (r, n) -> new Receipt(
                UUID.fromString(r.getString(1)), UUID.fromString(r.getString(2)), UUID.fromString(r.getString(3)),
                r.getString(4), r.getString(5), r.getString(6), r.getString(7), r.getString(8),
                r.getObject(9, OffsetDateTime.class).toInstant(), r.getString(10)
            ), attemptId).stream().findFirst();
    }

    public void recordReceipt(WorkRow w, UUID lease, long providerId, Receipt receipt) {
        requireTx();
        owned(w.id(), lease, false);
        jdbc.update("""
            INSERT dbo.bien_nhan_ky(yeu_cau_ky_id, buoc_ky_id, lan_goi_id, he_thong_cung_cap_id, chu_ky_ben_ngoai_id,
                van_ban_ben_ngoai_id, chung_thu_id, so_seri_chung_thu, ten_nguoi_ky, email_nguoi_ky,
                sha256_van_ban, sha256_pdf_da_ky, trang_thai_chu_ky_phia_ca, duoc_chon, thoi_diem_ky, thoi_diem_tiep_nhan, phien_ban_hop_dong)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, SYSDATETIMEOFFSET(), N'SHCN-TK02-26-11')
            """, w.requestId(), w.stepId(), w.id(), providerId, receipt.id().toString(),
                receipt.document().toString(), receipt.certificate().toString(), receipt.certificateSerial(),
                receipt.signerName(), receipt.signerEmail(), receipt.documentHash(), receipt.signedPdfHash(),
                receipt.status(), receipt.signedAt().atOffset(ZoneOffset.UTC));
        jdbc.update("""
            UPDATE dbo.lan_goi_dich_vu_ky
            SET giai_doan_xu_ly=N'download', ket_luan=N'receipt', chu_ky_ca_phan_hoi_id=?, sha256_pdf_phan_hoi=?, co_the_da_thay_doi_ben_ngoai=0
            WHERE id=?
            """, receipt.id().toString(), receipt.signedPdfHash(), w.id());
    }

    public void recordError(WorkRow w, UUID lease, String phase, String code, boolean uncertain) {
        requireTx();
        if (lease != null) owned(w.id(), lease, false);
        String nextPhase = uncertain ? "unknown" : phase;
        String state = uncertain ? "unknown" : "failed";
        jdbc.update("""
            UPDATE dbo.lan_goi_dich_vu_ky
            SET giai_doan_xu_ly=?, ket_luan=?, ma_loi=?, co_the_da_thay_doi_ben_ngoai=?, lease_den=NULL
            WHERE id=?
            """, nextPhase, uncertain ? "unknown" : "rejected", code, uncertain, w.id());
        jdbc.update("UPDATE dbo.yeu_cau_ky SET trang_thai=?, ma_loi_gan_nhat=?, thoi_diem_cap_nhat=SYSDATETIMEOFFSET() WHERE id=?",
                state, code, w.requestId());
        jdbc.update("UPDATE dbo.buoc_ky SET trang_thai=?, thoi_diem_cap_nhat=SYSDATETIMEOFFSET() WHERE id=?",
                state, w.stepId());
    }

    public void completeSigning(WorkRow w, UUID lease, long danhSachId, long signedFileId) {
        requireTx();
        owned(w.id(), lease, true);
        Long receiptId = jdbc.queryForObject(
                "SELECT id FROM dbo.bien_nhan_ky WHERE lan_goi_id=? AND duoc_chon=1", Long.class, w.id());
        Long fileCheckId = jdbc.query("SELECT TOP(1) id FROM dbo.lan_kiem_tra_tep WHERE tep_dinh_kem_id=? AND trang_thai_toan_ven=N'verified' ORDER BY id DESC",
                (r, n) -> r.getLong(1), signedFileId).stream().findFirst().orElse(null);
        jdbc.update("""
            INSERT dbo.tep_ket_qua_ky(yeu_cau_ky_id, bien_nhan_ky_id, tep_dinh_kem_id, loai_tep_ket_qua,
                trang_thai_toan_ven, trang_thai_kiem_tra_chu_ky, phuong_phap_kiem_tra, lan_kiem_tra_tep_id, thoi_diem_tai_ve, thoi_diem_xac_minh)
            VALUES(?, ?, ?, N'final_signed_pdf', N'verified', N'cryptographic_verified', N'pdfbox-bc-sha256', ?, SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET())
            """, w.requestId(), receiptId, signedFileId, fileCheckId);
        jdbc.update("""
            UPDATE dbo.lan_goi_dich_vu_ky
            SET giai_doan_xu_ly=N'completed', ket_luan=N'completed', tep_dau_ra_id=?, luot_luu_tep_id=?,
                thoi_diem_ket_thuc=SYSDATETIMEOFFSET(), lease_den=NULL
            WHERE id=?
            """, signedFileId, w.operation().toString(), w.id());
        jdbc.update("UPDATE dbo.buoc_ky SET trang_thai=N'completed', thoi_diem_cap_nhat=SYSDATETIMEOFFSET() WHERE id=?", w.stepId());
        jdbc.update("""
            UPDATE dbo.yeu_cau_ky
            SET trang_thai=N'completed', ma_loi_gan_nhat=NULL, thoi_diem_hoan_tat=SYSDATETIMEOFFSET(), thoi_diem_cap_nhat=SYSDATETIMEOFFSET()
            WHERE id=?
            """, w.requestId());
        int changed = jdbc.update("""
            UPDATE shcn.shcn_danh_sach_phan_cong_khoa
            SET trang_thai='DA_KY', ngay_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')
            WHERE id=? AND trang_thai='CHO_KY'
            """, danhSachId);
        if (changed != 1) throw PhanCongKhoaFailure.conflict("SIGNING_NOT_READY");
    }

    public boolean safePreDispatch(WorkRow w) {
        requireTx();
        if (!"failed".equals(w.phase()) || w.externalMayHaveChanged() || findReceipt(w.id()).isPresent()) return false;
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM dbo.lan_goi_dich_vu_ky
            WHERE id=? AND co_the_da_thay_doi_ben_ngoai=0 AND chu_ky_ca_phan_hoi_id IS NULL
              AND ma_loi IN(N'CA_CERTIFICATE_SELECTION_REQUIRED', N'CA_CERTIFICATE_NOT_READY', N'CA_NOT_CONFIGURED', N'SIGNING_AUTHORIZATION_REVOKED')
            """, Integer.class, w.id());
        return count != null && count == 1;
    }

    public boolean canReconcile(WorkRow w) {
        requireTx();
        if (findReceipt(w.id()).isPresent() && Set.of("download", "storage", "unknown", "failed").contains(w.phase())) return true;
        return safePreDispatch(w);
    }

    public void releaseForNewConsent(long danhSachId, byte[] expectedVersion, WorkRow w) {
        requireTx();
        jdbc.update("UPDATE dbo.lan_goi_dich_vu_ky SET giai_doan_xu_ly=N'awaiting_consent', lease_den=NULL WHERE id=?", w.id());
        jdbc.update("""
            UPDATE dbo.buoc_ky
            SET trang_thai=N'waiting', so_trang=NULL, toa_do_x=NULL, toa_do_y=NULL, chieu_rong=NULL, chieu_cao=NULL,
                thoi_diem_cap_nhat=SYSDATETIMEOFFSET()
            WHERE id=?
            """, w.stepId());
        jdbc.update("UPDATE dbo.yeu_cau_ky SET trang_thai=N'prepared', ma_loi_gan_nhat=NULL, thoi_diem_cap_nhat=SYSDATETIMEOFFSET() WHERE id=?", w.requestId());
        int touched = jdbc.update("""
            UPDATE shcn.shcn_danh_sach_phan_cong_khoa
            SET ngay_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')
            WHERE id=? AND phien_ban_ghi=? AND trang_thai='CHO_KY'
            """, danhSachId, expectedVersion);
        if (touched != 1) throw new XungDot("VERSION_CONFLICT");
    }

    public void resumePostReceipt(long danhSachId, byte[] expectedVersion, WorkRow w) {
        requireTx();
        jdbc.update("UPDATE dbo.lan_goi_dich_vu_ky SET giai_doan_xu_ly=N'download', co_the_da_thay_doi_ben_ngoai=0, lease_den=NULL, lease_token=NULL WHERE id=?", w.id());
        jdbc.update("UPDATE dbo.yeu_cau_ky SET trang_thai=N'queued', ma_loi_gan_nhat=NULL, thoi_diem_cap_nhat=SYSDATETIMEOFFSET() WHERE id=?", w.requestId());
        jdbc.update("UPDATE dbo.buoc_ky SET trang_thai=N'queued', thoi_diem_cap_nhat=SYSDATETIMEOFFSET() WHERE id=?", w.stepId());
        int touched = jdbc.update("""
            UPDATE shcn.shcn_danh_sach_phan_cong_khoa
            SET ngay_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')
            WHERE id=? AND phien_ban_ghi=? AND trang_thai='CHO_KY'
            """, danhSachId, expectedVersion);
        if (touched != 1) throw new XungDot("VERSION_CONFLICT");
    }

    public void markSubmittedToSpecialist(long danhSachId, byte[] expectedVersion) {
        requireTx();
        int changed = jdbc.update("""
            UPDATE shcn.shcn_danh_sach_phan_cong_khoa
            SET trang_thai='DA_GUI_CHUYEN_VIEN',
                ngay_gui_chuyen_vien=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'),
                ngay_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')
            WHERE id=? AND phien_ban_ghi=? AND trang_thai='DA_KY'
            """, danhSachId, expectedVersion);
        if (changed != 1) throw new XungDot("VERSION_CONFLICT");
    }

    public List<MucLichSuKyDTO> history(long danhSachId) {
        requireTx();
        return jdbc.query("""
            SELECT CONVERT(varchar(20), id), hanh_dong, ly_do, CONVERT(varchar(42), thoi_diem_tao, 127)
            FROM dbo.nhat_ky_thao_tac
            WHERE loai_doi_tuong=N'shcn_danh_sach_phan_cong_khoa' AND doi_tuong_id=?
            ORDER BY id DESC
            """, (r, n) -> new MucLichSuKyDTO(r.getString(1), r.getString(2), r.getString(3), r.getString(4)),
                Long.toString(danhSachId));
    }

    public List<FileOwnerRow> findFileOwners(long fileId) {
        requireTx();
        if (!schemaReady()) return List.of();
        return jdbc.query("""
            SELECT ds.id, ds.ma_khoa, ds.trang_thai,
                   CASE WHEN v.tep_goc_id=? THEN 0 ELSE 1 END AS is_signed_output
            FROM shcn.shcn_lien_ket_van_ban_khoa l
            JOIN shcn.shcn_danh_sach_phan_cong_khoa ds ON ds.id=l.ma_danh_sach_khoa
            JOIN dbo.phien_ban_van_ban v ON v.id=l.phien_ban_van_ban_id
            JOIN dbo.van_ban d ON d.id=v.van_ban_id
            WHERE d.ma_phan_he=N'SHCN' AND ds.trang_thai<>'DA_HUY'
              AND (v.tep_goc_id=? OR EXISTS(
                   SELECT 1 FROM dbo.yeu_cau_ky q
                   JOIN dbo.tep_ket_qua_ky a ON a.yeu_cau_ky_id=q.id
                   WHERE q.phien_ban_van_ban_id=v.id AND q.phien_ban_adapter=?
                     AND a.tep_dinh_kem_id=? AND a.trang_thai_toan_ven=N'verified'))
            """, (r, n) -> new FileOwnerRow(r.getLong(1), r.getString(2), r.getString(3), r.getInt(4) == 1),
                fileId, fileId, ADAPTER, fileId);
    }

    private void ensureSchemaOrUnavailable() {
        if (!schemaReady()) throw PhanCongKhoaFailure.unavailable();
    }

    private LinkRow mapLink(ResultSet r) throws SQLException {
        String rawJson = r.getString(4);
        NoiDungHanhChinhDTO meta = null;
        if (rawJson != null && !rawJson.isBlank()) {
            try {
                meta = json.readValue(rawJson, NoiDungHanhChinhDTO.class);
            } catch (JsonProcessingException e) {
                throw PhanCongKhoaFailure.unavailable();
            }
        }
        return new LinkRow(r.getLong(1), r.getLong(2), r.getLong(3), meta, r.getString(5), r.getObject(6, OffsetDateTime.class));
    }

    private static RequestRow mapRequest(ResultSet r) throws SQLException {
        return new RequestRow(
                r.getLong(1), UUID.fromString(r.getString(2)), r.getLong(3), r.getLong(4), r.getString(5),
                uuid(r.getString(6)), uuid(r.getString(7)), r.getString(8), r.getLong(9),
                r.getObject(10, OffsetDateTime.class), r.getObject(11, OffsetDateTime.class),
                r.getObject(12, OffsetDateTime.class), r.getString(13)
        );
    }

    private String toJson(NoiDungHanhChinhDTO meta) {
        try {
            return json.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize PDF metadata", e);
        }
    }

    private static UUID uuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }
}
