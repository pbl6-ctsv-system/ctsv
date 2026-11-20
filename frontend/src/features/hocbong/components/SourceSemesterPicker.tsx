import {useEffect,useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {errorMessage} from '../../../shared/api/error-message';
import {Alert} from '../../../shared/ui/Alert';
import {Button} from '../../../shared/ui/Button';
import {listSourceSemesters} from '../api/rounds';
import type {SourceSemester,SourceSemesterPage} from '../types/round';
import styles from './CreateRoundForm.module.css';

const reasons:Record<string,string>={
 SOURCE_NOT_PRESENT:'Học kỳ không còn hiện diện ở nguồn Đào tạo.',
 SUMMER_MERGED_INTO_SEMESTER_2:'Học kỳ hè không tạo đợt riêng; chọn học kỳ 2.',
 SOURCE_DATES_UNVERIFIED:'Ngày học kỳ chưa đủ hoặc chưa được xác minh.',
 SEMESTER_MAPPING_REQUIRED:'Chưa xác định được năm học hoặc học kỳ học bổng.',
 SEMESTER_MAPPING_CONFLICT:'Thông tin học kỳ đang có mâu thuẫn ánh xạ.',
};
export const sourceReason=(source:SourceSemester)=>reasons[source.unavailableReason??'']??'Học kỳ này chưa đủ điều kiện để tạo đợt.';
type Props={client:AuthClient;value:SourceSemester|null;onChange:(source:SourceSemester|null)=>void;
 disabled:boolean;refresh:number;error?:string;onBusy:(busy:boolean)=>void;onForbidden:()=>void};
type State={key:string;data:SourceSemesterPage|null;error:string};

export function SourceSemesterPicker({client,value,onChange,disabled,refresh,error,onBusy,onForbidden}:Props){
 const [input,setInput]=useState(''),[query,setQuery]=useState(''),[page,setPage]=useState(0),[revision,setRevision]=useState(0);
 const [state,setState]=useState<State|null>(null);
 const key=JSON.stringify([query,page,revision,refresh]);
 const current=state?.key===key?state:null;
 const selectedId=value?.sourceSemesterId;
 useEffect(()=>{
  const abort=new AbortController();
  onBusy(true);setState(null);
  void listSourceSemesters(client,{q:query,page,size:20},abort.signal).then(data=>{
   if(abort.signal.aborted)return;
   setState({key,data,error:''});
  }).catch(error=>{
   if(abort.signal.aborted)return;
   if(error instanceof ApiError&&error.status===403)onForbidden();
   setState({key,data:null,error:error instanceof DOMException&&error.name==='TimeoutError'?'Tải học kỳ quá lâu. Vui lòng thử lại.':errorMessage(error)});
  }).finally(()=>{if(!abort.signal.aborted)onBusy(false);});
  return()=>abort.abort();
 },[client,query,page,revision,refresh,key,onBusy,onForbidden]);
 function search(){setQuery(input.trim());setPage(0);setRevision(value=>value+1);}
 const data=current?.data;
 const pages=data?Math.max(1,Math.ceil(data.totalElements/data.size)):1;
 return <fieldset className={styles.picker} disabled={disabled} aria-describedby={error?'source-semester-error':undefined}>
  <legend>Học kỳ xét</legend>
  <p>Chọn học kỳ có sẵn từ Đào tạo. Học kỳ học bổng được liên kết khi lưu đợt.</p>
  <div className={styles.search}>
   <label htmlFor="source-query">Tìm học kỳ<input id="source-query" value={input} maxLength={256} placeholder="Mã hoặc tên học kỳ" onChange={event=>setInput(event.target.value)} onKeyDown={event=>{if(event.key==='Enter'){event.preventDefault();search();}}}/></label>
   <Button variant="secondary" onClick={search}>Tìm học kỳ</Button>
   <Button variant="secondary" disabled={!current} onClick={()=>setRevision(value=>value+1)}>Đọc lại học kỳ</Button>
  </div>
  {!current&&<p role="status">Đang tải học kỳ…</p>}
  {current?.error&&<Alert>{current.error}</Alert>}
  {data&&<>
   {data.items.length===0?<p>Không có học kỳ phù hợp.</p>:<div className={styles.sourceList}>
    {data.items.map(item=><div key={item.sourceSemesterId} className={styles.sourceRow}>
     <label className={styles.sourceChoice}>
      <input type="radio" name="scholarship-source" value={item.sourceSemesterId} checked={selectedId===item.sourceSemesterId} disabled={!item.selectable} onChange={()=>onChange(item)}/>
      <span>{item.code} · {item.name??'Chưa có tên'}</span>
     </label>
     <p className={styles.hint}>{item.selectable?`${item.academicYear} · Học kỳ ${item.semesterOrder}`:reasons[item.unavailableReason??'']??'Học kỳ này chưa đủ điều kiện để tạo đợt.'}</p>
    </div>)}
   </div>}
   <div className={styles.sourcePaging}>
    <Button variant="secondary" disabled={data.page===0} onClick={()=>setPage(value=>value-1)}>Trang học kỳ trước</Button>
    <span>Trang học kỳ {data.page+1} / {pages}</span>
    <Button variant="secondary" disabled={data.page+1>=pages||(data.page+1)*data.size>100000} onClick={()=>setPage(value=>value+1)}>Trang học kỳ sau</Button>
   </div>
  </>}
  <p className={styles.selected} aria-live="polite">{value?`Học kỳ đã chọn: ${value.code} · ${value.name??'Chưa có tên'}`:'Chưa chọn học kỳ.'}</p>
  {error&&<p id="source-semester-error" className={styles.fieldError}>{error}</p>}
 </fieldset>;
}
