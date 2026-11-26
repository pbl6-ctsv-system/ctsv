package pbl6.ctsv.mghp.application;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pbl6.ctsv.shared.files.ManagedFiles;
import static pbl6.ctsv.mghp.application.SigningEntities.*;
import static pbl6.ctsv.mghp.application.IntakeMapping.map;

@Component @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningReads {
 final SigningSupportRepository support;final SigningRepository r;final SigningAccess access;final SigningDocuments docs;final ManagedFiles files;final DecisionReads hashing;
 SigningReads(SigningRepository r,SigningAccess access,SigningDocuments docs,ManagedFiles files,DecisionReads hashing,SigningSupportRepository support){this.support=support;this.r=r;this.access=access;this.docs=docs;this.files=files;this.hashing=hashing;}
 Map<String,Object> file(long id){var f=files.describe(id);return map("id",f.id(),"fileName",f.name(),"contentType","application/pdf","size",""+f.size(),"sha256",f.sha256(),"state",f.ready()?"ready":"pending");}
 Step current(Request q){return r.steps(q.id).stream().filter(s->!"completed".equals(s.state)).findFirst().orElse(null);}
 boolean mayAct(Request q,Step step,long user){return step!=null&&Objects.equals(step.userId,user)&&access.role(user,step.role)&&"waiting".equals(step.state)&&Set.of("prepared","waiting_next").contains(q.state)&&"current".equals(r.link(q.versionId).state)&&r.minutesCompleted(r.link(q.versionId))&&r.authorized(step);}
 ApplicationReads.Detail request(long id,long user){
  var q=r.find(Request.class,id);access.requireRead(user,q.versionId);var current=current(q);var actions=new ArrayList<String>();
  if(mayAct(q,current,user))actions.add("sign");

  var steps=r.steps(id).stream().map(s->{var out=r.output(s.id);return map("id",s.id,"order",s.order,"role",s.role,"status",SigningSupportRules.publicStatus(s.state),"signerName",s.name,"signerEmail",s.email,"outputFile",out==null?null:file(out.file()),"receiptId",out==null?null:out.receipt(),"signedAt",out==null?null:out.at(),"reason",s.reason,"verificationLevel",out==null?"not_verified":"cryptographic_verified");}).toList();
  var b=map("id",q.id,"code",q.code,"documentVersionId",q.versionId,"status",SigningSupportRules.publicStatus(q.state),"phase",Set.of("failed","unknown").contains(q.state)?"support":r.phase(q.id),"mode",q.mode,"provider","CA_DUT","startedAt",q.startedAt,"completedAt",q.completedAt,"supportMessage",Set.of("failed","unknown").contains(q.state)?support.publicMessage(q.id):null,"document",docs.view(q.versionId,user).body(),"inputFile",file(r.input(q)),"currentStep",current==null?null:current.order,"steps",steps,"allowedActions",actions,"history",r.history(q.versionId));
  b.putAll(r.context(q.versionId));
  return new ApplicationReads.Detail(b,"\""+hashing.hash(List.of("P11/request",Base64.getEncoder().encodeToString(q.fence),b))+"\"");
 }
 ApplicationReads.Detail list(long user,String status,boolean mine,int page,int size,String view){
  boolean cv=access.role(user,"chuyen_vien"),pp=access.role(user,"pho_phong"),pht=access.role(user,"pho_hieu_truong");
  if(!cv&&!pp&&!pht)throw new IntakeProblem(403,"FORBIDDEN","request");
  var items=r.visibleRows(user,cv,pp,pht,mine,status,page,size,view).stream().map(row->{
   var item=new LinkedHashMap<>(row);item.put("status",SigningSupportRules.publicStatus((String)item.get("status")));if("needs_support".equals(item.get("status")))item.put("supportMessage",SigningFailurePolicy.SUPPORT);item.put("inputFile",map("id",item.remove("inputFileId"),"fileName",item.remove("inputFileName"),"size",item.remove("inputFileSize"),"sha256",item.remove("inputFileHash"),"contentType","application/pdf","state","ready"));
   boolean capable="chuyen_vien".equals(item.get("waitingForRole"))?cv:"pho_phong".equals(item.get("waitingForRole"))?pp:pht;
   item.put("allowedActions",((Number)item.remove("canSign")).intValue()==1&&capable?List.of("sign"):List.of());
   try{var progress=new com.fasterxml.jackson.databind.ObjectMapper().readTree((String)item.remove("progressJson"));for(var step:progress)if(step.isObject())((com.fasterxml.jackson.databind.node.ObjectNode)step).put("status",SigningSupportRules.publicStatus(step.path("status").asText()));item.put("progress",progress);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}return item;
  }).toList();
  long total=r.visibleCount(user,cv,pp,pht,mine,status,view);var b=map("items",items,"page",page,"size",size,"totalElements",total,"hasMore",(long)page*size+items.size()<total);
  return new ApplicationReads.Detail(b,"\""+hashing.hash(List.of("P11/list",user,b))+"\"");
 }
}
