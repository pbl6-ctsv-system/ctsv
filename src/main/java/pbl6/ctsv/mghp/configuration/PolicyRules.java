package pbl6.ctsv.mghp.configuration;
import com.fasterxml.jackson.databind.*;import java.math.BigDecimal;import java.util.*;
final class PolicyRules {
 static final String FIELDS="name,rate,scopeCode,effectiveFrom,effectiveUntil,legalBasis,ruleVersion,benefitDuration,tiePriority,documentNumber,article,sourceUrl,evidenceUpdateMode,evidenceUpdateGuidance,minimumAge,maximumAge,ageReferenceRule,firstDegreeRequired,criteria,documentGroups";
 static final String CRITERION="code,name,assessmentGuidance,required,conditional,applicabilityGuidance,order";
 static final String GROUP="code,name,minimumRequired,order,documents";
 static final String DOCUMENT="code,name,type,hardCopyRequired,maxAgeDays,order,certifiedCopyRequired,stampRequired,confirmationContent,confirmingAuthority,reviewGuidance,evidenceSubject";
 static final String[] TYPES={"don_de_nghi","xac_nhan_nguoi_co_cong","giay_khai_sinh","xac_nhan_khuyet_tat","quyet_dinh_tro_cap_xa_hoi","xac_nhan_mo_coi","xac_nhan_ho_ngheo","xac_nhan_ho_can_ngheo","xac_nhan_cu_tru","xac_nhan_vung","quyet_dinh_tro_cap_tnld_bnn","don_xac_nhan_duong_chuc","don_xac_nhan_huu_tri"};
 static void validate(JsonNode n){
  ConfigRules.shape(n,FIELDS);String rate=ConfigRules.text(n,"rate",32,true);try{if(!rate.matches("[0-9]+(?:\\.[0-9]{1,9})?"))throw new IllegalArgumentException();var v=new BigDecimal(rate);if(v.compareTo(BigDecimal.ZERO)<0||v.compareTo(BigDecimal.ONE)>0||v.precision()-v.scale()>3)throw new IllegalArgumentException();}catch(Exception e){throw ConfigFailure.invalid("rate");}
  ConfigRules.text(n,"scopeCode",128,true);ConfigRules.allow(n,"scopeCode","hoc_phi_chuong_trinh_chinh");
  for(var k:new String[]{"name","legalBasis","ruleVersion","benefitDuration","documentNumber","article","sourceUrl","evidenceUpdateMode","evidenceUpdateGuidance","ageReferenceRule"})ConfigRules.text(n,k,k.equals("name")?512:k.equals("sourceUrl")?2048:k.equals("legalBasis")||k.equals("evidenceUpdateGuidance")?1048576:128,false);
  ConfigRules.allow(n,"evidenceUpdateMode","mot_lan_cap_nhat_khi_thay_doi","moi_hoc_ky","theo_don_vi_cong_tac");ConfigRules.allow(n,"ageReferenceRule","chuyen_vien_xac_nhan_khoang_ngay");
  for(var k:new String[]{"minimumAge","maximumAge"})ConfigRules.integer(n,k,true);ConfigRules.integer(n,"tiePriority",false);ConfigRules.bool(n,"firstDegreeRequired");
  if(!n.path("minimumAge").isNull()&&!n.path("maximumAge").isNull()&&n.path("minimumAge").asInt()>n.path("maximumAge").asInt())throw ConfigFailure.invalid("maximumAge");
  var from=ConfigRules.date(n,"effectiveFrom");var until=ConfigRules.date(n,"effectiveUntil");if(from!=null&&until!=null&&from.isAfter(until))throw ConfigFailure.invalid("effectiveUntil");
  unique(n.get("criteria"));for(var c:n.path("criteria")){ConfigRules.shape(c,CRITERION);ConfigRules.text(c,"code",128,true);ConfigRules.text(c,"name",512,false);ConfigRules.text(c,"assessmentGuidance",1048576,false);ConfigRules.text(c,"applicabilityGuidance",1048576,false);ConfigRules.bool(c,"required");ConfigRules.bool(c,"conditional");ConfigRules.integer(c,"order",false);}
  unique(n.get("documentGroups"));for(var g:n.path("documentGroups")){ConfigRules.shape(g,GROUP);ConfigRules.text(g,"code",128,true);ConfigRules.text(g,"name",512,false);ConfigRules.integer(g,"order",false);ConfigRules.integer(g,"minimumRequired",true);unique(g.get("documents"));if(g.path("minimumRequired").isNull()||g.path("minimumRequired").asInt()<1||g.path("minimumRequired").asInt()>g.path("documents").size())throw ConfigFailure.invalid("minimumRequired");
   for(var d:g.path("documents")){ConfigRules.shape(d,DOCUMENT);ConfigRules.text(d,"code",128,true);for(var k:new String[]{"name","confirmationContent","confirmingAuthority","reviewGuidance","type","evidenceSubject"})ConfigRules.text(d,k,k.equals("reviewGuidance")?1048576:k.equals("type")||k.equals("evidenceSubject")?128:512,false);for(var k:new String[]{"hardCopyRequired","certifiedCopyRequired","stampRequired"})ConfigRules.bool(d,k);ConfigRules.integer(d,"maxAgeDays",true);ConfigRules.integer(d,"order",false);ConfigRules.allow(d,"type",TYPES);ConfigRules.allow(d,"evidenceSubject","theo_yeu_cau_chinh_sach","sinh_vien","cha_hoac_me");}
  }
 }
 static void unique(JsonNode a){if(a==null||!a.isArray())throw ConfigFailure.invalid("array");var seen=new HashSet<String>();for(var n:a)if(!seen.add(n.path("code").asText().toLowerCase(Locale.ROOT)))throw ConfigFailure.invalid("code");}
 static void confirm(JsonNode n,String code){validate(n);boolean ready=filled(n,"name","legalBasis","ruleVersion","effectiveFrom","evidenceUpdateMode")&&!n.path("criteria").isEmpty()&&!n.path("documentGroups").isEmpty();
  for(var c:n.path("criteria"))ready&=filled(c,"name")&&c.path("required").isBoolean()&&c.path("conditional").isBoolean()&&(!c.path("conditional").asBoolean()||filled(c,"applicabilityGuidance"));
  var subjects=new HashSet<String>();for(var g:n.path("documentGroups")){ready&=filled(g,"name");for(var d:g.path("documents")){ready&=filled(d,"name","type","evidenceSubject")&&d.path("hardCopyRequired").isBoolean()&&d.path("certifiedCopyRequired").isBoolean()&&d.path("stampRequired").isBoolean();if(d.path("type").asText().equals("xac_nhan_cu_tru"))subjects.add(d.path("evidenceSubject").asText());}}
  String expected=switch(code){case "M4"->"moi_hoc_ky";case "M8"->"theo_don_vi_cong_tac";default->"mot_lan_cap_nhat_khi_thay_doi";};ready&=expected.equals(n.path("evidenceUpdateMode").asText());
  if(code.equals("M3"))ready&=filled(n,"ageReferenceRule","evidenceUpdateGuidance")&&n.path("firstDegreeRequired").isBoolean();
  if(code.equals("M6")){
   // Both residency subjects must be unavoidable under AND/minimumRequired semantics, not merely listed in an OR group.
   var mandatory=new HashSet<String>();for(var g:n.path("documentGroups"))for(var subject:Set.of("sinh_vien","cha_hoac_me")){int without=0;for(var d:g.path("documents"))if(!d.path("type").asText().equals("xac_nhan_cu_tru")||!d.path("evidenceSubject").asText().equals(subject))without++;if(g.path("minimumRequired").asInt()>without)mandatory.add(subject);}
   ready&=subjects.containsAll(Set.of("sinh_vien","cha_hoac_me"))&&mandatory.containsAll(Set.of("sinh_vien","cha_hoac_me"));
  }
  if(code.equals("M8"))ready&=filled(n,"evidenceUpdateGuidance");
  if(!ready)throw new ConfigFailure(409,"POLICY_NOT_READY");
 }
 static boolean filled(JsonNode n,String...keys){for(var k:keys)if(!n.path(k).isTextual()||n.path(k).asText().isBlank())return false;return true;}
}
