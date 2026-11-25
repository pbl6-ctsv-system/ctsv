package pbl6.ctsv.drl.reference;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.foundation.api.StaffReferenceSource;
import pbl6.ctsv.foundation.api.StaffReferenceSource.Staff;
import pbl6.ctsv.foundation.api.StudentReferenceSource;
import pbl6.ctsv.foundation.api.StudentReferenceSource.ClassReference;
import pbl6.ctsv.foundation.api.StudentReferenceSource.StudentClassFacts;

/** Foundation facts used by DRL in the caller's local transaction. */
@Service
@ConditionalOnProperty(name = "ctsv.foundation.enabled", havingValue = "true")
public final class DrlReferenceData {
    private static final String DEAN_POSITION = "CV1209";
    private static final String DEPUTY_DEAN_POSITION = "CV1210";

    public enum SemesterStatus { PRESENT, ABSENT_FROM_SOURCE, NOT_FOUND }

    public enum EvaluationClassStatus { RESOLVED, NO_TRAINING_CLASS, NEEDS_VERIFICATION }

    public record StudentBatch(SemesterStatus semesterStatus, Map<Long, StudentReferenceSource.Student> students) {
        public StudentBatch {
            students = Map.copyOf(students);
        }
    }

    public record EvaluationClass(long studentId, String mssv, String fullName, Long userId, String sourcePositionCode,
                                  ClassReference trainingClass, ClassReference evaluationClass,
                                  EvaluationClassStatus status) {}

    public record EvaluationClassBatch(SemesterStatus semesterStatus, Map<Long, EvaluationClass> students) {
        public EvaluationClassBatch {
            students = Map.copyOf(students);
        }
    }

    public record ClassRoster(SemesterStatus semesterStatus, List<EvaluationClass> members,
                              List<EvaluationClass> studentsNeedingVerification) {
        public ClassRoster {
            members = List.copyOf(members);
            studentsNeedingVerification = List.copyOf(studentsNeedingVerification);
        }
    }

    private final AcademicSemesterSource semesters;
    private final StudentReferenceSource students;
    private final StaffReferenceSource staff;

    public DrlReferenceData(AcademicSemesterSource semesters, StudentReferenceSource students, StaffReferenceSource staff) {
        this.semesters = semesters;
        this.students = students;
        this.staff = staff;
    }

    public AcademicSemesterSource.Page findSemesters(String query, int page, int size) {
        return semesters.findPage(query, page, size);
    }

    public Optional<AcademicSemesterSource.Semester> findSemester(long semesterId) {
        return semesters.findById(semesterId);
    }

    public StudentReferenceSource.CohortPage findCohorts(String query, int page, int size) {
        return students.findCohorts(query, page, size);
    }

    public StudentReferenceSource.ClassPage findClasses(String query, int page, int size) {
        return students.findClasses(query, page, size);
    }

    /** The caller supplies the class IDs that belong to the current evaluation scope. */
    public List<StudentReferenceSource.ManagingUnit> findManagingFaculties(List<Long> classIds) {
        return students.findManagingUnitsForClasses(classIds);
    }

    public List<Staff> findDeans(long facultyId) {
        return staff.findServingStaffByUnitAndPositionCodes(facultyId, Set.of(DEAN_POSITION));
    }

    public List<Staff> findFacultyRepresentatives(long facultyId) {
        return staff.findServingStaffByUnitAndPositionCodes(facultyId, Set.of(DEAN_POSITION, DEPUTY_DEAN_POSITION));
    }

    public List<Staff> findHomeroomReplacementCandidates(long evaluationClassId, String query) {
        return staff.findStaffByClassManagingUnit(evaluationClassId, query);
    }

    public Optional<Staff> findSuggestedHomeroomTeacher(long semesterId, long evaluationClassId) {
        var teachers = staff.findAssignedHomeroomTeachers(semesterId, evaluationClassId);
        return teachers.size() == 1 ? Optional.of(teachers.getFirst()) : Optional.empty();
    }

    public Optional<Staff> findStaff(long staffId) {
        return staff.findById(staffId);
    }

    public StudentBatch findStudents(long semesterId, List<Long> studentIds) {
        StudentReferenceSource.validateStudentIds(studentIds);
        var status = semesterStatus(semesterId);
        return new StudentBatch(status,
                status == SemesterStatus.PRESENT ? students.findStudents(semesterId, studentIds) : Map.of());
    }

    /** Resolves each student's evaluation class using Foundation data for a delivered semester. */
    public EvaluationClassBatch findEvaluationClasses(long semesterId, List<Long> studentIds) {
        StudentReferenceSource.validateStudentIds(studentIds);
        var status = semesterStatus(semesterId);
        if (status != SemesterStatus.PRESENT) {
            return new EvaluationClassBatch(status, Map.of());
        }
        var results = students.findClassFacts(semesterId, studentIds).stream()
                .map(DrlReferenceData::resolveEvaluationClass)
                .collect(Collectors.toMap(EvaluationClass::studentId, Function.identity()));
        return new EvaluationClassBatch(status, results);
    }

    /** Returns the confirmed roster and any candidates whose assignments conflict in the semester. */
    public ClassRoster findClassRoster(long semesterId, long classId) {
        if (classId <= 0) {
            throw new IllegalArgumentException("Expected a positive CTSV class ID");
        }
        var status = semesterStatus(semesterId);
        if (status != SemesterStatus.PRESENT) {
            return new ClassRoster(status, List.of(), List.of());
        }
        var candidates = students.findClassRosterCandidates(semesterId, classId).stream()
                .map(DrlReferenceData::resolveEvaluationClass)
                .toList();
        var members = candidates.stream()
                .filter(student -> student.evaluationClass() != null && student.evaluationClass().id() == classId)
                .toList();
        var studentsNeedingVerification = candidates.stream()
                .filter(student -> student.status() == EvaluationClassStatus.NEEDS_VERIFICATION)
                .toList();
        return new ClassRoster(status, members, studentsNeedingVerification);
    }

    private SemesterStatus semesterStatus(long semesterId) {
        return semesters.findById(semesterId)
                .map(semester -> semester.present() ? SemesterStatus.PRESENT : SemesterStatus.ABSENT_FROM_SOURCE)
                .orElse(SemesterStatus.NOT_FOUND);
    }

    private static EvaluationClass resolveEvaluationClass(StudentClassFacts student) {
        var combinedClasses = student.effectiveCombinedClasses();
        var trainingClass = student.trainingClass();
        if (combinedClasses.size() > 1) {
            return new EvaluationClass(student.studentId(), student.mssv(), student.fullName(), student.userId(),
                    student.sourcePositionCode(), trainingClass, null, EvaluationClassStatus.NEEDS_VERIFICATION);
        }
        var resolved = combinedClasses.isEmpty() ? trainingClass : combinedClasses.getFirst();
        var status = resolved == null ? EvaluationClassStatus.NO_TRAINING_CLASS : EvaluationClassStatus.RESOLVED;
        return new EvaluationClass(student.studentId(), student.mssv(), student.fullName(), student.userId(),
                student.sourcePositionCode(), trainingClass, resolved, status);
    }
}
