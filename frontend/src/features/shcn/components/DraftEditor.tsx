import {assignmentError} from '../api/errors';
import {useCallback,useEffect,useState} from 'react';
import type {AssignmentsApi} from '../api/assignments';
import type {Draft,Faculty} from '../types/assignments';
import {saveBody,statusLabels,matchingContent} from '../model/draft.mjs';
import {TimeFields,formatTime} from './Controls';
import {IndustryAssignments} from './IndustryAssignments';
import {Button} from '../../../shared/ui/Button';
import {Alert} from '../../../shared/ui/Alert';
import styles from '../pages/Assignments.module.css';
export function DraftEditor({api,initial,faculty,back,saved}:{api:AssignmentsApi;initial:Draft;faculty:Faculty;back:()=>void;saved:()=>void}){
 const[base,setBase]=useState(initial),[draft,setDraft]=useState(initial);
 const[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[conflict,setConflict]=useState(false),[remote,setRemote]=useState<Draft|null>(null),[blocked,setBlocked]=useState(false);
 const dirty=JSON.stringify(draft)!==JSON.stringify(base);const writable=faculty.duocLapNhap&&draft.trangThai==='NHAP'&&!blocked;
 useEffect(()=>{const warn=(e:BeforeUnloadEvent)=>{if(dirty||busy)e.preventDefault();};const guard=(e:Event)=>{if((dirty||busy)&&!window.confirm('Có thay đổi chưa lưu. Bạn muốn rời trang?'))e.preventDefault();};window.addEventListener('beforeunload',warn);window.addEventListener('shcn-leave',guard);return()=>{window.removeEventListener('beforeunload',warn);window.removeEventListener('shcn-leave',guard);};},[dirty,busy]);
 function change(k:'hieuLucTu'|'hieuLucDen'|'lyDoDieuChinh',v:string|null){setDraft(d=>({...d,[k]:v} as Draft));}
 async function save(){setError('');setNotice('');if(!draft.cacPhanCong.length&&!window.confirm('Lưu danh sách trống sẽ bỏ hết các phân công đang có. Tiếp tục?'))return;let body;try{body=saveBody(draft);}catch(e){setError((e as Error).message);return;}setBusy(true);
 try{const result=await api.save(draft.id,body);setDraft(result);setBase(result);setNotice('Đã lưu nháp.');saved();}
 catch(e){const err=e as Error&{status?:number;code?:string;fields?:string[]};setError((err.code==='VERSION_CONFLICT'?'Danh sách đã thay đổi ở nơi khác.':err.code==='SHCN_THAM_CHIEU_KHONG_CON_HOP_LE'?'Có lớp hoặc giảng viên không còn hợp lệ. Hãy kiểm tra và chọn lại.':assignmentError(err))+(err.fields?.length?' · Trường cần kiểm tra: '+err.fields.map(f=>{const m=/cacPhanCong\[(\d+)\]/.exec(f);return m?'Lớp '+(draft.cacPhanCong[Number(m[1])]?.maLop??'đang sửa'):f==='hieuLucDen'?'Mốc kết thúc':f==='hieuLucTu'?'Mốc bắt đầu':f==='lyDoDieuChinh'?'Lý do điều chỉnh':'Thông tin danh sách';}).join(', '):''));if(err.status===403||err.status===404){setBlocked(true);setDraft(d=>({...d,cacPhanCong:[]}));}else if(!err.status||err.status===409||err.status>=500)setConflict(true);}
 finally{setBusy(false);}}
 async function readRemote(){setBusy(true);try{const value=await api.detail(draft.id);setRemote(value);if(matchingContent(value,draft))setNotice('Hệ thống hiện chứa đúng nội dung đang sửa. Có thể dùng bản trên hệ thống.');}catch(e){const err=e as Error&{status?:number};setError('Chưa đọc lại được: '+assignmentError(err));if(err.status===403||err.status===404){setBlocked(true);setDraft(d=>({...d,cacPhanCong:[]}));}}finally{setBusy(false);}}
 const lookupFailure=useCallback((e:unknown)=>{
  const err=e as {status?:number};
  if(err.status===403||err.status===404){setBlocked(true);setDraft(d=>({...d,cacPhanCong:[]}));}
 },[]);
 return <section className={styles.workspace}><Button variant="secondary" disabled={busy} onClick={()=>{if(!dirty||window.confirm('Bỏ thay đổi chưa lưu và về danh sách?'))back();}}>← Danh sách phân công</Button><div className={styles.heading}><div><h1>Phân công GVCN — Phiên bản {draft.phienBan}</h1><p>{faculty.tenKhoa} · {draft.namHoc}</p></div><span className={styles.badge}>{statusLabels[draft.trangThai as keyof typeof statusLabels]??'Chưa hỗ trợ trạng thái'}</span></div>
 <p>Đã lưu {base.soLop} lớp · Cập nhật {formatTime(base.ngayCapNhat)} {dirty&&'· Có thay đổi chưa lưu'}</p>
 {blocked?<Alert>Không còn quyền truy cập. Hãy quay về danh sách để kiểm tra phạm vi.</Alert>:<>
 <form onSubmit={e=>{e.preventDefault();void save();}}><fieldset disabled={busy||!writable||conflict}><legend>Thông tin danh sách</legend>{writable?<><TimeFields from={draft.hieuLucTu} until={draft.hieuLucDen} change={change}/><label>Lý do điều chỉnh (không bắt buộc)<textarea maxLength={4000} value={draft.lyDoDieuChinh??''} onChange={e=>change('lyDoDieuChinh',e.target.value)}/></label></>:<p>Đề nghị hiệu lực: {formatTime(draft.hieuLucTu)} → {formatTime(draft.hieuLucDen)}<br/>{draft.lyDoDieuChinh}</p>}{draft.maDanhSachTruoc&&<p>Bản kế thừa: {draft.maDanhSachTruoc}</p>}</fieldset>
 <IndustryAssignments api={api} faculty={draft.maKhoa} rows={draft.cacPhanCong} writable={writable} disabled={busy||conflict} showReview={!!error}
  onChange={rows=>setDraft(d=>({...d,cacPhanCong:rows}))} onFailure={lookupFailure}/>
 {error&&<Alert>{error}</Alert>}{notice&&<p role="status">{notice}</p>}
 {conflict&&<section className={styles.warning}><h2>Đối chiếu kết quả lưu</h2><p>Không gửi lại yêu cầu ghi tự động. Đọc bản trên hệ thống để kiểm tra.</p><Button variant="secondary" disabled={busy} onClick={()=>void readRemote()}>Đọc bản mới</Button>{remote&&<><p>Bản hệ thống: {remote.cacPhanCong.length} lớp · {formatTime(remote.ngayCapNhat)} · {remote.trangThai}</p><div className={styles.table}><table><thead><tr><th>Lớp</th><th>GVCN đang sửa</th><th>GVCN hệ thống</th><th>Ghi chú đang sửa / hệ thống</th></tr></thead><tbody>{[...new Set([...draft.cacPhanCong,...remote.cacPhanCong].map(r=>r.maLop))].map(code=>{const a=draft.cacPhanCong.find(r=>r.maLop===code),b=remote.cacPhanCong.find(r=>r.maLop===code);return <tr key={code}><td>{code}</td><td>{a?.maGiaoVien??'Không có'}</td><td>{b?.maGiaoVien??'Không có'}</td><td>{a?.ghiChu||'—'} / {b?.ghiChu||'—'}</td></tr>;})}</tbody></table></div><p>Đề nghị hiệu lực hệ thống: {formatTime(remote.hieuLucTu)} → {formatTime(remote.hieuLucDen)} · Lý do: {remote.lyDoDieuChinh||'—'}</p><Button variant="secondary" onClick={()=>{if(window.confirm('Dùng bản hệ thống và bỏ thay đổi đang sửa?')){setBase(remote);setDraft(remote);setRemote(null);setConflict(false);setError('');}}}>Dùng bản mới để chỉnh lại</Button></>}</section>}
 {writable&&<div className={styles.actions}><Button variant="secondary" disabled={busy||conflict} onClick={()=>{if(window.confirm('Bỏ các thay đổi chưa lưu?'))setDraft(base);}}>Bỏ thay đổi chưa lưu</Button><Button type="submit" disabled={busy||conflict||!dirty}>{busy?'Đang lưu…':'Lưu nháp'}</Button></div>}</form></>}
</section>;
}
