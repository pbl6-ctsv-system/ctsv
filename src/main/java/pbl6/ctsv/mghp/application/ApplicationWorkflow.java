package pbl6.ctsv.mghp.application;

import java.util.*;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import static pbl6.ctsv.mghp.application.IntakeMapping.map;

/** SQL read projection: filtering/counting happens before paging; never mutates closed source. */
@Repository @ConditionalOnProperty(name="ctsv.mghp.applications.enabled",havingValue="true")
class ApplicationWorkflow {
 private final IntakeRepository repo;
 ApplicationWorkflow(IntakeRepository repo){this.repo=repo;}
 private static final String FROM="""
  FROM dbo.mghp_ho_so h
  OUTER APPLY (SELECT TOP(1) x.id,x.ket_qua result,x.ly_do_ket_luan reviewReason,x.dot_xet_id roundId
    FROM dbo.mghp_danh_gia_dieu_kien x WHERE x.ho_so_nguon_id=h.id
    AND x.trang_thai_xu_ly=N'confirmed' AND x.la_ban_hien_hanh=1
    AND x.phien_ban_ho_so_nguon_id=(SELECT TOP(1) s.id FROM dbo.mghp_phien_ban_ho_so s WHERE s.ho_so_id=h.id ORDER BY s.lan_sua_doi DESC,s.id DESC)
    ORDER BY x.id DESC) e
  JOIN dbo.mghp_dot_xet d ON d.id=COALESCE(e.roundId,(SELECT TOP(1) s.dot_tiep_nhan_id FROM dbo.mghp_phien_ban_ho_so s WHERE s.ho_so_id=h.id ORDER BY s.lan_sua_doi DESC,s.id DESC),h.dot_xet_id)
  OUTER APPLY (SELECT TOP(1) c.trang_thai_lua_chon selection,
    CASE WHEN EXISTS(SELECT 1 FROM dbo.mghp_cong_bo publication WHERE publication.phien_ban_qd_id=v.id AND publication.thoi_diem_cong_bo IS NOT NULL) THEN 1 ELSE 0 END published
    FROM dbo.mghp_ung_vien_phien_ban_qd c JOIN dbo.mghp_phien_ban_qd v ON v.id=c.phien_ban_qd_id
    WHERE c.danh_gia_dieu_kien_id=e.id AND v.trang_thai=N'locked'
    AND NOT EXISTS(SELECT 1 FROM dbo.mghp_phien_ban_qd n WHERE n.quyet_dinh_id=v.quyet_dinh_id AND n.lan_sua_doi>v.lan_sua_doi)
    ORDER BY v.id DESC,c.id) c
  OUTER APPLY (SELECT CASE WHEN EXISTS(SELECT 1 FROM dbo.mghp_danh_gia_dieu_kien x
    WHERE x.ho_so_nguon_id=h.id AND x.trang_thai_xu_ly=N'draft'
    AND x.phien_ban_ho_so_nguon_id=(SELECT TOP(1) s.id FROM dbo.mghp_phien_ban_ho_so s WHERE s.ho_so_id=h.id ORDER BY s.lan_sua_doi DESC,s.id DESC)) THEN 1 ELSE 0 END draft) progress
  OUTER APPLY (SELECT TOP(1) a.ly_do intakeReason FROM dbo.mghp_nhat_ky_ho_so a
    WHERE a.ho_so_id=h.id AND a.hanh_dong=N'reject_application' ORDER BY a.id DESC) rejection
  """;
 private static final String VALUE="""
  CASE WHEN h.trang_thai IN(N'rejected',N'withdrawn') THEN h.trang_thai
   WHEN h.trang_thai=N'approved' AND e.result=N'khong_dat' THEN N'rejected'
   WHEN h.trang_thai=N'approved' AND e.result=N'dat' AND progress.draft=0 THEN N'approved'
   WHEN h.trang_thai=N'supplementing' THEN N'supplementing'
   WHEN h.trang_thai=N'submitted' AND d.trang_thai=N'open' THEN N'submitted'
   WHEN h.trang_thai IN(N'submitted',N'processing',N'supplement_responded',N'approved',N'reopened') THEN N'processing' ELSE NULL END
  """;
 private static final String ONLINE="CASE WHEN h.trang_thai IN(N'submitted',N'processing',N'reopened') THEN N'pending' WHEN h.trang_thai IN(N'supplementing',N'supplement_responded',N'approved',N'rejected',N'withdrawn') THEN h.trang_thai ELSE N'unspecified' END";
 private static final String PAPER="CASE WHEN e.result=N'khong_dat' THEN N'rejected' WHEN e.result=N'dat' AND progress.draft=0 THEN N'approved' WHEN progress.draft=1 OR e.id IS NOT NULL THEN N'in_progress' ELSE N'pending' END";
 private static final String COLUMNS="h.id,h.trang_thai intake,e.result,e.reviewReason,rejection.intakeReason,progress.draft,d.thoi_diem_dong_xet closedAt,d.thoi_diem_chot_online onlineClosedAt,d.trang_thai roundStatus,c.selection,c.published ";
 Map<String,Object> fields(Map<String,Object> facts){
  String intake=(String)facts.get("intake"),result=(String)facts.get("result");
  boolean sealed=facts.get("onlineClosedAt")!=null||facts.get("closedAt")!=null;
  boolean draft=facts.get("draft") instanceof Number n&&n.intValue()==1;
  String online=intake==null?"unspecified":switch(intake){
   case "submitted","processing","reopened"->"pending";
   case "supplementing","supplement_responded","approved","rejected","withdrawn"->intake;
   default->"unspecified";};
  String paper=!"approved".equals(online)?"not_transferred":"khong_dat".equals(result)?"rejected":
   "dat".equals(result)&&!draft?"approved":!sealed?"not_transferred":draft||result!=null?"in_progress":"pending";
  String status="rejected".equals(online)||"withdrawn".equals(online)?online:
   "rejected".equals(paper)?"rejected":"approved".equals(paper)?"approved":
   "supplementing".equals(online)?"supplementing":
   "submitted".equals(intake)&&"open".equals(facts.get("roundStatus"))?"submitted":"unspecified".equals(online)?null:"processing";
  String rejectionStage="rejected".equals(status)?("rejected".equals(online)?"online":"hard_copy"):null;
  return map("status",status,"workflowStatus",status,"onlineStatus",online,"hardCopyStatus",paper,
   "currentStage",Set.of("rejected","withdrawn").contains(Objects.toString(status,""))?"ended":"approved".equals(paper)?"council":sealed&&"approved".equals(online)?"hard_copy":"online",
   "councilResult","fully_selected".equals(facts.get("selection"))?"selected":"not_selected".equals(facts.get("selection"))?"not_selected":null,
   "rejectionStage",rejectionStage,"rejectionReason",rejectionStage==null?null:facts.get("online".equals(rejectionStage)?"intakeReason":"reviewReason"),
   "intakeSealed",sealed,
   "evaluationAllowed",ReviewLifecycleRules.canEvaluate(intake,"khong_dat".equals(result))&&facts.get("onlineClosedAt")!=null&&facts.get("closedAt")==null&&!"completed".equals(facts.get("roundStatus")));
 }
 Map<String,Object> fields(long id){return fields(repo.row("SELECT "+COLUMNS+FROM+" WHERE h.id=?",id));}
 Map<String,Object> fields(long id,boolean student){return fields(repo.row("SELECT "+COLUMNS+FROM+" WHERE h.id=?",id),student);}
 Map<String,Object> fields(Map<String,Object> facts,boolean student){
  var result=fields(facts);
  if(student){
   if(!(facts.get("published") instanceof Number n&&n.intValue()==1))result.put("councilResult",null);
   result.put("evaluationAllowed",false);
   result.remove("intakeSealed");
  }
  return result;
 }
 record Page(List<Map<String,Object>> facts,long count){}
 Page page(Long student,Long round,Long semester,String status,int page,int size){
  return page(student,round,semester,status,page,size,false);
 }
 Page page(Long student,Long round,Long semester,String status,int page,int size,boolean hardCopy){
  return page(student,round,semester,status,page,size,hardCopy?"hard-copy":null);
 }
 Page page(Long student,Long round,Long semester,String status,int page,int size,String stage){
  String where=" WHERE 1=1";var args=new ArrayList<Object>();
  if(student!=null){where+=" AND h.sinh_vien_id=?";args.add(student);}
  if(round!=null){where+=" AND d.id=?";args.add(round);}
  if(semester!=null){where+=" AND d.hoc_ky_id=?";args.add(semester);}
  boolean hardCopy="hard-copy".equals(stage);
  if(hardCopy)where+=" AND h.trang_thai=N'approved' AND (d.thoi_diem_chot_online IS NOT NULL OR d.thoi_diem_dong_xet IS NOT NULL)";
  String visibleStatus="("+(hardCopy?PAPER:"online".equals(stage)?ONLINE:VALUE)+")";
  if("UNSPECIFIED".equals(status))where+=" AND "+visibleStatus+("online".equals(stage)?"=N'unspecified'":" IS NULL");
  else if(hardCopy&&"pending".equals(status))where+=" AND "+visibleStatus+" IN(N'pending',N'in_progress')";
  else if(status!=null){where+=" AND "+visibleStatus+"=?";args.add(status);}
  long count=((Number)repo.row("SELECT COUNT_BIG(*) total "+FROM+where,args.toArray()).get("total")).longValue();
  args.add((long)page*size);args.add(size);
  return new Page(repo.rows("SELECT "+COLUMNS+FROM+where+" ORDER BY h.thoi_diem_cap_nhat DESC,h.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray()),count);
 }
}
