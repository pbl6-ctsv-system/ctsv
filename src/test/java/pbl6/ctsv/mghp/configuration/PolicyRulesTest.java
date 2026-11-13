package pbl6.ctsv.mghp.configuration;
import com.fasterxml.jackson.databind.*;import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;import org.junit.jupiter.params.ParameterizedTest;import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
class PolicyRulesTest {
 static final ObjectMapper JSON=new ObjectMapper();
 public static ObjectNode draft(){
  var n=JSON.createObjectNode();for(var f:("name,rate,scopeCode,effectiveFrom,effectiveUntil,legalBasis,ruleVersion,benefitDuration,tiePriority,documentNumber,article,sourceUrl,evidenceUpdateMode,evidenceUpdateGuidance,minimumAge,maximumAge,ageReferenceRule,firstDegreeRequired").split(","))n.putNull(f);
  n.put("name","SYNTHETIC M2");n.put("rate","1.000000000");n.put("scopeCode","hoc_phi_chuong_trinh_chinh");n.putArray("criteria");n.putArray("documentGroups");return n;
 }
 @Test void draftMayLackConfirmationEvidence(){assertThatCode(()->PolicyRules.validate(draft())).doesNotThrowAnyException();}
 @ParameterizedTest @ValueSource(strings={"70","-0.1","1.0000000001","1e0","NaN"})void POL_N01_rejectInvalidRates(String rate){var n=draft();n.put("rate",rate);assertThatThrownBy(()->PolicyRules.validate(n)).hasMessage("VALIDATION_ERROR");}
 @Test void rejectMissingNullableFieldAndUnknownProperty(){var n=draft();n.remove("effectiveUntil");assertThatThrownBy(()->PolicyRules.validate(n)).hasMessage("VALIDATION_ERROR");var b=draft();b.put("status","locked");assertThatThrownBy(()->PolicyRules.validate(b)).hasMessage("VALIDATION_ERROR");}
 @Test void POL_N03_incompleteDraftCannotBeConfirmed(){assertThatThrownBy(()->PolicyRules.confirm(draft(),"M2")).hasMessage("POLICY_NOT_READY");}
 @Test void invalidAgeRange(){var n=draft();n.put("minimumAge",24);n.put("maximumAge",22);assertThatThrownBy(()->PolicyRules.validate(n)).hasMessage("VALIDATION_ERROR");}
 @Test void idStrictBigintDomain(){assertThat(ConfigRules.id("9223372036854775807")).isEqualTo(Long.MAX_VALUE);for(String s:new String[]{"0","-1","01","1.1","9223372036854775808","x"})assertThatThrownBy(()->ConfigRules.id(s)).hasMessage("VALIDATION_ERROR");}
 @Test void etagBindsIdentityProjectionAndBody(){var b=draft();var a=ConfigRules.tag("policy:1:manager",new byte[]{1},b,JSON);assertThat(a).isNotEqualTo(ConfigRules.tag("policy:2:manager",new byte[]{1},b,JSON));b.put("name","Changed");assertThat(a).isNotEqualTo(ConfigRules.tag("policy:1:manager",new byte[]{1},b,JSON));assertThatThrownBy(()->ConfigRules.match(a,null)).hasMessage("PRECONDITION_REQUIRED");assertThatThrownBy(()->ConfigRules.match(a,"W/"+a)).hasMessage("VALIDATION_ERROR");assertThatThrownBy(()->ConfigRules.match(a,"\"stale\"")).hasMessage("VERSION_CONFLICT");}
 static ObjectNode readyM6(){var n=draft();n.put("legalBasis","SYNTHETIC");n.put("ruleVersion","TEST-M6");n.put("effectiveFrom","2026-09-01");n.put("evidenceUpdateMode","mot_lan_cap_nhat_khi_thay_doi");n.withArray("criteria").addObject().put("code","C1").put("name","SYNTHETIC").putNull("assessmentGuidance").put("required",true).put("conditional",false).putNull("applicabilityGuidance").put("order",1);var g=n.withArray("documentGroups").addObject().put("code","RESIDENCE").put("name","SYNTHETIC").put("minimumRequired",2).put("order",1);var docs=g.putArray("documents");for(var subject:new String[]{"sinh_vien","cha_hoac_me"}){var d=docs.addObject();for(var f:PolicyRules.DOCUMENT.split(","))d.putNull(f);d.put("code",subject);d.put("name","SYNTHETIC");d.put("type","xac_nhan_cu_tru");d.put("evidenceSubject",subject);d.put("hardCopyRequired",false);d.put("certifiedCopyRequired",false);d.put("stampRequired",false);}return n;}
 @Test void POL_N04_M6_needs_both_subjects_not_just_an_OR_group(){var n=readyM6();assertThatCode(()->PolicyRules.confirm(n,"M6")).doesNotThrowAnyException();((ObjectNode)n.path("documentGroups").get(0)).put("minimumRequired",1);assertThatThrownBy(()->PolicyRules.confirm(n,"M6")).hasMessage("POLICY_NOT_READY");}
}
