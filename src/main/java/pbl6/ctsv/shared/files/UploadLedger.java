package pbl6.ctsv.shared.files;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class UploadLedger {
    record Attempt(UUID id, long owner, String cloud, String key, String kind, long bytes, String hash) {}
    record Due(UUID id, String key, String state, String dueAt) {}

    private final JdbcTemplate jdbc;

    UploadLedger(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void start(Attempt a) {
        jdbc.update("""
            INSERT dbo.luot_tai_tep(id,nguoi_dung_id,ten_cloud,ma_cong_khai,loai_tep,kich_thuoc,sha256,trang_thai,doi_soat_sau)
            VALUES(?,?,?,?,?,?,?,'started',DATEADD(minute,10,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')))
            """, a.id().toString(), a.owner(), a.cloud(), a.key(), a.kind(), a.bytes(), a.hash());
    }

    void complete(Attempt a, long file, PrivateFileStorage.Asset asset) {
        if (jdbc.update("""
            UPDATE dbo.luot_tai_tep SET trang_thai='committed',tep_id=?,ma_tai_nguyen=?
            WHERE id=? AND trang_thai IN ('started','unknown') AND tep_id IS NULL
            """, file, asset.id(), a.id().toString()) != 1) {
            throw new IllegalStateException("Upload ledger state changed before commit");
        }
    }

    void uncertain(Attempt a) {
        jdbc.update("UPDATE dbo.luot_tai_tep SET trang_thai='unknown' WHERE id=? AND trang_thai='started'", a.id().toString());
    }

    List<Due> due(String cloud) {
        return jdbc.query("""
            SELECT TOP (8) id,ma_cong_khai,trang_thai,CONVERT(varchar(42),doi_soat_sau,127)
            FROM dbo.luot_tai_tep WHERE ten_cloud COLLATE Latin1_General_100_BIN2=?
            AND trang_thai IN ('started','unknown','not_found') AND doi_soat_sau<=SYSUTCDATETIME()
            ORDER BY doi_soat_sau,id
            """, (r,n) -> new Due(UUID.fromString(r.getString(1)),r.getString(2),r.getString(3),r.getString(4)), cloud);
    }

    void reconcile(Due candidate, Optional<PrivateFileStorage.Asset> remote) {
        update(candidate, remote.isPresent() ? "orphan" : "not_found",
            remote.map(PrivateFileStorage.Asset::id).orElse(null), 1440);
    }

    void defer(Due candidate) { update(candidate, candidate.state(), null, 10); }

    private void update(Due candidate, String state, String asset, int delayMinutes) {
        jdbc.update("""
            UPDATE dbo.luot_tai_tep SET trang_thai=?,ma_tai_nguyen=?,
                doi_soat_sau=DATEADD(minute,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
            WHERE id=? AND trang_thai=? AND doi_soat_sau=CONVERT(datetimeoffset(7),?) AND tep_id IS NULL
            """, state, asset, delayMinutes, candidate.id().toString(), candidate.state(), candidate.dueAt());
    }
}
