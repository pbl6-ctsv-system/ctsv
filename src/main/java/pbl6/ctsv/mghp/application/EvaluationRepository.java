package pbl6.ctsv.mghp.application;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import static pbl6.ctsv.mghp.application.EvaluationEntities.*;

@Repository
@ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class EvaluationRepository {
 @jakarta.persistence.PersistenceContext EntityManager em;
 final JdbcTemplate jdbc;
 EvaluationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 <T extends ApplicationEntities.Row> T save(T row){em.persist(row);return row;}
 void flush(){em.flush();}
 int update(String sql,Object... args){em.flush();return jdbc.update(sql,args);}
 void refresh(Object row){em.refresh(row);}
 List<BenefitEvent> events(long id){return em.createQuery("from P08BenefitEvent where benefitId=:id order by revision desc,id desc",BenefitEvent.class).setParameter("id",id).getResultList();}
 <T> List<T> children(Class<T> type,long id){return em.createQuery("from P08"+type.getSimpleName()+" where evaluationId=:id order by id",type).setParameter("id",id).getResultList();}
 Evaluation evaluation(long id){
  if(jdbc.queryForList("SELECT id FROM dbo.mghp_danh_gia_dieu_kien WITH(UPDLOCK,HOLDLOCK) WHERE id=?",Long.class,id).isEmpty())throw IntakeProblem.missing();
  return em.find(Evaluation.class,id);
 }
 Benefit benefit(long id){
  if(jdbc.queryForList("SELECT id FROM dbo.mghp_quyen_huong WITH(UPDLOCK,HOLDLOCK) WHERE id=?",Long.class,id).isEmpty())throw IntakeProblem.missing();
  return em.find(Benefit.class,id);
 }
 List<Evaluation> chain(Evaluation e){
  return em.createQuery("from P08Evaluation where "+(e.applicationId!=null?"applicationId=:source":"benefitId=:source")+
   " and semesterId=:semester and policyVersionId=:policy and scopeCode=:scope order by revision",Evaluation.class)
   .setParameter("source",e.applicationId!=null?e.applicationId:e.benefitId).setParameter("semester",e.semesterId)
   .setParameter("policy",e.policyVersionId).setParameter("scope",e.scopeCode).getResultList();
 }
 void touch(Evaluation e){
  em.flush();em.refresh(e);if(jdbc.update("UPDATE dbo.mghp_danh_gia_dieu_kien SET la_ban_hien_hanh=la_ban_hien_hanh WHERE id=? AND phien_ban_ky_thuat=?",e.id,e.fence)!=1)
   throw new IntakeProblem(412,"VERSION_CONFLICT","If-Match");em.refresh(e);
 }
 void audit(long user,String action,String type,long id,String reason){
  em.flush();jdbc.update("""
   INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,nguoi_dung_id,hanh_dong,loai_doi_tuong,doi_tuong_id,ly_do,thoi_diem_tao)
   VALUES(NEWID(),N'user',?,?,?,?,?,SYSDATETIMEOFFSET())
   """,user,action,type,Long.toString(id),reason);
 }
 void replaceChildren(Evaluation e){
  em.flush();
  for(String table:List.of("mghp_can_cu_danh_gia","mghp_ket_qua_tieu_chi","mghp_khoang_du_dieu_kien"))
   jdbc.update("DELETE FROM dbo."+table+" WHERE danh_gia_dieu_kien_id=?",e.id);
 }
}
