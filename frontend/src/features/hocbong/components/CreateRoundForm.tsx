import {useEffect,useRef,useState,type FormEvent} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {errorMessage} from '../../../shared/api/error-message';
import {Alert} from '../../../shared/ui/Alert';
import {Button} from '../../../shared/ui/Button';
import {createRound,listRounds,readRound} from '../api/rounds';
import type {CreateRoundCommand,RoundView,SourceSemester} from '../types/round';
import styles from './CreateRoundForm.module.css';

type Props={client:AuthClient;initialSource:SourceSemester;onCancel:()=>void;onCreated:(view:RoundView,recovered:boolean)=>Promise<void>;onForbidden:()=>void};
type FieldErrors=Partial<Record<'sourceSemesterId'|'number'|'name',string>>;
const savedError='Đợt đã được tạo nhưng chưa tải lại đầy đủ. Hãy đọc lại kết quả; hệ thống không gửi lại lệnh tạo.';
const unknownError='Chưa xác nhận được kết quả tạo đợt. Hãy kiểm tra đợt đã lưu trước khi gửi lại; hệ thống không tự tạo lại.';

export function CreateRoundForm({client,initialSource,onCancel,onCreated,onForbidden}:Props){
 const source=initialSource;
 const [sourceBlocked,setSourceBlocked]=useState(false);
 const [number,setNumber]=useState('1'),[name,setName]=useState('');
 const [error,setError]=useState(''),[fields,setFields]=useState<FieldErrors>({});
 const [busy,setBusy]=useState(false);
 const [saved,setSaved]=useState<RoundView|null>(null),[uncertain,setUncertain]=useState<CreateRoundCommand|null>(null),[found,setFound]=useState<RoundView|null>(null);
 const pending=useRef(false),alive=useRef(true),heading=useRef<HTMLHeadingElement>(null);
 useEffect(()=>{alive.current=true;heading.current?.focus();return()=>{alive.current=false;};},[]);
 function begin(){if(pending.current)return false;pending.current=true;setBusy(true);setError('');return true;}
 function end(){pending.current=false;if(alive.current)setBusy(false);}
 function forbidden(error:unknown){if(error instanceof ApiError&&error.status===403){onForbidden();return true;}return false;}
 async function submit(event:FormEvent<HTMLFormElement>){
  event.preventDefault();
  if(pending.current||saved||uncertain||sourceBlocked)return;
  const invalid:FieldErrors={};
  if(!source?.selectable)invalid.sourceSemesterId='Chọn một học kỳ đủ điều kiện.';
  if(!Number.isInteger(Number(number))||Number(number)<1||Number(number)>2147483647)invalid.number='Số đợt phải là số nguyên từ 1 đến 2147483647.';
  if(!name.trim()||name.trim().length>512)invalid.name='Tên đợt cần từ 1 đến 512 ký tự.';
  setFields(invalid);
  if(Object.keys(invalid).length||!source)return;
  if(!begin())return;
  const command={sourceSemesterId:source.sourceSemesterId,number:Number(number),name};
  let created:RoundView|null=null;
  try{
   created=await createRound(client,command);
   if(!alive.current)return;
   setSaved(created);
   await onCreated(created,false);
  }catch(error){
   if(!alive.current||forbidden(error))return;
   if(created){setError(savedError);return;}
   if(error instanceof ApiError&&error.status<500){
    if(error.code==='ROUND_EXISTS')setError('Số đợt đã tồn tại trong học kỳ. Hãy chọn số đợt khác.');
    else if(['SOURCE_SEMESTER_UNAVAILABLE','SEMESTER_MAPPING_REQUIRED','SEMESTER_MAPPING_CONFLICT'].includes(error.code)){
     setError('Học kỳ nguồn chưa đủ điều kiện hoặc đã thay đổi. Hãy chọn lại sau khi dữ liệu được đọc mới.');
     setSourceBlocked(true);
    }else if(error.code==='VALIDATION_ERROR'){
     setError('Thông tin tạo đợt chưa hợp lệ. Hãy kiểm tra các trường được đánh dấu.');
     const next:FieldErrors={};
     for(const field of error.fields??[]){
      if(field==='name')next.name='Tên đợt chưa hợp lệ.';
      if(field==='number')next.number='Số đợt chưa hợp lệ.';
      if(field==='sourceSemesterId')next.sourceSemesterId='Học kỳ nguồn chưa hợp lệ.';
     }
     setFields(next);
    }else setError(errorMessage(error));
   }else{setUncertain(command);setError(unknownError);}
  }finally{end();}
 }
 async function retrySaved(view:RoundView,recovered=false){
  if(!begin())return;
  try{await onCreated(view,recovered);}catch(error){if(alive.current&&!forbidden(error))setError(savedError);}finally{end();}
 }
 async function checkSaved(){
  if(!uncertain||!begin())return;
  try{
   const page=await listRounds(client,{sourceSemesterId:uncertain.sourceSemesterId,number:uncertain.number});
   if(!alive.current)return;
   if(page.totalElements===0){setError('Chưa tìm thấy đợt theo học kỳ và số đợt đã gửi. Bạn có thể kiểm tra lại; hệ thống không tự gửi thêm lệnh tạo.');return;}
   if(page.totalElements!==1||page.items.length!==1)throw new Error('Kết quả đối chiếu không duy nhất. Vui lòng kiểm tra danh sách đợt xét.');
   const view=await readRound(client,page.items[0].id);
   if(alive.current)setFound(view);
  }catch(error){if(alive.current&&!forbidden(error))setError(errorMessage(error));}finally{end();}
 }
 return <section className={styles.formArea} aria-labelledby="create-round-title">
  <h2 id="create-round-title" ref={heading} tabIndex={-1}>Tạo đợt xét</h2>
  <p>Nhập thông tin đợt trong học kỳ đã chọn. Đợt được lưu ở trạng thái Nháp để cấu hình trước khi mở.</p>
  <form aria-label="Tạo đợt xét KKHT" onSubmit={submit} className={styles.form}>
   <fieldset className={styles.information}>
    <legend>Học kỳ xét</legend><p>{source.code} · {source.name??'Chưa có tên'}</p>
    <p>{source.academicYear} · Học kỳ {source.semesterOrder}</p>
    <p className={styles.hint}>Để đổi học kỳ, hủy tạo đợt và chọn lại từ danh sách.</p>
    {fields.sourceSemesterId&&<p className={styles.fieldError}>{fields.sourceSemesterId}</p>}
   </fieldset>
   <fieldset className={styles.information} disabled={busy||!!saved||!!uncertain}>
    <legend>Thông tin đợt</legend>
    <label htmlFor="create-round-number">Số đợt<input id="create-round-number" type="number" min="1" max="2147483647" step="1" required value={number} onChange={event=>{setNumber(event.target.value);setFields(current=>({...current,number:undefined}));}} aria-invalid={!!fields.number} aria-describedby={fields.number?'create-number-error':undefined}/></label>
    {fields.number&&<p id="create-number-error" className={styles.fieldError}>{fields.number}</p>}
    <label htmlFor="create-round-name">Tên đợt<input id="create-round-name" required value={name} onChange={event=>{setName(event.target.value);setFields(current=>({...current,name:undefined}));}} aria-invalid={!!fields.name} aria-describedby={fields.name?'create-name-error':'create-name-hint'}/></label>
    <p id="create-name-hint" className={styles.hint}>Tối đa 512 ký tự sau khi bỏ khoảng trắng đầu và cuối.</p>
    {fields.name&&<p id="create-name-error" className={styles.fieldError}>{fields.name}</p>}
   </fieldset>
   {error&&<Alert>{error}</Alert>}
   {uncertain&&<div className={styles.recovery}>
    {found&&<><p>Đợt tìm thấy: {found.round.name}</p><p>Học kỳ {found.round.semester.semesterOrder} · {found.round.semester.academicYear} · Số đợt {found.round.number}</p><p>Đối chiếu thông tin trước khi mở đợt hiện có.</p><Button disabled={busy} onClick={()=>void retrySaved(found,true)}>Mở đợt tìm thấy</Button></>}
    <Button variant={found?'secondary':'primary'} disabled={busy} onClick={()=>void checkSaved()}>Kiểm tra đợt đã lưu</Button>
    <Button variant="secondary" disabled={busy} onClick={()=>{setUncertain(null);setFound(null);setError('');}}>Quay lại chỉnh sửa</Button>
   </div>}
   <div className={styles.actions}>
    {!saved&&!uncertain&&<Button type="submit" disabled={busy||sourceBlocked||!source.selectable}>{busy?'Đang tạo…':'Tạo đợt xét'}</Button>}
    {saved&&<Button disabled={busy} onClick={()=>void retrySaved(saved)}>Đọc lại kết quả</Button>}
    <Button variant="secondary" disabled={busy} onClick={onCancel}>Quay lại danh sách</Button>
   </div>
  </form>
 </section>;
}
