package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.foundation.web.FoundationRoundSqlHarness;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipAudit;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipRound;
import pbl6.ctsv.hocbong.kkht.round.entity.SourceSemesterMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real SQL Server, Hibernate, Foundation ports and HB service. Auth account checks are stubbed. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class ScholarshipRoundSqlIT {
    private static final String MARKER = "SYN-HB-T01";
    private static final String ISSUER = "urn:ctsv:hoc-bong:t01:sql-fixture";
    private static final String YEAR = "2094-2095";
    private static final int SEMESTER_ORDER = 209401;
    private static final LocalDate START = LocalDate.of(2094, 8, 1);
    private static final LocalDate END = LocalDate.of(2095, 1, 15);
    private static final AuthAccess.Principal FIRST = principal("00a1");
    private static final AuthAccess.Principal SECOND = principal("00b2");
    private static final AuthAccess.Principal WITHOUT_ROLE = principal("00c3");

    private final Set<Long> ownedRounds = ConcurrentHashMap.newKeySet();
    private final Set<Long> ownedAliases = ConcurrentHashMap.newKeySet();
    private final Set<Long> ownedPeriods = ConcurrentHashMap.newKeySet();
    private final ObjectMapper json = new ObjectMapper();
    private Connection suiteLock;
    private LocalContainerEntityManagerFactoryBean factory;
    private ExecutorService workers;
    private JdbcTemplate jdbc;
    private EntityManager em;
    private TransactionTemplate tx;
    private FoundationRoundSqlHarness foundation;
    private RoundAccess access;
    private RoundService service;
    private RoundRepository repository;
    private long sourceId;
    private long secondSourceId;
    private long summerId;
    private long firstUser;
    private long secondUser;
    private long specialistRole;

    @BeforeAll
    void setupGuardedDatabase() throws Exception {
        var dataSource = ScholarshipTestDatabase.migrate();
        suiteLock = dataSource.getConnection();
        try (var statement = suiteLock.createStatement(); var result = statement.executeQuery("""
                DECLARE @result int;
                EXEC @result=sys.sp_getapplock @Resource=N'ctsv.hocbong.t01.test-fixtures',
                    @LockMode='Exclusive', @LockOwner='Session', @LockTimeout=5000;
                SELECT @result;
                """)) {
            assertThat(result.next() && result.getInt(1) >= 0).as("exclusive HB test fixture lease").isTrue();
        }

        jdbc = new JdbcTemplate(dataSource);
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("pbl6.ctsv.foundation", "pbl6.ctsv.hocbong.kkht.round.entity");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.getJpaPropertyMap().put("hibernate.hbm2ddl.auto", "validate");
        factory.afterPropertiesSet();
        var emf = factory.getObject();
        var manager = new JpaTransactionManager(emf);
        manager.setDataSource(dataSource);
        manager.afterPropertiesSet();
        em = SharedEntityManagerCreator.createSharedEntityManager(emf);
        tx = new TransactionTemplate(manager);
        repository = new RoundRepository(em, jdbc);
        var usage = new ScholarshipConfiguration().scholarshipFoundationUsage(repository);
        foundation = new FoundationRoundSqlHarness(em, jdbc, manager, json, authStub(), List.of(usage));
        access = new RoundAccess(manager, foundation.identities(), foundation.roles());
        service = roundService(repository);
        workers = Executors.newFixedThreadPool(2);

        tx.executeWithoutResult(status -> {
            specialistRole = jdbc.queryForObject(
                    "SELECT id FROM dbo.vai_tro WHERE ma=N'hoc_bong_chuyen_vien' AND dang_hoat_dong=1", Long.class);
            firstUser = actor(FIRST, "cv-a", true);
            secondUser = actor(SECOND, "cv-b", true);
            actor(WITHOUT_ROLE, "none", false);
            sourceId = source("9410", SEMESTER_ORDER, START, END);
            secondSourceId = source("9420", 209402, LocalDate.of(2095, 1, 16), LocalDate.of(2095, 6, 30));
            summerId = source("9421", 209403, LocalDate.of(2095, 7, 1), LocalDate.of(2095, 8, 1));
        });
    }

    @BeforeEach
    void requireUnoccupiedHbPeriod() {
        assertThat(count("SELECT COUNT_BIG(*) FROM dbo.hb_hoc_ky WHERE nam_hoc=?", YEAR))
                .as("fixture must not reuse a pre-existing HB period").isZero();
        assertThat(aliasCount()).isZero();
        assertThat(count("SELECT COUNT_BIG(*) FROM dbo.vai_tro WHERE id=-1")).isZero();
        assertThat(count("SELECT COUNT_BIG(*) FROM dbo.nguoi_dung WHERE id=-1")).isZero();
    }

    @AfterEach
    void removeOnlyTrackedHbRows() {
        if (tx == null) return;
        tx.executeWithoutResult(status -> {
            for (long id : ownedRounds) {
                jdbc.update("DELETE dbo.kkht_dot_xet WHERE id=? AND nguoi_tao_id IN (?,?) AND ten_dot LIKE ?",
                        id, firstUser, secondUser, MARKER + "%");
            }
            for (long id : ownedAliases) {
                jdbc.update("""
                        DELETE FROM dbo.hb_anh_xa_ky_nguon WHERE id=? AND he_thong_nguon=N'CTSV'
                          AND loai_ma=N'ctsv_hoc_ky_id' AND ma_ky_nguon IN (?,?) AND xac_nhan_boi IN (?,?)
                          AND NOT EXISTS (SELECT 1 FROM dbo.kkht_dot_xet WHERE anh_xa_ky_nguon_id=?)
                        """, id, Long.toString(sourceId), Long.toString(secondSourceId), firstUser, secondUser, id);
            }
            for (long id : ownedPeriods) {
                jdbc.update("""
                        DELETE FROM dbo.hb_hoc_ky WHERE id=? AND nam_hoc=? AND thu_tu_ky IN (1,2)
                          AND NOT EXISTS (SELECT 1 FROM dbo.hb_anh_xa_ky_nguon WHERE hoc_ky_id=?)
                          AND NOT EXISTS (SELECT 1 FROM dbo.kkht_dot_xet WHERE hoc_ky_id=?)
                        """, id, YEAR, id, id);
            }
        });
        ownedRounds.clear();
        ownedAliases.clear();
        ownedPeriods.clear();
    }

    @AfterAll
    void releaseFixtures() throws Exception {
        if (workers != null) workers.shutdownNow();
        try {
            if (factory != null) factory.destroy();
        } finally {
            if (suiteLock != null) suiteLock.close();
        }
    }

    @Test
    void HB_T00_040_sameNumberHasOneWinner() throws Exception {
        long auditBefore = auditCount();
        var outcomes = race(() -> create(FIRST, 1), () -> create(SECOND, 1));
        assertThat(outcomes).filteredOn(o -> o.failure() == null).hasSize(1);
        assertFailure(onlyFailure(outcomes), 409, "ROUND_EXISTS");
        assertPeriodState(1, 1, 1);
        assertThat(auditCount() - auditBefore).isEqualTo(3);
    }

    @Test
    void HB_T00_041_differentNumbersSharePeriodAndAlias() throws Exception {
        long auditBefore = auditCount();
        var outcomes = race(() -> create(FIRST, 1), () -> create(SECOND, 2));
        assertThat(outcomes).allSatisfy(o -> assertThat(o.failure()).isNull());
        assertThat(outcomes.stream().map(o -> o.value().body().semester().id()).distinct()).hasSize(1);
        assertPeriodState(1, 1, 2);
        assertThat(auditCount() - auditBefore).isEqualTo(4);
    }

    @Test
    void HB_T00_042_summerRaceRejectsSummerCreation() throws Exception {
        var outcomes = race(() -> create(service, FIRST, secondSourceId, 1), () -> access.execute(SECOND, true,
                actor -> service.create(new RoundDtos.CreateCommand(summerId, 1, MARKER + " summer"), actor)));
        assertThat(outcomes.getFirst().failure()).isNull();
        assertThat(outcomes.getFirst().value().body().semester().semesterOrder()).isEqualTo(2);
        assertFailure(outcomes.getLast().failure(), 409, "SOURCE_SEMESTER_UNAVAILABLE");
        assertPeriodState(1, 1, 1);
        assertThat(count("SELECT COUNT_BIG(*) FROM dbo.hb_anh_xa_ky_nguon WHERE ma_ky_nguon=?",
                Long.toString(summerId))).isZero();
    }

    @Test
    void HB_T00_043_conflictingUpdatesKeepLosingRootAndTag() throws Exception {
        var a = create(FIRST, 1);
        var b = create(SECOND, 2);
        long auditBefore = auditCount();
        var outcomes = race(() -> update(FIRST, a, 3, "winner candidate A"),
                () -> update(SECOND, b, 3, "winner candidate B"));
        assertFailure(onlyFailure(outcomes), 409, "ROUND_EXISTS");
        var unchanged = outcomes.getFirst().failure() != null ? a : b;
        assertThat(detail(unchanged)).isEqualTo(unchanged);
        assertThat(auditCount() - auditBefore).isEqualTo(1);
    }

    @Test
    void HB_T00_057_sameTagRaceAndRetryRequireReadback() throws Exception {
        var initial = create(FIRST, 1);
        long auditBefore = auditCount();
        var outcomes = race(() -> update(FIRST, initial, 2, "edit A"),
                () -> update(SECOND, initial, 2, "edit B"));
        assertFailure(onlyFailure(outcomes), 412, "VERSION_CONFLICT");
        var winner = outcomes.stream().map(Attempt::value).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        assertThat(winner.etag()).isNotEqualTo(initial.etag());
        assertThatThrownBy(() -> update(FIRST, initial, 2, "edit A"))
                .satisfies(error -> assertFailure(error, 412, "VERSION_CONFLICT"));
        assertThat(detail(initial)).isEqualTo(winner);
        assertThat(auditCount() - auditBefore).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"semester.initialized", "semester.source-linked", "round.created"})
    void HB_T00_044_auditFkFailureRollsBackNewPeriodAndRound(String action) {
        long auditBefore = auditCount();
        var fault = serviceWithAuditFault(action);
        assertAuditFkFailure(() -> create(fault, FIRST, 1));
        assertPeriodState(0, 0, 0);
        assertThat(auditCount()).isEqualTo(auditBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fk_hb_anh_xa_ky_nguon_xac_nhan_boi", "fk_kkht_dot_xet_nguoi_tao"})
    void HB_T00_044_persistFailureRollsBackEarlierWrites(String constraint) {
        long auditBefore = auditCount();
        var writer = roundService(new RoundRepository(em, jdbc) {
            @Override void persist(Object entity) {
                if (entity instanceof SourceSemesterMapping alias && constraint.equals("fk_hb_anh_xa_ky_nguon_xac_nhan_boi")) {
                    alias.setConfirmedBy(-1L);
                }
                if (entity instanceof ScholarshipRound round && constraint.equals("fk_kkht_dot_xet_nguoi_tao")) {
                    round.setCreatedBy(-1L);
                }
                super.persist(entity);
            }
        });
        assertFkFailure(constraint, () -> create(writer, FIRST, 1));
        assertPeriodState(0, 0, 0);
        assertThat(auditCount()).isEqualTo(auditBefore);
    }

    @Test
    void HB_T00_044_auditFkFailurePreservesExistingPeriodAndAlias() {
        var existing = create(FIRST, 1);
        long auditBefore = auditCount();
        assertAuditFkFailure(() -> create(serviceWithAuditFault("round.created"), SECOND, 2));
        assertPeriodState(1, 1, 1);
        assertThat(detail(existing)).isEqualTo(existing);
        assertThat(auditCount()).isEqualTo(auditBefore);
    }

    @Test
    void HB_T00_045_auditFkFailureRollsBackRoundCas() {
        var before = create(FIRST, 1);
        long auditBefore = auditCount();
        var fault = serviceWithAuditFault("round.updated");
        assertAuditFkFailure(() -> access.execute(SECOND, true, actor -> fault.update(Long.parseLong(before.body().id()),
                new RoundDtos.UpdateCommand(2, MARKER + " failed edit"), before.etag(), actor)));
        assertThat(detail(before)).isEqualTo(before);
        assertThat(auditCount()).isEqualTo(auditBefore);
    }

    @Test
    void HB_T00_049_realApplicationLockTimeoutReturnsUnavailable() throws Exception {
        try (var holder = jdbc.getDataSource().getConnection()) {
            holder.setAutoCommit(false);
            try (var statement = holder.createStatement()) {
                statement.execute("""
                        BEGIN TRANSACTION;
                        DECLARE @result int;
                        EXEC @result=sys.sp_getapplock @Resource=N'ctsv.hocbong.kkht.rounds',
                          @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=1000;
                        IF @result < 0 THROW 51909,N'HB test could not hold application lock',1;
                        """);
                assertThatThrownBy(() -> create(FIRST, 1))
                        .satisfies(error -> assertFailure(error, 503, "DEPENDENCY_UNAVAILABLE"));
            } finally {
                holder.rollback();
            }
        }
        assertPeriodState(0, 0, 0);
    }

    @Test
    void HB_T00_050_foundationVerificationWaitsForCreateThenRejectsChangedDates() throws Exception {
        String sourceTag = foundation.semesterTag(sourceId);
        var createHoldingSource = new CountDownLatch(1);
        var releaseCreate = new CountDownLatch(1);
        var verifying = new CountDownLatch(1);
        var writerSession = new AtomicInteger();
        var verifierSession = new AtomicInteger();
        var pausedService = roundService(new RoundRepository(em, jdbc) {
            @Override void persistAudit(ScholarshipAudit event) {
                super.persistAudit(event);
                if ("round.created".equals(event.getAction())) {
                    writerSession.set(jdbc.queryForObject("SELECT @@SPID", Integer.class));
                    createHoldingSource.countDown();
                    await(releaseCreate);
                }
            }
        });
        Future<Attempt> creating = workers.submit(() -> attempt(() -> create(pausedService, FIRST, 1)));
        Future<Throwable> verification;
        try {
            await(createHoldingSource);
            verification = workers.submit(() -> {
                try {
                    tx.executeWithoutResult(status -> {
                        verifierSession.set(jdbc.queryForObject("SELECT @@SPID", Integer.class));
                        verifying.countDown();
                        foundation.verifyDates(secondUser, sourceId, SEMESTER_ORDER, sourceTag, START.plusDays(1), END);
                    });
                    return null;
                } catch (FoundationAccess.Failure failure) {
                    return failure;
                }
            });
            await(verifying);
            assertThat(verifierSession.get()).isPositive().isNotEqualTo(writerSession.get());
            assertThatThrownBy(() -> verification.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        } finally {
            releaseCreate.countDown();
        }
        assertThat(creating.get(15, TimeUnit.SECONDS).failure()).isNull();
        assertThat(verification.get(15, TimeUnit.SECONDS)).isInstanceOfSatisfying(FoundationAccess.Failure.class,
                failure -> {
                    assertThat(failure.status()).isEqualTo(409);
                    assertThat(failure.code()).isEqualTo("SEMESTER_VERIFICATION_CONFLICT");
                });
        assertThat(foundation.semesterTag(sourceId)).isEqualTo(sourceTag);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void HB_T00_056_createSeesCommittedSourceAbsence(boolean withExistingAlias) throws Exception {
        var existing = withExistingAlias ? create(FIRST, 1) : null;
        long auditBefore = auditCount();
        var sourceLocked = new CountDownLatch(1);
        var releaseSource = new CountDownLatch(1);
        var approachingSourceRead = new CountDownLatch(1);
        var activeWriter = roundService(new RoundRepository(em, jdbc) {
            @Override void serializeWrites() {
                super.serializeWrites();
                approachingSourceRead.countDown();
            }
        });
        var markingAbsent = workers.submit(() -> tx.executeWithoutResult(status -> {
            assertThat(jdbc.update("""
                    UPDATE dbo.hoc_ky SET dang_hien_dien_nguon=0 WHERE id=? AND ten=? AND dang_hien_dien_nguon=1
                    """, sourceId, MARKER + " 9410")).isEqualTo(1);
            sourceLocked.countDown();
            await(releaseSource);
        }));
        Future<Attempt> creating = null;
        try {
            try {
                await(sourceLocked);
                var waiting = workers.submit(() -> attempt(() -> create(activeWriter, SECOND, 2)));
                creating = waiting;
                await(approachingSourceRead);
                assertThatThrownBy(() -> waiting.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                releaseSource.countDown();
                markingAbsent.get(15, TimeUnit.SECONDS);
            }
            assertFailure(creating.get(15, TimeUnit.SECONDS).failure(), 409, "SOURCE_SEMESTER_UNAVAILABLE");
            if (existing != null) assertThat(detail(existing)).isEqualTo(existing);
            long expected = withExistingAlias ? 1 : 0;
            assertPeriodState(expected, expected, expected);
            assertThat(auditCount()).isEqualTo(auditBefore);
        } finally {
            try {
                if (creating != null && !creating.isDone()) creating.get(35, TimeUnit.SECONDS);
            } finally {
                jdbc.update("""
                        UPDATE dbo.hoc_ky SET dang_hien_dien_nguon=1 WHERE id=? AND ten=? AND dang_hien_dien_nguon=0
                        """, sourceId, MARKER + " 9410");
            }
        }
    }

    @Test
    void HB_T00_055_usesRealSourceProjectionAndRealGlobalRoleLookup() {
        var page = access.execute(FIRST, true, actor -> service.sources(MARKER, new int[]{0, 100}));
        assertThat(page.items()).hasSize(3);
        assertThat(page.items()).filteredOn(item -> item.code().equals("9410")).singleElement().satisfies(item -> {
            assertThat(item.sourceSemesterId()).isEqualTo(Long.toString(sourceId));
            assertThat(item.academicYear()).isEqualTo(YEAR);
            assertThat(item.semesterOrder()).isEqualTo(1);
            assertThat(item.selectable()).isTrue();
        });
        assertThat(page.items()).filteredOn(item -> item.code().equals("9421")).singleElement().satisfies(item -> {
            assertThat(item.selectable()).isFalse();
            assertThat(item.unavailableReason()).isEqualTo("SUMMER_MERGED_INTO_SEMESTER_2");
        });
        assertThatThrownBy(() -> access.execute(WITHOUT_ROLE, true, actor -> service.sources(null, new int[]{0, 20})))
                .satisfies(error -> assertFailure(error, 403, "FORBIDDEN"));
    }

    private RoundDtos.View create(AuthAccess.Principal principal, int number) {
        return create(service, principal, number);
    }

    private RoundDtos.View create(RoundService writer, AuthAccess.Principal principal, int number) {
        return create(writer, principal, sourceId, number);
    }

    private RoundDtos.View create(RoundService writer, AuthAccess.Principal principal, long selectedSource, int number) {
        return access.execute(principal, true, actor -> {
            var result = writer.create(new RoundDtos.CreateCommand(selectedSource, number, MARKER + " round " + number), actor);
            long roundId = Long.parseLong(result.body().id());
            ownedRounds.add(roundId);
            recordCreatedAncestors(roundId, actor.userId());
            return result;
        });
    }

    private void recordCreatedAncestors(long roundId, long creator) {
        record CreatedObject(String action, long id) {}
        var ancestors = jdbc.query("""
                SELECT candidate.hanh_dong, candidate.doi_tuong_id
                FROM dbo.hb_nhat_ky created JOIN dbo.hb_nhat_ky candidate
                  ON candidate.correlation_id=created.correlation_id
                  AND candidate.nguoi_dung_id=created.nguoi_dung_id AND candidate.pham_vi=created.pham_vi
                WHERE created.loai_doi_tuong=N'kkht_dot_xet' AND created.hanh_dong=N'round.created'
                  AND created.doi_tuong_id=? AND created.nguoi_dung_id=?
                  AND ((candidate.hanh_dong=N'semester.initialized' AND candidate.loai_doi_tuong=N'hb_hoc_ky')
                    OR (candidate.hanh_dong=N'semester.source-linked' AND candidate.loai_doi_tuong=N'hb_anh_xa_ky_nguon'))
                """, (row, ignored) -> new CreatedObject(row.getString(1), row.getLong(2)), roundId, creator);
        for (var ancestor : ancestors) {
            if (ancestor.action().equals("semester.initialized")) ownedPeriods.add(ancestor.id());
            else ownedAliases.add(ancestor.id());
        }
    }

    private RoundDtos.View detail(RoundDtos.View view) {
        return access.execute(FIRST, true, actor -> service.detail(Long.parseLong(view.body().id())));
    }

    private RoundDtos.View update(AuthAccess.Principal principal, RoundDtos.View initial, int number, String name) {
        return access.execute(principal, true, actor -> service.update(Long.parseLong(initial.body().id()),
                new RoundDtos.UpdateCommand(number, MARKER + " " + name), initial.etag(), actor));
    }

    private RoundService roundService(RoundRepository repo) {
        return new RoundService(repo, foundation.source(), json, Clock.systemUTC());
    }

    private RoundService serviceWithAuditFault(String action) {
        return roundService(new RoundRepository(em, jdbc) {
            @Override void persistAudit(ScholarshipAudit event) {
                if (action.equals(event.getAction())) event.setRoleId(-1L);
                super.persistAudit(event);
            }
        });
    }

    private static void assertAuditFkFailure(Supplier<RoundDtos.View> work) {
        assertFkFailure("fk_hb_nhat_ky_vai_tro", work);
    }

    private static void assertFkFailure(String constraint, Supplier<RoundDtos.View> work) {
        assertThatThrownBy(work::get).isInstanceOfSatisfying(ConstraintViolationException.class,
                error -> assertThat(error.getSQLException().getMessage()).contains(constraint));
    }

    private record Attempt(RoundDtos.View value, RoundFailure failure) {}

    private static Attempt attempt(Supplier<RoundDtos.View> work) {
        try {
            return new Attempt(work.get(), null);
        } catch (RoundFailure failure) {
            return new Attempt(null, failure);
        }
    }

    private List<Attempt> race(Supplier<RoundDtos.View> first, Supplier<RoundDtos.View> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        var sessions = ConcurrentHashMap.<Integer>newKeySet();
        var a = workers.submit(() -> racing(first, barrier, sessions));
        var b = workers.submit(() -> racing(second, barrier, sessions));
        var results = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        assertThat(sessions).as("two overlapping SQL Server sessions").hasSize(2);
        return results;
    }

    private Attempt racing(Supplier<RoundDtos.View> action, CyclicBarrier barrier, Set<Integer> sessions) {
        return attempt(() -> tx.execute(status -> {
            sessions.add(jdbc.queryForObject("SELECT @@SPID", Integer.class));
            try {
                barrier.await(5, TimeUnit.SECONDS);
            } catch (Exception error) {
                throw new IllegalStateException("Concurrent transactions did not reach the SQL barrier", error);
            }
            return action.get();
        }));
    }

    private static RoundFailure onlyFailure(List<Attempt> outcomes) {
        assertThat(outcomes).filteredOn(o -> o.failure() != null).hasSize(1);
        return outcomes.stream().map(Attempt::failure).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
    }

    private static void assertFailure(Throwable error, int status, String code) {
        assertThat(error).isInstanceOfSatisfying(RoundFailure.class, failure -> {
            assertThat(failure.status).isEqualTo(status);
            assertThat(failure.code).isEqualTo(code);
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).as("concurrency latch").isTrue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for SQL fixture transaction", error);
        }
    }

    private long count(String sql, Object... parameters) {
        return jdbc.queryForObject(sql, Long.class, parameters);
    }

    private long aliasCount() {
        return count("SELECT COUNT_BIG(*) FROM dbo.hb_anh_xa_ky_nguon WHERE ma_ky_nguon IN (?,?,?)",
                Long.toString(sourceId), Long.toString(secondSourceId), Long.toString(summerId));
    }

    private long auditCount() {
        return count("SELECT COUNT_BIG(*) FROM dbo.hb_nhat_ky WHERE nguoi_dung_id IN (?,?)", firstUser, secondUser);
    }

    private void assertPeriodState(long periods, long aliases, long rounds) {
        assertThat(count("SELECT COUNT_BIG(*) FROM dbo.hb_hoc_ky WHERE nam_hoc=?", YEAR)).isEqualTo(periods);
        assertThat(aliasCount()).isEqualTo(aliases);
        assertThat(count("""
                SELECT COUNT_BIG(*) FROM dbo.kkht_dot_xet r JOIN dbo.hb_hoc_ky h ON h.id=r.hoc_ky_id
                WHERE h.nam_hoc=?
                """, YEAR)).isEqualTo(rounds);
    }

    private long actor(AuthAccess.Principal principal, String label, boolean grantRole) {
        String email = "hb-t01-" + label + "@example.invalid";
        String name = MARKER + " " + label;
        var records = jdbc.query("SELECT id,ho_ten,dang_hoat_dong,thoi_diem_xoa FROM dbo.nguoi_dung WHERE dia_chi_email=?",
                (row, ignored) -> {
                    assertThat(row.getString(2)).isEqualTo(name);
                    assertThat(row.getBoolean(3)).isTrue();
                    assertThat(row.getObject(4)).isNull();
                    return row.getLong(1);
                }, email);
        long id = records.isEmpty() ? jdbc.queryForObject("""
                INSERT dbo.nguoi_dung(dia_chi_email,ho_ten,dang_hoat_dong) OUTPUT INSERTED.id VALUES (?,?,1)
                """, Long.class, email, name) : records.getFirst();
        var bindings = jdbc.query("""
                SELECT nguoi_dung_id,thoi_diem_xac_minh FROM dbo.dinh_danh_dang_nhap
                WHERE ma_nha_cung_cap=? AND ma_chu_the_dang_nhap=?
                """, (row, ignored) -> {
                    assertThat(row.getObject(2)).isNotNull();
                    return row.getLong(1);
                }, ISSUER, principal.accountId().toString());
        if (bindings.isEmpty()) {
            jdbc.update("""
                    INSERT dbo.dinh_danh_dang_nhap(nguoi_dung_id,ma_nha_cung_cap,ma_chu_the_dang_nhap,thoi_diem_xac_minh)
                    VALUES (?,?,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
                    """, id, ISSUER, principal.accountId().toString());
        } else {
            assertThat(bindings).containsExactly(id);
        }
        long grants = count("""
                SELECT COUNT_BIG(*) FROM dbo.phan_quyen_nguoi_dung WHERE nguoi_dung_id=?
                  AND vai_tro_id=? AND ma_phan_he=N'HOC_BONG'
                """, id, specialistRole);
        if (grantRole && grants == 0) {
            jdbc.update("INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he) VALUES (?,?,N'HOC_BONG')",
                    id, specialistRole);
        } else {
            assertThat(grants).isEqualTo(grantRole ? 1 : 0);
        }
        return id;
    }

    private long source(String code, int order, LocalDate start, LocalDate end) {
        String name = MARKER + " " + code;
        var records = jdbc.query("""
                SELECT id,ten,so_thu_tu,ngay_bat_dau,ngay_ket_thuc,trang_thai_xac_minh_moc,dang_hien_dien_nguon
                FROM dbo.hoc_ky WHERE ma=?
                """, (row, ignored) -> {
                    assertThat(row.getString("ten")).isEqualTo(name);
                    assertThat(row.getInt("so_thu_tu")).isEqualTo(order);
                    assertThat(row.getDate("ngay_bat_dau").toLocalDate()).isEqualTo(start);
                    assertThat(row.getDate("ngay_ket_thuc").toLocalDate()).isEqualTo(end);
                    assertThat(row.getString("trang_thai_xac_minh_moc")).isEqualTo("verified");
                    assertThat(row.getBoolean("dang_hien_dien_nguon")).isTrue();
                    return row.getLong("id");
                }, code);
        return records.isEmpty() ? jdbc.queryForObject("""
                INSERT dbo.hoc_ky(ma,ten,so_thu_tu,ngay_bat_dau,ngay_ket_thuc,trang_thai_xac_minh_moc,
                    can_cu_xac_minh_moc,dang_hien_dien_nguon)
                OUTPUT INSERTED.id VALUES (?,?,?,?,?,N'verified',?,1)
                """, Long.class, code, name, order, start, end, MARKER) : records.getFirst();
    }

    private static AuthAccess.Principal principal(String suffix) {
        return new AuthAccess.Principal(ISSUER, UUID.fromString("94100000-0000-0000-0000-00000000" + suffix), 1L);
    }

    private static AuthAccess authStub() {
        return new AuthAccess() {
            @Override public Principal verifyAccessToken(String token) {
                throw new UnsupportedOperationException("SQL service harness supplies synthetic principals directly");
            }

            @Override public State requireCtsvAccess(Principal principal, UUID mappedAccount) {
                assertThat(principal.issuer()).isEqualTo(ISSUER);
                assertThat(principal.accountId()).isEqualTo(mappedAccount);
                return new State(mappedAccount, true, "1", true);
            }
        };
    }
}
