package pbl6.ctsv.mghp.application;

import java.time.*;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pbl6.ctsv.shared.ca.*;import pbl6.ctsv.shared.ca.CaProviderDtos.*;
import pbl6.ctsv.shared.files.ManagedFiles;
import pbl6.ctsv.mghp.configuration.IntakeConfiguration;
import static pbl6.ctsv.mghp.application.SigningEntities.*;
import static pbl6.ctsv.mghp.application.SigningRepository.Work;

/** Durable one-actor worker. SQL transactions end before every CA/storage HTTP call. */
@Service @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
public class SigningWorker {
 final SigningRepository r;final SigningAccess access;final IntakeConfiguration config;final SigningProvider ca;
 final ManagedFiles files;final SigningArtifacts artifacts;final TransactionTemplate tx;final Clock clock;
 SigningWorker(SigningRepository r,SigningAccess access,IntakeConfiguration config,SigningProvider ca,ManagedFiles files,SigningArtifacts artifacts,PlatformTransactionManager manager,Clock clock){this.r=r;this.access=access;this.config=config;this.ca=ca;this.files=files;this.artifacts=artifacts;this.tx=new TransactionTemplate(manager);this.clock=clock;}
 record Context(Request request,Step step,DocumentVersion document){}
 Context context(Work work){return new Context(r.find(Request.class,work.request()),r.find(Step.class,work.step()),r.find(DocumentVersion.class,r.find(Request.class,work.request()).versionId));}
 void scope(long request){r.beginSqlScope();config.lockWrites();r.lock(request);}
 void dispatchPermission(Work w){var i=access.actor(w.principal());var c=context(w);if(i.userId()!=c.step.userId)throw new IntakeProblem(403,"FORBIDDEN","request");access.require(i.userId(),c.step.role);if(!r.authorized(c.step)||!"current".equals(r.link(c.document.id).state)||!r.minutesCompleted(r.link(c.document.id))||r.input(c.request)!=w.input()||!files.describe(w.input()).sha256().equals(w.hash()))throw IntakeProblem.conflict("SIGNING_NOT_READY");}
 public boolean runRequest(long request){
  Work pending=r.work(request);if(pending==null||!Set.of("queued","uploaded","download","storage","upload_intent","sign_intent").contains(pending.phase()))return false;
  UUID lease=UUID.randomUUID();
  Boolean claimed;
  try{claimed=tx.execute(s->{
   // Recovering an already signed receipt persists facts; revocation must not discard that PDF.
   boolean recovery=r.receipt(pending.id())!=null;
   if(!recovery&&!Set.of("upload_intent","sign_intent").contains(pending.phase()))dispatchPermission(pending);
   scope(request);var latest=r.work(request);
   if(latest==null||latest.id()!=pending.id()||!Objects.equals(latest.phase(),pending.phase())||latest.leaseUntil()!=null&&latest.leaseUntil().toInstant().isAfter(clock.instant()))return false;
   if(Set.of("upload_intent","sign_intent").contains(latest.phase())){r.error(latest,latest.lease(),"unknown","SIGNING_RESULT_UNKNOWN",true);return false;}
   if(!r.claim(latest,lease))return false;var q=r.find(Request.class,request);q.state="signing";r.refresh(q);return true;
  });}catch(pbl6.ctsv.foundation.api.FoundationAccess.Failure|pbl6.ctsv.auth.AuthFailure|IntakeProblem rejected){
   tx.executeWithoutResult(s->{scope(request);var w=r.work(request);if(w!=null&&w.id()==pending.id()&&Set.of("queued","uploaded").contains(w.phase())&&(w.leaseUntil()==null||!w.leaseUntil().toInstant().isAfter(clock.instant())))r.error(w,null,"failed","SIGNING_AUTHORIZATION_REVOKED",false);});return true;
  }
  if(!Boolean.TRUE.equals(claimed))return false;
  String phase=pending.phase();
  try{
   Context c=tx.execute(s->context(pending));var box=new CaPdfPreflight.Box(c.step.page,c.step.x.doubleValue(),c.step.y.doubleValue(),c.step.width.doubleValue(),c.step.height.doubleValue());
   var input=files.read(pending.input());if(!input.sha256().equals(pending.hash()))throw new CaFailure("CA_INPUT_HASH_MISMATCH");
   Receipt receipt=r.receipt(pending.id());UUID external=c.request.externalId;
   if(receipt==null){
    var certificates=ca.certificates(c.step.email);var active=certificates.items().stream().filter(cert->"active".equals(cert.status())&&cert.valid()&&cert.hasPrivateKey()&&cert.validFrom()!=null&&cert.validTo()!=null&&!clock.instant().isBefore(cert.validFrom())&&clock.instant().isBefore(cert.validTo())).toList();
    if(active.size()!=1)throw new CaFailure("CA_CERTIFICATE_SELECTION_REQUIRED");var cert=active.getFirst();
    var signer=CaSignerBinding.bind(c.step.email,cert.user(),cert.id(),null,certificates,new Templates(c.step.email,List.of()),clock.instant());
    CaSignerBinding.requireConsent(new CaSignerBinding.Consent("CTSV confirmed operation "+pending.operation(),c.step.email,cert.id(),pending.hash(),c.step.order),signer,pending.hash(),c.step.order);
    tx.executeWithoutResult(s->{dispatchPermission(pending);scope(request);r.owned(pending.id(),lease,true);
     for(var other:r.steps(request))if(!other.id.equals(c.step.id)&&other.certificate!=null&&(other.certificate.equals(cert.id())||Objects.equals(other.caUser,cert.user())||Objects.equals(other.serial,cert.serialNumber())))throw new CaFailure("CA_SIGNERS_NOT_DISTINCT");
     r.certificate(r.find(Step.class,pending.step()),cert);
    });
    boolean upload=external==null;
    if(!upload){try{if(!CaPdfPreflight.sha256(ca.download(external)).equals(pending.hash()))throw new CaFailure("CA_INPUT_HASH_MISMATCH");}catch(CaFailure e){if(e.httpStatus()==404&&c.step.order>1)upload=true;else throw e;}}
    if(upload){
     phase="upload_intent";setPhase(pending,lease,phase,true);
     var uploaded=ca.upload(CaPdfPreflight.inspect(input.bytes(),box),c.document.title,c.request.category);
     if(!uploaded.fileHash().equals(pending.hash())||!Objects.equals(uploaded.category(),c.request.category))throw new CaFailure("CA_UPLOAD_MISMATCH",201,true);
     external=uploaded.id();UUID id=external;tx.executeWithoutResult(s->{scope(request);r.uploaded(pending,lease,id);});
    }
    phase="sign_intent";setPhase(pending,lease,phase,true);
    receipt=ca.sign(external,cert.id(),signer.email(),signer.name(),box);
    if(!Objects.equals(receipt.document(),external)||!Objects.equals(receipt.certificate(),cert.id())||!Objects.equals(receipt.certificateSerial(),cert.serialNumber())||!signer.email().equals(receipt.signerEmail())||!signer.name().equals(receipt.signerName())||!pending.hash().equals(receipt.documentHash())||!"valid".equals(receipt.status()))throw new CaFailure("CA_RECEIPT_MISMATCH",201,true);
    Receipt captured=receipt;tx.executeWithoutResult(s->{scope(request);r.receipt(pending,lease,captured);});
   }
   phase="download";setPhase(pending,lease,phase,false);
   var committed=files.committed(pending.operation(),c.step.userId,receipt.signedPdfHash());
   byte[] output=committed.isPresent()?files.read(committed.get().id()).bytes():ca.download(receipt.document());
   phase="storage";setPhase(pending,lease,phase,false);
   var file=artifacts.store(pending.operation(),c.step.userId,input.bytes(),output,receipt,c.document.title+" — sau người ký "+c.step.order+".pdf");
   tx.executeWithoutResult(s->{scope(request);if(!files.describe(file.id()).ready())throw IntakeProblem.conflict("FILE_NOT_READY");r.completed(pending,lease,file.id(),c.step.userId);});
   return true;
  }catch(RuntimeException failure){
   boolean intent=Set.of("upload_intent","sign_intent").contains(phase);
   boolean uncertain=intent&&(!(failure instanceof CaFailure caFailure)||caFailure.uncertain());
   String safe=failure instanceof CaFailure e?e.code():failure instanceof pbl6.ctsv.shared.files.FileProblem?failure.getMessage():"SIGNING_INTERNAL_ERROR";
   String resume=Set.of("download","storage").contains(phase)?phase:"failed";
   tx.executeWithoutResult(s->{scope(request);r.error(pending,lease,resume,safe,uncertain);});
   org.slf4j.LoggerFactory.getLogger(SigningWorker.class).warn("Signing operation {} stopped at {} ({})",pending.operation(),phase,safe);
   return true;
  }
 }
 private void setPhase(Work w,UUID lease,String phase,boolean dispatch){tx.executeWithoutResult(s->{if(dispatch)dispatchPermission(w);scope(w.request());r.phase(w,lease,phase);});}
 public void tick(){for(long id:r.due())try{runRequest(id);}catch(RuntimeException e){org.slf4j.LoggerFactory.getLogger(SigningWorker.class).warn("Signing job deferred ({})",e.getClass().getSimpleName());}}
}
