package pbl6.ctsv.drl;

import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.drl.reference.DrlReferenceData;
import pbl6.ctsv.drl.reference.DrlReferenceData.EvaluationClass;
import pbl6.ctsv.drl.reference.DrlReferenceData.EvaluationClassStatus;
import pbl6.ctsv.foundation.api.AcademicSemesterSource.Semester;
import pbl6.ctsv.foundation.api.StaffReferenceSource.Staff;
import pbl6.ctsv.foundation.api.StudentReferenceSource.AcademicLoad;
import pbl6.ctsv.foundation.api.StudentReferenceSource.AcademicLoadStatus;
import pbl6.ctsv.foundation.api.StudentReferenceSource.ClassSummary;
import pbl6.ctsv.foundation.api.StudentReferenceSource.Cohort;
import pbl6.ctsv.foundation.api.StudentReferenceSource.ManagingUnit;
import pbl6.ctsv.foundation.api.StudentReferenceSource.TrainingClass;
import pbl6.ctsv.foundation.repository.FoundationRepository;
import pbl6.ctsv.foundation.repository.JdbcStaffReferenceRepository;
import pbl6.ctsv.foundation.repository.JdbcStudentReferenceRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static pbl6.ctsv.drl.reference.DrlReferenceData.SemesterStatus.ABSENT_FROM_SOURCE;
import static pbl6.ctsv.drl.reference.DrlReferenceData.SemesterStatus.NOT_FOUND;
import static pbl6.ctsv.drl.reference.DrlReferenceData.SemesterStatus.PRESENT;
import static pbl6.ctsv.foundation.api.StudentReferenceSource.AcademicLoadStatus.MISSING;
import static pbl6.ctsv.foundation.api.StudentReferenceSource.AcademicLoadStatus.NEEDS_VERIFICATION;
import static pbl6.ctsv.foundation.api.StudentReferenceSource.MAX_STUDENT_IDS;

