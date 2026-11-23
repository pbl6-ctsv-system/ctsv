package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pbl6.ctsv.auth.AuthAccess;import pbl6.ctsv.mghp.configuration.IntakeConfiguration;
import static pbl6.ctsv.mghp.application.SigningEntities.*;
import static pbl6.ctsv.mghp.application.IntakeRules.*;
import static pbl6.ctsv.mghp.application.IntakeMapping.map;

@Service @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningAuthorizations {
 final SigningRepository r;final SigningAccess access;final SigningReads reads;final IntakeConfiguration config;final DecisionReads hashing;
 SigningAuthorizations(SigningRepository r,SigningAccess access,SigningReads reads,IntakeConfiguration config,DecisionReads hashing){this.r=r;this.access=access;this.reads=reads;this.config=config;this.hashing=hashing;}
 ApplicationReads.Detail list(AuthAccess.Principal p,long id){var a=access.specialist(p);var step=r.find(Step.class,id);return view(step,a.userId());}
 ApplicationReads.Detail view(Step step,long user){var q=r.find(Request.class,step.requestId);access.requireRead(user,q.versionId);var rows=r.authorizations(step.id);return new ApplicationReads.Detail(map("items",rows),"\""+hashing.hash(List.of("P11/authority",Base64.getEncoder().encodeToString(q.fence),rows))+"\"");}
 ApplicationReads.Detail add(AuthAccess.Principal p,long id,JsonNode body,String tag){
  var actor=access.specialist(p);config.lockWrites();var step=r.find(Step.class,id);var q=r.lock(step.requestId);SigningDocuments.tag(tag,view(step,actor.userId()).tag());editable(q,step);
  var allowed=Set.of("type","authorizedSignerEmail","evidenceFileId","evidenceReference","effectiveAt","expiresAt");
  if(body==null||!body.isObject())throw IntakeProblem.invalid("body");body.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw IntakeProblem.invalid(k);});
  for(String key:List.of("type","authorizedSignerEmail","effectiveAt"))if(!body.has(key))throw IntakeProblem.invalid(key);
  if(!"send_for_selected_signer".equals(text(body.get("type"),"type",128,true))||!step.email.equals(text(body.get("authorizedSignerEmail"),"authorizedSignerEmail",254,true)))throw IntakeProblem.conflict("AUTHORIZATION_CONFLICT");
  String reference=body.hasNonNull("evidenceReference")?text(body.get("evidenceReference"),"evidenceReference",512,true):null;
  Long file=body.hasNonNull("evidenceFileId")?id(body.get("evidenceFileId"),"evidenceFileId"):null;if(reference==null&&file==null)throw IntakeProblem.invalid("evidenceReference");
  Instant effective=instant(body,"effectiveAt",false),expires=instant(body,"expiresAt",true);if(expires!=null&&!expires.isAfter(effective))throw IntakeProblem.invalid("expiresAt");
  long added=r.addAuthorization(step,actor.userId(),reference,file,effective,expires);r.audit(actor.userId(),"SIGNING_AUTHORITY_ADD",q.versionId,reference);r.touch(q);
  var result=r.authorization(added);return new ApplicationReads.Detail(result,view(step,actor.userId()).tag());
 }
 Instant instant(JsonNode b,String key,boolean nullable){if(!b.hasNonNull(key)&&nullable)return null;try{return OffsetDateTime.parse(text(b.get(key),key,64,true)).toInstant();}catch(java.time.DateTimeException e){throw IntakeProblem.invalid(key);}}
 void editable(Request q,Step step){if(!Set.of("prepared","waiting_next").contains(q.state)||!"waiting".equals(step.state)||!"current".equals(r.link(q.versionId).state))throw IntakeProblem.conflict("AUTHORIZATION_CONFLICT");}
 ApplicationReads.Detail revoke(AuthAccess.Principal p,long id,JsonNode body,String tag){
  var actor=access.specialist(p);shape(body,List.of("reason"));String reason=text(body.get("reason"),"reason",4000,true);config.lockWrites();var row=r.authorization(id);var step=r.find(Step.class,Long.parseLong(row.get("stepId").toString()));var q=r.lock(step.requestId);SigningDocuments.tag(tag,view(step,actor.userId()).tag());editable(q,step);
  r.revokeAuthorization(id,actor.userId(),reason);r.audit(actor.userId(),"SIGNING_AUTHORITY_REVOKE",q.versionId,reason);r.touch(q);return new ApplicationReads.Detail(r.authorization(id),view(step,actor.userId()).tag());
 }
}
