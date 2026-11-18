package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
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
class BenefitService {
 private final EvaluationRepository store;private final EvaluationService evaluations;private final IntakeRepository repo;
 private final SupplementService supplements;private final ApplicationReads reads;private final IntakeConfiguration config;
 BenefitService(EvaluationRepository store,EvaluationService evaluations,IntakeRepository repo,SupplementService supplements,ApplicationReads reads,IntakeConfiguration config){this.store=store;this.evaluations=evaluations;this.repo=repo;this.supplements=supplements;this.reads=reads;this.config=config;}
 ApplicationReads.Detail read(Identity actor,boolean staff,long id){
  var b=store.benefit(id);if(!staff&&!Objects.equals(actor.studentId(),b.studentId))throw missing();return view(b,staff);
 }
 private ApplicationReads.Detail view(Benefit b,boolean staff){
  store.flush();var events=store.events(b.id);
  var result=map("id",b.id,"code",b.code,"processId",b.processId,"policyId",b.policyId,"scopeCode",b.scopeCode,"status",b.status,"nextReviewOn",b.nextReviewOn,
   "rate",b.rate==null?null:b.rate.toPlainString(),"recognizedFrom",b.from,"recognizedUntil",b.to,"revokedOn",b.revokedOn);
  result.put("events",events.stream().map(e->{var row=map("id",e.id,"revision",e.revision,"type",e.type,"effectiveOn",e.effectiveOn);if(staff){row.put("reason",e.reason);row.put("decisionLineId",wire(e.decisionLineId));}return row;}).toList());
  return new ApplicationReads.Detail(result,staff?evaluations.tag("benefit:"+b.id,b.fence,result):null);
 }
 Object review(long semester,String policyCode,int page,int size){
  if(policyCode!=null&&!policyCode.matches("M[1-9]|UNMAPPED"))throw invalid("policyCode");
  var term=repo.row("SELECT ngay_bat_dau start,ngay_ket_thuc finish,trang_thai_xac_minh_moc state FROM dbo.hoc_ky WHERE id=?",semester);
  var all=repo.rows("""
   SELECT b.id,b.ma_quyen_huong code,b.qua_trinh_hoc_id processId,b.chinh_sach_id policyId,b.thoi_diem_ra_soat_tiep nextReviewOn,b.trang_thai status,
    b.ly_do_can_ra_soat reason,b.sinh_vien_id studentId,s.mssv studentCode,s.ho_ten fullName,p.ma policyCode,
    b.ngay_cong_nhan_tu start,b.ngay_cong_nhan_den finish,b.ngay_hieu_luc_thu_hoi revoked,
    b.tinh_trang_xac_nhan_chu_ky cycle,b.che_do_cap_nhat_minh_chung mode,b.nhom_don_vi_cong_tac unitGroup,b.thoi_diem_cap_nhat observedAt
   FROM dbo.mghp_quyen_huong b JOIN dbo.sinh_vien s ON s.id=b.sinh_vien_id LEFT JOIN dbo.mghp_chinh_sach p ON p.id=b.chinh_sach_id
   ORDER BY b.thoi_diem_ra_soat_tiep,b.id
   """);
  var result=new ArrayList<Map<String,Object>>();
  for(var row:all){
   String code=(String)row.get("policyCode");if(code==null||!code.matches("M[1-9]"))code="UNMAPPED";
   if(policyCode!=null&&!policyCode.equals(code))continue;
   LocalDate start=(LocalDate)term.get("start"),end=(LocalDate)term.get("finish");
   if(start!=null&&(row.get("finish") instanceof LocalDate finish&&finish.isBefore(start)||row.get("revoked") instanceof LocalDate revoked&&revoked.isBefore(start)))continue;
   if(end!=null&&row.get("start") instanceof LocalDate begin&&begin.isAfter(end))continue;
   boolean incomplete=!"verified".equals(term.get("state"))||start==null||end==null||!"verified".equals(row.get("cycle"))||row.get("nextReviewOn")==null||row.get("mode")==null
    ||("M8".equals(code)||"theo_don_vi_cong_tac".equals(row.get("mode")))&&(row.get("unitGroup")==null||"unverified".equals(row.get("unitGroup")));
   boolean activeUpdate=!repo.rows("SELECT id FROM dbo.mghp_yeu_cau_bo_sung WHERE quyen_huong_id=? AND trang_thai IN ('open','responded','expired')",row.get("id")).isEmpty();
   boolean updated=!repo.rows("SELECT v.id FROM dbo.mghp_phien_ban_ho_so v JOIN dbo.mghp_hoc_ky_de_nghi k ON k.phien_ban_ho_so_id=v.id WHERE v.quyen_huong_cap_nhat_id=? AND k.hoc_ky_id=?",row.get("id"),semester).isEmpty();
   var definitiveRows=repo.rows("SELECT thoi_diem_danh_gia assessedAt FROM dbo.mghp_danh_gia_dieu_kien WHERE quyen_huong_nguon_id=? AND hoc_ky_ap_dung_id=? AND la_ban_hien_hanh=1 AND trang_thai_xu_ly='confirmed' AND ket_qua IN ('dat','khong_dat')",row.get("id"),semester);
   var observations=repo.rows("SELECT MAX(thoi_diem_ghi_nhan) observedAt FROM dbo.mghp_bien_dong_quyen_huong WHERE quyen_huong_id=? AND loai_su_kien='review_observation'",row.get("id"));
   var latestObservation=observations.getFirst().get("observedAt");
   boolean changed=row.get("reason") instanceof String reason&&!reason.isBlank()&&(definitiveRows.isEmpty()||latestObservation instanceof OffsetDateTime observed&&definitiveRows.getFirst().get("assessedAt") instanceof OffsetDateTime assessed&&observed.isAfter(assessed));
   boolean uncertain=!repo.rows("SELECT id FROM dbo.mghp_danh_gia_dieu_kien WHERE quyen_huong_nguon_id=? AND hoc_ky_ap_dung_id=? AND la_ban_hien_hanh=1 AND trang_thai_xu_ly='confirmed' AND ket_qua='can_xac_minh'",row.get("id"),semester).isEmpty();
   boolean due=incomplete||activeUpdate||changed||uncertain||Set.of("M4","M9").contains(code)||"moi_hoc_ky".equals(row.get("mode"))&&!updated
    ||end!=null&&row.get("nextReviewOn") instanceof LocalDate date&&!date.isAfter(end);
   boolean definitive=!definitiveRows.isEmpty();
   if(!due||definitive&&!incomplete&&!activeUpdate&&!changed)continue;
   var item=new LinkedHashMap<String,Object>();for(String key:List.of("id","code","processId","policyId","nextReviewOn","status","studentId","studentCode","fullName"))item.put(key,wire(row.get(key)));
   item.put("reviewReason",incomplete?"Cần xác minh mốc kỳ, chu kỳ hoặc ngày rà soát.":changed?row.get("reason"):"Cần rà soát điều kiện và minh chứng cho học kỳ.");result.add(item);
  }
  long skip=(long)page*size;
  return map("items",result.stream().skip(skip).limit(size).toList(),"page",page,"size",size,"totalElements",result.size());
 }
 ApplicationReads.Detail observe(Identity actor,long id,JsonNode body,String tag){
  shape(body,List.of("nextReviewOn","reviewReason","cycleVerificationStatus","updateMode","employmentUnitGroup"));
  var b=store.benefit(id);ApplicationReads.matchTag(view(b,true).tag(),tag);
  String status=EvaluationService.choice(body,"cycleVerificationStatus",Set.of("unverified","verified"));
  String mode=text(body.get("updateMode"),"updateMode",128,false),group=text(body.get("employmentUnitGroup"),"employmentUnitGroup",128,false);
  String reason=text(body.get("reviewReason"),"reviewReason",4000,true);
  if(mode!=null&&!Set.of("mot_lan_cap_nhat_khi_thay_doi","moi_hoc_ky","theo_don_vi_cong_tac").contains(mode))throw invalid("updateMode");
  if(group!=null&&!Set.of("dut","udn_member","other_verified","unverified").contains(group))throw invalid("employmentUnitGroup");
  var policy=(String)repo.row("SELECT ma FROM dbo.mghp_chinh_sach WHERE id=?",b.policyId).get("ma");
  if("verified".equals(status)&&(mode==null||("theo_don_vi_cong_tac".equals(mode)||"M8".equals(policy))&&(group==null||"unverified".equals(group))))throw invalid("cycleVerificationStatus");
  b.nextReviewOn=date(body.get("nextReviewOn"),"nextReviewOn",false);b.reviewReason=reason;b.cycleStatus=status;b.updateMode=mode;b.employmentUnitGroup=group;b.updatedAt=OffsetDateTime.now(ZoneOffset.UTC);
  var former=store.events(id);
  var event=new BenefitEvent();event.benefitId=b.id;event.type="review_observation";event.reason=reason;event.actor=actor.userId();event.at=b.updatedAt;
  event.revision=former.isEmpty()?1:Math.addExact(former.getFirst().revision,1);event.previousId=former.isEmpty()?null:former.getFirst().id;event.nextReviewOn=b.nextReviewOn;event.updateMode=mode;
  store.save(event);store.audit(actor.userId(),"observe_benefit","benefit",id,reason);store.refresh(b);return view(b,true);
 }
 ApplicationReads.Detail requestUpdate(Identity actor,long id,JsonNode input,String tag){
  shape(input,List.of("sourceVersionId","receivingRoundId","benefitId","updateType","reason","deadline","items"));
  // Resolve identity without a lock, then keep P07 order: receiving round -> application -> benefit.
  Long sub=(Long)repo.row("SELECT phien_ban_ho_so_nguon_id id FROM dbo.mghp_quyen_huong WHERE id=?",id).get("id");
  if(sub==null)throw conflict("BENEFIT_UPDATE_CONFLICT");
  long receiving=id(input.path("receivingRoundId"),"receivingRoundId");config.lock(receiving);
  var source=repo.find(ApplicationEntities.Submission.class,sub);if(source==null)throw conflict("BENEFIT_UPDATE_CONFLICT");
  var root=repo.root(source.applicationId);var b=store.benefit(id);ApplicationReads.matchTag(view(b,true).tag(),tag);
  if(!Objects.equals(nullableId(input,"benefitId"),id)||!root.studentId.equals(b.studentId)||!root.processId.equals(b.processId)||!root.policyId.equals(b.policyId))throw conflict("BENEFIT_UPDATE_CONFLICT");
  var created=supplements.create(actor,root.id,input,reads.detail(root).tag(),Instant.now());
  store.audit(actor.userId(),"request_benefit_update","benefit",id,"Yêu cầu cập nhật có nguồn quyền hưởng");
  b.updatedAt=OffsetDateTime.now(ZoneOffset.UTC);store.flush();return created;
 }
}
