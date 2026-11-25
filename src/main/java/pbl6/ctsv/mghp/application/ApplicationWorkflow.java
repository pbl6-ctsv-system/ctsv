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
  OUTER APPLY (SELECT TOP(1) x.id,x.ket_qua result,x.dot_xet_id roundId
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
  """;
 private static final String VALUE="""
  CASE WHEN h.trang_thai IN(N'rejected',N'withdrawn') THEN h.trang_thai
   WHEN c.selection=N'fully_selected' THEN N'selected' WHEN c.selection=N'not_selected' THEN N'not_selected'
   WHEN e.result=N'khong_dat' THEN N'hard_copy_failed'
   WHEN e.result=N'dat' AND d.thoi_diem_dong_xet IS NOT NULL THEN N'awaiting_council'
   WHEN e.result=N'dat' THEN N'approved_v2'
   WHEN h.trang_thai=N'approved' THEN N'approved_v1' ELSE h.trang_thai END
  """;
 private static final String COLUMNS="h.id,h.trang_thai intake,e.result,d.thoi_diem_dong_xet closedAt,d.thoi_diem_chot_online onlineClosedAt,d.trang_thai roundStatus,c.selection,c.published ";
 private static final String PUBLIC_VALUE="CASE WHEN COALESCE(c.published,0)=0 AND ("+VALUE+") IN(N'selected',N'not_selected') THEN N'awaiting_council' ELSE ("+VALUE+") END";
 Map<String,Object> fields(Map<String,Object> facts){
  String intake=(String)facts.get("intake"),result=(String)facts.get("result");
  return map("workflowStatus",ReviewLifecycleRules.status(intake,result,facts.get("closedAt")!=null,(String)facts.get("selection")),
   "intakeSealed",facts.get("onlineClosedAt")!=null||facts.get("closedAt")!=null,
   "evaluationAllowed",ReviewLifecycleRules.canEvaluate(intake,"khong_dat".equals(result))&&facts.get("onlineClosedAt")!=null&&facts.get("closedAt")==null&&!"completed".equals(facts.get("roundStatus")));
 }
 Map<String,Object> fields(long id){return fields(repo.row("SELECT "+COLUMNS+FROM+" WHERE h.id=?",id));}
 Map<String,Object> fields(long id,boolean student){return fields(repo.row("SELECT "+COLUMNS+FROM+" WHERE h.id=?",id),student);}
 Map<String,Object> fields(Map<String,Object> facts,boolean student){
  var result=fields(facts);
  if(student){
   if(!Integer.valueOf(1).equals(facts.get("published"))&&Set.of("selected","not_selected").contains(Objects.toString(result.get("workflowStatus"),"")))result.put("workflowStatus","awaiting_council");
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
  String where=" WHERE 1=1";var args=new ArrayList<Object>();
  if(student!=null){where+=" AND h.sinh_vien_id=?";args.add(student);}
  if(round!=null){where+=" AND d.id=?";args.add(round);}
  if(semester!=null){where+=" AND d.hoc_ky_id=?";args.add(semester);}
  if(hardCopy||"approved".equals(status))where+=" AND h.trang_thai=N'approved' AND (d.thoi_diem_chot_online IS NOT NULL OR d.thoi_diem_dong_xet IS NOT NULL)";
  String visibleStatus="("+(student!=null?PUBLIC_VALUE:VALUE)+")";
  if("UNSPECIFIED".equals(status))where+=" AND "+visibleStatus+" IS NULL";
  // The approved alias is used by the review inbox for the whole online-approved pool.
  // Every visible status tab must filter the same projection as its displayed badge.
  else if(status!=null){where+=" AND "+("approved".equals(status)?"h.trang_thai":visibleStatus)+"=?";args.add(status);}
  long count=((Number)repo.row("SELECT COUNT_BIG(*) total "+FROM+where,args.toArray()).get("total")).longValue();
  args.add((long)page*size);args.add(size);
  return new Page(repo.rows("SELECT "+COLUMNS+FROM+where+" ORDER BY h.thoi_diem_cap_nhat DESC,h.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray()),count);
 }
}
