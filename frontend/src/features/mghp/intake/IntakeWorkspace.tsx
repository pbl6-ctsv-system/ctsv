import {useEffect,useRef,useState,type FormEvent} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {errorMessage} from '../../../shared/api/error-message';
import {Alert} from '../../../shared/ui/Alert';
import {DeclarationForm,submissionErrorMessage,type FormContext,type SubmissionForm,type Attachment} from './DeclarationForm';
import styles from './IntakeWorkspace.module.css';
import {StaffSupplement,StudentSupplement} from './SupplementWorkspace';
import {applicationTitle,displayName,dateTime,claimLabels,submissionWindow,applicationStates,applicationStatusLabel} from '../presentation.mjs';
import {applicationListUrl,noticeAction} from './dossier-model.mjs';
import {DeclarationSummary} from './DeclarationSummary';
import {PdfViewer} from '../../../shared/ui/pdf';
import {StaffDossierReview} from './StaffDossierReview';
import {SupplementHistory} from './SupplementHistory';
import {RoundStagePanel} from '../RoundStagePanel';
import type {Dossier,Declaration} from './dossier-types';

const base='/api/ctsv/mghp';
const root=base+'/ho-so';
const inbox='/api/ctsv/toi/thong-bao';
type Page<T>={items:T[];page:number;size:number;totalElements:number};
type Summary={id:string;code:string;roundId:string;studyProcessId:string;policyId:string;policyVersionId:string;status:string|null;submittedAt:string|null;studentCode?:string;fullName?:string;classCode?:string|null;policyCode?:string};
type StudentDocument={id:string;requirementId:string;fileId:string;documentNumber:string|null;issuer:string|null};
type Version={id:string;revision:number;submittedAt:string;submissionType:string;documents:StudentDocument[]};
type Detail=Dossier;
type Round={id:string;code:string;name:string;status:string;opensAt:string;submissionDeadline:string;semesterId:string};
type Notice={id:string;title:string|null;content:string|null;readAt:string|null;actionUrl:string|null};
type Ack={applicationId:string;versionId:string};
type History=Declaration;
const labels:Record<string,string>=applicationStates;

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
 const [declaration,setDeclaration]=useState<Declaration|null>(null),[preview,setPreview]=useState<StudentDocument|null>(null);
 const [status,setStatus]=useState(''),[semester,setSemester]=useState(''),[semesters,setSemesters]=useState<{id:string;name:string;code:string}[]>([]);
 const [stageBusy,setStageBusy]=useState(false);
 const [roundFilter,setRoundFilter]=useState(''),[staffRounds,setStaffRounds]=useState<Round[]>([]),[roundsLoading,setRoundsLoading]=useState(false);
 const [tab,setTab]=useState(new URLSearchParams(location.search).has('bo-sung')?'supplement':'overview');
 const availableRounds=rounds.filter(round=>submissionWindow(round).canStart);
 function clearStartQuery(){const url=new URL(location.href);url.searchParams.delete('dot_xet_id');window.history.replaceState(null,'',url.pathname+url.search+url.hash);}

 async function run(task:()=>Promise<void>,formatError=errorMessage) {
  if(active.current)return;
  active.current=true;setBusy(true);setError('');
  try{await task();}catch(e){setError(formatError(e));}
  finally{active.current=false;setBusy(false);}
 }

 async function list(page=offset,onlyUnread=unread,filterStatus=status,filterSemester=semester,filterRound=roundFilter) {
  if(mode==='inbox'){
   const result=await read<Page<Notice>>(client,inbox+'?page='+page+'&unreadOnly='+onlyUnread);
   setMessages(result.items);setCount(result.totalElements);
  }else{
   const result=await read<Page<Summary>>(client,applicationListUrl(page,filterStatus,filterSemester,filterRound));
   setRows(result.items);setCount(result.totalElements);
  }
  setOffset(page);
 }

 useEffect(()=>{
  if(mode!=='staff-applications')return;
  let cancelled=false;
  setStaffRounds([]);setRoundsLoading(true);
  void (async()=>{
   const items:Round[]=[];
   for(let page=0;;page++){
    const query=new URLSearchParams({page:String(page),size:'100'});
    if(semester)query.set('semesterId',semester);
    const result=await read<Page<Round>>(client,base+'/dot-xet?'+query);
    if(cancelled)return;
    items.push(...result.items);
    if(items.length>=result.totalElements||result.items.length===0)break;
   }
   if(!cancelled)setStaffRounds(items);
  })().catch(e=>{if(!cancelled)setError(errorMessage(e));}).finally(()=>{if(!cancelled)setRoundsLoading(false);});
  return()=>{cancelled=true;};
 },[client,mode,semester]);

 useEffect(()=>{
  let cancelled=false;
  const route=mode==='inbox'?inbox:root;
  setBusy(true);
  const listRequest=read<Page<Summary|Notice>>(client,route);
  const roundRequest=mode==='applications'?read<Page<Round>>(client,base+'/dot-xet?size=100'):Promise.resolve(null);
  if(mode==='staff-applications')void read<Page<{id:string;name:string;code:string}>>(client,'/api/ctsv/nen-tang/danh-muc?kind=semesters&size=100')
   .then(result=>{if(!cancelled)setSemesters(result.items);}).catch(e=>{if(!cancelled)setError(errorMessage(e));});
  Promise.all([listRequest,roundRequest]).then(async([result,roundList])=>{
   if(cancelled)return;
   if(mode==='inbox')setMessages(result.items as Notice[]);
   else setRows(result.items as Summary[]);
   if(roundList)setRounds(roundList.items);
   setCount(result.totalElements);
   const requestedCase=new URLSearchParams(location.search).get('ho-so');if(mode!=='inbox'&&requestedCase){if(!/^[1-9]\d*$/.test(requestedCase))throw new Error('Liên kết hồ sơ không hợp lệ.');await select(requestedCase);}
   const requested=new URLSearchParams(location.search).get('dot_xet_id');
   if(student&&requested){
    if(!/^[1-9]\d*$/.test(requested))throw new Error('Mã đợt chưa hợp lệ. Hãy chọn đợt từ danh sách đợt xét.');
    await openForm(requested,null);
   }
  }).catch(e=>{if(!cancelled)setError(errorMessage(e));}).finally(()=>{if(!cancelled)setBusy(false);});
  return ()=>{cancelled=true;};
 },[client,mode]);

 async function select(id:string) {
  const r=await client.request(root+'/'+id);
  const value=await r.json() as Detail;
  const latest=value.submittedVersions[0];
  const declared=latest?await read<Declaration>(client,root+'/'+id+'/phien-ban/'+latest.id):null;
  setDetail(value);setDeclaration(declared);setTag(r.headers.get('ETag'));setHistory(null);setReason('');setPreview(null);
 }

 async function openForm(round:string,application:Detail|null) {
  setMessage('');
  const fresh=await read<FormContext>(client,base+'/toi/ngu-canh-ke-khai?dot_xet_id='+round);
  const window=submissionWindow(fresh.round);
  if(!window.canStart)throw new Error(window.hint);
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
   setContext(null);setCreating(false);clearStartQuery();setTab('overview');setMessage('Đã tiếp nhận hồ sơ.');
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
  <div className={styles.heading}><div><h1>{mode==='inbox'?'Thông báo của tôi':student?(context?'Nộp hồ sơ miễn giảm học phí':'Hồ sơ của tôi'):'Tiếp nhận & bổ sung hồ sơ MGHP'}</h1>
   <p>{mode==='inbox'?'Theo dõi các yêu cầu và cập nhật về hồ sơ của bạn.':student?'Chỉ hiển thị hồ sơ của tài khoản đang đăng nhập. Theo dõi xử lý và trả lời yêu cầu bổ sung tại đây.':'Chọn hồ sơ để tiếp nhận, yêu cầu bổ sung và kiểm tra minh chứng.'}</p></div>
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
    {noticeAction(n.actionUrl)&&<p><a href={noticeAction(n.actionUrl)!.href}>{noticeAction(n.actionUrl)!.label}</a></p>}
    </div>{n.readAt?<span className={styles.badge}>Đã đọc</span>:<button disabled={busy} onClick={()=>void run(async()=>{
     await client.request(inbox+'/'+n.id+'/da-doc',{method:'PUT'});await list();
    })}>Đánh dấu đã đọc</button>}</li>)}</ul>
  </>:<>
   {creating&&!context&&availableRounds.length>0&&<div className={styles.toolbar}><label>Đợt kê khai <select aria-label="Đợt kê khai" disabled={busy} defaultValue="" onChange={e=>{
    const id=e.currentTarget.value;if(id)void run(()=>openForm(id,null));
   }}><option value="">Chọn đợt đang nhận hồ sơ</option>{availableRounds.map(r=><option key={r.id} value={r.id}>{displayName(r.name,r.code)}</option>)}</select></label><a href="/mghp/dot-xet">Xem lịch tiếp nhận các đợt</a></div>}
   {creating&&!availableRounds.length&&!busy&&<p className={styles.empty}>Hiện chưa có đợt trong thời gian nhận hồ sơ. <a href="/mghp/dot-xet">Xem lịch tiếp nhận các đợt</a></p>}
   {context&&<DeclarationForm client={client} key={context.round.id+':'+(target??'new')} context={context}
    initial={prefill} existing={existing} target={target?detail:null} busy={busy} onUpload={attach} onSubmit={submit}
    onCancel={()=>{setContext(null);setCreating(false);clearStartQuery();setError('');}}/>}
   {!context&&!detail&&!creating&&<><div className={styles.heading}><h2>{student?'Hồ sơ đã nộp của tôi':'Danh sách hồ sơ'}</h2>{!student&&<div><button type="button" disabled title="Chức năng xuất Excel chưa được triển khai">Xuất Excel</button><small className={styles.muted}> · Sắp có</small></div>}</div>
    {!student&&<><div className={styles.toolbar}><label>Học kỳ <select aria-label="Học kỳ" value={semester} disabled={busy||stageBusy} onChange={e=>{const value=e.currentTarget.value;setSemester(value);setRoundFilter('');void run(()=>list(0,unread,status,value,''));}}><option value="">Tất cả học kỳ</option>{semesters.map(term=><option key={term.id} value={term.id}>{term.name||term.code}</option>)}</select></label><label>Đợt xét <select aria-label="Đợt xét" value={roundFilter} disabled={busy||stageBusy||roundsLoading} onChange={e=>{const value=e.currentTarget.value;setRoundFilter(value);void run(()=>list(0,unread,status,semester,value));}}><option value="">{roundsLoading?'Đang tải đợt xét…':'Tất cả đợt xét'}</option>{staffRounds.map(round=><option key={round.id} value={round.id}>{displayName(round.name,round.code)} · {round.code}</option>)}</select></label><p>{count} hồ sơ phù hợp</p></div>
     <div className={styles.statusFilters} aria-label="Lọc trạng thái hồ sơ">{[['','Tất cả'],...Object.entries(labels).filter(([key])=>!['approved','completed'].includes(key))].map(([value,title])=><button type="button" key={value} aria-pressed={status===value} disabled={busy} onClick={()=>{setStatus(value);void run(()=>list(0,unread,value,semester));}}>{title}</button>)}</div>
    </>}
    {!rows.length&&!busy&&<p className={styles.empty}>{student?'Chưa có hồ sơ. Chọn “Nộp hồ sơ mới” để bắt đầu.':'Không có hồ sơ phù hợp với học kỳ, đợt xét và trạng thái đã chọn.'}</p>}
    {!student&&<RoundStagePanel client={client} stage="online" onBusyChange={setStageBusy} semesterId={semester} round={staffRounds.find(r=>r.id===roundFilter)} revision={rows} onChanged={()=>list(0)} onOpen={id=>select(id)}/>}
    {!student?<div className={styles.reviewTable}><table aria-label="Danh sách tiếp nhận hồ sơ"><thead><tr><th>MSSV</th><th>Họ và tên</th><th>Lớp</th><th>Diện M</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{rows.map(row=><tr key={row.id}><td>{row.studentCode||'Chưa ghi nhận'}</td><td>{row.fullName||'Chưa ghi nhận'}</td><td>{row.classCode||'Chưa ghi nhận lớp'}</td><td>{row.policyCode||'Chưa ghi nhận'}</td><td><span className={styles.badge}>{applicationStatusLabel(row)}</span></td><td><button data-application-id={row.id} disabled={busy} onClick={()=>void run(async()=>{await select(row.id);setTab('overview');})}>Xem hồ sơ</button></td></tr>)}</tbody></table></div>:
    <ul className={styles.list}>{rows.map(row=><li className={styles.listItem} key={row.id}>
     <div><strong>{student?displayName(rounds.find(r=>r.id===row.roundId)?.name,applicationTitle(row.id,row.code)):applicationTitle(row.id,row.code)}</strong><p>{student?applicationTitle(row.id,row.code)+' · ':''}{row.fullName?row.fullName+' · ':''}{row.studentCode?row.studentCode+' · ':''}{row.submittedAt?'Nộp '+dateTime(row.submittedAt):'Chưa ghi nhận thời điểm nộp'}</p></div>
     <div className={styles.actions}><span className={styles.badge}>{applicationStatusLabel(row)}</span>
      <button data-application-id={row.id} disabled={busy} onClick={()=>void run(async()=>{await select(row.id);setTab('overview');setCreating(false);})}>Xem hồ sơ</button></div>
    </li>)}</ul>}
   </>}
   {!context&&detail&&<section className={styles.panel} aria-label="Chi tiết hồ sơ">
    <div className={styles.heading}><div><h2>{applicationTitle(detail.id,detail.code)}</h2><span className={styles.badge}>{applicationStatusLabel(detail)}</span></div>
     <div className={styles.actions}><button disabled={busy} onClick={()=>{setDetail(null);setTab('overview');setHistory(null);}}>Về danh sách</button>
      {!student&&tab==='overview'&&<button type="button" className="primary" onClick={()=>document.getElementById('review-pdf-'+detail.id)?.scrollIntoView({block:'start'})}>Kiểm tra & kết luận</button>}
      <button disabled={busy} onClick={()=>void run(()=>select(detail.id))}>Đọc lại hồ sơ</button></div>
    </div>
    <div className={styles.tabs} aria-label="Nội dung hồ sơ">{[['overview','Tổng quan'],['supplement',student?'Yêu cầu cần trả lời':'Yêu cầu bổ sung'],['history','Lịch sử & minh chứng']].map(([key,title])=>
     <button type="button" key={key} aria-pressed={tab===key} onClick={()=>setTab(key)}>{title}</button>)}</div>
    {tab==='overview'&&<div className={styles.taskGroup}>
     <DeclarationSummary value={declaration} application={detail} showClaims={student}/>
     {student&&<section className={styles.contentBlock}><h3>Giấy tờ đính kèm</h3><p className={styles.muted}>Minh chứng của bản nộp mới nhất. Các bản trước nằm trong Lịch sử & minh chứng.</p><ul className={styles.list}>
      {detail.submittedVersions[0]?.documents.map((document,index)=><li className={styles.listItem} key={document.id}><div><strong>{detail.documentRequirements?.find(r=>r.id===document.requirementId)?.name||document.documentNumber||'Minh chứng '+(index+1)}</strong>{document.issuer&&<p>Nơi cấp: {document.issuer}</p>}</div><div className={styles.actions}><button type="button" onClick={()=>setPreview(document)}>Xem PDF {index+1}</button><button type="button" disabled={busy} onClick={()=>void run(()=>download(document.fileId))}>Tải PDF {index+1}</button></div></li>)}
     </ul>{preview&&<PdfViewer client={client} fileId={preview.fileId} title={detail.documentRequirements?.find(r=>r.id===preview.requirementId)?.name||'Minh chứng PDF'}/>}</section>}
     <section className={styles.contentBlock}><h3>Trạng thái và thao tác</h3><p><span className={styles.badge}>{applicationStatusLabel(detail)}</span> · {detail.submittedVersions.length} bản nộp</p>
      {detail.dispositionHistory?.[0]?.reason&&<p>Lý do: {detail.dispositionHistory[0].reason}</p>}
      {detail.status==='supplementing'&&<button type="button" onClick={()=>setTab('supplement')}>{student?'Xem yêu cầu và trả lời bổ sung':'Xem yêu cầu đã gửi'}</button>}
      {detail.status==='supplement_responded'&&<p>Đã nhận bổ sung, đang chờ chuyên viên kiểm tra.</p>}
     {student&&detail.status==='reopened'&&<button className="primary" disabled={busy} onClick={()=>void run(()=>openForm(detail.roundId,detail))}>Kê khai nộp lại</button>}
     {student&&['submitted','processing','supplementing','supplement_responded','reopened'].includes(detail.status??'')&&
      <details className={styles.disclosure}><summary>Rút hồ sơ</summary><p className={styles.muted}>Sau khi rút, hồ sơ dừng xử lý. Các bản nộp đã có được lưu trong lịch sử.</p>
       <label>Lý do rút hồ sơ<textarea value={reason} maxLength={4000} onChange={e=>setReason(e.currentTarget.value)}/></label>
       <button className="danger" disabled={busy||!reason.trim()} onClick={()=>void run(withdraw)}>Rút hồ sơ</button>
      </details>}
     </section>
     {!student&&detail.status==='approved'&&(detail.intakeSealed?<p><a href={'/mghp/danh-gia?ho-so='+detail.id}>Đến bước đối chiếu bản cứng</a></p>:<p>Đã duyệt V1. Chờ chốt hồ sơ online của cả đợt để chuyển sang đối chiếu bản cứng.</p>)}
     {!student&&<StaffDossierReview declaredNames={declaration?.claimDisplayNames} client={client} application={detail} rootTag={tag} refresh={async()=>{await Promise.all([list(),select(detail.id)]);}} onSupplement={()=>setTab('supplement')}/>}
    </div>}
    {tab==='supplement'&&!student&&<StaffSupplement key={detail.id} client={client} application={detail} rootTag={tag} refresh={async()=>{await Promise.all([list(),select(detail.id)]);}} download={download} onOverview={()=>setTab('overview')}/>}
    {tab==='history'&&<div className={styles.taskGroup}><h3>Lịch sử bản nộp</h3>
     <ul className={styles.history}>{detail.submittedVersions.map(v=><li key={v.id}>
      <div className={styles.historyHeader}><button disabled={busy} onClick={()=>void run(async()=>setHistory(await read<History>(client,root+'/'+detail.id+'/phien-ban/'+v.id)))}>Bản nộp {v.revision}</button>
       <time dateTime={v.submittedAt}>{dateTime(v.submittedAt)}</time></div>
      <ul className={styles.documents}>{v.documents.map((d,index)=><li key={d.id}><button disabled={busy} onClick={()=>void run(()=>download(d.fileId))}>Tải PDF {d.documentNumber || 'minh chứng '+(index+1)}</button></li>)}</ul>
     </li>)}</ul>
     {history&&<dl className={styles.details}>{Object.entries(history.claims).map(([field,value])=><div key={field}><dt>{claimLabels[field as keyof typeof claimLabels]??field}</dt><dd>{history.claimDisplayNames?.[field]??value??'—'}</dd></div>)}</dl>}
     <SupplementHistory client={client} application={detail}/>
     {!!detail.dispositionHistory?.length&&<section><h3>Lịch sử kết luận</h3>{detail.dispositionHistory.map(event=><p key={event.id}><strong>{labels[event.result]??event.result}</strong> · {dateTime(event.at)}{event.reason?' — '+event.reason:''}</p>)}</section>}
    </div>}
   </section>}
   {!context&&student&&tab==='supplement'&&<StudentSupplement client={client} application={detail} onOpen={async id=>{if(detail?.id!==id)await select(id);}} onSubmitted={async id=>{await Promise.all([list(0),select(id)]);}}/>}
  </>}
  {!detail&&!context&&!creating&&<div className={styles.actions} aria-label="Phân trang">
   <button disabled={busy||offset===0} onClick={()=>void run(()=>list(offset-1))}>Trang trước</button>
   <span>Trang {offset+1}</span>
   <button disabled={busy||(offset+1)*20>=count} onClick={()=>void run(()=>list(offset+1))}>Trang sau</button>
  </div>}
 </section>;
}
