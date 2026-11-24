package pbl6.ctsv.drl;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static pbl6.ctsv.drl.DrlDossierFixture.*;

/** D14-01/02: real SQL Server constraints and locks, not PDF/storage/CA acceptance. */
class DrlDossierFreezeIT {
    private static DataSource source;

    @BeforeAll
    static void migrate() throws Exception {
        source = DrlTestDatabase.source(DrlTestDatabase.FRESH);
        DrlTestDatabase.flyway(source, DrlTestDatabase.TARGET).migrate();
    }

    @Test
    void addsOnlyTheAgreedNullableCompletionColumnAndFourGuards() {
        var jdbc = new JdbcTemplate(source);
        var column = jdbc.queryForMap("""
                SELECT TYPE_NAME(user_type_id) AS kind, scale, is_nullable
                FROM sys.columns WHERE object_id=OBJECT_ID('dbo.RLLopDot') AND name='NgayLapHoSo'
                """);
        assertThat(column).containsEntry("kind", "datetimeoffset").containsEntry("is_nullable", true);
        assertThat(((Number) column.get("scale")).intValue()).isEqualTo(7);
        assertThat(jdbc.queryForList("""
                SELECT name FROM sys.triggers WHERE name IN
                ('TR_RLLopDot_HoSo','TR_RLHopLop_HoSo','TR_RLPhieu_HoSo','TR_RLVanBanKy_HoSo') AND is_disabled=0
                """, String.class)).hasSize(4);
    }

    enum InvalidPair { NONE, ONE, SAME_DOCUMENT, UNCLOSED, NULL_HASH, DIFFERENT_HASH, NO_ORIGINAL_FILE, UNAVAILABLE, NULL_STORAGE_STATUS }

    @ParameterizedTest
    @EnumSource(InvalidPair.class)
    void rejectsCompletionUnlessBothStoredOriginalsIdentifyOneSnapshot(InvalidPair invalid) throws Exception {
        withFixture((jdbc, data) -> {
            if (invalid == InvalidPair.NONE) {
                rejects(51716, () -> complete(jdbc, data.root()));
                return;
            }
            var first = original(jdbc, data.root(), 1);
            if (invalid != InvalidPair.ONE) {
                original(jdbc, data.root(), 2,
                        invalid == InvalidPair.SAME_DOCUMENT ? first.document() : null,
                        invalid != InvalidPair.UNCLOSED,
                        invalid == InvalidPair.NULL_HASH ? null : invalid == InvalidPair.DIFFERENT_HASH ? "b".repeat(64) : HASH,
                        invalid != InvalidPair.NO_ORIGINAL_FILE,
                        invalid == InvalidPair.NULL_STORAGE_STATUS ? null : invalid == InvalidPair.UNAVAILABLE ? "pending" : "available");
            }
            rejects(51716, () -> complete(jdbc, data.root()));
        });
    }

