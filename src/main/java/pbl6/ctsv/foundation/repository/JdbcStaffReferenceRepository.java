package pbl6.ctsv.foundation.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pbl6.ctsv.foundation.api.StaffReferenceSource;

public final class JdbcStaffReferenceRepository implements StaffReferenceSource {
    private static final String STAFF = """
            SELECT c.id, c.ma_cb, c.ho_ten, c.nguoi_dung_id, c.don_vi_nguon_id, c.ma_chuc_vu_nguon
            FROM dbo.can_bo c
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcStaffReferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    public Optional<Staff> findById(long staffId) {
        return read("WHERE c.id = :staffId", Map.of("staffId", staffId)).stream().findFirst();
    }

    @Override
    public List<Staff> findServingStaffByUnitAndPositionCodes(long unitId, Set<String> positionCodes) {
        if (positionCodes.isEmpty()) {
            return List.of();
        }
        return read("""
                WHERE c.don_vi_nguon_id = :unitId
                  AND c.ma_chuc_vu_nguon IN (:positionCodes)
                  AND (c.huu_tri_nguon IS NULL OR c.huu_tri_nguon <> N'1')
                  AND (c.ma_bo_phan_nguon IS NULL OR c.ma_bo_phan_nguon <> N'990')
                ORDER BY c.ma_cb, c.id
                """, Map.of("unitId", unitId, "positionCodes", positionCodes));
    }

    @Override
    public List<Staff> findStaffByClassManagingUnit(long classId, String query) {
        return read("""
                JOIN dbo.lop_sinh_hoat l ON l.khoa_quan_ly_id = c.don_vi_nguon_id
                WHERE l.id = :classId
                  AND (:query = N'' OR CHARINDEX(:query, c.ho_ten) > 0 OR CHARINDEX(:query, c.ma_cb) > 0)
                ORDER BY c.ma_cb, c.id
                """, Map.of("classId", classId, "query", query == null ? "" : query.strip()));
    }

    @Override
    public List<Staff> findAssignedHomeroomTeachers(long semesterId, long classId) {
        return read("""
                WHERE EXISTS (
                    SELECT 1
                    FROM dbo.phan_cong_co_van_lop p
                    WHERE p.lop_id = :classId AND p.hoc_ky_id = :semesterId
                      AND p.can_bo_chu_nhiem_id = c.id
                )
                """, Map.of("semesterId", semesterId, "classId", classId));
    }

    private List<Staff> read(String scope, Map<String, ?> params) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Foundation staff reference source requires a local transaction");
        }
        return List.copyOf(jdbc.query(STAFF + scope, params, (row, index) -> new Staff(
                row.getLong("id"), row.getString("ma_cb"), row.getString("ho_ten"),
                row.getObject("nguoi_dung_id", Long.class), row.getObject("don_vi_nguon_id", Long.class),
                row.getString("ma_chuc_vu_nguon"))));
    }
}
