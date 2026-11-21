import {useEffect,useId,useRef,useState,type CSSProperties} from 'react';
import {createPortal} from 'react-dom';
import type {Page} from '../types/assignments';
import {assignmentError} from '../api/errors';
import {Button} from '../../../shared/ui/Button';
import styles from '../pages/Assignments.module.css';

// Fetch only the open picker. Query/page changes cancel older responses.
export function AsyncChoice<T>({label,value,load,itemKey,itemLabel,unavailable,disabled=false,onChoose,onFailure}:{
 label:string;value:string;load:(q:string,page:number,signal:AbortSignal)=>Promise<Page<T>>;
 itemKey:(item:T)=>string;itemLabel:(item:T)=>string;unavailable?:(item:T)=>string|null;
 disabled?:boolean;onChoose:(item:T)=>void;onFailure:(error:unknown)=>void;
}){
 const id=useId(),control=useRef<HTMLDivElement>(null),panel=useRef<HTMLDivElement>(null),input=useRef<HTMLInputElement>(null);
 const [open,setOpen]=useState(false),[query,setQuery]=useState(''),[page,setPage]=useState(0),[active,setActive]=useState(-1);
 const [data,setData]=useState<Page<T>|null>(null),[loading,setLoading]=useState(false),[error,setError]=useState(''),[retry,setRetry]=useState(0);
 const [position,setPosition]=useState<CSSProperties>({});
 useEffect(()=>{if(disabled)setOpen(false);},[disabled]);
 useEffect(()=>{
  if(!open)return;
  const place=()=>{const r=control.current?.getBoundingClientRect();if(!r)return;
   const width=Math.min(Math.max(r.width,280),window.innerWidth-24),below=window.innerHeight-r.bottom-12,above=r.top-12;
   const down=below>=220||below>=above;
   setPosition({position:'fixed',width,left:Math.max(12,Math.min(r.left,window.innerWidth-width-12)),maxHeight:Math.max(120,down?below:above),...(down?{top:r.bottom+4}:{bottom:window.innerHeight-r.top+4})});};
  place();const outside=(e:PointerEvent)=>{if(!control.current?.contains(e.target as Node)&&!panel.current?.contains(e.target as Node))setOpen(false);};
  document.addEventListener('pointerdown',outside);window.addEventListener('resize',place);window.addEventListener('scroll',place,true);
  return()=>{document.removeEventListener('pointerdown',outside);window.removeEventListener('resize',place);window.removeEventListener('scroll',place,true);};
 },[open]);
 useEffect(()=>{
  if(!open)return;
  const controller=new AbortController();setLoading(true);setData(null);setError('');setActive(-1);
  const timer=window.setTimeout(()=>{void load(query,page,controller.signal).then(result=>{
   if(!controller.signal.aborted){setData(result);setLoading(false);}
  }).catch(e=>{if(!controller.signal.aborted){setError(assignmentError(e));setLoading(false);onFailure(e);}});},180);
  return()=>{window.clearTimeout(timer);controller.abort();};
 },[open,query,page,load,retry,onFailure]);
 useEffect(()=>{if(active>=0)document.getElementById(id+'-'+active)?.scrollIntoView({block:'nearest'});},[active,id]);
 function show(){if(!disabled){setQuery('');setPage(0);setOpen(true);}}
 function choose(item:T){if(unavailable?.(item))return;onChoose(item);input.current?.focus();setOpen(false);}
 function leave(e:React.FocusEvent){if(!control.current?.contains(e.relatedTarget as Node)&&!panel.current?.contains(e.relatedTarget as Node))setOpen(false);}
 return <div className={styles.asyncChoice} onBlur={leave}>
  <label htmlFor={id}>{label}</label>
  <div className={styles.choiceControl} ref={control}>
   <input ref={input} id={id} role="combobox" autoComplete="off" disabled={disabled} aria-expanded={open} aria-controls={id+'-list'} aria-autocomplete="list" aria-activedescendant={open&&active>=0?id+'-'+active:undefined}
    placeholder="Tìm theo tên hoặc mã" value={open?query:value} onFocus={show} onChange={e=>{setQuery(e.target.value);setPage(0);setOpen(true);setData(null);setActive(-1);}}
    onKeyDown={e=>{
     if(e.key==='Escape'){e.preventDefault();setOpen(false);return;}
     if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();if(!open){show();return;}const items=data?.items??[];if(items.length)setActive(i=>e.key==='ArrowDown'?Math.min(i+1,items.length-1):Math.max(0,i-1));}
     if(e.key==='Enter'){e.preventDefault();if(!open)show();else if(active>=0&&data?.items[active])choose(data.items[active]);}
    }}/>
   <button type="button" disabled={disabled} aria-label={'Mở danh sách '+label} aria-expanded={open} onMouseDown={e=>e.preventDefault()} onClick={()=>{if(open)setOpen(false);else{input.current?.focus();show();}}}>▾</button>
  </div>
  {open&&createPortal(<div ref={panel} className={styles.choicePanel} style={position} onBlur={leave} onKeyDown={e=>{if(e.key==='Escape'){e.preventDefault();input.current?.focus();setOpen(false);}}}>
   <small>Chọn một kết quả để xác nhận.</small>
   {loading&&<p role="status">Đang tải…</p>}
   {error&&<div role="alert"><p>{error}</p><Button variant="secondary" onClick={()=>setRetry(v=>v+1)}>Thử lại</Button></div>}
   <ul id={id+'-list'} role="listbox" aria-label={label} aria-busy={loading}>
    {data?.items.map((item,index)=>{const reason=unavailable?.(item);return <li id={id+'-'+index} key={itemKey(item)} role="option" aria-selected={active===index} aria-disabled={!!reason}
     onMouseDown={e=>e.preventDefault()} onClick={()=>choose(item)} onMouseMove={()=>setActive(index)}>{itemLabel(item)}{reason&&<small>{reason}</small>}</li>;})}
   </ul>
   {data&&!data.items.length&&<p>Không có kết quả phù hợp.</p>}
   {data&&<div className={styles.actions}><Button variant="secondary" disabled={page===0} onClick={()=>{setPage(p=>p-1);setData(null);}}>Trước</Button><span>Trang {page+1} · {data.totalElements} kết quả</span><Button variant="secondary" disabled={(page+1)*data.size>=data.totalElements} onClick={()=>{setPage(p=>p+1);setData(null);}}>Sau</Button></div>}
  </div>,document.body)}
 </div>;
}
