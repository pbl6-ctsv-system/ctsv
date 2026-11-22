import {useId,useState} from 'react';
import {rankYearOptions} from '../model/draft.mjs';
import styles from '../pages/Assignments.module.css';

type Props={label:string;value:string;onChange:(value:string)=>void;known?:string[];required?:boolean;disabled?:boolean};
export function YearCombobox({label,value,onChange,known=[],required=false,disabled=false}:Props){
 const id=useId(),listId=id+'-list';
 const[open,setOpen]=useState(false),[active,setActive]=useState(-1);
 const options=rankYearOptions(value,known);
 function choose(option:string){onChange(option);setOpen(false);setActive(-1);}
 return <div className={styles.yearField} onBlur={event=>{if(!event.currentTarget.contains(event.relatedTarget as Node))setOpen(false);}}>
  <label htmlFor={id}>{label}</label>
  <div className={styles.yearControl}>
   <input id={id} role="combobox" aria-autocomplete="list" aria-expanded={open} aria-controls={listId} aria-activedescendant={open&&active>=0?`${listId}-${active}`:undefined}
    value={value} maxLength={20} required={required} disabled={disabled} placeholder="Nhập hoặc chọn năm học" autoComplete="off"
    onFocus={()=>setOpen(true)} onChange={event=>{onChange(event.target.value);setOpen(true);setActive(-1);}}
    onKeyDown={event=>{if(event.key==='ArrowDown'||event.key==='ArrowUp'){event.preventDefault();setOpen(true);setActive(index=>event.key==='ArrowDown'?Math.min(index+1,options.length-1):Math.max(index-1,0));}
     else if(event.key==='Escape'){event.preventDefault();event.stopPropagation();setOpen(false);}
     else if(event.key==='Enter'&&open){event.preventDefault();if(active>=0)choose(options[active]);else setOpen(false);}}}/>
   <button type="button" className={styles.yearToggle} disabled={disabled} aria-label={'Gợi ý '+label.toLocaleLowerCase('vi')} aria-expanded={open} aria-controls={listId}
    onMouseDown={event=>event.preventDefault()} onClick={()=>{setOpen(v=>!v);setActive(-1);}}>▾</button>
  </div>
  {open&&!disabled&&<div className={styles.yearMenu}><small>Gợi ý năm học · Có thể nhập năm khác</small>
   <ul id={listId} role="listbox" aria-label={'Các gợi ý '+label.toLocaleLowerCase('vi')}>
    {options.map((option,index)=><li key={option} id={`${listId}-${index}`} role="option" aria-selected={active>=0?active===index:option===value}
     onMouseDown={event=>event.preventDefault()} onMouseEnter={()=>setActive(index)} onClick={()=>choose(option)}>{option}</li>)}
   </ul>
  </div>}
 </div>;
}
