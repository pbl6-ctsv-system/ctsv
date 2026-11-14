package pbl6.ctsv.mghp.configuration;
import com.fasterxml.jackson.databind.*;import java.time.*;import java.util.*;
final class PolicyService {
 record View(Object body,String tag){}
 final ConfigurationRepository r;final ObjectMapper json;final Clock clock;
 PolicyService(ConfigurationRepository r,ObjectMapper json,Clock clock){this.r=r;this.json=json;this.clock=clock;}
 View detail(long id){var f=r.fence("mghp_phien_ban_chinh_sach",id);var b=r.policyView(id);return new View(b,ConfigRules.tag("policy:"+id+":manager",f,b,json));}
 Object list(){return r.policies();}
 View create(long policy,JsonNode n,long actor){PolicyRules.validate(n);r.policy(policy);int next=r.nextVersion(policy);var v=ConfigurationRepository.values(n,ConfigurationRepository.POLICY);v.put("chinh_sach_id",policy);v.put("phien_ban",next);v.put("trang_thai","draft");v.put("nguoi_tao_id",actor);v.put("thoi_diem_tao",OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));long id=r.insert("mghp_phien_ban_chinh_sach",v);r.children(id,n,false);r.audit(actor,"POLICY_CREATE","mghp_phien_ban_chinh_sach",id,null);return detail(id);}
 View update(long id,JsonNode n,String tag,long actor){var f=r.fence("mghp_phien_ban_chinh_sach",id);var old=detail(id);ConfigRules.match(old.tag(),tag);mutable(id);PolicyRules.validate(n);r.cas("mghp_phien_ban_chinh_sach",id,f,ConfigurationRepository.values(n,ConfigurationRepository.POLICY));r.children(id,n,true);r.audit(actor,"POLICY_UPDATE","mghp_phien_ban_chinh_sach",id,null);return detail(id);}
 void mutable(long id){var b=r.policyView(id);if(!"draft".equals(b.get("status"))||b.get("lockedAt")!=null||r.usedPolicy(id))throw new ConfigFailure(409,"POLICY_IMMUTABLE");}
 View confirm(long id,JsonNode n,String tag,long actor){ConfigRules.shape(n,"evidence");String evidence=ConfigRules.text(n,"evidence",16000,true);var f=r.fence("mghp_phien_ban_chinh_sach",id);var old=detail(id);ConfigRules.match(old.tag(),tag);var b=r.policyView(id);if("locked".equals(b.get("status"))){if(evidence.equals(r.lastConfirmation(id)))return old;throw new ConfigFailure(409,"POLICY_IMMUTABLE");}
  mutable(id);var content=json.createObjectNode();for(var k:PolicyRules.FIELDS.split(","))content.set(k,json.valueToTree(b.get(k)));for(var c:content.path("criteria"))((com.fasterxml.jackson.databind.node.ObjectNode)c).remove("id");for(var g:content.path("documentGroups")){((com.fasterxml.jackson.databind.node.ObjectNode)g).remove("id");for(var d:g.path("documents"))((com.fasterxml.jackson.databind.node.ObjectNode)d).remove("id");}
  String code=r.policyCode(id);PolicyRules.confirm(content,code);var v=new LinkedHashMap<String,Object>();v.put("trang_thai","locked");v.put("thoi_diem_khoa",OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC));v.put("tinh_trang_xac_nhan_quy_tac",code.equals("M9")?"quy_uoc_do_an":"da_doi_chieu_nguon");r.cas("mghp_phien_ban_chinh_sach",id,f,v);r.audit(actor,"POLICY_CONFIRM","mghp_phien_ban_chinh_sach",id,evidence);return detail(id);
 }
}
