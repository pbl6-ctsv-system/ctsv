package pbl6.ctsv.foundation.sync;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import pbl6.ctsv.hocbong.kkht.round.ScholarshipTestDatabase;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Guarded real SQL and production target. The source change envelope and rows are synthetic. */
class EquivalentCourseSqlIT {
    private static final String ORIGINAL_COURSE = "9000000000000000101";
    private static final String EQUIVALENT_COURSE = "9000000000000000102";
    private static final String UNMAPPED_COURSE = "9000000000000000103";
    private static final String SEMESTER = "9000000000000000201";
    private static final String STUDENT = "9000000000000000301";
    private static DataSource dataSource;

    @BeforeAll
    static void guardAndCompareExistingDataAcrossFlywayMigrate() throws Exception {
        var guardedSource = ScholarshipTestDatabase.source();
        var target = new JdbcTemplate(guardedSource);
        var mappings = EquivalentCourseMappingTest.mappings().toList();
        var before = mappings.stream().map(mapping -> readRows(target, mapping)).toList();
        var originalNullability = mappings.stream().map(mapping -> isNullable(target, mapping)).toList();

        if (originalNullability.stream().noneMatch(Objects::isNull)) {
            verifyNullableAlterPreservesLegacyFixture(guardedSource, mappings);
            assertThat(mappings.stream().map(mapping -> isNullable(target, mapping)).toList())
                    .isEqualTo(originalNullability);
            assertThat(mappings.stream().map(mapping -> readRows(target, mapping)).toList()).isEqualTo(before);
        }

        dataSource = ScholarshipTestDatabase.migrate();

        for (int i = 0; i < mappings.size(); i++) {
            var mapping = mappings.get(i);
            assertThat(readRows(target, mapping)).as(mapping.table()).isEqualTo(before.get(i));
            assertThat(isNullable(target, mapping)).as(mapping.column()).isTrue();
            LoggerFactory.getLogger(EquivalentCourseSqlIT.class).info(
                    "HB-COMB-12 {} existing rows compared: {}, formerly nullable: {}",
                    mapping.table(), before.get(i).size(), originalNullability.get(i));
        }
    }

