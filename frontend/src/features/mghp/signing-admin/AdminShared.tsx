import {useState} from 'react';
import {dateTime} from '../presentation.mjs';
import type {History} from './types';
import styles from '../signing/SigningWorkspace.module.css';
import css from './SigningAdmin.module.css';
export function AdminHistory({items}:{items:History[]}){return <>{items.length?items.map(h=><div className={css.history} key={h.id}><div>{h.text}</div>{h.reference&&<p>{h.reference}</p>}<small>{dateTime(h.at)} · giờ Việt Nam{h.actorId?' · Người ghi nhận #'+h.actorId:' · Hệ thống'}</small></div>):<p className={styles.muted}>Chưa có lịch sử.</p>}</>;}
export function NoteForm({busy,onSave}:{busy:boolean;onSave:(text:string,reference:string)=>Promise<boolean>}){
 const [text,setText]=useState(''),[reference,setReference]=useState('');
 return <form className={css.noteForm} onSubmit={e=>{e.preventDefault();void onSave(text.trim(),reference.trim()).then(saved=>{if(saved){setText('');setReference('');}});}}><label>Ghi chú xử lý<textarea value={text} maxLength={2000} onChange={e=>setText(e.target.value)} required disabled={busy}/></label><label>Tham chiếu trao đổi (không bắt buộc)<input value={reference} maxLength={200} onChange={e=>setReference(e.target.value)} disabled={busy}/></label><button disabled={busy||!text.trim()}>Lưu ghi chú</button></form>;
}
