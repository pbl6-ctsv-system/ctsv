import {useEffect,useRef,useState,type FormEvent} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {errorMessage} from '../../../shared/api/error-message';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {Alert} from '../../../shared/ui/Alert';
import {DeclarationForm,submissionErrorMessage,type Attachment,type FormContext,type SubmissionForm} from './DeclarationForm';
import type {Dossier,SupplementSummary,Declaration} from './dossier-types';
import {dateTime} from '../presentation.mjs';
import {finishGeneralSupplement} from './supplement-workflow.mjs';
import {vietnamInput,vietnamInstant} from '../configuration/model.mjs';
import styles from './IntakeWorkspace.module.css';

const base='/api/ctsv/mghp',requests=base+'/yeu-cau-bo-sung/';
const states:Record<string,string>={open:'Cần sinh viên bổ sung',responded:'Sinh viên đã bổ sung',expired:'Đã hết hạn phản hồi',completed:'Đã xử lý xong',cancelled:'Đã hủy'};
type Item={id:string;type:string;reason:string;requirementId:string|null;responseDocumentId:string|null;reviewResult:string|null};
type Supplement=SupplementSummary&{applicationId:string;receivingRoundId:string|null;items:Item[]};
type Context={supplement:Supplement;application:Dossier;baseDeclaration:Declaration;baseDocuments:Parameters<typeof DeclarationForm>[0]['existing'];latestResponse:{applicationId:string;versionId:string}|null};
type Tagged<T>={data:T;tag:string};
async function tagged<T>(client:AuthClient,url:string):Promise<Tagged<T>>{
 const response=await client.request(url);const tag=response.headers.get('ETag');
 if(!tag)throw new Error('Không đọc được phiên bản dữ liệu.');
 return {data:await response.json() as T,tag};
}
function useActions(){
 const active=useRef(false);const [busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
 async function run(task:()=>Promise<void>,format=errorMessage){
  if(active.current)return;active.current=true;setBusy(true);setError('');setMessage('');
  try{await task();}catch(e){setError(e instanceof ApiError&&e.code==='INTAKE_STAGE_CLOSED'?'Đợt đã chốt hồ sơ online; không thể thay đổi yêu cầu bổ sung.':e instanceof ApiError&&e.status===412?'Hồ sơ đã thay đổi. Hãy đọc lại hồ sơ trước khi gửi.':format(e));}
  finally{active.current=false;setBusy(false);}
 }
 return {busy,error,message,setMessage,run};
}
function Info({value}:{value:Supplement}){
 const state=value.status==='open'&&value.deadline&&Date.now()>Date.parse(value.deadline)?'expired':value.status;
 return <><div className={styles.heading}><h3>Yêu cầu bổ sung lần {value.roundNumber}</h3><span className={styles.badge}>{states[state]??'Chưa xác định'}</span></div>
  <dl className={styles.details}><div><dt>Hạn bổ sung</dt><dd>{dateTime(value.deadline)}</dd></div><div><dt>Đã nhận phản hồi</dt><dd>{value.respondedAt?dateTime(value.respondedAt):'Chưa nhận'}</dd></div></dl>
  <div className={styles.requestNote}><strong>Nội dung đã yêu cầu</strong><p>{value.reason}</p></div>
  {value.items.filter(i=>i.type!=='general').map(item=><p key={item.id}>{item.reason}</p>)}
 </>;
}
function RequestChoices({values,selected,onSelect,busy}:{values:SupplementSummary[];selected:string;onSelect:(id:string)=>void;busy:boolean}){
 return <ul className={styles.list}>{values.map(value=><li className={styles.listItem} key={value.id}><div><strong>Yêu cầu lần {value.roundNumber}</strong><p>{value.reason}</p><p>Hạn: {dateTime(value.deadline)} · {states[value.status]??value.status}</p></div>
  <button type="button" disabled={busy} aria-pressed={selected===value.id} onClick={()=>onSelect(value.id)}>Xem yêu cầu lần {value.roundNumber}</button></li>)}</ul>;
}
export function StudentSupplement({client,application,onOpen,onSubmitted}:{client:AuthClient;application:Dossier|null;onOpen:(id:string)=>Promise<void>;onSubmitted:(id:string)=>Promise<void>}){
 const [selected,setSelected]=useState(''),[context,setContext]=useState<Tagged<Context>|null>(null),[form,setForm]=useState<FormContext|null>(null);
 const linked=useRef(new URLSearchParams(location.search).get('bo-sung')??'');
 const operation=useRef<{json:string;id:string}|null>(null),actions=useActions();
 const values=application?.supplementRequests??[];
 const wanted=application?(values.some(r=>r.id===selected)?selected:values[0]?.id??''):linked.current;
 useEffect(()=>{
  let live=true;if(!/^[1-9]\d*$/.test(wanted)){setContext(null);return;}
  setContext(null);setForm(null);operation.current=null;
  void tagged<Context>(client,requests+wanted+'/phan-hoi').then(async value=>{
   if(!live)return;if(application&&value.data.application.id!==application.id)throw new Error('Yêu cầu không thuộc hồ sơ đang xem.');
   setSelected(wanted);setContext(value);await onOpen(value.data.application.id);
  }).catch(e=>{if(live)void actions.run(async()=>{throw e;});});
  return ()=>{live=false;};
 },[client,wanted,application?.id]);
 async function start(){
  if(!context?.data.supplement.receivingRoundId)return;
  const source=context.data;
  const response=await (await client.request(base+'/toi/ngu-canh-ke-khai?dot_xet_id='+source.supplement.receivingRoundId)).json() as FormContext;
  const allowed=response.policyVersions.filter(p=>source.application.roundId===source.supplement.receivingRoundId?p.id===source.application.policyVersionId:p.policyId===source.application.policyId);
  if(!allowed.length)throw new Error('Chưa có phiên bản chính sách phù hợp trong đợt tiếp nhận.');
  setForm({...response,policyVersions:allowed});
 }
 async function submit(event:FormEvent,body:SubmissionForm){
  event.preventDefault();if(!context)return;
  await actions.run(async()=>{
   const canonical=JSON.stringify(body);if(operation.current?.json!==canonical)operation.current={json:canonical,id:crypto.randomUUID()};
   const url=requests+context.data.supplement.id+'/phan-hoi';
   const result=await client.request(url,{method:'POST',headers:{'Content-Type':'application/json','If-Match':context.tag},body:JSON.stringify({...body,operationId:operation.current.id})});
   const ack=await result.json() as {applicationId:string};setForm(null);
   await Promise.all([onSubmitted(ack.applicationId),tagged<Context>(client,url).then(setContext)]);actions.setMessage('Đã gửi bổ sung. Hồ sơ đang chờ chuyên viên kiểm tra.');
  },submissionErrorMessage);
 }
 return <section className={styles.taskGroup} aria-label="Bổ sung hồ sơ" aria-busy={actions.busy}><h2>Yêu cầu cần trả lời</h2>
  {actions.error&&<Alert>{actions.error}</Alert>}{actions.message&&<p className={styles.success} role="status">{actions.message}</p>}
  {values.length>1&&<RequestChoices values={values} selected={wanted} busy={actions.busy} onSelect={setSelected}/>}
  {!wanted&&<p className={styles.empty}>Hồ sơ chưa có yêu cầu bổ sung. Khi chuyên viên gửi yêu cầu, nội dung và hạn sẽ xuất hiện tại đây.</p>}
  {wanted&&!context&&<p role="status">Đang đọc yêu cầu bổ sung…</p>}
  {context&&<section className={styles.panel}><Info value={context.data.supplement}/>
   {context.data.latestResponse&&<p className={styles.success}>Phản hồi của bạn đã được ghi nhận.</p>}
   {!form&&context.data.supplement.status==='open'&&context.data.supplement.deadline&&Date.now()<=Date.parse(context.data.supplement.deadline)&&<button className="primary" disabled={actions.busy} onClick={()=>void actions.run(start)}>Bổ sung hồ sơ</button>}
   {form&&<DeclarationForm client={client} context={form} initial={context.data.baseDeclaration}
    existing={context.data.baseDocuments.filter(d=>form.policyVersions.some(p=>p.documentGroups.some(g=>g.documents.some(r=>r.id===d.requirementId))))}
    target={{studyProcessId:context.data.application.studyProcessId,policyVersionId:form.policyVersions[0].id}}
    busy={actions.busy} submitLabel="Gửi phản hồi bổ sung" allowPolicySelection={form.policyVersions.length>1}
    onUpload={async file=>{const data=new FormData();data.append('file',file);return (await client.request('/api/ctsv/tep/minh-chung',{method:'POST',body:data})).json() as Promise<Attachment>;}}
    onSubmit={submit} onCancel={()=>setForm(null)}/>}
  </section>}
 </section>;
}
function suggestedDeadline(){return vietnamInput(new Date(Date.now()+3*86400000).toISOString()).slice(0,16);}
export function StaffSupplement({client,application,rootTag,refresh,onOverview}:{client:AuthClient;application:Dossier;rootTag:string|null;refresh:()=>Promise<void>;download:(id:string)=>Promise<void>;onOverview:()=>void}){
 const actions=useActions(),[reason,setReason]=useState(''),[deadline,setDeadline]=useState(suggestedDeadline);
 const [selected,setSelected]=useState(''),[data,setData]=useState<Tagged<Supplement>|null>(null);
 const [newDeadline,setNewDeadline]=useState(''),[changeReason,setChangeReason]=useState('');
 const [nextFrom,setNextFrom]=useState('');
 useEffect(()=>{if(nextFrom)document.getElementById('next-supplement-'+application.id)?.scrollIntoView({block:'center'});},[nextFrom,application.id]);
 const values=application.supplementRequests??[],wanted=values.some(r=>r.id===selected)?selected:values[0]?.id??'';
 const hasActive=values.some(r=>['open','responded','expired'].includes(r.status));
 async function open(id:string){const value=await tagged<Supplement>(client,requests+id);if(value.data.applicationId!==application.id)throw new Error('Yêu cầu thuộc hồ sơ khác.');setData(value);setSelected(id);}
 useEffect(()=>{if(wanted)void actions.run(()=>open(wanted));else setData(null);},[client,wanted,rootTag]);
 async function send(event:FormEvent){event.preventDefault();if(!rootTag)return;await actions.run(async()=>{
  try{
   if(nextFrom)await finishGeneralSupplement(client,nextFrom,'unsatisfied',reason,data?.tag);
   const tag=nextFrom?(await client.request(base+'/ho-so/'+application.id)).headers.get('ETag'):rootTag;
   if(!tag)throw new Error('Không đọc được phiên bản hồ sơ.');
   const response=await client.request(base+'/ho-so/'+application.id+'/yeu-cau-bo-sung',{method:'POST',headers:{'Content-Type':'application/json','If-Match':tag},body:JSON.stringify({reason,deadline:vietnamInstant(deadline)})});
   const value=await response.json() as Supplement;await refresh();await open(value.id);setNextFrom('');setReason('');setDeadline(suggestedDeadline());actions.setMessage('Đã gửi yêu cầu bổ sung đến sinh viên.');
  }catch(error){
   await Promise.allSettled([refresh(),...(wanted?[open(wanted)]:[])]);
   throw error;
  }
 });}
 async function completeGeneral(){if(!data)return;await actions.run(async()=>{
  await finishGeneralSupplement(client,data.data.id,'satisfied','Chuyên viên đã kiểm tra và xác nhận phản hồi đáp ứng yêu cầu.',data.tag);
  await refresh();await open(data.data.id);setNextFrom('');actions.setMessage('Đã xử lý xong yêu cầu bổ sung. Hồ sơ chưa tự được duyệt.');
 });}
 async function mutate(path:string,method:string,body:unknown){if(!data)return;const id=data.data.id;await actions.run(async()=>{
  await client.request(path,{method,headers:{'Content-Type':'application/json','If-Match':data.tag},body:JSON.stringify(body)});
  await Promise.all([open(id),refresh()]);actions.setMessage('Đã cập nhật yêu cầu bổ sung.');
 });}
 return <section className={styles.taskGroup} aria-label="Yêu cầu bổ sung chuyên viên" aria-busy={actions.busy}>
  <h3>Yêu cầu sinh viên bổ sung</h3>{actions.error&&<Alert>{actions.error}</Alert>}{actions.message&&<p className={styles.success} role="status">{actions.message}</p>}
  {(!hasActive||(nextFrom&&data?.data.status==='responded'))&&['submitted','processing','supplementing','supplement_responded'].includes(application.status??'')&&<form id={'next-supplement-'+application.id} className={styles.form} onSubmit={send}>
   {nextFrom&&<p className={styles.requestNote}>Gửi yêu cầu tiếp theo sẽ kết thúc lần trước với kết quả chưa đáp ứng, giữ nguyên phản hồi và lịch sử rồi mở lần bổ sung mới.</p>}
   <label>Hạn bổ sung · giờ Việt Nam<input required type="datetime-local" value={deadline} onChange={e=>setDeadline(e.currentTarget.value)}/></label>
   <label>Nội dung yêu cầu bổ sung<textarea required maxLength={4000} rows={4} value={reason} onChange={e=>setReason(e.currentTarget.value)} placeholder="Nêu rõ thông tin hoặc giấy tờ sinh viên cần bổ sung, sửa lại"/></label>
   <button className="primary" disabled={actions.busy}>{nextFrom?'Gửi yêu cầu bổ sung tiếp':'Gửi yêu cầu bổ sung'}</button>
  </form>}
  {hasActive&&<p className={styles.muted}>Hồ sơ đang có một yêu cầu cần xử lý. Xem nội dung và phản hồi dưới đây trước khi tạo yêu cầu tiếp theo.</p>}
  {values.length>0?<RequestChoices values={values} selected={wanted} busy={actions.busy} onSelect={setSelected}/>:<p>Chưa có yêu cầu bổ sung nào được gửi.</p>}
  {data&&<section className={styles.panel}><Info value={data.data}/>
   {data.data.status==='responded'&&<p><button type="button" onClick={onOverview}>Xem bản bổ sung và kiểm tra PDF</button></p>}
   {data.data.status==='responded'&&data.data.items.length>0&&data.data.items.every(i=>i.type==='general')&&<div className={styles.actions}>
    <button type="button" disabled={actions.busy} onClick={()=>void completeGeneral()}>Đã đáp ứng yêu cầu</button>
    <button type="button" disabled={actions.busy||!!nextFrom} onClick={()=>{setNextFrom(data.data.id);setReason('');setDeadline(suggestedDeadline());}}>Yêu cầu bổ sung tiếp</button>
   </div>}
   {['open','responded','expired'].includes(data.data.status)&&<details className={styles.disclosure}><summary>Điều chỉnh yêu cầu đang xử lý</summary>
    <form className={styles.form} onSubmit={e=>{e.preventDefault();void mutate(requests+data.data.id+'/han-nop','PUT',{deadline:vietnamInstant(newDeadline),reason:changeReason});}}>
     <label>Hạn bổ sung mới<input required type="datetime-local" value={newDeadline} onChange={e=>setNewDeadline(e.currentTarget.value)}/></label><label>Lý do điều chỉnh<textarea required maxLength={4000} value={changeReason} onChange={e=>setChangeReason(e.currentTarget.value)}/></label>
     <div className={styles.actions}><button disabled={actions.busy}>Gia hạn bổ sung</button><button type="button" className="danger" disabled={actions.busy||!changeReason.trim()} onClick={()=>void mutate(requests+data.data.id+'/huy','POST',{reason:changeReason})}>Hủy yêu cầu bổ sung</button></div>
    </form>
   </details>}
   {data.data.status==='responded'&&data.data.items.some(i=>i.type!=='general')&&<LegacyReview client={client} value={data} application={application} onUpdated={async()=>{await Promise.all([open(data.data.id),refresh()]);}}/>}
  </section>}
 </section>;
}
function LegacyReview({client,value,application,onUpdated}:{client:AuthClient;value:Tagged<Supplement>;application:Dossier;onUpdated:()=>Promise<void>}){
 const actions=useActions(),[note,setNote]=useState('');
 const [files,setFiles]=useState<Record<string,string>>({});
 return <details className={styles.disclosure}><summary>Kiểm tra từng nội dung đã yêu cầu</summary><p className={styles.muted}>Những yêu cầu đã chỉ rõ từng giấy hoặc thông tin cần được ghi nhận kết quả từng mục.</p>
  {actions.error&&<Alert>{actions.error}</Alert>}
  <label>Nhận xét xử lý<textarea value={note} maxLength={4000} onChange={e=>setNote(e.currentTarget.value)}/></label>
  {value.data.items.map(item=><div className={styles.listItem} key={item.id}><div><p>{item.reason} · {item.reviewResult??'Chưa kiểm tra'}</p>
   {item.requirementId&&<label>PDF phản hồi cho {item.reason}<select value={files[item.id]??item.responseDocumentId??''} onChange={e=>{const id=e.currentTarget.value;setFiles(current=>({...current,[item.id]:id}));}}><option value="">Không chọn giấy</option>{application.submittedVersions[0]?.documents.filter(d=>d.requirementId===item.requirementId).map((d,i)=><option key={d.id} value={d.id}>{d.documentNumber||'Minh chứng phản hồi '+(i+1)}</option>)}</select></label>}
   </div><div className={styles.actions}>{[['satisfied','Đồng ý nội dung'],['unsatisfied','Chưa đáp ứng']].map(([result,label])=><button type="button" key={result} disabled={actions.busy||!note.trim()} onClick={()=>void actions.run(async()=>{
   await client.request(base+'/muc-bo-sung/'+item.id+'/danh-gia',{method:'PUT',headers:{'Content-Type':'application/json','If-Match':value.tag},body:JSON.stringify({responseDocumentId:(files[item.id]??item.responseDocumentId)||null,result,reason:note})});await onUpdated();
  })}>{label}</button>)}</div></div>)}
  <button type="button" disabled={actions.busy||!note.trim()} onClick={()=>void actions.run(async()=>{await client.request(requests+value.data.id+'/hoan-tat',{method:'POST',headers:{'Content-Type':'application/json','If-Match':value.tag},body:JSON.stringify({reason:note})});await onUpdated();})}>Hoàn tất kiểm tra yêu cầu</button>
 </details>;
}
