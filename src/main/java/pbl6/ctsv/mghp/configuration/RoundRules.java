package pbl6.ctsv.mghp.configuration;
import com.fasterxml.jackson.databind.*;import java.time.*;import java.util.*;
final class RoundRules {
 static final String FIELDS="code,semesterId,name,description,opensAt,submissionDeadline,reviewDeadline,declarationGuidance,lateSubmissionGuidance,allowRetroactive,retroactiveMode,midSemesterMode,policyVersions";
 static void validate(JsonNode n){ConfigRules.shape(n,FIELDS);ConfigRules.text(n,"code",128,true);ConfigRules.id(ConfigRules.text(n,"semesterId",19,true));ConfigRules.text(n,"name",512,false);for(var k:new String[]{"description","declarationGuidance","lateSubmissionGuidance"})ConfigRules.text(n,k,1048576,false);ConfigRules.bool(n,"allowRetroactive");for(var k:new String[]{"retroactiveMode","midSemesterMode"})ConfigRules.text(n,k,128,false);ConfigRules.allow(n,"retroactiveMode","theo_phap_luat");ConfigRules.allow(n,"midSemesterMode","khoang_ngay_chuyen_vien_xac_nhan");
  var a=ConfigRules.instant(n,"opensAt");var b=ConfigRules.instant(n,"submissionDeadline");var c=ConfigRules.instant(n,"reviewDeadline");if(a!=null&&b!=null&&a.isAfter(b)||b!=null&&c!=null&&b.isAfter(c))throw ConfigFailure.invalid("submissionDeadline");
  if(!n.path("policyVersions").isArray())throw ConfigFailure.invalid("policyVersions");var ids=new HashSet<Long>();for(var p:n.path("policyVersions")){ConfigRules.shape(p,"policyVersionId,applicationTemplateFileId,order");long id=ConfigRules.id(ConfigRules.text(p,"policyVersionId",19,true));if(!ids.add(id))throw ConfigFailure.invalid("policyVersions");if(!p.path("applicationTemplateFileId").isNull())ConfigRules.id(ConfigRules.text(p,"applicationTemplateFileId",19,true));ConfigRules.integer(p,"order",false);}
 }
 static boolean receives(String status,Instant start,Instant deadline,Instant serverReceived){return "open".equals(status)&&start!=null&&deadline!=null&&!serverReceived.isBefore(start)&&!serverReceived.isAfter(deadline);}
}