    private static void verifyNullableAlterPreservesLegacyFixture(DataSource source,
                                                                  List<EquivalentCourseMappingTest.Mapping> mappings) throws Exception {
        String migration = new ClassPathResource("db/migration/V28__hoc_bong_training_combinations.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        String nullableBatch = migration.split("(?m)^GO\\s*$", 2)[0];
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            try {
                var fixtureIds = new ArrayList<Long>();
                var expected = new ArrayList<CourseKeys>();
                for (var mapping : mappings) {
                    var page = ResourceSchemaTest.page(sourceData(mapping).put(mapping.wireField(), EQUIVALENT_COURSE));
                    var fixture = createParentsAndCheckpoints(jdbc, mapping.resource(), page.get("source_epoch").asText());
                    long id = new JdbcFoundationTarget(mapping.resource())
                            .apply(connection, fixture.sourceId(), page.get("items").get(0));
                    fixtureIds.add(id);
                    expected.add(new CourseKeys(fixture.originalId(), fixture.equivalentId()));
                }
                var before = mappings.stream().map(mapping -> readRows(jdbc, mapping)).toList();

                jdbc.execute(nullableBatch);

                for (int i = 0; i < mappings.size(); i++) {
                    var mapping = mappings.get(i);
                    assertThat(isNullable(jdbc, mapping)).as(mapping.column()).isTrue();
                    assertThat(readCourseKeys(jdbc, mapping, fixtureIds.get(i))).isEqualTo(expected.get(i));
                    assertThat(readRows(jdbc, mapping)).isEqualTo(before.get(i));
                }
                LoggerFactory.getLogger(EquivalentCourseSqlIT.class)
                        .info("HB-COMB-12 V28 nullable batch preserved {} preexisting synthetic FK rows", fixtureIds.size());
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void HB_COMB_11_hibernateValidatesFoundationAgainstTheMigratedSchema() {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("pbl6.ctsv.foundation");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "validate");
        factory.getJpaPropertyMap().put("hibernate.default_schema", "dbo");
        try {
            factory.afterPropertiesSet();
            var metamodel = factory.getObject().getMetamodel();
            EquivalentCourseMappingTest.mappings()
                    .forEach(mapping -> assertThat(metamodel.entity(mapping.entity())).isNotNull());
        } finally {
            factory.destroy();
        }
    }

    @ParameterizedTest
    @MethodSource("pbl6.ctsv.foundation.sync.EquivalentCourseMappingTest#mappings")
    void HB_COMB_10_syncPersistsNullOrResolvedForeignKey(EquivalentCourseMappingTest.Mapping mapping) throws Exception {
        var data = sourceData(mapping);
        var page = ResourceSchemaTest.page(data);
        String epoch = page.get("source_epoch").asText();
        var validator = new CtSchema(mapping.resource());
        var syncTarget = new JdbcFoundationTarget(mapping.resource());

        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            try {
                jdbc.execute("SET XACT_ABORT OFF");
                var fixture = createParentsAndCheckpoints(jdbc, mapping.resource(), epoch);

                data.putNull(mapping.wireField());
                validator.validate(page);
                long id = syncTarget.apply(connection, fixture.sourceId(), page.get("items").get(0));
                assertThat(readCourseKeys(jdbc, mapping, id)).isEqualTo(new CourseKeys(fixture.originalId(), null));

                data.put(mapping.wireField(), EQUIVALENT_COURSE);
                validator.validate(page);
                assertThat(syncTarget.apply(connection, fixture.sourceId(), page.get("items").get(0))).isEqualTo(id);
                var expected = new CourseKeys(fixture.originalId(), fixture.equivalentId());
                assertThat(readCourseKeys(jdbc, mapping, id)).isEqualTo(expected);

                data.put(mapping.wireField(), UNMAPPED_COURSE);
                validator.validate(page);
                assertThatThrownBy(() -> syncTarget.apply(connection, fixture.sourceId(), page.get("items").get(0)))
                        .isInstanceOf(IllegalStateException.class).hasMessage("FK_MAPPING_REQUIRED: " + mapping.wireField());
                assertThat(readCourseKeys(jdbc, mapping, id)).isEqualTo(expected);

                assertThatThrownBy(() -> jdbc.update("UPDATE dbo.%s SET %s = ? WHERE id = ?"
                        .formatted(mapping.table(), mapping.column()), -1L, id))
                        .hasRootCauseInstanceOf(SQLException.class)
                        .rootCause().satisfies(cause -> {
                            assertThat(((SQLException) cause).getErrorCode()).isEqualTo(547);
                            assertThat(cause.getMessage()).contains("fk_" + mapping.table() + "_" + mapping.column());
                        });
                assertThat(readCourseKeys(jdbc, mapping, id)).isEqualTo(expected);

                data.putNull(mapping.wireField());
                validator.validate(page);
                assertThat(syncTarget.apply(connection, fixture.sourceId(), page.get("items").get(0))).isEqualTo(id);
                assertThat(readCourseKeys(jdbc, mapping, id)).isEqualTo(new CourseKeys(fixture.originalId(), null));
                assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.hoc_phan WHERE he_thong_nguon_id = ?",
                        Long.class, fixture.sourceId())).isEqualTo(2L);
            } finally {
                connection.rollback();
            }
        }
    }

    private static ObjectNode sourceData(EquivalentCourseMappingTest.Mapping mapping) throws Exception {
        var data = ResourceSchemaTest.fixture(mapping.resource()).put("course_source_id", ORIGINAL_COURSE);
        if (data.has("plan_source_id")) {
            data.put("plan_source_id", SEMESTER).put("student_source_id", STUDENT);
        }
        return data;
    }

    private static Fixture createParentsAndCheckpoints(JdbcTemplate jdbc, String resource, String epoch) {
        String syntheticCode = "SYN-HB-COMB-" + UUID.randomUUID();
        long source = jdbc.queryForObject("INSERT dbo.he_thong_ngoai (ma) OUTPUT INSERTED.id VALUES (?)",
                Long.class, syntheticCode);
        jdbc.update("INSERT dbo.hoc_ky (ma, he_thong_nguon_id, ma_dinh_danh_ben_ngoai) VALUES (?, ?, ?)",
                syntheticCode, source, SEMESTER);
        jdbc.update("INSERT dbo.sinh_vien (mssv, he_thong_nguon_id, ma_dinh_danh_ben_ngoai) VALUES (?, ?, ?)",
                syntheticCode, source, STUDENT);
        long originalId = insertCourse(jdbc, source, ORIGINAL_COURSE);
        long equivalentId = insertCourse(jdbc, source, EQUIVALENT_COURSE);

        for (String parent : List.of("plans", "students", "courses")) {
            jdbc.update("""
                    INSERT dbo.dong_bo_trang_thai (he_thong_nguon_id, tai_nguyen, source_epoch, ct_version)
                    VALUES (?, ?, ?, 12)
                    """, source, parent, epoch);
        }
        String owner = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT dbo.dong_bo_trang_thai (he_thong_nguon_id, tai_nguyen, the_he, chu_so_huu)
                VALUES (?, ?, 1, ?)
                """, source, resource, owner);
        jdbc.update("""
                INSERT dbo.dong_bo_luot (id, he_thong_nguon_id, tai_nguyen, the_he, chu_so_huu, export_id,
                                         source_epoch, che_do, den_version, as_of, trang_thai)
                VALUES (?, ?, ?, 1, ?, ?, ?, 'full', 12, SYSUTCDATETIME(), 'RUNNING')
                """, UUID.randomUUID().toString(), source, resource, owner, UUID.randomUUID().toString(), epoch);
        return new Fixture(source, originalId, equivalentId);
    }

    private static long insertCourse(JdbcTemplate jdbc, long sourceId, String courseSourceId) {
        return jdbc.queryForObject("""
                INSERT dbo.hoc_phan (he_thong_nguon_id, ma_dinh_danh_ben_ngoai, dang_hien_dien_nguon)
                OUTPUT INSERTED.id VALUES (?, ?, 1)
                """, Long.class, sourceId, courseSourceId);
    }

    private static CourseKeys readCourseKeys(JdbcTemplate jdbc, EquivalentCourseMappingTest.Mapping mapping, long id) {
        return jdbc.queryForObject("SELECT hoc_phan_id, %s FROM dbo.%s WHERE id = ?"
                        .formatted(mapping.column(), mapping.table()),
                (row, index) -> new CourseKeys(row.getLong(1), row.getObject(2, Long.class)), id);
    }

    private static List<ExistingRow> readRows(JdbcTemplate jdbc, EquivalentCourseMappingTest.Mapping mapping) {
        if (jdbc.queryForObject("SELECT OBJECT_ID(?, 'U')", Integer.class, "dbo." + mapping.table()) == null) {
            return List.of();
        }
        return jdbc.query("SELECT id, he_thong_nguon_id, ma_dinh_danh_ben_ngoai, hoc_phan_id, %s FROM dbo.%s ORDER BY id"
                        .formatted(mapping.column(), mapping.table()),
                (row, index) -> new ExistingRow(row.getLong(1), row.getLong(2), row.getString(3),
                        new CourseKeys(row.getLong(4), row.getObject(5, Long.class))));
    }

    private static Boolean isNullable(JdbcTemplate jdbc, EquivalentCourseMappingTest.Mapping mapping) {
        return jdbc.queryForObject("""
                SELECT COLUMNPROPERTY(OBJECT_ID(?), ?, 'AllowsNull')
                """, Boolean.class, "dbo." + mapping.table(), mapping.column());
    }

    private record ExistingRow(long id, long sourceId, String sourceRecord, CourseKeys keys) {}
    private record CourseKeys(long originalId, Long equivalentId) {}
    private record Fixture(long sourceId, long originalId, long equivalentId) {}
}
