package pbl6.ctsv.hocbong.kkht.configuration.rules;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import pbl6.ctsv.hocbong.kkht.configuration.rules.entity.RuleVersion;
import pbl6.ctsv.hocbong.kkht.round.RoundFailure;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipAudit;

class RuleRepository {
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    RuleRepository(EntityManager em,JdbcTemplate jdbc) { this.em=em;this.jdbc=jdbc; }
    void serializeWrites() {
        em.flush();
        Integer result=jdbc.queryForObject("""
                DECLARE @result int,@unused bigint;
                IF @@TRANCOUNT=0 SELECT TOP(0) @unused=id FROM dbo.kkht_phien_ban_quy_tac;
                EXEC @result=sys.sp_getapplock @Resource=N'ctsv.hocbong.rule-versions',
                    @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
                SELECT @result
                """,Integer.class);
        if(result==null||result<0)throw new RoundFailure(503,"DEPENDENCY_UNAVAILABLE");
    }
    int nextNumber() {
        int latest=jdbc.queryForObject("SELECT COALESCE(MAX(phien_ban),0) FROM dbo.kkht_phien_ban_quy_tac WITH(UPDLOCK,HOLDLOCK)",Integer.class);
        if(latest==Integer.MAX_VALUE)throw new RoundFailure(503,"DEPENDENCY_UNAVAILABLE");
        return latest+1;
    }
    RuleVersion get(long id) {
        var value=em.find(RuleVersion.class,id);if(value==null)throw new RoundFailure(404,"NOT_FOUND");return value;
    }
    RuleVersion lock(long id) {
        em.flush();var ids=jdbc.query("SELECT id FROM dbo.kkht_phien_ban_quy_tac WITH(UPDLOCK,HOLDLOCK) WHERE id=?",(row,n)->row.getLong(1),id);
        if(ids.isEmpty())throw new RoundFailure(404,"NOT_FOUND");em.clear();return get(id);
    }
    List<RuleVersion> list(int page,int size) {
        return em.createQuery("from RuleVersion order by number desc",RuleVersion.class).setFirstResult(Math.multiplyExact(page,size)).setMaxResults(size).getResultList();
    }
    long count(){return em.createQuery("select count(r) from RuleVersion r",Long.class).getSingleResult();}
    void save(RuleVersion value){if(value.getId()==null)em.persist(value);em.flush();em.refresh(value);}
    void audit(ScholarshipAudit value){em.persist(value);em.flush();}
}
