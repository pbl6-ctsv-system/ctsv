import {useEffect,useId,useState,useRef,type FormEvent} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {errorMessage} from '../../../shared/api/error-message';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {Alert} from '../../../shared/ui/Alert';
import styles from './IntakeWorkspace.module.css';
import {displayName} from '../presentation.mjs';

type Requirement={id:string;name:string;type:string;evidenceSubject:string|null};
type Policy={id:string;policyId:string;name:string;documentGroups:{id:string;name:string;minimumRequired:number;documents:Requirement[]}[]};
export type FormContext={student:{studentCode:string};round:{id:string;name:string;semesterId:string;declarationGuidance:string|null};
 sourceStatus:string;suggestedClaims:Record<string,string|null>;studyProcesses:{id:string;code:string}[];policyVersions:Policy[]};
export type Attachment={id:string;fileName:string};
type Existing={requirementId:string;fileId:string;documentNumber:string|null;issuer:string|null;issuedOn?:string|null;
 expiresOn?:string|null;relatedPersonIndex?:number|null};
type Doc={requirementId:string;fileId:string;documentNumber:string|null;issuer:string|null;issuedOn:string|null;expiresOn:string|null;relatedPersonIndex:number|null};
export type SubmissionForm={roundId:string;studyProcessId:string;policyVersionId:string;claims:Record<string,string>;
 requestedSemesterIds:string[];statements:Record<string,boolean|null>;relatedPeople:Record<string,string|null>[];documents:Doc[]};
type History={claims:Record<string,string|null>;relatedPeople:Record<string,string|null>[];requestedSemesters:{semesterId:string}[];statements:Record<string,boolean|null>};
const fields={fullName:'Họ tên',citizenId:'CCCD',birthDate:'Ngày sinh',birthPlace:'Nơi sinh',gender:'Giới tính',
 ethnicityId:'ID dân tộc',classId:'ID lớp',studentCode:'Mã sinh viên',cohortId:'ID khóa học',managementUnitId:'ID đơn vị quản lý',
 permanentAddress:'Địa chỉ thường trú',phoneNumber:'Số điện thoại'};
const personFields={relation:'Quan hệ',fullName:'Họ tên người liên quan',citizenId:'CCCD người liên quan',ethnicityId:'ID dân tộc',
 permanentAddress:'Địa chỉ thường trú',localityCode:'Mã địa bàn',workplaceUnitId:'ID nơi công tác',workplaceName:'Tên nơi công tác',
 workplaceGroup:'Nhóm đơn vị',employmentStatus:'Tình trạng công tác',statusFrom:'Bắt đầu',statusUntil:'Kết thúc',
 matchingStaffCode:'Mã cán bộ đối chiếu'};
const statementFields={firstDegree:'Đang học văn bằng thứ nhất',alreadyBenefitedAtSameLevel:'Đã hưởng ở cùng trình độ',
 salariedWhileStudying:'Đang hưởng lương khi đi học'};
const idFields=new Set(['ethnicityId','classId','cohortId','managementUnitId','workplaceUnitId']);
const catalogKinds:Record<string,string>={ethnicityId:'ethnicities',classId:'classes',cohortId:'cohorts',managementUnitId:'units'};
type Reference={id:string;name?:string|null;code?:string|null};
function ReferenceField({label,value,items,onChange}:{label:string;value:string;items:Reference[];onChange:(value:string)=>void}){
 const listId=useId();
 const readable=label.replace(/^ID /,'').replace(/^./,letter=>letter.toUpperCase());
 const title=(item:Reference)=>[item.name,item.code].filter(Boolean).join(' · ')||'Mã '+item.id;
 const chosen=items.find(item=>item.id===value);
 return <label>{readable}<input aria-label={readable} list={listId} required value={chosen?title(chosen):value} onChange={event=>{
  const text=event.currentTarget.value;onChange(items.find(item=>title(item)===text)?.id??text);
 }}/><datalist id={listId}>{items.map(item=><option key={item.id} value={title(item)}/>)}</datalist>
 <small className={styles.muted}>Chọn tên gợi ý hoặc nhập mã danh mục.</small></label>;
}
const submitFields:Record<string,string>={...personFields,...fields,...statementFields,
 requestedSemesterIds:'Học kỳ đề nghị',declarationConfirmed:'Cam kết lời khai',
 relatedPersonIndex:'Người liên quan trên giấy minh chứng',requirementId:'Loại giấy minh chứng',documents:'Minh chứng PDF'};

