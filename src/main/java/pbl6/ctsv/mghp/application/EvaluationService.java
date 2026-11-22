package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import pbl6.ctsv.mghp.application.EvaluationEntities.Period;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pbl6.ctsv.foundation.api.FoundationAccess.Identity;
import pbl6.ctsv.mghp.configuration.IntakeConfiguration;
import static pbl6.ctsv.mghp.application.EvaluationEntities.*;
import static pbl6.ctsv.mghp.application.IntakeRules.*;
import static pbl6.ctsv.mghp.application.IntakeMapping.*;
import static pbl6.ctsv.mghp.application.IntakeProblem.*;

@Service @ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class EvaluationService {
 static final List<String> FIELDS=List.of("applicationId","submissionId","benefitId","updateSubmissionId","processId","semesterId","policyVersionId","scopeCode","result","reason","timeEvidence","ageReferenceDate","birthDateUsed","statusHistoryId","criteria","evidence","eligiblePeriods");
 private final EvaluationRepository store; private final IntakeRepository repo;
 private final IntakeConfiguration config; private final ObjectMapper json;private final Clock clock;
 EvaluationService(EvaluationRepository store,IntakeRepository repo,IntakeConfiguration config,ObjectMapper json,Clock clock){this.store=store;this.repo=repo;this.config=config;this.json=json;this.clock=clock;}
 ApplicationReads.Detail read(long id){return view(store.evaluation(id));}
 private Evaluation parse(JsonNode b){
  shape(b,FIELDS);var e=new Evaluation();
  e.applicationId=nullableId(b,"applicationId");e.submissionId=nullableId(b,"submissionId");
  e.benefitId=nullableId(b,"benefitId");e.updateSubmissionId=nullableId(b,"updateSubmissionId");
  if(e.applicationId!=null?(e.submissionId==null||e.benefitId!=null||e.updateSubmissionId!=null):(e.benefitId==null||e.submissionId!=null))throw invalid("source");
  e.processId=id(b.get("processId"),"processId");e.semesterId=id(b.get("semesterId"),"semesterId");e.policyVersionId=id(b.get("policyVersionId"),"policyVersionId");
  e.scopeCode=text(b.get("scopeCode"),"scopeCode",128,true);e.result=text(b.get("result"),"result",128,false);
  if(e.result!=null&&!EvaluationRules.RESULTS.contains(e.result))throw invalid("result");
  e.reason=text(b.get("reason"),"reason",16000,false);e.timeEvidence=text(b.get("timeEvidence"),"timeEvidence",16000,false);
  e.ageReferenceDate=date(b.get("ageReferenceDate"),"ageReferenceDate",false);e.birthDateUsed=date(b.get("birthDateUsed"),"birthDateUsed",false);
  e.statusHistoryId=nullableId(b,"statusHistoryId");return e;
 }
 private void source(Evaluation e){
  var policy=config.policy(e.policyVersionId);
  if(!Objects.equals(policy.get("scopeCode"),e.scopeCode))throw conflict("EVALUATION_SOURCE_CONFLICT");
  Long sourceSubmission;
  if(e.applicationId!=null){
   var root=repo.root(e.applicationId);var sub=repo.find(ApplicationEntities.Submission.class,e.submissionId);
   if(sub==null||!root.id.equals(sub.applicationId)||!root.processId.equals(e.processId)
    ||!Objects.equals(root.policyId.toString(),policy.get("policyId")))throw conflict("EVALUATION_SOURCE_CONFLICT");
   if(repo.rows("SELECT id FROM dbo.mghp_hoc_ky_de_nghi WHERE phien_ban_ho_so_id=? AND hoc_ky_id=? AND phien_ban_chinh_sach_id=?",sub.id,e.semesterId,e.policyVersionId).isEmpty())
    throw conflict("EVALUATION_SOURCE_CONFLICT");
   if("withdrawn".equals(root.status)||"rejected".equals(root.status))throw conflict("EVALUATION_SOURCE_CONFLICT");
   e.studentId=root.studentId;e.roundId=sub.roundId==null?root.roundId:sub.roundId;sourceSubmission=sub.id;
  }else{
   var benefit=store.benefit(e.benefitId);
   if(!benefit.processId.equals(e.processId)||!Objects.equals(benefit.policyId.toString(),policy.get("policyId"))||!benefit.scopeCode.equals(e.scopeCode))throw conflict("EVALUATION_SOURCE_CONFLICT");
   e.studentId=benefit.studentId;sourceSubmission=benefit.submissionId;
   if(e.updateSubmissionId!=null){
    var update=repo.find(ApplicationEntities.Submission.class,e.updateSubmissionId);
    if(update==null||!e.benefitId.equals(update.benefitId)||!repo.root(update.applicationId).processId.equals(e.processId)
     ||!Objects.equals(update.policyVersionId,e.policyVersionId)
     ||repo.rows("SELECT id FROM dbo.mghp_hoc_ky_de_nghi WHERE phien_ban_ho_so_id=? AND hoc_ky_id=? AND phien_ban_chinh_sach_id=?",update.id,e.semesterId,e.policyVersionId).isEmpty())throw conflict("EVALUATION_SOURCE_CONFLICT");
    sourceSubmission=update.id;e.roundId=update.roundId;
   }
  }
  if(e.roundId!=null)config.requireReviewOpen(e.roundId);
  var process=repo.row("SELECT sinh_vien_id student FROM dbo.qua_trinh_theo_hoc WHERE id=?",e.processId);
  if(!Objects.equals(process.get("student"),e.studentId))throw conflict("EVALUATION_SOURCE_CONFLICT");
  repo.row("SELECT id FROM dbo.hoc_ky WHERE id=?",e.semesterId);
  if(e.statusHistoryId!=null && repo.rows("SELECT id FROM dbo.lich_su_trang_thai_sv WHERE id=? AND qua_trinh_hoc_id=? AND hoc_ky_ap_dung_id=?",e.statusHistoryId,e.processId,e.semesterId).isEmpty())throw conflict("EVALUATION_SOURCE_CONFLICT");
  e.observedAt=(OffsetDateTime)repo.student(e.studentId).get("observedAt");
  e.ruleVersion=(String)policy.get("ruleVersion");
 }
 private void lockChain(Evaluation e){repo.lockOperation(UUID.nameUUIDFromBytes(("evaluation:"+(e.applicationId!=null?"app:"+e.applicationId:"benefit:"+e.benefitId)+":"+e.semesterId+":"+e.policyVersionId+":"+e.scopeCode).getBytes(StandardCharsets.UTF_8)));}
 ApplicationReads.Detail create(Identity actor,JsonNode body){
  config.lockWrites();var e=parse(body);source(e);lockChain(e);
  if(!store.chain(e).isEmpty())throw conflict("EVALUATION_SOURCE_CONFLICT");
  e.revision=1;e.status="draft";e.current=false;store.save(e);children(actor,e,body);
  store.audit(actor.userId(),"create_evaluation","evaluation",e.id,e.reason);store.touch(e);touchParent(e);return view(e);
 }
 ApplicationReads.Detail update(Identity actor,long id,JsonNode body,String tag){
  config.lockWrites();var incoming=parse(body);config.lockWrites();var e=locked(id);ApplicationReads.matchTag(view(e).tag(),tag);
  if(!"draft".equals(e.status))throw conflict("EVALUATION_IMMUTABLE");
  if(!sameSource(e,incoming))throw conflict("EVALUATION_SOURCE_CONFLICT");source(incoming);
  e.submissionId=incoming.submissionId;e.updateSubmissionId=incoming.updateSubmissionId;
  e.result=incoming.result;e.reason=incoming.reason;e.timeEvidence=incoming.timeEvidence;e.ageReferenceDate=incoming.ageReferenceDate;
  e.birthDateUsed=incoming.birthDateUsed;e.statusHistoryId=incoming.statusHistoryId;e.observedAt=incoming.observedAt;
  store.replaceChildren(e);children(actor,e,body);store.audit(actor.userId(),"update_evaluation","evaluation",e.id,e.reason);store.touch(e);touchParent(e);return view(e);
 }
 private boolean sameSource(Evaluation a,Evaluation b){return Objects.equals(a.applicationId,b.applicationId)&&Objects.equals(a.benefitId,b.benefitId)&&a.processId.equals(b.processId)&&a.semesterId.equals(b.semesterId)&&a.policyVersionId.equals(b.policyVersionId)&&a.scopeCode.equals(b.scopeCode);}
 private Evaluation locked(long id){
  var fields=repo.row("SELECT ho_so_nguon_id app,quyen_huong_nguon_id benefit FROM dbo.mghp_danh_gia_dieu_kien WHERE id=?",id);
  if(fields.get("app") instanceof Long root){if("rejected".equals(repo.root(root).status))throw conflict("EVALUATION_SOURCE_CONFLICT");}else store.benefit((Long)fields.get("benefit"));
  var e=store.evaluation(id);if(e.roundId!=null)config.requireReviewOpen(e.roundId);lockChain(e);return e;
 }
 private void touchParent(Evaluation e){
  if(e.applicationId!=null){var root=repo.root(e.applicationId);repo.change(root,root.status,clock.instant().atOffset(ZoneOffset.UTC),null);}
  else store.update("UPDATE dbo.mghp_quyen_huong SET thoi_diem_cap_nhat=SYSDATETIMEOFFSET() WHERE id=?",e.benefitId);
 }
 private void children(Identity actor,Evaluation e,JsonNode b){
  var now=clock.instant().atOffset(ZoneOffset.UTC);var ids=new HashMap<Long,Long>();
  for(var c:array(b.get("criteria"),"criteria",false)){
   shape(c,List.of("criterionId","result","reasonCode","note"));var r=new Criterion();r.evaluationId=e.id;r.criterionId=id(c.get("criterionId"),"criterionId");
   if(ids.containsKey(r.criterionId)||repo.rows("SELECT id FROM dbo.mghp_tieu_chi_chinh_sach WHERE id=? AND phien_ban_chinh_sach_id=?",r.criterionId,e.policyVersionId).isEmpty())throw invalid("criterionId");
   r.result=choice(c,"result",EvaluationRules.CRITERIA);r.reasonCode=text(c.get("reasonCode"),"reasonCode",2000,false);r.note=text(c.get("note"),"note",4000,false);
   r.actor=actor.userId();r.at=now;store.save(r);ids.put(r.criterionId,r.id);
  }
  Long sub=e.submissionId!=null?e.submissionId:e.updateSubmissionId!=null?e.updateSubmissionId:store.benefit(e.benefitId).submissionId;
  var used=new HashSet<String>();
  for(var item:array(b.get("evidence"),"evidence",false)){
   shape(item,List.of("documentId","criterionId","from","to","result","reason"));var r=new Evidence();r.evaluationId=e.id;r.documentId=id(item.get("documentId"),"documentId");
   Long criterion=nullableId(item,"criterionId");if(criterion!=null&&!ids.containsKey(criterion))throw invalid("criterionId");r.criterionResultId=ids.get(criterion);
   if(!used.add(r.documentId+":"+criterion))throw invalid("evidence");
   if(sub==null||repo.rows("SELECT d.id FROM dbo.mghp_giay_to_ho_so d JOIN dbo.mghp_nhom_yeu_cau_giay_to g ON g.id=(SELECT nhom_id FROM dbo.mghp_giay_to_bat_buoc WHERE id=d.giay_to_bat_buoc_id) WHERE d.id=? AND d.phien_ban_ho_so_id=? AND g.phien_ban_chinh_sach_id=?",r.documentId,sub,e.policyVersionId).isEmpty())throw conflict("EVALUATION_SOURCE_CONFLICT");
   r.from=date(item.get("from"),"from",false);r.to=date(item.get("to"),"to",false);if(r.from!=null&&r.to!=null&&r.from.isAfter(r.to))throw invalid("evidence");
   r.result=choice(item,"result",EvaluationRules.EVIDENCE);r.reason=text(item.get("reason"),"reason",4000,false);
   if(!"dat".equals(r.result)&&(r.reason==null||r.reason.isBlank()))throw invalid("reason");r.actor=actor.userId();r.at=now;store.save(r);
  }
  var intervals=new ArrayList<EvaluationRules.Interval>();
  for(var item:array(b.get("eligiblePeriods"),"eligiblePeriods",false)){
   shape(item,List.of("from","to","evidence"));var r=new Period();r.evaluationId=e.id;r.from=date(item.get("from"),"from",true);r.to=date(item.get("to"),"to",true);
   r.evidence=text(item.get("evidence"),"evidence",16000,true);intervals.add(new EvaluationRules.Interval(r.from,r.to));r.actor=actor.userId();r.at=now;store.save(r);
  }
  if(!intervals.isEmpty())EvaluationRules.periods(intervals,LocalDate.of(1,1,1),LocalDate.of(9999,12,31));store.flush();
 }
 static String choice(JsonNode b,String key,Set<String> allowed){String v=text(b.get(key),key,128,true);if(!allowed.contains(v))throw invalid(key);return v;}
 ApplicationReads.Detail confirm(Identity actor,long id,String reason,String tag){
  var e=locked(id);ApplicationReads.matchTag(view(e).tag(),tag);
  if(!"draft".equals(e.status))throw conflict("EVALUATION_IMMUTABLE");
  source(e);ready(e);var chain=store.chain(e);
  if(!chain.getLast().id.equals(e.id))throw conflict("EVALUATION_CHAIN_CONFLICT");
  for(var old:chain)if(Boolean.TRUE.equals(old.current)){
   store.update("UPDATE dbo.mghp_danh_gia_dieu_kien SET la_ban_hien_hanh=0 WHERE id=?",old.id);store.refresh(old);
  }
  e.status="confirmed";e.current=true;e.assessedBy=actor.userId();e.assessedAt=clock.instant().atOffset(ZoneOffset.UTC);
  store.audit(actor.userId(),"confirm_evaluation","evaluation",e.id,reason);store.touch(e);touchParent(e);return view(e);
 }
 ApplicationReads.Detail revision(Identity actor,long id,String reason,String tag){
  config.lockWrites();var old=locked(id);ApplicationReads.matchTag(view(old).tag(),tag);var chain=store.chain(old);
  if(!chain.getLast().id.equals(id)||!"confirmed".equals(old.status)||!Boolean.TRUE.equals(old.current))throw conflict("EVALUATION_CHAIN_CONFLICT");
  var wire=json.valueToTree(view(old).body());var body=json.createObjectNode();FIELDS.forEach(key->body.set(key,wire.get(key)));
  var next=parse(body);source(next);next.previousId=old.id;next.revision=Math.addExact(old.revision,1);next.revisionReason=reason;
  next.status="draft";next.current=false;store.save(next);children(actor,next,body);
  store.audit(actor.userId(),"revise_evaluation","evaluation",next.id,reason);store.touch(next);touchParent(next);return view(next);
 }
 // Same-module preflight reuses the confirmed assessment rules; it never changes the assessment.
 void ready(Evaluation e){
  if(e.result==null||e.reason==null||e.reason.isBlank())throw conflict("EVALUATION_NOT_READY");
  var policy=config.policy(e.policyVersionId);var criteria=store.children(Criterion.class,e.id);
  boolean failed=false,unknown=false;
  for(var c:IntakeConfiguration.list(policy,"criteria")){
   var r=criteria.stream().filter(x->x.criterionId.toString().equals(c.get("id"))).findFirst().orElse(null);
   boolean required=Boolean.TRUE.equals(c.get("required")),conditional=Boolean.TRUE.equals(c.get("conditional"));
   if(r==null){if(required)throw conflict("EVALUATION_NOT_READY");continue;}
   if(!EvaluationRules.criterionReady(r.result,r.note,required,conditional))throw conflict("EVALUATION_NOT_READY");
   if(required){failed|="khong_dat".equals(r.result);unknown|="can_xac_minh".equals(r.result)||"chua_xet".equals(r.result);}
  }
  boolean requiredUnresolved=unknown;
  var evidence=store.children(Evidence.class,e.id);var periods=store.children(Period.class,e.id);
  unknown|=evidence.stream().anyMatch(x->"can_xac_minh".equals(x.result));
  for(var group:IntakeConfiguration.list(policy,"documentGroups")){
   int possible=0;
   for(var requirement:IntakeConfiguration.list(group,"documents")){
    var uses=evidence.stream().filter(x->repo.find(ApplicationEntities.Document.class,x.documentId).requirementId.toString().equals(requirement.get("id"))).toList();
    if(uses.isEmpty()||uses.stream().anyMatch(x->!"khong_dat".equals(x.result)))possible++;
   }
   failed|=possible<((Number)group.get("minimumRequired")).intValue();
  }
  if(!"dat".equals(e.result)){
   if(!periods.isEmpty()||evidence.isEmpty()||"khong_dat".equals(e.result)&&!failed||"can_xac_minh".equals(e.result)&&!unknown)throw conflict("EVALUATION_NOT_READY");
   return;
  }
  if(failed||requiredUnresolved||periods.isEmpty()||e.timeEvidence==null||e.timeEvidence.isBlank()||evidence.isEmpty()
    ||!EvaluationRules.ageReady(config.policyCode(e.policyVersionId),e.birthDateUsed,e.ageReferenceDate))throw conflict("EVALUATION_NOT_READY");
  var term=repo.row("SELECT ngay_bat_dau start,ngay_ket_thuc finish,trang_thai_xac_minh_moc state FROM dbo.hoc_ky WHERE id=?",e.semesterId);
  var process=repo.row("SELECT trang_thai_doi_chieu state,thoi_diem_bat_dau start,ngay_ket_thuc_thuc_te finish FROM dbo.qua_trinh_theo_hoc WHERE id=?",e.processId);
  String code=config.policyCode(e.policyVersionId);
  boolean policyVerified="da_doi_chieu_nguon".equals(policy.get("ruleVerificationStatus"))||"M9".equals(code)&&"quy_uoc_do_an".equals(policy.get("ruleVerificationStatus"));
  if(!"verified".equals(term.get("state"))||!"verified".equals(process.get("state"))||!policyVerified)throw conflict("EVALUATION_NOT_READY");
  if(e.benefitId!=null){
   var benefit=store.benefit(e.benefitId);
   if("M8".equals(code)&&(!"verified".equals(benefit.cycleStatus)||benefit.employmentUnitGroup==null||"unverified".equals(benefit.employmentUnitGroup)))throw conflict("EVALUATION_NOT_READY");
   boolean updateRequired="M4".equals(code)||"moi_hoc_ky".equals(benefit.updateMode)||"M8".equals(code)&&"udn_member".equals(benefit.employmentUnitGroup);
   if(updateRequired&&(e.updateSubmissionId==null||repo.rows("SELECT id FROM dbo.mghp_hoc_ky_de_nghi WHERE phien_ban_ho_so_id=? AND hoc_ky_id=?",e.updateSubmissionId,e.semesterId).isEmpty()))throw conflict("EVALUATION_NOT_READY");
  }
  LocalDate from=(LocalDate)term.get("start"),to=(LocalDate)term.get("finish");
  if(from==null||to==null||process.get("start")==null)throw conflict("EVALUATION_NOT_READY");
  LocalDate policyFrom=policy.get("effectiveFrom")==null?null:LocalDate.parse(policy.get("effectiveFrom").toString());
  LocalDate policyTo=policy.get("effectiveUntil")==null?null:LocalDate.parse(policy.get("effectiveUntil").toString());
  if(policyFrom==null)throw conflict("EVALUATION_NOT_READY");
  from=Collections.max(List.of(from,(LocalDate)process.get("start"),policyFrom));
  if(policyTo!=null&&policyTo.isBefore(to))to=policyTo;if(process.get("finish") instanceof LocalDate end&&end.isBefore(to))to=end;
  EvaluationRules.periods(periods.stream().map(p->new EvaluationRules.Interval(p.from,p.to)).toList(),from,to);
  // A pass uses explicitly accepted evidence for each required group, for every claimed interval.
  for(var period:periods)for(var group:IntakeConfiguration.list(policy,"documentGroups")){
   long count=0;
   for(var requirement:IntakeConfiguration.list(group,"documents")){
    boolean accepted=false;
    for(var use:evidence){
     if(!"dat".equals(use.result)||use.from==null||use.to==null||use.from.isAfter(period.from)||use.to.isBefore(period.to))continue;
     var doc=repo.find(ApplicationEntities.Document.class,use.documentId);
     if(!doc.requirementId.toString().equals(requirement.get("id")))continue;
     if(doc.expiresOn!=null&&doc.expiresOn.isBefore(period.to))continue;
     var checks=repo.children(ApplicationEntities.DocumentCheck.class,"documentId",doc.id,"id desc");
     boolean checked=true;
     for(String kind:List.of("form","content","validity","subject")){
      var latest=checks.stream().filter(c->kind.equals(c.kind)).findFirst().orElse(null);
      if(latest==null||!Set.of("pass","not_applicable").contains(latest.result))checked=false;
     }
     if(Boolean.TRUE.equals(requirement.get("hardCopyRequired"))&&!checks.stream().filter(c->"hard_copy".equals(c.kind)).findFirst().map(c->"received".equals(c.result)).orElse(false))checked=false;
     accepted|=checked;
    }
    if(accepted)count++;
   }
   if(count<((Number)group.get("minimumRequired")).intValue())throw conflict("EVALUATION_NOT_READY");
  }
 }
 ApplicationReads.Detail view(Evaluation e){
  store.flush();var chain=store.chain(e);var head=chain.getLast();
  var body=map("id",e.id,"revision",e.revision,"status","confirmed".equals(e.status)&&!Boolean.TRUE.equals(e.current)?"superseded":e.status,
   "result",e.result,"applicationId",e.applicationId,"submissionId",e.submissionId,"benefitId",e.benefitId,"updateSubmissionId",e.updateSubmissionId,
   "processId",e.processId,"semesterId",e.semesterId,"policyVersionId",e.policyVersionId,"scopeCode",e.scopeCode,"reason",e.reason,"timeEvidence",e.timeEvidence,
   "ageReferenceDate",e.ageReferenceDate,"birthDateUsed",e.birthDateUsed,"statusHistoryId",e.statusHistoryId,"previousEvaluationId",e.previousId,"chainHeadId",head.id,
   "assessedByUserId",e.assessedBy,"assessedAt",e.assessedAt,"sourceObservedAt",e.observedAt);
  var criteria=store.children(Criterion.class,e.id);var lookup=new HashMap<Long,Long>();criteria.forEach(c->lookup.put(c.id,c.criterionId));
  body.put("criteria",criteria.stream().map(c->map("criterionId",c.criterionId,"result",c.result,"reasonCode",c.reasonCode,"note",c.note)).toList());
  body.put("evidence",store.children(Evidence.class,e.id).stream().map(r->map("documentId",r.documentId,"criterionId",lookup.get(r.criterionResultId),"from",r.from,"to",r.to,"result",r.result,"reason",r.reason)).toList());
  body.put("eligiblePeriods",store.children(Period.class,e.id).stream().map(p->map("from",p.from,"to",p.to,"evidence",p.evidence)).toList());
  return new ApplicationReads.Detail(body,tag("evaluation:"+e.id,e.fence,body));
 }
 String tag(String identity,byte[] fence,Object body){try{var d=MessageDigest.getInstance("SHA-256");d.update(identity.getBytes(StandardCharsets.UTF_8));d.update(fence);return "\""+Base64.getUrlEncoder().withoutPadding().encodeToString(d.digest(json.writeValueAsBytes(body)))+"\"";}catch(Exception ex){throw new IllegalStateException(ex);}}
}
