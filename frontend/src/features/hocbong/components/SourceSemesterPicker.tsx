import {useEffect,useId,useRef,useState,type KeyboardEvent} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {errorMessage} from '../../../shared/api/error-message';
import {Alert} from '../../../shared/ui/Alert';
import {Button} from '../../../shared/ui/Button';
import {listSourceSemesters} from '../api/rounds';
import type {SourceSemester,SourceSemesterPage} from '../types/round';
import styles from './CreateRoundForm.module.css';
import combo from './SourceSemesterPicker.module.css';

const reasons:Record<string,string>={
 SOURCE_NOT_PRESENT:'Học kỳ không còn hiện diện ở nguồn Đào tạo.',
 SUMMER_MERGED_INTO_SEMESTER_2:'Học kỳ hè không tạo đợt riêng; chọn học kỳ 2.',
 SOURCE_DATES_UNVERIFIED:'Chưa có ngày bắt đầu hoặc khoảng ngày học kỳ không hợp lệ.',
 SEMESTER_MAPPING_REQUIRED:'Chưa xác định được năm học hoặc học kỳ học bổng.',
 SEMESTER_MAPPING_CONFLICT:'Thông tin học kỳ đang có mâu thuẫn ánh xạ.',
};
export const sourceReason=(source:SourceSemester)=>reasons[source.unavailableReason??'']??'Học kỳ này chưa đủ điều kiện để tạo đợt.';
type Props={client:AuthClient;value:SourceSemester|null;onChange:(source:SourceSemester|null)=>void;
 disabled:boolean;refresh:number;error?:string;onBusy:(busy:boolean)=>void;onForbidden:()=>void};
type State={key:string;data:SourceSemesterPage|null;error:string};

