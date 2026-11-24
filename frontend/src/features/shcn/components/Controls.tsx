import {useEffect,useRef,type ReactNode} from 'react';
import {Button} from '../../../shared/ui/Button';
import styles from '../pages/Assignments.module.css';
export function Modal({title,children,close,busy=false}:{title:string;children:ReactNode;close:()=>void;busy?:boolean}){
 const ref=useRef<HTMLDialogElement>(null);
 useEffect(()=>{const old=document.activeElement as HTMLElement;ref.current?.showModal();return()=>{ref.current?.close();old?.focus();};},[]);
 return <dialog ref={ref} className={styles.dialog} aria-label={title} onCancel={e=>{e.preventDefault();if(!busy)close();}}><header><h2>{title}</h2><Button variant="secondary" disabled={busy} onClick={close} aria-label="Đóng hộp thoại">Đóng</Button></header>{children}</dialog>;
}
export function Pager({page,total,change}:{page:number;total:number;change:(p:number)=>void}){return <div className={styles.actions}><Button variant="secondary" disabled={!page} onClick={()=>change(page-1)}>Trước</Button><span>Trang {page+1} · {total} kết quả</span><Button variant="secondary" disabled={(page+1)*25>=total||((page+1)*25)>100000} onClick={()=>change(page+1)}>Sau</Button></div>;}
export function TimeFields({from,until,change,disabled=false}:{from:string;until:string|null;change:(k:'hieuLucTu'|'hieuLucDen',v:string|null)=>void;disabled?:boolean}){
 return <div className={styles.timeGrid}><label>Bắt đầu đề nghị hiệu lực (giờ Việt Nam)<input required type="datetime-local" step="1" disabled={disabled} value={vietnamInput(from)} onChange={e=>change('hieuLucTu',instant(e.target.value))}/></label><label>Kết thúc đề nghị hiệu lực (không bao gồm)<input type="datetime-local" step="1" disabled={disabled} value={vietnamInput(until)} onChange={e=>change('hieuLucDen',instant(e.target.value))}/><small>Để trống nếu chưa đặt mốc kết thúc.</small></label></div>;
}
import {vietnamInput,instant} from '../model/draft.mjs';
export const formatTime=(v:string|null)=>v?new Intl.DateTimeFormat('vi-VN',{dateStyle:'short',timeStyle:'short',timeZone:'Asia/Ho_Chi_Minh'}).format(new Date(v)):'Chưa đặt';
