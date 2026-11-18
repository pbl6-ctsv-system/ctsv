import {useEffect,useRef,useState,type FormEvent,type ReactNode} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {errorMessage} from '../../../shared/api/error-message';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {Alert} from '../../../shared/ui/Alert';
import {DeclarationForm,submissionErrorMessage,type Attachment,type FormContext,type SubmissionForm} from './DeclarationForm';
import styles from './IntakeWorkspace.module.css';

const base='/api/ctsv/mghp';
const supplements=base+'/yeu-cau-bo-sung/';
const applications=base+'/ho-so/';
const idPattern=/^[1-9]\d*$/;
const stateLabels:Record<string,string>={open:'Đang chờ phản hồi',responded:'Đã phản hồi',expired:'Hết hạn',completed:'Đã hoàn tất',cancelled:'Đã hủy'};
const itemTypes:Record<string,string>={document:'Giấy tờ',declaration:'Lời khai',semester:'Học kỳ',evaluation:'Đánh giá'};
const itemResults:Record<string,string>={satisfied:'Đáp ứng',unsatisfied:'Chưa đáp ứng',needs_verification:'Cần xác minh',not_applicable:'Không áp dụng'};
const claimLabels:Record<string,string>={fullName:'Họ tên',citizenId:'CCCD',birthDate:'Ngày sinh',birthPlace:'Nơi sinh',gender:'Giới tính',
 ethnicityId:'Dân tộc',classId:'Lớp',studentCode:'Mã sinh viên',cohortId:'Khóa học',managementUnitId:'Đơn vị quản lý',permanentAddress:'Thường trú',phoneNumber:'Điện thoại'};
type Json=Record<string,unknown>;
type ItemInput={type:string;reason:string;documentId:string|null;requirementId:string|null;semesterId:string|null;evaluationId:string|null;declarationField:string|null};
type Item=ItemInput&{id:string;responseDocumentId:string|null;reviewResult:string|null};
type Supplement={id:string;applicationId:string;receivingRoundId:string|null;roundNumber:number;reason:string|null;status:string;deadline:string|null;items:Item[]};
type Check={id:string;kind:string;result:string;note:string|null;checkedAt:string};
type Document={id:string;requirementId:string;fileId:string;documentNumber:string|null;issuer:string|null;
 issuedOn:string|null;expiresOn:string|null;relatedPersonIndex:number|null};
type ReviewedDocument=Omit<Document,'relatedPersonIndex'>&{submissionId:string;checks:Check[]};
type Application={id:string;roundId:string;studyProcessId:string;policyVersionId:string;policyId:string;status:string|null;
 submittedVersions:{id:string;revision:number}[]};
type Context={supplement:Supplement;application:Application;baseDeclaration:Parameters<typeof DeclarationForm>[0]['initial'];
 baseDocuments:Document[];latestResponse:{applicationId:string;versionId:string}|null};
type Review={submittedVersions:Application['submittedVersions'];documents:ReviewedDocument[];
 comparisons:{field:string;declaredValue:string|null;sourceValue:string|null;status:string|null}[]};
type Tagged<T>={data:T;tag:string};

async function get<T>(client:AuthClient,path:string):Promise<T> {
 return (await client.request(path)).json() as Promise<T>;
}

async function tagged<T>(client:AuthClient,path:string):Promise<Tagged<T>> {
 const response=await client.request(path);
 const tag=response.headers.get('ETag');
 if(!tag)throw new Error('Không đọc được phiên bản dữ liệu.');
 return {data:await response.json() as T,tag};
}

async function write<T>(client:AuthClient,path:string,method:'POST'|'PUT',tag:string,payload:Json):Promise<T> {
 const response=await client.request(path,{method,headers:{'Content-Type':'application/json','If-Match':tag},body:JSON.stringify(payload)});
 return response.json() as Promise<T>;
}

function useActions() {
 const running=useRef(false);
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
 async function run(action:()=>Promise<void>,format=errorMessage) {
  if(running.current)return;
  running.current=true;setBusy(true);setError('');setMessage('');
  try{await action();}
  catch(e){setError(e instanceof ApiError&&e.status===412?'Dữ liệu đã thay đổi. Hãy đọc lại trước khi thao tác.':format(e));}
  finally{running.current=false;setBusy(false);}
 }
 return {busy,error,message,setMessage,run};
}

