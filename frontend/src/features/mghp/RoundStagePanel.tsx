import {useEffect,useRef,useState} from 'react';
import type {AuthClient} from '../../shared/types/api';
import {ApiError} from '../../shared/auth/auth-client.mjs';
import {errorMessage} from '../../shared/api/error-message';
import {Alert} from '../../shared/ui/Alert';
import {dateTime} from './presentation.mjs';
import {decisionWorkspaceUrl} from './decisions/model.mjs';
import styles from './RoundStagePanel.module.css';

type Round={id:string;semesterId:string;name?:string;code?:string;status:string};
type Context={roundId:string;semesterId:string;status:string;intakeSealed:boolean;onlineClosedAt:string|null;reviewClosedAt:string|null;
 eligibleCount:number;excludedCount:number;blockerCount:number;canSeal:boolean;hasMore:boolean;blockers:{applicationId:string|null;code:string}[]};
const names:Record<string,string>={INTAKE_UNRESOLVED:'Hồ sơ chưa xử lý xong',SUPPLEMENT_UNRESOLVED:'Vòng bổ sung chưa xử lý xong',ROUND_NOT_ACTIVE:'Đợt chưa đến thời gian xử lý',
 ONLINE_NOT_SEALED:'Chưa chốt hồ sơ online',INTAKE_NOT_CLOSED:'Chưa đóng nhận hồ sơ',MISSING_REQUESTED_SEMESTER:'Thiếu học kỳ xét',MISSING_ASSESSMENT:'Chưa có kết quả đối chiếu',
 STALE_OR_UNCONFIRMED_ASSESSMENT:'Kết quả chưa xác nhận hoặc nguồn đã thay đổi',ASSESSMENT_SOURCE_NOT_READY:'Căn cứ chưa đủ điều kiện',UNRESOLVED_ASSESSMENT:'Chưa kết luận đối chiếu',MANDATORY_WORK_UNRESOLVED:'Còn công việc bắt buộc chưa hoàn tất',MISSING_SUBMISSION:'Thiếu bản nộp'};
