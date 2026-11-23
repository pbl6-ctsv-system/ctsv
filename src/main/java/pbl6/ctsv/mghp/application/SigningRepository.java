package pbl6.ctsv.mghp.application;

import jakarta.persistence.EntityManager;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static pbl6.ctsv.mghp.application.SigningEntities.*;

@Repository @ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningRepository {
 @jakarta.persistence.PersistenceContext EntityManager em;
 final JdbcTemplate jdbc;
 SigningRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 <T extends ApplicationEntities.Row>T save(T value){em.persist(value);em.flush();if(value instanceof Request)em.refresh(value);return value;}
 <T>T find(Class<T> type,long id){var v=em.find(type,id);if(v==null)throw IntakeProblem.missing();return v;}
 Link link(long version){return em.createQuery("from P11Link where versionId=:v",Link.class).setParameter("v",version).getResultStream().findFirst().orElseThrow(IntakeProblem::missing);}
 List<Link> links(boolean minutes,long id){return em.createQuery("from P11Link where "+(minutes?"minutesId":"decisionVersionId")+"=:id order by id desc",Link.class).setParameter("id",id).getResultList();}
 Request request(long version){return em.createQuery("from P11Request where versionId=:id and adapter='ctsv-p11-v1'",Request.class).setParameter("id",version).getResultStream().findFirst().orElse(null);}
 List<Step> steps(long request){return em.createQuery("from P11Step s where s.requestId=:id order by s.order,s.id",Step.class).setParameter("id",request).getResultList();}
 void flush(){em.flush();}
 void beginSqlScope(){
  // With SQL Server implicit transactions, a no-row table SELECT starts SQL scope without row locks.
  // An applock as the first SQL statement otherwise returns -999 even inside TransactionTemplate.
  jdbc.query("SELECT TOP(0) id FROM dbo.lan_goi_dich_vu_ky",(org.springframework.jdbc.core.RowCallbackHandler)row->{});
 }
 List<Long> documentsForFile(long file){return jdbc.queryForList("""
  SELECT v.id FROM dbo.phien_ban_van_ban v JOIN dbo.van_ban d ON d.id=v.van_ban_id
  WHERE d.ma_phan_he=N'MGHP' AND (v.tep_goc_id=? OR EXISTS(
   SELECT 1 FROM dbo.yeu_cau_ky q JOIN dbo.tep_ket_qua_ky a ON a.yeu_cau_ky_id=q.id
   WHERE q.phien_ban_van_ban_id=v.id AND a.tep_dinh_kem_id=? AND a.trang_thai_toan_ven=N'verified'))
  """,Long.class,file,file);}
 void touchSource(boolean minutes,long id){em.flush();jdbc.update(minutes?"UPDATE dbo.mghp_bien_ban SET trang_thai=trang_thai WHERE id=?":"UPDATE dbo.mghp_phien_ban_qd SET trang_thai=trang_thai WHERE id=?",id);}
 boolean ppReadsMinutes(long user,long minutes){return jdbc.queryForObject("""
  SELECT COUNT(*) FROM dbo.mghp_bien_ban b
  JOIN dbo.mghp_lien_ket_phien_ban_van_ban l ON l.phien_ban_qd_id=b.phien_ban_qd_nguon_id
  JOIN dbo.yeu_cau_ky q ON q.phien_ban_van_ban_id=l.phien_ban_van_ban_dung_chung_id
  JOIN dbo.buoc_ky s ON s.yeu_cau_ky_id=q.id
  WHERE b.id=? AND s.nguoi_dung_noi_bo_id=? AND s.vai_tro_nguoi_ky=N'pho_phong' AND q.phien_ban_adapter=N'ctsv-p11-v1'
  """,Integer.class,minutes,user)>0;}
 void refresh(Object v){em.flush();em.refresh(v);}
 Request lock(long id){em.flush();if(jdbc.queryForList("SELECT id FROM dbo.yeu_cau_ky WITH(UPDLOCK,HOLDLOCK) WHERE id=? AND phien_ban_adapter=N'ctsv-p11-v1'",Long.class,id).isEmpty())throw IntakeProblem.missing();var v=find(Request.class,id);em.refresh(v);return v;}
 void touch(Request v){em.flush();if(jdbc.update("UPDATE dbo.yeu_cau_ky SET trang_thai=trang_thai WHERE id=? AND phien_ban_ky_thuat=?",v.id,v.fence)!=1)throw new IntakeProblem(412,"VERSION_CONFLICT","If-Match");em.refresh(v);}
 void audit(long actor,String action,long id,String reason){jdbc.update("INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,nguoi_dung_id,hanh_dong,loai_doi_tuong,doi_tuong_id,ly_do,thoi_diem_tao) VALUES(NEWID(),N'user',?,?,N'phien_ban_van_ban',?,?,TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00'))",actor,action,""+id,reason);}
 boolean minutesCompleted(Link link){
  if(link.minutesId!=null)return true;
  return jdbc.queryForObject("""
   SELECT COUNT(*) FROM dbo.mghp_bien_ban b JOIN dbo.mghp_lien_ket_phien_ban_van_ban l ON l.bien_ban_id=b.id
   JOIN dbo.yeu_cau_ky q ON q.phien_ban_van_ban_id=l.phien_ban_van_ban_dung_chung_id
   WHERE b.phien_ban_qd_nguon_id=? AND l.trang_thai=N'current' AND q.trang_thai=N'completed' AND q.phien_ban_adapter=N'ctsv-p11-v1'
   AND 2=(SELECT COUNT(*) FROM dbo.buoc_ky s JOIN dbo.bien_nhan_ky n ON n.buoc_ky_id=s.id
    JOIN dbo.tep_ket_qua_ky a ON a.bien_nhan_ky_id=n.id JOIN dbo.tep_dinh_kem f ON f.id=a.tep_dinh_kem_id
    WHERE s.yeu_cau_ky_id=q.id AND s.trang_thai=N'completed' AND a.trang_thai_toan_ven=N'verified' AND f.trang_thai_luu_tru=N'available')
   """,Integer.class,link.decisionVersionId)>0;
 }
 record Output(long file,long receipt,String hash,String at){}
 Output output(long step){return jdbc.query("""
  SELECT a.tep_dinh_kem_id,n.id,f.sha256,CONVERT(varchar(42),n.thoi_diem_ky,127)
  FROM dbo.bien_nhan_ky n JOIN dbo.tep_ket_qua_ky a ON a.bien_nhan_ky_id=n.id JOIN dbo.tep_dinh_kem f ON f.id=a.tep_dinh_kem_id
  WHERE n.buoc_ky_id=? AND a.trang_thai_toan_ven=N'verified' ORDER BY a.id DESC
  """,(r,n)->new Output(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4)),step).stream().findFirst().orElse(null);}
 String phase(long request){return jdbc.query("SELECT TOP(1) giai_doan_xu_ly FROM dbo.lan_goi_dich_vu_ky WHERE yeu_cau_ky_id=? ORDER BY id DESC",(r,n)->r.getString(1),request).stream().findFirst().orElse("waiting");}
 long input(Request q){var steps=steps(q.id);for(int i=steps.size()-1;i>=0;i--){var o=output(steps.get(i).id);if(o!=null)return o.file();}return find(DocumentVersion.class,q.versionId).fileId;}
 void authorization(Step step,String reference,long actor){jdbc.update("INSERT dbo.uy_quyen_ky(buoc_ky_id,loai_uy_quyen,email_nguoi_duoc_uy_quyen_ky,tham_chieu_minh_chung,trang_thai,thoi_diem_uy_quyen,nguoi_ghi_nhan_id,thoi_diem_ghi_nhan) VALUES(?,N'send_for_selected_signer',?,?,N'active',SYSUTCDATETIME(),?,SYSUTCDATETIME())",step.id,step.email,reference,actor);}
 boolean authorized(Step step){return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.uy_quyen_ky WHERE buoc_ky_id=? AND trang_thai=N'active' AND email_nguoi_duoc_uy_quyen_ky COLLATE Latin1_General_100_BIN2=? AND thoi_diem_uy_quyen<=SYSUTCDATETIME() AND (thoi_diem_het_han IS NULL OR thoi_diem_het_han>SYSUTCDATETIME())",Integer.class,step.id,step.email)>0;}
 long enqueue(Request q,Step step,pbl6.ctsv.auth.AuthAccess.Principal principal,long input,String hash){
  em.flush();return jdbc.queryForObject("""
   DECLARE @ids TABLE(id bigint);
   INSERT dbo.lan_goi_dich_vu_ky(yeu_cau_ky_id,buoc_ky_id,loai_thao_tac,so_thu_tu_lan_goi,ma_lien_ket_yeu_cau,
    sha256_dau_vao_du_kien,ket_luan,co_the_da_thay_doi_ben_ngoai,thoi_diem_bat_dau,phien_ban_adapter,tep_dau_vao_id,
    giai_doan_xu_ly,nha_phat_hanh_auth,chu_the_auth,phien_ban_auth)
   OUTPUT inserted.id INTO @ids
   VALUES(?,?,N'sign',1,NEWID(),?,N'queued',0,SYSUTCDATETIME(),N'ctsv-p11-v1',?,N'queued',?,?,?);
   SELECT id FROM @ids;
   """,Long.class,q.id,step.id,hash,input,principal.issuer(),principal.accountId().toString(),principal.tokenVersion());
 }
 void acknowledge(DocumentVersion v,long user,String hash){jdbc.update("INSERT dbo.ghi_nhan_xem_van_ban(phien_ban_van_ban_id,nguoi_nhan_xet_id,loai_nhan_xet,ket_qua_nhan_xet,ghi_chu,thoi_diem_nhan_xet) VALUES(?,?,N'pdf_input',N'reviewed',?,SYSUTCDATETIME())",v.id,user,hash);}
 record Work(long id,long request,long step,UUID operation,long input,String hash,String phase,UUID lease,java.time.OffsetDateTime leaseUntil,pbl6.ctsv.auth.AuthAccess.Principal principal){}
 Work work(long request){return jdbc.query("""
  SELECT TOP(1) id,yeu_cau_ky_id,buoc_ky_id,ma_lien_ket_yeu_cau,tep_dau_vao_id,sha256_dau_vao_du_kien,
   giai_doan_xu_ly,lease_token,lease_den,nha_phat_hanh_auth,chu_the_auth,phien_ban_auth
  FROM dbo.lan_goi_dich_vu_ky WHERE yeu_cau_ky_id=? AND phien_ban_adapter=N'ctsv-p11-v1' ORDER BY id DESC
  """,(v,n)->new Work(v.getLong(1),v.getLong(2),v.getLong(3),UUID.fromString(v.getString(4)),v.getLong(5),v.getString(6),v.getString(7),v.getString(8)==null?null:UUID.fromString(v.getString(8)),v.getObject(9,java.time.OffsetDateTime.class),new pbl6.ctsv.auth.AuthAccess.Principal(v.getString(10),UUID.fromString(v.getString(11)),v.getLong(12))),request).stream().findFirst().orElse(null);}
 List<Long> due(){return jdbc.queryForList("SELECT TOP(8) yeu_cau_ky_id FROM dbo.lan_goi_dich_vu_ky WHERE phien_ban_adapter=N'ctsv-p11-v1' AND giai_doan_xu_ly IN(N'queued',N'uploaded',N'download',N'storage',N'upload_intent',N'sign_intent') AND (lease_den IS NULL OR lease_den<SYSUTCDATETIME()) ORDER BY id",Long.class);}
 boolean claim(Work w,UUID lease){return jdbc.update("UPDATE dbo.lan_goi_dich_vu_ky SET lease_token=?,lease_den=DATEADD(minute,5,SYSDATETIMEOFFSET()) WHERE id=? AND giai_doan_xu_ly=? AND (lease_den IS NULL OR lease_den<SYSUTCDATETIME())",lease.toString(),w.id,w.phase)==1;}
 void owned(long work,UUID lease,boolean dispatch){int count=jdbc.queryForObject("SELECT COUNT(*) FROM dbo.lan_goi_dich_vu_ky WITH(UPDLOCK,HOLDLOCK) WHERE id=? AND lease_token=?"+(dispatch?" AND lease_den>SYSUTCDATETIME()":""),Integer.class,work,lease.toString());if(count!=1)throw IntakeProblem.conflict("SIGNING_LEASE_LOST");}
 void phase(Work w,UUID lease,String phase){owned(w.id,lease,true);jdbc.update("UPDATE dbo.lan_goi_dich_vu_ky SET giai_doan_xu_ly=?,lease_den=DATEADD(minute,5,SYSDATETIMEOFFSET()),co_the_da_thay_doi_ben_ngoai=? WHERE id=?",phase,Set.of("upload_intent","sign_intent").contains(phase),w.id);}
 void uploaded(Work w,UUID lease,UUID document){owned(w.id,lease,false);jdbc.update("UPDATE dbo.yeu_cau_ky SET van_ban_ben_ngoai_id=? WHERE id=?",document.toString(),w.request);jdbc.update("UPDATE dbo.lan_goi_dich_vu_ky SET van_ban_ca_phan_hoi_id=?,giai_doan_xu_ly=N'uploaded',ket_luan=N'uploaded',co_the_da_thay_doi_ben_ngoai=0 WHERE id=?",document.toString(),w.id);em.clear();}
 void certificate(Step s,pbl6.ctsv.shared.ca.CaProviderDtos.Certificate c){
  Long check=jdbc.queryForObject("""
   INSERT dbo.lan_kiem_tra_chung_thu(buoc_ky_id,chung_thu_ca_id,so_seri,nguoi_dung_ca_id,ten_nguoi_so_huu,hieu_luc_tu,hieu_luc_den,trang_thai_ca,hop_le_theo_ca,khoa_rieng_san_sang_theo_ca,thoi_diem_kiem_tra,ket_qua)
   VALUES(?,?,?,?,?,?,?,?,?,?,SYSUTCDATETIME(),N'verified');SELECT CAST(SCOPE_IDENTITY() AS bigint);
   """,Long.class,s.id,c.id().toString(),c.serialNumber(),c.user().toString(),c.userDisplay(),c.validFrom().atOffset(java.time.ZoneOffset.UTC),c.validTo().atOffset(java.time.ZoneOffset.UTC),c.status(),c.valid(),c.hasPrivateKey());
  s.caUser=c.user();s.certificate=c.id();s.serial=c.serialNumber();s.name=c.userDisplay();em.flush();jdbc.update("UPDATE dbo.buoc_ky SET lan_kiem_tra_chung_thu_id=?,thoi_diem_kiem_tra_chung_thu=SYSUTCDATETIME() WHERE id=?",check,s.id);
 }
 pbl6.ctsv.shared.ca.CaProviderDtos.Receipt receipt(long attempt){return jdbc.query("""
  SELECT chu_ky_ben_ngoai_id,van_ban_ben_ngoai_id,chung_thu_id,so_seri_chung_thu,email_nguoi_ky,ten_nguoi_ky,
   sha256_van_ban,sha256_pdf_da_ky,thoi_diem_ky,trang_thai_chu_ky_phia_ca
  FROM dbo.bien_nhan_ky WHERE lan_goi_id=? AND duoc_chon=1
  """,(v,n)->new pbl6.ctsv.shared.ca.CaProviderDtos.Receipt(UUID.fromString(v.getString(1)),UUID.fromString(v.getString(2)),UUID.fromString(v.getString(3)),v.getString(4),v.getString(5),v.getString(6),v.getString(7),v.getString(8),v.getObject(9,java.time.OffsetDateTime.class).toInstant(),v.getString(10)),attempt).stream().findFirst().orElse(null);}
 void receipt(Work w,UUID lease,pbl6.ctsv.shared.ca.CaProviderDtos.Receipt n){
  owned(w.id,lease,false);var q=find(Request.class,w.request);
  jdbc.update("""
   INSERT dbo.bien_nhan_ky(yeu_cau_ky_id,buoc_ky_id,lan_goi_id,he_thong_cung_cap_id,chu_ky_ben_ngoai_id,van_ban_ben_ngoai_id,
    chung_thu_id,so_seri_chung_thu,ten_nguoi_ky,email_nguoi_ky,sha256_van_ban,sha256_pdf_da_ky,trang_thai_chu_ky_phia_ca,duoc_chon,thoi_diem_ky,thoi_diem_tiep_nhan,phien_ban_hop_dong)
   VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,SYSUTCDATETIME(),N'P11-23-11')
   """,w.request,w.step,w.id,q.providerId,n.id().toString(),n.document().toString(),n.certificate().toString(),n.certificateSerial(),n.signerName(),n.signerEmail(),n.documentHash(),n.signedPdfHash(),n.status(),n.signedAt().atOffset(java.time.ZoneOffset.UTC));
  jdbc.update("UPDATE dbo.lan_goi_dich_vu_ky SET giai_doan_xu_ly=N'download',ket_luan=N'receipt',chu_ky_ca_phan_hoi_id=?,sha256_pdf_phan_hoi=?,co_the_da_thay_doi_ben_ngoai=0 WHERE id=?",n.id().toString(),n.signedPdfHash(),w.id);
 }
 void error(Work w,UUID lease,String phase,String code,boolean uncertain){
  if(lease!=null)owned(w.id,lease,false);
  jdbc.update("UPDATE dbo.lan_goi_dich_vu_ky SET giai_doan_xu_ly=?,ket_luan=?,ma_loi=?,co_the_da_thay_doi_ben_ngoai=?,lease_den=NULL WHERE id=?",uncertain?"unknown":phase,uncertain?"unknown":"rejected",code,uncertain,w.id);
  var q=find(Request.class,w.request);q.state=uncertain?"unknown":"failed";q.error=code;var s=find(Step.class,w.step);s.state=q.state;em.flush();em.refresh(q);
 }
 void completed(Work w,UUID lease,long file,long actor){
  owned(w.id,lease,true);var n=receipt(w.id);if(n==null)throw IntakeProblem.conflict("SIGNING_NOT_READY");
  Long receiptId=jdbc.queryForObject("SELECT id FROM dbo.bien_nhan_ky WHERE lan_goi_id=? AND duoc_chon=1",Long.class,w.id);
  Long check=jdbc.queryForObject("SELECT TOP(1) id FROM dbo.lan_kiem_tra_tep WHERE tep_dinh_kem_id=? AND trang_thai_toan_ven=N'verified' ORDER BY id DESC",Long.class,file);
  if(check==null)throw IntakeProblem.conflict("FILE_NOT_READY");
  jdbc.update("INSERT dbo.tep_ket_qua_ky(yeu_cau_ky_id,bien_nhan_ky_id,tep_dinh_kem_id,loai_tep_ket_qua,trang_thai_toan_ven,trang_thai_kiem_tra_chu_ky,phuong_phap_kiem_tra,lan_kiem_tra_tep_id,thoi_diem_tai_ve,thoi_diem_xac_minh) VALUES(?,?,?,N'step_pdf',N'verified',N'cryptographic_verified',N'pdfbox-bc-sha256',?,SYSUTCDATETIME(),SYSUTCDATETIME())",w.request,receiptId,file,check);
  jdbc.update("UPDATE dbo.lan_goi_dich_vu_ky SET giai_doan_xu_ly=N'completed',ket_luan=N'completed',tep_dau_ra_id=?,luot_luu_tep_id=?,thoi_diem_ket_thuc=SYSUTCDATETIME(),lease_den=NULL WHERE id=?",file,w.operation.toString(),w.id);
  var step=find(Step.class,w.step);step.state="completed";step.updatedAt=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
  var q=find(Request.class,w.request);q.error=null;q.state=steps(q.id).stream().allMatch(s->"completed".equals(s.state))?"completed":"waiting_next";if(q.state.equals("completed"))q.completedAt=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
  em.flush();audit(actor,"SIGNING_STEP_COMPLETED",q.versionId,"Đã kiểm và lưu PDF bên CTSV");
  var recipients=steps(q.id).stream().filter(s->"waiting".equals(s.state)).map(s->s.userId).toList();
  notify(q.id,actor,recipients.isEmpty()?List.of(q.actor):recipients,recipients.isEmpty()?"Văn bản đã ký xong":"Văn bản đang chờ bạn ký","PDF của bước trước đã được lưu tại CTSV.");em.refresh(q);
 }
 boolean safePreDispatch(Work w){return "failed".equals(w.phase)&&jdbc.queryForObject("SELECT COUNT(*) FROM dbo.lan_goi_dich_vu_ky WHERE id=? AND co_the_da_thay_doi_ben_ngoai=0 AND chu_ky_ca_phan_hoi_id IS NULL AND ma_loi IN(N'CA_CERTIFICATE_SELECTION_REQUIRED',N'CA_CERTIFICATE_NOT_READY',N'CA_NOT_CONFIGURED',N'SIGNING_AUTHORIZATION_REVOKED')",Integer.class,w.id)==1&&receipt(w.id)==null;}
 boolean canResume(Work w){return receipt(w.id)!=null&&Set.of("download","storage").contains(w.phase)||safePreDispatch(w);}
 void releaseForNewConsent(Work w){
  jdbc.update("UPDATE dbo.lan_goi_dich_vu_ky SET giai_doan_xu_ly=N'awaiting_consent' WHERE id=?",w.id);
  var q=find(Request.class,w.request);var step=find(Step.class,w.step);step.state="waiting";step.page=null;step.x=null;step.y=null;step.width=null;step.height=null;q.state=step.order==1?"prepared":"waiting_next";q.error=null;em.flush();em.refresh(q);
 }
 void resume(Work w){jdbc.update("UPDATE dbo.lan_goi_dich_vu_ky SET giai_doan_xu_ly=N'download',lease_den=NULL,lease_token=NULL WHERE id=?",w.id);var q=find(Request.class,w.request);q.state="queued";q.error=null;em.flush();em.refresh(q);}
 void notify(long request,long actor,Collection<Long> recipients,String title,String content){
  if(recipients.isEmpty())return;
  Long id=jdbc.queryForObject("""
   DECLARE @ids TABLE(id bigint);
   INSERT dbo.thong_bao(ma_phan_he,tieu_de,noi_dung,nhom_phan_loai,loai_doi_tuong,doi_tuong_id,url_thao_tac,trang_thai,thoi_diem_cong_bo,nguoi_tao_id,thoi_diem_tao)
   OUTPUT inserted.id INTO @ids
   VALUES(N'MGHP',?,?,N'mghp',N'yeu_cau_ky',?,?,N'published',SYSUTCDATETIME(),?,SYSUTCDATETIME());SELECT id FROM @ids;
   """,Long.class,title,content,""+request,"/mghp/ky-so?yeu-cau="+request,actor);
  for(long user:new LinkedHashSet<>(recipients))jdbc.update("INSERT dbo.nguoi_nhan_thong_bao(thong_bao_id,nguoi_dung_id,thoi_diem_tao) VALUES(?,?,SYSUTCDATETIME())",id,user);
 }
 List<Map<String,Object>> history(long version){return jdbc.queryForList("""
  SELECT CONVERT(varchar(20),id) id,hanh_dong action,CONVERT(varchar(20),nguoi_dung_id) actorId,ly_do reason,
   CONVERT(varchar(42),thoi_diem_tao,127) at FROM dbo.nhat_ky_thao_tac
  WHERE loai_doi_tuong=N'phien_ban_van_ban' AND doi_tuong_id=? ORDER BY id
  """,""+version);}
 List<Map<String,Object>> authorizations(long step){return jdbc.queryForList("""
  SELECT CONVERT(varchar(20),id) id,CONVERT(varchar(20),buoc_ky_id) stepId,loai_uy_quyen type,email_nguoi_duoc_uy_quyen_ky authorizedSignerEmail,
  CONVERT(varchar(20),tep_minh_chung_id) evidenceFileId,tham_chieu_minh_chung evidenceReference,trang_thai status,
  CONVERT(varchar(42),thoi_diem_uy_quyen,127) effectiveAt,CONVERT(varchar(42),thoi_diem_het_han,127) expiresAt,
  CONVERT(varchar(42),thoi_diem_ghi_nhan,127) recordedAt,CONVERT(varchar(42),thoi_diem_thu_hoi,127) revokedAt
  FROM dbo.uy_quyen_ky WHERE buoc_ky_id=? ORDER BY id
  """,step);}
 Map<String,Object> authorization(long id){var step=jdbc.queryForList("SELECT buoc_ky_id FROM dbo.uy_quyen_ky WHERE id=?",Long.class,id);if(step.isEmpty())throw IntakeProblem.missing();return authorizations(step.getFirst()).stream().filter(a->a.get("id").equals(""+id)).findFirst().orElseThrow(IntakeProblem::missing);}
 long addAuthorization(Step step,long user,String reference,Long file,java.time.Instant effective,java.time.Instant expires){return jdbc.queryForObject("INSERT dbo.uy_quyen_ky(buoc_ky_id,loai_uy_quyen,email_nguoi_duoc_uy_quyen_ky,tham_chieu_minh_chung,tep_minh_chung_id,trang_thai,thoi_diem_uy_quyen,thoi_diem_het_han,nguoi_ghi_nhan_id,thoi_diem_ghi_nhan) VALUES(?,N'send_for_selected_signer',?,?,?,N'active',?,?,?,SYSUTCDATETIME());SELECT CAST(SCOPE_IDENTITY() AS bigint)",Long.class,step.id,step.email,reference,file,effective.atOffset(java.time.ZoneOffset.UTC),expires==null?null:expires.atOffset(java.time.ZoneOffset.UTC),user);}
 void revokeAuthorization(long id,long actor,String reason){jdbc.update("UPDATE dbo.uy_quyen_ky SET trang_thai=N'revoked',thoi_diem_thu_hoi=SYSUTCDATETIME(),ly_do_thu_hoi=? WHERE id=? AND trang_thai=N'active'",reason,id);}
 String listFilter(boolean manager,boolean pp,boolean pht,boolean mine,String status){
  String scope=manager&&!mine?"1=1":"EXISTS(SELECT 1 FROM dbo.buoc_ky s WHERE s.yeu_cau_ky_id=q.id AND s.nguoi_dung_noi_bo_id=@user AND ("+
   (manager?"s.vai_tro_nguoi_ky=N'chuyen_vien'":"1=0")+(pp?" OR s.vai_tro_nguoi_ky=N'pho_phong'":"")+(pht?" OR s.vai_tro_nguoi_ky=N'pho_hieu_truong'":"")+"))";
  return " WHERE q.phien_ban_adapter=N'ctsv-p11-v1' AND ("+scope+")"+(status==null?"":" AND q.trang_thai=@state");
 }
 String listFilter(boolean manager,boolean pp,boolean pht,boolean mine,String status,String view){
  String filter=listFilter(manager,pp,pht,mine,status);
  if("all".equals(view))return filter;
  String roles=(manager?"s.vai_tro_nguoi_ky=N'chuyen_vien'":"1=0")+(pp?" OR s.vai_tro_nguoi_ky=N'pho_phong'":"")+(pht?" OR s.vai_tro_nguoi_ky=N'pho_hieu_truong'":"");
  String step=" EXISTS(SELECT 1 FROM dbo.buoc_ky s WHERE s.yeu_cau_ky_id=q.id AND s.nguoi_dung_noi_bo_id=@user AND ("+roles+") AND ";
  if("handled".equals(view))return filter+" AND "+step+"s.trang_thai=N'completed')";
  return filter+" AND q.trang_thai IN(N'prepared',N'waiting_next') AND "+step+"s.trang_thai=N'waiting' AND NOT EXISTS(SELECT 1 FROM dbo.buoc_ky prev WHERE prev.yeu_cau_ky_id=q.id AND prev.thu_tu_ky<s.thu_tu_ky AND prev.trang_thai<>N'completed') AND EXISTS(SELECT 1 FROM dbo.uy_quyen_ky au WHERE au.buoc_ky_id=s.id AND au.trang_thai=N'active' AND au.thoi_diem_uy_quyen<=SYSUTCDATETIME() AND (au.thoi_diem_het_han IS NULL OR au.thoi_diem_het_han>SYSUTCDATETIME()))) AND EXISTS(SELECT 1 FROM dbo.mghp_lien_ket_phien_ban_van_ban l WHERE l.phien_ban_van_ban_dung_chung_id=q.phien_ban_van_ban_id AND l.trang_thai=N'current')";
 }
 List<Long> visibleRequests(long user,boolean manager,boolean pp,boolean pht,boolean mine,String status,int page,int size){
  return jdbc.queryForList("DECLARE @user bigint=?,@state nvarchar(128)=?; SELECT q.id FROM dbo.yeu_cau_ky q"+listFilter(manager,pp,pht,mine,status)+" ORDER BY q.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",Long.class,user,status,(long)page*size,size);
 }
 long visibleCount(long user,boolean manager,boolean pp,boolean pht,boolean mine,String status,String view){return jdbc.queryForObject("DECLARE @user bigint=?,@state nvarchar(128)=?; SELECT COUNT_BIG(*) FROM dbo.yeu_cau_ky q"+listFilter(manager,pp,pht,mine,status,view),Long.class,user,status);}
 List<Map<String,Object>> visibleRows(long user,boolean manager,boolean pp,boolean pht,boolean mine,String status,int page,int size,String view){
  return jdbc.queryForList("""
   DECLARE @user bigint=?,@state nvarchar(128)=?;
   SELECT CONVERT(varchar(20),q.id) id,CONVERT(varchar(20),v.id) documentVersionId,v.tieu_de_tai_thoi_diem_chot title,
    v.so_phien_ban version,d.loai_van_ban documentType,q.trang_thai status,q.che_do_thuc_hien mode,
    CONVERT(varchar(20),rd.id) roundId,rd.ten roundName,h.ten semesterName,
    cur.thu_tu_ky currentStep,cur.vai_tro_nguoi_ky waitingForRole,
    CONVERT(varchar(20),f.id) inputFileId,f.ten_tep inputFileName,CONVERT(varchar(20),f.kich_thuoc_tep) inputFileSize,f.sha256 inputFileHash,
    (SELECT CONVERT(varchar(20),s.id) id,s.thu_tu_ky [order],s.vai_tro_nguoi_ky role,s.trang_thai status,
      (SELECT CONVERT(varchar(20),a.tep_dinh_kem_id) FROM dbo.bien_nhan_ky n JOIN dbo.tep_ket_qua_ky a ON a.bien_nhan_ky_id=n.id WHERE n.buoc_ky_id=s.id AND a.trang_thai_toan_ven=N'verified') outputFileId
     FROM dbo.buoc_ky s WHERE s.yeu_cau_ky_id=q.id ORDER BY s.thu_tu_ky FOR JSON PATH) progressJson,
    CASE WHEN cur.nguoi_dung_noi_bo_id=@user AND cur.trang_thai=N'waiting' AND q.trang_thai IN(N'prepared',N'waiting_next') AND l.trang_thai=N'current'
     AND EXISTS(SELECT 1 FROM dbo.uy_quyen_ky au WHERE au.buoc_ky_id=cur.id AND au.trang_thai=N'active' AND au.thoi_diem_uy_quyen<=SYSUTCDATETIME() AND (au.thoi_diem_het_han IS NULL OR au.thoi_diem_het_han>SYSUTCDATETIME()))
     THEN 1 ELSE 0 END canSign,
    CONVERT(varchar(42),q.thoi_diem_yeu_cau,127) createdAt,CONVERT(varchar(42),q.thoi_diem_hoan_tat,127) completedAt
   FROM dbo.yeu_cau_ky q JOIN dbo.phien_ban_van_ban v ON v.id=q.phien_ban_van_ban_id
   JOIN dbo.van_ban d ON d.id=v.van_ban_id
   JOIN dbo.mghp_lien_ket_phien_ban_van_ban l ON l.phien_ban_van_ban_dung_chung_id=v.id
   LEFT JOIN dbo.mghp_bien_ban bb ON bb.id=l.bien_ban_id
   JOIN dbo.mghp_phien_ban_qd qv ON qv.id=COALESCE(l.phien_ban_qd_id,bb.phien_ban_qd_nguon_id)
   JOIN dbo.mghp_quyet_dinh root ON root.id=qv.quyet_dinh_id JOIN dbo.mghp_dot_xet rd ON rd.id=root.dot_xet_id
   JOIN dbo.hoc_ky h ON h.id=rd.hoc_ky_id
   OUTER APPLY(SELECT TOP(1) s.id,s.thu_tu_ky,s.vai_tro_nguoi_ky,s.nguoi_dung_noi_bo_id,s.trang_thai FROM dbo.buoc_ky s WHERE s.yeu_cau_ky_id=q.id AND s.trang_thai<>N'completed' ORDER BY s.thu_tu_ky,s.id) cur
   OUTER APPLY(SELECT TOP(1) a.tep_dinh_kem_id fileId FROM dbo.tep_ket_qua_ky a WHERE a.yeu_cau_ky_id=q.id AND a.trang_thai_toan_ven=N'verified' ORDER BY a.id DESC) latest
   JOIN dbo.tep_dinh_kem f ON f.id=COALESCE(latest.fileId,v.tep_goc_id)
   """+listFilter(manager,pp,pht,mine,status,view)+" ORDER BY q.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",user,status,(long)page*size,size);
 }
 Map<String,Object> context(long version){
  var rows=jdbc.queryForList("""
   SELECT CONVERT(varchar(20),rd.id) roundId,rd.ten roundName,h.ten semesterName,l.loai_van_ban documentType,CONVERT(varchar(20),v.id) decisionVersionId,
    CONVERT(varchar(42),rd.ky_du_kien_tu,127) signingPlannedStartAt,CONVERT(varchar(42),rd.ky_du_kien_den,127) signingPlannedEndAt,
    CASE WHEN rd.ky_du_kien_tu IS NULL AND rd.ky_du_kien_den IS NULL THEN N'unscheduled' WHEN rd.ky_du_kien_den<SYSDATETIMEOFFSET() THEN N'overdue' WHEN rd.ky_du_kien_tu>SYSDATETIMEOFFSET() THEN N'upcoming' ELSE N'ongoing' END signingScheduleStatus,
    CONVERT(varchar(20),linked.id) relatedDocumentVersionId,CONVERT(varchar(20),qr.id) relatedRequestId
   FROM dbo.mghp_lien_ket_phien_ban_van_ban l
   LEFT JOIN dbo.mghp_bien_ban b ON b.id=l.bien_ban_id
   JOIN dbo.mghp_phien_ban_qd v ON v.id=COALESCE(l.phien_ban_qd_id,b.phien_ban_qd_nguon_id)
   JOIN dbo.mghp_quyet_dinh root ON root.id=v.quyet_dinh_id
   JOIN dbo.mghp_dot_xet rd ON rd.id=root.dot_xet_id JOIN dbo.hoc_ky h ON h.id=rd.hoc_ky_id
   OUTER APPLY(SELECT TOP(1) dl.phien_ban_van_ban_dung_chung_id id FROM dbo.mghp_bien_ban mb
    JOIN dbo.mghp_lien_ket_phien_ban_van_ban dl ON dl.bien_ban_id=mb.id
    JOIN dbo.yeu_cau_ky rq ON rq.phien_ban_van_ban_id=dl.phien_ban_van_ban_dung_chung_id
    WHERE l.phien_ban_qd_id IS NOT NULL AND mb.phien_ban_qd_nguon_id=v.id AND dl.trang_thai=N'current' AND rq.trang_thai=N'completed' AND rq.phien_ban_adapter=N'ctsv-p11-v1' ORDER BY dl.id DESC) linked
   LEFT JOIN dbo.yeu_cau_ky qr ON qr.phien_ban_van_ban_id=linked.id AND qr.phien_ban_adapter=N'ctsv-p11-v1'
   WHERE l.phien_ban_van_ban_dung_chung_id=?
   """,version);
  if(rows.isEmpty())throw IntakeProblem.missing();var row=rows.getFirst();var result=new LinkedHashMap<String,Object>();
  for(String key:List.of("roundId","roundName","semesterName","documentType","decisionVersionId","signingPlannedStartAt","signingPlannedEndAt","signingScheduleStatus"))result.put(key,row.get(key));
  result.put("relatedMinutes",row.get("relatedRequestId")==null?null:Map.of("documentVersionId",row.get("relatedDocumentVersionId"),"requestId",row.get("relatedRequestId")));
  return result;
 }
}
