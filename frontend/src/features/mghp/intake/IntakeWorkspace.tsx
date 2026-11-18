import {useEffect,useRef,useState,type FormEvent} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {errorMessage} from '../../../shared/api/error-message';
import {Alert} from '../../../shared/ui/Alert';
import {DeclarationForm,submissionErrorMessage,type FormContext,type SubmissionForm,type Attachment} from './DeclarationForm';
import styles from './IntakeWorkspace.module.css';
import {StaffSupplement,StudentSupplement} from './SupplementWorkspace';
import {applicationTitle,displayName,dateTime,claimLabels} from '../presentation.mjs';

const base='/api/ctsv/mghp';
const root=base+'/ho-so';
const inbox='/api/ctsv/toi/thong-bao';
type Page<T>={items:T[];page:number;size:number;totalElements:number};
type Summary={id:string;code:string;roundId:string;studyProcessId:string;policyId:string;policyVersionId:string;status:string|null;submittedAt:string|null;studentCode?:string;fullName?:string};
type StudentDocument={id:string;requirementId:string;fileId:string;documentNumber:string|null;issuer:string|null};
type Version={id:string;revision:number;submittedAt:string;submissionType:string;documents:StudentDocument[]};
type Detail=Summary&{submittedVersions:Version[]};
type Round={id:string;code:string;name:string;status:string;opensAt:string;submissionDeadline:string;semesterId:string};
type Notice={id:string;title:string|null;content:string|null;readAt:string|null;actionUrl:string|null};
type Ack={applicationId:string;versionId:string};
type History={claims:Record<string,string|null>;relatedPeople:Record<string,string|null>[];requestedSemesters:{semesterId:string}[];statements:Record<string,boolean|null>};
const labels:Record<string,string>={submitted:'Đã nộp',withdrawn:'Đã rút',reopened:'Đã mở lại',processing:'Đang xử lý',supplementing:'Đang bổ sung',completed:'Hoàn tất'};

async function read<T>(client:AuthClient,url:string):Promise<T> {
 return (await client.request(url)).json() as Promise<T>;
}

