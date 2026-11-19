import {useCallback,useEffect,useRef,useState,type FormEvent} from 'react';
import type {FeaturePageProps} from '../../../shared/types/module';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {errorMessage} from '../../../shared/api/error-message';
import {Button} from '../../../shared/ui/Button';
import {Alert} from '../../../shared/ui/Alert';
import {listRounds,readRound} from '../api/rounds';
import type {RoundFilters,RoundPage,RoundView} from '../types/round';
import {RoundDetails,roundStatus} from '../components/RoundDetails';
import {CreateRoundForm} from '../components/CreateRoundForm';
import styles from './KkhtRoundsPage.module.css';

type ListState={key:string;data:RoundPage|null;error:string;loading:boolean};
type DetailState={id:string;data:RoundView|null;error:string;loading:boolean};
const selectedFromUrl=()=>new URLSearchParams(window.location.search).get('dotXetId');
const requestError=(error:unknown)=>error instanceof DOMException&&error.name==='TimeoutError'
 ?'Yêu cầu mất quá nhiều thời gian. Vui lòng thử lại.':errorMessage(error);

export function KkhtRoundsPage({client}:FeaturePageProps){
 const [selectedId,setSelectedId]=useState(selectedFromUrl);
 const [creating,setCreating]=useState(false),[notice,setNotice]=useState('');
 const [page,setPage]=useState(0);
 const [number,setNumber]=useState('');
 const [filters,setFilters]=useState<RoundFilters>({});
 const [semesterLabel,setSemesterLabel]=useState('');
 const [revision,setRevision]=useState(0);
 const [list,setList]=useState<ListState|null>(null);
 const [detail,setDetail]=useState<DetailState|null>(null);
 const [forbidden,setForbidden]=useState(false);
 const detailHeading=useRef<HTMLHeadingElement>(null);
 const listHeading=useRef<HTMLHeadingElement>(null);
 const hydratedDetail=useRef<string|null>(null),readbackAbort=useRef<AbortController|null>(null);
 const onForbidden=useCallback(()=>setForbidden(true),[]);
 const listKey=JSON.stringify({...filters,page,revision});
 const currentList=list?.key===listKey?list:null;
 const currentDetail=detail?.id===selectedId?detail:null;

 useEffect(()=>{
  const onBack=()=>{readbackAbort.current?.abort();setCreating(false);setNotice('');setSelectedId(selectedFromUrl());};
  window.addEventListener('popstate',onBack);
  return()=>{window.removeEventListener('popstate',onBack);readbackAbort.current?.abort();};
 },[]);

 useEffect(()=>{
  if(selectedId!==null||forbidden||creating)return;
  const abort=new AbortController();
  setList({key:listKey,data:null,error:'',loading:true});
  void listRounds(client,{...filters,page,size:20},abort.signal).then(data=>{
   if(!abort.signal.aborted)setList({key:listKey,data,error:'',loading:false});
  }).catch(error=>{
   if(abort.signal.aborted)return;
   if(error instanceof ApiError&&error.status===403)setForbidden(true);
   setList({key:listKey,data:null,error:requestError(error),loading:false});
  });
  return()=>abort.abort();
 },[client,filters,page,revision,selectedId,forbidden,listKey,creating]);

 useEffect(()=>{
  if(selectedId===null||forbidden||creating)return;
  if(hydratedDetail.current===selectedId){hydratedDetail.current=null;return;}
  const abort=new AbortController();
  setDetail({id:selectedId,data:null,error:'',loading:true});
  void readRound(client,selectedId,abort.signal).then(data=>{
   if(!abort.signal.aborted)setDetail({id:selectedId,data,error:'',loading:false});
  }).catch(error=>{
   if(abort.signal.aborted)return;
   if(error instanceof ApiError&&error.status===403)setForbidden(true);
   setDetail({id:selectedId,data:null,error:requestError(error),loading:false});
  });
  return()=>abort.abort();
 },[client,selectedId,revision,forbidden,creating]);

 useEffect(()=>{
  if(selectedId!==null&&currentDetail?.data)detailHeading.current?.focus();
 },[selectedId,currentDetail?.data]);

 function select(id:string|null){
  setCreating(false);setNotice('');
  const url=new URL(window.location.href);
  if(id===null)url.searchParams.delete('dotXetId');else url.searchParams.set('dotXetId',id);
  window.history.pushState(null,'',url.pathname+url.search+url.hash);
  setSelectedId(id);
  if(id===null)requestAnimationFrame(()=>listHeading.current?.focus());
 }
 async function created(view:RoundView,recovered:boolean){
  readbackAbort.current?.abort();
  const abort=new AbortController();readbackAbort.current=abort;
  const confirmed=await readRound(client,view.round.id,abort.signal);
  if(abort.signal.aborted)return;
  const nextFilters={semesterId:confirmed.round.semester.id,number:confirmed.round.number};
  const updated=await listRounds(client,{...nextFilters,page:0,size:20},abort.signal);
  if(abort.signal.aborted)return;
  if(!updated.items.some(row=>row.id===confirmed.round.id&&row.name===confirmed.round.name&&row.number===confirmed.round.number&&row.status===confirmed.round.status))throw new Error('Dữ liệu vừa thay đổi hoặc chưa xuất hiện trong danh sách. Hãy đọc lại kết quả.');
  setFilters(nextFilters);setNumber(String(confirmed.round.number));setPage(0);
  setSemesterLabel(`Học kỳ ${confirmed.round.semester.semesterOrder} · ${confirmed.round.semester.academicYear}`);
  setList({key:JSON.stringify({...nextFilters,page:0,revision}),data:updated,error:'',loading:false});
  hydratedDetail.current=confirmed.round.id;
  setDetail({id:confirmed.round.id,data:confirmed,error:'',loading:false});
  select(confirmed.round.id);
  setNotice(recovered?'Đã đối chiếu đợt xét hiện có.':'Đã tạo đợt xét ở trạng thái Nháp.');
 }
 function apply(event:FormEvent<HTMLFormElement>){
  event.preventDefault();
  setFilters(current=>({...current,number:number===''?undefined:Number(number)}));
  setPage(0);
 }
 function chooseSemester(id:string,academicYear:string,order:number){
  setFilters(current=>({...current,semesterId:id}));
  setSemesterLabel(`Học kỳ ${order} · ${academicYear}`);
  setPage(0);
  if(selectedId!==null)select(null);
 }
 function clearSemester(){
  setFilters(current=>({...current,semesterId:undefined}));
  setSemesterLabel('');setPage(0);
 }
 const data=currentList?.data;
 const totalPages=data?Math.max(1,Math.ceil(data.totalElements/data.size)):1;
 const loading=!currentList||currentList.loading;
 const round=currentDetail?.data?.round;

 return <div className={styles.workspace}>
  <header className={styles.heading}>
   <p className={styles.eyebrow}>Học bổng · Khuyến khích học tập</p>
   <h1>Đợt xét học bổng KKHT</h1>
   <p>Xem các đợt xét theo học kỳ và thông tin cấu hình của từng đợt.</p>
  </header>
  {notice&&<p role="status">{notice}</p>}
  {forbidden?<Alert>Bạn không có quyền truy cập các đợt xét KKHT. Vui lòng kiểm tra lại quyền hoặc đăng nhập lại.</Alert>:creating?
   <CreateRoundForm client={client} onCancel={()=>{setCreating(false);requestAnimationFrame(()=>listHeading.current?.focus());}} onCreated={created} onForbidden={onForbidden}/>:selectedId!==null?
   <section className={styles.content} aria-label="Chi tiết đợt xét">
    <div className={styles.toolbar}>
     <Button variant="secondary" onClick={()=>select(null)}>Quay lại danh sách</Button>
     <Button variant="secondary" disabled={currentDetail?.loading} onClick={()=>setRevision(value=>value+1)}>Đọc lại chi tiết</Button>
    </div>
    {(!currentDetail||currentDetail.loading)&&<p role="status">Đang tải chi tiết đợt xét…</p>}
    {currentDetail?.error&&<Alert>{currentDetail.error}</Alert>}
    {round&&<>
     <h2 ref={detailHeading} tabIndex={-1} className={styles.detailTitle}>{round.name}</h2>
     <RoundDetails round={round} onSemester={()=>chooseSemester(round.semester.id,round.semester.academicYear,round.semester.semesterOrder)}/>
    </>}
   </section>:
   <section className={styles.content} aria-labelledby="round-list-title">
    <h2 id="round-list-title" ref={listHeading} tabIndex={-1}>Danh sách đợt xét</h2>
    <div className={styles.toolbar}><Button onClick={()=>{setNotice('');setCreating(true);}}>Tạo đợt xét</Button></div>
    <form className={styles.filters} onSubmit={apply} aria-label="Lọc đợt xét">
     <label htmlFor="round-number">Số đợt
      <input id="round-number" type="number" min="1" max="2147483647" step="1" value={number} onChange={event=>setNumber(event.target.value)} placeholder="Tất cả số đợt"/>
     </label>
     <Button type="submit" variant="secondary">Lọc danh sách</Button>
     <Button variant="secondary" disabled={loading} onClick={()=>setRevision(value=>value+1)}>Đọc lại danh sách</Button>
    </form>
    {semesterLabel&&<div className={styles.semesterFilter}><span>{semesterLabel}</span><Button variant="secondary" onClick={clearSemester}>Bỏ lọc học kỳ</Button></div>}
    {loading&&<p role="status">Đang tải danh sách đợt xét…</p>}
    {currentList?.error&&<Alert>{currentList.error}</Alert>}
    {data&&<>
     <p className={styles.resultCount} role="status">{data.totalElements} đợt xét</p>
     {data.items.length===0?<p>Không có đợt xét phù hợp.</p>:<div className={styles.tableScroll} role="region" aria-label="Danh sách đợt xét KKHT" tabIndex={0}>
      <table className={styles.table}>
       <thead><tr><th scope="col">Năm học</th><th scope="col">Học kỳ</th><th scope="col">Số đợt</th><th scope="col">Tên đợt xét</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
       <tbody>{data.items.map(row=><tr key={row.id}>
        <td>{row.academicYear}</td><td><button className={styles.semesterLink} type="button" aria-label={`Lọc học kỳ ${row.semesterOrder} năm học ${row.academicYear}`} onClick={()=>chooseSemester(row.semesterId,row.academicYear,row.semesterOrder)}>Học kỳ {row.semesterOrder}</button></td>
        <td>{row.number}</td><td className={styles.roundName}>{row.name}</td><td><span className={styles.badge}>{roundStatus(row.status)}</span></td>
        <td><Button variant="secondary" aria-label={`Xem chi tiết ${row.name}`} onClick={()=>select(row.id)}>Xem chi tiết</Button></td>
       </tr>)}</tbody>
      </table>
     </div>}
     <nav className={styles.pagination} aria-label="Phân trang đợt xét">
      <Button variant="secondary" disabled={data.page===0} onClick={()=>setPage(value=>value-1)}>Trang trước</Button>
      <span>Trang {data.page+1} / {totalPages}</span>
      <Button variant="secondary" disabled={data.page+1>=totalPages||(data.page+1)*data.size>100000} onClick={()=>setPage(value=>value+1)}>Trang sau</Button>
     </nav>
    </>}
   </section>}
 </div>;
}
