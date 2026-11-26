package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;
import static pbl6.ctsv.mghp.application.SigningSupportEntities.*;
import static pbl6.ctsv.mghp.application.IntakeMapping.map;
import static pbl6.ctsv.mghp.application.IntakeRules.*;

@Service @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningSupportService {
 final SigningSupportRepository r;final SigningSupportAccess access;final SigningResultSpool spool;final TransactionTemplate tx;
 SigningSupportService(SigningSupportRepository r,SigningSupportAccess access,SigningResultSpool spool,PlatformTransactionManager manager){this.r=r;this.access=access;this.spool=spool;tx=new TransactionTemplate(manager);}
 Map<String,Object> summary(Incident i){
  var e=r.snapshot(i);var c=e.context();return map("id",i.id,"status",i.state,"requestId",c.request(),"documentVersionId",documentVersion(i.attempt),"title",c.title(),"documentType",c.documentType(),"version",c.version(),"roundName",c.round(),"semesterName",c.semester(),"actorName",c.account(),"actorAccount",c.account(),"actorAccountId",c.accountId(),"actorRole",c.role(),"stage",e.error().stage(),"occurredAt",i.occurredAt);
 }
 Long documentVersion(long attempt){return r.documentVersion(attempt);}
 ApplicationReads.Detail view(Incident i){
  var e=r.snapshot(i);var error=e.error();var b=summary(i);
  b.putAll(map("code",error.code(),"message",error.adminMessage(),"httpStatus",error.httpStatus(),"apiMethod",error.apiMethod(),"apiPath",error.apiPath(),"providerDocumentId",e.receipt()==null?null:e.receipt().document(),"receiptId",e.receipt()==null?null:e.receipt().id(),"startedAt",i.startedAt,"closedAt",i.closedAt,"startedBy",i.startedBy,"closedBy",i.closedBy));
  var h=r.history(i.id);b.put("notes",h.stream().filter(n->"note".equals(n.kind)).map(n->map("id",n.id,"text",n.text,"reference",n.reference,"actorId",n.actor,"at",n.at)).toList());
  b.put("history",h.stream().map(n->map("id",n.id,"kind",n.kind,"text",n.text,"reference",n.reference,"actorId",n.actor,"at",n.at)).toList());
  var actions=new ArrayList<String>();if("waiting".equals(i.state))actions.add("start");if("working".equals(i.state))actions.add("finish");actions.addAll(List.of("add_note","copy"));b.put("allowedActions",actions);
  return new ApplicationReads.Detail(b,r.tag(i));
 }
 ApplicationReads.Detail read(AuthAccess.Principal p,long id){return tx.execute(s->{access.require(p);return view(r.find(Incident.class,id));});}
 Map<String,Object> list(AuthAccess.Principal p,String status,String query,int page,int size){return tx.execute(s->{access.require(p);if(!Set.of("waiting","working","resolved","all").contains(status))throw IntakeProblem.invalid("status");if(query.length()>200)throw IntakeProblem.invalid("q");var items=r.incidents(status,query,page,size);return map("items",items.items().stream().map(this::summary).toList(),"page",page,"size",size,"totalElements",items.total(),"hasMore",(long)page*size+items.items().size()<items.total(),"counts",items.counts());});}
 ApplicationReads.Detail change(AuthAccess.Principal p,long id,String action,JsonNode body,String supplied){return tx.execute(s->{
  var actor=access.require(p);shape(body,List.of());var i=r.lockIncident(id);SigningDocuments.tag(supplied,r.tag(i));String next=SigningSupportRules.transition(i.state,action);
  if(!i.state.equals(next)){r.change(i,actor.userId(),next);r.event(i,actor.userId(),action,"start".equals(action)?"Admin bắt đầu xử lý sự cố.":"Admin kết thúc xử lý sự cố.",null);r.audit(actor.userId(),"start".equals(action)?"SIGNING_SUPPORT_START":"SIGNING_SUPPORT_FINISH","mghp_su_co_ky",i.id.toString());}
  return view(i);
 });}
 ApplicationReads.Detail note(AuthAccess.Principal p,long id,JsonNode body,String supplied){return tx.execute(s->{
  var actor=access.require(p);SigningSupportRules.body(body,List.of("text"),List.of("reference"));String value=text(body.get("text"),"text",2000,true),reference=body.hasNonNull("reference")?text(body.get("reference"),"reference",200,false):null;
  var i=r.lockIncident(id);SigningDocuments.tag(supplied,r.tag(i));r.event(i,actor.userId(),"note",value,reference);r.touch(i);r.audit(actor.userId(),"SIGNING_SUPPORT_NOTE","mghp_su_co_ky",i.id.toString());return view(i);
 });}
 Map<String,Object> files(AuthAccess.Principal p,String filter){
  tx.executeWithoutResult(s->access.require(p));if(!Set.of("all","unfinished","deleted").contains(filter))throw IntakeProblem.invalid("filter");
  var items=spool.list().stream().filter(e->e.size()>0).filter(e->"deleted".equals(filter)?e.deleted():!e.deleted()&&(!"unfinished".equals(filter)||!e.completed())).map(e->map("operationId",e.context().operation(),"requestId",e.context().request(),"title",e.context().title(),"fileName",e.fileName(),"size",""+e.size(),"sha256",e.sha256(),"receivedAt",e.receivedAt()==null?null:e.receivedAt().toString(),"hadError",e.hadError(),"completed",e.completed(),"deleted",e.deleted(),"verified",spool.verified(e.context().operation()))).toList();return map("items",items);
 }
 Map<String,Object> deleteFile(AuthAccess.Principal p,UUID id,JsonNode body){
  long actor=tx.execute(s->access.require(p).userId());shape(body,List.of("confirm"));if(!body.path("confirm").isBoolean()||!body.path("confirm").asBoolean())throw IntakeProblem.invalid("confirm");
  if(spool.read(id).isEmpty())throw IntakeProblem.missing();
  // SQL intent commits before an irreversible filesystem operation. If SQL is down, keep the PDF.
  tx.executeWithoutResult(s->r.audit(actor,"SIGNING_SPOOL_DELETE_INTENT","signing_operation",id.toString()));
  spool.delete(id);tx.executeWithoutResult(s->r.audit(actor,"SIGNING_SPOOL_DELETE","signing_operation",id.toString()));return map("operationId",id,"deleted",true);
 }
}
