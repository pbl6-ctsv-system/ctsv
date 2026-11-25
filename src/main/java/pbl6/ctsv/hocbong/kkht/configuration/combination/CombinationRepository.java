package pbl6.ctsv.hocbong.kkht.configuration.combination;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import pbl6.ctsv.hocbong.kkht.configuration.combination.entity.TrainingCombination;
import pbl6.ctsv.hocbong.kkht.round.RoundFailure;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipAudit;

/** JPA writes/projections; native SQL only for transaction fencing and fresh rowversion reads. */
class CombinationRepository {
    private final EntityManager em;
    private final JdbcTemplate jdbc;

    CombinationRepository(EntityManager em, JdbcTemplate jdbc) { this.em = em; this.jdbc = jdbc; }

    void serializeWrites() {
        // A table SELECT starts SQL Server's implicit transaction on a freshly enlisted JDBC connection.
        Integer result = jdbc.queryForObject("""
                DECLARE @result int, @unused bigint;
                IF @@TRANCOUNT = 0 SELECT TOP (0) @unused=id FROM dbo.hb_to_hop_dao_tao;
                EXEC @result=sys.sp_getapplock @Resource=N'ctsv.hocbong.combinations',
                    @LockMode='Exclusive', @LockOwner='Transaction', @LockTimeout=5000;
                SELECT @result
                """, Integer.class);
        if (result == null || result < 0) throw new RoundFailure(503, "DEPENDENCY_UNAVAILABLE");
    }

    TrainingCombination get(long id) {
        var row = em.find(TrainingCombination.class, id);
        if (row == null) throw new RoundFailure(404, "NOT_FOUND");
        return row;
    }

    TrainingCombination lock(long id) {
        em.flush();
        var ids = jdbc.query("SELECT id FROM dbo.hb_to_hop_dao_tao WITH (UPDLOCK,HOLDLOCK) WHERE id=?",
                (rs,n) -> rs.getLong(1), id);
        if (ids.isEmpty()) throw new RoundFailure(404, "NOT_FOUND");
        em.clear();
        return get(id);
    }

    List<TrainingCombination> list(int page, int size) {
        return em.createQuery("from TrainingCombination order by id desc", TrainingCombination.class)
                .setFirstResult(Math.multiplyExact(page, size)).setMaxResults(size).getResultList();
    }

    long count() {
        return em.createQuery("select count(c) from TrainingCombination c", Long.class).getSingleResult();
    }

    boolean overlaps(long excludingId, CombinationDtos.Command command) {
        return em.createQuery("""
                select count(c) from TrainingCombination c where c.id<>:id
                and c.programId=:program and c.cohortId=:cohort and c.majorId=:major
                and c.effectiveFrom<=:until and (c.effectiveTo is null or c.effectiveTo>=:since)
                """, Long.class).setParameter("id", excludingId).setParameter("program", command.programId())
                .setParameter("cohort", command.cohortId()).setParameter("major", command.majorId())
                .setParameter("until", command.effectiveTo() == null ? LocalDate.of(9999,12,31) : command.effectiveTo())
                .setParameter("since", command.effectiveFrom()).getSingleResult() > 0;
    }

    void save(TrainingCombination entity) {
        if (entity.getId() == null) em.persist(entity);
        em.flush();
        em.refresh(entity);
    }

    void delete(TrainingCombination entity) { em.remove(entity); em.flush(); }
    void audit(ScholarshipAudit event) { em.persist(event); em.flush(); }
}
