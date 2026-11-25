package pbl6.ctsv.hocbong.kkht.configuration.combination;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.foundation.api.ScholarshipCombinationSource;
import pbl6.ctsv.foundation.api.ScholarshipCombinationSource.Kind;
import pbl6.ctsv.foundation.repository.ScholarshipCombinationRepository;
import pbl6.ctsv.hocbong.kkht.configuration.combination.entity.TrainingCombination;
import pbl6.ctsv.hocbong.kkht.round.RoundAccess;
import pbl6.ctsv.hocbong.kkht.round.RoundFailure;
import pbl6.ctsv.hocbong.kkht.round.ScholarshipTestDatabase;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipAudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Guarded SQL Server, real Foundation provider and JPA; the actor is pre-resolved. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CombinationSqlIT {
    private static final LocalDate START = LocalDate.of(2094, 9, 1);
    private static final LocalDate END = LocalDate.of(2095, 8, 31);
    private static final long PROGRAM_SOURCE_ID = 9_000_000_000_000_000_110L;
    private static final String PROGRAM_NAME = "SYN Chương trình đào tạo";

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private LocalContainerEntityManagerFactoryBean factory;
    private EntityManager em;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private ScholarshipCombinationSource source;
    private CombinationService service;

    @BeforeAll
    void guardTargetAndValidateSchema() throws Exception {
        var dataSource = ScholarshipTestDatabase.migrate();
        jdbc = new JdbcTemplate(dataSource);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("pbl6.ctsv.foundation", TrainingCombination.class.getPackageName(),
                ScholarshipAudit.class.getPackageName());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "validate");
        factory.afterPropertiesSet();

        var manager = new JpaTransactionManager(factory.getObject());
        manager.setDataSource(dataSource);
        manager.afterPropertiesSet();
        tx = new TransactionTemplate(manager);
        em = SharedEntityManagerCreator.createSharedEntityManager(factory.getObject());
        source = new ScholarshipCombinationRepository(em, jdbc);
        service = service(new CombinationRepository(em, jdbc));
    }

    @AfterAll
    void closeFactory() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test
    void HB_COMB_01_realLookupPagesLiteralSearchAndDistinctFrameworkCodes() {
        rollingBack(fixture -> {
            var first = service.sources(Kind.CURRICULA, fixture.prefix(), new int[]{0, 1});
            var second = service.sources(Kind.CURRICULA, fixture.prefix(), new int[]{1, 1});
            var matching = new ScholarshipCombinationSource.Option(fixture.frameworkCode(), fixture.frameworkCode(), null, null);
            var other = new ScholarshipCombinationSource.Option(fixture.otherFrameworkCode(), fixture.otherFrameworkCode(), null, null);
            assertThat(first.totalElements()).isEqualTo(2);
            assertThat(second.totalElements()).isEqualTo(2);
            assertThat(first.items()).hasSize(1);
            assertThat(second.items()).hasSize(1);
            assertThat(List.of(first.items().getFirst(), second.items().getFirst()))
                    .containsExactlyInAnyOrder(matching, other);
            assertThat(service.sources(Kind.CURRICULA, fixture.prefix() + "-KHUNG~%_[", new int[]{0, 10}).items())
                    .containsExactly(matching);
            assertThat(service.sources(Kind.CURRICULA, fixture.prefix() + "-ABSENT", new int[]{0, 10}).totalElements())
                    .isZero();

            var programs = service.sources(Kind.PROGRAMS, fixture.programCode(), new int[]{0, 10});
            assertThat(programs.totalElements()).isEqualTo(1);
            assertThat(programs.items()).containsExactly(new ScholarshipCombinationSource.Option(
                    Long.toString(fixture.program()), fixture.programCode(), PROGRAM_NAME, Long.toString(fixture.major())));
            assertThat(service.sources(Kind.COHORTS, fixture.cohortCode(), new int[]{0, 10}).items())
                    .containsExactly(new ScholarshipCombinationSource.Option(
                            Long.toString(fixture.cohort()), fixture.cohortCode(), null, null));
            assertThat(service.sources(Kind.MAJORS, fixture.majorCode(), new int[]{0, 10}).items())
                    .containsExactly(new ScholarshipCombinationSource.Option(
                            Long.toString(fixture.major()), fixture.majorCode(), "SYN Ngành", null));
        });
    }

    @Test
    void HB_COMB_01_invalidSourceDoesNotFallBackToExternalIdsOrInactiveRows() {
        rollingBack(fixture -> {
            assertSourceFailure(() -> service.create(command(fixture, PROGRAM_SOURCE_ID, fixture.major(),
                    fixture.frameworkCode(), START, END), fixture.actor()), "programId");
            assertSourceFailure(() -> service.create(command(fixture, fixture.program(), fixture.otherMajor(),
                    fixture.frameworkCode(), START, END), fixture.actor()), "majorId");
            assertSourceFailure(() -> service.create(command(fixture, fixture.program(), fixture.major(),
                    Long.toString(fixture.frameworkRows().getFirst()), START, END), fixture.actor()), "curriculumCode");
            assertSourceFailure(() -> service.create(command(fixture, fixture.program(), fixture.major(),
                    fixture.prefix() + "-ABSENT", START, END), fixture.actor()), "curriculumCode");
            jdbc.update("UPDATE dbo.khoa_hoc SET dang_hien_dien_nguon=0 WHERE id=?", fixture.cohort());
            assertThat(service.sources(Kind.COHORTS, fixture.cohortCode(), new int[]{0, 10}).items()).isEmpty();
            assertSourceFailure(() -> service.create(command(fixture, START, END), fixture.actor()), "cohortId");
            assertThat(combinationCount(fixture)).isZero();
            assertThat(auditCount(fixture)).isZero();
        });
    }

    @Test
    void HB_COMB_03_inclusiveOverlapAcrossFrameworksAllowsAdjacentRanges() {
        rollingBack(fixture -> {
            var first = service.create(command(fixture, START, END), fixture.actor());
            var overlapping = command(fixture, fixture.program(), fixture.major(), fixture.otherFrameworkCode(), END, null);
            assertFailure(() -> service.create(overlapping, fixture.actor()), "COMBINATION_OVERLAP");

            var second = service.create(command(fixture, fixture.program(), fixture.major(),
                    fixture.otherFrameworkCode(), END.plusDays(1), null), fixture.actor());
            assertThat(second.body().effectiveTo()).isNull();
            assertFailure(() -> service.update(Long.parseLong(second.body().id()), overlapping, second.etag(),
                    fixture.actor()), "COMBINATION_OVERLAP");
            assertFailure(() -> service.create(command(fixture, END.plusYears(1), null), fixture.actor()),
                    "COMBINATION_OVERLAP");

            var latest = service.list(new int[]{0, 1});
            var previous = service.list(new int[]{1, 1});
            assertThat(latest.items()).containsExactly(second.body());
            assertThat(previous.items()).containsExactly(first.body());
            assertThat(latest.totalElements()).isEqualTo(previous.totalElements()).isGreaterThanOrEqualTo(2);
            assertThat(combinationCount(fixture)).isEqualTo(2);
            assertThat(auditCount(fixture)).isEqualTo(2);
        });
    }

    @Test
    void HB_COMB_03_preservesSqlDateBoundsBeforeGregorianCalendarCutover() {
        rollingBack(fixture -> {
            var from = LocalDate.of(1582, 10, 10);
            var through = LocalDate.of(1582, 10, 12);
            var created = service.create(command(fixture, from, through), fixture.actor());
            long id = Long.parseLong(created.body().id());

            var stored = jdbc.queryForObject("""
                    SELECT CONVERT(varchar(10),hieu_luc_tu,23),CONVERT(varchar(10),hieu_luc_den,23)
                    FROM dbo.hb_to_hop_dao_tao WHERE id=?
                    """, (row, n) -> List.of(row.getString(1), row.getString(2)), id);
            assertThat(stored).containsExactly("1582-10-10", "1582-10-12");
            em.clear();
            assertThat(service.get(id).body().effectiveFrom()).isEqualTo(from);
            assertThat(service.get(id).body().effectiveTo()).isEqualTo(through);
            assertFailure(() -> service.create(command(fixture, through, through), fixture.actor()),
                    "COMBINATION_OVERLAP");

            var nextDay = LocalDate.of(1582, 10, 13);
            var adjacent = service.create(command(fixture, nextDay, nextDay), fixture.actor());
            assertThat(adjacent.body().effectiveFrom()).isEqualTo(nextDay);
            assertThat(combinationCount(fixture)).isEqualTo(2);
        });
    }

    @Test
    void HB_COMB_04_createUpdateDeletePersistSnapshotVersionAndAuditTombstone() {
        rollingBack(fixture -> {
            var created = service.create(command(fixture, START, END), fixture.actor());
            long id = Long.parseLong(created.body().id());
            assertThat(created.body()).isEqualTo(new CombinationDtos.Detail(Long.toString(id),
                    Long.toString(fixture.program()), fixture.programCode(), PROGRAM_NAME,
                    Long.toString(fixture.cohort()), fixture.cohortCode(), Long.toString(fixture.major()),
                    fixture.majorCode(), fixture.frameworkCode(), 8, "CN", START, END));
            assertThat(created.etag()).matches("\"[A-Za-z0-9_-]+\"");

            jdbc.update("UPDATE dbo.chuong_trinh_dao_tao SET ten=? WHERE id=?", "SYN Tên mới", fixture.program());
            em.clear();
            assertThat(service.get(id)).isEqualTo(created);

            var update = new CombinationDtos.Command(fixture.program(), fixture.cohort(), fixture.major(),
                    fixture.otherFrameworkCode(), 10, "KS", START, null);
            assertFailure(() -> service.update(id, update, null, fixture.actor()), "PRECONDITION_REQUIRED");
            var updated = service.update(id, update, created.etag(), fixture.actor());
            assertThat(updated.body().programName()).isEqualTo("SYN Tên mới");
            assertThat(updated.body().programType()).isEqualTo("KS");
            assertThat(updated.body().standardSemesters()).isEqualTo(10);
            assertThat(updated.body().effectiveTo()).isNull();
            assertThat(updated.etag()).isNotEqualTo(created.etag());
            em.clear();
            assertThat(service.get(id)).isEqualTo(updated);
            assertFailure(() -> service.update(id, update, created.etag(), fixture.actor()), "VERSION_CONFLICT");
            assertFailure(() -> service.delete(id, null, fixture.actor()), "PRECONDITION_REQUIRED");
            assertFailure(() -> service.delete(id, created.etag(), fixture.actor()), "VERSION_CONFLICT");

            service.delete(id, updated.etag(), fixture.actor());
            assertFailure(() -> service.get(id), "NOT_FOUND");
            assertThat(combinationCount(fixture)).isZero();
            assertAuditTrail(fixture, id, created.body(), updated.body());
        });
    }

    @Test
    void HB_COMB_08_realAuditForeignKeyFailureRollsBackConfigurationWrite() {
        var fixture = tx.execute(status -> seedFixture());
        try {
            var faulty = service(new CombinationRepository(em, jdbc) {
                @Override
                void audit(ScholarshipAudit event) {
                    assertThat(combinationCount(fixture)).as("configuration flushed before audit").isOne();
                    event.setRoleId(-1L);
                    super.audit(event);
                }
            });
            assertThatThrownBy(() -> tx.execute(status -> faulty.create(command(fixture, START, END), fixture.actor())))
                    .hasRootCauseInstanceOf(SQLException.class)
                    .hasStackTraceContaining("fk_hb_nhat_ky_vai_tro");
            assertThat(combinationCount(fixture)).isZero();
            assertThat(auditCount(fixture)).isZero();
        } finally {
            cleanupCommittedFixture(fixture);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "chuong_trinh_dao_tao_id, -1, fk_hb_to_hop_chuong_trinh",
        "khoa_hoc_id, -1, fk_hb_to_hop_khoa_hoc",
        "nganh_noi_bo_id, -1, fk_hb_to_hop_nganh",
        "so_ky_chuan, 0, ck_hb_to_hop_so_ky",
        "loai_ctdt, cn, ck_hb_to_hop_loai",
        "hieu_luc_den, 2094-08-31, ck_hb_to_hop_ngay"
    })
    void HB_COMB_10_constraintsGuardDirectSql(String column, String value, String constraint) {
        rollingBack(fixture -> {
            var created = service.create(command(fixture, START, END), fixture.actor());
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE dbo.hb_to_hop_dao_tao SET " + column + "=? WHERE id=?",
                    value, Long.parseLong(created.body().id())))
                    .hasRootCauseInstanceOf(SQLException.class)
                    .hasStackTraceContaining(constraint);
        });
    }

    @Test
    void HB_COMB_10_triggerRejectsDirectSqlOverlapAcrossFrameworkCodes() {
        rollingBack(fixture -> {
            service.create(command(fixture, START, END), fixture.actor());
            var next = service.create(command(fixture, fixture.program(), fixture.major(),
                    fixture.otherFrameworkCode(), END.plusDays(1), null), fixture.actor());
            assertThatThrownBy(() -> jdbc.update("UPDATE dbo.hb_to_hop_dao_tao SET hieu_luc_tu=? WHERE id=?",
                    END.toString(), Long.parseLong(next.body().id())))
                    .hasRootCauseInstanceOf(SQLException.class)
                    .hasStackTraceContaining("COMBINATION_OVERLAP");
        });
    }

    @Test
    void HB_COMB_09_transactionFenceWorksBeforeReadingAnyApplicationRows() {
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            new CombinationRepository(em, jdbc).serializeWrites();
            assertThat(jdbc.queryForObject("""
                    SELECT APPLOCK_MODE('public', N'ctsv.hocbong.combinations', 'Transaction')
                    """, String.class)).isEqualTo("Exclusive");
        });
    }

    @Test
    void HB_COMB_09_twoConcurrentSqlTransactionsCommitExactlyOneCreate() throws Exception {
        var fixture = tx.execute(status -> seedFixture());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var bothConnected = new CountDownLatch(2);
            var command = command(fixture, START, null);
            Callable<Attempt> create = () -> {
                int[] session = new int[1];
                try {
                    var view = tx.execute(status -> {
                        session[0] = jdbc.queryForObject("SELECT @@SPID", Integer.class);
                        bothConnected.countDown();
                        awaitBothConnections(bothConnected);
                        return service.create(command, fixture.actor());
                    });
                    return new Attempt(session[0], view.body().id(), null);
                } catch (RoundFailure failure) {
                    return new Attempt(session[0], null, failure.getMessage());
                }
            };

            var first = executor.submit(create);
            var second = executor.submit(create);
            var attempts = List.of(first.get(40, TimeUnit.SECONDS), second.get(40, TimeUnit.SECONDS));
            assertThat(attempts).extracting(Attempt::session).doesNotHaveDuplicates();
            assertThat(attempts).extracting(Attempt::failure).containsExactlyInAnyOrder(null, "COMBINATION_OVERLAP");
            String winner = attempts.stream().filter(attempt -> attempt.id() != null).findFirst().orElseThrow().id();
            assertThat(combinationCount(fixture)).isOne();
            assertThat(tx.execute(status -> service.get(Long.parseLong(winner))).body().id()).isEqualTo(winner);
            assertThat(jdbc.queryForList("""
                    SELECT doi_tuong_id FROM dbo.hb_nhat_ky
                    WHERE nguoi_dung_id=? AND hanh_dong=N'combination.created'
                    """, Long.class, fixture.actor().userId())).containsExactly(Long.parseLong(winner));
            assertThat(auditCount(fixture)).isOne();
        } finally {
            cleanupCommittedFixture(fixture);
        }
    }

    private void assertAuditTrail(Fixture fixture, long id, CombinationDtos.Detail created, CombinationDtos.Detail updated) {
        var events = jdbc.query("""
                SELECT nguoi_dung_id,vai_tro_id,loai_doi_tuong,doi_tuong_id,pham_vi,hanh_dong,
                       du_lieu_truoc,du_lieu_sau,DATEPART(TZOFFSET,thoi_diem),correlation_id
                FROM dbo.hb_nhat_ky WHERE nguoi_dung_id=? ORDER BY id
                """, (row, n) -> {
            assertThat(row.getLong("nguoi_dung_id")).isEqualTo(fixture.actor().userId());
            assertThat(row.getLong("vai_tro_id")).isEqualTo(fixture.actor().roleId());
            assertThat(row.getString("loai_doi_tuong")).isEqualTo("combination");
            assertThat(row.getLong("doi_tuong_id")).isEqualTo(id);
            assertThat(row.getString("pham_vi")).isEqualTo("HOC_BONG");
            assertThat(row.getInt(9)).isZero();
            return new AuditEvent(row.getString("hanh_dong"), readJson(row.getString("du_lieu_truoc")),
                    readJson(row.getString("du_lieu_sau")), UUID.fromString(row.getString("correlation_id")));
        }, fixture.actor().userId());
        assertThat(events).extracting(AuditEvent::action)
                .containsExactly("combination.created", "combination.updated", "combination.deleted");
        assertThat(events).extracting(AuditEvent::correlationId).doesNotHaveDuplicates();
        assertThat(events.get(0).before()).isNull();
        assertThat(events.get(0).after()).isEqualTo(json.valueToTree(created));
        assertThat(events.get(1).before()).isEqualTo(json.valueToTree(created));
        assertThat(events.get(1).after()).isEqualTo(json.valueToTree(updated));
        assertThat(events.get(2).before()).isEqualTo(json.valueToTree(updated));
        assertThat(events.get(2).after()).isEqualTo(json.createObjectNode().put("id", Long.toString(id)).put("deleted", true));
        var fields = new ArrayList<String>();
        events.getFirst().after().fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "programId", "programCode", "programName",
                "cohortId", "cohortCode", "majorId", "majorCode", "curriculumCode", "standardSemesters",
                "programType", "effectiveFrom", "effectiveTo");
        assertThat(events.getFirst().after().get("programId").isTextual()).isTrue();
    }

    private JsonNode readJson(String value) {
        try {
            return value == null ? null : json.readTree(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new AssertionError("Invalid persisted JSON audit", exception);
        }
    }

    private CombinationService service(CombinationRepository repository) {
        return new CombinationService(repository, source, List.of(), json, Clock.systemUTC());
    }

    private void rollingBack(Consumer<Fixture> action) {
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            action.accept(seedFixture());
        });
    }

    private Fixture seedFixture() {
        String prefix = "SYN-HB-COMB-" + UUID.randomUUID();
        String programCode = prefix + "-PROGRAM";
        String cohortCode = prefix + "-COHORT";
        String majorCode = prefix + "-MAJOR";
        String frameworkCode = prefix + "-KHUNG~%_[-A";
        String otherFrameworkCode = prefix + "-KHUNG~%Q[-B";
        long actor = insert("INSERT dbo.nguoi_dung(dia_chi_email,dang_hoat_dong) OUTPUT INSERTED.id VALUES(?,1)",
                prefix + "@example.invalid");
        var role = jdbc.queryForObject("SELECT id FROM dbo.vai_tro WHERE ma=N'hoc_bong_chuyen_vien'", Long.class);
        long externalSystem = insert("INSERT dbo.he_thong_ngoai(ma) OUTPUT INSERTED.id VALUES(?)", prefix);
        long cohort = insert("INSERT dbo.khoa_hoc(ma) OUTPUT INSERTED.id VALUES(?)", cohortCode);
        long major = insert("INSERT dbo.nganh_noi_bo(ma,ten) OUTPUT INSERTED.id VALUES(?,N'SYN Ngành')", majorCode);
        long otherMajor = insert("INSERT dbo.nganh_noi_bo(ma,ten) OUTPUT INSERTED.id VALUES(?,N'SYN Ngành khác')",
                prefix + "-OTHER");
        long program = insert("""
                INSERT dbo.chuong_trinh_dao_tao(ma,ten,nganh_noi_bo_id,he_thong_nguon_id,ma_dinh_danh_ben_ngoai)
                OUTPUT INSERTED.id VALUES(?,?,?,?,?)
                """, programCode, PROGRAM_NAME, major, externalSystem, Long.toString(PROGRAM_SOURCE_ID));
        long firstCourse = course(externalSystem, "course-a");
        long secondCourse = course(externalSystem, "course-b");
        var frameworkRows = List.of(
                framework(externalSystem, "framework-a1", firstCourse, frameworkCode, true),
                framework(externalSystem, "framework-a2", secondCourse, frameworkCode, true),
                framework(externalSystem, "framework-b1", firstCourse, otherFrameworkCode, true),
                framework(externalSystem, "framework-absent", secondCourse, prefix + "-ABSENT", false));
        return new Fixture(prefix, externalSystem, new RoundAccess.Actor(actor, null, role), program, programCode,
                cohort, cohortCode, major, majorCode, otherMajor, frameworkCode, otherFrameworkCode,
                frameworkRows, List.of(firstCourse, secondCourse));
    }

    private void cleanupCommittedFixture(Fixture fixture) {
        tx.executeWithoutResult(status -> {
            jdbc.update("""
                    DELETE FROM dbo.hb_to_hop_dao_tao
                    WHERE chuong_trinh_dao_tao_id=? AND khoa_hoc_id=? AND nganh_noi_bo_id=?
                    """, fixture.program(), fixture.cohort(), fixture.major());
            fixture.frameworkRows().forEach(id -> jdbc.update(
                    "DELETE FROM dbo.khung_chuong_trinh WHERE id=? AND he_thong_nguon_id=?", id, fixture.externalSystem()));
            fixture.courses().forEach(id -> jdbc.update(
                    "DELETE FROM dbo.hoc_phan WHERE id=? AND he_thong_nguon_id=?", id, fixture.externalSystem()));
            jdbc.update("DELETE FROM dbo.chuong_trinh_dao_tao WHERE id=? AND he_thong_nguon_id=?",
                    fixture.program(), fixture.externalSystem());
            jdbc.update("DELETE FROM dbo.khoa_hoc WHERE id=? AND ma=?", fixture.cohort(), fixture.cohortCode());
            jdbc.update("DELETE FROM dbo.nganh_noi_bo WHERE id=? AND ma=?", fixture.major(), fixture.majorCode());
            jdbc.update("DELETE FROM dbo.nganh_noi_bo WHERE id=? AND ma=?", fixture.otherMajor(), fixture.prefix() + "-OTHER");
            jdbc.update("DELETE FROM dbo.he_thong_ngoai WHERE id=? AND ma=?", fixture.externalSystem(), fixture.prefix());
            // Keep the dedicated synthetic actor only if referenced by the immutable audit trail.
            jdbc.update("""
                    DELETE FROM dbo.nguoi_dung WHERE id=? AND dia_chi_email=?
                    AND NOT EXISTS (SELECT 1 FROM dbo.hb_nhat_ky WHERE nguoi_dung_id=?)
                    """, fixture.actor().userId(), fixture.prefix() + "@example.invalid", fixture.actor().userId());
        });
    }

    private long course(long system, String externalId) {
        return insert("""
                INSERT dbo.hoc_phan(he_thong_nguon_id,ma_dinh_danh_ben_ngoai,dang_hien_dien_nguon)
                OUTPUT INSERTED.id VALUES(?,?,1)
                """, system, externalId);
    }

    private long framework(long system, String externalId, long course, String code, boolean present) {
        return insert("""
                INSERT dbo.khung_chuong_trinh(he_thong_nguon_id,ma_dinh_danh_ben_ngoai,
                    hoc_phan_id,ma_khung_nguon,dang_hien_dien_nguon)
                OUTPUT INSERTED.id VALUES(?,?,?,?,?)
                """, system, externalId, course, code, present);
    }

    private long insert(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long combinationCount(Fixture fixture) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.hb_to_hop_dao_tao WHERE chuong_trinh_dao_tao_id=?",
                Long.class, fixture.program());
    }

    private long auditCount(Fixture fixture) {
        return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.hb_nhat_ky WHERE nguoi_dung_id=?",
                Long.class, fixture.actor().userId());
    }

    private static CombinationDtos.Command command(Fixture fixture, LocalDate from, LocalDate to) {
        return command(fixture, fixture.program(), fixture.major(), fixture.frameworkCode(), from, to);
    }

    private static CombinationDtos.Command command(Fixture fixture, long program, long major, String code,
            LocalDate from, LocalDate to) {
        return new CombinationDtos.Command(program, fixture.cohort(), major, code, 8, "CN", from, to);
    }

    private static void assertSourceFailure(Runnable action, String field) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(FoundationAccess.Failure.class, failure -> {
            assertThat(failure.status()).isEqualTo(400);
            assertThat(failure.code()).isEqualTo("INVALID_COMBINATION_SOURCE");
            assertThat(failure.field()).isEqualTo(field);
        });
    }

    private static void assertFailure(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RoundFailure.class).hasMessage(code);
    }

    private static void awaitBothConnections(CountDownLatch ready) {
        try {
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("two active SQL sessions").isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while opening concurrent SQL sessions", exception);
        }
    }

    private record Fixture(String prefix, long externalSystem, RoundAccess.Actor actor, long program, String programCode,
            long cohort, String cohortCode, long major, String majorCode, long otherMajor, String frameworkCode,
            String otherFrameworkCode, List<Long> frameworkRows, List<Long> courses) {}

    private record AuditEvent(String action, JsonNode before, JsonNode after, UUID correlationId) {}

    private record Attempt(int session, String id, String failure) {}
}
