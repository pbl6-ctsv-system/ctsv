package pbl6.ctsv.drl;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.drl.config.DrlConfiguration;
import pbl6.ctsv.drl.config.DrlPhysicalNamingStrategy;
import pbl6.ctsv.drl.entity.DrlEntity;
import pbl6.ctsv.drl.entity.RlBieuMau;
import pbl6.ctsv.drl.entity.RlBieuMauCt;
import pbl6.ctsv.drl.entity.RlDot;
import pbl6.ctsv.drl.entity.RlHopLop;
import pbl6.ctsv.drl.entity.RlKetQuaSv;
import pbl6.ctsv.drl.entity.RlKhoaDot;
import pbl6.ctsv.drl.entity.RlLopDot;
import pbl6.ctsv.drl.entity.RlNhomTieuChi;
import pbl6.ctsv.drl.entity.RlPhieu;
import pbl6.ctsv.drl.entity.RlPhieuDiemCt;
import pbl6.ctsv.drl.entity.RlVanBanKy;
import pbl6.ctsv.drl.entity.RlXepLoai;
import pbl6.ctsv.drl.repository.DrlRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full CTSV entity validation on SQL Server with Boot JPA and production DrlConfiguration.
 * Guarded DRL fresh DB only; fixtures roll back. DEV is verified separately.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DrlPersistenceIT {
    ConfigurableApplicationContext context;
    jakarta.persistence.EntityManagerFactory factory;
    EntityManager em;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    DrlRepository repo;
    static final OffsetDateTime TIME = OffsetDateTime.parse("2099-05-20T08:09:10.1234567+05:45");
    static final List<Class<? extends DrlEntity>> TABLES = List.of(
            RlBieuMau.class, RlNhomTieuChi.class, RlBieuMauCt.class, RlXepLoai.class,
            RlDot.class, RlKhoaDot.class, RlLopDot.class, RlPhieu.class,
            RlPhieuDiemCt.class, RlHopLop.class, RlVanBanKy.class);

    @BeforeAll void validateRealSchema() throws Exception {
        var ds = DrlTestDatabase.source(DrlTestDatabase.FRESH);
        DrlTestDatabase.flyway(ds, DrlTestDatabase.TARGET).validate();
        var application = new AnnotationConfigApplicationContext();
        context = application;
        TestPropertyValues.of("spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false")
                .applyTo(application);
        AutoConfigurationPackages.register(application, "pbl6.ctsv");
        application.registerBean(DataSource.class, () -> ds);
        application.register(SqlConfiguration.class);
        application.refresh();
        factory = application.getBean(jakarta.persistence.EntityManagerFactory.class);
        jdbc = application.getBean(JdbcTemplate.class);
        em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        tx = new TransactionTemplate(application.getBean(JpaTransactionManager.class));
        assertThat(application.getBeanNamesForType(DrlRepository.class))
                .withFailMessage("DRL repository missing; EntityManagers=%s, JdbcTemplates=%s, conditions=%s",
                        Arrays.toString(application.getBeanNamesForType(EntityManager.class)),
                        Arrays.toString(application.getBeanNamesForType(JdbcTemplate.class)),
                        org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport.get(application.getBeanFactory())
                                .getConditionAndOutcomesBySource().entrySet().stream()
                                .filter(entry -> entry.getKey().contains("drl.config"))
                                .flatMap(entry -> java.util.stream.StreamSupport.stream(entry.getValue().spliterator(), false)
                                        .map(outcome -> entry.getKey() + ": " + outcome.getOutcome().getMessage())).toList())
                .hasSize(1);
        repo = application.getBean(DrlRepository.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ImportAutoConfiguration({HibernateJpaAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class, TransactionAutoConfiguration.class})
    @Import(DrlConfiguration.class)
    static class SqlConfiguration {}

    @AfterAll void close() {
        if (context != null) context.close();
    }

    @Test void validatesAllMonolithEntitiesWithDrlNamingStrategy() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        var expected = scanner.findCandidateComponents("pbl6.ctsv").stream()
                .map(definition -> definition.getBeanClassName()).toList();
        var actual = factory.getMetamodel().getEntities().stream()
                .map(entity -> entity.getJavaType().getName()).toList();
        assertThat(actual).as("All CTSV @Entity classes in Hibernate's validated metamodel")
                .containsExactlyInAnyOrderElementsOf(expected);
        var moduleCounts = actual.stream().collect(Collectors.groupingBy(
                name -> name.split("\\.")[2], TreeMap::new, Collectors.counting()));
        assertThat(moduleCounts.keySet())
                .contains("drl", "foundation", "mghp", "hocbong", "shcn");
        assertThat(factory.getProperties()).containsEntry("hibernate.hbm2ddl.auto", "validate")
                .containsEntry("hibernate.hbm2ddl.jdbc_metadata_extraction_strategy", "individually");
        assertThat(context.getBean(org.hibernate.boot.model.naming.PhysicalNamingStrategy.class))
                .isInstanceOf(DrlPhysicalNamingStrategy.class);
        System.out.printf("CTSV schema validated on %s: %d entities %s%n",
                DrlTestDatabase.FRESH, actual.size(), moduleCounts);
    }

    @Test void registersElevenTablesAndReadOnlyView() {
        var expected = new ArrayList<Class<?>>(TABLES);
        expected.add(RlKetQuaSv.class);
        var drlEntities = factory.getMetamodel().getEntities().stream()
                .<Class<?>>map(entity -> entity.getJavaType())
                .filter(type -> type.getPackageName().startsWith("pbl6.ctsv.drl."))
                .toList();
        assertThat(drlEntities).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(RlKetQuaSv.class).hasAnnotation(Immutable.class);
        assertThat(DrlEntity.class.isAssignableFrom(RlKetQuaSv.class)).isFalse();
        assertThat(Arrays.stream(RlKetQuaSv.class.getMethods()).map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.startsWith("set"));
    }

    @Test void matchesAll75TableColumnsNineViewColumnsAnd21ScalarForeignKeys() {
        var types = new ArrayList<Class<?>>(TABLES);
        types.add(RlKetQuaSv.class);
        int columns = 0;
        for (var type : types) {
            String table = table(type);
            var metadata = jdbc.queryForList("""
                    SELECT c.name, t.name AS sql_type, c.is_nullable, c.max_length, c.precision, c.scale,
                           c.is_identity, CONVERT(bigint, i.seed_value) AS seed_value,
                           CONVERT(bigint, i.increment_value) AS increment_value
                    FROM sys.columns c JOIN sys.types t ON t.user_type_id=c.user_type_id
                    LEFT JOIN sys.identity_columns i ON i.object_id=c.object_id AND i.column_id=c.column_id
                    WHERE c.object_id=OBJECT_ID(?) ORDER BY c.column_id
                    """, "dbo." + table);
            var fields = fields(type);
            assertThat(fields).as(table).hasSize(metadata.size());
            if (type == RlKetQuaSv.class) assertThat(fields).hasSize(9);
            else columns += fields.size();
            for (var column : metadata) {
                String name = (String) column.get("name");
                var field = fields.stream().filter(f -> unquote(f.getAnnotation(Column.class).name()).equals(name))
                        .findFirst().orElseThrow(() -> new AssertionError(table + "." + name));
                var mapping = field.getAnnotation(Column.class);
                assertThat(mapping.nullable()).as(table + "." + name).isEqualTo(column.get("is_nullable"));
                switch ((String) column.get("sql_type")) {
                    case "bigint" -> assertThat(field.getType()).isEqualTo(Long.class);
                    case "int" -> assertThat(field.getType()).isEqualTo(Integer.class);
                    case "tinyint" -> {
                        assertThat(field.getType()).isEqualTo(Short.class);
                        assertThat(field.getAnnotation(JdbcTypeCode.class).value()).isEqualTo(SqlTypes.TINYINT);
                    }
                    case "bit" -> assertThat(field.getType()).isEqualTo(Boolean.class);
                    case "decimal" -> {
                        assertThat(field.getType()).isEqualTo(BigDecimal.class);
                        assertThat(mapping.precision()).isEqualTo(((Number) column.get("precision")).intValue()).isEqualTo(5);
                        assertThat(mapping.scale()).isEqualTo(((Number) column.get("scale")).intValue()).isEqualTo(2);
                    }
                    case "nvarchar" -> {
                        assertThat(field.getType()).isEqualTo(String.class);
                        assertThat(field.isAnnotationPresent(Nationalized.class)).isTrue();
                        int length = ((Number) column.get("max_length")).intValue();
                        if (length == -1) assertThat(mapping.columnDefinition()).isEqualTo("nvarchar(max)");
                        else assertThat(mapping.length()).isEqualTo(length / 2);
                    }
                    case "datetimeoffset" -> {
                        assertThat(field.getType()).isEqualTo(OffsetDateTime.class);
                        assertThat(field.getAnnotation(TimeZoneStorage.class).value()).isEqualTo(TimeZoneStorageType.NATIVE);
                        assertThat(mapping.columnDefinition()).isEqualTo("datetimeoffset(7)");
                    }
                    default -> throw new AssertionError("Unexpected SQL type: " + column);
                }
                if (Boolean.TRUE.equals(column.get("is_identity"))) {
                    assertThat(field.getAnnotation(GeneratedValue.class).strategy()).isEqualTo(GenerationType.IDENTITY);
                    assertThat(column.get("seed_value")).isEqualTo(1L);
                    assertThat(column.get("increment_value")).isEqualTo(1L);
                }
            }
            assertThat(factory.getMetamodel().entity(type).getAttributes())
                    .noneMatch(jakarta.persistence.metamodel.Attribute::isAssociation);
        }
        assertThat(columns).isEqualTo(75);
        var fks = jdbc.queryForList("""
                SELECT OBJECT_NAME(f.parent_object_id) AS table_name, c.name AS column_name
                FROM sys.foreign_key_columns f JOIN sys.columns c
                  ON c.object_id=f.parent_object_id AND c.column_id=f.parent_column_id
                WHERE OBJECT_NAME(f.parent_object_id) LIKE 'RL%'
                """);
        assertThat(fks).hasSize(21);
        for (var fk : fks) {
            var type = TABLES.stream().filter(t -> table(t).equals(fk.get("table_name"))).findFirst().orElseThrow();
            assertThat(fields(type).stream().filter(f -> unquote(f.getAnnotation(Column.class).name())
                    .equals(fk.get("column_name"))).findFirst().orElseThrow().getType()).isEqualTo(Long.class);
        }
    }

    @Test void roundTripsAllElevenEntitiesEveryColumnAndDossierTimestamp() {
        rollback(f -> {
            assertNullDraft(f);
            completeValues(f);
            var expected = new LinkedHashMap<DrlEntity, Map<String, Object>>();
            for (var entity : f.entities()) expected.put(entity, values(entity));
            repo.flushAndClear();
            for (var entry : expected.entrySet()) {
                var entity = entry.getKey();
                var reloaded = repo.find(entity.getClass(), entity.getId()).orElseThrow();
                assertThat(values(reloaded)).as(entity.getClass().getSimpleName()).isEqualTo(entry.getValue());
                // Independent native column check catches swapped mappings as well as a symmetric JPA round-trip.
                var raw = jdbc.queryForMap("SELECT * FROM dbo." + table(entity.getClass()) + " WHERE ID=?", entity.getId());
                for (var field : fields(entity.getClass())) {
                    String column = unquote(field.getAnnotation(Column.class).name());
                    Object actual = raw.get(column);
                    if (actual instanceof microsoft.sql.DateTimeOffset) {
                        actual = jdbc.queryForObject("SELECT [" + column + "] FROM dbo." + table(entity.getClass()) + " WHERE ID=?",
                                (row, n) -> row.getObject(1, OffsetDateTime.class), entity.getId());
                    } else if (field.getType() == Short.class && actual instanceof Number number) actual = number.shortValue();
                    assertThat(actual).as(table(entity.getClass()) + "." + column).isEqualTo(entry.getValue().get(field.getName()));
                }
            }
            assertThat(repo.find(RlLopDot.class, f.root().getId()).orElseThrow().getNgayLapHoSo()).isEqualTo(TIME.plusDays(8));
        });
    }

    @Test void typedQueriesCoverAllTablesAndStayWithinTheirForeignKeyScope() {
        rollback(f -> {
            assertThat(repo.formsByCode(f.form().getMaBieuMau())).extracting(DrlEntity::getId).containsExactly(f.form().getId());
            assertThat(repo.groupsForForm(f.form().getId())).extracting(DrlEntity::getId).containsExactly(f.group().getId());
            assertThat(repo.criteriaForGroup(f.group().getId())).extracting(DrlEntity::getId)
                    .containsExactly(f.criterion().getId(), f.guidance().getId());
            assertThat(repo.ratingsForForm(f.form().getId())).extracting(RlXepLoai::getDiemToiThieu)
                    .containsExactly(new BigDecimal("0.00"), new BigDecimal("20.10"));
            assertThat(repo.roundsForSemester(f.round().getHocKyId())).extracting(DrlEntity::getId).containsExactly(f.round().getId());
            assertThat(repo.facultiesForRound(f.round().getId())).extracting(DrlEntity::getId).containsExactly(f.faculty().getId());
            assertThat(repo.classesForFacultyRound(f.faculty().getId())).extracting(DrlEntity::getId).containsExactly(f.root().getId());
            assertThat(repo.ballotsForClass(f.root().getId())).extracting(DrlEntity::getId).containsExactly(f.ballot().getId());
            assertThat(repo.scoresForBallot(f.ballot().getId())).extracting(DrlEntity::getId).containsExactly(f.score().getId());
            assertThat(repo.meetingForClass(f.root().getId())).contains(f.meeting());
            assertThat(repo.documentsForClass(f.root().getId())).extracting(RlVanBanKy::getLoaiVanBan).containsExactly((short) 1, (short) 2);
            assertThat(repo.find(RlDot.class, -1)).isEmpty();
            assertThat(repo.groupsForForm(-1)).isEmpty();
            assertThat(repo.criteriaForGroup(-1)).isEmpty();
            assertThat(repo.ratingsForForm(-1)).isEmpty();
            assertThat(repo.roundsForSemester(-1)).isEmpty();
            assertThat(repo.facultiesForRound(-1)).isEmpty();
            assertThat(repo.classesForFacultyRound(-1)).isEmpty();
            assertThat(repo.ballotsForClass(-1)).isEmpty();
            assertThat(repo.scoresForBallot(-1)).isEmpty();
            assertThat(repo.meetingForClass(-1)).isEmpty();
            assertThat(repo.documentsForClass(-1)).isEmpty();
        });
    }

    @Test void viewReadsAllNineColumnsAndRecomputesAfterFlushWithoutStaleManagedResults() {
        rollback(f -> {
            var draft = repo.resultForBallot(f.ballot().getId()).orElseThrow();
            assertThat(draft.getPhieuId()).isEqualTo(f.ballot().getId());
            assertThat(draft.getSinhVienId()).isEqualTo(f.ballot().getSinhVienId());
            assertThat(draft.getLopDotId()).isEqualTo(f.root().getId());
            assertThat(draft.getDotId()).isEqualTo(f.round().getId());
            assertThat(draft.getHocKyId()).isEqualTo(f.round().getHocKyId());
            assertThat(draft.getTongDiemCn()).isNull();
            assertThat(draft.getTongDiemLop()).isNull();
            assertThat(draft.getXepLoai()).isNull();
            assertThat(draft.getDaCongBo()).isFalse();
            assertThat(em.contains(draft)).isFalse();
            completeValues(f);
            var confirmed = repo.resultForBallot(f.ballot().getId()).orElseThrow();
            assertThat(confirmed.getTongDiemCn()).isEqualByComparingTo("12.34");
            assertThat(confirmed.getTongDiemLop()).isEqualByComparingTo("23.45");
            assertThat(confirmed.getXepLoai()).isEqualTo("Đạt yêu cầu");
            assertThat(confirmed.getDaCongBo()).isFalse();
            f.round().setTrangThai((short) 1);
            repo.flush();
            f.round().setTrangThai((short) 2);
            assertThat(repo.resultForBallot(f.ballot().getId()).orElseThrow().getDaCongBo()).isTrue();
            assertThat(repo.resultsForClass(f.root().getId())).hasSize(1);
            assertThat(repo.resultsForRound(f.round().getId())).hasSize(1);
            assertThat(repo.resultForBallot(-1)).isEmpty();
            assertThat(repo.resultsForClass(-1)).isEmpty();
            assertThat(repo.resultsForRound(-1)).isEmpty();
            assertThat(draft.getTongDiemCn()).isNull(); // detached snapshot is stable
        });
    }

    @Test void nativeClassLockFlushesPendingChangesAndRefreshesCachedRoot() {
        rollback(f -> {
            f.meeting().setDiaDiem("Đã flush trước khóa");
            jdbc.update("UPDATE dbo.RLLopDot SET NgayGuiKhoa=? WHERE ID=?", TIME, f.root().getId());
            assertThat(f.root().getNgayGuiKhoa()).isNull();
            assertThat(repo.lockLopDot(f.root().getId()).orElseThrow()).isSameAs(f.root());
            assertThat(f.root().getNgayGuiKhoa()).isEqualTo(TIME);
            assertThat(jdbc.queryForObject("SELECT DiaDiem FROM dbo.RLHopLop WHERE ID=?", String.class, f.meeting().getId()))
                    .isEqualTo("Đã flush trước khóa");
            assertThat(repo.lockLopDot(-1)).isEmpty();
        });
    }

    @Test void jpaAndJdbcShareRollbackAndManagedDeletesDoNotMergeDetachedGraphs() {
        String code = "D22-" + UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            var form = new RlBieuMau();
            form.setMaBieuMau(code); form.setSoPhienBan(1); form.setTenBieuMau("Có thể xóa"); form.setDiemToiDa(new BigDecimal("100.00"));
            repo.persist(form);
            long term = insert("INSERT dbo.hoc_ky(ma) VALUES (?)", code);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RLBieuMau WHERE ID=?", Integer.class, form.getId())).isEqualTo(1);
            repo.remove(form); repo.flush();
            assertThat(repo.find(RlBieuMau.class, form.getId())).isEmpty();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.hoc_ky WHERE id=?", Integer.class, term)).isEqualTo(1);
            var other = new RlBieuMau();
            other.setMaBieuMau(code + "-R"); other.setSoPhienBan(1); other.setTenBieuMau("Rollback"); other.setDiemToiDa(BigDecimal.ZERO);
            repo.persist(other); repo.flushAndClear();
            assertThatThrownBy(() -> repo.remove(other)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> repo.persist(other)).isInstanceOf(IllegalArgumentException.class);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.hoc_ky WHERE ma=?", Integer.class, code)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RLBieuMau WHERE MaBieuMau IN (?,?)", Integer.class, code, code + "-R")).isZero();
    }

    @Test void requiresCallerTransactionAndCannotWriteTheReadModelThroughRepository() {
        assertThatThrownBy(() -> repo.find(RlDot.class, -1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repo.lockLopDot(-1)).isInstanceOf(IllegalStateException.class);
        // Even erased/raw calls cannot admit the view to generic persistence operations.
        tx.executeWithoutResult(status -> {
            assertThatThrownBy(() -> repo.find((Class) RlKetQuaSv.class, -1)).isInstanceOf(IllegalArgumentException.class);
            status.setRollbackOnly();
        });
    }

    @Test void dossierFreezeStillRaises51716ThroughManagedEntityFlush() {
        assertThatThrownBy(() -> rollback(f -> {
            completeValues(f);
            repo.flush();
            f.ballot().setNgayXnSv(TIME.plusDays(20));
            repo.flush();
        })).satisfies(error -> {
            Throwable cause = error;
            while (cause != null && !(cause instanceof SQLException)) cause = cause.getCause();
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getErrorCode()).isEqualTo(51716);
        });
    }

    private void assertNullDraft(Fixture f) {
        for (var entity : f.entities()) repo.refresh(entity);
        assertThat(repo.find(RlLopDot.class, f.root().getId()).orElseThrow().getNgayLapHoSo()).isNull();
        var ballot = repo.find(RlPhieu.class, f.ballot().getId()).orElseThrow();
        assertThat(ballot.getThamDuHop()).isNull();
        assertThat(ballot.getXacNhanSv()).isFalse();
        assertThat(ballot.getXacNhanLop()).isFalse();
        assertThat(ballot.getKhongDanhGia()).isFalse();
        assertThat(repo.find(RlPhieuDiemCt.class, f.score().getId()).orElseThrow().getDiemCn()).isNull();
        assertThat(repo.find(RlPhieuDiemCt.class, f.score().getId()).orElseThrow().getDiemLop()).isNull();
        assertThat(repo.find(RlHopLop.class, f.meeting().getId()).orElseThrow().getNoiDung()).isNull();
    }

    private void completeValues(Fixture f) {
        f.score().setDiemCn(new BigDecimal("12.34")); f.score().setDiemLop(new BigDecimal("23.45"));
        repo.flush(); // scores precede individual confirmation
        f.ballot().setXacNhanSv(true); f.ballot().setNgayXnSv(TIME.plusDays(1));
        f.ballot().setXacNhanLop(true); f.ballot().setNgayXnLop(TIME.plusDays(2));
        f.ballot().setThamDuHop((short) 2); f.ballot().setKhongDanhGia(true);
        f.faculty().setDaiDienKhoaId(f.staff()); f.faculty().setNgayGuiCtsv(TIME.plusDays(5));
        f.root().setLopTruongId(f.ballot().getSinhVienId()); f.root().setGvcnId(f.staff());
        f.root().setNgayGuiKhoa(TIME.plusDays(4)); f.root().setNguoiXnCtsvId(f.user()); f.root().setNgayXnCtsv(TIME.plusDays(6));
        f.meeting().setBatDauHop(TIME.plusDays(2)); f.meeting().setKetThucHop(TIME.plusDays(2).plusHours(1));
        f.meeting().setDiaDiem("Phòng Đào tạo – Đà Nẵng"); f.meeting().setNoiDung("Nội dung họp\n" + "Tiếng Việt ấ ộ đ. ".repeat(500));
        f.meeting().setKienNghi("Kiến nghị: hỗ trợ sinh viên\n" + "Đề nghị xác nhận. ".repeat(500));
        repo.flush(); // meeting and roster are complete before the V26 freeze transition
        f.root().setNgayLapHoSo(TIME.plusDays(8));
        repo.flush();
    }

    private record Fixture(RlBieuMau form, RlNhomTieuChi group, RlBieuMauCt criterion, RlBieuMauCt guidance,
                           RlDot round, RlKhoaDot faculty, RlLopDot root, RlPhieu ballot, RlPhieuDiemCt score,
                           RlHopLop meeting, List<DrlEntity> entities, long staff, long user) {}

    private void rollback(Consumer<Fixture> action) {
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            action.accept(fixture());
        });
    }

    private Fixture fixture() {
        String code = "D22-" + UUID.randomUUID();
        long term = insert("INSERT dbo.hoc_ky(ma) VALUES (?)", code);
        long facultyId = insert("INSERT dbo.don_vi(ma) VALUES (?)", code);
        long classroom = insert("INSERT dbo.lop_sinh_hoat(ma,khoa_quan_ly_id) VALUES (?,?)", code, facultyId);
        long student = insert("INSERT dbo.sinh_vien(mssv,lop_hien_tai_id) VALUES (?,?)", code, classroom);
        long staff = insert("INSERT dbo.can_bo(ma_cb) VALUES (?)", code);
        long user = insert("INSERT dbo.nguoi_dung(dia_chi_email) VALUES (?)", code + "@example.invalid");
        var entities = new ArrayList<DrlEntity>();
        var form = new RlBieuMau(); form.setMaBieuMau(code); form.setSoPhienBan(3);
        form.setTenBieuMau("Biểu mẫu rèn luyện tiếng Việt Đà Nẵng"); form.setDiemToiDa(new BigDecimal("100.00"));
        form.setHuongDanChung("Hướng dẫn chung ấ ộ đ. ".repeat(500)); add(entities, form);
        var group = new RlNhomTieuChi(); group.setBieuMauId(form.getId()); group.setTenNhom("Ý thức học tập");
        group.setThuTu(7); group.setDiemToiDa(new BigDecimal("99.99")); add(entities, group);
        var criterion = new RlBieuMauCt(); criterion.setNhomTieuChiId(group.getId()); criterion.setMaNoiDung("ĐRL.1");
        criterion.setLoaiDong((short) 0); criterion.setNoiDung("Tiêu chí chấm điểm ấ ộ đ. ".repeat(500));
        criterion.setThuTu(3); criterion.setMucLuc("I.1.a"); criterion.setDiemToiDa(new BigDecimal("80.25")); add(entities, criterion);
        var guidance = new RlBieuMauCt(); guidance.setNhomTieuChiId(group.getId()); guidance.setMaNoiDung("HD.1");
        guidance.setLoaiDong((short) 1); guidance.setTieuChiChaId(criterion.getId()); guidance.setNoiDung("Hướng dẫn chấm");
        guidance.setThuTu(4); add(entities, guidance);
        var low = new RlXepLoai(); low.setBieuMauId(form.getId()); low.setDiemToiThieu(new BigDecimal("0.00")); low.setTenXepLoai("Chưa đạt"); add(entities, low);
        var rating = new RlXepLoai(); rating.setBieuMauId(form.getId()); rating.setDiemToiThieu(new BigDecimal("20.10")); rating.setTenXepLoai("Đạt yêu cầu"); add(entities, rating);
        var round = new RlDot(); round.setMaDot(code); round.setTenDot("Đợt kiểm chứng SQL thật");
        round.setHocKyId(term); round.setBieuMauId(form.getId());
        round.setBatDauSv(TIME); round.setKetThucSv(TIME.plusDays(1)); round.setBatDauLop(TIME.plusDays(2));
        round.setKetThucLop(TIME.plusDays(3)); round.setBatDauKhoa(TIME.plusDays(4)); round.setKetThucKhoa(TIME.plusDays(5)); add(entities, round);
        var faculty = new RlKhoaDot(); faculty.setDotId(round.getId()); faculty.setKhoaId(facultyId); add(entities, faculty);
        var root = new RlLopDot(); root.setKhoaDotId(faculty.getId()); root.setLopId(classroom); add(entities, root);
        var ballot = new RlPhieu(); ballot.setLopDotId(root.getId()); ballot.setSinhVienId(student); add(entities, ballot);
        var score = new RlPhieuDiemCt(); score.setPhieuId(ballot.getId()); score.setBieuMauCtId(criterion.getId()); add(entities, score);
        var meeting = new RlHopLop(); meeting.setLopDotId(root.getId()); add(entities, meeting);
        for (short type = 1; type <= 2; type++) {
            long document = insert("INSERT dbo.van_ban(ma_van_ban) VALUES (?)", UUID.randomUUID().toString());
            long file = insert("INSERT dbo.tep_dinh_kem(ten_tep,kieu_noi_dung,trang_thai_luu_tru,sha256) VALUES (N'synthetic.pdf',N'application/pdf',N'available',?)", DrlDossierFixture.HASH);
            long version = insert("INSERT dbo.phien_ban_van_ban(van_ban_id,so_phien_ban,tep_goc_id,sha256_tap_du_lieu,thoi_diem_chot) VALUES (?,1,?,?,?)",
                    document, file, DrlDossierFixture.HASH, TIME);
            var link = new RlVanBanKy(); link.setLopDotId(root.getId()); link.setLoaiVanBan(type); link.setPhienBanVanBanId(version); add(entities, link);
        }
        repo.flush();
        return new Fixture(form, group, criterion, guidance, round, faculty, root, ballot, score, meeting, entities, staff, user);
    }

    private void add(List<DrlEntity> entities, DrlEntity entity) { repo.persist(entity); entities.add(entity); }
    private long insert(String sql, Object... args) { return DrlDossierFixture.insert(jdbc, sql, args); }
    private static String unquote(String name) { return name.replace("`", ""); }
    private static String table(Class<?> type) { return unquote(type.getAnnotation(Table.class).name()); }
    private static List<Field> fields(Class<?> type) {
        var result = new ArrayList<Field>();
        for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
            for (var field : current.getDeclaredFields()) if (field.isAnnotationPresent(Column.class)) result.add(field);
        }
        return result;
    }
    private static Map<String, Object> values(DrlEntity entity) {
        var values = new LinkedHashMap<String, Object>();
        try {
            for (var field : fields(entity.getClass())) { field.setAccessible(true); values.put(field.getName(), field.get(entity)); }
        } catch (IllegalAccessException error) { throw new AssertionError(error); }
        return values;
    }
}
