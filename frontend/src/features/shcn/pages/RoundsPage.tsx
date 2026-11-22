import {useCallback,useEffect,useMemo,useRef,useState} from 'react';
import type {FeaturePageProps} from '../../../shared/types/module';
import type {Round,RoundPage,RoundFilters,RoundResult,Semester} from '../types/rounds';
import {roundsApi} from '../api/rounds';
import {roundStates,roundDate,roundError} from '../model/round.mjs';
import {RoundEditor,semesterLabel} from '../components/RoundEditor';
import {AsyncChoice} from '../components/AsyncChoice';
import {Pager} from '../components/Controls';
import {Button} from '../../../shared/ui/Button';
import {Alert} from '../../../shared/ui/Alert';
import styles from './Rounds.module.css';

export function RoundsPage({client}:FeaturePageProps){
 const api=useMemo(()=>roundsApi(client),[client]);
 const [data,setData]=useState<RoundPage|null>(null),[canPrepare,setCanPrepare]=useState(false),[filters,setFilters]=useState<RoundFilters>({}),[page,setPage]=useState(0),[q,setQ]=useState(''),[state,setState]=useState(''),[semester,setSemester]=useState<Semester|null>(null);
 const [id,setId]=useState(()=>new URLSearchParams(location.search).get('dotId')),[detail,setDetail]=useState<Round|null>(null),[creating,setCreating]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),[denied,setDenied]=useState(false),[retry,setRetry]=useState(0);
 const request=useRef(0),currentUrl=useRef(location.href);
 const loadSemesters=useCallback((query:string,p:number,s:AbortSignal)=>api.semesters(query,p,s),[api]);
 const lookupFailure=useCallback((e:unknown)=>setError(roundError(e)),[]);
 const block=useCallback(()=>{setDenied(true);setDetail(null);setData(null);setCanPrepare(false);setCreating(false);setError('Bạn không còn quyền truy cập đợt SHCN.');},[]);
 useEffect(()=>{const pop=()=>{if(!window.dispatchEvent(new Event('shcn-leave',{cancelable:true}))){history.pushState(null,'',currentUrl.current);return;}currentUrl.current=location.href;setId(new URLSearchParams(location.search).get('dotId'));setDetail(null);setCreating(false);};window.addEventListener('popstate',pop);return()=>window.removeEventListener('popstate',pop);},[]);
 useEffect(()=>{
  if(creating||denied)return;const controller=new AbortController(),generation=++request.current;setBusy(true);setError('');
  const action=id?api.detail(id,controller.signal):api.list(filters,page,controller.signal);
  void action.then(result=>{if(controller.signal.aborted||generation!==request.current)return;setCanPrepare(result.canPrepare);if(id)setDetail(result.value as Round);else setData(result.value as RoundPage);})
   .catch(e=>{if(controller.signal.aborted)return;if(e.status===403)block();else setError(roundError(e));})
   .finally(()=>{if(!controller.signal.aborted)setBusy(false);});return()=>controller.abort();
 },[api,id,creating,filters,page,retry,denied,block]);
 function navigate(value:Round|null){const url=new URL(location.href);if(value)url.searchParams.set('dotId',value.id);else url.searchParams.delete('dotId');history.pushState(null,'',url);currentUrl.current=location.href;setId(value?.id??null);setDetail(null);setCreating(false);setError('');}
 function saved(result:RoundResult<Round>){setCanPrepare(result.canPrepare);setDetail(result.value);setData(p=>p?{...p,items:p.items.map(d=>d.id===result.value.id?result.value:d)}:p);if(!id){const url=new URL(location.href);url.searchParams.set('dotId',result.value.id);history.replaceState(null,'',url);currentUrl.current=location.href;setId(result.value.id);setCreating(false);}}
 if(denied)return <Alert>{error}</Alert>;
 if(creating||detail&&id)return <><RoundEditor key={creating?'new':id} api={api} initial={creating?null:detail} canPrepare={canPrepare} back={()=>navigate(null)} saved={saved} blocked={block}/>{error&&<Alert>{error}</Alert>}</>;
 return <section className={styles.workspace}><div className={styles.heading}><div><h1>Quản lý đợt SHCN</h1><p>Quản lý các đợt sinh hoạt chủ nhiệm toàn trường.</p></div>{canPrepare&&<Button disabled={busy} onClick={()=>{setCreating(true);setError('');}}>Tạo đợt SHCN</Button>}</div>
  <form className={styles.filters} onSubmit={e=>{e.preventDefault();setPage(0);setFilters({q:q||undefined,trangThai:state||undefined,maHocKy:semester?.ma});}}>
   <div><AsyncChoice label="Học kỳ" value={semester?semesterLabel(semester):'Tất cả học kỳ'} load={loadSemesters} itemKey={s=>s.ma} itemLabel={semesterLabel} onChoose={setSemester} onFailure={lookupFailure}/>{semester&&<Button variant="secondary" onClick={()=>setSemester(null)}>Bỏ lọc học kỳ</Button>}</div>
   <label>Trạng thái<select value={state} onChange={e=>setState(e.target.value)}><option value="">Tất cả</option>{Object.entries(roundStates).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label>
   <label>Tìm tên đợt<input maxLength={200} value={q} onChange={e=>setQ(e.target.value)}/></label><Button type="submit" disabled={busy}>Xem danh sách</Button>
  </form>
  {busy&&<p role="status">Đang tải đợt SHCN…</p>}{error&&<><Alert>{error}</Alert><Button variant="secondary" disabled={busy} onClick={()=>setRetry(v=>v+1)}>Thử lại</Button></>}
  {!busy&&!error&&data&&<>{data.items.length?<div className={styles.table}><table><thead><tr><th>Tên đợt</th><th>Học kỳ</th><th>Khung ngày</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{data.items.map(d=><tr key={d.id}><td>{d.tenDot}</td><td>{d.maHocKy}</td><td>{roundDate(d.khungNgayBatDau)} → {roundDate(d.khungNgayKetThuc)}</td><td><span className={styles.badge}>{roundStates[d.trangThai as keyof typeof roundStates]??d.trangThai}</span></td><td><Button variant="secondary" onClick={()=>navigate(d)}>Mở</Button></td></tr>)}</tbody></table></div>:<p>Chưa có đợt phù hợp bộ lọc.</p>}<Pager page={page} total={data.totalElements} change={setPage}/></>}
 </section>;
}
