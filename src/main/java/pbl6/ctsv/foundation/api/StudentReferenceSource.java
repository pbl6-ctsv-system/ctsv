package pbl6.ctsv.foundation.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Read-only Foundation projections using CTSV IDs and the caller's local transaction. */
public interface StudentReferenceSource {
    int MAX_STUDENT_IDS = 256;

    record Cohort(long id, String code, String admissionYear, boolean present) {}

    record CohortPage(List<Cohort> items, int page, int size, long totalElements) {
        public CohortPage {
            items = List.copyOf(items);
        }
    }

    record TrainingClass(long id, String code, String name) {}

    /** Unit linked by the class's managing-unit FK. */
    record ManagingUnit(long id, String code, String name) {}

    record ClassReference(long id, String code, String name, ManagingUnit managingUnit) {}

    /** The source position code and original class may be missing. Combined classes are distinct by CTSV class ID. */
    record StudentClassFacts(long studentId, String mssv, String fullName, Long userId, String sourcePositionCode,
                             ClassReference trainingClass, List<ClassReference> effectiveCombinedClasses) {
        public StudentClassFacts {
            effectiveCombinedClasses = List.copyOf(effectiveCombinedClasses);
        }
    }

    record ClassSummary(long id, String code, String name, boolean present,
                        ManagingUnit managingUnit, String sourceCombinedClassFlag) {
        /** Null means the source has not provided the flag. */
        public Boolean combinedClass() {
            return sourceCombinedClassFlag == null ? null : "1".equals(sourceCombinedClassFlag);
        }
    }

    record ClassPage(List<ClassSummary> items, int page, int size, long totalElements) {
        public ClassPage {
            items = List.copyOf(items);
        }
    }

    record Program(long id, String code, String name, String curriculumType) {}

    enum AcademicLoadStatus { MISSING, PRESENT, NEEDS_VERIFICATION }

    /** A present row can have a null load. Multiple current rows require verification. */
    record AcademicLoad(AcademicLoadStatus status, BigDecimal registeredCourseLoad) {}

    /** Nullable relationships and source position code describe the student's current source profile. */
    record Student(long id, String mssv, String fullName, Long userId, String sourcePositionCode, boolean present,
                   TrainingClass trainingClass, Cohort cohort, Program program, AcademicLoad academicLoad) {}

    /** Searches by a literal code fragment, with zero-based pages and sizes from 1 to 100. */
    CohortPage findCohorts(String query, int page, int size);

    /** Searches class code or name literally, with zero-based pages and sizes from 1 to 100. */
    ClassPage findClasses(String query, int page, int size);

    /**
     * Lists distinct managing units for the caller's scoped CTSV class IDs, ordered by code then ID.
     * Classes with no managing-unit link, and IDs without a class, contribute no unit.
     */
    List<ManagingUnit> findManagingUnitsForClasses(List<Long> classIds);

    /**
     * Caller supplies an existing, source-present semester. The semester scopes only the academic load.
     * Students missing from Foundation are omitted. Duplicate IDs return one entry.
     */
    Map<Long, Student> findStudents(long semesterId, List<Long> studentIds);

    /** Reads class facts for known students in a delivered semester. Missing student IDs are omitted. */
    List<StudentClassFacts> findClassFacts(long semesterId, List<Long> studentIds);

    /**
     * Reads facts for students with the given training class or an effective assignment to it.
     * Each candidate includes all their effective combined classes in the semester.
     */
    List<StudentClassFacts> findClassRosterCandidates(long semesterId, long classId);

    static void validateStudentIds(List<Long> studentIds) {
        if (studentIds == null || studentIds.size() > MAX_STUDENT_IDS) {
            throw new IllegalArgumentException("Expected up to " + MAX_STUDENT_IDS + " CTSV student IDs");
        }
        if (studentIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Student IDs must be positive");
        }
    }
}