export function IntakeWorkspace({client,mode}:{client:AuthClient;mode:'applications'|'staff-applications'|'inbox'}) {
 const student=mode==='applications';
 const [rows,setRows]=useState<Summary[]>([]),[rounds,setRounds]=useState<Round[]>([]),[messages,setMessages]=useState<Notice[]>([]);
 const [detail,setDetail]=useState<Detail|null>(null),[tag,setTag]=useState<string|null>(null),[context,setContext]=useState<FormContext|null>(null);
 const [prefill,setPrefill]=useState<History|null>(null),[existing,setExisting]=useState<StudentDocument[]>([]);
 const [target,setTarget]=useState<string|null>(null),[reason,setReason]=useState(''),[message,setMessage]=useState('');
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[history,setHistory]=useState<History|null>(null);
 const [offset,setOffset]=useState(0),[count,setCount]=useState(0),[unread,setUnread]=useState(false);
 const operation=useRef<{json:string;id:string}|null>(null);
 const active=useRef(false);
 const [creating,setCreating]=useState(false);
 const [tab,setTab]=useState(new URLSearchParams(location.search).has('bo-sung')?'supplement':'overview');

 async function run(task:()=>Promise<void>,formatError=errorMessage) {
  if(active.current)return;
  active.current=true;setBusy(true);setError('');
  try{await task();}catch(e){setError(formatError(e));}
  finally{active.current=false;setBusy(false);}
 }

 async function list(page=offset,onlyUnread=unread) {
  if(mode==='inbox'){
   const result=await read<Page<Notice>>(client,inbox+'?page='+page+'&unreadOnly='+onlyUnread);
   setMessages(result.items);setCount(result.totalElements);
  }else{
   const result=await read<Page<Summary>>(client,root+'?page='+page);
   setRows(result.items);setCount(result.totalElements);
  }
  setOffset(page);
 }

 useEffect(()=>{
  let cancelled=false;
  const route=mode==='inbox'?inbox:root;
  setBusy(true);
  const listRequest=read<Page<Summary|Notice>>(client,route);
  const roundRequest=mode==='applications'?read<Page<Round>>(client,base+'/dot-xet?size=100'):Promise.resolve(null);
  Promise.all([listRequest,roundRequest]).then(([result,roundList])=>{
   if(cancelled)return;
   if(mode==='inbox')setMessages(result.items as Notice[]);
   else setRows(result.items as Summary[]);
   if(roundList)setRounds(roundList.items.filter(r=>r.status==='open'));
   setCount(result.totalElements);
  }).catch(e=>{if(!cancelled)setError(errorMessage(e));}).finally(()=>{if(!cancelled)setBusy(false);});
  return ()=>{cancelled=true;};
 },[client,mode]);

 async function select(id:string) {
  const r=await client.request(root+'/'+id);
  setDetail(await r.json() as Detail);setTag(r.headers.get('ETag'));setHistory(null);setReason('');
 }

 async function openForm(round:string,application:Detail|null) {
  setMessage('');
  const fresh=await read<FormContext>(client,base+'/toi/ngu-canh-ke-khai?dot_xet_id='+round);
  if(application&&application.submittedVersions.length){
   const last=application.submittedVersions[0];
   setPrefill(await read<History>(client,root+'/'+application.id+'/phien-ban/'+last.id));
   setExisting(last.documents);setTarget(application.id);
  }else{setPrefill(null);setExisting([]);setTarget(null);}
  operation.current=null;setContext(fresh);
 }

 async function attach(file:File):Promise<Attachment> {
  const data=new FormData();data.append('file',file);
  const response=await client.request('/api/ctsv/tep/minh-chung',{method:'POST',body:data});
  return response.json() as Promise<Attachment>;
 }

 async function submit(evt:FormEvent,form:SubmissionForm) {
  evt.preventDefault();
  await run(async()=>{
   const canonical=JSON.stringify(form);
   if(operation.current?.json!==canonical)operation.current={json:canonical,id:crypto.randomUUID()};
   const url=target?root+'/'+target+'/ban-nop':root;
   const headers:Record<string,string>={'Content-Type':'application/json'};
   if(target&&tag)headers['If-Match']=tag;
   const result=await client.request(url,{method:'POST',headers,body:JSON.stringify({operationId:operation.current.id,...form})});
   const ack=await result.json() as Ack;
   setContext(null);setCreating(false);setTab('overview');setMessage('Đã tiếp nhận hồ sơ.');
   await Promise.all([list(0),select(ack.applicationId)]);
  },submissionErrorMessage);
 }

 async function withdraw() {
  if(!detail||!tag)return;
  const id=detail.id;
  await client.request(root+'/'+id+'/rut',{
   method:'POST',headers:{'Content-Type':'application/json','If-Match':tag},body:JSON.stringify({reason})
  });
  setMessage('Đã rút hồ sơ.');
  await Promise.all([list(),select(id)]);
 }

 async function download(fileId:string) {
  const response=await client.request('/api/ctsv/tep/'+fileId+'/noi-dung');
  const object=URL.createObjectURL(await response.blob());
  const a=document.createElement('a');a.href=object;a.download='minh-chung-'+fileId+'.pdf';a.click();
  setTimeout(()=>URL.revokeObjectURL(object),1000);
 }

 return <section className={styles.workspace} aria-busy={busy}>
  <div className={styles.heading}><div><h1>{mode==='inbox'?'Thông báo của tôi':student?'Hồ sơ miễn giảm học phí':'Tiếp nhận & bổ sung hồ sơ MGHP'}</h1>
   <p>{mode==='inbox'?'Theo dõi các yêu cầu và cập nhật về hồ sơ của bạn.':student?'Nộp hồ sơ, theo dõi xử lý và trả lời yêu cầu bổ sung.':'Chọn hồ sơ để tiếp nhận, yêu cầu bổ sung và kiểm tra minh chứng.'}</p></div>
   {student&&!context&&!detail&&<button className="primary" disabled={busy} onClick={()=>setCreating(!creating)}>Nộp hồ sơ mới</button>}
  </div>
  {error&&<Alert>{error}</Alert>}
  {message&&<p role="status">{message}</p>}
  {mode==='inbox'?<>
   <label><input type="checkbox" checked={unread} disabled={busy} onChange={e=>{
    const value=e.currentTarget.checked;setUnread(value);void run(()=>list(0,value));
   }}/> Chỉ thông báo chưa đọc</label>
   {!messages.length&&!busy&&<p>Không có thông báo.</p>}
   <ul className={styles.list}>{messages.map(n=><li className={styles.listItem} key={n.id}><div><h2>{n.title||'Thông báo'}</h2><p>{n.content}</p>
    {n.actionUrl&&/^\/mghp\/ho-so\?bo-sung=[1-9]\d*$/.test(n.actionUrl)&&
     <p><a href={n.actionUrl}>Xem yêu cầu bổ sung</a></p>}
    </div>{n.readAt?<span className={styles.badge}>Đã đọc</span>:<button disabled={busy} onClick={()=>void run(async()=>{
     await client.request(inbox+'/'+n.id+'/da-doc',{method:'PUT'});await list();
    })}>Đánh dấu đã đọc</button>}</li>)}</ul>
  </>:<>
   {creating&&!context&&rounds.length>0&&<div className={styles.toolbar}><label>Đợt kê khai <select aria-label="Đợt kê khai" disabled={busy} defaultValue="" onChange={e=>{
    const id=e.currentTarget.value;if(id)void run(()=>openForm(id,null));
   }}><option value="">Chọn đợt đang nhận hồ sơ</option>{rounds.map(r=><option key={r.id} value={r.id}>{displayName(r.name,r.code)} · {r.code}</option>)}</select></label></div>}
   {creating&&!rounds.length&&!busy&&<p className={styles.empty}>Hiện chưa có đợt đang nhận hồ sơ.</p>}
   {context&&<DeclarationForm client={client} key={context.round.id+':'+(target??'new')} context={context}
    initial={prefill} existing={existing} target={target?detail:null} busy={busy} onUpload={attach} onSubmit={submit}
    onCancel={()=>{setContext(null);setCreating(false);setError('');}}/>}
   {!context&&!detail&&<><h2>Danh sách hồ sơ</h2>
    {!rows.length&&!busy&&<p className={styles.empty}>Chưa có hồ sơ. Chọn “Nộp hồ sơ mới” để bắt đầu.</p>}
    <ul className={styles.list}>{rows.map(row=><li className={styles.listItem} key={row.id}>
     <div><strong>{applicationTitle(row.id,row.code)}</strong><p>{row.fullName?row.fullName+' · ':''}{row.studentCode??''} · Nộp {dateTime(row.submittedAt)}</p></div>
     <div className={styles.actions}><span className={styles.badge}>{labels[row.status??'']??'Chưa xác định'}</span>
      <button data-application-id={row.id} disabled={busy} onClick={()=>void run(async()=>{await select(row.id);setTab('overview');setCreating(false);})}>Xem hồ sơ</button></div>
    </li>)}</ul>
   </>}
   {!context&&detail&&<section className={styles.panel} aria-label="Chi tiết hồ sơ">
    <div className={styles.heading}><div><h2>{applicationTitle(detail.id,detail.code)}</h2><span className={styles.badge}>{labels[detail.status??'']??'Chưa xác định'}</span></div>
     <div className={styles.actions}><button disabled={busy} onClick={()=>{setDetail(null);setTab('overview');setHistory(null);}}>Về danh sách</button>
      <button disabled={busy} onClick={()=>void run(()=>select(detail.id))}>Đọc lại hồ sơ</button></div>
    </div>
    <div className={styles.tabs} aria-label="Nội dung hồ sơ">{[['overview','Tổng quan'],['supplement',student?'Yêu cầu bổ sung':'Bổ sung & kiểm giấy'],['history','Lịch sử & minh chứng']].map(([key,title])=>
     <button type="button" key={key} aria-pressed={tab===key} onClick={()=>setTab(key)}>{title}</button>)}</div>
    {tab==='overview'&&<div className={styles.taskGroup}>
     <p>{detail.status==='supplementing'?'Hồ sơ cần bổ sung thông tin. Mở yêu cầu trong thông báo để xem nội dung và hạn phản hồi.':'Theo dõi trạng thái xử lý và các bản nộp của hồ sơ tại đây.'}</p>
     <dl className={styles.details}><div><dt>Ngày nộp hồ sơ</dt><dd>{dateTime(detail.submittedAt)}</dd></div><div><dt>Số bản nộp</dt><dd>{detail.submittedVersions.length}</dd></div></dl>
     <details className={styles.disclosure}><summary>Thông tin đối chiếu hồ sơ</summary><p className={styles.technical}>{detail.code}</p></details>
     {student&&detail.status==='reopened'&&<button className="primary" disabled={busy} onClick={()=>void run(()=>openForm(detail.roundId,detail))}>Kê khai nộp lại</button>}
     {student&&['submitted','processing','supplementing','reopened'].includes(detail.status??'')&&
      <details className={styles.disclosure}><summary>Rút hồ sơ</summary><p className={styles.muted}>Sau khi rút, hồ sơ dừng xử lý. Các bản nộp đã có được lưu trong lịch sử.</p>
       <label>Lý do rút hồ sơ<textarea value={reason} maxLength={4000} onChange={e=>setReason(e.currentTarget.value)}/></label>
       <button className="danger" disabled={busy||!reason.trim()} onClick={()=>void run(withdraw)}>Rút hồ sơ</button>
      </details>}
    </div>}
    {tab==='supplement'&&!student&&<StaffSupplement key={detail.id} client={client} application={detail} rootTag={tag} refresh={()=>select(detail.id)} download={download}/>}
    {tab==='history'&&<div className={styles.taskGroup}><h3>Lịch sử bản nộp</h3>
     <ul className={styles.history}>{detail.submittedVersions.map(v=><li key={v.id}>
      <div className={styles.historyHeader}><button disabled={busy} onClick={()=>void run(async()=>setHistory(await read<History>(client,root+'/'+detail.id+'/phien-ban/'+v.id)))}>Bản nộp {v.revision}</button>
       <time dateTime={v.submittedAt}>{dateTime(v.submittedAt)}</time></div>
      <ul className={styles.documents}>{v.documents.map((d,index)=><li key={d.id}><button disabled={busy} onClick={()=>void run(()=>download(d.fileId))}>Tải PDF {d.documentNumber || 'minh chứng '+(index+1)}</button></li>)}</ul>
     </li>)}</ul>
     {history&&<dl className={styles.details}>{Object.entries(history.claims).map(([field,value])=><div key={field}><dt>{claimLabels[field as keyof typeof claimLabels]??field}</dt><dd>{value??'—'}</dd></div>)}</dl>}
    </div>}
   </section>}
   {!context&&student&&tab==='supplement'&&<StudentSupplement client={client} onSubmitted={async id=>{await Promise.all([list(0),select(id)]);}}/>}
  </>}
  {!detail&&!context&&<div className={styles.actions} aria-label="Phân trang">
   <button disabled={busy||offset===0} onClick={()=>void run(()=>list(offset-1))}>Trang trước</button>
   <span>Trang {offset+1}</span>
   <button disabled={busy||(offset+1)*20>=count} onClick={()=>void run(()=>list(offset+1))}>Trang sau</button>
  </div>}
 </section>;
}
