package pbl6.ctsv.drl;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Synthetic SQL metadata only; no real storage or CA call is made by these tests. */
final class DrlDossierFixture {
    static final String HASH = "a".repeat(64);

    record ClassData(long round, long facultyRound, long root, long first, long second, long meeting, long spareStudent, long criterion) {}
    record Original(long link, long version, long document, Long file) {}

    private DrlDossierFixture() {}

    static ClassData create(JdbcTemplate jdbc) {
        String code = "D02-" + UUID.randomUUID();
        long term = insert(jdbc, "INSERT dbo.hoc_ky (ma) VALUES (?)", code);
        long faculty = insert(jdbc, "INSERT dbo.don_vi (ma) VALUES (?)", code);
        long classroom = insert(jdbc, "INSERT dbo.lop_sinh_hoat (ma, khoa_quan_ly_id) VALUES (?, ?)", code, faculty);
        long form = insert(jdbc, """
                INSERT dbo.RLBieuMau (MaBieuMau, SoPhienBan, TenBieuMau, DiemToiDa)
                VALUES (?, 1, N'Synthetic DRL-02.1', 100)
                """, code);
        long group = insert(jdbc, """
                INSERT dbo.RLNhomTieuChi (BieuMauID,TenNhom,ThuTu,DiemToiDa) VALUES (?,N'Test',1,100)
                """, form);
        long criterion = insert(jdbc, """
                INSERT dbo.RLBieuMauCT (NhomTieuChiID,MaNoiDung,LoaiDong,NoiDung,ThuTu,DiemToiDa)
                VALUES (?,N'SCORE',0,N'Test score',1,100)
                """, group);
        jdbc.update("INSERT dbo.RLXepLoai (BieuMauID, DiemToiThieu, TenXepLoai) VALUES (?, 0, N'Test')", form);
        long round = insert(jdbc, """
                INSERT dbo.RLDot (MaDot, TenDot, HocKyID, BieuMauID, BatDauSV, KetThucSV,
                    BatDauLop, KetThucLop, BatDauKhoa, KetThucKhoa)
                SELECT ?, N'Synthetic DRL-02.1', ?, ?, DATEADD(day,-2,t), DATEADD(day,-1,t),
                    t, DATEADD(day,1,t), t, DATEADD(day,2,t)
                FROM (VALUES (SYSDATETIMEOFFSET())) AS schedule(t)
                """, code, term, form);
        long facultyRound = insert(jdbc, "INSERT dbo.RLKhoaDot (DotID, KhoaID) VALUES (?, ?)", round, faculty);
        long root = insert(jdbc, "INSERT dbo.RLLopDot (KhoaDotID, LopID) VALUES (?, ?)", facultyRound, classroom);
        long firstStudent = student(jdbc, classroom);
        long secondStudent = student(jdbc, classroom);
        long spareStudent = student(jdbc, classroom);
        long first = insert(jdbc, "INSERT dbo.RLPhieu (LopDotID, SinhVienID, ThamDuHop) VALUES (?, ?, 1)", root, firstStudent);
        long second = insert(jdbc, "INSERT dbo.RLPhieu (LopDotID, SinhVienID, ThamDuHop) VALUES (?, ?, 0)", root, secondStudent);
        long meeting = insert(jdbc, "INSERT dbo.RLHopLop (LopDotID, NoiDung) VALUES (?, N'Before completion')", root);
        return new ClassData(round, facultyRound, root, first, second, meeting, spareStudent, criterion);
    }

    static long anotherRoot(JdbcTemplate jdbc, ClassData data) {
        long classroom = insert(jdbc, """
                INSERT dbo.lop_sinh_hoat (ma, khoa_quan_ly_id)
                SELECT ?, KhoaID FROM dbo.RLKhoaDot WHERE ID = ?
                """, "D02-" + UUID.randomUUID(), data.facultyRound());
        return insert(jdbc, "INSERT dbo.RLLopDot (KhoaDotID, LopID) VALUES (?, ?)", data.facultyRound(), classroom);
    }

    private static long student(JdbcTemplate jdbc, long classroom) {
        return insert(jdbc, "INSERT dbo.sinh_vien (mssv, lop_hien_tai_id) VALUES (?, ?)", "D02-" + UUID.randomUUID(), classroom);
    }

    static Original original(JdbcTemplate jdbc, long root, int type) {
        return original(jdbc, root, type, null, true, HASH, true, "available");
    }

    static Original original(JdbcTemplate jdbc, long root, int type, Long document,
                             boolean closed, String snapshotHash, boolean hasFile, String storageStatus) {
        long owner = document == null ? insert(jdbc, "INSERT dbo.van_ban (ma_van_ban) VALUES (NEWID())") : document;
        Long file = hasFile ? insert(jdbc, """
                INSERT dbo.tep_dinh_kem (ten_tep, kieu_noi_dung, trang_thai_luu_tru, sha256)
                VALUES (N'synthetic-drl.pdf', N'application/pdf', ?, ?)
                """, storageStatus, HASH) : null;
        long version = insert(jdbc, """
                INSERT dbo.phien_ban_van_ban (van_ban_id, so_phien_ban, tep_goc_id, sha256_tap_du_lieu, thoi_diem_chot)
                VALUES (?, ?, ?, ?, CASE WHEN ? = 1 THEN SYSDATETIMEOFFSET() END)
                """, owner, type, file, snapshotHash, closed ? 1 : 0);
        long link = insert(jdbc, "INSERT dbo.RLVanBanKy (LopDotID, LoaiVanBan, PhienBanVanBanID) VALUES (?, ?, ?)", root, type, version);
        return new Original(link, version, owner, file);
    }

    static void pair(JdbcTemplate jdbc, long root) {
        original(jdbc, root, 1);
        original(jdbc, root, 2);
    }

    static void complete(JdbcTemplate jdbc, long root) {
        jdbc.update("UPDATE dbo.RLLopDot SET NgayLapHoSo = SYSDATETIMEOFFSET() WHERE ID = ?", root);
    }

    static long insert(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.queryForObject("SET NOCOUNT ON; " + sql + "; SELECT CONVERT(bigint, SCOPE_IDENTITY())", Long.class, args);
    }
}