function explain(e:unknown){
 if(e instanceof ApiError){
  if(e.status===412)return 'Danh sách đã thay đổi. Nhấn “Kiểm tra lại” để xem tình trạng mới trước khi chốt.';
  if(e.code==='ROUND_STATE_CONFLICT')return 'Chưa thể chốt. Kiểm tra lại các hồ sơ còn chờ xử lý và trạng thái đợt.';
 }
 return errorMessage(e);
}
/** MGHP-only handoff; both inboxes consume the same server preflight and immutable markers. */
export function RoundStagePanel({client,semesterId,round,stage,revision,onChanged,onOpen,onBusyChange}:{client:AuthClient;semesterId:string;round?:Round;stage:'online'|'hard-copy';revision:unknown;onChanged:()=>Promise<void>;onOpen:(id:string)=>Promise<void>;onBusyChange?:(busy:boolean)=>void}){
 const [context,setContext]=useState<Context|null>(null),[tag,setTag]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState(''),[confirm,setConfirm]=useState(false);
 const dialog=useRef<HTMLDialogElement>(null),running=useRef(false);
 const valid=!!semesterId&&!!round&&round.semesterId===semesterId;
 const suffix=stage==='online'?'/chot-ho-so-online':'/dong-xet';
 const path='/api/ctsv/mghp/dot-xet/'+round?.id+suffix;
 const title=stage==='online'?'Chốt hồ sơ online':'Chốt bản cứng chuyển Hội đồng';
 async function read(){const response=await client.request(path+'?semesterId='+semesterId);return {context:await response.json() as Context,tag:response.headers.get('ETag')??''};}
 useEffect(()=>{
  let live=true;setContext(null);setTag('');setError('');setConfirm(false);setMessage('');
  if(!valid)return;setBusy(true);
  void read().then(value=>{if(live){setContext(value.context);setTag(value.tag);}}).catch(e=>{if(live)setError(explain(e));}).finally(()=>{if(live)setBusy(false);});
  return()=>{live=false;};
 },[client,semesterId,round?.id,stage,revision]);
 useEffect(()=>{if(confirm)dialog.current?.showModal();else dialog.current?.close();},[confirm]);
 async function reload(){if(running.current)return;setBusy(true);setError('');try{const value=await read();setContext(value.context);setTag(value.tag);}catch(e){setError(explain(e));}finally{setBusy(false);}}
 async function seal(){
  if(running.current||!valid||!context?.canSeal||!tag)return;running.current=true;setBusy(true);onBusyChange?.(true);setError('');
  try{
   const response=await client.request(path,{method:'POST',headers:{'Content-Type':'application/json','If-Match':tag},body:JSON.stringify({semesterId,reason:stage==='online'?'Chốt hồ sơ online và chuyển sang đối chiếu bản cứng':'Chốt kết quả đối chiếu bản cứng để chuyển Hội đồng'})});
   setContext(await response.json() as Context);setTag(response.headers.get('ETag')??'');setConfirm(false);
   await onChanged();setMessage(stage==='online'?'Đã chốt hồ sơ online. Các hồ sơ duyệt V1 đã được chuyển sang đối chiếu bản cứng.':'Đã chốt bản cứng. Danh sách đủ điều kiện đã sẵn sàng cho Hội đồng.');
  }catch(e){setConfirm(false);setError(explain(e));}finally{running.current=false;setBusy(false);onBusyChange?.(false);}
 }
 async function open(id:string){setBusy(true);setError('');try{await onOpen(id);}catch(e){setError(explain(e));}finally{setBusy(false);}}
 if(!valid)return <p className={styles.hint}>Chọn một học kỳ và một đợt xét để xem điều kiện chốt.</p>;
 const sealed=stage==='online'?context?.intakeSealed:!!context?.reviewClosedAt;
 const active=!!context&&['open','closed'].includes(context.status)&&!context.blockers.some(b=>b.code==='ROUND_NOT_ACTIVE');
 return <section className={styles.panel} aria-label={title} aria-busy={busy}>
  <div className={styles.heading}><div><h3>{title}</h3><p>{round.name||round.code}</p></div>{sealed&&<strong className={styles.badge}>Đã chốt · chỉ xem</strong>}</div>
  {error&&<Alert>{error}</Alert>}{error&&<p role="alert">{error}</p>}{message&&<p role="status">{message}</p>}
  {busy&&!context&&<p role="status">Đang kiểm tra toàn bộ hồ sơ của đợt…</p>}
  {context&&<>
   <p className={styles.hint}>{stage==='online'?'Chốt toàn bộ đợt, không chỉ các hồ sơ đang hiện trên trang. Nếu đợt còn mở, hệ thống đồng thời đóng nhận hồ sơ mới.':'Chỉ đưa hồ sơ đủ căn cứ sang Hội đồng. Chốt xong không thể chỉnh sửa kết quả đối chiếu.'}</p>
   {sealed?<><p>{stage==='online'?(context.onlineClosedAt?'Đã chốt online lúc '+dateTime(context.onlineClosedAt):'Đợt đã đóng xét từ trước; lịch sử được giữ nguyên.'):'Đã chốt lúc '+dateTime(context.reviewClosedAt)}</p>
    {stage==='online'?<a href={'/mghp/danh-gia?hoc-ky='+semesterId+'&dot-xet='+round.id}>Đến đối chiếu bản cứng</a>:<div className={styles.actions}><a href={decisionWorkspaceUrl(semesterId,round.id)}>Đến Hội đồng &amp; quyết định</a><button type="button" disabled title="Chức năng xuất Excel chưa được triển khai">Xuất Excel</button><span className={styles.hint}>Xuất Excel chưa triển khai</span></div>}
   </>:<>
    <p><strong>{context.eligibleCount}</strong> hồ sơ đủ điều kiện chuyển · <strong>{context.excludedCount}</strong> đã loại khỏi danh sách · <strong>{context.blockerCount}</strong> vướng mắc</p>
    {!!context.blockerCount&&<ul className={styles.blockers}>{context.blockers.map((b,index)=><li key={index}>{names[b.code]??'Cần kiểm tra nguồn'}{b.applicationId&&<> — <button type="button" disabled={busy} onClick={()=>void open(b.applicationId!)}>Xem hồ sơ #{b.applicationId}</button></>}</li>)}</ul>}
    {context.hasMore&&<p className={styles.hint}>Đang hiển thị 20 vướng mắc đầu; điều kiện chốt kiểm toàn bộ hồ sơ.</p>}
    {!active&&<p>Đợt chưa bắt đầu hoặc đã hoàn tất; không thể chốt tại đây.</p>}
    <div className={styles.actions}><button type="button" disabled={busy} onClick={()=>void reload()}>Kiểm tra lại</button>{active&&<button type="button" className="primary" disabled={busy||!context.canSeal} onClick={()=>setConfirm(true)}>{title}</button>}</div>
   </>}
  </>}
  <dialog className={styles.dialog} ref={dialog} aria-labelledby={'confirm-stage-'+stage} onCancel={event=>{if(busy)event.preventDefault();else setConfirm(false);}}>
   <h3 id={'confirm-stage-'+stage}>{title}?</h3><p>{round.name||round.code}</p><p>Chuyển {context?.eligibleCount??0} hồ sơ đủ điều kiện. Sau khi chốt, dữ liệu của bước này không thể chỉnh sửa.</p>
   {stage==='online'&&context?.status==='open'&&<p>Đợt hiện còn mở. Thao tác này cũng đóng nhận hồ sơ mới.</p>}
   <div className={styles.actions}><button type="button" className="primary" disabled={busy} onClick={()=>void seal()}>{busy?'Đang chốt…':'Xác nhận chốt'}</button><button type="button" disabled={busy} onClick={()=>setConfirm(false)}>Hủy</button></div>
  </dialog>
 </section>;
}