/** SQL IT for DRL-02.5 using rollback fixtures and production read ports. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DrlReferenceDataIT {
    private static final long UNKNOWN_ID = Long.MAX_VALUE;
    private static final BigDecimal POSITIVE = new BigDecimal("1.1250000001");

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private DrlReferenceData references;

    @BeforeAll
    void connectToVerifiedSchema() throws Exception {
        var source = DrlTestDatabase.source(DrlTestDatabase.FRESH);
        var flyway = DrlTestDatabase.flyway(source, DrlTestDatabase.TARGET);
        flyway.validate();
        var currentMigration = flyway.info().current();
        assertThat(currentMigration).isNotNull();
        assertThat(currentMigration.getVersion().getVersion()).isEqualTo(DrlTestDatabase.TARGET);

        context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of("spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false")
                .applyTo(context);
        AutoConfigurationPackages.register(context, "pbl6.ctsv");
        context.registerBean(DataSource.class, () -> source);
        context.register(DrlPersistenceIT.SqlConfiguration.class);
        context.refresh();

        jdbc = context.getBean(JdbcTemplate.class);
        tx = new TransactionTemplate(context.getBean(JpaTransactionManager.class));
        var em = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
        references = new DrlReferenceData(
                new FoundationRepository(em, jdbc), new JdbcStudentReferenceRepository(jdbc),
                new JdbcStaffReferenceRepository(jdbc));
    }

    @AfterAll
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void pagesAndFindsSemestersWithDistinctSourcePresence() {
        String key = key();
        inRollback(() -> {
            long active = insert("""
                    INSERT dbo.hoc_ky(ma, ten, nam_hoc_nguon, ngay_bat_dau, ngay_ket_thuc, trang_thai_xac_minh_moc)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, key + "-A", "Học kỳ kiểm thử", "2098-2099", LocalDate.of(2098, 9, 1),
                    LocalDate.of(2098, 12, 20), "CHUA_XAC_MINH");
            long absent = semester(key + "-B", false);
            semester(key(), true);

            var first = references.findSemesters(key, 0, 1);
            assertThat(first.page()).isZero();
            assertThat(first.size()).isEqualTo(1);
            assertThat(first.totalElements()).isEqualTo(2);
            assertThat(first.items()).extracting(Semester::id).containsExactly(active);

            var second = references.findSemesters(key, 1, 1);
            assertThat(second.page()).isEqualTo(1);
            assertThat(second.size()).isEqualTo(1);
            assertThat(second.totalElements()).isEqualTo(2);
            assertThat(second.items()).extracting(Semester::id).containsExactly(absent);

            var found = references.findSemester(active).orElseThrow();
            assertThat(found.code()).isEqualTo(key + "-A");
            assertThat(found.name()).isEqualTo("Học kỳ kiểm thử");
            assertThat(found.sourceAcademicYear()).isEqualTo("2098-2099");
            assertThat(found.startDate()).isEqualTo(LocalDate.of(2098, 9, 1));
            assertThat(found.endDate()).isEqualTo(LocalDate.of(2098, 12, 20));
            assertThat(found.milestoneVerificationStatus()).isEqualTo("CHUA_XAC_MINH");
            assertThat(found.present()).isTrue();
            assertThat(references.findSemester(absent).orElseThrow().present()).isFalse();
            assertThat(references.findSemester(UNKNOWN_ID)).isEmpty();
        });
    }

    @Test
    void distinguishesUnavailableSemesterFromMissingAcademicLoad() {
        inRollback(() -> {
            long current = semester(key(), true);
            long absent = semester(key(), false);
            long student = student();
            load(student, absent, POSITIVE, true);
            var ids = List.of(student);

            var found = references.findStudents(current, ids);
            assertThat(found.semesterStatus()).isEqualTo(PRESENT);
            assertThat(found.students().get(student).academicLoad()).isEqualTo(new AcademicLoad(MISSING, null));

            var deleted = references.findStudents(absent, ids);
            assertThat(deleted.semesterStatus()).isEqualTo(ABSENT_FROM_SOURCE);
            assertThat(deleted.students()).isEmpty();

            var missing = references.findStudents(UNKNOWN_ID, ids);
            assertThat(missing.semesterStatus()).isEqualTo(NOT_FOUND);
            assertThat(missing.students()).isEmpty();
        });
    }

    @Test
    void preservesMssvAndReturnsOnlyFoundStudentProfiles() {
        inRollback(() -> {
            long semester = semester(key(), true);
            String classCode = key();
            long trainingClass = insert("INSERT dbo.lop_sinh_hoat(ma, ten) VALUES (?, ?)",
                    classCode, "Lớp đào tạo synthetic");
            long withClass = student("001234", trainingClass, null, null, null, true);
            long withoutClass = student(key(), null, null, null, null, false);

            var students = references.findStudents(semester, List.of(withClass, withoutClass, UNKNOWN_ID)).students();
            assertThat(students).containsOnlyKeys(withClass, withoutClass);
            var found = students.get(withClass);
            assertThat(found.id()).isEqualTo(withClass);
            assertThat(found.mssv()).isEqualTo("001234");
            assertThat(found.fullName()).isEqualTo("Sinh viên synthetic");
            assertThat(found.present()).isTrue();
            assertThat(found.trainingClass())
                    .isEqualTo(new TrainingClass(trainingClass, classCode, "Lớp đào tạo synthetic"));
            assertThat(students.get(withoutClass).id()).isEqualTo(withoutClass);
            assertThat(students.get(withoutClass).trainingClass()).isNull();
            assertThat(students.get(withoutClass).present()).isFalse();
        });
    }

    @Test
    void pagesCohortsAndUsesStudentLinkInsteadOfTrainingClassLink() {
        inRollback(() -> {
            long semester = semester(key(), true);
            String search = key();
            long studentCohort = cohort(search + "-A", "2097", true);
            long classCohort = cohort(search + "-B", null, false);
            cohort(key(), "2096", true);
            long trainingClass = insert("INSERT dbo.lop_sinh_hoat(ma, khoa_hoc_id) VALUES (?, ?)", key(), classCohort);
            long assigned = student(key(), trainingClass, studentCohort, null, null, true);
            long unassigned = student(key(), trainingClass, null, null, null, true);
            var expected = new Cohort(studentCohort, search + "-A", "2097", true);

            var first = references.findCohorts(search, 0, 1);
            assertThat(first.page()).isZero();
            assertThat(first.size()).isEqualTo(1);
            assertThat(first.totalElements()).isEqualTo(2);
            assertThat(first.items()).containsExactly(expected);

            var second = references.findCohorts(search, 1, 1);
            assertThat(second.page()).isEqualTo(1);
            assertThat(second.size()).isEqualTo(1);
            assertThat(second.totalElements()).isEqualTo(2);
            assertThat(second.items()).containsExactly(new Cohort(classCohort, search + "-B", null, false));

            var students = references.findStudents(semester, List.of(assigned, unassigned)).students();
            assertThat(students.get(assigned).trainingClass().id()).isEqualTo(trainingClass);
            assertThat(students.get(assigned).cohort()).isEqualTo(expected);
            assertThat(students.get(unassigned).cohort()).isNull();
        });
    }

    @Test
    void pagesClassesWithManagingUnitAndSourcePresence() {
        inRollback(() -> {
            String search = key();
            String unitCode = key();
            long managingUnit = insert("INSERT dbo.don_vi(ma, ten) VALUES (?, ?)",
                    unitCode, "Khoa quản lý synthetic");
            long linkedClass = catalogClass(search + "-A", "Lớp có Khoa", managingUnit, "0", true);
            long unlinkedClass = catalogClass(search + "-B", "Lớp thiếu Khoa", null, null, false);
            catalogClass(key(), "Lớp ngoài tìm kiếm", managingUnit, "0", true);

            var first = references.findClasses(search, 0, 1);
            assertThat(first.page()).isZero();
            assertThat(first.size()).isEqualTo(1);
            assertThat(first.totalElements()).isEqualTo(2);
            assertThat(first.items()).containsExactly(new ClassSummary(linkedClass, search + "-A",
                    "Lớp có Khoa", true, new ManagingUnit(managingUnit, unitCode, "Khoa quản lý synthetic"), "0"));

            var second = references.findClasses(search, 1, 1);
            assertThat(second.page()).isEqualTo(1);
            assertThat(second.totalElements()).isEqualTo(2);
            assertThat(second.items()).containsExactly(new ClassSummary(unlinkedClass, search + "-B",
                    "Lớp thiếu Khoa", false, null, null));
            var pastEnd = references.findClasses(search, 2, 1);
            assertThat(pastEnd.items()).isEmpty();
            assertThat(pastEnd.totalElements()).isEqualTo(2);
        });
    }

    @Test
    void distinguishesCombinedOrdinaryAndMissingClassFlags() {
        inRollback(() -> {
            String code = key();
            String name = key() + "%_[]";
            long combined = catalogClass(code + "-A", name + " A", null, "1", true);
            long ordinary = catalogClass(code + "-B", name + " B", null, "0", true);
            long missingFlag = catalogClass(code + "-C", name + " C", null, null, true);
            catalogClass(code + "-D", name.replace("%", "X") + " D", null, "1", true);

            var page = references.findClasses(name, 0, 10);
            assertThat(page.totalElements()).isEqualTo(3);
            assertThat(page.items()).extracting(ClassSummary::id).containsExactly(combined, ordinary, missingFlag);
            assertThat(page.items()).extracting(ClassSummary::sourceCombinedClassFlag).containsExactly("1", "0", null);
            assertThat(page.items()).extracting(ClassSummary::combinedClass).containsExactly(true, false, null);
        });
    }

    @Test
    void findsDistinctManagingFacultiesFromTheRequestedClasses() {
        inRollback(() -> {
            String prefix = key();
            String name = "Khoa cùng tên synthetic";
            String firstCode = prefix + "-A";
            String secondCode = prefix + "-B";
            long secondUnit = insert("INSERT dbo.don_vi(ma, ten, loai_don_vi) VALUES (?, ?, ?)",
                    secondCode, name, "phong");
            long firstUnit = insert("INSERT dbo.don_vi(ma, ten) VALUES (?, ?)", firstCode, name);
            long outsideUnit = insert("""
                    INSERT dbo.don_vi(ma, ten, loai_don_vi, ma_bo_phan_nguon, is_us_nguon)
                    VALUES (?, ?, ?, ?, ?)
                    """, prefix + "-C", "Khoa ngoài phạm vi", "khoa", "199", "1");
            long secondClass = catalogClass(key(), "Lớp ghép", secondUnit, "1", true);
            long firstClass = catalogClass(key(), "Lớp thường", firstUnit, "0", true);
            long anotherFirstClass = catalogClass(key(), "Lớp cùng Khoa", firstUnit, "0", true);
            long unlinkedClass = catalogClass(key(), "Lớp thiếu Khoa", null, "0", true);
            catalogClass(key(), "Lớp ngoài phạm vi", outsideUnit, "0", true);

            var classes = List.of(secondClass, firstClass, anotherFirstClass, firstClass, unlinkedClass, UNKNOWN_ID);
            assertThat(references.findManagingFaculties(classes)).containsExactly(
                    new ManagingUnit(firstUnit, firstCode, name), new ManagingUnit(secondUnit, secondCode, name));
        });
    }

    @Test
    void keepsAnEmptyScopeEmptyAndRejectsInvalidClassIds() {
        inRollback(() -> {
            long unit = insert("INSERT dbo.don_vi(ma) VALUES (?)", key());
            long trainingClass = catalogClass(key(), "Lớp có Khoa", unit, "0", true);

            assertThat(references.findManagingFaculties(List.of())).isEmpty();
            assertThatThrownBy(() -> references.findManagingFaculties(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> references.findManagingFaculties(Collections.singletonList(null)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> references.findManagingFaculties(List.of(trainingClass, 0L)))
                    .isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void acceptsAClassScopeLargerThanTheSqlParameterLimit() {
        inRollback(() -> {
            String code = key();
            String name = "Khoa phạm vi lớn synthetic";
            long unit = insert("INSERT dbo.don_vi(ma, ten) VALUES (?, ?)", code, name);
            long trainingClass = catalogClass(key(), "Lớp có Khoa", unit, "0", true);
            var classes = new ArrayList<>(LongStream.range(UNKNOWN_ID - 2_100, UNKNOWN_ID).boxed().toList());
            classes.add(trainingClass);

            assertThat(references.findManagingFaculties(classes)).containsExactly(new ManagingUnit(unit, code, name));
        });
    }

    @Test
    void usesTrainingClassWhenNoEffectiveCombinedAssignmentExists() {
        inRollback(() -> {
            long semester = semester(key(), true);
            long otherSemester = semester(key(), true);
            long source = assignmentSource();
            long original = catalogClass(key(), "Lớp đào tạo", null, "0", true);
            long combined = catalogClass(key(), "Lớp ghép", null, "1", true);
            long ordinary = catalogClass(key(), "Lớp thường", null, "0", true);
            long unclassified = catalogClass(key(), "Lớp thiếu cờ", null, null, true);
            long unassigned = student(key(), original, null, null, null, true);
            long excludedRows = student(key(), original, null, null, null, true);
            assignment(source, otherSemester, excludedRows, combined, true);
            assignment(source, semester, excludedRows, combined, false);
            assignment(source, semester, excludedRows, ordinary, true);
            assignment(source, semester, excludedRows, unclassified, true);

            var batch = references.findEvaluationClasses(semester, List.of(unassigned, excludedRows));
            assertThat(batch.semesterStatus()).isEqualTo(PRESENT);
            assertThat(batch.students()).containsOnlyKeys(unassigned, excludedRows);
            assertThat(batch.students().values()).allSatisfy(studentClass -> {
                assertThat(studentClass.status()).isEqualTo(EvaluationClassStatus.RESOLVED);
                assertThat(studentClass.trainingClass().id()).isEqualTo(original);
                assertThat(studentClass.evaluationClass()).isEqualTo(studentClass.trainingClass());
            });
            assertThat(references.findClassRoster(semester, original).members()).extracting(EvaluationClass::studentId)
                    .containsExactlyInAnyOrder(unassigned, excludedRows);
            assertThat(references.findClassRoster(semester, combined).members()).isEmpty();
            assertThat(references.findClassRoster(semester, ordinary).members()).isEmpty();
            assertThat(references.findClassRoster(semester, unclassified).members()).isEmpty();
            assertThat(references.findClassRoster(otherSemester, combined).members())
                    .extracting(EvaluationClass::studentId).containsExactly(excludedRows);
        });
    }

    @Test
    void usesCombinedClassAndItsFacultyAndIncludesStudentWithoutBallot() {
        inRollback(() -> {
            long semester = semester(key(), true);
            long source = assignmentSource();
            long originalUnit = insert("INSERT dbo.don_vi(ma) VALUES (?)", key());
            long combinedUnit = insert("INSERT dbo.don_vi(ma) VALUES (?)", key());
            long original = catalogClass(key(), "Lớp đào tạo", originalUnit, "0", true);
            long combined = catalogClass(key(), "Lớp ghép", combinedUnit, "1", true);
            String mssv = key();
            long assigned = student(mssv, original, null, null, null, true);
            assignment(source, semester, assigned, combined, true);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RLPhieu WHERE SinhVienID = ?",
                    Integer.class, assigned)).isZero();

            var result = references.findEvaluationClasses(semester, List.of(assigned)).students().get(assigned);
            assertThat(result.status()).isEqualTo(EvaluationClassStatus.RESOLVED);
            assertThat(result.mssv()).isEqualTo(mssv);
            assertThat(result.trainingClass().id()).isEqualTo(original);
            assertThat(result.trainingClass().managingUnit().id()).isEqualTo(originalUnit);
            assertThat(result.evaluationClass().id()).isEqualTo(combined);
            assertThat(result.evaluationClass().managingUnit().id()).isEqualTo(combinedUnit);

            var roster = references.findClassRoster(semester, combined);
            assertThat(roster.semesterStatus()).isEqualTo(PRESENT);
            assertThat(roster.members()).containsExactly(result);
            assertThat(references.findClassRoster(semester, original).members()).isEmpty();
        });
    }

    @Test
    void isolatesConflictingCombinedAssignmentsFromResolvedStudentsAndRosters() {
        inRollback(() -> {
            long semester = semester(key(), true);
            long source = assignmentSource();
            long original = catalogClass(key(), "Lớp đào tạo", null, "0", true);
            long combined = catalogClass(key(), "Lớp ghép A", null, "1", true);
            long otherCombined = catalogClass(key(), "Lớp ghép B", null, "1", true);
            long unresolved = student(key(), original, null, null, null, true);
            long assigned = student(key(), original, null, null, null, true);
            assignment(source, semester, unresolved, combined, true);
            assignment(source, semester, unresolved, otherCombined, true);
            assignment(source, semester, assigned, combined, true);
            assignment(assignmentSource(), semester, assigned, combined, true);

            var results = references.findEvaluationClasses(semester, List.of(unresolved, assigned)).students();
            assertThat(results.get(unresolved).status()).isEqualTo(EvaluationClassStatus.NEEDS_VERIFICATION);
            assertThat(results.get(unresolved).trainingClass().id()).isEqualTo(original);
            assertThat(results.get(unresolved).evaluationClass()).isNull();
            assertThat(results.get(assigned).evaluationClass().id()).isEqualTo(combined);
            var combinedRoster = references.findClassRoster(semester, combined);
            assertThat(combinedRoster.members()).containsExactly(results.get(assigned));
            assertThat(combinedRoster.studentsNeedingVerification()).containsExactly(results.get(unresolved));
            var otherCombinedRoster = references.findClassRoster(semester, otherCombined);
            assertThat(otherCombinedRoster.members()).isEmpty();
            assertThat(otherCombinedRoster.studentsNeedingVerification()).containsExactly(results.get(unresolved));
            var originalRoster = references.findClassRoster(semester, original);
            assertThat(originalRoster.members()).isEmpty();
            assertThat(originalRoster.studentsNeedingVerification()).containsExactly(results.get(unresolved));
        });
    }

    @Test
    void readsCurriculumTypeFromStudentProgramAndPreservesMissingFields() {
        inRollback(() -> {
            long semester = semester(key(), true);
            String bachelorCode = key();
            String engineerCode = key();
            long bachelorProgram = program(bachelorCode, "Cử nhân synthetic", "CN");
            long engineerProgram = program(engineerCode, "Kỹ sư synthetic", "KS");
            long programWithoutType = program(key(), "Chưa có loại", null);
            long bachelor = student(key(), null, null, bachelorProgram, "KS", true);
            long engineer = student(key(), null, null, engineerProgram, "CN", true);
            long missingProgram = student(key(), null, null, null, "CN", true);
            long missingType = student(key(), null, null, programWithoutType, "KS", true);
            load(missingProgram, semester, POSITIVE, true);
            load(missingType, semester, POSITIVE, true);

            var students = references.findStudents(semester, List.of(bachelor, engineer, missingProgram, missingType)).students();
            assertThat(students.get(bachelor).program().id()).isEqualTo(bachelorProgram);
            assertThat(students.get(bachelor).program().code()).isEqualTo(bachelorCode);
            assertThat(students.get(bachelor).program().name()).isEqualTo("Cử nhân synthetic");
            assertThat(students.get(bachelor).program().curriculumType()).isEqualTo("CN");
            assertThat(students.get(engineer).program().id()).isEqualTo(engineerProgram);
            assertThat(students.get(engineer).program().code()).isEqualTo(engineerCode);
            assertThat(students.get(engineer).program().name()).isEqualTo("Kỹ sư synthetic");
            assertThat(students.get(engineer).program().curriculumType()).isEqualTo("KS");
            assertThat(students.get(missingProgram).program()).isNull();
            assertThat(students.get(missingType).program().id()).isEqualTo(programWithoutType);
            assertThat(students.get(missingType).program().curriculumType()).isNull();
            assertThat(students.get(missingProgram).academicLoad()).isEqualTo(presentLoad(POSITIVE));
            assertThat(students.get(missingType).academicLoad()).isEqualTo(presentLoad(POSITIVE));
        });
    }

    @Test
    void separatesPositiveZeroNullAndMissingForExactStudentAndSemester() {
        inRollback(() -> {
            long semester = semester(key(), true);
            long otherSemester = semester(key(), true);
            long positive = student();
            long zero = student();
            long nullValue = student();
            long missing = student();
            long differentStudent = student();
            load(positive, semester, POSITIVE, true);
            load(zero, semester, BigDecimal.ZERO, true);
            load(nullValue, semester, null, true);
            load(missing, otherSemester, POSITIVE, true);
            load(differentStudent, semester, POSITIVE, true);

            var students = references.findStudents(semester, List.of(positive, zero, nullValue, missing)).students();
            assertThat(students).containsOnlyKeys(positive, zero, nullValue, missing);
            assertThat(students.get(positive).academicLoad()).isEqualTo(presentLoad(POSITIVE));
            assertThat(students.get(zero).academicLoad()).isEqualTo(presentLoad(BigDecimal.ZERO));
            assertThat(students.get(nullValue).academicLoad()).isEqualTo(presentLoad(null));
            assertThat(students.get(missing).academicLoad()).isEqualTo(new AcademicLoad(MISSING, null));
        });
    }

    @Test
    void marksEachDuplicateCurrentLoadForVerificationAndPreservesOtherStudents() {
        inRollback(() -> {
            long semester = semester(key(), true);
            long repeated = student();
            long conflicting = student();
            long unaffected = student();
            load(repeated, semester, POSITIVE, true);
            load(repeated, semester, POSITIVE, true);
            load(conflicting, semester, POSITIVE, true);
            load(conflicting, semester, BigDecimal.ZERO, true);
            load(unaffected, semester, POSITIVE, true);

            var students = references.findStudents(semester, List.of(repeated, conflicting, unaffected)).students();
            var unresolved = new AcademicLoad(NEEDS_VERIFICATION, null);
            assertThat(students.get(repeated).academicLoad()).isEqualTo(unresolved);
            assertThat(students.get(conflicting).academicLoad()).isEqualTo(unresolved);
            assertThat(students.get(unaffected).academicLoad()).isEqualTo(presentLoad(POSITIVE));
        });
    }

    @Test
    void excludesDeletedSourceLoadsFromValueAndDuplicateDetection() {
        inRollback(() -> {
            long semester = semester(key(), true);
            long hasCurrent = student();
            long onlyDeleted = student();
            load(hasCurrent, semester, POSITIVE, true);
            load(hasCurrent, semester, BigDecimal.ZERO, false);
            load(onlyDeleted, semester, POSITIVE, false);

            var students = references.findStudents(semester, List.of(hasCurrent, onlyDeleted)).students();
            assertThat(students.get(hasCurrent).academicLoad()).isEqualTo(presentLoad(POSITIVE));
            assertThat(students.get(onlyDeleted).academicLoad()).isEqualTo(new AcademicLoad(MISSING, null));
        });
    }

    @Test
    void sharesTheCallerTransactionAndEnforcesBoundedLookup() {
        String semesterCode = key();
        inRollback(() -> {
            long semester = semester(semesterCode, true);
            long first = student();
            long second = student();
            var singleton = references.findStudents(semester, List.of(first));
            var group = references.findStudents(semester, List.of(second, first));
            assertThat(singleton.students()).containsOnlyKeys(first);
            assertThat(group.students()).containsOnlyKeys(first, second);
            assertThat(group.students().get(first)).isEqualTo(singleton.students().get(first));
            assertThat(references.findStudents(semester, List.of()).students()).isEmpty();
            assertThatThrownBy(() -> references.findStudents(semester, Collections.nCopies(MAX_STUDENT_IDS + 1, first)))
                    .isInstanceOf(IllegalArgumentException.class);
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.hoc_ky WHERE ma = ?", Integer.class, semesterCode))
                .isZero();
    }

    @Test
    void readsServingDeansAndFacultyRepresentativesFromCurrentUnit() {
        inRollback(() -> {
            long faculty = insert("INSERT dbo.don_vi(ma) VALUES (?)", key());
            long otherFaculty = insert("INSERT dbo.don_vi(ma) VALUES (?)", key());
            long dean = staff(faculty, key(), "Trưởng khoa", "CV1209", "0", null);
            long deputy = staff(faculty, key(), "Phó trưởng khoa", "CV1210", null, "101");
            staff(faculty, key(), "Trưởng Bộ môn", "CV1211", null, null);
            staff(faculty, key(), "Trưởng khoa nghỉ hưu", "CV1209", "1", "101");
            staff(faculty, key(), "Phó trưởng khoa lưu trữ", "CV1210", "0", "990");
            staff(otherFaculty, key(), "Trưởng khoa khác", "CV1209", "0", null);
            staff(null, key(), "Thiếu đơn vị công tác", "CV1209", null, null);

            assertThat(references.findDeans(faculty)).singleElement().satisfies(result -> {
                assertThat(result.id()).isEqualTo(dean);
                assertThat(result.employingUnitId()).isEqualTo(faculty);
                assertThat(result.positionCode()).isEqualTo("CV1209");
            });
            assertThat(references.findFacultyRepresentatives(faculty)).extracting(Staff::id)
                    .containsExactlyInAnyOrder(dean, deputy);
        });
    }

    @Test
    void searchesReplacementStaffByEvaluationClassFacultyAndLiteralNameOrCode() {
        inRollback(() -> {
            long faculty = insert("INSERT dbo.don_vi(ma) VALUES (?)", key());
            long trainingFaculty = insert("INSERT dbo.don_vi(ma) VALUES (?)", key());
            long evaluationClass = catalogClass(key(), "Lớp đánh giá", faculty, "1", true);
            long classWithoutFaculty = catalogClass(key(), "Lớp thiếu Khoa", null, "0", true);
            String deanCode = "CB%_" + key();
            long dean = staff(faculty, deanCode, "Nguyễn Văn An", "CV1209", "0", null);
            long unknownPosition = staff(faculty, key(), "Trần Bình", null, null, null);
            long retired = staff(faculty, key(), "Lê Hoài", "CV1220", "1", null);
            staff(trainingFaculty, key(), "Trần Bình", null, null, null);

            assertThat(references.findHomeroomReplacementCandidates(evaluationClass, "  ")).extracting(Staff::id)
                    .containsExactlyInAnyOrder(dean, unknownPosition, retired);
            assertThat(references.findHomeroomReplacementCandidates(evaluationClass, "Trần Bình")).extracting(Staff::id)
                    .containsExactly(unknownPosition);
            assertThat(references.findHomeroomReplacementCandidates(evaluationClass, "CB%_")).extracting(Staff::id)
                    .containsExactly(dean);
            assertThat(references.findHomeroomReplacementCandidates(classWithoutFaculty, null)).isEmpty();
        });
    }

    @Test
    void suggestsOnlyTheDistinctHomeroomTeacherFromTheExactClassAndSemester() {
        inRollback(() -> {
            long semester = semester(key(), true);
            long otherSemester = semester(key(), true);
            long evaluationClass = catalogClass(key(), "Lớp đánh giá", null, "1", true);
            long otherClass = catalogClass(key(), "Lớp khác", null, "0", true);
            String assignedCode = key();
            long assigned = staff(null, assignedCode, "GVCN nguồn", null, null, null);
            long adviser = staff(null, key(), "Cố vấn nguồn", null, null, null);

            homeroomAssignment(evaluationClass, semester, assigned, adviser);
            homeroomAssignment(evaluationClass, semester, assigned, null);
            homeroomAssignment(evaluationClass, semester, null, adviser);
            homeroomAssignment(evaluationClass, otherSemester, adviser, null);
            homeroomAssignment(otherClass, semester, adviser, null);

            assertThat(references.findSuggestedHomeroomTeacher(semester, evaluationClass)).hasValueSatisfying(result -> {
                assertThat(result.id()).isEqualTo(assigned);
                assertThat(result.code()).isEqualTo(assignedCode);
            });
        });
    }

    @Test
    void leavesHomeroomSuggestionEmptyForMissingOrConflictingTeachers() {
        inRollback(() -> {
            long semester = semester(key(), true);
            long evaluationClass = catalogClass(key(), "Lớp đánh giá", null, "1", true);
            long first = staff(null, key(), "Cán bộ thứ nhất", null, null, null);
            long second = staff(null, key(), "Cán bộ thứ hai", null, null, null);

            assertThat(references.findSuggestedHomeroomTeacher(semester, evaluationClass)).isEmpty();
            homeroomAssignment(evaluationClass, semester, null, first);
            assertThat(references.findSuggestedHomeroomTeacher(semester, evaluationClass)).isEmpty();
            jdbc.update("""
                    INSERT dbo.phan_cong_co_van_lop(lop_id, hoc_ky_id, can_bo_chu_nhiem_id_goc_nguon)
                    VALUES (?, ?, ?)
                    """, evaluationClass, semester, key());
            assertThat(references.findSuggestedHomeroomTeacher(semester, evaluationClass)).isEmpty();
            homeroomAssignment(evaluationClass, semester, first, null);
            homeroomAssignment(evaluationClass, semester, second, null);
            assertThat(references.findSuggestedHomeroomTeacher(semester, evaluationClass)).isEmpty();
        });
    }

    @Test
    void readsNullableUserLinksIndependentlyOfStudentAndStaffIds() {
        inRollback(() -> {
            long semester = semester(key(), true);
            long trainingClass = catalogClass(key(), "Lớp đào tạo", null, "0", true);
            long linkedStudent = student(key(), trainingClass, null, null, null, true);
            long unlinkedStudent = student(key(), trainingClass, null, null, null, true);
            long linkedStaff = staff(null, key(), "Cán bộ có liên kết", null, null, null);
            long unlinkedStaff = staff(null, key(), "Cán bộ chưa liên kết", null, null, null);
            long studentUser = userOtherThan(linkedStudent);
            long staffUser = userOtherThan(linkedStaff);
            jdbc.update("UPDATE dbo.sinh_vien SET nguoi_dung_id = ? WHERE id = ?", studentUser, linkedStudent);
            jdbc.update("UPDATE dbo.can_bo SET nguoi_dung_id = ? WHERE id = ?", staffUser, linkedStaff);

            var students = references.findStudents(semester, List.of(linkedStudent, unlinkedStudent)).students();
            assertThat(students.get(linkedStudent).id()).isEqualTo(linkedStudent);
            assertThat(students.get(linkedStudent).userId()).isEqualTo(studentUser).isNotEqualTo(linkedStudent);
            assertThat(students.get(unlinkedStudent).userId()).isNull();
            assertThat(references.findClassRoster(semester, trainingClass).members())
                    .extracting(EvaluationClass::studentId, EvaluationClass::userId)
                    .containsExactlyInAnyOrder(tuple(linkedStudent, studentUser), tuple(unlinkedStudent, null));
            assertThat(references.findStaff(linkedStaff)).hasValueSatisfying(result -> {
                assertThat(result.id()).isEqualTo(linkedStaff);
                assertThat(result.userId()).isEqualTo(staffUser).isNotEqualTo(linkedStaff);
            });
            assertThat(references.findStaff(unlinkedStaff)).hasValueSatisfying(result ->
                    assertThat(result.userId()).isNull());
            assertThat(references.findStaff(UNKNOWN_ID)).isEmpty();
        });
    }

    @Test
    void mapsTheNullableUnicodeSourcePositionColumn() {
        var column = jdbc.queryForMap("""
                SELECT t.name AS type_name, c.max_length, c.is_nullable, c.default_object_id
                FROM sys.columns c JOIN sys.types t ON t.user_type_id = c.user_type_id
                WHERE c.object_id = OBJECT_ID(N'dbo.sinh_vien') AND c.name = N'ma_chuc_vu_nguon'
                """);
        assertThat(column.get("type_name")).isEqualTo("nvarchar");
        assertThat(((Number) column.get("max_length")).intValue()).isEqualTo(256);
        assertThat(column.get("is_nullable")).isEqualTo(true);
        assertThat(column.get("default_object_id")).isEqualTo(0);
    }

    @Test
    void readsSourcePositionCodesFromEachStudentAndPreservesMissingCodes() {
        inRollback(() -> {
            long semester = semester(key(), true);
            long classLeader = student();
            long deputy = student();
            long withoutCode = student();
            setSourcePositionCode(classLeader, "LT");
            setSourcePositionCode(deputy, "LP");

            var students = references.findStudents(semester, List.of(classLeader, deputy, withoutCode)).students();
            assertThat(students.get(classLeader).sourcePositionCode()).isEqualTo("LT");
            assertThat(students.get(deputy).sourcePositionCode()).isEqualTo("LP");
            assertThat(students.get(withoutCode).sourcePositionCode()).isNull();
        });
    }

    @Test
    void carriesSourcePositionIntoTheEvaluationClassAndRosterWithoutABallot() {
        inRollback(() -> {
            long semester = semester(key(), true);
            long trainingClass = catalogClass(key(), "Lớp đào tạo", null, "0", true);
            long combinedClass = catalogClass(key(), "Lớp ghép", null, "1", true);
            long classLeader = student(key(), trainingClass, null, null, null, true);
            long withoutCode = student(key(), trainingClass, null, null, null, true);
            setSourcePositionCode(classLeader, "LT");
            assignment(assignmentSource(), semester, classLeader, combinedClass, true);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RLPhieu WHERE SinhVienID = ?",
                    Integer.class, classLeader)).isZero();

            var resolved = references.findEvaluationClasses(semester, List.of(classLeader)).students().get(classLeader);
            assertThat(resolved.sourcePositionCode()).isEqualTo("LT");
            assertThat(resolved.trainingClass().id()).isEqualTo(trainingClass);
            assertThat(resolved.evaluationClass().id()).isEqualTo(combinedClass);
            assertThat(references.findClassRoster(semester, combinedClass).members()).containsExactly(resolved);
            assertThat(references.findClassRoster(semester, trainingClass).members())
                    .extracting(EvaluationClass::studentId, EvaluationClass::sourcePositionCode)
                    .containsExactly(tuple(withoutCode, null));
        });
    }

    @Test
    void keepsBothSourceClassLeadersInTheSameRoster() {
        inRollback(() -> {
            long semester = semester(key(), true);
            long trainingClass = catalogClass(key(), "Lớp đào tạo", null, "0", true);
            long first = student(key(), trainingClass, null, null, null, true);
            long second = student(key(), trainingClass, null, null, null, true);
            setSourcePositionCode(first, "LT");
            setSourcePositionCode(second, "LT");

            assertThat(references.findClassRoster(semester, trainingClass).members())
                    .extracting(EvaluationClass::studentId, EvaluationClass::sourcePositionCode)
                    .containsExactlyInAnyOrder(tuple(first, "LT"), tuple(second, "LT"));
        });
    }

    private void inRollback(Runnable test) {
        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            test.run();
        });
    }

    private long semester(String code, boolean present) {
        return insert("INSERT dbo.hoc_ky(ma, dang_hien_dien_nguon) VALUES (?, ?)", code, present);
    }

    private long cohort(String code, String admissionYear, boolean present) {
        return insert("INSERT dbo.khoa_hoc(ma, nam_tuyen_sinh_nguon, dang_hien_dien_nguon) VALUES (?, ?, ?)",
                code, admissionYear, present);
    }

    private long catalogClass(String code, String name, Long unitId, String combinedClassFlag, boolean present) {
        return insert("""
                INSERT dbo.lop_sinh_hoat(ma, ten, khoa_quan_ly_id, lop_ghep_nguon, dang_hien_dien_nguon)
                VALUES (?, ?, ?, ?, ?)
                """, code, name, unitId, combinedClassFlag, present);
    }

    private long assignmentSource() {
        return insert("INSERT dbo.he_thong_ngoai(ma) VALUES (?)", key());
    }

    private void assignment(long sourceId, long semesterId, long studentId, long classId, boolean present) {
        jdbc.update("""
                INSERT dbo.phan_lop_ren_luyen(he_thong_nguon_id, ma_dinh_danh_ben_ngoai, hoc_ky_id,
                                            sinh_vien_id, lop_ren_luyen_id, dang_hien_dien_nguon)
                VALUES (?, ?, ?, ?, ?, ?)
                """, sourceId, key(), semesterId, studentId, classId, present);
    }

    private long program(String code, String name, String type) {
        return insert("INSERT dbo.chuong_trinh_dao_tao(ma, ten, loai_ctdt_nguon) VALUES (?, ?, ?)", code, name, type);
    }

    private long student() {
        return student(key(), null, null, null, null, true);
    }

    private long student(String mssv, Long trainingClass, Long cohort, Long program, String educationSystem, boolean present) {
        return insert("""
                INSERT dbo.sinh_vien(mssv, ho_ten, lop_hien_tai_id, khoa_hoc_hien_tai_id, chuong_trinh_hien_tai_id,
                    he_dao_tao_nguon, dang_hien_dien_nguon)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, mssv, "Sinh viên synthetic", trainingClass, cohort, program, educationSystem, present);
    }

    private void setSourcePositionCode(long studentId, String code) {
        jdbc.update("UPDATE dbo.sinh_vien SET ma_chuc_vu_nguon = ? WHERE id = ?", code, studentId);
    }

    private void load(long student, long semester, BigDecimal amount, boolean present) {
        jdbc.update("""
                INSERT dbo.du_lieu_hoc_vu_dong_bo(sinh_vien_id, hoc_ky_id, hp_dang_ky, dang_hien_dien_nguon)
                VALUES (?, ?, ?, ?)
                """, student, semester, amount, present);
    }

    private long staff(Long unit, String code, String name, String position, String retired, String department) {
        return insert("""
                INSERT dbo.can_bo(don_vi_nguon_id, ma_cb, ho_ten, ma_chuc_vu_nguon, huu_tri_nguon, ma_bo_phan_nguon)
                VALUES (?, ?, ?, ?, ?, ?)
                """, unit, code, name, position, retired, department);
    }

    private void homeroomAssignment(long evaluationClass, long semester, Long homeroomTeacher, Long adviser) {
        jdbc.update("""
                INSERT dbo.phan_cong_co_van_lop(lop_id, hoc_ky_id, can_bo_chu_nhiem_id, can_bo_id)
                VALUES (?, ?, ?, ?)
                """, evaluationClass, semester, homeroomTeacher, adviser);
    }

    private long userOtherThan(long profileId) {
        String sql = "INSERT dbo.nguoi_dung(dia_chi_email) VALUES (?)";
        long id = insert(sql, key() + "@example.test");
        return id == profileId ? insert(sql, key() + "@example.test") : id;
    }

    private static AcademicLoad presentLoad(BigDecimal value) {
        return new AcademicLoad(AcademicLoadStatus.PRESENT, value == null ? null : value.setScale(10));
    }

    private long insert(String sql, Object... parameters) {
        return DrlDossierFixture.insert(jdbc, sql, parameters);
    }

    private static String key() {
        return "D25-" + UUID.randomUUID();
    }
}