    @Test
    void acceptsSequentialDocumentInsertsAndThenCompletionInOneTransaction() throws Exception {
        withFixture((jdbc, data) -> {
            original(jdbc, data.root(), 1);
            assertThat(jdbc.queryForObject("SELECT NgayLapHoSo FROM dbo.RLLopDot WHERE ID=?", Object.class, data.root())).isNull();
            original(jdbc, data.root(), 2);
            complete(jdbc, data.root());
            assertThat(jdbc.queryForObject("SELECT NgayLapHoSo FROM dbo.RLLopDot WHERE ID=?", Object.class, data.root())).isNotNull();
            // Completion protects content, not later handover/confirmation columns on the root.
            assertThat(jdbc.update("UPDATE dbo.RLLopDot SET NgayGuiKhoa=SYSDATETIMEOFFSET(), NgayLapHoSo=NgayLapHoSo WHERE ID=?", data.root())).isOne();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULL", "DATEADD(second,1,NgayLapHoSo)"})
    void completionCannotBeResetOrChanged(String expression) throws Exception {
        withFixture((jdbc, data) -> {
            pair(jdbc, data.root());
            complete(jdbc, data.root());
            rejects(51716, () -> jdbc.update("UPDATE dbo.RLLopDot SET NgayLapHoSo=" + expression + " WHERE ID=?", data.root()));
        });
    }

    @Test
    void existingForeignKeysAlsoPreventDeletingACompletedClass() throws Exception {
        withFixture((jdbc, data) -> {
            pair(jdbc, data.root());
            complete(jdbc, data.root());
            // SQL Server checks FKs before AFTER DELETE triggers; do not disable
            // constraints just to force the later 51716 backstop to run.
            rejects(547, () -> jdbc.update("DELETE dbo.RLLopDot WHERE ID=?", data.root()));
        });
    }

    @Test
    void validatesEveryRootInAMultiRowCompletion() throws Exception {
        withFixture((jdbc, data) -> {
            pair(jdbc, data.root());
            long incomplete = anotherRoot(jdbc, data);
            original(jdbc, incomplete, 1);
            rejects(51716, () -> jdbc.update("UPDATE dbo.RLLopDot SET NgayLapHoSo=SYSDATETIMEOFFSET() WHERE ID IN (?,?)", data.root(), incomplete));
        });
    }

    enum FrozenWrite { MEETING_EDIT, MEETING_DELETE, MEETING_MOVE_OUT, ATTENDANCE, ATTENDANCE_TO_NULL,
        NOT_EVALUATED, BALLOT_INSERT, BALLOT_DELETE, STUDENT_CONFIRMATION, STUDENT_CONFIRMATION_TIME,
        CLASS_CONFIRMATION, CLASS_CONFIRMATION_TIME, BALLOT_NO_OP,
        DOCUMENT_DELETE, DOCUMENT_RELINK, DOCUMENT_MOVE_OUT }

    @ParameterizedTest
    @EnumSource(FrozenWrite.class)
    void freezesOnlyTheOriginalDossierFacts(FrozenWrite operation) throws Exception {
        withFixture((jdbc, data) -> {
            var first = original(jdbc, data.root(), 1);
            original(jdbc, data.root(), 2);
            long other = anotherRoot(jdbc, data);
            var replacement = original(jdbc, other, 1);
            if (operation == FrozenWrite.STUDENT_CONFIRMATION_TIME) {
                jdbc.update("UPDATE dbo.RLPhieu SET XacNhanSV=1,NgayXNSV=SYSDATETIMEOFFSET() WHERE ID=?", data.first());
            }
            if (operation == FrozenWrite.CLASS_CONFIRMATION_TIME) {
                jdbc.update("UPDATE dbo.RLPhieu SET XacNhanLop=1,NgayXNLop=SYSDATETIMEOFFSET() WHERE ID=?", data.first());
            }
            complete(jdbc, data.root());
            rejects(51716, () -> {
                switch (operation) {
                    case MEETING_EDIT -> jdbc.update("UPDATE dbo.RLHopLop SET NoiDung=N'changed' WHERE ID=?", data.meeting());
                    case MEETING_DELETE -> jdbc.update("DELETE dbo.RLHopLop WHERE ID=?", data.meeting());
                    case MEETING_MOVE_OUT -> jdbc.update("UPDATE dbo.RLHopLop SET LopDotID=? WHERE ID=?", other, data.meeting());
                    case ATTENDANCE -> jdbc.update("UPDATE dbo.RLPhieu SET ThamDuHop=2 WHERE ID=?", data.first());
                    case ATTENDANCE_TO_NULL -> jdbc.update("UPDATE dbo.RLPhieu SET ThamDuHop=NULL WHERE ID=?", data.first());
                    case NOT_EVALUATED -> jdbc.update("UPDATE dbo.RLPhieu SET KhongDanhGia=1 WHERE ID=?", data.first());
                    case BALLOT_INSERT -> jdbc.update("INSERT dbo.RLPhieu (LopDotID,SinhVienID) VALUES (?,?)", data.root(), data.spareStudent());
                    case BALLOT_DELETE -> jdbc.update("DELETE dbo.RLPhieu WHERE ID=?", data.first());
                    case STUDENT_CONFIRMATION -> jdbc.update("UPDATE dbo.RLPhieu SET XacNhanSV=1,NgayXNSV=SYSDATETIMEOFFSET() WHERE ID=?", data.first());
                    case STUDENT_CONFIRMATION_TIME -> jdbc.update("UPDATE dbo.RLPhieu SET NgayXNSV=DATEADD(second,1,NgayXNSV) WHERE ID=?", data.first());
                    case CLASS_CONFIRMATION -> jdbc.update("UPDATE dbo.RLPhieu SET XacNhanLop=1,NgayXNLop=SYSDATETIMEOFFSET() WHERE ID=?", data.first());
                    case CLASS_CONFIRMATION_TIME -> jdbc.update("UPDATE dbo.RLPhieu SET NgayXNLop=DATEADD(second,1,NgayXNLop) WHERE ID=?", data.first());
                    case BALLOT_NO_OP -> jdbc.update("UPDATE dbo.RLPhieu SET XacNhanLop=XacNhanLop WHERE ID=?", data.first());
                    case DOCUMENT_DELETE -> jdbc.update("DELETE dbo.RLVanBanKy WHERE ID=?", first.link());
                    case DOCUMENT_RELINK -> {
                        jdbc.update("DELETE dbo.RLVanBanKy WHERE ID=?", replacement.link());
                        jdbc.update("UPDATE dbo.RLVanBanKy SET PhienBanVanBanID=? WHERE ID=?", replacement.version(), first.link());
                    }
                    case DOCUMENT_MOVE_OUT -> jdbc.update("UPDATE dbo.RLVanBanKy SET LopDotID=? WHERE ID=?", anotherRoot(jdbc, data), first.link());
                }
            });
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"insert", "move"})
    void alsoRejectsNewMeetingFactsWhenACompletedRootHasNoMeetingRow(String operation) throws Exception {
        withFixture((jdbc, data) -> {
            jdbc.update("DELETE dbo.RLHopLop WHERE ID=?", data.meeting());
            long mutable = anotherRoot(jdbc, data);
            long otherMeeting = insert(jdbc, "INSERT dbo.RLHopLop (LopDotID) VALUES (?)", mutable);
            pair(jdbc, data.root());
            complete(jdbc, data.root());
            rejects(51716, () -> {
                if (operation.equals("insert")) jdbc.update("INSERT dbo.RLHopLop (LopDotID) VALUES (?)", data.root());
                else jdbc.update("UPDATE dbo.RLHopLop SET LopDotID=? WHERE ID=?", data.root(), otherMeeting);
            });
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"attendance", "meeting", "document", "ballot-insert", "ballot-delete"})
    void checksBothFrozenAndMutableRootsInMultiRowWrites(String kind) throws Exception {
        withFixture((jdbc, frozen) -> {
            var mutable = create(jdbc);
            pair(jdbc, frozen.root());
            pair(jdbc, mutable.root());
            complete(jdbc, frozen.root());
            rejects(51716, () -> {
                switch (kind) {
                    case "attendance" -> jdbc.update("UPDATE dbo.RLPhieu SET KhongDanhGia=1 WHERE LopDotID IN (?,?)", frozen.root(), mutable.root());
                    case "meeting" -> jdbc.update("UPDATE dbo.RLHopLop SET NoiDung=N'changed' WHERE LopDotID IN (?,?)", frozen.root(), mutable.root());
                    case "document" -> jdbc.update("DELETE dbo.RLVanBanKy WHERE LopDotID IN (?,?)", frozen.root(), mutable.root());
                    case "ballot-insert" -> jdbc.update("INSERT dbo.RLPhieu (LopDotID,SinhVienID) VALUES (?,?),(?,?)",
                            frozen.root(), frozen.spareStudent(), mutable.root(), mutable.spareStudent());
                    case "ballot-delete" -> jdbc.update("DELETE dbo.RLPhieu WHERE LopDotID IN (?,?)", frozen.root(), mutable.root());
                    default -> throw new AssertionError(kind);
                }
            });
        });
    }

    @Test
    void scoreClosureStillAllowsPartialMeetingAndAttendanceBeforeDossierCompletion() throws Exception {
        withFixture((jdbc, data) -> {
            jdbc.update("""
                    INSERT dbo.nhat_ky_thao_tac (su_kien_id,hanh_dong,loai_doi_tuong,doi_tuong_id)
                    VALUES (NEWID(),N'RL_CHOT_LOP',N'RLLopDot',CONVERT(nvarchar(128),?))
                    """, data.root());
            assertThat(jdbc.update("UPDATE dbo.RLHopLop SET DiaDiem=N'Room',NoiDung=NULL WHERE ID=?", data.meeting())).isOne();
            assertThat(jdbc.update("UPDATE dbo.RLPhieu SET ThamDuHop=NULL,KhongDanhGia=1 WHERE ID=?", data.first())).isOne();
            assertThat(jdbc.update("UPDATE dbo.RLPhieu SET ThamDuHop=2,KhongDanhGia=0 WHERE ID=?", data.first())).isOne();
            assertThat(jdbc.queryForObject("SELECT NgayLapHoSo FROM dbo.RLLopDot WHERE ID=?", Object.class, data.root())).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {51712, 51713})
    void preservesTheExistingClassAndIndividualScoreLocks(int error) throws Exception {
        withFixture((jdbc, data) -> {
            jdbc.update("INSERT dbo.RLPhieuDiemCT (PhieuID,BieuMauCTID,DiemCN,DiemLop) VALUES (?,?,50,50)", data.first(), data.criterion());
            if (error == 51712) {
                jdbc.update("""
                        INSERT dbo.nhat_ky_thao_tac (su_kien_id,hanh_dong,loai_doi_tuong,doi_tuong_id)
                        VALUES (NEWID(),N'RL_CHOT_LOP',N'RLLopDot',CONVERT(nvarchar(128),?))
                        """, data.root());
                rejects(error, () -> jdbc.update("UPDATE dbo.RLPhieuDiemCT SET DiemLop=60 WHERE PhieuID=?", data.first()));
            } else {
                jdbc.update("UPDATE dbo.RLPhieu SET XacNhanSV=1,NgayXNSV=SYSDATETIMEOFFSET() WHERE ID=?", data.first());
                assertThat(jdbc.update("UPDATE dbo.RLPhieuDiemCT SET DiemLop=60 WHERE PhieuID=?", data.first())).isOne();
                rejects(error, () -> jdbc.update("UPDATE dbo.RLPhieuDiemCT SET DiemCN=60 WHERE PhieuID=?", data.first()));
            }
        });
    }

    @Test
    void signingMayAppendReceiptsFilesResultsAndChecksWithoutReplacingOriginalVersions() throws Exception {
        withFixture((jdbc, data) -> {
            var original = original(jdbc, data.root(), 1);
            original(jdbc, data.root(), 2);
            complete(jdbc, data.root());
            long provider = insert(jdbc, "INSERT dbo.he_thong_ngoai (ma) VALUES (?)", "D02-" + UUID.randomUUID());
            long request = insert(jdbc, "INSERT dbo.yeu_cau_ky (ma_yeu_cau,phien_ban_van_ban_id) VALUES (NEWID(),?)", original.version());
            long step = insert(jdbc, "INSERT dbo.buoc_ky (yeu_cau_ky_id,thu_tu_ky) VALUES (?,1)", request);
            for (int position=1; position<=2; position++) {
                long receipt = insert(jdbc, """
                        INSERT dbo.bien_nhan_ky (yeu_cau_ky_id,buoc_ky_id,he_thong_cung_cap_id,chu_ky_ben_ngoai_id)
                        VALUES (?,?,?,NEWID())
                        """, request, step, provider);
                long file = insert(jdbc, "INSERT dbo.tep_dinh_kem (ten_tep,trang_thai_luu_tru,sha256) VALUES (N'signed.pdf',N'available',?)", HASH);
                long result = insert(jdbc, "INSERT dbo.tep_ket_qua_ky (yeu_cau_ky_id,bien_nhan_ky_id,tep_dinh_kem_id) VALUES (?,?,?)", request, receipt, file);
                long check = insert(jdbc, """
                        INSERT dbo.lan_kiem_tra_tep (tep_dinh_kem_id,tep_ket_qua_ky_id,sha256_mong_doi,sha256_thuc_te)
                        VALUES (?,?,?,?)
                        """, file, result, HASH, HASH);
                jdbc.update("UPDATE dbo.tep_ket_qua_ky SET lan_kiem_tra_tep_id=?,trang_thai_toan_ven=N'verified' WHERE id=?", check, result);
            }
            assertThat(jdbc.queryForObject("SELECT PhienBanVanBanID FROM dbo.RLVanBanKy WHERE ID=?", Long.class, original.link())).isEqualTo(original.version());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.tep_ket_qua_ky WHERE yeu_cau_ky_id=?", Integer.class, request)).isEqualTo(2);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"meeting", "attendance", "document"})
    @Timeout(30)
    void aConcurrentChildWriteWaitsForCompletionAndThenFails(String kind) throws Exception {
        ClassData data;
        // Separate SQL sessions need a committed fixture. It is retained only in
        // the allowlisted test DB because shared originals are append-only.
        try (var setup = source.getConnection()) {
            setup.setAutoCommit(false);
            var jdbc = jdbc(setup);
            data = create(jdbc);
            pair(jdbc, data.root());
            setup.commit();
        }
        try (var completion = source.getConnection(); var worker = Executors.newSingleThreadExecutor()) {
            completion.setAutoCommit(false);
            try {
                complete(jdbc(completion), data.root());
                int owner = jdbc(completion).queryForObject("SELECT @@SPID", Integer.class);
                var waitingSession = new CompletableFuture<Integer>();
                var write = worker.submit(() -> {
                    try (var child = source.getConnection()) {
                        child.setAutoCommit(false);
                        try {
                            var jdbc = jdbc(child);
                            waitingSession.complete(jdbc.queryForObject("SELECT @@SPID", Integer.class));
                            Throwable failure = catchThrowable(() -> {
                                switch (kind) {
                                    case "meeting" -> jdbc.update("UPDATE dbo.RLHopLop SET NoiDung=N'late' WHERE ID=?", data.meeting());
                                    case "attendance" -> jdbc.update("UPDATE dbo.RLPhieu SET ThamDuHop=2 WHERE ID=?", data.first());
                                    case "document" -> jdbc.update("DELETE dbo.RLVanBanKy WHERE LopDotID=?", data.root());
                                    default -> throw new AssertionError(kind);
                                }
                            });
                            return sqlError(failure);
                        } finally {
                            child.rollback();
                        }
                    }
                });
                int session = waitingSession.get(5, TimeUnit.SECONDS);
                awaitSqlBlock(session, owner);
                assertThat(write.isDone()).isFalse();
                completion.commit();
                assertThat(write.get(5, TimeUnit.SECONDS)).isEqualTo(51716);
                var observer = new JdbcTemplate(source);
                assertThat(observer.queryForObject("SELECT NoiDung FROM dbo.RLHopLop WHERE ID=?", String.class, data.meeting())).isEqualTo("Before completion");
                assertThat(observer.queryForObject("SELECT ThamDuHop FROM dbo.RLPhieu WHERE ID=?", Integer.class, data.first())).isOne();
                assertThat(observer.queryForObject("SELECT COUNT(*) FROM dbo.RLVanBanKy WHERE LopDotID=?", Integer.class, data.root())).isEqualTo(2);
            } finally {
                completion.rollback();
            }
        }
    }

    @Test
    @Timeout(30)
    void completionAlsoWaitsForAnEarlierMeetingWrite() throws Exception {
        ClassData data;
        try (var setup = source.getConnection()) {
            setup.setAutoCommit(false);
            var jdbc = jdbc(setup);
            data = create(jdbc);
            pair(jdbc, data.root());
            setup.commit();
        }
        try (var meeting = source.getConnection(); var worker = Executors.newSingleThreadExecutor()) {
            meeting.setAutoCommit(false);
            try {
                var jdbc = jdbc(meeting);
                jdbc.update("UPDATE dbo.RLHopLop SET NoiDung=N'pending edit' WHERE ID=?", data.meeting());
                int owner = jdbc.queryForObject("SELECT @@SPID", Integer.class);
                var waitingSession = new CompletableFuture<Integer>();
                var completion = worker.submit(() -> {
                    try (var connection = source.getConnection()) {
                        connection.setAutoCommit(false);
                        try {
                            waitingSession.complete(jdbc(connection).queryForObject("SELECT @@SPID", Integer.class));
                            complete(jdbc(connection), data.root());
                            connection.commit();
                            return true;
                        } finally {
                            connection.rollback();
                        }
                    }
                });
                awaitSqlBlock(waitingSession.get(5, TimeUnit.SECONDS), owner);
                assertThat(completion.isDone()).isFalse();
                meeting.rollback();
                assertThat(completion.get(5, TimeUnit.SECONDS)).isTrue();
                var observer = new JdbcTemplate(source);
                assertThat(observer.queryForObject("SELECT NoiDung FROM dbo.RLHopLop WHERE ID=?", String.class, data.meeting())).isEqualTo("Before completion");
                assertThat(observer.queryForObject("SELECT NgayLapHoSo FROM dbo.RLLopDot WHERE ID=?", Object.class, data.root())).isNotNull();
            } finally {
                meeting.rollback();
            }
        }
    }

    private static void awaitSqlBlock(int waiter, int owner) throws Exception {
        try (var connection = source.getConnection()) {
            var observer = jdbc(connection);
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < deadline) {
                if (observer.queryForObject("""
                        SELECT COUNT(*) FROM sys.dm_exec_requests
                        WHERE session_id=? AND blocking_session_id=? AND wait_type LIKE 'LCK_M_%'
                        """, Integer.class, waiter, owner) == 1) return;
                Thread.sleep(25);
            }
        }
        throw new AssertionError("Expected child SQL session to wait on the dossier completion transaction");
    }

    private static JdbcTemplate jdbc(Connection connection) {
        return new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    private static void withFixture(Scenario scenario) throws Exception {
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var jdbc = jdbc(connection);
                scenario.run(jdbc, create(jdbc));
            } finally {
                // A socket timeout closes the connection. Preserve that original
                // test failure instead of replacing it with a rollback error.
                if (!connection.isClosed()) connection.rollback();
            }
        }
    }

    private static void rejects(int error, Runnable operation) {
        assertThat(sqlError(catchThrowable(operation::run))).isEqualTo(error);
    }

    private static int sqlError(Throwable failure) {
        assertThat(failure).as("SQL operation must fail").isNotNull();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) return sql.getErrorCode();
        }
        throw new AssertionError("Expected a SQL Server error", failure);
    }

    @FunctionalInterface
    private interface Scenario { void run(JdbcTemplate jdbc, ClassData data) throws Exception; }
}
