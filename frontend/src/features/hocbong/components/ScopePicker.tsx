import {useEffect,useRef,useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {errorMessage} from '../../../shared/api/error-message';
import {Alert} from '../../../shared/ui/Alert';
import {Button} from '../../../shared/ui/Button';
import {loadScopeCatalog} from '../api/rounds';
import type {ScopeCatalogSnapshot,ScopeSelection,ScopePair,ScopeGroup,ScopeVersion} from '../types/round';
import styles from './ScopePicker.module.css';
export const pairKey=(p:ScopePair)=>p.cohortId+':'+p.majorId;
export function scopeReady(catalog:ScopeCatalogSnapshot|null,value:ScopeSelection|null){
 if(!catalog||!value||!catalog.groups.length)return false;
 if(value.mode==='all')return catalog.groups.length<=2000;
 const keys=new Set(catalog.groups.map(pairKey));
 return value.groups.length>0&&value.groups.length<=2000&&value.groups.every(g=>keys.has(pairKey(g)));
}
const label=(g:ScopeGroup)=>`${g.cohortCode??'Khóa chưa có mã'} · ${g.majorName??g.majorCode??'Ngành chưa có tên'}`;
type Props={client:AuthClient;sourceId:string;value:ScopeSelection|null;onChange:(s:ScopeSelection)=>void;onCatalog:(s:ScopeCatalogSnapshot|null)=>void;onForbidden:()=>void;disabled?:boolean;refresh?:number;initialGroups?:ScopePair[]};
export function ScopePicker({client,sourceId,value,onChange,onCatalog,onForbidden,disabled=false,refresh=0,initialGroups=[]}:Props){
 const [catalog,setCatalog]=useState<ScopeCatalogSnapshot|null>(null),[error,setError]=useState(''),[loading,setLoading]=useState(true),[reload,setReload]=useState(0);
 const [q,setQ]=useState(''),[cohort,setCohort]=useState(''),[major,setMajor]=useState(''),[page,setPage]=useState(0);
 const callbacks=useRef({onCatalog,onForbidden});callbacks.current={onCatalog,onForbidden};
 useEffect(()=>{
  const abort=new AbortController();setLoading(true);setError('');setCatalog(null);callbacks.current.onCatalog(null);
  void loadScopeCatalog(client,sourceId,abort.signal).then(data=>{if(!abort.signal.aborted){setCatalog(data);callbacks.current.onCatalog(data);setPage(0);}})
   .catch(e=>{if(!abort.signal.aborted){if(e instanceof ApiError&&e.status===403)callbacks.current.onForbidden();setError(e instanceof ApiError&&e.code==='SCOPE_SOURCE_UNAVAILABLE'?'Nguồn chưa đủ thông tin Khóa, Ngành hoặc kỳ bắt đầu học. Cần đối chiếu dữ liệu Đào tạo trước khi chọn phạm vi.':errorMessage(e));}})
   .finally(()=>{if(!abort.signal.aborted)setLoading(false);});
  return()=>abort.abort();
 },[client,sourceId,refresh,reload]);
 const groups=catalog?.groups??[],keys=new Set(value?.mode==='groups'?value.groups.map(pairKey):[]);
 const query=q.trim().toLocaleLowerCase('vi');
 const shown=groups.filter(g=>(!cohort||g.cohortId===cohort)&&(!major||g.majorId===major)&&(!query||[g.cohortCode,g.majorCode,g.majorName].some(t=>t?.toLocaleLowerCase('vi').includes(query))));
 const missing=value?.mode==='groups'?value.groups.filter(p=>!groups.some(g=>pairKey(g)===pairKey(p))):[];
 const cohorts=[...new Map(groups.map(g=>[g.cohortId,g.cohortCode??'Khóa chưa có mã'])).entries()];
 const majors=[...new Map(groups.map(g=>[g.majorId,g.majorName??g.majorCode??'Ngành chưa có tên'])).entries()];
 const selectedCount=value?.mode==='all'?groups.length:keys.size;
 function choose(pairs:ScopePair[]){onChange({mode:'groups',groups:pairs});}
 function toggle(g:ScopeGroup,checked:boolean){const current=value?.mode==='groups'?value.groups:[];choose(checked?[...current,{cohortId:g.cohortId,majorId:g.majorId}]:current.filter(p=>pairKey(p)!==pairKey(g)));}
 return <fieldset className={styles.picker} disabled={disabled}>
  <legend>Phạm vi sinh viên</legend>
  <p className={styles.hint}>Chỉ gồm sinh viên bắt đầu học không sau kỳ xét. Mỗi nhóm xét và nhập quỹ theo Khóa + Ngành; số sinh viên nguồn chưa phải số đủ điều kiện nhận học bổng.</p>
  {loading&&<p role="status">Đang tải danh mục nhóm…</p>}
  {error&&<Alert>{error}</Alert>}
  <div><Button variant="secondary" disabled={disabled||loading} onClick={()=>setReload(v=>v+1)}>Đọc lại danh mục nhóm</Button></div>
  {!loading&&catalog&&<>
   {!groups.length?<p role="status">Chưa có nhóm sinh viên để xét trong học kỳ này.</p>:<>
    <label className={styles.choice}><input type="radio" name="scope-mode" checked={value?.mode==='all'} onChange={()=>onChange({mode:'all'})}/>Toàn bộ nhóm trong học kỳ</label>
    <label className={styles.choice}><input type="radio" name="scope-mode" checked={value?.mode==='groups'} onChange={()=>choose(value?.mode==='groups'?value.groups:initialGroups.map(g=>({cohortId:g.cohortId,majorId:g.majorId})))}/>Chọn nhóm Khóa + Ngành</label>
    <p role="status">{value?`Đã chọn ${selectedCount} nhóm`:'Hãy chọn phạm vi trước khi tạo đợt.'}</p>
    {selectedCount>2000&&<Alert>Mỗi đợt tối đa 2000 nhóm. Hãy chọn các nhóm cụ thể.</Alert>}
    {value?.mode==='all'?<p className={styles.hint}>Phạm vi được cố định tại lúc lưu; nhóm mới xuất hiện sau đó không tự thêm vào đợt.</p>:value?.mode==='groups'&&<>
     <div className={styles.filters}>
      <label>Tìm nhóm<input value={q} onChange={e=>{setQ(e.target.value);setPage(0);}} maxLength={128} placeholder="Mã khóa, ngành hoặc tên ngành"/></label>
      <label>Lọc theo khóa<select value={cohort} onChange={e=>{setCohort(e.target.value);setPage(0);}}><option value="">Tất cả khóa</option>{cohorts.map(([id,name])=><option key={id} value={id}>{name}</option>)}</select></label>
      <label>Lọc theo ngành<select value={major} onChange={e=>{setMajor(e.target.value);setPage(0);}}><option value="">Tất cả ngành</option>{majors.map(([id,name])=><option key={id} value={id}>{name}</option>)}</select></label>
     </div>
     <div className={styles.actions}>
      <Button variant="secondary" disabled={!shown.length||new Set([...keys,...shown.map(pairKey)]).size>2000} onClick={()=>choose([...new Map([...(value.mode==='groups'?value.groups:[]),...shown.map(g=>({cohortId:g.cohortId,majorId:g.majorId}))].map(p=>[pairKey(p),p])).values()])}>Chọn tất cả nhóm đang lọc</Button>
      <Button variant="secondary" disabled={!keys.size} onClick={()=>choose([])}>Bỏ chọn tất cả</Button>
     </div>
     {missing.length>0&&<Alert>{missing.length} nhóm đã chọn không còn trong nguồn. <Button variant="secondary" onClick={()=>choose(value.groups.filter(p=>!missing.includes(p)))}>Bỏ nhóm không còn trong nguồn</Button></Alert>}
     <div className={styles.rows} role="group" aria-label="Các nhóm Khóa + Ngành">{shown.slice(page*20,(page+1)*20).map(g=><label key={pairKey(g)} className={styles.row}>
      <input type="checkbox" checked={keys.has(pairKey(g))} disabled={keys.size>=2000&&!keys.has(pairKey(g))} onChange={e=>toggle(g,e.target.checked)} aria-label={label(g)}/>
      <span><strong>{label(g)}</strong><span className={styles.count}>{g.studentCount} sinh viên nguồn</span></span>
     </label>)}</div>
     {!shown.length&&<p>Không có nhóm phù hợp với bộ lọc.</p>}
     <nav aria-label="Phân trang nhóm" className={styles.actions}>
      <Button variant="secondary" aria-label="Trang nhóm trước" disabled={page===0} onClick={()=>setPage(p=>p-1)}>Trang trước</Button>
      <span>Trang nhóm {page+1} / {Math.max(1,Math.ceil(shown.length/20))}</span>
      <Button variant="secondary" aria-label="Trang nhóm sau" disabled={(page+1)*20>=shown.length} onClick={()=>setPage(p=>p+1)}>Trang sau</Button>
     </nav>
    </>}
   </>}
  </>}
 </fieldset>;
}
export function ScopeSummary({scope}:{scope:ScopeVersion|null}){
 if(!scope)return <Alert>Đợt cũ chưa có phạm vi. Cần đối chiếu dữ liệu trước khi tiếp tục; không thể sửa phạm vi từ màn hình này.</Alert>;
 return <section className={styles.summary} aria-label="Phạm vi đã lưu"><h3>Phạm vi đã lưu</h3><p>Phiên phạm vi {scope.revision}</p><p>{scope.groups.length} nhóm · {scope.mode==='all'?'Toàn bộ nhóm tại lúc lưu':'Các nhóm đã chọn'}</p>
  <div className={styles.savedGroups}>{scope.groups.map(g=><p key={pairKey(g)}>{label(g)} · {g.studentCount} sinh viên nguồn</p>)}</div>
 </section>;
}
