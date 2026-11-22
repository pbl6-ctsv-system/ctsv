package pbl6.ctsv.mghp.application;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import static pbl6.ctsv.mghp.application.DecisionEntities.*;

@Repository @ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class DecisionRepository {
 @jakarta.persistence.PersistenceContext EntityManager em;
 final JdbcTemplate jdbc;
 DecisionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 <T extends ApplicationEntities.Row> T save(T row){em.persist(row);em.flush();if(row instanceof Root||row instanceof Version||row instanceof Minutes)em.refresh(row);return row;}
 <T> T find(Class<T> type,long id){var row=em.find(type,id);if(row==null)throw IntakeProblem.missing();return row;}
 <T> List<T> children(Class<T> type,String field,long id){return em.createQuery("from P10"+type.getSimpleName()+" where "+field+"=:id order by id",type).setParameter("id",id).getResultList();}
 <T> List<T> pageChildren(Class<T> type,String field,long id,int page,int size){long offset=(long)page*size;if(offset>Integer.MAX_VALUE)return List.of();return em.createQuery("from P10"+type.getSimpleName()+" where "+field+"=:id order by id",type).setParameter("id",id).setFirstResult((int)offset).setMaxResults(size).getResultList();}
 long countChildren(Class<?> type,String field,long id){return em.createQuery("select count(c) from P10"+type.getSimpleName()+" c where "+field+"=:id",Long.class).setParameter("id",id).getSingleResult();}
 List<Version> versions(long root){return em.createQuery("from P10Version where rootId=:id order by revision desc",Version.class).setParameter("id",root).getResultList();}
 Root byRound(long round){return em.createQuery("from P10Root where roundId=:id",Root.class).setParameter("id",round).getResultStream().findFirst().orElse(null);}
 boolean codeExists(String code){return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_quyet_dinh WHERE ma_qd=?",Integer.class,code)>0;}
 boolean unitExists(long id){return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.don_vi WHERE id=?",Integer.class,id)==1;}
 boolean minutesCodeExists(String code){return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_bien_ban WHERE ma_bb=?",Integer.class,code)>0;}
 long countRoots(Long round){var q=em.createQuery("select count(r) from P10Root r"+(round==null?"":" where roundId=:round"),Long.class);if(round!=null)q.setParameter("round",round);return q.getSingleResult();}
 Root lockRoot(long id){if(jdbc.queryForList("SELECT id FROM dbo.mghp_quyet_dinh WITH(UPDLOCK,HOLDLOCK) WHERE id=?",Long.class,id).isEmpty())throw IntakeProblem.missing();return find(Root.class,id);}
 void touch(Root root){em.flush();if(jdbc.update("UPDATE dbo.mghp_quyet_dinh SET ma_qd=ma_qd WHERE id=? AND phien_ban_ky_thuat=?",root.id,root.fence)!=1)throw new IntakeProblem(412,"VERSION_CONFLICT","If-Match");em.refresh(root);}
 void refresh(Object v){em.flush();em.refresh(v);}
 void councilClosed(long round,long actor,java.time.OffsetDateTime now){
  var fence=jdbc.queryForObject("SELECT phien_ban FROM dbo.mghp_dot_xet WHERE id=?",byte[].class,round);
  if(jdbc.update("UPDATE dbo.mghp_dot_xet SET nguoi_chot_hoi_dong_id=?,thoi_diem_hoi_dong_chot=? WHERE id=? AND phien_ban=? AND thoi_diem_hoi_dong_chot IS NULL",actor,now,round,fence)!=1)
   throw IntakeProblem.conflict("DECISION_NOT_READY");
 }
 void lockVersion(Version v,long actor,java.time.OffsetDateTime now){
  em.flush();if(jdbc.update("UPDATE dbo.mghp_phien_ban_qd SET trang_thai=N'locked',nguoi_chot_id=?,thoi_diem_chot=? WHERE id=? AND phien_ban_ky_thuat=? AND thoi_diem_chot IS NULL",actor,now,v.id,v.fence)!=1)throw new IntakeProblem(412,"VERSION_CONFLICT","If-Match");em.refresh(v);
 }
 List<Root> roots(Long round,int page,int size){
  var q=em.createQuery("from P10Root"+(round==null?"":" where roundId=:round")+" order by id desc",Root.class);
  if(round!=null)q.setParameter("round",round);long offset=(long)page*size;if(offset>Integer.MAX_VALUE)return List.of();return q.setFirstResult((int)offset).setMaxResults(size).getResultList();
 }
 boolean published(long process,long semester,String scope){
  return jdbc.queryForObject("""
   SELECT COUNT(*) FROM dbo.mghp_dong_qd l JOIN dbo.mghp_cong_bo p ON p.phien_ban_qd_id=l.phien_ban_qd_id
   WHERE l.qua_trinh_hoc_id=? AND l.hoc_ky_ap_dung_id=? AND l.ma_pham_vi=?
   """,Integer.class,process,semester,scope)>0;
 }
}

