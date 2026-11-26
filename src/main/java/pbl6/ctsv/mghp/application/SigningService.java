package pbl6.ctsv.mghp.application;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;import java.util.*;
import org.springframework.stereotype.Service;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.PlatformTransactionManager;import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.auth.AuthAccess;import pbl6.ctsv.mghp.configuration.IntakeConfiguration;
import pbl6.ctsv.shared.ca.CaPdfPreflight;import pbl6.ctsv.shared.files.ManagedFiles;
import static pbl6.ctsv.mghp.application.SigningEntities.*;
import static pbl6.ctsv.mghp.application.IntakeRules.*;

@Service @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningService {
 record Created(ApplicationReads.Detail detail,boolean created){}
 final SigningSupportJournal journal;final SigningRepository r;final SigningAccess access;final SigningDocuments docs;final SigningReads reads;final SigningOptions options;final IntakeConfiguration config;final ManagedFiles files;final TransactionTemplate tx;
 SigningService(SigningRepository r,SigningAccess access,SigningDocuments docs,SigningReads reads,SigningOptions options,IntakeConfiguration config,ManagedFiles files,PlatformTransactionManager manager,SigningSupportJournal journal){this.journal=journal;this.r=r;this.access=access;this.docs=docs;this.reads=reads;this.options=options;this.config=config;this.files=files;tx=new TransactionTemplate(manager);}
 Created prepare(AuthAccess.Principal principal,long version,JsonNode body){
  var actor=access.specialist(principal);shape(body,List.of());config.lockWrites();var link=r.link(version);if(!"current".equals(link.state))throw IntakeProblem.conflict("SIGNING_NOT_READY");
  var old=r.request(version);if(old!=null)return new Created(reads.request(old.id,actor.userId()),false);
  if(!r.minutesCompleted(link))throw IntakeProblem.conflict("MINUTES_NOT_COMPLETED");
  var plan=options.plan(link.minutesId!=null);for(var s:plan.signers())access.require(s.user(),s.role());
  var q=new Request();q.code=UUID.randomUUID();q.versionId=version;q.providerId=plan.provider();q.category=plan.category();q.mode=plan.mode();q.authority="send_for_selected_signer";q.state="prepared";q.actor=actor.userId();q.createdAt=docs.now();q.adapter="ctsv-p11-v1";r.save(q);
  int order=1;for(var s:plan.signers()){
   var step=new Step();step.requestId=q.id;step.userId=s.user();step.role=s.role();step.order=order++;step.email=s.email();step.state="waiting";step.createdAt=docs.now();r.save(step);r.authorization(step,s.authority(),actor.userId());
  }
  r.audit(actor.userId(),"SIGNING_PLAN_CREATE",version,"Kế hoạch "+plan.mode());
  long first=plan.signers().getFirst().user();if(first!=actor.userId())r.notify(q.id,actor.userId(),List.of(first),"Văn bản đang chờ bạn ký","Vui lòng mở đúng tài liệu và chủ động ký bước của mình.");
  return new Created(reads.request(q.id,actor.userId()),true);
 }
 ApplicationReads.Detail read(AuthAccess.Principal p,long id){var i=access.actor(p);return reads.request(id,i.userId());}
 ApplicationReads.Detail list(AuthAccess.Principal p,String status,boolean mine,int page,int size,String view){var i=access.actor(p);if(status!=null&&!Set.of("prepared","queued","signing","waiting_next","failed","unknown","needs_support","refused","completed").contains(status))throw IntakeProblem.invalid("status");return reads.list(i.userId(),status,mine,page,size,view);}
 record Input(long file,String hash,String tag){}
 ApplicationReads.Detail enqueue(AuthAccess.Principal principal,long id,JsonNode body,String tag){
  shape(body,List.of("geometry","confirmReviewed"));if(!body.get("confirmReviewed").isBoolean())throw IntakeProblem.invalid("confirmReviewed");
  var g=body.get("geometry");shape(g,List.of("page","x","y","width","height"));
  if(!g.get("page").canConvertToInt()||!g.get("page").isIntegralNumber())throw IntakeProblem.invalid("geometry.page");
  double[] v=new double[4];int n=0;for(String field:List.of("x","y","width","height")){if(!g.get(field).isNumber()||!Double.isFinite(g.get(field).doubleValue()))throw IntakeProblem.invalid("geometry."+field);v[n++]=g.get(field).doubleValue();}
  var box=new CaPdfPreflight.Box(g.get("page").asInt(),v[0],v[1],v[2],v[3]);
  Input input=tx.execute(s->{var actor=access.actor(principal);config.lockWrites();var q=r.lock(id);var snapshot=reads.request(id,actor.userId());SigningDocuments.tag(tag,snapshot.tag());var step=reads.current(q);guard(q,step,actor.userId(),body.get("confirmReviewed").asBoolean());long file=r.input(q);return new Input(file,files.describe(file).sha256(),snapshot.tag());});
  // Download and geometry parsing outside SQL. A second CAS below prevents a stale input/ACK.
  pbl6.ctsv.shared.files.FileProblem readFailure=null;
  try{var pdf=files.read(input.file());try{CaPdfPreflight.inspect(pdf.bytes(),box);}catch(pbl6.ctsv.shared.ca.CaFailure f){throw IntakeProblem.invalid("geometry");}}
  catch(pbl6.ctsv.shared.files.FileProblem failure){readFailure=failure;}
  var technicalFailure=readFailure;
  return tx.execute(s->{var actor=access.actor(principal);config.lockWrites();var q=r.lock(id);SigningDocuments.tag(input.tag(),reads.request(id,actor.userId()).tag());var step=reads.current(q);guard(q,step,actor.userId(),body.get("confirmReviewed").asBoolean());
   if(r.input(q)!=input.file()||!files.describe(input.file()).sha256().equals(input.hash()))throw new IntakeProblem(412,"VERSION_CONFLICT","If-Match");
   step.page=box.page();step.x=BigDecimal.valueOf(box.x());step.y=BigDecimal.valueOf(box.y());step.width=BigDecimal.valueOf(box.width());step.height=BigDecimal.valueOf(box.height());step.state="queued";step.updatedAt=docs.now();q.state="queued";q.startedAt=q.startedAt==null?docs.now():q.startedAt;
   if("pho_phong".equals(step.role))r.acknowledge(r.find(DocumentVersion.class,q.versionId),actor.userId(),input.hash());
   r.enqueue(q,step,principal,input.file(),input.hash());r.audit(actor.userId(),"SIGNING_CONFIRM",q.versionId,"Người ký xác nhận đúng PDF và vùng ký");r.refresh(q);
   if(technicalFailure!=null){var work=r.work(id);var evidence=journal.capture(work,null,"storage",technicalFailure);r.error(work,null,"failed",evidence.error().code(),false);journal.persist(evidence);}
   return reads.request(id,actor.userId());
  });
 }
 void guard(Request q,Step step,long user,boolean ack){
  if(step==null)throw IntakeProblem.conflict("SIGNING_NOT_READY");
  if(!Objects.equals(step.userId,user))throw IntakeProblem.conflict("SIGNING_STEP_NOT_READY");
  access.require(user,step.role);SigningRules.canAct(user,step.userId,step.state,step.order,reads.current(q).order,r.minutesCompleted(r.link(q.versionId)));
  if(!Set.of("prepared","waiting_next").contains(q.state)||!"current".equals(r.link(q.versionId).state)||!r.authorized(step))throw IntakeProblem.conflict("SIGNING_NOT_READY");
  if("pho_phong".equals(step.role)!=ack)throw IntakeProblem.invalid("confirmReviewed");
 }
 ApplicationReads.Detail reconcile(AuthAccess.Principal principal,long id,JsonNode body,String tag){
  var actor=access.actor(principal);var q=r.find(Request.class,id);access.requireRead(actor.userId(),q.versionId);
  throw new IntakeProblem(410,"SIGNING_SUPPORT_MANUAL_ONLY","request");
 }
}
