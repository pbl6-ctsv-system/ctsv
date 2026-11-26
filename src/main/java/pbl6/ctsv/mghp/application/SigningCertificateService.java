package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.shared.ca.*;
import pbl6.ctsv.shared.ca.CaProviderDtos.*;
import static pbl6.ctsv.mghp.application.SigningSupportEntities.*;
import static pbl6.ctsv.mghp.application.IntakeMapping.map;
import static pbl6.ctsv.mghp.application.IntakeRules.*;

@Service @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningCertificateService {
 final SigningSupportRepository r;final SigningSupportAccess access;final SigningOptions options;final SigningProvider ca;final Clock clock;final TransactionTemplate tx;
 SigningCertificateService(SigningSupportRepository r,SigningSupportAccess access,SigningOptions options,SigningProvider ca,Clock clock,PlatformTransactionManager manager){this.r=r;this.access=access;this.options=options;this.ca=ca;this.clock=clock;tx=new TransactionTemplate(manager);}
 CertificateMonitor scoped(long id){var c=r.lockCertificate(id);if(!options.monitoredSigners().containsKey(c.email))throw IntakeProblem.missing();return c;}
 List<CertificateMonitor> configured(){return options.monitoredSigners().keySet().stream().map(r::ensureCertificate).toList();}
 Map<String,Object> summary(CertificateMonitor c){
  var observed=SigningCertificateRules.observe(r.observation(c),clock.instant());var selected=observed.selected();String state=c.observation==null?"unknown":observed.state();var renewal=r.currentRenewal(c.id);
  if(renewal!=null&&"approved".equals(renewal.state)&&selected!=null&&Objects.equals(renewal.previousCertificate,selected.id()))state="not_ready";
  return map("id",c.id,"email",c.email,"usages",options.monitoredSigners().get(c.email),"state",state,"certificateId",selected==null?null:selected.id(),"serial",selected==null?null:selected.serialNumber(),"ownerDisplay",selected==null?null:selected.userDisplay(),"validFrom",selected==null||selected.validFrom()==null?null:selected.validFrom().toString(),"validTo",selected==null||selected.validTo()==null?null:selected.validTo().toString(),"lastCheckedAt",c.checkedAt,"lastSuccessfulAt",c.successfulAt,"checkFailed",c.error!=null,"errorCode",c.error,"renewal",renewalView(renewal));
 }
 Map<String,Object> renewalView(Renewal renewal){return renewal==null?null:map("operationId",renewal.operation,"providerRequestId",renewal.providerRequest,"status",renewal.state,"reason",renewal.reason,"adminComment",renewal.comment,"requestedAt",renewal.at,"checkedAt",renewal.checkedAt,"errorCode",renewal.error);}
 ApplicationReads.Detail view(CertificateMonitor c){
  var b=summary(c);b.put("history",r.certHistory(c.id).stream().map(h->map("id",h.id,"kind",h.kind,"text",h.text,"reference",h.reference,"actorId",h.actor,"at",h.at)).toList());
  var actions=new ArrayList<>(List.of("check","add_note","renew_help"));var renewal=r.currentRenewal(c.id);boolean busy=r.signerBusy(c.email);
  var selected=SigningCertificateRules.observe(r.observation(c),clock.instant()).selected();boolean replacing=renewal!=null&&"approved".equals(renewal.state)&&(selected==null||Objects.equals(renewal.previousCertificate,selected.id()));
  if((renewal==null||!Boolean.TRUE.equals(renewal.open))&&!busy&&!replacing)actions.add("renew");if(renewal!=null)actions.add("check_renewal");b.put("allowedActions",actions);b.put("signingInProgress",busy);
  return new ApplicationReads.Detail(b,r.tag(c));
 }
 Map<String,Object> list(AuthAccess.Principal p,String filter){return tx.execute(s->{access.require(p);if(!Set.of("all","attention").contains(filter))throw IntakeProblem.invalid("filter");var items=configured().stream().map(this::summary).filter(c->"all".equals(filter)||!"valid".equals(c.get("state"))||Boolean.TRUE.equals(c.get("checkFailed"))).toList();return map("items",items);});}
 ApplicationReads.Detail read(AuthAccess.Principal p,long id){return tx.execute(s->{access.require(p);return view(scoped(id));});}
 ApplicationReads.Detail check(AuthAccess.Principal p,long id){long actor=tx.execute(s->{var a=access.require(p);scoped(id);return a.userId();});return checkInternal(id,actor);}
 Map<String,Object> checkAll(AuthAccess.Principal p){var a=tx.execute(s->{var actor=access.require(p);return new CheckSet(actor.userId(),configured().stream().map(c->c.id).toList());});for(long id:a.ids())checkInternal(id,a.actor());return tx.execute(s->map("items",configured().stream().map(this::summary).toList()));}
 record CheckSet(long actor,List<Long> ids){}
 void checkSystem(){var ids=tx.execute(s->configured().stream().map(c->c.id).toList());for(long id:ids)checkInternal(id,null);}
 ApplicationReads.Detail checkInternal(long id,Long actor){
  String email=tx.execute(s->scoped(id).email);Certificates observed=null;CaFailure failure=null;
  try{observed=ca.certificates(email);if(!email.equals(observed.email()))throw new CaFailure("CA_IDENTITY_INVALID");}catch(CaFailure e){failure=e;}
  Certificates result=observed;CaFailure error=failure;
  return tx.execute(s->{var c=scoped(id);c.checkedAt=r.now();
   if(error==null){c.observation=r.encode(result);c.state=SigningCertificateRules.observe(result,clock.instant()).state();c.successfulAt=c.checkedAt;c.error=null;r.certEvent(c,actor,"check","Đã đọc chứng thư CA: "+c.state,null,c.observation);}
   else{var safe=SigningFailurePolicy.classify("certificate",error);c.error=safe.code();r.certEvent(c,actor,"check_failed",safe.adminMessage(),null,null);}
   r.touchCertificate(c);return view(c);
  });
 }
 ApplicationReads.Detail note(AuthAccess.Principal p,long id,JsonNode body,String tag){return tx.execute(s->{
  var actor=access.require(p);SigningSupportRules.body(body,List.of("text"),List.of("reference"));String value=text(body.get("text"),"text",2000,true),reference=body.hasNonNull("reference")?text(body.get("reference"),"reference",200,false):null;
  var c=scoped(id);SigningDocuments.tag(tag,r.tag(c));r.certEvent(c,actor.userId(),"note",value,reference,null);r.touchCertificate(c);r.audit(actor.userId(),"SIGNING_CERTIFICATE_NOTE","mghp_theo_doi_chung_thu",c.id.toString());return view(c);
 });}
 record RenewalResult(ApplicationReads.Detail detail,boolean created){}
 record Intent(long id,long certificate,String email,long actor,String reason,boolean created){}
 RenewalResult renew(AuthAccess.Principal p,long id,JsonNode body,String tag){
  shape(body,List.of("reason"));String reason=text(body.get("reason"),"reason",1000,true);
  var intent=tx.execute(s->{var actor=access.require(p);var c=scoped(id);SigningDocuments.tag(tag,r.tag(c));var current=r.currentRenewal(id);
   if(current!=null&&Boolean.TRUE.equals(current.open))return new Intent(current.id,id,c.email,actor.userId(),reason,false);
   var observed=SigningCertificateRules.observe(r.observation(c),clock.instant()).selected();if(current!=null&&"approved".equals(current.state)&&(observed==null||Objects.equals(current.previousCertificate,observed.id())))throw IntakeProblem.conflict("CERTIFICATE_REPLACEMENT_PENDING");
   if(r.signerBusy(c.email))throw IntakeProblem.conflict("CERTIFICATE_SIGNING_IN_PROGRESS");
   var renewal=new Renewal();renewal.certificate=id;renewal.operation=UUID.randomUUID();renewal.state="intent";renewal.open=true;renewal.reason=reason;renewal.actor=actor.userId();renewal.at=r.now();var old=SigningCertificateRules.observe(r.observation(c),clock.instant()).selected();renewal.previousCertificate=old==null?null:old.id();r.save(renewal);
   r.certEvent(c,actor.userId(),"renew_intent","Admin xác nhận gửi đề nghị gia hạn.",null,null);r.touchCertificate(c);r.audit(actor.userId(),"SIGNING_CERTIFICATE_RENEW","mghp_theo_doi_chung_thu",c.id.toString());return new Intent(renewal.id,id,c.email,actor.userId(),reason,true);
  });
  if(!intent.created())return new RenewalResult(read(p,id),false);
  try{
   var pending=ca.certificateRequests(intent.email(),"pending");
   if(pending.size()>1)throw new CaFailure("CA_RENEW_AMBIGUOUS",0,true);
   if(pending.size()==1){recordRequest(intent,pending.getFirst());return new RenewalResult(read(p,id),false);}
   tx.executeWithoutResult(s->{access.accepted(p);var c=scoped(id);var current=r.currentRenewal(id);if(current==null||current.id!=intent.id()||!"intent".equals(current.state)||r.signerBusy(c.email))throw IntakeProblem.conflict("CERTIFICATE_SIGNING_IN_PROGRESS");});
   var submitted=ca.renewCertificate(intent.email(),intent.reason());recordRequest(intent,submitted);return new RenewalResult(read(p,id),true);
  }catch(CaFailure failure){
   if("CA_RENEW_PENDING".equals(failure.code())){var pending=ca.certificateRequests(intent.email(),"pending");if(pending.size()==1){recordRequest(intent,pending.getFirst());return new RenewalResult(read(p,id),false);}}
   tx.executeWithoutResult(s->{var renewal=r.find(Renewal.class,intent.id());renewal.state=failure.uncertain()?"unknown":"failed";renewal.open=failure.uncertain();renewal.error=SigningFailurePolicy.classify("certificate",failure).code();renewal.checkedAt=r.now();var c=scoped(id);r.certEvent(c,intent.actor(),"renew_failed","Chưa hoàn tất gửi đề nghị gia hạn; đối chiếu trạng thái yêu cầu trước khi gửi tiếp.",null,null);r.touchCertificate(c);});
   throw new IntakeProblem(failure.httpStatus()==400?400:503,"CA_RENEWAL_REQUIRES_ATTENTION","reason");
  }
 }
 void recordRequest(Intent intent,CertificateRequest request){tx.executeWithoutResult(s->{
  var c=scoped(intent.certificate());var renewal=r.find(Renewal.class,intent.id());renewal.providerRequest=request.id();renewal.state=request.status();renewal.open="pending".equals(request.status());renewal.comment=request.adminComment();renewal.checkedAt=r.now();renewal.error=null;
  r.certEvent(c,intent.actor(),"renew_status","CA ghi nhận yêu cầu gia hạn: "+request.status(),request.id().toString(),null);r.touchCertificate(c);
 });}
 ApplicationReads.Detail checkRenewal(AuthAccess.Principal p,long id){
  var intent=tx.execute(s->{var actor=access.require(p);var c=scoped(id);var renewal=r.currentRenewal(id);if(renewal==null)throw IntakeProblem.conflict("RENEWAL_NOT_FOUND");return new Intent(renewal.id,id,c.email,actor.userId(),renewal.reason,false);});
  UUID target=tx.execute(s->r.find(Renewal.class,intent.id()).providerRequest);var matches=new ArrayList<CertificateRequest>();
  try{
   for(String status:List.of("pending","approved","rejected"))for(var request:ca.certificateRequests(intent.email(),status))if(Objects.equals(target,request.id()))matches.add(request);
   if(matches.size()==1){recordRequest(intent,matches.getFirst());if("approved".equals(matches.getFirst().status()))return checkInternal(id,intent.actor());}
   else tx.executeWithoutResult(s->{var c=scoped(id);var renewal=r.find(Renewal.class,intent.id());if(target==null){renewal.state="unknown";renewal.open=true;}renewal.error="CA_REQUEST_NOT_FOUND";renewal.checkedAt=r.now();r.certEvent(c,intent.actor(),"renew_check","Chưa xác định được kết quả yêu cầu. Admin đối chiếu với CA, không gửi lại mù.",null,null);r.touchCertificate(c);});
  }catch(CaFailure error){throw new IntakeProblem(503,"CA_CERTIFICATE_CHECK_UNAVAILABLE","request");}
  return read(p,id);
 }
}