function SupplementInfo({value,children}:{value:Supplement;children?:ReactNode}) {
 const shownState=value.status==='open'&&value.deadline&&Date.now()>Date.parse(value.deadline)?'expired':value.status;
 return <section className={styles.panel} aria-label="Chi tiết vòng bổ sung">
  <div className={styles.heading}><h3>Vòng bổ sung {value.roundNumber} · Mã {value.id}</h3><span className={styles.badge}>{stateLabels[shownState]??shownState}</span></div>
  <p className={styles.muted}>{value.deadline?'Hạn phản hồi · '+new Date(value.deadline).toLocaleString('vi-VN'):''}</p>
  <p>{value.reason}</p>
  <ul className={styles.list}>{value.items.map(item=><li className={styles.listItem} key={item.id}>
   {itemTypes[item.type]??item.type} · {item.reason} · {item.reviewResult?itemResults[item.reviewResult]:'Chưa kiểm'}
  </li>)}</ul>
  {children}
 </section>;
}

export function StudentSupplement({client,onSubmitted}:{client:AuthClient;onSubmitted:(id:string)=>Promise<void>}) {
 const [id,setId]=useState(()=>new URLSearchParams(location.search).get('bo-sung')??'');
 const [context,setContext]=useState<Tagged<Context>|null>(null),[form,setForm]=useState<FormContext|null>(null);
 const actions=useActions();
 const operation=useRef<{json:string;id:string}|null>(null);
 const loaded=useRef(false);
 async function open(key:string){
  setContext(null);setForm(null);operation.current=null;
  setContext(await tagged<Context>(client,supplements+key+'/phan-hoi'));
 }
 useEffect(()=>{
  if(!loaded.current&&idPattern.test(id)){loaded.current=true;void actions.run(()=>open(id));}
 });
 async function start(){
  if(!context?.data.supplement.receivingRoundId)return;
  const source=context.data;
  const response=await get<FormContext>(
   client,base+'/toi/ngu-canh-ke-khai?dot_xet_id='+source.supplement.receivingRoundId);
  const allowed=response.policyVersions.filter(p=>source.application.roundId===source.supplement.receivingRoundId
   ?p.id===source.application.policyVersionId:p.policyId===source.application.policyId);
  if(!allowed.length)throw new Error('Không có phiên bản chính sách phù hợp trong đợt tiếp nhận.');
  setForm({...response,policyVersions:allowed});
 }
 async function submit(event:FormEvent,body:SubmissionForm){
  event.preventDefault();
  if(!context)return;
  await actions.run(async()=>{
   const canonical=JSON.stringify(body);
   if(operation.current?.json!==canonical)operation.current={json:canonical,id:crypto.randomUUID()};
   const url=supplements+context.data.supplement.id+'/phan-hoi';
   const result=await write<{applicationId:string}>(client,url,'POST',context.tag,{...body,operationId:operation.current.id});
   setForm(null);actions.setMessage('Đã gửi phản hồi bổ sung.');
   await Promise.all([onSubmitted(result.applicationId),tagged<Context>(client,url).then(setContext)]);
  },submissionErrorMessage);
 }
 return <section className={styles.taskGroup} aria-label="Bổ sung hồ sơ" aria-busy={actions.busy}>
  <h2>Bổ sung hồ sơ</h2>
  {actions.error&&<Alert>{actions.error}</Alert>}{actions.message&&<p role="status">{actions.message}</p>}
  <details className={styles.disclosure} open={!context}><summary>Tra cứu yêu cầu bằng mã</summary>
  <p className={styles.muted}>Bạn cũng có thể mở trực tiếp yêu cầu từ mục Thông báo.</p>
  <form className={styles.toolbar} onSubmit={e=>{e.preventDefault();void actions.run(()=>open(id));}}>
   <label>Mã yêu cầu bổ sung <input inputMode="numeric" value={id} onChange={e=>{setId(e.currentTarget.value);setContext(null);setForm(null);}}/></label>
   <button disabled={actions.busy||!idPattern.test(id)}>Đọc yêu cầu bổ sung</button>
  </form></details>
  {context&&<SupplementInfo value={context.data.supplement}>
   {context.data.latestResponse&&<p>Phản hồi đã được ghi nhận.</p>}
   {!form&&context.data.supplement.status==='open'&&context.data.supplement.deadline&&
    Date.now()<=Date.parse(context.data.supplement.deadline)&&<button className="primary" disabled={actions.busy} onClick={()=>void actions.run(start)}>Kê khai bổ sung</button>}
   {form&&<DeclarationForm client={client} context={form} initial={context.data.baseDeclaration}
    existing={context.data.baseDocuments.filter(d=>form.policyVersions.some(p=>p.documentGroups.some(g=>g.documents.some(r=>r.id===d.requirementId))))}
    target={{studyProcessId:context.data.application.studyProcessId,policyVersionId:form.policyVersions[0].id}}
    busy={actions.busy} submitLabel="Gửi phản hồi bổ sung" allowPolicySelection={form.policyVersions.length>1}
    onUpload={async file=>{const upload=new FormData();upload.append('file',file);
     return (await client.request('/api/ctsv/tep/minh-chung',{method:'POST',body:upload})).json() as Promise<Attachment>;}}
    onSubmit={submit} onCancel={()=>setForm(null)}/>}
  </SupplementInfo>}
 </section>;
}

