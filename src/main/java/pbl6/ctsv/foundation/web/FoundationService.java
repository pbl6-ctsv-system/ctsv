package pbl6.ctsv.foundation.web;
import pbl6.ctsv.auth.AuthAccess;
import java.sql.*;import java.time.*;import java.util.*;import java.util.function.*;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.*;import org.springframework.transaction.support.TransactionTemplate;

final class FoundationService {
 final JdbcTemplate jdbc; private final FoundationSecurity security; private final TransactionTemplate tx; private final ObjectMapper json; private final pbl6.ctsv.foundation.api.FoundationUsage usage;
 record View(Object body,String tag){}
 FoundationService(FoundationSecurity security,JdbcTemplate jdbc,PlatformTransactionManager manager,ObjectMapper json,pbl6.ctsv.foundation.api.FoundationUsage usage){
  this.security=security;this.jdbc=jdbc;this.json=json;this.usage=usage;tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
 }
 <T>T execute(AuthAccess.Principal p,boolean online,String capability,Function<Long,T> action){
  return tx.execute(s->action.apply(security.authorize(p,online,capability).userId()));
 }
 List<Map<String,Object>> rows(String sql,Object...args){
  return jdbc.query(sql,(r,n)->{
   var m=new LinkedHashMap<String,Object>();var md=r.getMetaData();
   for(int i=1;i<=md.getColumnCount();i++){
    Object v=r.getObject(i);if(v instanceof Long)v=v.toString();
    if(v instanceof java.sql.Date d)v=d.toLocalDate().toString();
    if(v instanceof OffsetDateTime t)v=t.toInstant().toString();
    if(v!=null&&v.getClass().getName().equals("microsoft.sql.DateTimeOffset"))v=r.getObject(i,OffsetDateTime.class).toInstant().toString();
    m.put(md.getColumnLabel(i),v);
   }return m;
  },args);
 }
 Map<String,Object> one(String sql,Object...args){var r=rows(sql,args);if(r.size()!=1)throw new FoundationFailure(404,"NOT_FOUND");return r.getFirst();}
 byte[] version(String table,long id){var values=jdbc.query("SELECT phien_ban FROM dbo."+table+" WITH(UPDLOCK,HOLDLOCK) WHERE id=?",(r,n)->r.getBytes(1),id);if(values.isEmpty())throw new FoundationFailure(404,"NOT_FOUND");return values.getFirst();}
 View view(Object body,byte[] version){try{
  var digest=MessageDigest.getInstance("SHA-256");digest.update(version);return new View(body,"\""+Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(json.writeValueAsBytes(body)))+"\"");
 }catch(Exception e){throw new IllegalStateException(e);}}
 void match(View view,String tag){if(tag==null)throw new FoundationFailure(428,"PRECONDITION_REQUIRED");if(!tag.matches("\"[A-Za-z0-9_-]+\""))throw FoundationFailure.invalid("If-Match");if(!view.tag.equals(tag))throw new FoundationFailure(412,"VERSION_CONFLICT");}
 void touch(String table,long id,byte[] version){
  if(jdbc.update("UPDATE dbo."+table+" SET thoi_diem_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00') WHERE id=? AND phien_ban=?",id,version)!=1)throw new FoundationFailure(412,"VERSION_CONFLICT");
 }
 void audit(long user,String action,String type,long id,String reason){
  jdbc.update("""
   INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,nguoi_dung_id,hanh_dong,loai_doi_tuong,doi_tuong_id,ly_do,thoi_diem_tao)
   VALUES(?,N'user',?,?,?,?,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
   """,UUID.randomUUID().toString(),user,action,type,""+id,reason);
 }
 static final String STUDENT="id,mssv studentCode,ho_ten fullName,lop_hien_tai_id currentClassId,khoa_quan_ly_hien_tai_id managementUnitId";
 Object students(String q,FoundationRules.PageInput p){
  String filter=" WHERE (? IS NULL OR mssv LIKE ? ESCAPE '~' OR ho_ten LIKE ? ESCAPE '~')";
  String like=like(q);
  var items=rows("SELECT "+STUDENT+" FROM dbo.sinh_vien"+filter+" ORDER BY mssv,id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",q,like,like,p.offset(),p.size());
  long count=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.sinh_vien"+filter,Long.class,q,like,like);
  return page(items,p,count);
 }
 Object student(long id){
  var m=one("SELECT "+STUDENT+",he_thong_nguon_id sourceSystemId,ma_dinh_danh_ben_ngoai sourceId,thoi_diem_dong_bo sourceObservedAt,trang_thai_chuan_hoa normalizationStatus FROM dbo.sinh_vien WHERE id=?",id);
  m.put("studyProcesses",rows("""
   SELECT id,ma_qua_trinh_hoc code,chuong_trinh_dao_tao_id programId,khoa_hoc_id cohortId,hoc_ky_bat_dau_id startSemesterId,
     trang_thai_doi_chieu verificationStatus,can_cu_xac_minh evidence FROM dbo.qua_trinh_theo_hoc WHERE sinh_vien_id=? ORDER BY id
   """,id));return m;
 }
 Object lookup(String kind,String q,FoundationRules.PageInput p){
  String table=switch(kind){case "ethnicities"->"dan_toc";case "cohorts"->"khoa_hoc";case "classes"->"lop_sinh_hoat";case "units"->"don_vi";case "semesters"->"hoc_ky";default->throw FoundationFailure.invalid("kind");};
  String name=kind.equals("cohorts")?"CAST(NULL AS nvarchar(512))":"ten";
  String filter=" WHERE (? IS NULL OR ma LIKE ? ESCAPE '~' OR "+name+" LIKE ? ESCAPE '~')";
  var items=rows("SELECT id,ma code,"+name+" name FROM dbo."+table+filter+" ORDER BY CASE WHEN ma IS NULL THEN 1 ELSE 0 END,ma,id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",q,like(q),like(q),p.offset(),p.size());
  long count=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo."+table+filter,Long.class,q,like(q),like(q));
  return page(items,p,count);
 }
 private String like(String q){return q==null?null:"%"+q.replace("~","~~").replace("%","~%").replace("_","~_").replace("[","~[")+"%";}
 private Object page(Object items,FoundationRules.PageInput p,long count){return Map.of("items",items,"page",p.page(),"size",p.size(),"totalElements",count);}
 View semester(long id){var version=version("hoc_ky",id);return view(one("""
  SELECT id,ma code,ten name,so_thu_tu [order],ngay_bat_dau startDate,ngay_ket_thuc endDate,
    trang_thai_xac_minh_moc verificationStatus,can_cu_xac_minh_moc evidence FROM dbo.hoc_ky WHERE id=?
  """,id),version);}
 View verifySemester(long actor,long id,String tag,JsonNode b){
  var current=semester(id);match(current,tag);
  if(!b.path("order").isIntegralNumber()||!b.path("order").canConvertToInt()||b.path("order").intValue()<1)throw FoundationFailure.invalid("order");
  var from=date(b,"startDate",false);var until=date(b,"endDate",false);FoundationRules.dates(from,until);
  String evidence=text(b,"evidence",16000,false);
  var old=(Map<?,?>)current.body;
  boolean changed=!Objects.equals(old.get("order"),b.get("order").intValue())||!Objects.equals(old.get("startDate"),from.toString())||!Objects.equals(old.get("endDate"),until.toString());
  if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.hoc_ky WITH(UPDLOCK,HOLDLOCK) WHERE so_thu_tu=? AND id<>?",Integer.class,b.get("order").intValue(),id)>0)throw new FoundationFailure(409,"SEMESTER_VERIFICATION_CONFLICT");
  if(changed&&semesterUsed(id))throw new FoundationFailure(409,"SEMESTER_VERIFICATION_CONFLICT");
  if(!changed&&Objects.equals(old.get("evidence"),evidence)&&Objects.equals(old.get("verificationStatus"),"verified"))return current;
  var v=version("hoc_ky",id);
  if(jdbc.update("UPDATE dbo.hoc_ky SET so_thu_tu=?,ngay_bat_dau=?,ngay_ket_thuc=?,can_cu_xac_minh_moc=?,trang_thai_xac_minh_moc='verified' WHERE id=? AND phien_ban=?",b.get("order").intValue(),from,until,evidence,id,v)!=1)throw new FoundationFailure(412,"VERSION_CONFLICT");
  audit(actor,"SEMESTER_VERIFIED","hoc_ky",id,evidence);return semester(id);
 }
 boolean semesterUsed(long id){
  return usage.semesterUsed(id);
 }
 View process(long id){
  var v=version("qua_trinh_theo_hoc",id);
  var m=one("""
   SELECT id processId,ma_qua_trinh_hoc code,chuong_trinh_dao_tao_id programId,khoa_hoc_id cohortId,
    hoc_ky_bat_dau_id startSemesterId,hoc_ky_ket_thuc_nguon_id endSemesterId,thoi_diem_bat_dau actualStart,
    ngay_ket_thuc_du_kien expectedEnd,ngay_ket_thuc_thuc_te actualEnd,can_cu_xac_minh evidence,tep_can_cu_id evidenceFileId,
    trang_thai_doi_chieu verificationStatus FROM dbo.qua_trinh_theo_hoc WHERE id=?
   """,id);
  m.put("statusHistory",rows("""
   SELECT id,hoc_ky_ap_dung_id semesterId,trang_thai status,ngay_bat_dau_hieu_luc [from],ngay_ket_thuc_hieu_luc [to],
    so_qd_nguon decisionNumber,ngay_qd_nguon decisionDate,can_cu_xac_minh evidence,thoi_diem_xac_minh verifiedAt
   FROM dbo.lich_su_trang_thai_sv WHERE qua_trinh_hoc_id=? ORDER BY ngay_bat_dau_hieu_luc,id
   """,id));
  m.put("classHistory",rows("""
   SELECT id,lop_id classId,hieu_luc_tu [from],hieu_luc_den until,can_cu_xac_minh evidence,thoi_diem_xac_minh verifiedAt
   FROM dbo.qua_trinh_thuoc_lop WHERE qua_trinh_hoc_id=? ORDER BY hieu_luc_tu,id
   """,id));return view(m,v);
 }
 void fk(String table,Long id){if(id!=null&&jdbc.queryForObject("SELECT COUNT(*) FROM dbo."+table+" WHERE id=?",Integer.class,id)==0)throw FoundationFailure.invalid("id");}
 View verifyProcess(long actor,long id,String tag,JsonNode b){
  var current=process(id);match(current,tag);var v=version("qua_trinh_theo_hoc",id);
  Long program=optionalId(b,"programId"),cohort=optionalId(b,"cohortId"),start=optionalId(b,"startSemesterId"),end=optionalId(b,"endSemesterId"),file=optionalId(b,"evidenceFileId");
  fk("chuong_trinh_dao_tao",program);fk("khoa_hoc",cohort);fk("hoc_ky",start);fk("hoc_ky",end);fk("tep_tin",file);
  var from=date(b,"actualStart",true);var expected=date(b,"expectedEnd",true);var actual=date(b,"actualEnd",true);
  if(from!=null){FoundationRules.dates(from,expected);FoundationRules.dates(from,actual);}
  String evidence=text(b,"evidence",16000,false);
  if(processUsed(id))throw new FoundationFailure(409,"HISTORY_IN_USE");
  String state=program!=null&&cohort!=null&&start!=null&&from!=null?"verified":"partial";
  int changed=jdbc.update("""
   UPDATE dbo.qua_trinh_theo_hoc SET chuong_trinh_dao_tao_id=?,khoa_hoc_id=?,hoc_ky_bat_dau_id=?,hoc_ky_ket_thuc_nguon_id=?,
    thoi_diem_bat_dau=?,ngay_ket_thuc_du_kien=?,ngay_ket_thuc_thuc_te=?,can_cu_xac_minh=?,tep_can_cu_id=?,trang_thai_doi_chieu=?,
    nguoi_xac_minh_id=?,thoi_diem_xac_minh=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00') WHERE id=? AND phien_ban=?
   """,program,cohort,start,end,from,expected,actual,evidence,file,state,actor,id,v);
  if(changed!=1)throw new FoundationFailure(412,"VERSION_CONFLICT");
  audit(actor,"PROCESS_VERIFIED","qua_trinh_theo_hoc",id,evidence);return process(id);
 }
 boolean processUsed(long id){return usage.processUsed(id);}
 View history(long actor,long id,String tag,JsonNode b,boolean status){
  var current=process(id);match(current,tag);var v=version("qua_trinh_theo_hoc",id);
  if(processUsed(id))throw new FoundationFailure(409,"HISTORY_IN_USE");
  LocalDate from=date(b,"from",false),end=date(b,status?"to":"until",true);FoundationRules.dates(from,end);
  String evidence=text(b,"evidence",16000,false);
  long reference=FoundationRules.id(text(b,status?"semesterId":"classId",19,false));fk(status?"hoc_ky":"lop_sinh_hoat",reference);
  String table=status?"lich_su_trang_thai_sv":"qua_trinh_thuoc_lop",startColumn=status?"ngay_bat_dau_hieu_luc":"hieu_luc_tu",endColumn=status?"ngay_ket_thuc_hieu_luc":"hieu_luc_den";
  if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo."+table+" WITH(UPDLOCK,HOLDLOCK) WHERE qua_trinh_hoc_id=? AND ("+endColumn+" IS NULL OR "+endColumn+">=?) AND (? IS NULL OR "+startColumn+"<=?)",Integer.class,id,from,end,end)>0)throw new FoundationFailure(409,"HISTORY_CONFLICT");
  if(status)jdbc.update("""
   INSERT dbo.lich_su_trang_thai_sv(qua_trinh_hoc_id,hoc_ky_ap_dung_id,trang_thai,ngay_bat_dau_hieu_luc,ngay_ket_thuc_hieu_luc,so_qd_nguon,ngay_qd_nguon,can_cu_xac_minh,nguoi_xac_minh_id,thoi_diem_xac_minh)
   VALUES(?,?,?,?,?,?,?,?,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
   """,id,reference,text(b,"status",128,false),from,end,text(b,"decisionNumber",512,true),date(b,"decisionDate",true),evidence,actor);
  else jdbc.update("""
   INSERT dbo.qua_trinh_thuoc_lop(qua_trinh_hoc_id,lop_id,hieu_luc_tu,hieu_luc_den,can_cu_xac_minh,nguoi_xac_minh_id,thoi_diem_xac_minh)
   VALUES(?,?,?,?,?,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
   """,id,reference,from,end,evidence,actor);
  touch("qua_trinh_theo_hoc",id,v);audit(actor,"HISTORY_ADDED",table,id,evidence);return process(id);
 }
 View roles(long user){
  var v=version("nguoi_dung",user);
  var items=rows("""
    SELECT p.id assignmentId,r.id roleId,r.ma roleCode,p.ma_phan_he module,p.don_vi_id unitId,p.hieu_luc_tu effectiveFrom,p.hieu_luc_den effectiveUntil
    FROM dbo.phan_quyen_nguoi_dung p JOIN dbo.vai_tro r ON r.id=p.vai_tro_id
    WHERE p.nguoi_dung_id=? AND p.ma_phan_he='MGHP' AND r.dang_hoat_dong=1
    AND (p.hieu_luc_tu IS NULL OR p.hieu_luc_tu<=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))
    AND (p.hieu_luc_den IS NULL OR p.hieu_luc_den>TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')) ORDER BY r.id,p.id
    """,user);
  return view(Map.of("userId",""+user,"roles",items),v);
 }
 View role(long actor,long user,long role,String tag,boolean grant){
  var current=roles(user);match(current,tag);var v=version("nguoi_dung",user);
  if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.vai_tro WHERE id=? AND dang_hoat_dong=1 AND ma IN ('mghp_chuyen_vien','mghp_pho_phong')",Integer.class,role)==0)throw new FoundationFailure(409,"ROLE_NOT_APPLICABLE");
  int active=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.phan_quyen_nguoi_dung WHERE nguoi_dung_id=? AND vai_tro_id=? AND ma_phan_he='MGHP' AND (hieu_luc_den IS NULL OR hieu_luc_den>TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))",Integer.class,user,role);
  if(grant==(active>0))return current;
  if(grant)jdbc.update("INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he,nguoi_cap_quyen_id,thoi_diem_tao) VALUES(?,?,'MGHP',?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))",user,role,actor);
  else jdbc.update("UPDATE dbo.phan_quyen_nguoi_dung SET hieu_luc_den=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00') WHERE nguoi_dung_id=? AND vai_tro_id=? AND ma_phan_he='MGHP' AND (hieu_luc_den IS NULL OR hieu_luc_den>TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))",user,role);
  touch("nguoi_dung",user,v);audit(actor,grant?"MGHP_ROLE_GRANTED":"MGHP_ROLE_REVOKED","nguoi_dung",user,null);return roles(user);
 }
 static String text(JsonNode b,String key,int max,boolean nullable){
  var n=b.get(key);if(nullable&&n!=null&&n.isNull())return null;
  if(n==null||!n.isTextual()||n.asText().isBlank()||n.asText().length()>max)throw FoundationFailure.invalid(key);return n.asText();
 }
 static Long optionalId(JsonNode b,String key){return b.get(key).isNull()?null:FoundationRules.id(text(b,key,19,false));}
 static LocalDate date(JsonNode b,String key,boolean nullable){
  String s=text(b,key,10,nullable);if(s==null)return null;
  try{if(!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw FoundationFailure.invalid(key);return LocalDate.parse(s);}catch(java.time.DateTimeException e){throw FoundationFailure.invalid(key);}
 }
}
