package pbl6.ctsv.foundation.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.foundation.api.FoundationAccess.Failure;
import pbl6.ctsv.foundation.api.ScholarshipCombinationSource;

/** Foundation lookup projections and SQL Server locking reads for combination writes. */
public final class ScholarshipCombinationRepository implements ScholarshipCombinationSource {
    private final EntityManager em;
    private final JdbcTemplate jdbc;

    public ScholarshipCombinationRepository(EntityManager em, JdbcTemplate jdbc) {
        this.em = em;
        this.jdbc = jdbc;
    }

    @Override
    public Page findPage(Kind kind, String q, int page, int size) {
        requireTransaction();
        long offset = (long) page * size;
        if (page < 0 || size < 1 || size > 100 || offset > 100_000) {
            throw new IllegalArgumentException("Invalid combination source page");
        }
        String search = q == null ? "" : q.strip();
        if (search.codePointCount(0, search.length()) > 128) {
            throw new IllegalArgumentException("Combination source query exceeds 128 characters");
        }

        String entity = switch (kind) {
            case PROGRAMS -> "ChuongTrinhDaoTao";
            case COHORTS -> "KhoaHoc";
            case MAJORS -> "NganhNoiBo";
            case CURRICULA -> "KhungChuongTrinh";
        };
        boolean curricula = kind == Kind.CURRICULA;
        boolean hasName = kind == Kind.PROGRAMS || kind == Kind.MAJORS;
        String code = curricula ? "e.maKhungNguon" : "e.ma";
        String name = hasName ? "e.ten" : "cast(null as string)";
        String major = kind == Kind.PROGRAMS ? "e.nganhNoiBoId" : "cast(null as long)";
        String from = " from " + entity + " e where e.dangHienDienNguon=true and trim(" + code + ")<>''";
        if (!search.isEmpty()) {
            String like = " like :pattern escape '~'";
            from += " and (" + code + like + (hasName ? " or " + name + like : "") + ")";
        }
        String selectId = curricula ? "distinct " + code : "e.id";
        String countKey = curricula ? "distinct " + code : "e.id";
        String order = " order by " + code + (curricula ? "" : ",e.id");
        var rows = em.createQuery("select " + selectId + "," + code + "," + name + "," + major
            + from + order, Object[].class);
        var count = em.createQuery("select count(" + countKey + ")" + from, Long.class);
        if (!search.isEmpty()) {
            String pattern = "%" + search.replace("~", "~~").replace("%", "~%").replace("_", "~_")
                .replace("[", "~[") + "%";
            rows.setParameter("pattern", pattern);
            count.setParameter("pattern", pattern);
        }
        var items = rows.setFirstResult((int) offset).setMaxResults(size).getResultList().stream()
            .map(row -> new Option(row[0].toString(), (String) row[1], (String) row[2],
                row[3] == null ? null : row[3].toString()))
            .toList();
        return new Page(items, count.getSingleResult());
    }

    @Override
    public Resolved resolve(long programId, long cohortId, long majorId, String curriculumCode) {
        requireTransaction();
        if (programId <= 0) throw invalidSource("programId");
        if (cohortId <= 0) throw invalidSource("cohortId");
        if (majorId <= 0) throw invalidSource("majorId");
        if (!hasCode(curriculumCode)) throw invalidSource("curriculumCode");

        em.flush();
        // Hold shared locks on the qualifying source data through the Scholarship write and commit.
        var programs = jdbc.query("""
            SELECT ma,ten,nganh_noi_bo_id
            FROM dbo.chuong_trinh_dao_tao WITH(HOLDLOCK)
            WHERE id=? AND dang_hien_dien_nguon=1
            """, (row, index) -> new Program(row.getString("ma"), row.getString("ten"),
                row.getObject("nganh_noi_bo_id", Long.class)), programId);
        if (programs.isEmpty() || !hasCode(programs.getFirst().code())) throw invalidSource("programId");
        Program program = programs.getFirst();
        if (program.majorId() == null || program.majorId() != majorId) throw invalidSource("majorId");

        String cohort = requireCode(jdbc.queryForList("""
            SELECT ma FROM dbo.khoa_hoc WITH(HOLDLOCK) WHERE id=? AND dang_hien_dien_nguon=1
            """, String.class, cohortId), "cohortId");
        String major = requireCode(jdbc.queryForList("""
            SELECT ma FROM dbo.nganh_noi_bo WITH(HOLDLOCK) WHERE id=? AND dang_hien_dien_nguon=1
            """, String.class, majorId), "majorId");
        String curriculum = requireCode(jdbc.queryForList("""
            SELECT TOP (1) ma_khung_nguon
            FROM dbo.khung_chuong_trinh WITH(HOLDLOCK)
            WHERE ma_khung_nguon=? AND dang_hien_dien_nguon=1
            ORDER BY id
            """, String.class, curriculumCode), "curriculumCode");
        return new Resolved(programId, program.code(), program.name(), cohortId, cohort, majorId, major, curriculum);
    }

    private static String requireCode(List<String> codes, String field) {
        if (codes.isEmpty() || !hasCode(codes.getFirst())) throw invalidSource(field);
        return codes.getFirst();
    }

    private static boolean hasCode(String code) {
        return code != null && !code.isBlank();
    }

    private static Failure invalidSource(String field) {
        return new Failure(400, "INVALID_COMBINATION_SOURCE", field);
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Combination source requires a local transaction");
        }
    }

    private record Program(String code, String name, Long majorId) {}
}
