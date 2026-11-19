package pbl6.ctsv.hocbong.kkht.round;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pbl6.ctsv.hocbong.kkht.round.entity.*;

/** JPA for HB persistence; SQL is limited to transaction fencing and rowversion CAS. */
class RoundRepository {
    private final EntityManager em;
    private final JdbcTemplate jdbc;

    RoundRepository(EntityManager em,JdbcTemplate jdbc) { this.em=em; this.jdbc=jdbc; }

    Map<Long,ScholarshipSemester> semestersBySources(java.util.Collection<Long> ids) {
        if(ids.isEmpty()) return Map.of();
        var result=new HashMap<Long,ScholarshipSemester>();
        var sourceIds=ids.stream().map(Object::toString).toList();
        em.createQuery("""
            select m.sourceSemesterCode,s from SourceSemesterMapping m,ScholarshipSemester s
            where m.semesterId=s.id and m.sourceSystem='CTSV' and m.sourceKind='ctsv_hoc_ky_id'
            and m.sourceRoundCode is null and m.sourceSemesterCode in :ids
            """,Object[].class).setParameter("ids",sourceIds).getResultList()
            .forEach(row->result.put(Long.parseLong((String)row[0]),(ScholarshipSemester)row[1]));
        return result;
    }
    Map<String,ScholarshipSemester> semestersByPeriods(java.util.Collection<RoundRules.Period> periods) {
        if(periods.isEmpty()) return Map.of();
        var years=periods.stream().map(RoundRules.Period::academicYear).distinct().toList();
        var orders=periods.stream().map(RoundRules.Period::semesterOrder).distinct().toList();
        var result=new HashMap<String,ScholarshipSemester>();
        em.createQuery("from ScholarshipSemester where academicYear in :years and semesterOrder in :orders",ScholarshipSemester.class)
            .setParameter("years",years).setParameter("orders",orders).getResultList()
            .forEach(row->result.put(periodKey(row.getAcademicYear(),row.getSemesterOrder()),row));
        return result;
    }
    static String periodKey(String year,int order) { return year+":"+order; }

    void serializeWrites() {
        Integer result=jdbc.queryForObject("""
            DECLARE @result int;
            EXEC @result=sys.sp_getapplock @Resource=N'ctsv.hocbong.kkht.rounds',
                @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
            SELECT @result
            """,Integer.class);
        if(result==null || result<0) throw new RoundFailure(503,"DEPENDENCY_UNAVAILABLE");
    }
    Optional<ScholarshipSemester> semesterByPeriod(String year,int order) {
        return em.createQuery("from ScholarshipSemester where academicYear=:year and semesterOrder=:number",ScholarshipSemester.class)
            .setParameter("year",year).setParameter("number",order).getResultList().stream().findFirst();
    }
    Optional<SourceSemesterMapping> mappingBySource(long sourceId) {
        return em.createQuery("""
            from SourceSemesterMapping where sourceSystem='CTSV' and sourceKind='ctsv_hoc_ky_id'
            and sourceSemesterCode=:id and sourceRoundCode is null
            """,SourceSemesterMapping.class).setParameter("id",Long.toString(sourceId)).getResultList().stream().findFirst();
    }
    ScholarshipSemester semester(long id) { return required(ScholarshipSemester.class,id); }
    SourceSemesterMapping mapping(long id) { return required(SourceSemesterMapping.class,id); }
    ScholarshipRound round(long id) { return required(ScholarshipRound.class,id); }

    private <T> T required(Class<T> type,long id) {
        T value=em.find(type,id);
        if(value==null) throw new RoundFailure(404,"NOT_FOUND");
        return value;
    }

    byte[] lockRound(long id) {
        em.flush();
        List<byte[]> rows=jdbc.query("SELECT phien_ban_ky_thuat FROM dbo.kkht_dot_xet WITH(UPDLOCK,HOLDLOCK) WHERE id=?",
            (row,index)->row.getBytes(1),id);
        if(rows.isEmpty()) throw new RoundFailure(404,"NOT_FOUND");
        em.clear();
        return rows.getFirst();
    }
    boolean numberTaken(long semester,int number,Long excluded) {
        String hql="select count(r) from ScholarshipRound r where r.semesterId=:semester and r.number=:number";
        if(excluded!=null) hql+=" and r.id<>:excluded";
        var query=em.createQuery(hql,Long.class).setParameter("semester",semester).setParameter("number",number);
        if(excluded!=null) query.setParameter("excluded",excluded);
        return query.getSingleResult()>0;
    }
    void persist(Object entity) { em.persist(entity); em.flush(); }
    void persistAudit(ScholarshipAudit entity) { persist(entity); }

    void changeRound(long id,byte[] version,int number,String name) {
        em.flush();
        int changed=jdbc.update("UPDATE dbo.kkht_dot_xet SET so_dot=?,ten_dot=? WHERE id=? AND phien_ban_ky_thuat=?",number,name,id,version);
        if(changed!=1) throw new RoundFailure(412,"VERSION_CONFLICT");
        em.clear();
    }
    RoundDtos.Page<RoundDtos.RoundSummary> list(Long semester,Integer number,int[] page) {
        String from="from ScholarshipRound r, ScholarshipSemester s, SourceSemesterMapping m where r.semesterId=s.id and r.sourceMappingId=m.id";
        Map<String,Object> parameters=new HashMap<>();
        if(semester!=null) { from+=" and r.semesterId=:semester"; parameters.put("semester",semester); }
        if(number!=null) { from+=" and r.number=:number"; parameters.put("number",number); }
        var count=em.createQuery("select count(r) "+from,Long.class);
        var query=em.createQuery("select r,s,m "+from+" order by s.academicYear desc,s.semesterOrder desc,r.number,r.id",Object[].class);
        parameters.forEach((key,value)-> { count.setParameter(key,value); query.setParameter(key,value); });
        List<RoundDtos.RoundSummary> items=query.setFirstResult(page[0]*page[1]).setMaxResults(page[1]).getResultList().stream()
            .map(row->RoundMapper.summary((ScholarshipRound)row[0],(ScholarshipSemester)row[1],(SourceSemesterMapping)row[2])).toList();
        return new RoundDtos.Page<>(items,page[0],page[1],count.getSingleResult());
    }
    boolean semesterUsed(long sourceId) {
        return em.createQuery("""
            select count(m) from SourceSemesterMapping m where m.sourceSystem='CTSV'
            and m.sourceKind='ctsv_hoc_ky_id' and m.sourceSemesterCode=:id and m.sourceRoundCode is null
            and exists (select r.id from ScholarshipRound r where r.sourceMappingId=m.id)
            """,Long.class).setParameter("id",Long.toString(sourceId)).getSingleResult()>0;
    }
}
