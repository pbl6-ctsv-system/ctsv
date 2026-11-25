import {useEffect,useRef,useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {errorMessage} from '../../../shared/api/error-message';
import {Alert} from '../../../shared/ui/Alert';
import {claimLabels,dateTime} from '../presentation.mjs';
import {PdfViewer} from '../../../shared/ui/pdf';
import {currentCheck,pdfAccepted,nextPendingPaper,approvalBlockers} from './review-model.mjs';
import type {Dossier} from './dossier-types';
import styles from './IntakeWorkspace.module.css';

type Check={id:string;kind:string;result:string;note:string|null;checkedAt:string};
type Paper={id:string;submissionId:string;requirementId:string;fileId:string;documentNumber:string|null;checks:Check[]};
type Review={documents:Paper[];comparisons:{field:string;declaredValue:string|null;sourceValue:string|null;status:string|null}[];evaluations:{status:string}[]};
const checkNames:Record<string,string>={pass:'Đã đồng ý PDF',fail:'PDF chưa được đồng ý',needs_verification:'Cần xác minh PDF',not_applicable:'Không áp dụng'};
function failure(error:unknown){
 if(error instanceof ApiError){
  if(error.code==='INTAKE_STAGE_CLOSED')return 'Đợt đã chốt hồ sơ online. Bước tiếp nhận chỉ còn xem lại; tiếp tục xử lý tại Đối chiếu bản cứng.';
  if(error.status===412)return 'Hồ sơ đã thay đổi. Hãy đọc lại hồ sơ trước khi thao tác.';
  if(error.code==='APPLICATION_REVIEW_INCOMPLETE')return 'Chưa thể đồng ý hồ sơ. Hãy kiểm tra các PDF mới nhất và xử lý yêu cầu bổ sung đang chờ.';
  if(error.code==='APPLICATION_NOT_REVIEWABLE')return 'Hồ sơ đã kết thúc hoặc đã có đánh giá xác nhận; không thể đổi kết luận tại đây.';
 }
 return errorMessage(error);
}

export function StaffDossierReview({client,application,rootTag,refresh,onSupplement,declaredNames}:{client:AuthClient;application:Dossier;rootTag:string|null;declaredNames?:Record<string,string|null>;
 refresh:()=>Promise<void>;onSupplement:()=>void}) {
 const [review,setReview]=useState<Review|null>(null),[selected,setSelected]=useState('');
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState(''),[reason,setReason]=useState('');
 const [decision,setDecision]=useState<'approved'|'rejected'|null>(null);
 const [names,setNames]=useState<Record<string,Record<string,string>>>({});
 const [labelError,setLabelError]=useState(false);
 const dialog=useRef<HTMLDialogElement>(null),running=useRef(false);
 const loadedApplication=useRef(''),[reviewTag,setReviewTag]=useState<string|null>(null);
 useEffect(()=>{
  let live=true;setError('');
  // A check changes metadata, not PDF bytes. Keep the viewer mounted for this root,
  // but fence every mutation until the fresh review context matches the new ETag.
  if(loadedApplication.current!==application.id){setReview(null);setSelected('');loadedApplication.current=application.id;}
  const url='/api/ctsv/mghp/ho-so/'+application.id;
  void Promise.all([client.request(url+'/ngu-canh-xet-duyet').then(r=>r.json() as Promise<Review>),client.request(url)]).then(([data,after])=>{
   if(!live)return;if(after.headers.get('ETag')!==rootTag)throw new Error('Hồ sơ đã thay đổi. Hãy đọc lại hồ sơ.');setReview(data);setReviewTag(rootTag);
  }).catch(e=>{if(live)setError(failure(e));});
  return ()=>{live=false;};
 },[client,application.id,rootTag]);
 useEffect(()=>{let live=true;setNames({});setLabelError(false);if(!review)return;const kinds:Record<string,string>={ethnicityId:'ethnicities',classId:'classes',cohortId:'cohorts',managementUnitId:'units'};
  void Promise.all(Object.entries(kinds).map(async([field,kind])=>{const ids=[...new Set(review.comparisons.filter(c=>c.field===field).flatMap(c=>[c.declaredValue,c.sourceValue]).filter((v):v is string=>!!v&&/^[1-9]\d*$/.test(v)))];if(!ids.length)return [field,{}] as const;const r=await client.request('/api/ctsv/nen-tang/danh-muc?kind='+kind+'&ids='+ids.join(','));const data=await r.json();return [field,Object.fromEntries(data.items.map((item:{id:string;name:string|null;code:string|null})=>[item.id,item.name||item.code||'Chưa có tên']))] as const;})).then(entries=>{if(live)setNames(Object.fromEntries(entries));}).catch(()=>{if(live)setLabelError(true);});return()=>{live=false;};
 },[client,review]);
 function comparisonValue(field:string,value:string|null,declared=false){return (declared?declaredNames?.[field]:null)||names[field]?.[value??'']||value||'Chưa có nguồn';}
 useEffect(()=>{if(decision)dialog.current?.showModal();else dialog.current?.close();},[decision]);
 const latest=application.submittedVersions[0];
 const papers=review?.documents.filter(d=>d.submissionId===latest?.id)??[];
 const paper=papers.find(d=>d.id===selected)??papers[0];
 const pending=['submitted','processing','supplementing','supplement_responded'].includes(application.status??'');
 const locked=!!review?.evaluations.some(e=>e.status==='confirmed');
 const loadingReview=reviewTag!==rootTag;
 const editable=pending&&!locked&&!application.intakeSealed&&!loadingReview;
 const blockers=approvalBlockers(papers,application.supplementRequests??[]);
 const blocked=!papers.length||blockers.pendingPdfIds.length>0||blockers.otherCheckIds.length>0||blockers.supplementIds.length>0;
 const title=(p:Paper)=>application.documentRequirements?.find(r=>r.id===p.requirementId)?.name||p.documentNumber||'Minh chứng PDF';
 const lastCheck=(p:Paper)=>currentCheck(p,'content');
 async function run(action:()=>Promise<void>){if(running.current)return;running.current=true;setBusy(true);setError('');setMessage('');try{await action();}catch(e){setError(failure(e));}finally{running.current=false;setBusy(false);}}
 async function check(kind='content'){
  if(!paper||!rootTag||loadingReview)return;
  const next=kind==='content'?nextPendingPaper(papers,paper.id):null;
  await run(async()=>{
   const hardCopy=kind==='hard_copy';
   await client.request('/api/ctsv/mghp/giay-to-da-nop/'+paper.id+(hardCopy?'/xac-nhan-ban-cung':'/kiem-tra'),{method:'POST',headers:{'Content-Type':'application/json','If-Match':rootTag},
    body:JSON.stringify(hardCopy?{received:true,note:null}:{kind,result:'pass',note:null,replacesCheckId:currentCheck(paper,kind)?.id??null})});
   await refresh();if(next)setSelected(next);
   setMessage(kind==='content'?(next?'Đã đồng ý PDF. Đang xem giấy tờ chưa đồng ý tiếp theo.':'Đã đồng ý PDF. Không còn giấy tờ chưa đồng ý.'):'Đã xác nhận kiểm tra giấy tờ.');
  });
 }
 async function conclude(){
  if(!rootTag||!decision||loadingReview)return;const result=decision;
  await run(async()=>{
   await client.request('/api/ctsv/mghp/ho-so/'+application.id+'/ket-luan',{method:'POST',headers:{'Content-Type':'application/json','If-Match':rootTag},body:JSON.stringify(result==='rejected'?{result,reason}:{result})});
   setDecision(null);await refresh();setMessage(result==='approved'?'Đã duyệt hồ sơ online (V1).':'Đã từ chối hồ sơ và lưu lý do.');
  });
 }
 const responded=application.supplementRequests?.find(s=>s.status==='responded');
 return <section className={styles.taskGroup} aria-label="Xử lý hồ sơ chuyên viên" aria-busy={busy}>
  {error&&<Alert>{error}</Alert>}{message&&<p className={styles.success} role="status">{message}</p>}
  {loadingReview&&<p role="status">Đang cập nhật thông tin kiểm tra hồ sơ…</p>}
  {responded&&<aside className={styles.requestNote}><strong>Sinh viên đã trả lời yêu cầu bổ sung lần {responded.roundNumber}</strong><p>{responded.reason}</p><span>Nhận phản hồi: {dateTime(responded.respondedAt)}</span></aside>}
  <section className={styles.contentBlock}><h3>Đối chiếu thông tin</h3>
   {labelError&&<p role="status">Chưa đọc được tên danh mục; đang hiển thị giá trị nguồn gốc. Đọc lại hồ sơ để thử lại.</p>}
   {!review?<p role="status">Đang đọc thông tin đối chiếu…</p>:<div className={styles.reviewTable}><table><thead><tr><th>Thông tin</th><th>Đã kê khai</th><th>Nguồn đào tạo</th><th>Kết quả</th></tr></thead>
    <tbody>{review.comparisons.map(c=><tr key={c.field}><th>{claimLabels[c.field as keyof typeof claimLabels]??c.field}</th><td>{comparisonValue(c.field,c.declaredValue,true)}</td><td>{comparisonValue(c.field,c.sourceValue)}</td><td>{{trung_khop:'Trùng khớp',khac_biet:'Cần đối chiếu',khong_co_nguon:'Chưa có nguồn'}[c.status??'']??'Chưa xác định'}</td></tr>)}</tbody></table><small className={styles.muted}>Tên danh mục hỗ trợ đọc; kết quả vẫn dựa trên giá trị nguồn đã quan sát, không tự xác minh lại.</small></div>}
  </section>
  <section id={'review-pdf-'+application.id} className={styles.contentBlock}><h3>Kiểm tra PDF</h3><p className={styles.muted}>Đọc từng PDF của bản nộp mới nhất. Đồng ý PDF chỉ ghi nhận kiểm giấy; kết luận hồ sơ nằm ở phần bên dưới.</p>
   <p role="status" className={styles.reviewProgress}>{papers.filter(pdfAccepted).length} / {papers.length} giấy tờ đã đồng ý nội dung</p>
   <div className={styles.paperChoices}>{papers.map((p,index)=><button type="button" disabled={busy} aria-pressed={paper?.id===p.id} data-review-state={pdfAccepted(p)?'accepted':'pending'} key={p.id} onClick={()=>{setSelected(p.id);setError('');}}>
    <span>{index+1}. {title(p)}</span><small>{pdfAccepted(p)?'✓ Đã đồng ý PDF':lastCheck(p)?'! Cần kiểm tra lại':'○ Chưa đồng ý PDF'}</small></button>)}</div>
   {paper&&<><PdfViewer client={client} fileId={paper.fileId} title={title(paper)}/>
    {editable&&<div className={styles.reviewActions}><div className={styles.actions}>
     <button type="button" className="primary" disabled={busy||pdfAccepted(paper)} onClick={()=>void check()}>{pdfAccepted(paper)?'Đã đồng ý PDF':'Đồng ý PDF'}</button>
     <button type="button" disabled={busy} onClick={onSupplement}>Yêu cầu bổ sung</button></div></div>}
    {!locked&&pending&&!application.intakeSealed&&<details className={styles.disclosure} open={blockers.otherCheckIds.includes(paper.id)}><summary>Kiểm tra điều kiện giấy tờ khác</summary>
     <p className={styles.muted}>Chỉ xác nhận mục đã kiểm tra thực tế. Nếu giấy tờ chưa phù hợp, gửi yêu cầu bổ sung; đồng ý nội dung không tự xác nhận các mục này.</p>
     <div className={styles.checkOptions}>{[['form','Hình thức giấy tờ'],['validity','Thời hạn giấy tờ'],['subject','Đúng chủ thể']].map(([kind,label])=>{
      const checked=['pass','not_applicable','received'].includes(currentCheck(paper,kind)?.result??'');
      return <div key={kind}><span>{label}</span><button type="button" disabled={busy||loadingReview||checked} onClick={()=>void check(kind)}>{checked?'✓ Đã xác nhận':'Xác nhận'}</button></div>;
     })}</div>
    </details>}
   </>}
   {review&&!papers.length&&<p>Không có PDF trong bản nộp mới nhất.</p>}
  </section>
  <section className={styles.contentBlock}><h3>Kết quả tiếp nhận online</h3>
   {locked?<p>Hồ sơ đã có đánh giá xác nhận. Kết luận và nguồn đã chốt được giữ nguyên.</p>:editable?<div className={styles.reviewActions}>
    <p className={styles.muted}>Đồng ý khi đã kiểm tra đủ PDF. Từ chối kết thúc hồ sơ và yêu cầu nhập lý do.</p>
    {review&&blocked&&<div role="status"><p>{!papers.length?'Chưa có PDF để kiểm tra.':blockers.pendingPdfIds.length?'Còn '+blockers.pendingPdfIds.length+' PDF chưa đồng ý.':blockers.otherCheckIds.length?'Còn điều kiện giấy tờ chưa được xác minh.':'Còn yêu cầu bổ sung chưa xử lý xong.'}</p>
     <div className={styles.actions}>{(blockers.pendingPdfIds.length>0||blockers.otherCheckIds.length>0)&&<button type="button" className="secondary" onClick={()=>{setSelected(blockers.pendingPdfIds[0]??blockers.otherCheckIds[0]);document.getElementById('review-pdf-'+application.id)?.scrollIntoView({block:'start'});}}>Xem giấy cần kiểm tra</button>}{blockers.supplementIds.length>0&&<button type="button" className="secondary" onClick={onSupplement}>Xử lý yêu cầu bổ sung</button>}</div></div>}
    <div className={styles.actions}><button type="button" className="primary" disabled={busy||!review||blocked} onClick={()=>{setReason('');setDecision('approved');}}>Duyệt hồ sơ online</button><button type="button" className="danger" disabled={busy||!review} onClick={()=>{setReason('');setDecision('rejected');}}>Từ chối hồ sơ</button></div>
   </div>:<p>Hồ sơ đang ở trạng thái kết thúc hoặc chưa đủ điều kiện thao tác; không sửa kết luận tại đây.</p>}
   <p className={styles.muted}>Duyệt hồ sơ online chưa phải công bố quyền hưởng MGHP.</p>
  </section>
  <dialog className={styles.confirmDialog} ref={dialog} aria-labelledby="disposition-title" onCancel={e=>{e.preventDefault();if(!busy)setDecision(null);}}>
   <h3 id="disposition-title">{decision==='approved'?'Duyệt hồ sơ online này?':'Từ chối hồ sơ này?'}</h3>
   {decision==='rejected'&&<label className={styles.reviewActions}>Lý do từ chối hồ sơ<textarea required maxLength={4000} value={reason} onChange={e=>{setReason(e.currentTarget.value);setError('');}}/></label>}
   <p>Kết luận sẽ được lưu vào lịch sử hồ sơ.</p>
   {error&&<p role="alert">{error}</p>}
   <div className={styles.actions}><button type="button" autoFocus disabled={busy} onClick={()=>setDecision(null)}>Quay lại</button><button type="button" className={decision==='rejected'?'danger':'primary'} disabled={busy||(decision==='rejected'&&!reason.trim())} onClick={()=>void conclude()}>Xác nhận kết luận</button></div>
  </dialog>
 </section>;
}
