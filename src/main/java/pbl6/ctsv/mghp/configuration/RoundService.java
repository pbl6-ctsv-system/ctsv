package pbl6.ctsv.mghp.configuration;
import com.fasterxml.jackson.databind.*;import java.time.*;import java.util.*;
final class RoundService {
 final ConfigurationRepository r;final ObjectMapper json;final Clock clock;
 RoundService(ConfigurationRepository r,ObjectMapper json,Clock clock){this.r=r;this.json=json;this.clock=clock;}
 PolicyService.View detail(long id,boolean manager){byte[] f=r.fence("mghp_dot_xet",id);var b=r.roundView(id,manager);return new PolicyService.View(b,manager?ConfigRules.tag("round:"+id+":manager",f,b,json):null);}
 Object list(boolean manager,Long semester,int[] page){if(semester!=null)semester(semester);return r.rounds(manager,semester,page);}
 Map<String,Object> semester(long id){return r.semester(id); }
 void references(JsonNode n,long actor){semester(ConfigRules.id(n.path("semesterId").asText()));var policyIds=new HashSet<String>();var ids=new TreeSet<Long>();for(var p:n.path("policyVersions"))ids.add(ConfigRules.id(p.path("policyVersionId").asText()));for(long id:ids){r.fence("mghp_phien_ban_chinh_sach",id);var pv=r.policyView(id);if(!policyIds.add(pv.get("policyId").toString()))throw ConfigFailure.invalid("policyVersions");}
  for(var p:n.path("policyVersions"))if(!p.path("applicationTemplateFileId").isNull())file(ConfigRules.id(p.path("applicationTemplateFileId").asText()),actor);
 }
 void file(long id,long actor){
  var file=r.file(id);
  boolean owned=Objects.equals(file.get("ownerId"),Long.toString(actor))||Objects.equals(file.get("uploaderId"),Long.toString(actor));
  // This method is reached only after online MGHP-manager authorization. Bound templates inherit round access (TEP02).
  boolean bound=r.boundTemplate(id);
  if(!owned&&!bound)throw new ConfigFailure(404,"NOT_FOUND");
  if(!"available".equals(file.get("state"))||file.get("deleted")!=null||!"application/pdf".equals(file.get("mime")))throw new ConfigFailure(409,"ROUND_NOT_READY");
 }
 PolicyService.View create(JsonNode n,long actor){RoundRules.validate(n);references(n,actor);if(r.roundCodeTaken(n.path("code").asText(),null))throw new ConfigFailure(409,"ROUND_CODE_CONFLICT");var v=ConfigurationRepository.values(n,ConfigurationRepository.ROUND);v.put("trang_thai","draft");v.put("nguoi_tao_id",actor);v.put("thoi_diem_tao",now());v.put("thoi_diem_cap_nhat",now());long id=r.insert("mghp_dot_xet",v);if(n.has("schedule")){r.find(pbl6.ctsv.mghp.configuration.entity.MghpDotXet.class,id).setLichTrinh(RoundTimelineRepository.entity(TimelineRules.parse(n.get("schedule"))));}r.roundChildren(id,n,false);r.audit(actor,"ROUND_CREATE","mghp_dot_xet",id,null);new RoundTimelineRepository(r).changed(id,actor,"CREATE","Tạo lịch của đợt",now());return detail(id,true);}
 PolicyService.View update(long id,JsonNode n,String tag,long actor){RoundRules.validate(n);var hint=r.roundView(id,true);var semesters=new TreeSet<Long>();semesters.add(ConfigRules.id(hint.get("semesterId").toString()));semesters.add(ConfigRules.id(n.path("semesterId").asText()));for(long s:semesters)semester(s);references(n,actor);var f=r.fence("mghp_dot_xet",id);var old=detail(id,true);ConfigRules.match(old.tag(),tag);var body=r.roundView(id,true);if(!"draft".equals(body.get("status"))||body.get("configLockedAt")!=null||r.hasApplications(id))throw new ConfigFailure(409,"ROUND_CONFIG_LOCKED");
  if(r.roundCodeTaken(n.path("code").asText(),id))throw new ConfigFailure(409,"ROUND_CODE_CONFLICT");var v=ConfigurationRepository.values(n,ConfigurationRepository.ROUND);v.put("thoi_diem_cap_nhat",now());new RoundTimelineRepository(r).baseline(id,actor,now());if(n.has("schedule"))v.putAll(RoundTimelineRepository.columns(TimelineRules.parse(n.get("schedule"))));r.cas("mghp_dot_xet",id,f,v);r.roundChildren(id,n,true);new RoundTimelineRepository(r).changed(id,actor,"DRAFT_UPDATE","Cập nhật cấu hình nháp",now());r.audit(actor,"ROUND_UPDATE","mghp_dot_xet",id,null);return detail(id,true);
 }
 PolicyService.View transition(long id,String action,String tag,String reason,long actor){var hint=r.roundView(id,true);var calendar=semester(ConfigRules.id(hint.get("semesterId").toString()));for(var p:(List<?>)hint.get("policyVersions")){var row=(Map<?,?>)p;r.fence("mghp_phien_ban_chinh_sach",ConfigRules.id(row.get("policyVersionId").toString()));}
  var f=r.fence("mghp_dot_xet",id);var old=detail(id,true);ConfigRules.match(old.tag(),tag);var b=r.roundView(id,true);String state=(String)b.get("status"),target=switch(action){case "open"->"open";case "close"->"closed";default->"completed";};if(target.equals(state))return old;
  var v=new LinkedHashMap<String,Object>();v.put("trang_thai",target);v.put("thoi_diem_cap_nhat",now());
  if(action.equals("open")){if(!"draft".equals(state))throw new ConfigFailure(409,"ROUND_STATE_CONFLICT");ready(b,calendar,actor);v.put("nguoi_khoa_cau_hinh_id",actor);v.put("thoi_diem_khoa_cau_hinh",now());}
  else if(action.equals("close")){if(!"open".equals(state))throw new ConfigFailure(409,"ROUND_STATE_CONFLICT");}
  else {// P12/P13 must supply publication/Portal/durable handoff evidence before new completion.
   throw new ConfigFailure(409,"ROUND_STATE_CONFLICT");}
  r.cas("mghp_dot_xet",id,f,v);
  if(action.equals("open")){var notice=new LinkedHashMap<String,Object>();notice.put("ma_phan_he","MGHP");notice.put("tieu_de",b.get("name"));notice.put("noi_dung",b.get("name"));notice.put("loai_doi_tuong","mghp_dot_xet");notice.put("doi_tuong_id",Long.toString(id));notice.put("trang_thai","published");notice.put("thoi_diem_cong_bo",now());notice.put("nguoi_tao_id",actor);notice.put("thoi_diem_tao",now());long notification=r.insert("thong_bao",notice);r.publish(notification,id,now());
  }
  r.audit(actor,"ROUND_"+action.toUpperCase(Locale.ROOT),"mghp_dot_xet",id,reason);return detail(id,true);
 }
 void ready(Map<String,Object> b,Map<String,Object> semester,long actor){boolean ok="verified".equals(semester.get("state"))&&semester.get("evidence")!=null&&semester.get("startDate")!=null&&semester.get("endDate")!=null&&Boolean.TRUE.equals(b.get("allowRetroactive"))&&"theo_phap_luat".equals(b.get("retroactiveMode"))&&"khoang_ngay_chuyen_vien_xac_nhan".equals(b.get("midSemesterMode"))&&b.get("opensAt")!=null&&b.get("submissionDeadline")!=null&&b.get("reviewDeadline")!=null;
  if(!ok)throw new ConfigFailure(409,"ROUND_NOT_READY");Instant start=Instant.parse(b.get("opensAt").toString()),end=Instant.parse(b.get("submissionDeadline").toString()),review=Instant.parse(b.get("reviewDeadline").toString());if(start.isAfter(end)||end.isAfter(review)||clock.instant().isAfter(end))throw new ConfigFailure(409,"ROUND_NOT_READY");var list=(List<?>)b.get("policyVersions");if(list.isEmpty())throw new ConfigFailure(409,"ROUND_NOT_READY");
  var from=LocalDate.parse(semester.get("startDate").toString());var until=LocalDate.parse(semester.get("endDate").toString());for(var x:list){var p=(Map<?,?>)x;var pv=r.policyView(ConfigRules.id(p.get("policyVersionId").toString()));if(!"locked".equals(pv.get("status"))||pv.get("lockedAt")==null||pv.get("ruleVerificationStatus")==null||pv.get("effectiveFrom")==null||LocalDate.parse(pv.get("effectiveFrom").toString()).isAfter(until)||pv.get("effectiveUntil")!=null&&LocalDate.parse(pv.get("effectiveUntil").toString()).isBefore(from))throw new ConfigFailure(409,"ROUND_NOT_READY");if(p.get("applicationTemplateFileId")!=null)file(ConfigRules.id(p.get("applicationTemplateFileId").toString()),actor);}
 }
 boolean pending(long id){return r.pending(id);}

 OffsetDateTime now(){return OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);}
}
