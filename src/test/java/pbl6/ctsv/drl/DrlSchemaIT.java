package pbl6.ctsv.drl;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DrlSchemaIT {
    private static DataSource fresh;

    @BeforeAll
    static void migrateFreshDatabase() throws Exception {
        fresh = DrlTestDatabase.source(DrlTestDatabase.FRESH);
        DrlTestDatabase.flyway(fresh, DrlTestDatabase.TARGET).migrate();
    }

    @Test
    void createsTheAgreedTablesAndResultView() {
        var jdbc = new JdbcTemplate(fresh);
        assertThat(jdbc.queryForList("""
                SELECT name FROM sys.tables WHERE schema_id = SCHEMA_ID('dbo') AND name LIKE 'RL%'
                """, String.class)).containsExactlyInAnyOrder(
                "RLBieuMau", "RLNhomTieuChi", "RLBieuMauCT", "RLXepLoai", "RLDot", "RLKhoaDot",
                "RLLopDot", "RLPhieu", "RLPhieuDiemCT", "RLHopLop", "RLVanBanKy");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys.columns c JOIN sys.tables t ON t.object_id = c.object_id
                WHERE t.schema_id = SCHEMA_ID('dbo') AND t.name LIKE 'RL%'
                """, Integer.class)).isEqualTo(75); // V17's 74 columns + V26 RLLopDot.NgayLapHoSo.
        assertThat(jdbc.queryForList("""
                SELECT name FROM sys.columns WHERE object_id = OBJECT_ID('dbo.RLKetQuaSV', 'V')
                ORDER BY column_id
                """, String.class)).containsExactly("PhieuID", "SinhVienID", "LopDotID", "DotID",
                "HocKyID", "TongDiemCN", "TongDiemLop", "XepLoai", "DaCongBo");
        var flyway = DrlTestDatabase.flyway(fresh, DrlTestDatabase.TARGET);
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void ratingThresholdsAndCriterionScoresHaveIdentityKeysAndUniqueBusinessKeys() {
        var jdbc = new JdbcTemplate(fresh);
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(t.name COLLATE DATABASE_DEFAULT, '/', k.type COLLATE DATABASE_DEFAULT, '/',
                       STRING_AGG(CONCAT(c.name COLLATE DATABASE_DEFAULT,
                           CASE WHEN c.is_identity = 1 THEN ' IDENTITY' ELSE '' END), ',')
                           WITHIN GROUP (ORDER BY ic.key_ordinal))
                FROM sys.key_constraints k
                JOIN sys.tables t ON t.object_id = k.parent_object_id
                JOIN sys.index_columns ic ON ic.object_id = t.object_id AND ic.index_id = k.unique_index_id
                JOIN sys.columns c ON c.object_id = t.object_id AND c.column_id = ic.column_id
                WHERE t.schema_id = SCHEMA_ID('dbo') AND t.name IN ('RLXepLoai', 'RLPhieuDiemCT')
                  AND ic.key_ordinal > 0
                GROUP BY t.name, k.object_id, k.type
                """, String.class)).containsExactlyInAnyOrder(
                "RLXepLoai/PK/ID IDENTITY", "RLXepLoai/UQ/BieuMauID,DiemToiThieu",
                "RLPhieuDiemCT/PK/ID IDENTITY", "RLPhieuDiemCT/UQ/PhieuID,BieuMauCTID");
    }

    @Test
    void upgradesFromV25PreservingExistingMigrationsAndDrlData() throws Exception {
        var source = DrlTestDatabase.source(DrlTestDatabase.UPGRADE);
        var jdbc = new JdbcTemplate(source);
        boolean alreadyUpgraded = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sys.columns WHERE object_id = OBJECT_ID('dbo.RLLopDot') AND name='NgayLapHoSo'
                """, Integer.class) == 1;
        if (!alreadyUpgraded) {
            DrlTestDatabase.flyway(source, "25").migrate();
            try (var connection = source.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    var fixtureJdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                    var probe = DrlDossierFixture.create(fixtureJdbc);
                    DrlDossierFixture.pair(fixtureJdbc, probe.root());
                    // Retain synthetic legacy records to verify the real V25 -> V26 boundary.
                    connection.commit();
                } finally {
                    connection.rollback();
                }
            }
        }
        String earlierHistory = """
                SELECT * FROM dbo.flyway_schema_history
                WHERE TRY_CONVERT(int, version) BETWEEN 1 AND 25 ORDER BY installed_rank
                """;
        var before = jdbc.queryForList(earlierHistory);
        assertThat(before).hasSize(25);
        var rowsBefore = legacyDrlRows(jdbc);
        int completedBefore = alreadyUpgraded
                ? jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RLLopDot WHERE NgayLapHoSo IS NOT NULL", Integer.class) : 0;
        var flyway = DrlTestDatabase.flyway(source, DrlTestDatabase.DOSSIER_FREEZE_TARGET);
        var result = flyway.migrate();
        assertThat(result.migrationsExecuted).isEqualTo(alreadyUpgraded ? 0 : 1);
        flyway.validate();
        assertThat(jdbc.queryForList(earlierHistory)).isEqualTo(before);
        assertThat(legacyDrlRows(jdbc)).isEqualTo(rowsBefore);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version = '17' AND success = 1
                """, Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.flyway_schema_history WHERE version='26' AND success=1
                """, Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RLLopDot WHERE NgayLapHoSo IS NOT NULL", Integer.class)).isEqualTo(completedBefore);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private static Map<String, List<Map<String, Object>>> legacyDrlRows(JdbcTemplate jdbc) {
        var snapshot = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("RLBieuMau", "RLNhomTieuChi", "RLBieuMauCT", "RLXepLoai", "RLDot",
                "RLKhoaDot", "RLLopDot", "RLPhieu", "RLPhieuDiemCT", "RLHopLop", "RLVanBanKy")) {
            var columns = jdbc.queryForList("""
                    SELECT QUOTENAME(name) FROM sys.columns
                    WHERE object_id=OBJECT_ID(?) AND name<>'NgayLapHoSo' ORDER BY column_id
                    """, String.class, "dbo." + table);
            snapshot.put(table, jdbc.queryForList("SELECT " + String.join(",", columns) + " FROM dbo." + table + " ORDER BY ID"));
        }
        return snapshot;
    }

    @Test
    void exposesEachClassScoreOnSaveAndPublishesAllStudentsTogether() throws Exception {
        withBallots((jdbc, b) -> {
            assertThat(result(jdbc, b.first())).isEqualTo(new Result(null, null, null, false));
            jdbc.update("""
                    INSERT dbo.RLPhieuDiemCT (PhieuID, BieuMauCTID, DiemCN) VALUES (?, ?, 64.75)
                    """, b.first(), b.criterion());
            assertThat(result(jdbc, b.first()).individual()).isNull();
            jdbc.update("UPDATE dbo.RLPhieu SET XacNhanSV = 1, NgayXNSV = SYSDATETIMEOFFSET() WHERE ID = ?", b.first());
            assertThat(result(jdbc, b.first()).individual()).isEqualByComparingTo("64.75");

            jdbc.update("""
                    UPDATE dbo.RLDot SET KetThucSV = DATEADD(minute, -1, SYSDATETIMEOFFSET()),
                        BatDauLop = SYSDATETIMEOFFSET() WHERE ID = ?
                    """, b.round());
            assertThat(result(jdbc, b.second()).individual()).isEqualByComparingTo("0");
            jdbc.update("UPDATE dbo.RLPhieuDiemCT SET DiemLop = 65.25 WHERE PhieuID = ?", b.first());
            saveClassScore(jdbc, b.first());
            assertThat(result(jdbc, b.first())).isEqualTo(
                    new Result(new BigDecimal("64.75"), new BigDecimal("65.25"), "Khá", false));
            assertThat(result(jdbc, b.second()).classScore()).isNull();
            jdbc.update("""
                    INSERT dbo.RLPhieuDiemCT (PhieuID, BieuMauCTID, DiemLop) VALUES (?, ?, 0)
                    """, b.second(), b.criterion());
            saveClassScore(jdbc, b.second());
            assertThat(result(jdbc, b.second()).rating()).isEqualTo("Kém");

            long officer = insert(jdbc, "INSERT dbo.nguoi_dung (dia_chi_email) VALUES (?)",
                    UUID.randomUUID() + "@example.test");
            jdbc.update("UPDATE dbo.RLLopDot SET NgayGuiKhoa = SYSDATETIMEOFFSET() WHERE ID = ?", b.roundClass());
            jdbc.update("UPDATE dbo.RLKhoaDot SET NgayGuiCTSV = SYSDATETIMEOFFSET() WHERE ID = ?", b.facultyRound());
            jdbc.update("""
                    UPDATE dbo.RLLopDot SET NguoiXNCTSVID = ?, NgayXNCTSV = SYSDATETIMEOFFSET() WHERE ID = ?
                    """, officer, b.roundClass());
            jdbc.update("UPDATE dbo.RLDot SET TrangThai = 2 WHERE ID = ?", b.round());
            assertThat(result(jdbc, b.first()).published()).isTrue();
            assertThat(result(jdbc, b.second())).isEqualTo(
                    new Result(new BigDecimal("0.00"), new BigDecimal("0.00"), "Kém", true));
        });
    }

    @Test
    void rejectsPublicationBeforeCtsvConfirmsTheClass() throws Exception {
        withBallots((jdbc, b) -> assertThatThrownBy(() ->
                jdbc.update("UPDATE dbo.RLDot SET TrangThai = 2 WHERE ID = ?", b.round()))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("CTSV confirmation"));
    }

    @Test
    void rejectsScoresOnGuidanceRowsAndOverTheCriterionMaximum() throws Exception {
        withBallots((jdbc, b) -> assertInvalidDraft(jdbc, b.first(), b.guidance(), 1));
        withBallots((jdbc, b) -> assertInvalidDraft(jdbc, b.first(), b.criterion(), 81));
    }

    @Test
    void checksGroupTotalsOnMultiRowDraftSaves() throws Exception {
        withBallots((jdbc, b) -> assertThatThrownBy(() -> jdbc.update("""
                INSERT dbo.RLPhieuDiemCT (PhieuID, BieuMauCTID, DiemCN) VALUES (?, ?, 75), (?, ?, 20)
                """, b.first(), b.criterion(), b.first(), b.secondCriterion()))
                .isInstanceOf(DataAccessException.class).hasStackTraceContaining("group or ballot maximum"));
    }

    @Test
    void rejectsTheSameStudentInAnotherClassOfTheRound() throws Exception {
        withBallots((jdbc, b) -> {
            long otherClass = insert(jdbc, """
                    INSERT dbo.lop_sinh_hoat (ma, khoa_quan_ly_id)
                    SELECT ?, KhoaID FROM dbo.RLKhoaDot WHERE ID = ?
                    """, UUID.randomUUID().toString(), b.facultyRound());
            long otherRoundClass = insert(jdbc, "INSERT dbo.RLLopDot (KhoaDotID, LopID) VALUES (?, ?)",
                    b.facultyRound(), otherClass);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT dbo.RLPhieu (LopDotID, SinhVienID) SELECT ?, SinhVienID FROM dbo.RLPhieu WHERE ID = ?
                    """, otherRoundClass, b.first())).isInstanceOf(DataAccessException.class)
                    .hasStackTraceContaining("already has a ballot");
        });
    }

    @Test
    void classClosureLocksTheSavedScores() throws Exception {
        withBallots((jdbc, b) -> {
            jdbc.update("""
                    INSERT dbo.RLPhieuDiemCT (PhieuID, BieuMauCTID, DiemLop) VALUES (?, ?, 70), (?, ?, 60)
                    """, b.first(), b.criterion(), b.second(), b.criterion());
            saveClassScore(jdbc, b.first());
            saveClassScore(jdbc, b.second());
            jdbc.update("""
                    INSERT dbo.nhat_ky_thao_tac (su_kien_id, hanh_dong, loai_doi_tuong, doi_tuong_id)
                    VALUES (NEWID(), N'RL_CHOT_LOP', N'RLLopDot', CONVERT(nvarchar(128), ?))
                    """, b.roundClass());
            assertThatThrownBy(() -> jdbc.update("UPDATE dbo.RLPhieuDiemCT SET DiemLop = 75 WHERE PhieuID = ?", b.first()))
                    .isInstanceOf(DataAccessException.class).hasStackTraceContaining("class scores are closed");
        });
    }

    @Test
    void linksBothClassDocumentsToDistinctSharedVersions() throws Exception {
        withBallots((jdbc, b) -> {
            insert(jdbc, "INSERT dbo.RLHopLop (LopDotID) VALUES (?)", b.roundClass());
            for (int type = 1; type <= 2; type++) {
                long document = insert(jdbc, "INSERT dbo.van_ban (ma_van_ban) VALUES (NEWID())");
                long version = insert(jdbc, "INSERT dbo.phien_ban_van_ban (van_ban_id, so_phien_ban) VALUES (?, 1)", document);
                jdbc.update("INSERT dbo.RLVanBanKy (LopDotID, LoaiVanBan, PhienBanVanBanID) VALUES (?, ?, ?)",
                        b.roundClass(), type, version);
            }
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(DISTINCT v.van_ban_id) FROM dbo.RLVanBanKy k
                    JOIN dbo.phien_ban_van_ban v ON v.id = k.PhienBanVanBanID WHERE k.LopDotID = ?
                    """, Integer.class, b.roundClass())).isEqualTo(2);
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE dbo.RLVanBanKy SET PhienBanVanBanID = -1 WHERE LopDotID = ? AND LoaiVanBan = 2
                    """, b.roundClass())).isInstanceOf(DataAccessException.class).hasStackTraceContaining("phien_ban_van_ban");
        });
    }

    private static void assertInvalidDraft(JdbcTemplate jdbc, long ballot, long criterion, int score) {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT dbo.RLPhieuDiemCT (PhieuID, BieuMauCTID, DiemCN) VALUES (?, ?, ?)
                """, ballot, criterion, score)).isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("invalid criterion");
    }

    private static void saveClassScore(JdbcTemplate jdbc, long ballot) {
        jdbc.update("UPDATE dbo.RLPhieu SET XacNhanLop = 1, NgayXNLop = SYSDATETIMEOFFSET() WHERE ID = ?", ballot);
    }

    private static Result result(JdbcTemplate jdbc, long ballot) {
        return jdbc.queryForObject("""
                SELECT TongDiemCN, TongDiemLop, XepLoai, DaCongBo FROM dbo.RLKetQuaSV WHERE PhieuID = ?
                """, (row, index) -> new Result(row.getBigDecimal(1), row.getBigDecimal(2),
                row.getString(3), row.getBoolean(4)), ballot);
    }

    private static void withBallots(BiConsumer<JdbcTemplate, Ballots> scenario) throws Exception {
        try (var connection = fresh.getConnection()) {
            connection.setAutoCommit(false);
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            try {
                scenario.accept(jdbc, createBallots(jdbc));
            } finally {
                connection.rollback();
            }
        }
    }

    private static Ballots createBallots(JdbcTemplate jdbc) {
        String code = UUID.randomUUID().toString();
        long term = insert(jdbc, "INSERT dbo.hoc_ky (ma) VALUES (?)", code);
        long unit = insert(jdbc, "INSERT dbo.don_vi (ma) VALUES (?)", code);
        long classroom = insert(jdbc, "INSERT dbo.lop_sinh_hoat (ma, khoa_quan_ly_id) VALUES (?, ?)", code, unit);
        long form = insert(jdbc, """
                INSERT dbo.RLBieuMau (MaBieuMau, SoPhienBan, TenBieuMau, DiemToiDa) VALUES (?, 1, N'ĐRL', 100)
                """, code);
        long group = insert(jdbc, """
                INSERT dbo.RLNhomTieuChi (BieuMauID, TenNhom, ThuTu, DiemToiDa) VALUES (?, N'Nhóm I', 1, 90)
                """, form);
        String criterionSql = """
                INSERT dbo.RLBieuMauCT (NhomTieuChiID, MaNoiDung, LoaiDong, NoiDung, ThuTu, DiemToiDa)
                VALUES (?, ?, 0, N'Tiêu chí chấm', ?, ?)
                """;
        long criterion = insert(jdbc, criterionSql, group, "C1", 1, 80);
        long secondCriterion = insert(jdbc, criterionSql, group, "C2", 2, 40);
        long guidance = insert(jdbc, """
                INSERT dbo.RLBieuMauCT (NhomTieuChiID, MaNoiDung, LoaiDong, TieuChiChaID, NoiDung, ThuTu)
                VALUES (?, 'G1', 1, ?, N'Hướng dẫn', 3)
                """, group, criterion);
        jdbc.update("INSERT dbo.RLXepLoai (BieuMauID, DiemToiThieu, TenXepLoai) VALUES (?, 0, N'Kém'), (?, 65, N'Khá')",
                form, form);
        long round = insert(jdbc, """
                INSERT dbo.RLDot (MaDot, TenDot, HocKyID, BieuMauID, BatDauSV, KetThucSV, BatDauLop,
                                  KetThucLop, BatDauKhoa, KetThucKhoa)
                SELECT ?, N'Đợt ĐRL', ?, ?, DATEADD(day, -1, t), DATEADD(day, 1, t), DATEADD(day, 1, t),
                       DATEADD(day, 3, t), DATEADD(day, 2, t), DATEADD(day, 5, t)
                FROM (VALUES (SYSDATETIMEOFFSET())) AS schedule(t)
                """, code, term, form);
        long facultyRound = insert(jdbc, "INSERT dbo.RLKhoaDot (DotID, KhoaID) VALUES (?, ?)", round, unit);
        long roundClass = insert(jdbc, "INSERT dbo.RLLopDot (KhoaDotID, LopID) VALUES (?, ?)", facultyRound, classroom);
        long first = createBallot(jdbc, code + "-1", classroom, roundClass);
        long second = createBallot(jdbc, code + "-2", classroom, roundClass);
        jdbc.update("UPDATE dbo.RLDot SET TrangThai = 1 WHERE ID = ?", round);
        return new Ballots(round, facultyRound, roundClass, first, second, criterion, secondCriterion, guidance);
    }

    private static long createBallot(JdbcTemplate jdbc, String studentCode, long classroom, long roundClass) {
        long student = insert(jdbc, "INSERT dbo.sinh_vien (mssv, lop_hien_tai_id) VALUES (?, ?)", studentCode, classroom);
        return insert(jdbc, "INSERT dbo.RLPhieu (LopDotID, SinhVienID) VALUES (?, ?)", roundClass, student);
    }

    private static long insert(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.queryForObject("SET NOCOUNT ON; " + sql + "; SELECT CONVERT(bigint, SCOPE_IDENTITY())",
                Long.class, args);
    }

    private record Ballots(long round, long facultyRound, long roundClass, long first, long second,
                           long criterion, long secondCriterion, long guidance) {}

    private record Result(BigDecimal individual, BigDecimal classScore, String rating, boolean published) {}
}
