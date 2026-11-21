package pbl6.ctsv.hocbong.kkht.round;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipAudit;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipRound;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipSemester;
import pbl6.ctsv.hocbong.kkht.round.entity.SourceSemesterMapping;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ScholarshipSchemaIT {
    private static final OffsetDateTime OCCURRED_AT = OffsetDateTime.parse("2026-11-19T03:00:00Z");
    private static final String INSERT_SEMESTER = """
            INSERT dbo.hb_hoc_ky (nam_hoc, thu_tu_ky, ngay_bat_dau, ngay_ket_thuc, trang_thai)
            VALUES (?, ?, ?, ?, N'draft')
            """;
    private static final String INSERT_ALIAS = """
            INSERT dbo.hb_anh_xa_ky_nguon
                (he_thong_nguon, loai_ma, ma_ky_nguon, ma_dot_nguon, hoc_ky_id, xac_nhan_boi, xac_nhan_luc)
            VALUES (N'SYN_HB_TEST', N'source_term', ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_ROUND = """
            INSERT dbo.kkht_dot_xet (hoc_ky_id, anh_xa_ky_nguon_id, so_dot, ten_dot, trang_thai, nguoi_tao_id)
            VALUES (?, ?, ?, N'Đợt xét SYN-HB', N'draft', ?)
            """;
    private static final String INSERT_AUDIT = """
            INSERT dbo.hb_nhat_ky (nguoi_dung_id, vai_tro_id, loai_doi_tuong, doi_tuong_id, pham_vi,
                                   hanh_dong, du_lieu_sau, thoi_diem, correlation_id)
            VALUES (?, ?, N'round', ?, N'SYN-HB', N'round.created', ?, ?, ?)
            """;

    private static DataSource dataSource;

    @BeforeAll
    static void migrateOnlyTheVerifiedTestDatabase() throws Exception {
        dataSource = ScholarshipTestDatabase.migrate();
    }

    @Test
    void HB_T00_046_enforcesUniqueKeysForeignKeysAndRanges() throws Exception {
        inRolledBackFixture(f -> {
            assertSqlRejection(2627, "uq_hb_hoc_ky_nam_hoc_thu_tu_ky", () ->
                    insert(f.connection(), INSERT_SEMESTER, f.academicYear(), 1, f.firstDay(), f.lastDay()));
            assertSqlRejection(547, "ck_hb_hoc_ky_thu_tu", () ->
                    insert(f.connection(), INSERT_SEMESTER, f.academicYear(), 0, f.firstDay(), f.lastDay()));
            assertSqlRejection(547, "ck_hb_hoc_ky_ngay", () ->
                    insert(f.connection(), INSERT_SEMESTER, f.academicYear(), 2, f.lastDay(), f.firstDay()));

            assertSqlRejection(2627, "uq_hb_anh_xa_ky_nguon_context", () ->
                    insert(f.connection(), INSERT_ALIAS, f.sourceCode(), null, f.semesterId(), f.actorId(), OCCURRED_AT));
            long otherContext = insert(f.connection(), INSERT_ALIAS, f.sourceCode(), "SYN-NEXT-ROUND",
                    f.semesterId(), f.actorId(), OCCURRED_AT);
            assertThat(otherContext).isNotEqualTo(f.aliasId());
            assertSqlRejection(547, "fk_hb_anh_xa_ky_nguon_xac_nhan_boi", () ->
                    insert(f.connection(), INSERT_ALIAS, f.sourceCode(), "SYN-MISSING-ACTOR",
                            f.semesterId(), -1L, OCCURRED_AT));
            assertSqlRejection(547, "fk_hb_anh_xa_ky_nguon_hoc_ky", () ->
                    insert(f.connection(), INSERT_ALIAS, f.sourceCode(), "SYN-MISSING-PERIOD", -1L,
                            f.actorId(), OCCURRED_AT));

            assertSqlRejection(2627, "uq_kkht_dot_xet_hoc_ky_so_dot", () ->
                    insert(f.connection(), INSERT_ROUND, f.semesterId(), f.aliasId(), 1, f.actorId()));
            assertSqlRejection(547, "ck_kkht_dot_xet_so_dot", () ->
                    insert(f.connection(), INSERT_ROUND, f.semesterId(), f.aliasId(), 0, f.actorId()));
            assertSqlRejection(547, "fk_kkht_dot_xet_nguoi_tao", () ->
                    insert(f.connection(), INSERT_ROUND, f.semesterId(), f.aliasId(), 2, -1L));

            long otherSemester = insert(f.connection(), INSERT_SEMESTER, f.academicYear(), 2, f.firstDay(), f.lastDay());
            assertSqlRejection(547, "fk_kkht_dot_xet_anh_xa_ky_nguon", () ->
                    insert(f.connection(), INSERT_ROUND, otherSemester, f.aliasId(), 1, f.actorId()));
            long otherAlias = insert(f.connection(), INSERT_ALIAS, f.sourceCode() + "-OTHER", null,
                    otherSemester, f.actorId(), OCCURRED_AT);
            assertThat(insert(f.connection(), INSERT_ROUND, otherSemester, otherAlias, 1, f.actorId())).isPositive();
        });
    }

    @Test
    void HB_T00_047_validatesSchemaAndRoundTripsNativeJpaTypes() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.datasource", dataSource)
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .build();
        try {
            var metadata = new MetadataSources(registry)
                    .addAnnotatedClass(ScholarshipSemester.class)
                    .addAnnotatedClass(SourceSemesterMapping.class)
                    .addAnnotatedClass(ScholarshipRound.class)
                    .addAnnotatedClass(ScholarshipAudit.class)
                    .buildMetadata();
            try (var factory = metadata.buildSessionFactory(); var session = factory.openSession()) {
                var transaction = session.beginTransaction();
                try {
                    var fixture = session.doReturningWork(Fixture::create);
                    assertThat(session.find(ScholarshipSemester.class, fixture.semesterId()).getSemesterOrder()).isEqualTo(1);
                    var round = session.find(ScholarshipRound.class, fixture.roundId());
                    byte[] before = round.getVersion();
                    assertThat(before).hasSize(8);
                    round.setName("Đợt xét đã đổi tên SYN-HB");
                    session.flush();
                    session.refresh(round);
                    assertThat(round.getName()).isEqualTo("Đợt xét đã đổi tên SYN-HB");
                    assertThat(round.getVersion()).hasSize(8).isNotEqualTo(before);

                    var audit = new ScholarshipAudit();
                    audit.setUserId(fixture.actorId());
                    audit.setRoleId(fixture.roleId());
                    audit.setObjectType("round");
                    audit.setObjectId(round.getId());
                    audit.setScope("SYN-HB");
                    audit.setAction("round.updated");
                    audit.setBeforeData("{\"name\":\"Đợt xét SYN-HB\"}");
                    String after = "{\"name\":\"Đợt xét đã đổi tên SYN-HB\",\"sample\":\"" + "ấ".repeat(5000) + "\"}";
                    UUID correlation = UUID.randomUUID();
                    audit.setAfterData(after);
                    audit.setOccurredAt(OCCURRED_AT);
                    audit.setCorrelationId(correlation);
                    session.persist(audit);
                    session.flush();
                    session.clear();
                    var saved = session.find(ScholarshipAudit.class, audit.getId());
                    assertThat(saved.getAfterData()).isEqualTo(after);
                    assertThat(saved.getOccurredAt()).isEqualTo(OCCURRED_AT);
                    assertThat(saved.getCorrelationId()).isEqualTo(correlation);
                } finally {
                    transaction.rollback();
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void HB_T00_048_rejectsInvalidAuditJsonAndNonUtcTimestamp() throws Exception {
        inRolledBackFixture(f -> {
            assertSqlRejection(547, "ck_hb_nhat_ky_du_lieu_sau_json", () ->
                    insert(f.connection(), INSERT_AUDIT, f.actorId(), f.roleId(), f.roundId(), "not-json",
                            OCCURRED_AT, UUID.randomUUID()));
            assertSqlRejection(547, "ck_hb_nhat_ky_thoi_diem_utc", () ->
                    insert(f.connection(), INSERT_AUDIT, f.actorId(), f.roleId(), f.roundId(), "{}",
                            OffsetDateTime.parse("2026-11-19T10:00:00+07:00"), UUID.randomUUID()));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE dbo.hb_nhat_ky SET ly_do = N'SYN-HB disallowed update' WHERE id = ?",
            "DELETE FROM dbo.hb_nhat_ky WHERE id = ?"
    })
    void HB_T00_048_rejectsAuditMutation(String statement) throws Exception {
        inRolledBackFixture(f -> {
            long id = insert(f.connection(), INSERT_AUDIT, f.actorId(), f.roleId(), f.roundId(), "{}",
                    OCCURRED_AT, UUID.randomUUID());
            assertSqlRejection(51902, "append-only", () -> execute(f.connection(), statement, id));
        });
    }

    @Test
    void HB_T00_046_protectsAliasAlreadyReferencedByARound() throws Exception {
        inRolledBackFixture(f -> assertSqlRejection(51901, "immutable", () -> execute(f.connection(),
                "UPDATE dbo.hb_anh_xa_ky_nguon SET ma_ky_nguon = ? WHERE id = ?", f.sourceCode() + "-REWRITE", f.aliasId())));
    }

    private static void inRolledBackFixture(FixtureWork work) throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                // Constraint violations must roll back the statement so later cases can reuse this fixture.
                statement.execute("SET XACT_ABORT OFF");
                try {
                    work.run(Fixture.create(connection));
                } finally {
                    connection.rollback();
                }
            }
        }
    }

    private static long insert(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, values);
            assertThat(statement.executeUpdate()).isEqualTo(1);
            try (var keys = statement.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object[] values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }

    private static void assertSqlRejection(int errorCode, String messageFragment,
                                           org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatExceptionOfType(SQLException.class).isThrownBy(action).satisfies(error -> {
            assertThat(error.getErrorCode()).isEqualTo(errorCode);
            assertThat(error.getMessage()).contains(messageFragment);
        });
    }

    @FunctionalInterface
    private interface FixtureWork {
        void run(Fixture fixture) throws Exception;
    }

    private record Fixture(Connection connection, String academicYear, LocalDate firstDay, LocalDate lastDay,
                           String sourceCode, long actorId, long roleId, long semesterId, long aliasId, long roundId) {
        static Fixture create(Connection connection) throws SQLException {
            UUID nonce = UUID.randomUUID();
            // Synthetic years outside Foundation's 20YY mapping range keep the fixture isolated.
            int year = 5000 + Math.floorMod(nonce.hashCode(), 4000);
            String academicYear = "%d-%d".formatted(year, year + 1);
            var firstDay = LocalDate.of(year, 8, 1);
            var lastDay = LocalDate.of(year + 1, 1, 31);
            String sourceCode = "SYN-HB-" + nonce;
            long actorId = insert(connection, "INSERT dbo.nguoi_dung (dia_chi_email, dang_hoat_dong) VALUES (?, 1)",
                    "hb-t01-" + nonce + "@example.invalid");
            long roleId;
            try (var statement = connection.createStatement(); var roles = statement.executeQuery("""
                    SELECT id FROM dbo.vai_tro WHERE ma = N'hoc_bong_chuyen_vien' AND dang_hoat_dong = 1
                    """)) {
                assertThat(roles.next()).as("V14 specialist role").isTrue();
                roleId = roles.getLong(1);
                assertThat(roles.next()).isFalse();
            }
            long semesterId = insert(connection, INSERT_SEMESTER, academicYear, 1, firstDay, lastDay);
            long aliasId = insert(connection, INSERT_ALIAS, sourceCode, null, semesterId, actorId, OCCURRED_AT);
            long roundId = insert(connection, INSERT_ROUND, semesterId, aliasId, 1, actorId);
            return new Fixture(connection, academicYear, firstDay, lastDay, sourceCode, actorId, roleId, semesterId, aliasId,
                    roundId);
        }
    }
}
