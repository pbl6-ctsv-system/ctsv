import {useEffect,useRef,useState} from 'react';
import type {RoundsApi} from '../api/rounds';
import type {RoundGroups,AudienceClass,AudienceStudent,AudienceWarning} from '../types/rounds';
import type {Page} from '../types/assignments';
import {roundError} from '../model/round.mjs';
import {Modal,Pager} from './Controls';
import {Button} from '../../../shared/ui/Button';
import styles from '../pages/Rounds.module.css';

function LookupError({message}:{message:string}){
 const ref=useRef<HTMLDivElement>(null);
 useEffect(()=>{ref.current?.scrollIntoView({block:'nearest'});},[message]);
 return <div ref={ref} role="alert" className={styles.lookupError}>{message}</div>;
}
export function AudienceWarnings({warnings}:{warnings:AudienceWarning[]|undefined}){
 return <aside className={styles.warning} aria-label="Căn cứ phân nhóm"><strong>Danh sách cần đối chiếu</strong>
  {warnings?.length?<ul>{warnings.map(w=><li key={w.ma}>{w.thongDiep}</li>)}</ul>:<p>Chưa có thông tin xác nhận danh sách phân lớp đầy đủ của học kỳ này.</p>}
  <p>Đây là dữ liệu đối chiếu để lập nháp, chưa phải danh sách nhận thông báo đã xác nhận.</p></aside>;
}
function Search({value,change,submit,busy,subject}:{value:string;change:(v:string)=>void;submit:()=>void;busy:boolean;subject:string}){
 return <div className={styles.lookupFilters}><label>Tìm {subject}<input maxLength={200} value={value} onChange={e=>change(e.target.value)} onKeyDown={e=>{if(e.key==='Enter'){e.preventDefault();submit();}}}/></label><Button variant="secondary" disabled={busy} onClick={submit}>Tìm</Button></div>;
}
export function RoundStudents({api,semester,classroom,back}:{api:RoundsApi;semester:string;classroom:{ma:string;ten:string};back:()=>void}){
 const [data,setData]=useState<Page<AudienceStudent>|null>(null),[text,setText]=useState(''),[q,setQ]=useState(''),[page,setPage]=useState(0),[retry,setRetry]=useState(0),[busy,setBusy]=useState(true),[error,setError]=useState('');
 useEffect(()=>{const abort=new AbortController();setData(null);setError('');setBusy(true);
  void api.students(semester,classroom.ma,q,page,abort.signal).then(d=>{if(!abort.signal.aborted)setData(d);}).catch(e=>{if(!abort.signal.aborted)setError(roundError(e));}).finally(()=>{if(!abort.signal.aborted)setBusy(false);});
  return()=>abort.abort();
 },[api,semester,classroom.ma,q,page,retry]);
 return <section aria-label={'Sinh viên lớp '+classroom.ten}><Button variant="secondary" onClick={back}>← Các lớp</Button><h3>Sinh viên lớp {classroom.ten}</h3>
  <Search subject="mã hoặc tên SV" value={text} change={setText} submit={()=>{setQ(text.trim());setPage(0);}} busy={busy}/>
  {busy&&<p role="status">Đang tải sinh viên…</p>}{error&&<><LookupError message={error}/><Button variant="secondary" onClick={()=>setRetry(v=>v+1)}>Thử lại danh sách SV</Button></>}
  {data&&!busy&&!error&&<><p>{data.totalElements} sinh viên phù hợp</p>
   <div className={styles.table}><table><thead><tr><th>Mã SV</th><th>Họ tên</th><th>Lớp trong kỳ</th><th>Căn cứ</th><th>Xác minh</th></tr></thead><tbody>
    {data.items.map(s=><tr key={s.id}><td>{s.maSinhVien}</td><td>{s.hoTen}</td><td>{s.tenLop}</td><td>{s.canCu==='PHAN_LOP_REN_LUYEN_DUNG_KY'?'Phân lớp rèn luyện đúng kỳ':'Học vụ đúng kỳ'}</td><td><span className={styles.badge}>{s.duCanCuNguon&&s.trangThaiXacMinh==='DU_CAN_CU_NGUON'?'Đủ căn cứ đối chiếu nguồn':'Cần xác minh'}</span></td></tr>)}
    {!data.items.length&&<tr><td colSpan={5}>Không có sinh viên phù hợp bộ lọc.</td></tr>}
   </tbody></table></div><Pager page={page} total={data.totalElements} change={setPage}/></>}
 </section>;
}
export type AudienceTarget={type:'KHOA_HOC'|'TRE_TIEN_DO';ma:string;ten:string};
export function RoundAudienceBrowser({api,semester,semesterName,catalog,target,close}:{api:RoundsApi;semester:string;semesterName:string;catalog:RoundGroups;target:AudienceTarget;close:()=>void}){
 const [selector,setSelector]=useState(target.type==='KHOA_HOC'?{type:'KHOA_HOC',ma:target.ma}:null),[selected,setSelected]=useState<AudienceClass|null>(null);
 const [data,setData]=useState<Page<AudienceClass>|null>(null),[text,setText]=useState(''),[q,setQ]=useState(''),[page,setPage]=useState(0),[retry,setRetry]=useState(0),[busy,setBusy]=useState(false),[error,setError]=useState('');
 useEffect(()=>{const abort=new AbortController();setData(null);setError('');if(!selector){setBusy(false);return()=>abort.abort();}setBusy(true);
  void api.classes(semester,selector.type,selector.ma,q,page,abort.signal).then(d=>{if(!abort.signal.aborted)setData(d);}).catch(e=>{if(!abort.signal.aborted)setError(roundError(e));}).finally(()=>{if(!abort.signal.aborted)setBusy(false);});
  return()=>abort.abort();
 },[api,semester,selector?.type,selector?.ma,q,page,retry]);
 return <Modal title={'Lớp và sinh viên – '+target.ten} close={close}>
  <p className={styles.lookupHint}>{semesterName} → {target.ten}{selected?' → '+selected.ten:''}</p>
  <AudienceWarnings warnings={catalog.canhBao}/>
  {selected?<RoundStudents key={semester+':'+selected.ma} api={api} semester={semester} classroom={selected} back={()=>setSelected(null)}/>:<>
   {target.type==='TRE_TIEN_DO'&&<><h3>Các lớp rèn luyện nhận sinh viên</h3>{!catalog.lopTreTienDo.length?<p>Chưa có danh sách phân lớp rèn luyện đúng kỳ để xem.</p>:<ul className={styles.receiverList}>{catalog.lopTreTienDo.map(l=><li key={l.ma}><span>{l.ten} · {l.soSinhVien} SV đối chiếu</span><Button variant="secondary" onClick={()=>{setSelector({type:'LOP',ma:l.ma});setText('');setQ('');setPage(0);}}>Xem lớp {l.ten}</Button></li>)}</ul>}</>}
   {selector&&<><h3>Các lớp có dữ liệu trong học kỳ</h3><Search subject="mã hoặc tên lớp" value={text} change={setText} submit={()=>{setQ(text.trim());setPage(0);}} busy={busy}/>
    {busy&&<p role="status">Đang tải lớp…</p>}{error&&<><LookupError message={error}/><Button variant="secondary" onClick={()=>setRetry(v=>v+1)}>Thử lại danh sách lớp</Button></>}
    {data&&!busy&&!error&&<><div className={styles.table}><table><thead><tr><th>Mã lớp</th><th>Tên lớp</th><th>Nhóm/khóa</th><th>SV đối chiếu</th><th>Cần xác minh</th><th>Thao tác</th></tr></thead><tbody>
     {data.items.map(l=><tr key={l.ma}><td>{l.ma}</td><td>{l.ten}</td><td>{l.loaiLop==='TRE_TIEN_DO'?'Lớp trễ tiến độ':l.tenKhoaHoc??'Chưa có thông tin khóa'}</td><td>{l.soSinhVien}</td><td>{l.soSinhVienCanXacMinh}</td><td><Button variant="secondary" onClick={()=>setSelected(l)} aria-label={'Xem SV lớp '+l.ten}>Xem SV</Button></td></tr>)}
     {!data.items.length&&<tr><td colSpan={6}>Không có lớp phù hợp bộ lọc.</td></tr>}
    </tbody></table></div><Pager page={page} total={data.totalElements} change={setPage}/></>}
   </>}
  </>}
 </Modal>;
}
