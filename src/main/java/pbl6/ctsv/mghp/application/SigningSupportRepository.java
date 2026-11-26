package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static pbl6.ctsv.mghp.application.SigningSupportEntities.*;

@Repository @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningSupportRepository {
 @jakarta.persistence.PersistenceContext EntityManager em;
 final JdbcTemplate jdbc;final Clock clock;
 final ObjectMapper json=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
 SigningSupportRepository(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
 Long documentVersion(long attempt){return jdbc.queryForObject("SELECT q.phien_ban_van_ban_id FROM dbo.lan_goi_dich_vu_ky a JOIN dbo.yeu_cau_ky q ON q.id=a.yeu_cau_ky_id WHERE a.id=?",Long.class,attempt);}
 String publicMessage(long request){
  var ids=jdbc.queryForList("SELECT TOP(1) i.id FROM dbo.mghp_su_co_ky i JOIN dbo.lan_goi_dich_vu_ky a ON a.id=i.lan_goi_id WHERE a.yeu_cau_ky_id=? ORDER BY i.id DESC",Long.class,request);
  if(ids.isEmpty())return SigningFailurePolicy.SUPPORT;var error=snapshot(find(Incident.class,ids.getFirst())).error();
  return error.userCorrectable()?error.publicMessage()+" "+SigningFailurePolicy.SUPPORT:SigningFailurePolicy.SUPPORT;
 }
 boolean completedAttempt(long attempt){return jdbc.queryForObject("SELECT IIF(EXISTS(SELECT 1 FROM dbo.lan_goi_dich_vu_ky WHERE id=? AND giai_doan_xu_ly=N'completed' AND tep_dau_ra_id IS NOT NULL),1,0)",Integer.class,attempt)==1;}
 OffsetDateTime now(){return clock.instant().atOffset(ZoneOffset.UTC);}
 <T extends ApplicationEntities.Row>T save(T row){em.persist(row);em.flush();if(row instanceof Fenced)em.refresh(row);return row;}
 <T>T find(Class<T> type,long id){var row=em.find(type,id);if(row==null)throw IntakeProblem.missing();return row;}
 String encode(Object value){try{return json.writeValueAsString(value);}catch(java.io.IOException e){throw new IllegalStateException("SIGNING_SUPPORT_JSON_INVALID");}}
 SigningResultSpool.Entry snapshot(Incident i){try{return json.readValue(i.snapshot,SigningResultSpool.Entry.class);}catch(java.io.IOException e){throw new IllegalStateException("SIGNING_SUPPORT_JSON_INVALID");}}
 String tag(Fenced i){return "\"support-"+i.id+"-"+Base64.getUrlEncoder().withoutPadding().encodeToString(i.fence)+"\"";}
 Incident lockIncident(long id){
  em.flush();if(jdbc.queryForList("SELECT id FROM dbo.mghp_su_co_ky WITH(UPDLOCK,HOLDLOCK) WHERE id=?",Long.class,id).isEmpty())throw IntakeProblem.missing();
  var i=find(Incident.class,id);em.refresh(i);return i;
 }
 void record(SigningResultSpool.Entry entry){
  if(entry.error()==null||entry.errorEvent()==null)return;
  em.flush();long attempt=entry.context().attempt();
  if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_su_co_ky_lich_su WHERE su_kien_id=?",Integer.class,entry.errorEvent().toString())>0)return;
  if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.lan_goi_dich_vu_ky a JOIN dbo.yeu_cau_ky q ON q.id=a.yeu_cau_ky_id JOIN dbo.phien_ban_van_ban v ON v.id=q.phien_ban_van_ban_id JOIN dbo.van_ban d ON d.id=v.van_ban_id WHERE a.id=? AND a.yeu_cau_ky_id=? AND a.buoc_ky_id=? AND q.phien_ban_adapter=N'ctsv-p11-v1' AND d.ma_phan_he=N'MGHP'",Integer.class,attempt,entry.context().request(),entry.context().step())!=1)throw IntakeProblem.missing();
  var ids=jdbc.queryForList("SELECT id FROM dbo.mghp_su_co_ky WITH(UPDLOCK,HOLDLOCK) WHERE lan_goi_id=?",Long.class,attempt);
  Incident i=ids.isEmpty()?null:find(Incident.class,ids.getFirst());if(i!=null)em.refresh(i);
  if(i!=null&&(entry.errorEvent().equals(i.event)||entry.errorAt().isBefore(snapshot(i).errorAt())))return;
  if(i==null){i=new Incident();i.attempt=attempt;i.occurredAt=entry.errorAt().atOffset(ZoneOffset.UTC);i.state="waiting";}
  else if("resolved".equals(i.state)){i.state="waiting";i.startedAt=null;i.startedBy=null;i.closedAt=null;i.closedBy=null;}
  i.event=entry.errorEvent();i.snapshot=encode(entry);i.updatedAt=now();if(i.id==null)save(i);else{em.flush();em.refresh(i);}
  var h=new IncidentHistory();h.incident=i.id;h.event=entry.errorEvent();h.kind="error";h.text=entry.error().adminMessage();h.snapshot=i.snapshot;h.at=entry.errorAt().atOffset(ZoneOffset.UTC);save(h);
 }
 List<IncidentHistory> history(long id){return em.createQuery("from SigningIncidentHistory where incident=:id order by id",IncidentHistory.class).setParameter("id",id).getResultList();}
 void event(Incident i,long actor,String kind,String text,String reference){
  var h=new IncidentHistory();h.incident=i.id;h.event=UUID.randomUUID();h.kind=kind;h.text=text;h.reference=reference;h.actor=actor;h.at=now();save(h);
 }
 void change(Incident i,long actor,String next){
  boolean start="working".equals(next);var at=now();
  int n=jdbc.update("UPDATE dbo.mghp_su_co_ky SET trang_thai=?,thoi_diem_cap_nhat=?,nguoi_bat_dau_id=?,thoi_diem_bat_dau=?,nguoi_ket_thuc_id=?,thoi_diem_ket_thuc=? WHERE id=? AND phien_ban_ky_thuat=?",next,at,start?actor:i.startedBy,start?at:i.startedAt,start?null:actor,start?null:at,i.id,i.fence);
  if(n!=1)throw new IntakeProblem(412,"VERSION_CONFLICT","If-Match");em.refresh(i);
 }
 void touch(Incident i){if(jdbc.update("UPDATE dbo.mghp_su_co_ky SET thoi_diem_cap_nhat=? WHERE id=? AND phien_ban_ky_thuat=?",now(),i.id,i.fence)!=1)throw new IntakeProblem(412,"VERSION_CONFLICT","If-Match");em.refresh(i);}
 void audit(long actor,String action,String type,String id){jdbc.update("INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,nguoi_dung_id,hanh_dong,loai_doi_tuong,doi_tuong_id,ly_do,thoi_diem_tao) VALUES(NEWID(),N'user',?,?,?,?,N'Quản trị ký số MGHP',?)",actor,action,type,id,now());}
 record IncidentPage(List<Incident> items,long total,Map<String,Long> counts){}
 IncidentPage incidents(String status,String query,int page,int size){
  String filter=" FROM dbo.mghp_su_co_ky WHERE (?=N'all' OR trang_thai=?) AND (?=N'' OR CHARINDEX(?,du_lieu_loi COLLATE Vietnamese_100_CI_AI)>0)";
  var ids=jdbc.queryForList("SELECT id"+filter+" ORDER BY id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",Long.class,status,status,query,query,(long)page*size,size);
  long total=jdbc.queryForObject("SELECT COUNT_BIG(*)"+filter,Long.class,status,status,query,query);
  var counts=new LinkedHashMap<String,Long>();for(String state:List.of("waiting","working","resolved"))counts.put(state,jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.mghp_su_co_ky WHERE trang_thai=?",Long.class,state));
  return new IncidentPage(ids.stream().map(id->find(Incident.class,id)).toList(),total,counts);
 }
 CertificateMonitor ensureCertificate(String email){
  em.flush();var ids=jdbc.queryForList("SELECT id FROM dbo.mghp_theo_doi_chung_thu WITH(UPDLOCK,HOLDLOCK) WHERE email=?",Long.class,email);
  if(!ids.isEmpty()){var c=find(CertificateMonitor.class,ids.getFirst());em.refresh(c);return c;}
  var c=new CertificateMonitor();c.email=email;c.state="unknown";return save(c);
 }
 CertificateMonitor lockCertificate(long id){
  em.flush();if(jdbc.queryForList("SELECT id FROM dbo.mghp_theo_doi_chung_thu WITH(UPDLOCK,HOLDLOCK) WHERE id=?",Long.class,id).isEmpty())throw IntakeProblem.missing();var c=find(CertificateMonitor.class,id);em.refresh(c);return c;
 }
 void certEvent(CertificateMonitor c,Long actor,String kind,String text,String reference,String observation){
  var h=new CertificateHistory();h.certificate=c.id;h.actor=actor;h.kind=kind;h.text=text;h.reference=reference;h.observation=observation;h.at=now();save(h);
 }
 List<CertificateHistory> certHistory(long id){return em.createQuery("from SigningCertificateHistory where certificate=:id order by id",CertificateHistory.class).setParameter("id",id).getResultList();}
 Renewal currentRenewal(long id){return em.createQuery("from SigningRenewal where certificate=:id order by id desc",Renewal.class).setParameter("id",id).setMaxResults(1).getResultStream().findFirst().orElse(null);}
 pbl6.ctsv.shared.ca.CaProviderDtos.Certificates observation(CertificateMonitor c){
  if(c.observation==null)return new pbl6.ctsv.shared.ca.CaProviderDtos.Certificates(c.email,List.of());
  try{return json.readValue(c.observation,pbl6.ctsv.shared.ca.CaProviderDtos.Certificates.class);}catch(java.io.IOException e){throw new IllegalStateException("SIGNING_SUPPORT_JSON_INVALID");}
 }
 void touchCertificate(CertificateMonitor c){em.flush();em.refresh(c);if(jdbc.update("UPDATE dbo.mghp_theo_doi_chung_thu SET email=email WHERE id=? AND phien_ban_ky_thuat=?",c.id,c.fence)!=1)throw new IntakeProblem(412,"VERSION_CONFLICT","If-Match");em.refresh(c);}
 boolean replacementNotReady(String email,UUID certificate){return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.mghp_yeu_cau_gia_han r JOIN dbo.mghp_theo_doi_chung_thu c ON c.id=r.theo_doi_id WHERE c.email=? AND r.trang_thai=N'approved' AND r.chung_thu_cu=?",Integer.class,email,certificate.toString())>0;}
 boolean signerBusy(String email){return jdbc.queryForObject("""
  SELECT COUNT_BIG(*) FROM dbo.buoc_ky s JOIN dbo.yeu_cau_ky q ON q.id=s.yeu_cau_ky_id
  WHERE s.email_nguoi_ky COLLATE Latin1_General_100_BIN2=? AND q.phien_ban_adapter=N'ctsv-p11-v1' AND q.trang_thai IN(N'queued',N'signing')
  """,Long.class,email)>0;}
 void refresh(Fenced row){em.flush();em.refresh(row);}
}
