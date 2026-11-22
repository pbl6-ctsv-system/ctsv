package pbl6.ctsv.mghp.application;

import java.util.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pbl6.ctsv.mghp.configuration.IntakeConfiguration;
import static pbl6.ctsv.mghp.application.EvaluationEntities.*;

/** Same-module source access. Review closure and all candidates are checked under the existing MGHP fence. */
@Component @ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class DecisionSource {
 record Ready(String sha,java.time.OffsetDateTime closedAt,List<DecisionRules.Source> sources,List<DecisionRules.Source> winners){}
 final IntakeRepository r;final EvaluationRepository evaluations;final EvaluationService evaluationService;
 final ReviewSourceAccess preflight;final IntakeConfiguration config;final DecisionRepository decisions;
 DecisionSource(IntakeRepository r,EvaluationRepository evaluations,EvaluationService evaluationService,ReviewSourceAccess preflight,IntakeConfiguration config,DecisionRepository decisions){
  this.r=r;this.evaluations=evaluations;this.evaluationService=evaluationService;this.preflight=preflight;this.config=config;this.decisions=decisions;
 }
 Ready ready(long roundId){
  var round=r.row("SELECT thoi_diem_dong_xet closedAt,sha256_nguon_dong_xet sourceHash FROM dbo.mghp_dot_xet WHERE id=?",roundId);
  if(round.get("closedAt")==null)throw IntakeProblem.conflict("DECISION_NOT_READY");
  var current=preflight.read(roundId);
  if(!current.blockers().isEmpty()||!Objects.equals(current.sha256(),round.get("sourceHash")))throw IntakeProblem.conflict("DECISION_NOT_READY");
  var sources=new ArrayList<DecisionRules.Source>();
  var ids=r.rows("""
   SELECT DISTINCT e.id FROM dbo.mghp_danh_gia_dieu_kien e
   JOIN dbo.mghp_phien_ban_ho_so v ON v.id=COALESCE(e.phien_ban_cap_nhat_minh_chung_id,e.phien_ban_ho_so_nguon_id)
   JOIN dbo.mghp_ho_so h ON h.id=v.ho_so_id
   JOIN dbo.mghp_hoc_ky_de_nghi req ON req.phien_ban_ho_so_id=v.id AND req.hoc_ky_id=e.hoc_ky_ap_dung_id AND req.phien_ban_chinh_sach_id=e.phien_ban_chinh_sach_id
   WHERE e.dot_xet_id=? AND COALESCE(v.dot_tiep_nhan_id,h.dot_xet_id)=?
   AND e.ket_qua=N'dat' AND e.trang_thai_xu_ly=N'confirmed' AND e.la_ban_hien_hanh=1
   AND h.trang_thai NOT IN(N'withdrawn',N'rejected')
   AND NOT EXISTS(SELECT 1 FROM dbo.mghp_phien_ban_ho_so n WHERE n.ho_so_id=v.ho_so_id
     AND COALESCE(n.dot_tiep_nhan_id,h.dot_xet_id)=COALESCE(v.dot_tiep_nhan_id,h.dot_xet_id)
     AND COALESCE(n.quyen_huong_cap_nhat_id,0)=COALESCE(v.quyen_huong_cap_nhat_id,0) AND n.id>v.id)
   ORDER BY e.id
   """,roundId,roundId);
  for(var row:ids){
   var e=r.find(Evaluation.class,((Number)row.get("id")).longValue());evaluationService.ready(e);
   var policy=config.policy(e.policyVersionId);
   var rate=new BigDecimal(policy.get("rate").toString());
   Integer priority=policy.get("tiePriority")==null?null:Integer.valueOf(policy.get("tiePriority").toString());
   for(var p:evaluations.children(Period.class,e.id))sources.add(new DecisionRules.Source(e.studentId,e.processId,e.semesterId,e.scopeCode,e.id,e.policyVersionId,p.id,rate,priority,p.from,p.to));
  }
  var winners=DecisionRules.winners(sources);
  if(winners.isEmpty())throw IntakeProblem.conflict("DECISION_NOT_READY");
  for(var s:winners)if(decisions.published(s.process(),s.semester(),s.scope()))throw IntakeProblem.conflict("DECISION_NOT_READY");
  return new Ready(current.sha256(),(java.time.OffsetDateTime)round.get("closedAt"),List.copyOf(sources),winners);
 }
}