const blankItem=():ItemInput=>({type:'declaration',reason:'',documentId:null,requirementId:null,semesterId:null,evaluationId:null,declarationField:'phoneNumber'});

function suggestedDeadline(){
 const date=new Date();
 date.setDate(date.getDate()+3);
 return new Date(date.getTime()-date.getTimezoneOffset()*60_000).toISOString().slice(0,16);
}

function ItemEditor({value,onChange}:{value:ItemInput;onChange:(next:ItemInput)=>void}) {
 return <fieldset><legend>Mục cần bổ sung</legend>
  <label>Loại yêu cầu <select value={value.type} onChange={e=>{
   const type=e.currentTarget.value;onChange({...blankItem(),type,reason:value.reason,declarationField:type==='declaration'?'phoneNumber':null});
  }}>{Object.entries(itemTypes).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>
  {value.type==='declaration'&&<label>Trường lời khai <select value={value.declarationField??''}
   onChange={e=>onChange({...value,declarationField:e.currentTarget.value})}>
   {Object.entries(claimLabels).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>}
  {(value.type==='document'?['requirementId','documentId'] as const:
   value.type==='semester'?['semesterId'] as const:value.type==='evaluation'?['evaluationId'] as const:[]).map(key=><label key={key}>
    {{requirementId:'Mã loại giấy yêu cầu',documentId:'Mã giấy đã nộp (nếu có)',semesterId:'Mã học kỳ',evaluationId:'Mã đánh giá'}[key]}
    <input required={key!=='documentId'} pattern="[1-9][0-9]*" inputMode="numeric" value={value[key]??''}
     onChange={e=>onChange({...value,[key]:e.currentTarget.value||null})}/>
   </label>)}
  <label>Nội dung cần bổ sung <input required maxLength={4000} value={value.reason} onChange={e=>onChange({...value,reason:e.currentTarget.value})}/></label>
 </fieldset>;
}

function ItemReview({item,docs,busy,onReview}:{item:Item;docs:ReviewedDocument[];busy:boolean;
 onReview:(id:string,body:Json)=>void}) {
 const [result,setResult]=useState(item.reviewResult??'needs_verification'),[reason,setReason]=useState('');
 const [file,setFile]=useState(item.responseDocumentId??'');
 return <form className={styles.form} onSubmit={e=>{e.preventDefault();onReview(item.id,{responseDocumentId:file||null,result,reason});}}>
  <h4>{item.reason}</h4>
  {item.requirementId&&<label>Giấy phản hồi <select value={file} onChange={e=>setFile(e.currentTarget.value)}>
   <option value="">Không chọn giấy</option>
   {docs.filter(d=>d.requirementId===item.requirementId).map(d=><option key={d.id} value={d.id}>{d.documentNumber||d.id}</option>)}
  </select></label>}
  <label>Kết quả mục bổ sung <select value={result} onChange={e=>setResult(e.currentTarget.value)}>
   {Object.entries(itemResults).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>
  <label>Lý do đánh giá <input required maxLength={4000} value={reason} onChange={e=>setReason(e.currentTarget.value)}/></label>
  <button disabled={busy}>Lưu đánh giá mục</button>
 </form>;
}

function CheckForm({document,busy,check,download}:{document:ReviewedDocument;busy:boolean;
 check:(id:string,hardCopy:boolean,body:Json)=>void;download:()=>void}) {
 const [kind,setKind]=useState('hard_copy'),[result,setResult]=useState('received'),[note,setNote]=useState('');
 const hardCopy=kind==='hard_copy';
 const previous=document.checks.filter(c=>c.kind===kind).at(-1);
 return <details className={styles.disclosure}><summary>Kiểm tra minh chứng {document.documentNumber||'#'+document.id}</summary><form className={styles.form} onSubmit={e=>{e.preventDefault();check(document.id,hardCopy,hardCopy?{received:result==='received',note:note||null}
  :{kind,result,note:note||null,replacesCheckId:previous?.id??null});}}>
  <h4>Giấy {document.documentNumber||document.id} · Yêu cầu {document.requirementId}</h4>
  <button type="button" disabled={busy} onClick={download}>Tải PDF để đối chiếu</button>
  <label>Loại kiểm tra <select value={kind} onChange={e=>{const type=e.currentTarget.value;setKind(type);setResult(type==='hard_copy'?'received':'pass');}}>
   <option value="hard_copy">Bản cứng</option><option value="form">Hình thức</option><option value="content">Nội dung</option>
   <option value="validity">Thời hạn</option><option value="subject">Chủ thể</option></select></label>
  <label>Kết quả kiểm giấy <select value={result} onChange={e=>setResult(e.currentTarget.value)}>
   {Object.entries(hardCopy?{received:'Đã nhận',not_received:'Chưa nhận'}:{pass:'Đạt',fail:'Không đạt',needs_verification:'Cần xác minh',not_applicable:'Không áp dụng'})
    .map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>
  <label>Ghi chú kiểm giấy <input required={!hardCopy&&result!=='pass'} maxLength={4000} value={note} onChange={e=>setNote(e.currentTarget.value)}/></label>
  <button disabled={busy}>Lưu kiểm giấy</button>
  <ul>{document.checks.map(c=><li key={c.id}>{c.kind} · {c.result} · {new Date(c.checkedAt).toLocaleString('vi-VN')} {c.note}</li>)}</ul>
 </form></details>;
}

export function StaffSupplement({client,application,rootTag,refresh,download}:{client:AuthClient;application:Application;
 rootTag:string|null;refresh:()=>Promise<void>;download:(id:string)=>Promise<void>}) {
 const actions=useActions();
 const [id,setId]=useState(''),[data,setData]=useState<Tagged<Supplement>|null>(null),[review,setReview]=useState<Review|null>(null);
 const [reason,setReason]=useState(''),[deadline,setDeadline]=useState(suggestedDeadline),[changeReason,setChangeReason]=useState(''),[newDeadline,setNewDeadline]=useState('');
 const [updateType,setType]=useState('initial_supplement'),[round,setRound]=useState(''),[benefit,setBenefit]=useState('');
 const [items,setItems]=useState([{key:0,value:blankItem()}]);
 const [section,setSection]=useState('round');
 const itemSerial=useRef(0);
 const initial=updateType==='initial_supplement';
 async function readRound(key:string){
  setData(null);
  const response=await tagged<Supplement>(client,supplements+key);
  if(response.data.applicationId!==application.id)throw new Error('Yêu cầu bổ sung thuộc hồ sơ khác.');
  setData(response);
  if(response.data.status==='responded')await refreshReview();
 }
 const [reviewTag,setReviewTag]=useState<string|null>(null);
 async function refreshReview(){
  const url=applications+application.id;
  const source=await tagged<Application>(client,url);
  const current=await get<Review>(client,url+'/ngu-canh-xet-duyet');
  const after=await tagged<Application>(client,url);
  if(source.tag!==after.tag)throw new Error('Dữ liệu đã thay đổi. Hãy đọc lại trước khi thao tác.');
  setReview(current);setReviewTag(after.tag);
 }
 async function send(event:FormEvent){
  event.preventDefault();
  await actions.run(async()=>{
   const latest=application.submittedVersions[0];
   if(!latest || !rootTag)throw new Error('Hãy đọc lại hồ sơ trước khi mở vòng bổ sung.');
   const result=await write<Supplement>(client,applications+application.id+'/yeu-cau-bo-sung','POST',rootTag,{
    sourceVersionId:latest.id,receivingRoundId:initial?application.roundId:round,benefitId:initial?null:benefit,
    updateType,reason,deadline:new Date(deadline).toISOString(),items:items.map(i=>i.value),
   });
   setId(result.id);setReason('');setDeadline(suggestedDeadline());setItems([{key:0,value:blankItem()}]);
   actions.setMessage('Đã gửi yêu cầu bổ sung.');
   await Promise.all([readRound(result.id),refresh()]);
  });
 }
 function mutate(path:string,method:'POST'|'PUT',payload:Json){
  if(!data)return;
  const saved=data;
  return actions.run(async()=>{
   await write<Supplement>(client,path,method,saved.tag,payload);
   actions.setMessage('Đã cập nhật vòng bổ sung.');
   await Promise.all([readRound(saved.data.id),refresh()]);
  });
 }
 function check(id:string,hardCopy:boolean,payload:Json){
  if(!reviewTag)return;
  void actions.run(async()=>{
   await write(client,base+'/giay-to-da-nop/'+id+(hardCopy?'/xac-nhan-ban-cung':'/kiem-tra'),'POST',reviewTag,payload);
   actions.setMessage('Đã ghi nhận kiểm tra giấy.');
   await Promise.all([refreshReview(),refresh()]);
  });
 }
 const activeState=data&&['open','responded','expired'].includes(data.data.status);
 const latest=review?.submittedVersions[0]?.id;
 const currentDocs=review?.documents.filter(d=>d.submissionId===latest)??[];
 return <section className={styles.taskGroup} aria-label="Thụ lý hồ sơ" aria-busy={actions.busy}>
  <h3>Bổ sung và kiểm giấy</h3>
  {actions.error&&<Alert>{actions.error}</Alert>}{actions.message&&<p role="status">{actions.message}</p>}
  <div className={styles.tabs} aria-label="Tác vụ chuyên viên">
   <button type="button" aria-pressed={section==='round'} onClick={()=>setSection('round')}>Vòng bổ sung</button>
   <button type="button" aria-pressed={section==='papers'} onClick={()=>{setSection('papers');void actions.run(refreshReview);}}>Đối chiếu & kiểm giấy</button>
  </div>
  {section==='round'&&<div className={styles.taskGroup}>
  {application.submittedVersions.length>0&&application.status!=='withdrawn'&&<details className={styles.disclosure}>
   <summary>Mở vòng bổ sung</summary>
   <form className={styles.form} onSubmit={send}>
    <label>Loại cập nhật <select value={updateType} onChange={e=>setType(e.currentTarget.value)}>
     <option value="initial_supplement">Bổ sung hồ sơ ban đầu</option><option value="periodic_update">Cập nhật định kỳ</option>
     <option value="information_change">Thay đổi thông tin</option><option value="legal_refresh">Cập nhật giấy tờ hết hiệu lực</option>
    </select></label>
    {!initial&&<><label>Mã đợt tiếp nhận <input required inputMode="numeric" pattern="[1-9][0-9]*" value={round} onChange={e=>setRound(e.currentTarget.value)}/></label>
     <label>Mã quyền hưởng <input required inputMode="numeric" pattern="[1-9][0-9]*" value={benefit} onChange={e=>setBenefit(e.currentTarget.value)}/></label></>}
    <label>Lý do mở vòng <input required maxLength={4000} value={reason} onChange={e=>setReason(e.currentTarget.value)}/></label>
    <label>Hạn bổ sung <input required type="datetime-local" value={deadline} onChange={e=>setDeadline(e.currentTarget.value)}/></label>
    {items.map(({key,value})=><div key={key}><ItemEditor value={value} onChange={next=>setItems(current=>current.map(i=>i.key===key?{key,value:next}:i))}/>
     {items.length>1&&<button type="button" onClick={()=>setItems(current=>current.filter(i=>i.key!==key))}>Bỏ mục</button>}</div>)}
    <button type="button" onClick={()=>setItems(current=>[...current,{key:++itemSerial.current,value:blankItem()}])}>Thêm mục bổ sung</button>
    <button disabled={actions.busy}>Gửi yêu cầu bổ sung</button>
   </form>
  </details>}
  <form className={styles.toolbar} onSubmit={e=>{e.preventDefault();void actions.run(()=>readRound(id));}}>
   <label>Mã vòng bổ sung <input inputMode="numeric" value={id} onChange={e=>{setId(e.currentTarget.value);setData(null);}}/></label>
   <button disabled={actions.busy||!idPattern.test(id)}>Đọc vòng bổ sung</button>
  </form>
  {data&&<SupplementInfo value={data.data}>
   {activeState&&<>
    <details className={styles.disclosure}><summary>Gia hạn phản hồi</summary><form onSubmit={e=>{e.preventDefault();void mutate(supplements+data.data.id+'/han-nop','PUT',
     {deadline:new Date(newDeadline).toISOString(),reason:changeReason});}}>
     <label>Hạn mới <input required type="datetime-local" value={newDeadline} onChange={e=>setNewDeadline(e.currentTarget.value)}/></label>
     <label>Lý do thay đổi hạn <input required value={changeReason} maxLength={4000} onChange={e=>setChangeReason(e.currentTarget.value)}/></label>
     <button disabled={actions.busy}>Gia hạn</button>
    </form></details>
    <CloseForm busy={actions.busy} canComplete={data.data.status==='responded'} onClose={(complete,reason)=>
     void mutate(supplements+data.data.id+(complete?'/hoan-tat':'/huy'),'POST',{reason})}/>
   </>}
   {data.data.status==='responded'&&<div className={styles.taskGroup}>{data.data.items.map(item=><ItemReview key={data.tag+item.id} item={item} docs={currentDocs}
    busy={actions.busy} onReview={(key,body)=>void mutate(base+'/muc-bo-sung/'+key+'/danh-gia','PUT',body)}/>)}</div>}
  </SupplementInfo>}
  </div>}
  {section==='papers'&&<>
  <button type="button" disabled={actions.busy} onClick={()=>void actions.run(refreshReview)}>Đọc đối chiếu và giấy đã nộp</button>
  {review&&<section className={styles.taskGroup} aria-label="Đối chiếu lời khai và giấy">
   <div className={styles.reviewTable}><table><thead><tr><th>Thông tin</th><th>Sinh viên kê khai</th><th>Nguồn đào tạo</th><th>Đối chiếu</th></tr></thead><tbody>
    {review.comparisons.map(c=><tr key={c.field}><th>{claimLabels[c.field]??c.field}</th><td>{c.declaredValue??'—'}</td><td>{c.sourceValue??'Chưa có nguồn'}</td>
     <td>{{trung_khop:'Trùng khớp',khac_biet:'Cần đối chiếu',khong_co_nguon:'Chưa có nguồn'}[c.status??'']??c.status??'—'}</td></tr>)}
   </tbody></table></div>
   {currentDocs.map(doc=><CheckForm key={doc.id} document={doc} busy={actions.busy} check={check} download={()=>void actions.run(()=>download(doc.fileId))}/>)}
   {review.documents.filter(d=>d.submissionId!==latest).map(d=><p key={d.id}>Giấy lịch sử {d.documentNumber||d.id} · {d.checks.length} lượt kiểm</p>)}
  </section>}</>}
 </section>;
}

function CloseForm({busy,canComplete,onClose}:{busy:boolean;canComplete:boolean;onClose:(complete:boolean,reason:string)=>void}){
 const [reason,setReason]=useState('');
 return <details className={styles.disclosure}><summary>Đóng vòng bổ sung</summary>
  <label>Lý do đóng vòng <input value={reason} maxLength={4000} onChange={e=>setReason(e.currentTarget.value)}/></label>
  <button type="button" className="danger" disabled={busy||!reason.trim()} onClick={()=>onClose(false,reason)}>Hủy vòng</button>
  {canComplete&&<button type="button" disabled={busy||!reason.trim()} onClick={()=>onClose(true,reason)}>Hoàn tất vòng</button>}
 </details>;
}
