package pbl6.ctsv.foundation.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.foundation.api.AcademicSemesterSource;
import pbl6.ctsv.foundation.api.FoundationAccess.Failure;
import pbl6.ctsv.foundation.api.ScholarshipScopeSource;

/** Fixed cohort/major mapping: admit only students whose mapped first semester is on/before the selected semester.
 * Semester source codes YY10/YY20/YY21 define chronology; raw so_thu_tu and admission-year guesses are not used.
 * No transfer-history or grade/status eligibility filter here.
 * Native read locks protect the complete catalog (including missing mappings/presence) until caller commits.
 */
public final class ScholarshipScopeRepository implements ScholarshipScopeSource {
    private final JdbcTemplate jdbc;
    private final AcademicSemesterSource semesters;
    private final ObjectMapper json;
    public ScholarshipScopeRepository(JdbcTemplate jdbc,AcademicSemesterSource semesters,ObjectMapper json) {
        this.jdbc=jdbc;this.semesters=semesters;this.json=json;
    }
    @Override public Catalog catalog(long sourceSemesterId) {
        if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Scope source requires a local transaction");
        var semester=semesters.findById(sourceSemesterId).orElseThrow(()->new Failure(404,"NOT_FOUND","sourceSemesterId"));
        if(!semester.present()||!semesterCode(semester.code())) throw unavailable();
        var rows=jdbc.query("""
            SELECT s.id,s.dang_hien_dien_nguon,s.khoa_hoc_hien_tai_id,s.chuong_trinh_hien_tai_id,
                k.id AS cohort_id,k.ma AS cohort_code,k.dang_hien_dien_nguon AS cohort_present,
                c.id AS program_id,c.nganh_noi_bo_id,c.dang_hien_dien_nguon AS program_present,
                n.id AS major_id,n.ma AS major_code,n.ten AS major_name,n.dang_hien_dien_nguon AS major_present,
                s.hoc_ky_bat_dau_nguon_id,h.ma AS first_semester_code,h.dang_hien_dien_nguon AS first_semester_present
            FROM dbo.sinh_vien s WITH(HOLDLOCK)
            LEFT JOIN dbo.khoa_hoc k WITH(HOLDLOCK) ON k.id=s.khoa_hoc_hien_tai_id
            LEFT JOIN dbo.chuong_trinh_dao_tao c WITH(HOLDLOCK) ON c.id=s.chuong_trinh_hien_tai_id
            LEFT JOIN dbo.nganh_noi_bo n WITH(HOLDLOCK) ON n.id=c.nganh_noi_bo_id
            LEFT JOIN dbo.hoc_ky h WITH(HOLDLOCK) ON h.id=s.hoc_ky_bat_dau_nguon_id
            ORDER BY s.id
            """,(row,index)-> {
                var values=new ArrayList<Object>();
                for(int column=1;column<=17;column++) values.add(row.getObject(column));
                return values;
            });
        // Full rows and term identity are hashed, never a filtered page, count alone or MAX(rowversion).
        final String revision;
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            digest.update("foundation:fixed-student-cohort-major:v1|".getBytes(StandardCharsets.UTF_8));
            digest.update(json.writeValueAsBytes(List.of(sourceSemesterId,semester.code()==null?"":semester.code(),
                semester.version()==null?new byte[0]:semester.version())));
            revision=Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(json.writeValueAsBytes(rows)));
        } catch(Exception e) {throw new IllegalStateException("Cannot fingerprint scope source",e);}
        record Pair(long cohort,long major) {}
        var groups=new LinkedHashMap<Pair,Group>();
        for(var row:rows) {
            if(!Boolean.TRUE.equals(row.get(1))) continue;
            if(row.get(14)==null||!semesterCode((String)row.get(15))||!Boolean.TRUE.equals(row.get(16))) throw unavailable();
            if(((String)row.get(15)).compareTo(semester.code())>0) continue;
            if(row.get(4)==null||row.get(7)==null||row.get(10)==null||!Boolean.TRUE.equals(row.get(6))
                    ||!Boolean.TRUE.equals(row.get(9))||!Boolean.TRUE.equals(row.get(13))) throw unavailable();
            long cohort=((Number)row.get(4)).longValue(),major=((Number)row.get(10)).longValue();
            var key=new Pair(cohort,major);var previous=groups.get(key);
            int count=previous==null?1:Math.addExact(previous.studentCount(),1);
            groups.put(key,new Group(cohort,(String)row.get(5),major,(String)row.get(11),(String)row.get(12),count));
        }
        return new Catalog(revision,groups.values().stream().sorted(Comparator.comparingLong(Group::cohortId).thenComparingLong(Group::majorId)).toList());
    }
    private static boolean semesterCode(String code) {return code!=null&&code.matches("[0-9]{2}(10|20|21)");}
    private static Failure unavailable() {return new Failure(503,"SCOPE_SOURCE_UNAVAILABLE","sourceSemesterId");}
}