export function SourceSemesterPicker({client,value,onChange,disabled,refresh,error,onBusy,onForbidden}:Props){
 const [input,setInput]=useState(''),[open,setOpen]=useState(false),[page,setPage]=useState(0),[revision,setRevision]=useState(0);
 const [activeId,setActiveId]=useState<string|null>(null);
 const inputRef=useRef<HTMLInputElement>(null),listRef=useRef<HTMLDivElement>(null);
 const id=useId(),listId=id+'-options',hintId=id+'-hint',errorId=id+'-error';
 const [state,setState]=useState<State|null>(null);
 const query=input.trim();
 const key=JSON.stringify([query,page,revision,refresh]);
 const current=state?.key===key?state:null;
 const selectedId=value?.sourceSemesterId;
 useEffect(()=>{
  if(!open||disabled)return;
  const abort=new AbortController();
  onBusy(true);setState(null);
  const timer=window.setTimeout(()=>{void listSourceSemesters(client,{q:query,page,size:20},abort.signal).then(data=>{
   if(abort.signal.aborted)return;
   setState({key,data,error:''});
  }).catch(error=>{
   if(abort.signal.aborted)return;
   if(error instanceof ApiError&&error.status===403)onForbidden();
   setState({key,data:null,error:error instanceof DOMException&&error.name==='TimeoutError'?'Tải học kỳ quá lâu. Vui lòng thử lại.':errorMessage(error)});
  }).finally(()=>{if(!abort.signal.aborted)onBusy(false);});},query?300:0);
  return()=>{window.clearTimeout(timer);abort.abort();onBusy(false);};
 },[client,query,page,revision,refresh,key,onBusy,onForbidden,open,disabled]);
 const data=current?.data;
 const pages=data?Math.max(1,Math.ceil(data.totalElements/data.size)):1;
 const active=data?.items.find(item=>item.sourceSemesterId===activeId&&item.selectable);
 const optionId=(sourceId:string)=>listId+'-'+sourceId;
 useEffect(()=>{
  if(!open&&document.activeElement===inputRef.current)inputRef.current?.select();
 },[open,selectedId]);
 useEffect(()=>{
  if(open&&activeId)listRef.current?.querySelector('[data-active="true"]')?.scrollIntoView({block:'nearest'});
 },[open,activeId]);
 function show(){if(!open){setInput('');setPage(0);setActiveId(null);setOpen(true);}}
 function select(item:SourceSemester){
  if(disabled||!item.selectable)return;
  inputRef.current?.focus();setOpen(false);setInput('');setActiveId(null);onChange(item);
 }
 function navigate(event:KeyboardEvent<HTMLInputElement>){
  if(event.nativeEvent.isComposing)return;
  if(event.key==='Escape'){if(open){event.preventDefault();event.stopPropagation();setOpen(false);setActiveId(null);}return;}
  if(event.key==='Enter'){event.preventDefault();if(open&&active)select(active);return;}
  if(event.key!=='ArrowDown'&&event.key!=='ArrowUp')return;
  event.preventDefault();if(!open){show();return;}
  const items=data?.items.filter(item=>item.selectable)??[];
  if(!items.length)return;
  const index=items.findIndex(item=>item.sourceSemesterId===activeId);
  const next=index<0?(event.key==='ArrowDown'?0:items.length-1):(index+(event.key==='ArrowDown'?1:-1)+items.length)%items.length;
  setActiveId(items[next].sourceSemesterId);
 }
 return <fieldset className={styles.picker} disabled={disabled} aria-describedby={error?errorId:undefined}>
  <legend>Học kỳ xét</legend>
  <p>Chọn học kỳ có sẵn từ Đào tạo. Học kỳ học bổng được liên kết khi lưu đợt.</p>
  <div className={combo.combobox} onBlur={event=>{if(!event.currentTarget.contains(event.relatedTarget)){setOpen(false);setActiveId(null);}}}>
   <label htmlFor={id}>Tìm học kỳ</label>
   <div className={combo.control}>
   <input ref={inputRef} id={id} role="combobox" aria-autocomplete="list" aria-expanded={open&&!disabled} aria-controls={open&&!disabled?listId:undefined}
    aria-activedescendant={open&&active?optionId(active.sourceSemesterId):undefined} aria-describedby={hintId} aria-invalid={!!error}
    autoComplete="off" value={open?input:value?`${value.code} · ${value.name??'Chưa có tên'}`:''} maxLength={256} placeholder="Nhập mã hoặc tên học kỳ"
    onFocus={show} onClick={show} onChange={event=>{setInput(event.target.value);setPage(0);setActiveId(null);setOpen(true);}} onKeyDown={navigate}/>
   {open&&!disabled&&<div className={combo.dropdown}>
    {!current&&<p role="status" className={combo.message}>Đang tìm học kỳ…</p>}
    {current?.error&&<div className={combo.message}><Alert>{current.error}</Alert><Button variant="secondary" onClick={()=>{inputRef.current?.focus();setActiveId(null);setRevision(value=>value+1);}}>Thử lại tìm học kỳ</Button></div>}
    {data&&<p role="status" className={combo.message}>{data.totalElements===0?'Không có học kỳ phù hợp.':`${data.totalElements} học kỳ phù hợp`}</p>}
    <div ref={listRef} id={listId} role="listbox" aria-label="Học kỳ phù hợp" aria-busy={!current} className={combo.options}>
     {data?.items.map(item=><div id={optionId(item.sourceSemesterId)} key={item.sourceSemesterId} role="option"
      aria-selected={selectedId===item.sourceSemesterId} aria-disabled={!item.selectable} data-active={active?.sourceSemesterId===item.sourceSemesterId}
      className={combo.option} onMouseDown={event=>event.preventDefault()} onClick={()=>select(item)}>
      <span className={combo.optionName}>{item.code} · {item.name??'Chưa có tên'}</span>
      <span className={styles.hint}>{item.selectable?`${item.academicYear} · Học kỳ ${item.semesterOrder}`:sourceReason(item)}</span>
     </div>)}
    </div>
    {data&&pages>1&&<div className={combo.paging}>
     <Button variant="secondary" disabled={data.page===0} onClick={()=>{inputRef.current?.focus();setActiveId(null);setPage(value=>value-1);}}>Trang học kỳ trước</Button>
     <span>Trang học kỳ {data.page+1} / {pages}</span>
     <Button variant="secondary" disabled={data.page+1>=pages||(data.page+1)*data.size>100000} onClick={()=>{inputRef.current?.focus();setActiveId(null);setPage(value=>value+1);}}>Trang học kỳ sau</Button>
    </div>}
   </div>}
   </div>
   <p id={hintId} className={styles.hint}>Gõ để tìm theo mã hoặc tên, rồi chọn học kỳ trong danh sách.</p>
   </div>
  <p className={styles.selected} aria-live="polite">{value?`Học kỳ đã chọn: ${value.code} · ${value.name??'Chưa có tên'}`:'Chưa chọn học kỳ.'}</p>
  {error&&<p id={errorId} className={styles.fieldError}>{error}</p>}
 </fieldset>;
}