export function submissionErrorMessage(error:unknown) {
 if(error instanceof ApiError && error.code==='VALIDATION_ERROR'){
  const messages=error.fields.map(field=>{
   if(field==='claims')return 'Kiểm tra mã dân tộc, lớp, khóa học và đơn vị quản lý. Các mã phải tồn tại trong dữ liệu CTSV.';
   if(field==='requestedSemesterIds')return 'Kiểm tra học kỳ đề nghị. Mỗi mã phải tồn tại trong CTSV và chỉ được nhập một lần.';
   if(!Object.hasOwn(submitFields,field))return '';
   return idFields.has(field)
    ? submitFields[field]+' phải là mã số có trong dữ liệu CTSV.'
    : 'Kiểm tra lại '+submitFields[field]+'.';
  }).filter(Boolean);
  if(messages.length)return [...new Set(messages)].join(' ');
 }
 return errorMessage(error);
}

export function DeclarationForm({context,initial,existing,target,busy,onUpload,onSubmit,onCancel,submitLabel='Nộp hồ sơ',allowPolicySelection=false,client}:{
 context:FormContext;initial:History|null;existing:Existing[];target:{studyProcessId:string;policyVersionId:string}|null;
 busy:boolean;onUpload:(file:File)=>Promise<Attachment>;onSubmit:(event:FormEvent,form:SubmissionForm)=>Promise<void>;onCancel:()=>void;submitLabel?:string;
 allowPolicySelection?:boolean;
 client?:AuthClient;
}) {
 const [claims,setClaims]=useState(()=>Object.fromEntries(Object.keys(fields).map(k=>[k,(initial?.claims??context.suggestedClaims)[k]??''])));
 const [process,setProcess]=useState(target?.studyProcessId??context.studyProcesses[0]?.id??'');
 const [policy,setPolicy]=useState(target?.policyVersionId??context.policyVersions[0]?.id??'');
 const [semesters,setSemesters]=useState(initial?.requestedSemesters.map(r=>r.semesterId).join(', ')??context.round.semesterId);
 const [statements,setStatements]=useState<Record<string,boolean|null>>(()=>initial?.statements??{firstDegree:null,alreadyBenefitedAtSameLevel:null,salariedWhileStudying:null});
 const [people,setPeople]=useState<Record<string,string|null>[]>(()=>initial?.relatedPeople??[]);
 const [docs,setDocs]=useState<Record<string,Doc>>(()=>Object.fromEntries(existing.map(d=>[d.requirementId,{
  ...d,issuedOn:d.issuedOn??null,expiresOn:d.expiresOn??null,relatedPersonIndex:d.relatedPersonIndex??null
 }])));
 const [attachments,setAttachments]=useState<Attachment[]>(()=>Array.from(new Set(existing.map(d=>d.fileId))).map(id=>({id,fileName:'PDF '+id})));
 const [uploading,setUploading]=useState(false),[error,setError]=useState('');
 const [step,setStep]=useState(0);
 const formElement=useRef<HTMLFormElement>(null);
 const stepNames=['Thông tin sinh viên','Người liên quan','Minh chứng & nộp'];
 const [references,setReferences]=useState<Record<string,Reference[]>>({});
 useEffect(()=>{
  if(!client)return;
  const controller=new AbortController();
  void Promise.allSettled(Object.entries(catalogKinds).map(async([field,kind])=>{
   const response=await client.request('/api/ctsv/nen-tang/danh-muc?kind='+kind+'&size=100',{signal:controller.signal});
   const page=await response.json();return [field,page.items] as const;
  })).then(results=>{
   if(controller.signal.aborted)return;
   setReferences(Object.fromEntries(results.flatMap(result=>result.status==='fulfilled'?[result.value]:[])));
  });
  return ()=>controller.abort();
 },[client]);
 const selected=context.policyVersions.find(p=>p.id===policy);
 const parents=people.flatMap((person,index)=>
  ['cha','me','mẹ','bố','bo','ba','father','mother'].includes(person.relation?.trim().toLowerCase()??'')?[{person,index}]:[]);

 async function upload(file:File) {
  setError('');
  if(!file.name.toLowerCase().endsWith('.pdf')){setError('Chỉ chấp nhận tệp PDF.');return;}
  if(file.size>10_000_000){setError('PDF minh chứng tối đa 10.000.000 byte.');return;}
  setUploading(true);
  try{const added=await onUpload(file);setAttachments(previous=>[...previous,added]);}
  catch(e){setError(errorMessage(e));}
  finally{setUploading(false);}
 }

 function updateDoc(requirement:string,delta:Partial<Doc>) {
  setDocs(all=>{
   const defaults={requirementId:requirement,fileId:'',documentNumber:null,issuer:null,issuedOn:null,expiresOn:null,relatedPersonIndex:null};
   const current=all[requirement]??defaults;
   return {...all,[requirement]:{...current,...delta}};
  });
 }

 function submit(event:FormEvent) {
  event.preventDefault();setError('');
  if(!selected||!process||selected.documentGroups.some(g=>g.documents.filter(d=>docs[d.id]?.fileId).length<g.minimumRequired)){
   setError('Chọn quá trình, chính sách và tải đủ giấy theo từng nhóm.');return;
  }
  const documents=selected.documentGroups.flatMap(group=>group.documents.flatMap(requirement=>{
   const doc=docs[requirement.id];
   if(!doc?.fileId)return [];
   if(requirement.evidenceSubject!=='cha_hoac_me')return [doc];
   const relatedPersonIndex=parents.length===1?parents[0].index:
    parents.some(parent=>parent.index===doc.relatedPersonIndex)?doc.relatedPersonIndex:null;
   return [{...doc,relatedPersonIndex}];
  }));
  const parentRequirements=new Set(selected.documentGroups.flatMap(group=>group.documents)
   .filter(requirement=>requirement.evidenceSubject==='cha_hoac_me').map(requirement=>requirement.id));
  if(documents.some(doc=>parentRequirements.has(doc.requirementId)&&doc.relatedPersonIndex===null)){
   setError('Kê khai cha hoặc mẹ và chọn người được xác nhận trên giấy cư trú.');return;
  }
  void onSubmit(event,{roundId:context.round.id,studyProcessId:process,policyVersionId:policy,claims,
   requestedSemesterIds:semesters.split(',').map(x=>x.trim()).filter(Boolean),relatedPeople:people,
   statements:{firstDegree:null,alreadyBenefitedAtSameLevel:null,salariedWhileStudying:null,...statements,declarationConfirmed:true},
   documents:documents.map(d=>({requirementId:d.requirementId,fileId:d.fileId,documentNumber:d.documentNumber,
    issuer:d.issuer,issuedOn:d.issuedOn,expiresOn:d.expiresOn,relatedPersonIndex:d.relatedPersonIndex}))});
 }

 return <form ref={formElement} className={styles.form} onSubmit={submit} onInvalidCapture={event=>{
  event.preventDefault();
  const input=event.target as HTMLInputElement;
  const group=input.closest('[data-form-step]');
  if(group)setStep(Number(group.getAttribute('data-form-step')));
  setError('Vui lòng kiểm tra '+(input.labels?.[0]?.textContent?.trim().slice(0,80)??'các trường bắt buộc')+'.');
  requestAnimationFrame(()=>input.focus());
 }}>
  <div><h2>Kê khai hồ sơ</h2><p className={styles.muted}>{displayName(context.round.name)}</p></div>
  <div className={styles.tabs} aria-label="Các phần kê khai">{stepNames.map((name,index)=><button type="button" key={name}
   aria-current={step===index?'step':undefined} onClick={()=>setStep(index)}>{index+1}. {name}</button>)}</div>
  {context.round.declarationGuidance&&<p>{context.round.declarationGuidance}</p>}
  {context.sourceStatus!=='ready'&&<p>Thiếu một phần dữ liệu nguồn. Kiểm tra và bổ sung lời khai trước khi nộp.</p>}
  {error&&<Alert>{error}</Alert>}
  <fieldset data-form-step="0" hidden={step!==0} disabled={busy||uploading}>
   <legend>Thông tin sinh viên</legend>
   <div className={styles.fields}>{Object.entries(fields).map(([key,label])=>catalogKinds[key]?<ReferenceField key={key} label={label} value={claims[key]}
    items={references[key]??[]} onChange={value=>setClaims(previous=>({...previous,[key]:value}))}/>:<label key={key}>{label}
    <input type={key==='birthDate'?'date':'text'} inputMode={idFields.has(key)?'numeric':undefined} required readOnly={key==='studentCode'} value={claims[key]} onChange={e=>{
     const value=e.currentTarget.value;setClaims(previous=>({...previous,[key]:value}));
    }}/></label>)}</div>
   <div className={styles.fields}>
    <label>Quá trình học<select required value={process} disabled={!!target} onChange={e=>setProcess(e.currentTarget.value)}>
     <option value="">Chọn quá trình</option>{context.studyProcesses.map(p=><option value={p.id} key={p.id}>{p.code}</option>)}</select></label>
    <label>Chính sách<select required value={policy} disabled={!!target&&!allowPolicySelection} onChange={e=>{setPolicy(e.currentTarget.value);setDocs({});}}>
     <option value="">Chọn chính sách</option>{context.policyVersions.map(p=><option value={p.id} key={p.id}>{displayName(p.name)}</option>)}</select></label>
    <label>ID học kỳ đề nghị, cách nhau bằng dấu phẩy<input required value={semesters} onChange={e=>setSemesters(e.currentTarget.value)}/></label>
   </div>
   <div className={styles.fields}>{Object.entries(statementFields).map(([key,label])=><label key={key}>{label}
    <select value={statements[key]===null||statements[key]===undefined?'':String(statements[key])} onChange={e=>{
     const value=e.currentTarget.value;setStatements(previous=>({...previous,[key]:value===''?null:value==='true'}));
    }}><option value="">Chưa khai</option><option value="true">Có</option><option value="false">Không</option></select></label>)}</div>
  </fieldset>
  <fieldset data-form-step="1" hidden={step!==1} disabled={busy||uploading}>
   <legend>Người liên quan</legend>
   <p className={styles.muted}>Kê khai khi giấy tờ chứng minh có thông tin cha, mẹ hoặc người liên quan.</p>
   {people.map((person,index)=><fieldset key={index}><legend>Người liên quan {index+1}</legend><div className={styles.fields}>
    {Object.entries(personFields).map(([key,label])=><label key={key}>{label}<input type={key==='statusFrom'||key==='statusUntil'?'date':'text'}
     value={person[key]??''} onChange={e=>{
      const value=e.currentTarget.value;setPeople(previous=>previous.map((row,i)=>i===index?{...row,[key]:value||null}:row));
     }}/></label>)}
   </div></fieldset>)}
   <button type="button" onClick={()=>setPeople(previous=>[...previous,Object.fromEntries(Object.keys(personFields).map(k=>[k,null]))])}>Thêm người liên quan</button>
  </fieldset>
  <fieldset data-form-step="2" hidden={step!==2} disabled={busy||uploading}>
   <legend>Minh chứng PDF</legend>
   <label>Chọn PDF minh chứng<input type="file" accept=".pdf,application/pdf" onChange={e=>{
    const file=e.currentTarget.files?.[0];e.currentTarget.value='';if(file)void upload(file);
   }}/></label>
   {uploading&&<p role="status">Đang tải PDF...</p>}
   {attachments.length>0&&<p role="status">PDF đã tải: {attachments.map(a=>a.fileName).join(', ')}</p>}
   {selected?.documentGroups.map(group=><fieldset key={group.id}><legend>{group.name} · Ít nhất {group.minimumRequired}</legend>
    {group.documents.map(req=><div key={req.id} className={styles.fields}>
     <label>PDF cho {req.name}<select value={docs[req.id]?.fileId??''} onChange={e=>updateDoc(req.id,{fileId:e.currentTarget.value})}>
      <option value="">Chọn PDF</option>{attachments.map(a=><option value={a.id} key={a.id}>{a.fileName}</option>)}
     </select></label>
     {req.evidenceSubject==='cha_hoac_me'&&docs[req.id]?.fileId&&parents.length===0&&<p>Kê khai cha hoặc mẹ trong phần Người liên quan để gắn giấy này.</p>}
     {req.evidenceSubject==='cha_hoac_me'&&docs[req.id]?.fileId&&parents.length>1&&<label>Cha hoặc mẹ trên {req.name}
      <select value={docs[req.id]?.relatedPersonIndex??''} onChange={e=>updateDoc(req.id,{
       relatedPersonIndex:e.currentTarget.value===''?null:Number(e.currentTarget.value)})}>
       <option value="">Chọn cha hoặc mẹ</option>{parents.map(({person,index})=><option key={index} value={index}>{person.fullName||'Người liên quan '+(index+1)}</option>)}
      </select></label>}
    </div>)}
   </fieldset>)}
  </fieldset>
  <div data-form-step="2" hidden={step!==2}><label><input type="checkbox" required/> Tôi cam kết lời khai đúng sự thật</label></div>
  <div className={styles.formFooter}><span className={styles.muted}>Phần {step+1} / 3</span><div className={styles.actions}>
   <button type="button" disabled={busy||uploading} onClick={onCancel}>Hủy kê khai</button>
   {step>0&&<button type="button" disabled={busy||uploading} onClick={()=>setStep(step-1)}>Quay lại</button>}
   {step<2?<button type="button" className="primary" disabled={busy||uploading} onClick={()=>{
    const fields=formElement.current?.querySelectorAll<HTMLInputElement|HTMLSelectElement>('[data-form-step="'+step+'"] input,[data-form-step="'+step+'"] select');
    const invalid=Array.from(fields??[]).find(input=>!input.checkValidity());
    if(invalid){invalid.reportValidity();return;}
    setError('');setStep(step+1);
   }}>Tiếp tục</button>:<button type="submit" disabled={busy||uploading}>{busy?'Đang gửi…':submitLabel}</button>}
  </div></div>
 </form>;
}
