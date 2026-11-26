package pbl6.ctsv.mghp.application;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pbl6.ctsv.auth.AuthAccess;
import pbl6.ctsv.mghp.configuration.IntakeConfiguration;
import pbl6.ctsv.shared.files.ManagedFiles;
import static pbl6.ctsv.mghp.application.SigningEntities.*;
import static pbl6.ctsv.mghp.application.IntakeRules.*;
import static pbl6.ctsv.mghp.application.IntakeMapping.map;

@Service @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningDocuments {
 final SigningRepository r;final DecisionRepository decisions;final DecisionReads decisionReads;
 final SigningAccess access;final IntakeConfiguration config;final ManagedFiles files;final Clock clock;
 SigningDocuments(SigningRepository r,DecisionRepository decisions,DecisionReads decisionReads,SigningAccess access,IntakeConfiguration config,ManagedFiles files,Clock clock){this.r=r;this.decisions=decisions;this.decisionReads=decisionReads;this.access=access;this.config=config;this.files=files;this.clock=clock;}
 OffsetDateTime now(){return clock.instant().atOffset(ZoneOffset.UTC);}
 static void tag(String supplied,String expected){if(supplied==null)throw new IntakeProblem(428,"PRECONDITION_REQUIRED","If-Match");if(!supplied.equals(expected))throw new IntakeProblem(412,"VERSION_CONFLICT","If-Match");}
 ApplicationReads.Detail bind(AuthAccess.Principal principal,boolean minutes,long businessId,JsonNode body,String supplied){
  var actor=access.specialist(principal);shape(body,List.of("fileId","title","evidence"));long fileId=id(body.get("fileId"),"fileId");
  String title=text(body.get("title"),"title",512,true),reason=text(body.get("evidence"),"evidence",16000,true);
  config.lockWrites();DecisionEntities.Minutes bb=minutes?decisions.find(DecisionEntities.Minutes.class,businessId):null;
  var source=decisions.find(DecisionEntities.Version.class,minutes?bb.versionId:businessId);decisions.lockRoot(source.rootId);decisions.refresh(source);
  if(minutes)decisions.refresh(bb);
  String currentTag=minutes?decisionReads.detail(map("id",bb.id,"hash",bb.hash),bb.fence).tag():decisionReads.version(source.id).tag();tag(supplied,currentTag);
  var f=files.describe(fileId);SigningRules.bindable(source.status,source.hash,minutes?bb.hash:source.hash,f.ready()&&f.size()<=10_000_000&&Objects.equals(f.owner(),actor.userId()));
  var links=r.links(minutes,businessId);Document document;
  if(!links.isEmpty()){
   var previous=r.find(DocumentVersion.class,links.getFirst().versionId);var req=r.request(previous.id);SigningRules.replaceable(req==null?null:req.state);
   if(previous.fileId==fileId&&title.equals(previous.title))return view(previous.id,actor.userId());
   links.getFirst().state="superseded";document=r.find(Document.class,previous.documentId);
  }else{
   document=new Document();document.code=UUID.randomUUID();document.module="MGHP";document.type=minutes?"bien_ban":"quyet_dinh";document.title=title;document.actor=actor.userId();document.createdAt=now();r.save(document);
  }
  var v=new DocumentVersion();v.documentId=document.id;v.revision=links.size()+1;v.title=title;v.fileId=fileId;v.businessReference=(minutes?"BB:":"QD:")+businessId;v.businessRevision=source.revision;v.dataset=source.hash;v.state="locked";v.lockedBy=actor.userId();v.lockedAt=now();v.actor=actor.userId();v.createdAt=now();r.save(v);
  var link=new Link();link.type=document.type;link.minutesId=minutes?businessId:null;link.decisionVersionId=minutes?null:businessId;link.versionId=v.id;link.state="current";link.createdAt=now();r.save(link);
  r.audit(actor.userId(),"SIGNING_DOCUMENT_BIND",v.id,reason);
  // Advance only the technical fence: the P10 dataset/revision stays immutable.
  r.touchSource(minutes,businessId);
  return view(v.id,actor.userId());
 }
 ApplicationReads.Detail read(AuthAccess.Principal principal,long id){var i=access.actor(principal);return view(id,i.userId());}
 ApplicationReads.Detail view(long id,long user){
  var v=r.find(DocumentVersion.class,id);access.requireRead(user,id);var f=files.describe(v.fileId);
  var request=r.request(id);
  var b=map("id",v.id,"documentId",v.documentId,"version",v.revision,"title",v.title,"datasetSha256",v.dataset,"createdAt",v.createdAt,"signingRequestId",request==null?null:request.id,"signingStatus",request==null?null:SigningSupportRules.publicStatus(request.state),
   "file",map("id",f.id(),"fileName",f.name(),"contentType","application/pdf","size",""+f.size(),"sha256",f.sha256(),"state",f.ready()?"ready":"pending"));
  b.put("documentType",r.find(Document.class,v.documentId).type);
  boolean current="current".equals(r.link(id).state);
  b.put("isCurrent",current);
  b.put("canPrepare",current&&request==null&&access.role(user,"chuyen_vien")&&r.minutesCompleted(r.link(id)));
  return new ApplicationReads.Detail(b,"\""+decisionReads.hash(List.of("P11/document",b))+"\"");
 }
}
