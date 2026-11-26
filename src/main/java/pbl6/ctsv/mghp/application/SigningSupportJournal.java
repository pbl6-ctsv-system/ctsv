package pbl6.ctsv.mghp.application;

import java.time.*;import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;
import static pbl6.ctsv.mghp.application.SigningEntities.*;
import static pbl6.ctsv.mghp.application.SigningRepository.Work;

/** Local evidence first, SQL incident second. Never dispatches/restarts external operations. */
@Component @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningSupportJournal {
 final SigningResultSpool spool;final SigningRepository signing;final SigningSupportRepository support;final AuthAccess auth;final Clock clock;final TransactionTemplate tx;
 SigningSupportJournal(SigningResultSpool spool,SigningRepository signing,SigningSupportRepository support,AuthAccess auth,Clock clock,PlatformTransactionManager manager){this.spool=spool;this.signing=signing;this.support=support;this.auth=auth;this.clock=clock;tx=new TransactionTemplate(manager);}
 SigningResultSpool spool(){return spool;}
 void requireCurrentCertificate(String email,UUID certificate){if(support.replacementNotReady(email,certificate))throw new pbl6.ctsv.shared.ca.CaFailure("CA_CERTIFICATE_NOT_READY");}
 SigningResultSpool.Context context(Work w){
  var previous=spool.read(w.operation());if(previous.isPresent())return previous.get().context();
  String name;try{name=auth.accountLabel(w.principal());}catch(RuntimeException e){name=w.principal().accountId().toString();}String account=name;
  return tx.execute(s->{var q=signing.find(Request.class,w.request());var v=signing.find(DocumentVersion.class,q.versionId);var step=signing.find(Step.class,w.step());var link=signing.link(q.versionId);var b=signing.context(q.versionId);
   return new SigningResultSpool.Context(w.operation(),w.id(),w.request(),w.step(),step.userId,step.role,account,v.title,Objects.toString(b.get("roundName"),""),v.revision,w.hash(),link.type,Objects.toString(b.get("semesterName"),""),w.principal().accountId().toString());
  });
 }
 SigningResultSpool.Entry capture(Work w,SigningResultSpool.Context context,String phase,RuntimeException failure){
  return capture(w,context,phase,failure,null);
 }
 SigningResultSpool.Entry capture(Work w,SigningResultSpool.Context context,String phase,RuntimeException failure,pbl6.ctsv.shared.ca.CaProviderDtos.Receipt receipt){
  var error=SigningFailurePolicy.classify(phase,failure);
  if(context==null)try{context=context(w);}catch(RuntimeException ignored){context=new SigningResultSpool.Context(w.operation(),w.id(),w.request(),w.step(),0,"unknown",w.principal().accountId().toString(),"Tài liệu của yêu cầu #"+w.request(),"",0,w.hash());}
  try{spool.start(context);if(receipt!=null)spool.receipt(w.operation(),receipt);return spool.failure(w.operation(),error);}catch(RuntimeException disk){
   // SQL can still record the failure even when the local volume itself is unavailable.
   return new SigningResultSpool.Entry(context,receipt,w.operation()+".pdf",0,null,true,error,UUID.randomUUID(),clock.instant(),null,clock.instant(),false,false,false);
  }
 }
 void persist(SigningResultSpool.Entry entry){support.record(entry);}
 void reported(SigningResultSpool.Entry entry){try{spool.reported(entry.context().operation(),entry.errorEvent());}catch(RuntimeException e){warn("SIGNING_LOCAL_REPORT_MARK_PENDING");}}
 void report(Work w,String phase,RuntimeException failure){var e=capture(w,null,phase,failure);tx.executeWithoutResult(s->persist(e));reported(e);}
 void replay(){
  for(var e:spool.list())try{
   if(e.hadError()&&!e.reported()){
    tx.executeWithoutResult(s->{
     signing.beginSqlScope();signing.lock(e.context().request());var w=signing.work(e.context().request());
     if(w!=null&&w.id()==e.context().attempt()&&!"completed".equals(w.phase()))signing.error(w,null,"failed",e.error().code(),e.error().uncertain());
     persist(e);
    });reported(e);
   }
   else if(!e.hadError()){
    boolean complete=Boolean.TRUE.equals(tx.execute(s->support.completedAttempt(e.context().attempt())));
    if(complete)spool.completed(e.context().operation(),true);
   }
  }catch(RuntimeException failure){warn("SIGNING_SUPPORT_REPLAY_PENDING");}
 }
 private static void warn(String code){org.slf4j.LoggerFactory.getLogger(SigningSupportJournal.class).warn("Signing support deferred ({})",code);}
}
