package pbl6.ctsv.drl.repository;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

/**
 * Internal persistence for DRL application services; never a public module boundary.
 * The caller owns the transaction, authorization, validation and write lock order.
 * Construct with CTSV's shared EntityManager/JdbcTemplate, as with FoundationRepository.
 * Updates use managed entities and dirty checking; detached graphs are never merged.
 */
public final class DrlRepository {
    private static final Set<Class<?>> TABLES = Set.of(
            RlBieuMau.class, RlNhomTieuChi.class, RlBieuMauCt.class, RlXepLoai.class,
            RlDot.class, RlKhoaDot.class, RlLopDot.class, RlPhieu.class,
            RlPhieuDiemCt.class, RlHopLop.class, RlVanBanKy.class);

    private final EntityManager em;
    private final JdbcTemplate jdbc;

    public DrlRepository(EntityManager em, JdbcTemplate jdbc) {
        this.em = Objects.requireNonNull(em);
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public <T extends DrlEntity> T persist(T entity) {
        table(entity.getClass());
        transaction();
        if (entity.getId() != null) throw new IllegalArgumentException("Expected a new DRL entity");
        em.persist(entity);
        return entity;
    }

    public <T extends DrlEntity> Optional<T> find(Class<T> type, long id) {
        table(type);
        transaction();
        return Optional.ofNullable(em.find(type, id));
    }

    public void remove(DrlEntity entity) {
        table(entity.getClass());
        managed(entity);
        em.remove(entity);
    }

    public void refresh(DrlEntity entity) {
        table(entity.getClass());
        managed(entity);
        em.refresh(entity);
    }

    public void flush() {
        transaction();
        em.flush();
    }

    /** Use after caller-owned bulk SQL when affected managed objects may be stale. */
    public void flushAndClear() {
        flush();
        em.clear();
    }

    /** Serialize writes on a class-round; refresh any root already in the persistence context. */
    public Optional<RlLopDot> lockLopDot(long id) {
        flush();
        var ids = jdbc.query("SELECT ID FROM dbo.RLLopDot WITH (UPDLOCK, HOLDLOCK) WHERE ID = ?",
                (row, index) -> row.getLong(1), id);
        if (ids.isEmpty()) return Optional.empty();
        var root = em.find(RlLopDot.class, id);
        em.refresh(root);
        return Optional.of(root);
    }

    public List<RlBieuMau> formsByCode(String code) {
        return rows(RlBieuMau.class, "maBieuMau", code, "soPhienBan, id");
    }

    public List<RlNhomTieuChi> groupsForForm(long formId) {
        return rows(RlNhomTieuChi.class, "bieuMauId", formId, "thuTu, id");
    }

    public List<RlBieuMauCt> criteriaForGroup(long groupId) {
        return rows(RlBieuMauCt.class, "nhomTieuChiId", groupId, "thuTu, id");
    }

    public List<RlXepLoai> ratingsForForm(long formId) {
        return rows(RlXepLoai.class, "bieuMauId", formId, "diemToiThieu, id");
    }

    public List<RlDot> roundsForSemester(long semesterId) {
        return rows(RlDot.class, "hocKyId", semesterId, "id");
    }

    public List<RlKhoaDot> facultiesForRound(long roundId) {
        return rows(RlKhoaDot.class, "dotId", roundId, "id");
    }

    public List<RlLopDot> classesForFacultyRound(long facultyRoundId) {
        return rows(RlLopDot.class, "khoaDotId", facultyRoundId, "id");
    }

    public List<RlPhieu> ballotsForClass(long classRoundId) {
        return rows(RlPhieu.class, "lopDotId", classRoundId, "id");
    }

    public List<RlPhieuDiemCt> scoresForBallot(long ballotId) {
        return rows(RlPhieuDiemCt.class, "phieuId", ballotId, "bieuMauCtId, id");
    }

    public Optional<RlHopLop> meetingForClass(long classRoundId) {
        return rows(RlHopLop.class, "lopDotId", classRoundId, "id").stream().findFirst();
    }

    public List<RlVanBanKy> documentsForClass(long classRoundId) {
        return rows(RlVanBanKy.class, "lopDotId", classRoundId, "loaiVanBan, id");
    }

    public Optional<RlKetQuaSv> resultForBallot(long ballotId) {
        return results("phieuId", ballotId).stream().findFirst();
    }

    public List<RlKetQuaSv> resultsForClass(long classRoundId) {
        return results("lopDotId", classRoundId);
    }

    public List<RlKetQuaSv> resultsForRound(long roundId) {
        return results("dotId", roundId);
    }

    private <T extends DrlEntity> List<T> rows(Class<T> type, String field, Object value, String order) {
        table(type);
        transaction();
        // Identifiers are private constants from the typed methods above, never request input.
        return em.createQuery("from " + type.getSimpleName() + " where " + field + " = :value order by " + order, type)
                .setParameter("value", value).getResultList();
    }

    private List<RlKetQuaSv> results(String field, long value) {
        flush();
        var results = em.createQuery("from RlKetQuaSv where " + field + " = :value order by phieuId", RlKetQuaSv.class)
                .setParameter("value", value).getResultList();
        // This computed view changes after score/confirmation writes and as the deadline passes.
        // Detach only the read models so later queries re-read SQL without discarding pending writes.
        results.forEach(em::detach);
        return List.copyOf(results);
    }

    private static void table(Class<?> type) {
        if (!TABLES.contains(type)) throw new IllegalArgumentException("Unsupported DRL table mapping");
    }

    private void managed(DrlEntity entity) {
        transaction();
        if (!em.contains(entity)) throw new IllegalArgumentException("Expected a managed DRL entity");
    }

    private void transaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !em.isJoinedToTransaction()) {
            throw new IllegalStateException("DRL persistence requires the caller's JPA transaction");
        }
    }
}
