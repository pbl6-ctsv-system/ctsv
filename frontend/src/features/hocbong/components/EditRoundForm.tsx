import {useEffect,useRef,useState,type FormEvent} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {errorMessage} from '../../../shared/api/error-message';
import {Alert} from '../../../shared/ui/Alert';
import {Button} from '../../../shared/ui/Button';
import {isRoundEtag,readRound,updateRound} from '../api/rounds';
import type {RoundView} from '../types/round';
import styles from './CreateRoundForm.module.css';

type Props={client:AuthClient;initial:RoundView;onCancel:()=>void;onSaved:(view:RoundView,recovered:boolean)=>Promise<void>;onForbidden:()=>void};
type Phase='editing'|'conflict'|'unknown'|'saved';
const savedError='Thay đổi đã được lưu nhưng chưa tải lại đầy đủ. Hãy đọc lại kết quả; hệ thống không gửi lại lệnh lưu.';
const unknownError='Chưa xác nhận được kết quả sửa đợt. Hãy kiểm tra bản đang lưu trước khi gửi lại; hệ thống không tự lưu lại.';

export function EditRoundForm({client,initial,onCancel,onSaved,onForbidden}:Props){
 const [base,setBase]=useState(initial),[number,setNumber]=useState(String(initial.round.number)),[name,setName]=useState(initial.round.name);
 const [phase,setPhase]=useState<Phase>(isRoundEtag(initial.etag)?'editing':'conflict');
 const [error,setError]=useState(isRoundEtag(initial.etag)?'':'Chưa có phiên dữ liệu hợp lệ. Hãy đọc bản mới trước khi lưu.');
 const [fields,setFields]=useState<Partial<Record<'number'|'name',string>>>({});
 const [info,setInfo]=useState(''),[busy,setBusy]=useState(false),[locked,setLocked]=useState(initial.round.status!=='draft');
 const [latest,setLatest]=useState<RoundView|null>(null),[saved,setSaved]=useState<RoundView|null>(null);
 const pending=useRef(false),alive=useRef(true),heading=useRef<HTMLHeadingElement>(null),readAbort=useRef<AbortController|null>(null);
 useEffect(()=>{alive.current=true;heading.current?.focus();return()=>{alive.current=false;readAbort.current?.abort();};},[]);
 function begin(){if(pending.current)return false;pending.current=true;setBusy(true);setError('');setInfo('');return true;}
 function end(){pending.current=false;if(alive.current)setBusy(false);}
 function forbidden(error:unknown){if(error instanceof ApiError&&error.status===403){onForbidden();return true;}return false;}
 async function submit(event:FormEvent<HTMLFormElement>){
  event.preventDefault();
  if(pending.current||phase!=='editing'||locked)return;
  const invalid:typeof fields={};
  if(!Number.isInteger(Number(number))||Number(number)<1||Number(number)>2147483647)invalid.number='Số đợt phải là số nguyên từ 1 đến 2147483647.';
  if(!name.trim()||name.trim().length>512)invalid.name='Tên đợt cần từ 1 đến 512 ký tự.';
  setFields(invalid);if(Object.keys(invalid).length||!begin())return;
  let updated:RoundView|null=null;
  try{
   updated=await updateRound(client,base.round.id,{number:Number(number),name},base.etag);
   if(!alive.current)return;
   setSaved(updated);setPhase('saved');
   await onSaved(updated,false);
  }catch(error){
   if(!alive.current||forbidden(error))return;
   if(updated){setError(savedError);return;}
   if(error instanceof ApiError&&error.status<500){
    if(error.code==='VERSION_CONFLICT'||error.code==='PRECONDITION_REQUIRED'){
     setPhase('conflict');setLatest(null);
     setError(error.code==='VERSION_CONFLICT'?'Dữ liệu đã được người khác thay đổi. Đọc bản mới để đối chiếu trước khi lưu.':'Chưa xác định được phiên dữ liệu. Hãy đọc bản mới trước khi lưu.');
    }else if(error.code==='ROUND_NOT_EDITABLE'){
     setLocked(true);setPhase('conflict');setLatest(null);setError('Đợt không còn ở trạng thái Nháp. Hãy đọc bản mới để kiểm tra.');
    }else if(error.code==='ROUND_EXISTS'){
     setError('Số đợt đã tồn tại trong học kỳ. Hãy chọn số đợt khác.');setFields({number:'Số đợt đã tồn tại trong học kỳ.'});
    }else if(error.code==='VALIDATION_ERROR'){
     setError('Thông tin chưa hợp lệ. Hãy kiểm tra các trường được đánh dấu.');
     const next:typeof fields={};
     for(const field of error.fields??[]){if(field==='name')next.name='Tên đợt chưa hợp lệ.';if(field==='number')next.number='Số đợt chưa hợp lệ.';}
     setFields(next);
    }else setError(errorMessage(error));
   }else{setPhase('unknown');setLatest(null);setError(unknownError);}
  }finally{end();}
 }
 async function readLatest(){
  if(!begin())return;
  const abort=new AbortController();readAbort.current=abort;
  try{
   const view=await readRound(client,base.round.id,abort.signal);
   if(!alive.current||abort.signal.aborted)return;
   setLatest(view);setLocked(view.round.status!=='draft');
  }catch(error){if(alive.current&&!abort.signal.aborted&&!forbidden(error))setError(errorMessage(error));}finally{end();}
 }
 function adoptLatest(replace:boolean){
  if(!latest||latest.round.status!=='draft'||!isRoundEtag(latest.etag))return;
  setBase(latest);setPhase('editing');setLocked(false);setSaved(null);setError('');setFields({});
  if(replace){setNumber(String(latest.round.number));setName(latest.round.name);}
  setLatest(null);setInfo('Đã dùng bản mới. Kiểm tra nội dung rồi bấm Lưu thay đổi.');
 }
 async function confirm(view:RoundView,recovered:boolean){
  if(!begin())return;
  try{await onSaved(view,recovered);}catch(error){if(alive.current&&!forbidden(error))setError(recovered?'Chưa tải lại đầy đủ bản hiện tại. Hãy kiểm tra lại.':savedError);}finally{end();}
 }
 return <section className={styles.formArea} aria-labelledby="edit-round-title">
  <h2 id="edit-round-title" ref={heading} tabIndex={-1}>Sửa đợt xét</h2>
  <p>Học kỳ {base.round.semester.semesterOrder} · {base.round.semester.academicYear}. Chỉ sửa số và tên đợt; học kỳ giữ nguyên.</p>
  <form aria-label="Sửa đợt xét KKHT" onSubmit={submit} className={styles.form}>
   <fieldset className={styles.information} disabled={busy||locked||phase==='unknown'||phase==='saved'}>
    <legend>Thông tin đợt</legend>
    <label htmlFor="edit-round-number">Số đợt<input id="edit-round-number" type="number" min="1" max="2147483647" step="1" required value={number} onChange={event=>{setNumber(event.target.value);setFields(current=>({...current,number:undefined}));}} aria-invalid={!!fields.number} aria-describedby={fields.number?'edit-number-error':undefined}/></label>
    {fields.number&&<p id="edit-number-error" className={styles.fieldError}>{fields.number}</p>}
    <label htmlFor="edit-round-name">Tên đợt<input id="edit-round-name" required value={name} onChange={event=>{setName(event.target.value);setFields(current=>({...current,name:undefined}));}} aria-invalid={!!fields.name} aria-describedby={fields.name?'edit-name-error':'edit-name-hint'}/></label>
    <p id="edit-name-hint" className={styles.hint}>Tối đa 512 ký tự sau khi bỏ khoảng trắng đầu và cuối.</p>
    {fields.name&&<p id="edit-name-error" className={styles.fieldError}>{fields.name}</p>}
   </fieldset>
   {busy&&<p role="status">{phase==='editing'?'Đang lưu thay đổi…':'Đang đọc kết quả hiện tại…'}</p>}
   {error&&<Alert>{error}</Alert>}{info&&<p role="status">{info}</p>}
   {(phase==='conflict'||phase==='unknown')&&<section className={styles.recovery} aria-label="Đối chiếu bản hiện tại">
    <Button variant="secondary" disabled={busy} onClick={()=>void readLatest()}>{phase==='unknown'?'Kiểm tra kết quả đã lưu':'Đọc bản mới'}</Button>
    {latest&&<>
     <dl className={styles.readOnly}><div><dt>Số đợt đang lưu</dt><dd>{latest.round.number}</dd></div><div><dt>Tên đợt đang lưu</dt><dd>{latest.round.name}</dd></div><div><dt>Trạng thái hiện tại</dt><dd>{latest.round.status==='draft'?'Nháp':'Không còn ở trạng thái Nháp'}</dd></div></dl>
     {latest.round.status==='draft'&&isRoundEtag(latest.etag)&&<>
      <p>Lưu nội dung đang nhập sẽ thay thế số và tên đang lưu. Hãy đối chiếu trước khi tiếp tục.</p>
      <Button disabled={busy} onClick={()=>adoptLatest(false)}>Giữ nội dung nhập và dùng bản mới</Button>
      <Button variant="secondary" disabled={busy} onClick={()=>adoptLatest(true)}>Thay nội dung nhập bằng bản mới</Button>
     </>}
     <Button variant="secondary" disabled={busy} onClick={()=>void confirm(latest,true)}>Xem đợt hiện tại</Button>
    </>}
   </section>}
   <div className={styles.actions}>
    {phase!=='saved'&&phase!=='unknown'&&<Button type="submit" disabled={busy||locked||phase!=='editing'||!isRoundEtag(base.etag)}>{busy?'Đang lưu…':'Lưu thay đổi'}</Button>}
    {saved&&<Button disabled={busy} onClick={()=>void confirm(saved,false)}>Đọc lại kết quả</Button>}
    <Button variant="secondary" disabled={busy} onClick={onCancel}>Quay lại chi tiết</Button>
   </div>
  </form>
 </section>;
}
