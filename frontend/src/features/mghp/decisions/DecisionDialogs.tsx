import {useEffect,useId,useRef} from 'react';
import type {ReactNode,FormEvent} from 'react';
import {shortDate,rateLabel} from './model.mjs';
import type {Candidate,Line,MeetingForm} from './types';
import styles from './DecisionWorkspace.module.css';

export function DecisionDialog({title,children,onClose,busy=false}:{title:string;children:ReactNode;onClose:()=>void;busy?:boolean}){
 const ref=useRef<HTMLDialogElement>(null),id=useId();
 useEffect(()=>{const d=ref.current;d?.showModal();return()=>{d?.close();};},[]);
 return <dialog ref={ref} className={styles.dialog} aria-labelledby={id} onCancel={e=>{e.preventDefault();if(!busy)onClose();}} onClick={e=>{if(e.target===e.currentTarget&&!busy){const r=e.currentTarget.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)onClose();}}}>
  <h2 id={id}>{title}</h2>{children}
 </dialog>;
}
export function MeetingFields({form,onChange,busy}:{form:MeetingForm;onChange:(next:MeetingForm)=>void;busy:boolean}){
 const update=(key:keyof MeetingForm,value:string)=>onChange({...form,[key]:value});
 return <div className={styles.fieldGrid}>
  <label>Mã biên bản<input aria-label="Mã biên bản" required maxLength={128} value={form.minutesCode} disabled={busy} onChange={e=>update('minutesCode',e.target.value)}/></label>
  <label>Thời gian họp thực tế<input aria-label="Thời gian họp thực tế" type="datetime-local" required value={form.meetingAt} disabled={busy} onChange={e=>update('meetingAt',e.target.value)}/><small>Giờ Việt Nam (UTC+07:00).</small></label>
  <label className={styles.full}>Địa điểm<input aria-label="Địa điểm" maxLength={512} value={form.place} disabled={busy} onChange={e=>update('place',e.target.value)}/></label>
  <label className={styles.full}>Kết luận và căn cứ<textarea aria-label="Kết luận và căn cứ" required maxLength={4000} value={form.reason} disabled={busy} onChange={e=>update('reason',e.target.value)}/><small>Ghi kết quả cuộc họp thực tế, không phải lịch dự kiến.</small></label>
  <label>Số quyết định (nếu có)<input aria-label="Số quyết định (nếu có)" maxLength={128} value={form.number} disabled={busy} onChange={e=>update('number',e.target.value)}/></label>
  <label>Ngày quyết định (nếu có)<input aria-label="Ngày quyết định (nếu có)" type="date" value={form.date} disabled={busy} onChange={e=>update('date',e.target.value)}/></label>
 </div>;
}
export function BasisDialog({line,candidates,busy,onClose}:{line:Line;candidates:Candidate[];busy:boolean;onClose:()=>void}){
 return <DecisionDialog title="Căn cứ lựa chọn" onClose={onClose} busy={busy}>
  <p><strong>{line.fullName??'Sinh viên'}</strong> · {line.studentCode??'Chưa có mã sinh viên'}</p>
  <div className={styles.tableWrap}><table className={styles.table}><thead><tr><th>Diện hợp lệ</th><th>Mức</th><th>Khoảng xác minh</th><th>Kết quả</th></tr></thead><tbody>{candidates.map(c=><tr key={c.id} className={c.selection==='fully_selected'?styles.selectedRow:undefined}><td>{c.policyName??'Diện đã xác minh'}</td><td>{rateLabel(c.rate)}</td><td>{shortDate(c.from)} – {shortDate(c.to)}</td><td><span className={styles.badge+' '+(c.selection==='fully_selected'?'':styles.neutral)}>{c.selection==='fully_selected'?'Được chọn':'Không chọn'}</span></td></tr>)}</tbody></table></div>
  <p className={styles.notice}>Chọn một M cao nhất. Không cộng mức, không lấy diện thấp hơn lấp thời gian còn lại. Kết quả trên do backend xác định từ nguồn đã Đóng xét.</p>
  <div className={styles.actions}><button className={styles.primary} onClick={onClose}>Đóng</button></div>
 </DecisionDialog>;
}
export function DialogForm({children,onSubmit}:{children:ReactNode;onSubmit:(e:FormEvent)=>void}){return <form onSubmit={onSubmit}>{children}</form>;}

