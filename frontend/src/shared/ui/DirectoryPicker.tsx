import {useEffect,useRef,useState} from 'react';
import type {AuthClient} from '../types/api';
import {errorMessage} from '../api/error-message';
import styles from './DirectoryPicker.module.css';

type Choice=Record<string,any>;
/** Native select + server search/page. A typed choice, never an arbitrary text ID. */
export function DirectoryPicker({client,path,label,value,onSelect,format,required=false,disabled=false,idField='id',selectedLabel,paged=true,getKey,itemsKey='items'}:{client:AuthClient;path:string;label:string;value:string;onSelect:(item:Choice|null)=>void;format?:(item:Choice)=>string;required?:boolean;disabled?:boolean;idField?:string;selectedLabel?:string;paged?:boolean;getKey?:(item:Choice)=>string;itemsKey?:string}){
 const [query,setQuery]=useState(''),[items,setItems]=useState<Choice[]>([]),[page,setPage]=useState(0),[total,setTotal]=useState(0),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const current=useRef(0),cache=useRef(new Map<string,Choice>());
 const title=(item:Choice)=>format?.(item)||[item.name,item.code].filter(Boolean).join(' · ')||'Chưa có tên';
 const key=(item:Choice)=>getKey?.(item)??String(item[idField]);
 async function load(q:string,p:number){
  const ticket=++current.current;setBusy(true);setError('');
  try{const separator=path.includes('?')?'&':'?';const response=await client.request(path+(paged?separator+new URLSearchParams({q,page:String(p),size:'20'}):''));const data=await response.json();if(ticket!==current.current)return;
   const rows=Array.isArray(data)?data:data[itemsKey];if(!Array.isArray(rows))throw new Error('Danh mục chưa trả về dữ liệu hợp lệ.');setItems(rows);rows.forEach((item:Choice)=>cache.current.set(key(item),item));setTotal(Array.isArray(data)?data.length:data.totalElements??rows.length);setPage(p);
  }catch(e){if(ticket===current.current){setItems([]);setError(errorMessage(e));}}finally{if(ticket===current.current)setBusy(false);}
 }
 useEffect(()=>{cache.current.clear();setItems([]);setQuery('');void load('',0);return()=>{current.current++;};},[client,path]);
 const selected=cache.current.get(value);
 return <div className={styles.picker} aria-busy={busy}>
  <label>{label}<select aria-label={label} required={required} disabled={disabled||busy} value={value} onChange={e=>onSelect(cache.current.get(e.currentTarget.value)??null)}>
   <option value="">{busy?'Đang tải…':'Chọn '+label.toLowerCase()}</option>
   {value&&!items.some(item=>key(item)===value)&&<option value={value}>{selected?title(selected):selectedLabel||'Lựa chọn đã lưu · cần đối chiếu tên'}</option>}
   {items.map(item=><option key={key(item)} value={key(item)}>{title(item)}</option>)}
  </select></label>
  {!disabled&&paged&&<details><summary>Tìm trong danh mục{total>20?' · '+total+' mục':''}</summary><div className={styles.search}><label>Tìm {label.toLowerCase()}<input aria-label={'Tìm '+label.toLowerCase()} maxLength={128} value={query} onChange={e=>setQuery(e.currentTarget.value)} onKeyDown={e=>{if(e.key==='Enter'){e.preventDefault();void load(query,0);}}}/></label><button type="button" className="secondary" disabled={busy} onClick={()=>void load(query,0)}>Tìm</button></div>
   <div className={styles.paging}><button type="button" className="secondary" disabled={busy||page===0} aria-label={'Trang trước '+label.toLowerCase()} onClick={()=>void load(query,page-1)}>‹</button><small>{total} kết quả · trang {page+1}</small><button type="button" className="secondary" disabled={busy||(page+1)*20>=total} aria-label={'Trang sau '+label.toLowerCase()} onClick={()=>void load(query,page+1)}>›</button></div></details>}
  {error&&<p role="alert">{error}</p>}{!busy&&!error&&!items.length&&<small>Không tìm thấy mục phù hợp.</small>}
 </div>;
}
