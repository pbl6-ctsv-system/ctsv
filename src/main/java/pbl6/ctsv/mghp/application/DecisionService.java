package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.mghp.configuration.IntakeConfiguration;
import static pbl6.ctsv.mghp.application.DecisionEntities.*;
import static pbl6.ctsv.mghp.application.IntakeRules.*;
import static pbl6.ctsv.mghp.application.IntakeProblem.invalid;
import static pbl6.ctsv.mghp.application.IntakeMapping.map;

@Service @ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class DecisionService {
 record Result(ApplicationReads.Detail detail,boolean created){}
 final DecisionRepository r;final DecisionReads reads;final DecisionSource source;final IntakeConfiguration config;
 final IntakeRepository intake;final EvaluationRepository evaluations;final Clock clock;
 DecisionService(DecisionRepository r,DecisionReads reads,DecisionSource source,IntakeConfiguration config,IntakeRepository intake,EvaluationRepository evaluations,Clock clock){
  this.r=r;this.reads=reads;this.source=source;this.config=config;this.intake=intake;this.evaluations=evaluations;this.clock=clock;
 }
 OffsetDateTime now(){return clock.instant().atOffset(ZoneOffset.UTC);}
 void unit(Long id){if(id!=null&&!r.unitExists(id))throw invalid("issuingUnitId");}
 Version head(Root root){return r.versions(root.id).getFirst();}
 void editable(Root root,Version v){if(!Objects.equals(head(root).id,v.id)||v.lockedAt!=null||!"draft".equals(v.status))throw IntakeProblem.conflict("IMMUTABLE");}
 void sourceStillValid(Root root,Version v){var current=source.ready(root.roundId);if(!Objects.equals(current.sha(),v.sourceHash)||!Objects.equals(current.closedAt(),v.reviewClosedAt))throw IntakeProblem.conflict("DECISION_NOT_READY");}
 Root lock(long version){config.lockWrites();var v=r.find(Version.class,version);var root=r.find(Root.class,v.rootId);config.lock(root.roundId);return r.lockRoot(root.id);}
 Result create(FoundationAccess.Identity actor,JsonNode body){
  shape(body,List.of("roundId","code","type","number","date","issuingUnitId"));
  long round=id(body.get("roundId"),"roundId");String code=text(body.get("code"),"code",128,true);
  if(!"initial".equals(text(body.get("type"),"type",128,true)))throw invalid("type");
  String number=text(body.get("number"),"number",128,false);LocalDate date=date(body.get("date"),"date",false);Long unit=nullableId(body,"issuingUnitId");unit(unit);
  config.lock(round);
  var existing=r.byRound(round);
  if(existing!=null){
   var first=r.versions(existing.id).getLast();
   if(!code.equals(first.code)||!Objects.equals(number,first.number)||!Objects.equals(date,first.date)||!Objects.equals(unit,first.unitId))throw IntakeProblem.conflict("DECISION_ALREADY_EXISTS");
   return new Result(reads.version(head(existing).id),false);
  }
  if(r.codeExists(code))throw IntakeProblem.conflict("DECISION_CODE_CONFLICT");
  var ready=source.ready(round);
  var root=new Root();root.roundId=round;root.code=code;root.type="initial";root.number=number;root.date=date;root.unitId=unit;root.actor=actor.userId();root.createdAt=now();r.save(root);
  var v=new Version();v.rootId=root.id;v.revision=1;v.status="draft";v.stage="before_meeting";v.actor=actor.userId();v.createdAt=now();v.code=code;v.number=number;v.date=date;v.unitId=unit;v.sourceHash=ready.sha();v.reviewClosedAt=ready.closedAt();
  // Hash is assigned before the first INSERT: all snapshots are append-only thereafter.
  v.hash=reads.hash(map("header",map("code",code,"number",number,"date",date,"issuingUnitId",unit),"source",ready.sha(),"candidates",ready.sources()));
  v.lineCount=(int)ready.winners().stream().map(s->List.of(s.process(),s.semester(),s.scope())).distinct().count();
  r.save(v);snapshot(v,ready);evaluations.audit(actor.userId(),"DECISION_CREATE","mghp_quyet_dinh",root.id,"Tạo toàn bộ danh sách đủ căn cứ");r.refresh(v);
  return new Result(reads.version(v.id),true);
 }
 void snapshot(Version v,DecisionSource.Ready ready){
  var candidateIds=new HashMap<Long,Long>();var winners=new HashSet<>(ready.winners().stream().map(DecisionRules.Source::period).toList());
  for(var s:ready.sources()){
   var c=new Candidate();c.versionId=v.id;c.evaluationId=s.evaluation();c.processId=s.process();c.semesterId=s.semester();c.scope=s.scope();c.rate=s.rate();c.from=s.from();c.to=s.to();c.periodId=s.period();
   c.selection=winners.contains(s.period())?"fully_selected":"not_selected";c.reasonCode=winners.contains(s.period())?"HIGHEST_M":"LOWER_RATE_OR_PRIORITY";r.save(c);candidateIds.put(s.period(),c.id);
  }
  var groups=new LinkedHashMap<String,List<DecisionRules.Source>>();
  for(var s:ready.winners())groups.computeIfAbsent(s.process()+"|"+s.semester()+"|"+s.scope(),x->new ArrayList<>()).add(s);
  for(var group:groups.values()){
   var s=group.getFirst();var e=intake.find(EvaluationEntities.Evaluation.class,s.evaluation());
   var submission=intake.find(ApplicationEntities.Submission.class,e.updateSubmissionId==null?e.submissionId:e.updateSubmissionId);
   var l=new Line();l.versionId=v.id;l.studentId=s.student();l.processId=s.process();l.semesterId=s.semester();l.policyVersionId=s.policyVersion();l.evaluationId=s.evaluation();l.benefitId=e.benefitId;l.scope=s.scope();l.rate=s.rate();l.operation="apply";l.submissionId=submission.id;l.candidateId=candidateIds.get(s.period());
   l.studentCode=submission.studentCode;l.fullName=submission.fullName;l.classCode=submission.classId==null?null:Objects.toString(intake.row("SELECT ma FROM dbo.lop_sinh_hoat WHERE id=?",submission.classId).get("ma"),null);
   l.policyName=Objects.toString(config.policy(s.policyVersion()).get("name"),null);l.startSemesterId=s.semester();l.endSemesterId=s.semester();l.from=group.getFirst().from();l.to=group.getLast().to();l.timeScope="verified_periods";
   l.hash=reads.hash(map("student",s.student(),"process",s.process(),"semester",s.semester(),"policy",s.policyVersion(),"evaluation",s.evaluation(),"periods",group,"studentCode",l.studentCode,"fullName",l.fullName,"classCode",l.classCode,"policyName",l.policyName));
   r.save(l);
   for(var p:group){var g=new GrantedPeriod();g.lineId=l.id;g.candidateId=candidateIds.get(p.period());g.from=p.from();g.to=p.to();g.rate=p.rate();r.save(g);}
  }
 }
 Result meeting(FoundationAccess.Identity actor,long id,JsonNode body,String tag){
  var keys=new ArrayList<>(List.of("minutesCode","reference","meetingAt","place","reason"));if(body!=null&&body.has("metadata"))keys.add("metadata");shape(body,keys);
  String code=text(body.get("minutesCode"),"minutesCode",128,true),ref=text(body.get("reference"),"reference",512,false),place=text(body.get("place"),"place",512,false),reason=text(body.get("reason"),"reason",4000,true);
  OffsetDateTime at;try{at=OffsetDateTime.parse(text(body.get("meetingAt"),"meetingAt",64,true)).withOffsetSameInstant(ZoneOffset.UTC);}catch(java.time.DateTimeException e){throw invalid("meetingAt");}
  if(at.getYear()<1||at.getYear()>9999||at.toInstant().isAfter(clock.instant()))throw invalid("meetingAt");
  String number=null;LocalDate date=null;Long unit=null;
  if(body.has("metadata")){var n=shape(body.get("metadata"),List.of("number","date","issuingUnitId"));number=text(n.get("number"),"number",128,false);date=date(n.get("date"),"date",false);unit=nullableId(n,"issuingUnitId");unit(unit);}
  var root=lock(id);var old=r.find(Version.class,id);ApplicationReads.matchTag(reads.version(id).tag(),tag);
  if(!body.has("metadata")){number=old.number;date=old.date;unit=old.unitId;}
  String commandHash=reads.hash(map("minutesCode",code,"reference",ref,"meetingAt",at,"place",place,"reason",reason,"metadata",map("number",number,"date",date,"issuingUnitId",unit)));
  var head=head(root);
  if(Objects.equals(head.previousId,id)&&Objects.equals(commandHash,head.meetingHash))return new Result(reads.version(head.id),false);
  editable(root,old);sourceStillValid(root,old);
  if(Objects.equals(old.meetingHash,commandHash))return new Result(reads.version(old.id),false);
  if(r.minutesCodeExists(code))throw IntakeProblem.conflict("MINUTES_CODE_CONFLICT");
  var v=new Version();v.rootId=root.id;v.revision=old.revision+1;v.status="draft";v.stage="after_meeting";v.previousId=id;v.reason=reason;v.actor=actor.userId();v.createdAt=now();
  v.code=old.code;v.number=body.has("metadata")?number:old.number;v.date=body.has("metadata")?date:old.date;v.unitId=body.has("metadata")?unit:old.unitId;v.sourceHash=old.sourceHash;v.reviewClosedAt=old.reviewClosedAt;v.meetingHash=commandHash;v.lineCount=old.lineCount;
  v.hash=reads.hash(map("previousDataset",old.hash,"header",map("code",v.code,"number",v.number,"date",v.date,"issuingUnitId",v.unitId),"meeting",map("code",code,"reference",ref,"at",at,"place",place)));
  r.save(v);cloneChildren(old,v);
  var m=new Minutes();m.roundId=root.roundId;m.code=code;m.reference=ref;m.meetingAt=at;m.place=place;m.status="draft";m.actor=actor.userId();m.createdAt=now();m.versionId=v.id;m.hash=v.hash;r.save(m);
  r.touch(root);evaluations.audit(actor.userId(),"DECISION_MEETING","mghp_phien_ban_qd",v.id,reason);r.refresh(v);
  return new Result(reads.version(v.id),true);
 }
 void cloneChildren(Version old,Version v){
  var candidates=new HashMap<Long,Long>();
  for(var c:r.children(Candidate.class,"versionId",old.id)){
   var n=new Candidate();n.versionId=v.id;n.evaluationId=c.evaluationId;n.processId=c.processId;n.semesterId=c.semesterId;n.scope=c.scope;n.rate=c.rate;n.from=c.from;n.to=c.to;n.selection=c.selection;n.reasonCode=c.reasonCode;n.reason=c.reason;n.periodId=c.periodId;r.save(n);candidates.put(c.id,n.id);
  }
  for(var l:r.children(Line.class,"versionId",old.id)){
   var n=new Line();n.versionId=v.id;n.studentId=l.studentId;n.processId=l.processId;n.semesterId=l.semesterId;n.policyVersionId=l.policyVersionId;n.benefitId=l.benefitId;n.evaluationId=l.evaluationId;n.scope=l.scope;n.rate=l.rate;n.operation=l.operation;n.hash=l.hash;n.submissionId=l.submissionId;n.candidateId=candidates.get(l.candidateId);n.studentCode=l.studentCode;n.fullName=l.fullName;n.classCode=l.classCode;n.policyName=l.policyName;n.startSemesterId=l.startSemesterId;n.endSemesterId=l.endSemesterId;n.from=l.from;n.to=l.to;n.timeScope=l.timeScope;r.save(n);
   for(var g:r.children(GrantedPeriod.class,"lineId",l.id)){var p=new GrantedPeriod();p.lineId=n.id;p.candidateId=candidates.get(g.candidateId);p.from=g.from;p.to=g.to;p.rate=g.rate;r.save(p);}
  }
 }
 ApplicationReads.Detail confirm(FoundationAccess.Identity actor,long id,JsonNode body,String tag){
  shape(body,List.of("reason"));String reason=text(body.get("reason"),"reason",4000,true);var root=lock(id);var v=r.find(Version.class,id);ApplicationReads.matchTag(reads.version(id).tag(),tag);
  if(Objects.equals(head(root).id,v.id)&&"locked".equals(v.status))return reads.version(id);
  editable(root,v);if(!"after_meeting".equals(v.stage)||r.children(Minutes.class,"versionId",id).size()!=1)throw IntakeProblem.conflict("DECISION_NOT_READY");
  sourceStillValid(root,v);var at=now();r.lockVersion(v,actor.userId(),at);r.councilClosed(root.roundId,actor.userId(),at);r.touch(root);evaluations.audit(actor.userId(),"DECISION_CONFIRM","mghp_phien_ban_qd",id,reason);
  return reads.version(id);
 }
}

