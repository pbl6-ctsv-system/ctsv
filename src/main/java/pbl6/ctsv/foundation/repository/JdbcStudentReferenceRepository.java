package pbl6.ctsv.foundation.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.foundation.api.StudentReferenceSource;

public final class JdbcStudentReferenceRepository implements StudentReferenceSource {
    private static final String COHORT_FILTER = " FROM dbo.khoa_hoc WHERE :query = N'' OR CHARINDEX(:query, ma) > 0";
    private static final String CLASS_FILTER = """
            WHERE :query = N'' OR CHARINDEX(:query, l.ma) > 0 OR CHARINDEX(:query, l.ten) > 0
            """;
    private static final String STUDENT_FACTS = """
            SELECT s.id AS student_id, s.mssv, s.ho_ten, s.nguoi_dung_id, s.ma_chuc_vu_nguon,
                   s.dang_hien_dien_nguon AS student_present,
                   l.id AS class_id, l.ma AS class_code, l.ten AS class_name,
                   k.id AS cohort_id, k.ma AS cohort_code, k.nam_tuyen_sinh_nguon AS admission_year,
                   k.dang_hien_dien_nguon AS cohort_present,
                   c.id AS program_id, c.ma AS program_code, c.ten AS program_name,
                   c.loai_ctdt_nguon AS curriculum_type, a.row_count, a.course_load
            FROM dbo.sinh_vien s
            LEFT JOIN dbo.lop_sinh_hoat l ON l.id = s.lop_hien_tai_id
            LEFT JOIN dbo.khoa_hoc k ON k.id = s.khoa_hoc_hien_tai_id
            LEFT JOIN dbo.chuong_trinh_dao_tao c ON c.id = s.chuong_trinh_hien_tai_id
            CROSS APPLY (
                SELECT COUNT_BIG(*) AS row_count,
                       CASE WHEN COUNT_BIG(*) = 1 THEN MAX(h.hp_dang_ky) END AS course_load
                FROM dbo.du_lieu_hoc_vu_dong_bo h
                WHERE h.sinh_vien_id = s.id AND h.hoc_ky_id = :semesterId AND h.dang_hien_dien_nguon = 1
            ) a
            WHERE s.id IN (:studentIds)
            """;
    private static final String CLASS_FACTS = """
            WITH effective_assignments AS (
                SELECT DISTINCT p.sinh_vien_id, p.lop_ren_luyen_id
                FROM dbo.phan_lop_ren_luyen p
                JOIN dbo.lop_sinh_hoat l ON l.id = p.lop_ren_luyen_id
                WHERE p.hoc_ky_id = :semesterId AND p.dang_hien_dien_nguon = 1
                  AND l.lop_ghep_nguon = N'1'
            )
            SELECT s.id AS student_id, s.mssv, s.ho_ten, s.nguoi_dung_id, s.ma_chuc_vu_nguon,
                   original.id AS training_id, original.ma AS training_code, original.ten AS training_name,
                   ou.id AS training_unit_id, ou.ma AS training_unit_code, ou.ten AS training_unit_name,
                   combined.id AS combined_id, combined.ma AS combined_code, combined.ten AS combined_name,
                   cu.id AS combined_unit_id, cu.ma AS combined_unit_code, cu.ten AS combined_unit_name
            FROM dbo.sinh_vien s
            LEFT JOIN dbo.lop_sinh_hoat original ON original.id = s.lop_hien_tai_id
            LEFT JOIN dbo.don_vi ou ON ou.id = original.khoa_quan_ly_id
            LEFT JOIN effective_assignments a ON a.sinh_vien_id = s.id
            LEFT JOIN dbo.lop_sinh_hoat combined ON combined.id = a.lop_ren_luyen_id
            LEFT JOIN dbo.don_vi cu ON cu.id = combined.khoa_quan_ly_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcStudentReferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public CohortPage findCohorts(String query, int page, int size) {
        requireTransaction();
        var params = pageParameters(query, page, size, "cohort");
        long total = jdbc.queryForObject("SELECT COUNT_BIG(*)" + COHORT_FILTER, params, Long.class);
        var items = jdbc.query("""
                SELECT id AS cohort_id, ma AS cohort_code, nam_tuyen_sinh_nguon AS admission_year,
                       dang_hien_dien_nguon AS cohort_present
                """ + COHORT_FILTER + " ORDER BY ma, id OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
                params, (row, index) -> cohort(row));
        return new CohortPage(items, page, size, total);
    }

    @Override
    public ClassPage findClasses(String query, int page, int size) {
        requireTransaction();
        var params = pageParameters(query, page, size, "class");
        long total = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.lop_sinh_hoat l\n" + CLASS_FILTER,
                params, Long.class);
        var items = jdbc.query("""
                SELECT l.id, l.ma, l.ten, l.dang_hien_dien_nguon, l.lop_ghep_nguon,
                       u.id AS unit_id, u.ma AS unit_code, u.ten AS unit_name
                FROM dbo.lop_sinh_hoat l
                LEFT JOIN dbo.don_vi u ON u.id = l.khoa_quan_ly_id
                """ + CLASS_FILTER + "ORDER BY l.ma, l.id OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY",
                params, (row, index) -> classSummary(row));
        return new ClassPage(items, page, size, total);
    }

    @Override
    public List<ManagingUnit> findManagingUnitsForClasses(List<Long> classIds) {
        requireTransaction();
        if (classIds == null || classIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Expected positive CTSV class IDs");
        }
        if (classIds.isEmpty()) {
            return List.of();
        }
        // Bind the entire scope as one parameter, including scopes over SQL Server's parameter limit.
        String scopeJson = classIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
        var units = jdbc.query("""
                SELECT u.id AS unit_id, u.ma AS unit_code, u.ten AS unit_name
                FROM dbo.don_vi u
                WHERE u.id IN (
                    SELECT l.khoa_quan_ly_id
                    FROM OPENJSON(:scopeJson) WITH (class_id bigint '$') AS scoped_class
                    JOIN dbo.lop_sinh_hoat l ON l.id = scoped_class.class_id
                )
                ORDER BY u.ma, u.id
                """, Map.of("scopeJson", scopeJson), (row, index) -> managingUnit(row, ""));
        return List.copyOf(units);
    }

    @Override
    public Map<Long, Student> findStudents(long semesterId, List<Long> studentIds) {
        StudentReferenceSource.validateStudentIds(studentIds);
        requireTransaction();
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        var params = Map.of("semesterId", semesterId, "studentIds", Set.copyOf(studentIds));
        var results = new HashMap<Long, Student>();
        for (Student student : jdbc.query(STUDENT_FACTS, params, (row, index) -> student(row))) {
            results.put(student.id(), student);
        }
        return Map.copyOf(results);
    }

    @Override
    public List<StudentClassFacts> findClassFacts(long semesterId, List<Long> studentIds) {
        StudentReferenceSource.validateStudentIds(studentIds);
        requireTransaction();
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return queryClassFacts("WHERE s.id IN (:studentIds)",
                Map.of("semesterId", semesterId, "studentIds", Set.copyOf(studentIds)));
    }

    @Override
    public List<StudentClassFacts> findClassRosterCandidates(long semesterId, long classId) {
        requireTransaction();
        return queryClassFacts("""
                WHERE s.lop_hien_tai_id = :classId
                   OR EXISTS (SELECT 1 FROM effective_assignments candidate
                              WHERE candidate.sinh_vien_id = s.id AND candidate.lop_ren_luyen_id = :classId)
                """, Map.of("semesterId", semesterId, "classId", classId));
    }

    private List<StudentClassFacts> queryClassFacts(String scope, Map<String, ?> params) {
        var rows = jdbc.query(CLASS_FACTS + scope + "\nORDER BY s.id, combined.id", params,
                (row, index) -> new StudentClassRow(row.getLong("student_id"), row.getString("mssv"),
                        row.getString("ho_ten"), row.getObject("nguoi_dung_id", Long.class),
                        row.getString("ma_chuc_vu_nguon"),
                        classReference(row, "training_"), classReference(row, "combined_")));
        var byStudent = rows.stream().collect(Collectors.groupingBy(
                StudentClassRow::studentId, LinkedHashMap::new, Collectors.toList()));
        return byStudent.values().stream().map(group -> {
            var student = group.getFirst();
            var combinedClasses = group.stream().map(StudentClassRow::combinedClass).filter(Objects::nonNull).toList();
            return new StudentClassFacts(student.studentId(), student.mssv(), student.fullName(), student.userId(),
                    student.sourcePositionCode(), student.trainingClass(), combinedClasses);
        }).toList();
    }

    private record StudentClassRow(long studentId, String mssv, String fullName, Long userId, String sourcePositionCode,
                                   ClassReference trainingClass, ClassReference combinedClass) {}

    private static ClassReference classReference(ResultSet row, String prefix) throws SQLException {
        Long id = row.getObject(prefix + "id", Long.class);
        return id == null ? null : new ClassReference(id, row.getString(prefix + "code"),
                row.getString(prefix + "name"), managingUnit(row, prefix));
    }

    private static Student student(ResultSet row) throws SQLException {
        Long classId = row.getObject("class_id", Long.class);
        var trainingClass = classId == null ? null
                : new TrainingClass(classId, row.getString("class_code"), row.getString("class_name"));
        Long programId = row.getObject("program_id", Long.class);
        var program = programId == null ? null : new Program(programId, row.getString("program_code"),
                row.getString("program_name"), row.getString("curriculum_type"));
        long rowCount = row.getLong("row_count");
        var status = AcademicLoadStatus.NEEDS_VERIFICATION;
        if (rowCount == 0) {
            status = AcademicLoadStatus.MISSING;
        } else if (rowCount == 1) {
            status = AcademicLoadStatus.PRESENT;
        }
        return new Student(row.getLong("student_id"), row.getString("mssv"), row.getString("ho_ten"),
                row.getObject("nguoi_dung_id", Long.class), row.getString("ma_chuc_vu_nguon"),
                row.getBoolean("student_present"),
                trainingClass, cohort(row), program,
                new AcademicLoad(status, row.getBigDecimal("course_load")));
    }

    private static Cohort cohort(ResultSet row) throws SQLException {
        Long id = row.getObject("cohort_id", Long.class);
        return id == null ? null : new Cohort(id, row.getString("cohort_code"),
                row.getString("admission_year"), row.getBoolean("cohort_present"));
    }

    private static ClassSummary classSummary(ResultSet row) throws SQLException {
        return new ClassSummary(row.getLong("id"), row.getString("ma"), row.getString("ten"),
                row.getBoolean("dang_hien_dien_nguon"), managingUnit(row, ""), row.getString("lop_ghep_nguon"));
    }

    private static ManagingUnit managingUnit(ResultSet row, String prefix) throws SQLException {
        Long unitId = row.getObject(prefix + "unit_id", Long.class);
        return unitId == null ? null
                : new ManagingUnit(unitId, row.getString(prefix + "unit_code"), row.getString(prefix + "unit_name"));
    }

    private static Map<String, Object> pageParameters(String query, int page, int size, String catalog) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Invalid " + catalog + " page");
        }
        return Map.of("query", query == null ? "" : query.strip(), "offset", (long) page * size, "size", size);
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Foundation student reference source requires a local transaction");
        }
    }
}
