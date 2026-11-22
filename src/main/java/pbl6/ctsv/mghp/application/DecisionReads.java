package pbl6.ctsv.mghp.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import static pbl6.ctsv.mghp.application.DecisionEntities.*;
import static pbl6.ctsv.mghp.application.IntakeMapping.map;

@Component @ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class DecisionReads {
 final DecisionRepository r;final IntakeRepository intake;final ObjectMapper json;
 DecisionReads(DecisionRepository r,IntakeRepository intake,ObjectMapper json){this.r=r;this.intake=intake;this.json=json;}
 String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical(json.valueToTree(value)))));}catch(Exception e){throw new IllegalStateException(e);}}
 com.fasterxml.jackson.databind.JsonNode canonical(com.fasterxml.jackson.databind.JsonNode n){
  if(n.isObject()){var out=json.createObjectNode();var names=new TreeSet<String>();n.fieldNames().forEachRemaining(names::add);names.forEach(k->out.set(k,canonical(n.get(k))));return out;}
  if(n.isArray()){var out=json.createArrayNode();n.forEach(x->out.add(canonical(x)));return out;}return n;
 }
 ApplicationReads.Detail detail(Object value,byte[] fence){
  @SuppressWarnings("unchecked") var body=(Map<String,Object>)value;
  return new ApplicationReads.Detail(body,"\""+hash(List.of("P10/json",Base64.getEncoder().encodeToString(fence),body))+"\"");
 }
 Map<String,Object> header(Version v){return map("id",v.id,"decisionId",v.rootId,"revision",v.revision,"status",v.status,"stage",v.stage,"previousVersionId",v.previousId,
  "code",v.code,"number",v.number,"date",v.date,"issuingUnitId",v.unitId,"datasetSha256",v.hash,"sourceSha256",v.sourceHash,
  "reviewClosedAt",v.reviewClosedAt,"createdAt",v.createdAt,"createdBy",v.actor,"lockedAt",v.lockedAt,"lockedBy",v.lockedBy,"reason",v.reason,"lineCount",v.lineCount);}
 Map<String,Object> candidate(Candidate c){
  var m=map("id",c.id,"evaluationId",c.evaluationId,"processId",c.processId,"semesterId",c.semesterId,"scopeCode",c.scope,"rate",c.rate.toPlainString(),"eligiblePeriodId",c.periodId,"from",c.from,"to",c.to,"selection",c.selection,"reasonCode",c.reasonCode);
  m.putAll(IntakeMapping.wire(intake.row("SELECT e.phien_ban_chinh_sach_id policyVersionId,p.ten_tai_thoi_diem_chot policyName,p.uu_tien_khi_cung_muc tiePriority FROM dbo.mghp_danh_gia_dieu_kien e JOIN dbo.mghp_phien_ban_chinh_sach p ON p.id=e.phien_ban_chinh_sach_id WHERE e.id=?",c.evaluationId)));return m;
 }
 Map<String,Object> period(GrantedPeriod p){return map("id",p.id,"candidateId",p.candidateId,"from",p.from,"to",p.to,"rate",p.rate.toPlainString());}
 Map<String,Object> line(Line l){
  var m=map("id",l.id,"studentId",l.studentId,"processId",l.processId,"semesterId",l.semesterId,"scopeCode",l.scope,"policyVersionId",l.policyVersionId,
   "evaluationId",l.evaluationId,"submissionId",l.submissionId,"candidateId",l.candidateId,"studentCode",l.studentCode,"fullName",l.fullName,"classCode",l.classCode,"policyName",l.policyName,
   "rate",l.rate.toPlainString(),"operation",l.operation,"sha256",l.hash);
  m.put("periods",r.children(GrantedPeriod.class,"lineId",l.id).stream().map(this::period).toList());return m;
 }
 Map<String,Object> minutes(Version v){
  var rows=r.children(Minutes.class,"versionId",v.id);if(rows.isEmpty())return null;var m=rows.getFirst();
  return map("id",m.id,"code",m.code,"reference",m.reference,"meetingAt",m.meetingAt,"place",m.place,"datasetSha256",m.hash,"etag",detail(map("id",m.id,"hash",m.hash),m.fence).tag());
 }
 List<Map<String,Object>> documents(long version){
  return intake.rows("""
   SELECT l.id,l.loai_van_ban documentType,l.phien_ban_van_ban_dung_chung_id documentVersionId,l.trang_thai status
   FROM dbo.mghp_lien_ket_phien_ban_van_ban l
   WHERE l.phien_ban_qd_id=? OR l.bien_ban_id IN(SELECT id FROM dbo.mghp_bien_ban WHERE phien_ban_qd_nguon_id=?) ORDER BY l.id
   """,version,version).stream().map(IntakeMapping::wire).toList();
 }
 ApplicationReads.Detail version(long id){
  var v=r.find(Version.class,id);var b=header(v);
  b.put("candidates",candidatePage(id,0,20));b.put("lines",linePage(id,0,20));
  b.put("minutes",minutes(v));b.put("documents",documents(id));return detail(b,v.fence);
 }
 ApplicationReads.Detail root(long id){var root=r.find(Root.class,id);var b=map("id",root.id,"roundId",root.roundId,"type",root.type,"createdAt",root.createdAt);b.put("head",header(r.versions(id).getFirst()));return detail(b,root.fence);}
 ApplicationReads.Detail history(long root,int page,int size){var rr=r.find(Root.class,root);var vs=r.versions(root).stream().map(v->{var m=header(v);m.put("minutes",minutes(v));m.put("documents",documents(v.id));return m;}).toList();return detail(map("decisionId",root,"versions",page(vs,page,size)),rr.fence);}
 Map<String,Object> candidatePage(long id,int page,int size){return envelope(r.pageChildren(Candidate.class,"versionId",id,page,size).stream().map(this::candidate).toList(),r.countChildren(Candidate.class,"versionId",id),page,size);}
 Map<String,Object> linePage(long id,int page,int size){return envelope(r.pageChildren(Line.class,"versionId",id,page,size).stream().map(this::line).toList(),r.countChildren(Line.class,"versionId",id),page,size);}
 ApplicationReads.Detail children(long version,boolean candidates,int page,int size){var v=r.find(Version.class,version);return detail(candidates?candidatePage(version,page,size):linePage(version,page,size),v.fence);}
 static Map<String,Object> envelope(List<?> items,long total,int page,int size){var m=map("items",items,"page",page,"size",size,"hasMore",(long)page*size+items.size()<total);m.put("totalElements",total);return m;}
 static Map<String,Object> page(List<?> all,int page,int size){long from=(long)page*size;int start=(int)Math.min(from,all.size()),end=Math.min(start+size,all.size());return envelope(all.subList(start,end),all.size(),page,size);}
 ApplicationReads.Detail list(Long round,int page,int size){var items=r.roots(round,page,size).stream().map(x->{var m=header(r.versions(x.id).getFirst());m.put("roundId",x.roundId.toString());return m;}).toList();
  long total=r.countRoots(round);
  return detail(envelope(items,total,page,size),new byte[0]);
 }
}

