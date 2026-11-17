package pbl6.ctsv.shared.files;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

final class FileMetadata {
    record Stored(long id, Long owner, long bytes, String hash, String name, String state,
                  String cloud, String asset, String resource, String delivery, boolean deleted) {}
    record Links(boolean bound, boolean ownApplication, boolean application, boolean publicTemplate, boolean template) {}
    record View(String id, String fileName, String contentType, String size, String sha256,
                String uploadedAt, String state) {}

    private final JdbcTemplate jdbc;

    FileMetadata(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    View save(UploadLedger.Attempt a, PrivateFileStorage.Asset asset, int pages, String name) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        Long id = jdbc.queryForObject("""
            DECLARE @ids TABLE(id bigint);
            INSERT dbo.tep_dinh_kem(ten_cloud,ma_tai_nguyen,ma_cong_khai,loai_tai_nguyen,kieu_phan_phoi,
                phien_ban_luu_tru,dinh_dang_tep,ten_tep,kieu_noi_dung,kich_thuoc_tep,sha256,trang_thai_luu_tru,
                nguoi_so_huu_id,nguoi_tai_len_id,thoi_diem_tai_len)
            OUTPUT inserted.id INTO @ids
            VALUES(?,?,?,'raw','authenticated',?,'pdf',?,'application/pdf',?,?,'available',?,?,?);
            SELECT id FROM @ids;
            """, Long.class, a.cloud(),asset.id(),a.key(),asset.version(),name,a.bytes(),a.hash(),a.owner(),a.owner(),now);
        jdbc.update("""
            INSERT dbo.lan_kiem_tra_tep(tep_dinh_kem_id,so_thu_tu,sha256_mong_doi,sha256_thuc_te,kieu_noi_dung_thuc_te,
                kich_thuoc_thuc_te,so_trang_pdf,pdf_co_mat_khau,trang_thai_toan_ven,trang_thai_cau_truc_pdf,
                phuong_phap_kiem_tra,phien_ban_cong_cu,nguoi_kiem_tra_id,thoi_diem_bat_dau,thoi_diem_ket_thuc)
            VALUES(?,1,?,?,'application/pdf',?,?,0,'verified','valid','pdfbox-sha256',?,?,?,?)
            """, id,a.hash(),a.hash(),a.bytes(),pages,org.apache.pdfbox.util.Version.getVersion(),a.owner(),now,now);
        jdbc.update("""
            INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,nguoi_dung_id,hanh_dong,
                loai_doi_tuong,doi_tuong_id,ma_lien_ket_yeu_cau,thoi_diem_tao)
            VALUES(NEWID(),'user',?,'upload_pdf','tep_dinh_kem',?,?,?)
            """, a.owner(),id.toString(),a.id().toString(),now);
        return new View(id.toString(), name, "application/pdf", Long.toString(a.bytes()), a.hash(),
            now.toInstant().toString(), "ready");
    }

    Optional<Stored> get(long file) {
        return jdbc.query("""
            SELECT id,nguoi_so_huu_id,kich_thuoc_tep,sha256,ten_tep,trang_thai_luu_tru,ten_cloud,
                   ma_tai_nguyen,loai_tai_nguyen,kieu_phan_phoi,thoi_diem_xoa
            FROM dbo.tep_dinh_kem WHERE id=?
            """, (r,n) -> new Stored(r.getLong(1),r.getObject(2,Long.class),r.getLong(3),r.getString(4),r.getString(5),
                r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getObject(11)!=null),file)
            .stream().findFirst();
    }

    Links links(long file, Long student) {
        return jdbc.queryForObject("""
            DECLARE @file bigint=?, @student bigint=?;
            WITH applications AS (
                SELECT h.sinh_vien_id FROM dbo.mghp_tep_minh_chung f
                JOIN dbo.mghp_giay_to_ho_so g ON g.id=f.giay_to_ho_so_id
                JOIN dbo.mghp_phien_ban_ho_so v ON v.id=g.phien_ban_ho_so_id
                JOIN dbo.mghp_ho_so h ON h.id=v.ho_so_id WHERE f.tep_dinh_kem_id=@file
            ), templates AS (
                SELECT d.trang_thai FROM dbo.mghp_chinh_sach_dot_xet b
                JOIN dbo.mghp_dot_xet d ON d.id=b.dot_xet_id WHERE b.tep_mau_don_id=@file
            )
            SELECT
                CASE WHEN EXISTS(SELECT 1 FROM applications) OR EXISTS(SELECT 1 FROM templates)
                    OR EXISTS(SELECT 1 FROM dbo.phien_ban_van_ban WHERE tep_goc_id=@file)
                    OR EXISTS(SELECT 1 FROM dbo.buoc_ky WHERE tep_anh_chu_ky_id=@file)
                    OR EXISTS(SELECT 1 FROM dbo.uy_quyen_ky WHERE tep_minh_chung_id=@file)
                    OR EXISTS(SELECT 1 FROM dbo.tep_ket_qua_ky WHERE tep_dinh_kem_id=@file)
                    OR EXISTS(SELECT 1 FROM dbo.qua_trinh_theo_hoc WHERE tep_can_cu_id=@file)
                    OR EXISTS(SELECT 1 FROM dbo.lan_goi_dich_vu_ky WHERE tep_dau_vao_id=@file)
                THEN 1 ELSE 0 END,
                CASE WHEN EXISTS(SELECT 1 FROM applications WHERE sinh_vien_id=@student) THEN 1 ELSE 0 END,
                CASE WHEN EXISTS(SELECT 1 FROM applications) THEN 1 ELSE 0 END,
                CASE WHEN EXISTS(SELECT 1 FROM templates WHERE trang_thai IN ('open','closed','completed')) THEN 1 ELSE 0 END,
                CASE WHEN EXISTS(SELECT 1 FROM templates) THEN 1 ELSE 0 END
            """, (r,n) -> new Links(r.getBoolean(1),r.getBoolean(2),r.getBoolean(3),r.getBoolean(4),r.getBoolean(5)),file,student);
    }
}
