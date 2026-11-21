import {useEffect,useState} from 'react';
import type {AssignmentsApi} from '../api/assignments';
import type {Page,Program,SchoolClass,Lecturer} from '../types/assignments';
import {Modal,Pager} from './Controls';
import {Button} from '../../../shared/ui/Button';
import {Alert} from '../../../shared/ui/Alert';
import styles from '../pages/Assignments.module.css';
export function Lookup({api,faculty,kind,close,choose,existing=[]}:{api:AssignmentsApi;faculty:string;kind:'class'|'lecturer';close:()=>void;choose:(v:SchoolClass|Lecturer)=>void;existing?:string[]}){
 const[q,setQ]=useState(''),[page,setPage]=useState(0),[program,setProgram]=useState(''),[programQ,setProgramQ]=useState(''),[pp,setPp]=useState(0);
 const[programs,setPrograms]=useState<Page<Program>|null>(null),[data,setData]=useState<Page<SchoolClass|Lecturer>|null>(null),[error,setError]=useState(''),[retry,setRetry]=useState(0);
 useEffect(()=>{if(kind!=='class')return;const abort=new AbortController();setPrograms(null);api.programs(faculty,programQ,pp,abort.signal).then(setPrograms).catch(e=>{if(!abort.signal.aborted)setError('Chưa tải được chương trình: '+e.message);});return()=>abort.abort();},[faculty,kind,programQ,pp,retry]);
 useEffect(()=>{const abort=new AbortController();setData(null);setError('');const timer=setTimeout(()=>{const load=kind==='class'?api.classes(faculty,q,page,program,abort.signal):api.lecturers(faculty,q,page,abort.signal);load.then(setData).catch(e=>{if(!abort.signal.aborted)setError('Chưa tải được danh mục: '+e.message);});},300);return()=>{clearTimeout(timer);abort.abort();};},[faculty,kind,q,page,program,retry]);
 return <Modal title={kind==='class'?'Chọn lớp':'Chọn GVCN'} close={close}><label>Tìm mã hoặc tên<input maxLength={100} value={q} onChange={e=>{setQ(e.target.value);setPage(0);}}/></label>
 {kind==='class'&&<fieldset><legend>Ngành/chương trình</legend><label>Tìm chương trình<input value={programQ} maxLength={100} onChange={e=>{setProgramQ(e.target.value);setPp(0);}}/></label><select aria-label="Ngành/chương trình" value={program} onChange={e=>{setProgram(e.target.value);setPage(0);}}><option value="">Tất cả chương trình</option>{program&& !programs?.items.some(p=>p.id===program)&&<option value={program}>Chương trình đã chọn</option>}{programs?.items.map(p=><option key={p.id} value={p.id}>{p.tenNganhMoi||p.ten} · {p.ten} ({p.ma})</option>)}</select>{programs&&<Pager page={pp} total={programs.totalElements} change={setPp}/>}</fieldset>}
 {error&&<Alert>{error} <Button variant="secondary" onClick={()=>setRetry(v=>v+1)}>Thử tải lại</Button></Alert>}
 {!data&&!error&&<p role="status">Đang tải danh mục…</p>}{data?.items.length===0&&<p>Không có kết quả phù hợp.</p>}
 <ul className={styles.options}>{data?.items.map(v=>{const code='maLop'in v?v.maLop:v.maGiaoVien;const name='tenLop'in v?v.tenLop:v.hoTen;const duplicate=kind==='class'&&existing.includes(code);return <li key={'id'in v?v.id:v.idCanBo}><div><strong>{name}</strong><small>{code}</small>{(!v.coTheChon||duplicate)&&<small>{duplicate?'Đã có trong danh sách':'Chưa đủ điều kiện chọn: '+(v.lyDoKhongTheChon??'Chưa xác minh')}</small>}</div><Button variant="secondary" disabled={!v.coTheChon||duplicate} onClick={()=>choose(v)}>Chọn</Button></li>;})}</ul>
 {data&&<Pager page={page} total={data.totalElements} change={setPage}/>}</Modal>;
}
