import {useCallback,useEffect,useMemo,useRef,useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {Alert} from '../../../shared/ui/Alert';
import {dateTime} from '../presentation.mjs';
import {createSigningApi} from './api.mjs';
import {canSign,roleName,selectionKey,signContent,signingError,signingLabel,signingPhase} from './model.mjs';
import {SigningBundle} from './SigningBundle';
import {SigningDialog} from './SigningDialog';
import {SigningPdf} from './SigningPdf';
import type {DocumentView,FileView,Geometry,Page,SigningRequest,SigningRow} from './types';
import styles from './SigningWorkspace.module.css';

type Bound=DocumentView&{signingRequestId:string|null;signingStatus:string|null};
type RequestContext=SigningRequest&{roundName:string;semesterName:string;documentType:string;decisionVersionId?:string;signingPlannedStartAt?:string|null;signingPlannedEndAt?:string|null;signingScheduleStatus?:string};
const blank:Page<SigningRow>={items:[],page:0,size:20,totalElements:0,hasMore:false};
const isWorking=(q:SigningRequest|null)=>!!q&&['queued','signing'].includes(q.status);
const actionLabel:Record<string,string>={SIGNING_DOCUMENT_BIND:'Lưu phiên bản PDF',SIGNING_PLAN_CREATE:'Chuẩn bị lượt ký',SIGNING_CONFIRM:'Người ký xác nhận ký',SIGNING_RECOVER:'Phục hồi kết quả',SIGNING_COMPLETE:'Đã lưu kết quả ký'};
export function SigningWorkspace({client}:{client:AuthClient}){
 const api=useMemo(()=>createSigningApi(client),[client]);
 const [manager,setManager]=useState(false),[accessReady,setAccessReady]=useState(false),[denied,setDenied]=useState(false),[list,setList]=useState<Page<SigningRow>>(blank),[view,setView]=useState('all');
 const [q,setQ]=useState<RequestContext|null>(null),[doc,setDoc]=useState<Bound|null>(null),[tag,setTag]=useState(''),[bundle,setBundle]=useState(false),[bundleVersion,setBundleVersion]=useState<string|null>(null);
 const [selection,setSelection]=useState<Geometry|null>(null),[preview,setPreview]=useState<FileView|null>(null),[tab,setTab]=useState('pdf'),[confirm,setConfirm]=useState(false),[ack,setAck]=useState(false),[recovery,setRecovery]=useState(false),[reason,setReason]=useState('');
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[needsRead,setNeedsRead]=useState(false),[pollingError,setPollingError]=useState(false),[returnRequest,setReturnRequest]=useState<string|null>(null);
 const flight=useRef(false),live=useRef(true),selected=useRef<string|null>(null),generation=useRef(0);
 const superseded=q?.document.isCurrent===false||doc?.isCurrent===false;
 const key=selectionKey(q,tag),currentStep=q?.steps.find(s=>s.order===q.currentStep),signable=canSign(q)&&!needsRead&&!preview&&!superseded;
 const file=preview??q?.inputFile??doc?.file;
 const clearSelection=useCallback((v:Geometry|null)=>setSelection(v),[]);
 useEffect(()=>{setSelection(null);setConfirm(false);setAck(false);},[key]);
 function url(request?:string,source?:string){const p=new URLSearchParams();if(request)p.set('yeu-cau',request);if(source)p.set('phien-ban',source);window.history.replaceState(null,'',window.location.pathname+(p.size?'?'+p:''));}
 function fail(e:unknown){setError(signingError(e));setNotice('');if((e as {status?:number})?.status===401||(e as {status?:number})?.status===403){setDenied(true);setQ(null);setDoc(null);setList(blank);setConfirm(false);}else if((e as {status?:number})?.status===412||!(e as {status?:number})?.status)setNeedsRead(true);}
 async function run(action:()=>Promise<void>){if(flight.current)return;flight.current=true;setBusy(true);setError('');setNotice('');try{await action();}catch(e){if(live.current)fail(e);}finally{flight.current=false;if(live.current)setBusy(false);}}
 async function readList(page=list.page,filter=view){const r=await api.list(page,filter);if(live.current)setList(r.data);}
 async function openRequest(id:string){
  const gen=++generation.current;const r=await api.request(id);if(!live.current||gen!==generation.current)return;
  selected.current=id;setQ(r.data);setDoc(null);setTag(r.tag);setBundle(false);setPreview(null);setTab('pdf');setNeedsRead(false);setPollingError(false);setNotice('');url(id);
 }
 async function openDocument(bound:{id:string}){
  const d=await api.document(bound.id);if(d.data.signingRequestId){await openRequest(d.data.signingRequestId);return;}
  generation.current++;selected.current=null;setQ(null);setDoc(d.data);setPreview(null);setTag(d.tag);setBundle(false);setTab('pdf');setNeedsRead(false);setNotice('');const p=new URLSearchParams();p.set('van-ban',d.data.id);window.history.replaceState(null,'',window.location.pathname+'?'+p);
 }
 async function reread(){if(q){const r=await api.request(q.id);setQ(r.data);setTag(r.tag);}else if(doc){const r=await api.document(doc.id);if(r.data.signingRequestId){await openRequest(r.data.signingRequestId);}else setDoc(r.data);}await readList();setNeedsRead(false);setPollingError(false);setNotice('Đã đọc lại tài liệu và tiến trình từ hệ thống.');}
 function back(){generation.current++;selected.current=null;setQ(null);setDoc(null);setBundle(false);setPreview(null);setError('');setNotice('');setNeedsRead(false);setReturnRequest(null);url();void run(()=>readList(0));}
 useEffect(()=>{
  live.current=true;const abort=new AbortController();setBusy(true);
  // Read lifecycle is separate from the single-write guard: StrictMode cleanup cancels
  // the first read and the next effect must still start its own independent read.
  void (async()=>{try{const a=await api.access(abort.signal);if(abort.signal.aborted)return;if(a.data.mghpSigning!==true){setDenied(true);setAccessReady(true);return;}setManager(a.data.mghpSpecialist===true);setAccessReady(true);const items=await api.list(0,'all',abort.signal);if(abort.signal.aborted)return;setList(items.data);const p=new URLSearchParams(window.location.search),id=p.get('yeu-cau'),source=p.get('phien-ban'),docId=p.get('van-ban');if(id&&/^[1-9]\d*$/.test(id))await openRequest(id);else if(docId&&/^[1-9]\d*$/.test(docId))await openDocument({id:docId});else if(source&&/^[1-9]\d*$/.test(source)&&a.data.mghpSpecialist){setBundleVersion(source);setBundle(true);}}catch(e){if(!abort.signal.aborted)fail(e);}finally{if(!abort.signal.aborted)setBusy(false);}})();
  return()=>{live.current=false;abort.abort();generation.current++;};
 },[api]);
 useEffect(()=>{
  if(!q||!isWorking(q))return;let cancelled=false;let timer:ReturnType<typeof setTimeout>;const id=q.id;
  async function poll(){if(cancelled)return;if(flight.current){timer=setTimeout(()=>void poll(),2000);return;}try{const r=await api.request(id);if(cancelled||selected.current!==id)return;setQ(r.data);setTag(r.tag);setPollingError(false);if(!isWorking(r.data)){await readList();if(r.data.status==='completed'||r.data.status==='waiting_next')setNotice('Đã lưu PDF sau bước ký tại CTSV. Tiến trình đã được cập nhật.');else setError(signingPhase(r.data));return;}timer=setTimeout(()=>void poll(),2500);}catch(e){if(!cancelled){setPollingError(true);setError(signingError(e));}}}
  timer=setTimeout(()=>void poll(),1500);return()=>{cancelled=true;clearTimeout(timer);};
 },[api,q?.id,q?.status]);
 async function prepare(){if(!doc)return;const r=await api.prepare(doc.id);await openRequest(r.data.id);await readList();setNotice('Đã chuẩn bị đúng hai bước ký. Mỗi người phải tự xác nhận bước của mình.');}
 async function sign(){if(!q||!selection||!signable)throw new Error('Mở PDF hiện hành và chọn vị trí ký.');const snapshotKey=key;
  // Server performs the final ETag/actor/PDF checks; a write is never replayed here.
  const r=await api.sign(q.id,signContent(selection,currentStep?.role==='pho_phong'?ack:false),tag);if(snapshotKey!==key)throw new Error('Tài liệu đã thay đổi. Đọc lại trạng thái.');setQ(r.data);setTag(r.tag);setSelection(null);setConfirm(false);await readList();setNotice('Đã ghi nhận xác nhận ký của bạn. Hệ thống đang nhận và lưu kết quả; chưa chuyển bước trước khi lưu xong.');
 }
 async function reconcile(){if(!q||!reason.trim())throw new Error('Ghi ngắn gọn nội dung đã kiểm tra/khắc phục.');const r=await api.reconcile(q.id,reason.trim(),tag);setQ(r.data);setTag(r.tag);setRecovery(false);setSelection(null);await readList();setNotice('Đã tiếp tục đúng bước phục hồi. Hệ thống không tự ký thay người dùng.');}
 const title=q?.document.title??doc?.title??'Tài liệu ký số MGHP';
 const historical=!!preview;
 if(bundle&&!denied)return <SigningBundle client={client} initialVersion={bundleVersion} onOpen={openDocument} onBack={back}/>;
 return <div className={styles.page} aria-busy={busy}>
  <div className={styles.heading}><div>{q||doc?<button className={styles.textButton} disabled={busy} onClick={back}>← Danh sách tài liệu</button>:<p className={styles.eyebrow}>Miễn giảm học phí / Tài liệu ký số</p>}<h1>{title}</h1><p className={styles.muted}>{q?`${q.roundName} · ${q.semesterName} · PDF bản ${q.document.version}`:doc?`PDF bản ${doc.version} · Đã lưu tại CTSV`:manager?'Chuẩn bị, chuyển ký và theo dõi từng bộ biên bản – quyết định.':'Xem tài liệu được giao, ký bước của bạn và theo dõi kết quả.'}</p></div><div className={styles.row}>{q&&<span className={styles.badge+' '+(q.status==='completed'?styles.done:['failed','unknown'].includes(q.status)?styles.danger:'')}>{signingLabel(q)}</span>}{manager&&!q&&!doc&&<button className={styles.primary} disabled={busy||!accessReady} onClick={()=>{setBundleVersion(null);setBundle(true);}}>Chuẩn bị bộ văn bản</button>}<button disabled={busy||!accessReady||denied} onClick={()=>void run(reread)}>Đọc lại trạng thái</button></div></div>
  {error&&<><Alert>{error}</Alert><p className={styles.error} role="alert">{error}</p></>}
  {notice&&<p className={styles.success} role="status">{notice}</p>}
  {busy&&<p role="status">Đang xử lý…</p>}
  {(!accessReady)&&<p role="status">Đang kiểm tra quyền ký số…</p>}
  {denied?<section className={styles.card}><h2>Không có quyền ký tài liệu này</h2><p>Quyền ký và tài liệu được giao được kiểm tra ở backend. Liên hệ chuyên viên nếu bạn cần được xác minh quyền.</p></section>:accessReady&&<>
   {needsRead&&<p className={styles.warning}>Chưa gửi lại thao tác. Nhấn “Đọc lại trạng thái”, kiểm tra PDF hiện hành rồi chọn lại vùng ký.</p>}
   {!q&&!doc&&<section className={styles.card}><div className={styles.tabs} role="tablist" aria-label="Danh sách tài liệu ký">{[['all','Tất cả'],['waiting','Chờ tôi ký'],['handled','Tôi đã xử lý']].map(([v,label])=><button key={v} role="tab" aria-selected={view===v} disabled={busy} onClick={()=>{setView(v);void run(()=>readList(0,v));}}>{label}</button>)}</div><p className={styles.hint}>{list.totalElements} tài liệu trong danh sách này.</p><div className={styles.tableScroll}><table className={styles.table}><thead><tr><th>Văn bản / Đợt xét</th><th>Phiên bản</th><th>Trạng thái</th><th>Đang chờ</th><th>Thao tác</th></tr></thead><tbody>{list.items.map(r=><tr key={r.id}><td><strong>{r.title}</strong><small>{r.roundName} · {r.semesterName}</small></td><td>Bản {r.version}</td><td><span className={styles.badge+' '+(r.status==='completed'?styles.done:'')}>{signingLabel(r)}</span>{r.mode==='TEST'&&<small>Môi trường ký thử</small>}</td><td>{r.status==='completed'?'Đã hoàn tất':r.waitingForRole?roleName(r.waitingForRole):'—'}</td><td><button disabled={busy} onClick={()=>void run(()=>openRequest(r.id))}>Xem tài liệu</button></td></tr>)}{!list.items.length&&<tr><td colSpan={5} className={styles.empty}>{busy?'Đang tải danh sách…':'Chưa có tài liệu trong danh sách này.'}</td></tr>}</tbody></table></div>{list.totalElements>list.size&&<div className={styles.pager}><button disabled={busy||list.page===0} onClick={()=>void run(()=>readList(list.page-1))}>Trang trước</button><span>Trang {list.page+1}</span><button disabled={busy||!list.hasMore} onClick={()=>void run(()=>readList(list.page+1))}>Trang sau</button></div>}<p className={styles.hint}>Quyết định đã ký xong chưa đồng nghĩa đã công bố. Mỗi người chỉ ký bước của mình.</p></section>}
   {(q||doc)&&<>
    {superseded&&<p className={styles.notice}>Đây là bản PDF trước, chỉ đọc. Không thể gửi ký hoặc thay nội dung bản này.</p>}
    {q?.mode==='TEST'&&<p className={styles.warning}>Ký thử nghiệm: danh tính chứng thư CA được cấu hình để kiểm thử quy trình; không chứng minh chức danh hoặc phân quyền ký thực tế.</p>}
    <div className={styles.tabs} role="tablist" aria-label="Chi tiết tài liệu">{[['pdf','Tài liệu & ký số'],['history','Lịch sử & phiên bản']].map(([v,label])=><button key={v} role="tab" aria-selected={tab===v} onClick={()=>setTab(v)}>{label}</button>)}</div>
    {tab==='pdf'&&file&&<div className={styles.documentGrid}>
     <div>{historical&&<div className={styles.warning}><p>Đang xem bản lịch sử, chỉ đọc.</p><button onClick={()=>setPreview(null)}>Về PDF hiện hành</button></div>}<SigningPdf client={client} file={file} selectable={signable&&!busy&&!historical} selection={selection} onSelection={clearSelection}/></div>
     <aside className={styles.side}>
      <section className={styles.card}><h2>Tiến trình ký</h2>{q?<><ol className={styles.signSteps}>{q.steps.map(s=><li key={s.id} className={s.status==='completed'?styles.stepDone:s.order===q.currentStep?styles.stepCurrent:''}><b>{s.order}</b><div><strong>{roleName(s.role)}</strong><span>{s.signerName??s.signerEmail}</span><small>{s.status==='completed'?'Đã ký · '+dateTime(s.signedAt):s.order===q.currentStep?isWorking(q)?signingPhase(q):'Chưa ký':'Chưa đến lượt'}</small></div></li>)}</ol><p className={styles.notice}>{signingPhase(q)}</p>
       {signable&&!historical&&<><p>{selection?'Đã chọn trang '+selection.page+'. Xem lại khung trước khi ký.':'Chọn trang và kéo vùng chữ ký trên PDF.'}</p><button className={styles.primary} disabled={busy||!selection} onClick={()=>{setAck(false);setConfirm(true);setError('');}}>Ký số</button></>}
       {!superseded&&!canSign(q)&&['prepared','waiting_next'].includes(q.status)&&<p className={styles.notice}>Đang chờ {roleName(currentStep?.role)} tự mở tài liệu và ký bước của mình.</p>}
       {isWorking(q)&&<p role="status" className={styles.warning}>{pollingError?'Chưa cập nhật được tiến độ. Đọc lại trạng thái; không ký lại.':'Đang xử lý bước ký. Bạn có thể rời trang; tiến độ đã được ghi nhận.'}</p>}
       {q.allowedActions.includes('reconcile')&&manager&&<button disabled={busy||needsRead} onClick={()=>{setReason('');setRecovery(true);}}>Kiểm tra &amp; phục hồi kết quả</button>}
       {q.status==='unknown'&&<p className={styles.error}>Chưa rõ kết quả phía CA. Không gửi ký lại. Chuyên viên phối hợp kỹ thuật kiểm tra.</p>}
      </>:doc?.isCurrent===false?<p className={styles.notice}>Đây là bản PDF trước, chỉ đọc. Không thể gửi ký hoặc thay nội dung bản này.</p>:<><p>PDF đã lưu tại CTSV. Chuẩn bị đúng hai người ký từ cấu hình backend.</p>{doc?.canPrepare?<button className={styles.primary} disabled={busy||needsRead||!manager} onClick={()=>void run(prepare)}>{doc?.documentType==='bien_ban'?'Chuẩn bị ký biên bản':'Gửi Phó phòng ký'}</button>:<p className={styles.notice}>Chờ biên bản ký hoàn tất trước khi chuyển quyết định cho Phó phòng.</p>}</>}</section>
      {q&&<section className={styles.card}><h3>Lịch ký dự kiến</h3><p className={styles.hint}>{q.signingPlannedStartAt||q.signingPlannedEndAt?`${dateTime(q.signingPlannedStartAt)} – ${dateTime(q.signingPlannedEndAt)} · giờ Việt Nam`:'Chưa lập lịch'}</p>{q.signingScheduleStatus==='overdue'&&q.status!=='completed'&&<p className={styles.warning}>Đã qua kế hoạch dự kiến. Vẫn có thể ký khi đủ điều kiện.</p>}<p className={styles.hint}>Kế hoạch theo dõi tiến độ; không tự ký hoặc khóa chữ ký theo giờ.</p>{q.completedAt&&<p>Hoàn tất thực tế: {dateTime(q.completedAt)}</p>}</section>}
      <section className={styles.card}><h3>PDF tại CTSV</h3><p className={styles.hint}>{q?.steps.filter(s=>s.outputFile).length?`Đã lưu ${q.steps.filter(s=>s.outputFile).length} bản sau ký. Xem/tải từng bản trong lịch sử.`:'Bản PDF gốc được giữ nguyên; mỗi bước ký tạo thêm một bản riêng.'}</p><button onClick={()=>setTab('history')}>Xem lịch sử &amp; phiên bản</button></section>
      {q?.relatedMinutes&&<section className={styles.card}><h3>Biên bản liên quan</h3><p>Đã ký xong, thuộc đúng bộ quyết định này.</p><button disabled={busy} onClick={()=>void run(async()=>{const original=q.id;await openRequest(q.relatedMinutes!.requestId);setReturnRequest(original);})}>Xem biên bản đã ký</button></section>}
      {returnRequest&&<button disabled={busy} onClick={()=>void run(async()=>{const id=returnRequest;setReturnRequest(null);await openRequest(id);})}>Về quyết định đang ký</button>}
      {manager&&q?.decisionVersionId&&<button disabled={busy} onClick={()=>{setBundleVersion(q.decisionVersionId!);setBundle(true);url(undefined,q.decisionVersionId);}}>Về bộ biên bản – quyết định</button>}
     </aside>
    </div>}
    {tab==='history'&&<section className={styles.card}><h2>Các bản PDF đã lưu</h2><p className={styles.muted}>Giữ nguyên PDF gốc và PDF sau mỗi người ký. Nhấn một bản để xem/tải đúng nội dung đã lưu tại CTSV.</p>{(q?[{title:'PDF gốc',file:q.document.file,at:q.document.createdAt},...q.steps.filter(s=>s.outputFile).map(s=>({title:'Sau '+roleName(s.role)+' ký',file:s.outputFile!,at:s.signedAt}))]:doc?[{title:'PDF gốc',file:doc.file,at:doc.createdAt}]:[]).map(v=><div key={v.file.id} className={styles.history}><div><strong>{v.title}</strong><small>{dateTime(v.at)} · giờ Việt Nam</small><span>{v.file.fileName}</span></div><button onClick={()=>{setPreview(v.file);setTab('pdf');setSelection(null);}}>Xem PDF bản này</button></div>)}<h3>Lịch sử xử lý</h3>{q?.history.map(h=><div className={styles.history} key={h.id}><div><strong>{actionLabel[h.action]??'Cập nhật tiến trình ký'}</strong><small>{dateTime(h.at)} · giờ Việt Nam</small>{h.reason&&<p>{h.reason}</p>}</div></div>)}{!q?.history.length&&<p>Chưa có lượt ký.</p>}</section>}
   </>}
  </>}
  {confirm&&q&&selection&&<SigningDialog title="Xác nhận ký số" busy={busy} onClose={()=>setConfirm(false)}><p><strong>{q.document.title}</strong> · Bản {q.document.version}</p><dl className={styles.summary}><dt>Người ký</dt><dd>{roleName(currentStep?.role)} · {currentStep?.signerName??currentStep?.signerEmail}</dd><dt>Vị trí</dt><dd>Trang {selection.page}, vùng đã chọn trên PDF hiện hành.</dd></dl><p className={styles.notice}>Chỉ ký bước của bạn. Kết quả sẽ được tải về và lưu tại CTSV trước khi chuyển người tiếp theo.</p>{currentStep?.role==='pho_phong'&&<label className={styles.check}><input type="checkbox" checked={ack} onChange={e=>setAck(e.target.checked)} disabled={busy}/>Tôi đã xem đúng phiên bản quyết định này và đồng ý ký.</label>}{error&&<p className={styles.error} role="alert">{error}</p>}<div className={styles.actions}><button disabled={busy} onClick={()=>setConfirm(false)}>Kiểm tra lại</button><button className={styles.primary} disabled={busy||needsRead||(currentStep?.role==='pho_phong'&&!ack)} onClick={()=>void run(sign)}>{busy?'Đang ghi nhận…':'Xác nhận ký'}</button></div></SigningDialog>}
  {recovery&&<SigningDialog title="Kiểm tra và phục hồi" busy={busy} onClose={()=>setRecovery(false)}><p>Chỉ tiếp tục bước có kết quả đã biết hoặc mở lại xác nhận khi chưa từng gửi ký. Không ký lại lượt chưa rõ kết quả.</p><label>Nội dung đã kiểm tra/khắc phục<textarea value={reason} onChange={e=>setReason(e.target.value)} maxLength={4000} disabled={busy}/></label>{error&&<p className={styles.error} role="alert">{error}</p>}<div className={styles.actions}><button disabled={busy} onClick={()=>setRecovery(false)}>Hủy</button><button className={styles.primary} disabled={busy||!reason.trim()} onClick={()=>void run(reconcile)}>Tiếp tục phục hồi</button></div></SigningDialog>}
 </div>;
}
