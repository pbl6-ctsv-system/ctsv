import {useCallback,useEffect,useRef,useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {PdfViewer,type PdfGeometry} from '../../../shared/ui/pdf';
import {Button} from '../../../shared/ui/Button';
import {Alert} from '../../../shared/ui/Alert';
import type {AssignmentsApi} from '../api/assignments';
import {assignmentError} from '../api/errors';
import type {Draft,Faculty,PdfAdminContent,PdfFileInfo,SigningDossier} from '../types/assignments';
import {statusLabels} from '../model/draft.mjs';
import {
 EMPTY_PDF_ADMIN_CONTENT,
 canPlaceSignatureBox,
 caErrorMessage,
 dossierSelectionKey,
 historyActionLabel,
 isPdfAdminContentDirty,
 isSigningWorking,
 normalizePdfAdminContent,
 signingPhaseDescription,
 signingStatusLabel,
} from '../model/assignment-signing.mjs';
import {formatTime} from './Controls';
import {IndustryAssignments} from './IndustryAssignments';
import {PdfAdminContentForm} from './PdfAdminContentForm';
import {AssignmentActionDialog} from './AssignmentActionDialog';
import styles from '../pages/Assignments.module.css';

type ActiveTab='pdf'|'data'|'history';
type ModalKind='sign'|'submit'|'reopen'|null;

export function AssignmentSigningPanel({
 client,
 api,
 draft,
 initialDossier,
 faculty,
 onBack,
 onDraftChanged,
}:{
 client:AuthClient;
 api:AssignmentsApi;
 draft:Draft;
 initialDossier:SigningDossier|null;
 faculty:Faculty;
 onBack:()=>void;
 onDraftChanged:(next:Draft)=>void;
}){
 const [dossier,setDossier]=useState<SigningDossier|null>(initialDossier);
 const [metaBase,setMetaBase]=useState<PdfAdminContent>(()=>normalizePdfAdminContent(initialDossier?.noiDungHanhChinh??EMPTY_PDF_ADMIN_CONTENT));
 const [metaDraft,setMetaDraft]=useState<PdfAdminContent>(()=>normalizePdfAdminContent(initialDossier?.noiDungHanhChinh??EMPTY_PDF_ADMIN_CONTENT));
 const [tab,setTab]=useState<ActiveTab>('pdf');
 const [selection,setSelection]=useState<PdfGeometry|null>(null);
 const [previewHistorical,setPreviewHistorical]=useState<PdfFileInfo|null>(null);
 const [modal,setModal]=useState<ModalKind>(null);
 const [reopenReason,setReopenReason]=useState('');
 const [reconcileReason,setReconcileReason]=useState('');
 const [loading,setLoading]=useState(!initialDossier);
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState('');
 const [notice,setNotice]=useState('');
 const [staleConflict,setStaleConflict]=useState(false);
 const [needsRead,setNeedsRead]=useState(false);
 const [pollingError,setPollingError]=useState(false);
 const flight=useRef(false);
 const live=useRef(true);

 const applyDossier=useCallback((d:SigningDossier)=>{
  setDossier(d);
  const normalized=normalizePdfAdminContent(d.noiDungHanhChinh??EMPTY_PDF_ADMIN_CONTENT);
  setMetaBase(normalized);
  setMetaDraft(normalized);
  setPreviewHistorical(null);
  setStaleConflict(false);
  setNeedsRead(false);
  setPollingError(false);
 },[]);

 const loadAll=useCallback(async(announce=false)=>{
  setLoading(true);
  setError('');
  try{
   const [freshDraft,freshDossier]=await Promise.all([
    api.detail(draft.id),
    api.signingDossier(draft.id),
   ]);
   if(!live.current)return;
   onDraftChanged(freshDraft);
   applyDossier(freshDossier);
   if(announce)setNotice('Đã đồng bộ dữ liệu và tiến trình mới nhất từ hệ thống.');
  }catch(e){
   if(!live.current)return;
   setError(assignmentError(e));
  }finally{
   if(live.current)setLoading(false);
  }
 },[api,draft.id,onDraftChanged,applyDossier]);

 useEffect(()=>{
  live.current=true;
  if(!initialDossier){
   void loadAll(false);
  }
  return()=>{live.current=false;};
 },[initialDossier,loadAll]);

 const selKey=dossierSelectionKey(dossier);
 useEffect(()=>{
  setSelection(null);
  setModal(m=>m==='sign'?null:m);
 },[selKey]);

 const working=isSigningWorking(dossier);
 useEffect(()=>{
  if(!dossier||!working)return;
  let cancelled=false;
  let timer:ReturnType<typeof setTimeout>;
  const id=draft.id;
  async function poll(){
   if(cancelled)return;
   if(flight.current){
    timer=setTimeout(()=>void poll(),2000);
    return;
   }
   try{
    const nextDossier=await api.signingDossier(id);
    if(cancelled||!live.current)return;
    setDossier(nextDossier);
    setPollingError(false);
    if(!isSigningWorking(nextDossier)){
     const nextDraft=await api.detail(id);
     if(cancelled||!live.current)return;
     onDraftChanged(nextDraft);
     if(nextDossier.trangThaiDanhSach==='DA_KY'){
      setPreviewHistorical(null);
      setNotice('Đã ký số thành công và lưu bản PDF đã ký tại CTSV. Vui lòng kiểm tra lại bản PDF trước khi gửi chuyên viên.');
     }else if(nextDossier.yeuCauKy?.errorCode){
      setError(caErrorMessage(nextDossier.yeuCauKy.errorCode));
     }
     return;
    }
    timer=setTimeout(()=>void poll(),2500);
   }catch(e){
    if(!cancelled&&live.current){
     setPollingError(true);
     setError(assignmentError(e));
    }
   }
  }
  timer=setTimeout(()=>void poll(),1800);
  return()=>{cancelled=true;clearTimeout(timer);};
 },[api,draft.id,dossier,working,onDraftChanged]);

 const dirtyPdfMeta=isPdfAdminContentDirty(metaBase,metaDraft);
 const selectable=canPlaceSignatureBox({
  dossier,
  faculty,
  dirtyPdfMeta,
  historicalPreview:!!previewHistorical,
  busy:busy||loading||staleConflict||needsRead,
 });

 const defaultFile=dossier?.tepDaKy??dossier?.tepGoc??null;
 const activeFile=previewHistorical??defaultFile;
 const currentState=dossier?.trangThaiDanhSach??draft.trangThai;
 const req=dossier?.yeuCauKy??null;

 async function runWrite(action:()=>Promise<void>,markUncertainOnNetwork=false){
  if(flight.current)return;
  flight.current=true;
  setBusy(true);
  setError('');
  setNotice('');
  try{
   await action();
  }catch(e){
   if(!live.current)return;
   const err=e as {status?:number;code?:string};
   setError(assignmentError(e));
   if(err.code==='VERSION_CONFLICT'||err.status===409&&err.code==='VERSION_CONFLICT'||err.status===412){
    setStaleConflict(true);
   }else if(markUncertainOnNetwork&&!err.status){
    setNeedsRead(true);
   }
  }finally{
   flight.current=false;
   if(live.current)setBusy(false);
  }
 }

 async function handleUpdatePdfContent(){
  if(!dossier)return;
  await runWrite(async()=>{
   const updated=await api.updatePdfContent(draft.id,dossier.version,normalizePdfAdminContent(metaDraft));
   const refreshedDraft=await api.detail(draft.id);
   if(!live.current)return;
   onDraftChanged(refreshedDraft);
   applyDossier(updated);
   setSelection(null);
   setNotice('Đã cập nhật chữ hành chính và render lại tệp PDF mới. Hãy kiểm tra PDF và chọn vị trí chữ ký.');
  });
 }

 async function handleConfirmSign(){
  if(!dossier||!selection)return;
  const expectedKey=selKey;
  await runWrite(async()=>{
   if(dossierSelectionKey(dossier)!==expectedKey){
    throw new Error('Hồ sơ PDF đã thay đổi. Hãy đọc lại trạng thái trước khi ký.');
   }
   const updated=await api.signPdf(draft.id,dossier.version,selection);
   if(!live.current)return;
   setDossier(updated);
   setSelection(null);
   setModal(null);
   setNotice('Đã ghi nhận yêu cầu ký số của Trưởng khoa. Hệ thống đang gọi CA DUT và lưu tệp PDF đã ký.');
  },true);
 }

 async function handleReconcile(e:React.FormEvent){
  e.preventDefault();
  if(!dossier)return;
  const cleanReason=reconcileReason.trim();
  if(!cleanReason){
   setError('Nhập lý do / ghi chú đối soát trước khi gửi yêu cầu.');
   return;
  }
  await runWrite(async()=>{
   const updated=await api.reconcileSigning(draft.id,dossier.version,cleanReason);
   const refreshedDraft=await api.detail(draft.id);
   if(!live.current)return;
   onDraftChanged(refreshedDraft);
   applyDossier(updated);
   setReconcileReason('');
   setNotice('Đã thực hiện đối soát tiến trình ký số.');
  },true);
 }

 async function handleReopenDraft(){
  if(!dossier)return;
  await runWrite(async()=>{
   const reopened=await api.reopenDraft(draft.id,dossier.version,reopenReason.trim()||null);
   if(!live.current)return;
   setModal(null);
   setReopenReason('');
   onDraftChanged(reopened);
  });
 }

 async function handleSubmitSpecialist(){
  if(!dossier)return;
  await runWrite(async()=>{
   const submitted=await api.submitSpecialist(draft.id,dossier.version);
   const nextDossier=await api.signingDossier(draft.id);
   if(!live.current)return;
   setModal(null);
   onDraftChanged(submitted);
   applyDossier(nextDossier);
   setNotice('Đã gửi danh sách phân công GVCN (kèm bản PDF đã ký số) cho Chuyên viên Phòng CTSV.');
  });
 }

 return (
  <section className={styles.workspace} aria-busy={busy||loading}>
   <div className={styles.topBar}>
    <Button
     type="button"
     variant="secondary"
     disabled={busy}
     onClick={()=>{
      if(!dirtyPdfMeta||window.confirm('Bỏ phần chữ hành chính đang sửa dở và quay về danh sách?')){
       onBack();
      }
     }}
    >
     ← Danh sách phân công
    </Button>
    <Button type="button" variant="secondary" disabled={busy||loading} onClick={()=>void loadAll(true)}>
     Đọc lại trạng thái
    </Button>
   </div>

   <div className={styles.heading}>
    <div>
     <h1>Phân công GVCN — Phiên bản {draft.phienBan}</h1>
     <p className={styles.muted}>
      {faculty.tenKhoa} · Năm học {draft.namHoc} · {draft.soLop} lớp đã phân công
     </p>
    </div>
    <span className={styles.badge}>
     {statusLabels[currentState as keyof typeof statusLabels]??currentState}
    </span>
   </div>

   {error&&<Alert>{error}</Alert>}
   {notice&&<p className={styles.successBanner} role="status">{notice}</p>}
   {staleConflict&&(
    <section className={styles.warning}>
     <h2>Dữ liệu đã thay đổi ở phiên khác</h2>
     <p>Hệ thống không tự động gửi đè thao tác cũ. Hãy tải lại dữ liệu mới nhất để đối chiếu trước khi tiếp tục.</p>
     <Button type="button" variant="secondary" disabled={busy} onClick={()=>void loadAll(true)}>
      Đọc lại dữ liệu mới nhất
     </Button>
    </section>
   )}
   {needsRead&&(
    <section className={styles.warning}>
     <p>Kết nối vừa gián đoạn khi gửi yêu cầu. Hãy bấm <strong>Đọc lại trạng thái</strong> để kiểm tra trước khi thực hiện lại.</p>
    </section>
   )}

   <div className={styles.tabs} role="tablist" aria-label="Không gian hồ sơ phân công">
    <button type="button" role="tab" aria-selected={tab==='pdf'} onClick={()=>setTab('pdf')}>
     Tài liệu PDF &amp; Ký số
    </button>
    <button type="button" role="tab" aria-selected={tab==='data'} onClick={()=>setTab('data')}>
     Dữ liệu phân công ({draft.cacPhanCong.length} lớp)
    </button>
    <button type="button" role="tab" aria-selected={tab==='history'} onClick={()=>setTab('history')}>
     Lịch sử &amp; Phiên bản PDF
    </button>
   </div>

   {loading&&!dossier?(
    <p role="status">Đang tải hồ sơ PDF và trạng thái ký số…</p>
   ):tab==='pdf'?(
    <div className={styles.signingGrid}>
     <div className={styles.pdfColumn}>
      {dossier?.tepDaKy&&dossier?.tepGoc&&(
       <div className={styles.versionSwitch} role="group" aria-label="Chọn bản PDF hiển thị">
        <span>Đang xem:</span>
        <button
         type="button"
         aria-pressed={!previewHistorical}
         onClick={()=>{setPreviewHistorical(null);setSelection(null);}}
        >
         Bản PDF đã ký số (Hiện hành)
        </button>
        <button
         type="button"
         aria-pressed={previewHistorical?.id===dossier.tepGoc.id}
         onClick={()=>{setPreviewHistorical(dossier.tepGoc);setSelection(null);}}
        >
         Xem đối chiếu PDF gốc trước ký
        </button>
       </div>
      )}
      {previewHistorical&&!dossier?.tepDaKy&&(
       <div className={styles.warning}>
        <p>Đang xem bản PDF lịch sử (chỉ đọc). Không thể chọn vùng ký trên bản cũ.</p>
        <Button type="button" variant="secondary" onClick={()=>setPreviewHistorical(null)}>
         Về bản PDF hiện hành
        </Button>
       </div>
      )}
      {activeFile?(
       <PdfViewer
        client={client}
        fileId={activeFile.id}
        title={activeFile.fileName}
        fileName={activeFile.fileName}
        selectable={selectable}
        selection={selection}
        onSelectionChange={setSelection}
       />
      ):(
       <section className={styles.sideCard}>
        <p>Chưa có tệp PDF gắn với phiên bản này. Hãy bấm <strong>Đọc lại trạng thái</strong> để kiểm tra.</p>
       </section>
      )}
     </div>

     <aside className={styles.sideColumn}>
      {currentState==='CHO_KY'&&req?.canUpdatePdfContent&&faculty.duocKySo&&(
       <PdfAdminContentForm
        tenKhoaGocTraiPdf={dossier?.tenKhoaGocTraiPdf??faculty.tenKhoa.toUpperCase()}
        value={metaDraft}
        dirty={dirtyPdfMeta}
        busy={busy}
        disabled={staleConflict||needsRead}
        onChange={setMetaDraft}
        onReset={()=>setMetaDraft(metaBase)}
        onSave={handleUpdatePdfContent}
       />
      )}

      <section className={styles.sideCard}>
       <h2>{currentState==='CHO_KY'?'2. Ký số Trưởng khoa':'Trạng thái ký & Phát hành'}</h2>
       {req?.mode==='TEST'&&(
        <p className={styles.testBanner}>
         Môi trường ký thử nghiệm (TEST): hệ thống dùng chứng thư kiểm thử CA DUT đã cấu hình để xác minh quy trình kỹ thuật.
        </p>
       )}
       {req&&(
        <p>
         <strong>Tiến trình:</strong> {signingStatusLabel(req.status)}
        </p>
       )}
       <p className={styles.muted}>{signingPhaseDescription(dossier)}</p>

       {req?.step&&(
        <dl className={styles.metaSummary}>
         <dt>Chức danh ký</dt>
         <dd>TRƯỞNG KHOA</dd>
         {req.step.signerName&&<><dt>Người ký</dt><dd>{req.step.signerName}</dd></>}
         {req.step.signerEmail&&<><dt>Email chứng thư</dt><dd>{req.step.signerEmail}</dd></>}
         {req.step.signedAt&&<><dt>Thời điểm ký</dt><dd>{formatTime(req.step.signedAt)}</dd></>}
         {dossier?.ngayGuiChuyenVien&&<><dt>Đã gửi CTSV lúc</dt><dd>{formatTime(dossier.ngayGuiChuyenVien)}</dd></>}
        </dl>
       )}

       {currentState==='CHO_KY'&&req?.canSign&&faculty.duocKySo&&(
        dirtyPdfMeta?(
         <p className={styles.warning}>
          Bạn vừa sửa phần chữ hành chính nhưng chưa cập nhật lên PDF. Hãy bấm <strong>Cập nhật bản PDF</strong> (hoặc <strong>Hoàn tác chữ</strong>) trước khi chọn vùng chữ ký.
         </p>
        ):(
         <div className={styles.signActionBox}>
          <p className={styles.muted}>
           {selection
            ?`Đã chọn vùng chữ ký tại Trang ${selection.page}. Kiểm tra vị trí khung trên PDF trước khi bấm Ký số.`
            :'Chuyển đến trang cuối PDF, bấm "Chọn vị trí" trên thanh công cụ PDF và kéo khung dưới chữ TRƯỞNG KHOA.'}
          </p>
          <Button
           type="button"
           disabled={busy||staleConflict||needsRead||!selection||!!previewHistorical}
           onClick={()=>{setError('');setModal('sign');}}
          >
           Ký số
          </Button>
         </div>
        )
       )}

       {working&&(
        <p role="status" className={styles.warning}>
         {pollingError
          ?'Tạm thời chưa đọc được tiến độ mới. Hãy bấm "Đọc lại trạng thái", không gửi ký lại.'
          :'Đang xử lý ký số nền và lưu tệp PDF về CTSV…'}
        </p>
       )}

       {currentState==='CHO_KY'&&(req?.status==='unknown'||req?.canReconcile)&&faculty.duocKySo&&(
        <form className={styles.reconcileForm} onSubmit={e=>{void handleReconcile(e);}}>
         <h3>Đối soát / Phục hồi tiến trình ký</h3>
         {req.errorCode&&<p className={styles.fieldError}>{caErrorMessage(req.errorCode)}</p>}
         <label>
          Lý do / ghi chú đối soát *
          <textarea
           required
           maxLength={4000}
           disabled={busy||staleConflict}
           placeholder="Ghi rõ tình huống kiểm tra để lưu nhật ký đối soát"
           value={reconcileReason}
           onChange={e=>setReconcileReason(e.target.value)}
          />
         </label>
         <Button type="submit" disabled={busy||staleConflict||!reconcileReason.trim()}>
          {busy?'Đang đối soát…':'Đối soát kết quả ký'}
         </Button>
        </form>
       )}

       {currentState==='DA_KY'&&req?.canSendSpecialist&&faculty.duocGuiChuyenVien&&(
        <div className={styles.signActionBox}>
         <p>
          Kiểm tra kỹ chữ ký số hiển thị trên bản PDF bên trái, sau đó gửi chính thức sang Phòng Công tác Sinh viên.
         </p>
         <Button
          type="button"
          disabled={busy||staleConflict||needsRead}
          onClick={()=>{setError('');setModal('submit');}}
         >
          Gửi chuyên viên CTSV
         </Button>
        </div>
       )}
      </section>

      {currentState==='CHO_KY'&&req?.canReopenDraft&&faculty.duocLapNhap&&(
       <section className={styles.sideCard}>
        <h3>Sai dữ liệu Lớp hoặc GVCN?</h3>
        <p className={styles.muted}>
         Bảng phân công Lớp–GVCN đang khóa theo dữ liệu đã chốt. Nếu cần đổi GVCN hoặc ghi chú lớp, hãy mở khóa quay về bản nháp trên chính Phiên bản {draft.phienBan} này.
        </p>
        <Button
         type="button"
         variant="danger"
         disabled={busy||staleConflict||needsRead}
         onClick={()=>{setError('');setModal('reopen');}}
        >
         Hủy chờ ký để sửa phân công
        </Button>
       </section>
      )}
     </aside>
    </div>
   ):tab==='data'?(
    <section className={styles.sideCard}>
     <p className={styles.muted}>
      Đề nghị hiệu lực: {formatTime(draft.hieuLucTu)} → {formatTime(draft.hieuLucDen)}
      {draft.lyDoDieuChinh?` · Lý do: ${draft.lyDoDieuChinh}`:''}
     </p>
     <IndustryAssignments
      api={api}
      faculty={draft.maKhoa}
      rows={draft.cacPhanCong}
      writable={false}
      disabled={true}
      showReview={true}
      onChange={()=>{}}
      onFailure={()=>{}}
     />
    </section>
   ):(
    <section className={styles.sideCard}>
     <h2>Các bản PDF của Phiên bản {draft.phienBan}</h2>
     {dossier?.cacPhienBanPdf?.length?(
      <ul className={styles.historyList}>
       {dossier.cacPhienBanPdf.map(v=>(
        <li key={v.phienBanVanBanId}>
         <div>
          <strong>Bản PDF #{v.soPhienBan} {v.current?'(Hiện hành)':'(Bản trước — đã thay thế)'}</strong>
          <small>{v.title} · Tạo lúc {formatTime(v.createdAt)}</small>
         </div>
         <div className={styles.actions}>
          {v.tepGoc&&(
           <Button
            type="button"
            variant="secondary"
            onClick={()=>{setPreviewHistorical(v.current&&!dossier.tepDaKy?null:v.tepGoc);setTab('pdf');}}
           >
            Xem PDF gốc
           </Button>
          )}
          {v.tepDaKy&&(
           <Button
            type="button"
            variant="secondary"
            onClick={()=>{setPreviewHistorical(v.current?null:v.tepDaKy);setTab('pdf');}}
           >
            Xem PDF đã ký
           </Button>
          )}
         </div>
        </li>
       ))}
      </ul>
     ):(
      <p className={styles.muted}>Chưa có bản PDF nào được lưu.</p>
     )}

     <h3>Nhật ký thao tác hồ sơ</h3>
     {dossier?.lichSu?.length?(
      <ul className={styles.historyList}>
       {dossier.lichSu.map(item=>(
        <li key={item.id}>
         <div>
          <strong>{historyActionLabel(item.action)}</strong>
          <small>{formatTime(item.at)}</small>
          {item.reason&&<p>{item.reason}</p>}
         </div>
        </li>
       ))}
      </ul>
     ):(
      <p className={styles.muted}>Chưa có nhật ký thao tác.</p>
     )}
    </section>
   )}

   {modal==='sign'&&selection&&dossier&&(
    <AssignmentActionDialog title="Xác nhận ký số Trưởng khoa" busy={busy} onClose={()=>setModal(null)}>
     <p>
      Xác nhận thực hiện ký số cho <strong>Phân công GVCN — Phiên bản {draft.phienBan}</strong> ({faculty.tenKhoa} · Năm học {draft.namHoc})?
     </p>
     <dl className={styles.metaSummary}>
      <dt>Chức danh ký</dt>
      <dd>TRƯỞNG KHOA</dd>
      <dt>Email chứng thư CA</dt>
      <dd>{req?.step?.signerEmail??'Theo cấu hình hệ thống'}</dd>
      <dt>Vị trí khung chữ ký</dt>
      <dd>Trang {selection.page} trên bản PDF hiện hành</dd>
     </dl>
     {req?.mode==='TEST'&&(
      <p className={styles.testBanner}>
       Đang chạy chế độ ký thử nghiệm (TEST) với chứng thư kiểm thử CA DUT.
      </p>
     )}
     <div className={styles.actions}>
      <Button type="button" variant="secondary" disabled={busy} onClick={()=>setModal(null)}>
       Kiểm tra lại
      </Button>
      <Button type="button" disabled={busy} onClick={()=>void handleConfirmSign()}>
       {busy?'Đang gửi yêu cầu ký…':'Xác nhận ký'}
      </Button>
     </div>
    </AssignmentActionDialog>
   )}

   {modal==='reopen'&&dossier&&(
    <AssignmentActionDialog title="Mở khóa lại bản nháp để chỉnh sửa?" busy={busy} onClose={()=>setModal(null)}>
     <p>
      <strong>Phiên bản {draft.phienBan}</strong> sẽ chuyển từ trạng thái <em>Chờ ký</em> về lại <em>Nháp</em> (giữ nguyên Phiên bản {draft.phienBan}, không sinh phiên bản mới). Bản PDF chờ ký hiện tại sẽ hết hiệu lực; sau khi sửa xong lớp/GVCN bạn cần bấm xuất PDF lại.
     </p>
     <label>
      Lý do hủy chờ ký (không bắt buộc)
      <textarea
       maxLength={4000}
       disabled={busy}
       value={reopenReason}
       onChange={e=>setReopenReason(e.target.value)}
      />
     </label>
     <div className={styles.actions}>
      <Button type="button" variant="secondary" disabled={busy} onClick={()=>setModal(null)}>
       Đóng
      </Button>
      <Button type="button" variant="danger" disabled={busy} onClick={()=>void handleReopenDraft()}>
       {busy?'Đang mở lại nháp…':'Mở lại bản nháp'}
      </Button>
     </div>
    </AssignmentActionDialog>
   )}

   {modal==='submit'&&dossier&&(
    <AssignmentActionDialog title="Gửi danh sách phân công cho Chuyên viên CTSV" busy={busy} onClose={()=>setModal(null)}>
     <p>
      Xác nhận gửi <strong>Phiên bản {draft.phienBan}</strong> (Năm học {draft.namHoc}, đã ký số hợp lệ) đến Phòng Công tác Sinh viên? Sau khi gửi, phiên bản này được khóa chính thức để Chuyên viên CTSV sử dụng làm nguồn phân công.
     </p>
     <div className={styles.actions}>
      <Button type="button" variant="secondary" disabled={busy} onClick={()=>setModal(null)}>
       Xem lại PDF
      </Button>
      <Button type="button" disabled={busy} onClick={()=>void handleSubmitSpecialist()}>
       {busy?'Đang gửi chuyên viên…':'Xác nhận gửi CTSV'}
      </Button>
     </div>
    </AssignmentActionDialog>
   )}
  </section>
 );
}
