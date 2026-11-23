import {useCallback,useEffect,useRef,useState,type FormEvent} from 'react';
import type {FeaturePageProps} from '../../../shared/types/module';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {errorMessage} from '../../../shared/api/error-message';
import {Button} from '../../../shared/ui/Button';
import {Alert} from '../../../shared/ui/Alert';
import {listRounds,readRoundContext,readDefaultSemester,readSourceSemester} from '../api/rounds';
import type {RoundFilters,RoundPage,RoundView,RoundContext,SourceSemester} from '../types/round';
import {RoundDetails,roundStatus} from '../components/RoundDetails';
import {SourceSemesterPicker,sourceReason} from '../components/SourceSemesterPicker';
import {CreateRoundForm,type CreateRoundMemory} from '../components/CreateRoundForm';
import {EditRoundForm} from '../components/EditRoundForm';
import {ScopeSummary} from '../components/ScopePicker';
import styles from './KkhtRoundsPage.module.css';

type Location={sourceId:string|null;roundId:string|null;error:string};
type ListState={key:string;data:RoundPage|null;error:string;loading:boolean};
type DetailState={id:string;data:RoundContext|null;error:string;loading:boolean};
type SemesterState={key:string;data:SourceSemester|null;error:string;loading:boolean;empty:boolean};
const validId=(id:string)=>/^[1-9][0-9]{0,18}$/.test(id)&&BigInt(id)<=9223372036854775807n;
function readLocation():Location{
 const query=new URLSearchParams(window.location.search),sourceId=query.get('hocKyNguonId'),roundId=query.get('dotXetId');
 const invalid=['hocKyNguonId','dotXetId'].some(key=>query.getAll(key).length>1)
  ||(sourceId!==null&&!validId(sourceId))||(roundId!==null&&!validId(roundId));
 return{sourceId,roundId,error:invalid?'Đường dẫn học kỳ hoặc đợt xét không hợp lệ. Hãy chọn lại học kỳ.':''};
}
function writeLocation(sourceId:string|null,roundId:string|null,replace=false){
 const url=new URL(window.location.href);
 for(const [key,value] of [['hocKyNguonId',sourceId],['dotXetId',roundId]] as const){
  url.searchParams.delete(key);if(value!==null)url.searchParams.set(key,value);
 }
 const next=url.pathname+url.search+url.hash;
 if(next!==window.location.pathname+window.location.search+window.location.hash)window.history[replace?'replaceState':'pushState'](null,'',next);
 return readLocation();
}
const ignoreBusy=()=>{};
const requestError=(error:unknown)=>error instanceof DOMException&&error.name==='TimeoutError'
 ?'Yêu cầu mất quá nhiều thời gian. Vui lòng thử lại.':errorMessage(error);

export function KkhtRoundsPage({client}:FeaturePageProps){
 const [location,setLocation]=useState(readLocation),selectedId=location.roundId;
 const [creating,setCreating]=useState(false),[notice,setNotice]=useState('');
 const [editing,setEditing]=useState<RoundContext|null>(null);
 const [page,setPage]=useState(0),[number,setNumber]=useState('');
 const [filters,setFilters]=useState<RoundFilters>({});
 const [revision,setRevision]=useState(0),[sourceRevision,setSourceRevision]=useState(0);
 const [list,setList]=useState<ListState|null>(null),[detail,setDetail]=useState<DetailState|null>(null);
 const [semester,setSemester]=useState<SemesterState|null>(null),[forbidden,setForbidden]=useState(false);
 const detailHeading=useRef<HTMLHeadingElement>(null),listHeading=useRef<HTMLHeadingElement>(null);
 const hydratedDetail=useRef<string|null>(null),hydratedSource=useRef<SourceSemester|null>(null);
 const readbackAbort=useRef<AbortController|null>(null);
 // Per-account page lifetime only: retain uncertain commands across back/form changes, never storage.
 const creationMemories=useRef(new Map<string,CreateRoundMemory>());
 const onForbidden=useCallback(()=>setForbidden(true),[]);
 const currentDetail=detail?.id===selectedId?detail:null;
 const fallbackRoundId=location.sourceId===null?selectedId:null;
 const requestedSource=location.sourceId??(selectedId===null?null:currentDetail?.data?.round.sourceSemesterId);
 const sourceKey=JSON.stringify([requestedSource,sourceRevision]);
 const currentSemester=semester?.key===sourceKey?semester:null;
 const source=location.error?null:currentSemester?.data??null;
 if(source&&!creationMemories.current.has(source.sourceSemesterId))creationMemories.current.set(source.sourceSemesterId,{selection:null,intent:null,phase:'editing',saved:null});
 const listFilters={...filters,sourceSemesterId:source?.sourceSemesterId,page,size:20};
 const listKey=JSON.stringify({...listFilters,revision});
 const currentList=list?.key===listKey?list:null;
 const round=currentDetail?.data?.round;
 const mismatch=!!source&&!!round&&source.sourceSemesterId!==round.sourceSemesterId
  &&!(source.academicYear===round.semester.academicYear&&source.semesterOrder===round.semester.semesterOrder
   &&(source.scholarshipSemesterId===null||source.scholarshipSemesterId===round.semester.id));

 useEffect(()=>{
  const onBack=()=>{
   readbackAbort.current?.abort();hydratedSource.current=null;hydratedDetail.current=null;
   setCreating(false);setEditing(null);setNotice('');setNumber('');setFilters({});setPage(0);setLocation(readLocation());
  };
  window.addEventListener('popstate',onBack);
  return()=>{window.removeEventListener('popstate',onBack);readbackAbort.current?.abort();};
 },[]);

 useEffect(()=>{
  if(forbidden||location.error||requestedSource===undefined)return;
  if(hydratedSource.current?.sourceSemesterId===requestedSource){
   setSemester({key:sourceKey,data:hydratedSource.current,error:'',loading:false,empty:false});hydratedSource.current=null;return;
  }
  const abort=new AbortController();setSemester({key:sourceKey,data:null,error:'',loading:true,empty:false});
  const pending=requestedSource===null?readDefaultSemester(client,abort.signal).then(result=>result.semester):readSourceSemester(client,requestedSource,abort.signal);
  void pending.then(data=>{
   if(abort.signal.aborted)return;
   if(data&&location.sourceId!==data.sourceSemesterId){
    hydratedSource.current=data;setSemester({key:JSON.stringify([data.sourceSemesterId,sourceRevision]),data,error:'',loading:false,empty:false});
    setLocation(writeLocation(data.sourceSemesterId,fallbackRoundId,true));
   }else setSemester({key:sourceKey,data,error:'',loading:false,empty:data===null});
  }).catch(error=>{
   if(abort.signal.aborted)return;
   if(error instanceof ApiError&&error.status===403)setForbidden(true);
   setSemester({key:sourceKey,data:null,error:requestError(error),loading:false,empty:false});
  });
  return()=>abort.abort();
 },[client,requestedSource,sourceRevision,sourceKey,forbidden,location.error,location.sourceId,fallbackRoundId]);

 useEffect(()=>{
  if(selectedId!==null||forbidden||creating||editing||!source||location.error)return;
  const abort=new AbortController();setList({key:listKey,data:null,error:'',loading:true});
  void listRounds(client,{...filters,sourceSemesterId:source.sourceSemesterId,page,size:20},abort.signal).then(data=>{
   if(!abort.signal.aborted)setList({key:listKey,data,error:'',loading:false});
  }).catch(error=>{
   if(abort.signal.aborted)return;
   if(error instanceof ApiError&&error.status===403)setForbidden(true);
   setList({key:listKey,data:null,error:requestError(error),loading:false});
  });
  return()=>abort.abort();
 },[client,filters,page,revision,selectedId,forbidden,listKey,creating,editing,source?.sourceSemesterId,source?.unavailableReason,location.error]);

 useEffect(()=>{
  if(selectedId===null||forbidden||creating||editing||location.error)return;
  if(hydratedDetail.current===selectedId){hydratedDetail.current=null;return;}
  const abort=new AbortController();setDetail({id:selectedId,data:null,error:'',loading:true});
  void readRoundContext(client,selectedId,abort.signal).then(data=>{
   if(!abort.signal.aborted)setDetail({id:selectedId,data,error:'',loading:false});
  }).catch(error=>{
   if(abort.signal.aborted)return;
   if(error instanceof ApiError&&error.status===403)setForbidden(true);
   setDetail({id:selectedId,data:null,error:requestError(error),loading:false});
  });
  return()=>abort.abort();
 },[client,selectedId,revision,forbidden,creating,editing,location.error]);

 useEffect(()=>{if(selectedId!==null&&round&&!mismatch)detailHeading.current?.focus();},[selectedId,round,mismatch]);

 function select(id:string|null){
  setCreating(false);setEditing(null);setNotice('');setLocation(writeLocation(location.sourceId??source?.sourceSemesterId??null,id));
  if(id===null)requestAnimationFrame(()=>listHeading.current?.focus());
 }
 const chooseSource=useCallback((value:SourceSemester|null)=>{
  if(!value)return;
  readbackAbort.current?.abort();hydratedSource.current=value;
  setSourceRevision(rev=>rev+1);setNumber('');setFilters({});setPage(0);setCreating(false);setEditing(null);setNotice('');
  setLocation(writeLocation(value.sourceSemesterId,null));
 },[]);
 function chooseSourceId(id:string){
  readbackAbort.current?.abort();hydratedSource.current=null;setSourceRevision(rev=>rev+1);
  setNumber('');setFilters({});setPage(0);setCreating(false);setEditing(null);setNotice('');setLocation(writeLocation(id,null));
 }
 async function confirmRound(view:RoundView,message:string){
  readbackAbort.current?.abort();const abort=new AbortController();readbackAbort.current=abort;
  const confirmed=await readRoundContext(client,view.round.id,abort.signal);if(abort.signal.aborted)return;
  const nextFilters={number:confirmed.round.number};
  const nextPage={...nextFilters,sourceSemesterId:confirmed.round.sourceSemesterId,page:0,size:20};
  const updated=await listRounds(client,nextPage,abort.signal);if(abort.signal.aborted)return;
  if(!updated.items.some(row=>row.id===confirmed.round.id&&row.name===confirmed.round.name&&row.number===confirmed.round.number&&row.status===confirmed.round.status))throw new Error('Dữ liệu vừa thay đổi hoặc chưa xuất hiện trong danh sách. Hãy đọc lại kết quả.');
  creationMemories.current.delete(confirmed.round.sourceSemesterId);
  setFilters(nextFilters);setNumber(String(confirmed.round.number));setPage(0);
  setList({key:JSON.stringify({...nextPage,revision}),data:updated,error:'',loading:false});
  hydratedDetail.current=confirmed.round.id;setDetail({id:confirmed.round.id,data:confirmed,error:'',loading:false});
  select(confirmed.round.id);setNotice(message);
 }
 function apply(event:FormEvent<HTMLFormElement>){event.preventDefault();setFilters({number:number===''?undefined:Number(number)});setPage(0);}
 const data=currentList?.data,totalPages=data?Math.max(1,Math.ceil(data.totalElements/data.size)):1;
 const loading=!!source&&(!currentList||currentList.loading);
 const sourceError=location.error||currentSemester?.error;
 const sourceLoading=!location.error&&(requestedSource===undefined
  ?!currentDetail||currentDetail.loading:!currentSemester||currentSemester.loading);
 const canCreate=!!source?.selectable&&!sourceError&&!sourceLoading;

 return <div className={styles.workspace}>
  <header className={styles.heading}>
   <p className={styles.eyebrow}>Học bổng · Khuyến khích học tập</p><h1>Đợt xét học bổng KKHT</h1>
   <p>Chọn học kỳ xét để xem và chuẩn bị các đợt học bổng.</p>
  </header>
  {notice&&<p role="status">{notice}</p>}
  {forbidden?<Alert>Bạn không có quyền truy cập các đợt xét KKHT. Vui lòng kiểm tra lại quyền hoặc đăng nhập lại.</Alert>:<>
   {!creating&&!editing&&<section className={styles.content} aria-label="Học kỳ đang xét">
    {sourceLoading&&<p role="status">Đang xác định học kỳ xét…</p>}
    {sourceError&&<Alert>{sourceError}</Alert>}
    {currentSemester?.empty&&!sourceError&&<p role="status">Chưa có học kỳ phù hợp để chọn mặc định. Hãy chọn một học kỳ từ danh sách.</p>}
    {source&&!source.selectable&&<Alert>{sourceReason(source)}</Alert>}
    {(selectedId===null||!!location.error)?<SourceSemesterPicker client={client} value={source} onChange={chooseSource} disabled={false} refresh={0} onBusy={ignoreBusy} onForbidden={onForbidden}/>
     :source&&<p className={styles.semesterFilter}>Học kỳ đang xét: {source.code} · {source.academicYear} · Học kỳ {source.semesterOrder}</p>}
    <div><Button variant="secondary" disabled={sourceLoading||requestedSource===undefined||!!location.error} onClick={()=>{hydratedSource.current=null;setSourceRevision(rev=>rev+1);}}>Đọc lại kỳ đã chọn</Button></div>
   </section>}
   {editing?<EditRoundForm key={editing.round.id} client={client} initial={editing} onCancel={()=>setEditing(null)} onSaved={(view,recovered)=>confirmRound(view,recovered?'Đã đối chiếu kết quả sửa đợt.':'Đã lưu thay đổi đợt xét.')} onForbidden={onForbidden}/>:creating&&source?
    <CreateRoundForm client={client} initialSource={source} memory={creationMemories.current.get(source.sourceSemesterId)!} onCancel={()=>{setCreating(false);hydratedSource.current=null;setSourceRevision(value=>value+1);requestAnimationFrame(()=>listHeading.current?.focus());}} onCreated={(view,recovered)=>confirmRound(view,recovered?'Đã đối chiếu đợt xét hiện có.':'Đã tạo đợt xét ở trạng thái Nháp.')} onForbidden={onForbidden}/>:selectedId!==null&&!location.error?
    <section className={styles.content} aria-label="Chi tiết đợt xét">
     <div className={styles.toolbar}>
      <Button variant="secondary" onClick={()=>select(null)}>Quay lại danh sách</Button>
      <Button variant="secondary" disabled={currentDetail?.loading} onClick={()=>setRevision(value=>value+1)}>Đọc lại chi tiết</Button>
      {round?.status==='draft'&&currentDetail?.data?.scope&&source&&!mismatch&&!sourceError&&<Button onClick={()=>{setNotice('');setEditing(currentDetail.data);}}>Sửa phạm vi</Button>}
     </div>
     {(!currentDetail||currentDetail.loading)&&<p role="status">Đang tải chi tiết đợt xét…</p>}
     {currentDetail?.error&&<Alert>{currentDetail.error}</Alert>}
     {mismatch?<Alert>Đợt xét không thuộc học kỳ đã chọn. Hãy quay lại danh sách hoặc chọn đúng học kỳ.</Alert>:round&&<>
      <h2 ref={detailHeading} tabIndex={-1} className={styles.detailTitle}>{round.name}</h2>
      <RoundDetails round={round} onSemester={()=>chooseSourceId(round.sourceSemesterId)}/>
      <ScopeSummary scope={currentDetail?.data?.scope??null}/>
     </>}
    </section>:
    <section className={styles.content} aria-labelledby="round-list-title">
     <h2 id="round-list-title" ref={listHeading} tabIndex={-1}>Danh sách đợt xét</h2>
     <div className={styles.toolbar}><Button disabled={!canCreate} onClick={()=>{setNotice('');setCreating(true);}}>Tạo đợt xét</Button></div>
     <form className={styles.filters} onSubmit={apply} aria-label="Lọc đợt xét">
      <label htmlFor="round-number">Số đợt<input id="round-number" type="number" min="1" max="2147483647" step="1" value={number} onChange={event=>setNumber(event.target.value)} placeholder="Tất cả số đợt"/></label>
      <Button type="submit" variant="secondary" disabled={!source}>Lọc danh sách</Button>
      <Button variant="secondary" disabled={!source||loading} onClick={()=>setRevision(value=>value+1)}>Đọc lại danh sách</Button>
     </form>
     {loading&&<p role="status">Đang tải danh sách đợt xét…</p>}
     {currentList?.error&&<Alert>{currentList.error}</Alert>}
     {data&&<>
      <p className={styles.resultCount} role="status">{data.totalElements} đợt xét</p>
      {data.items.length===0?<p>Không có đợt xét phù hợp trong học kỳ đã chọn.</p>:<div className={styles.tableScroll} role="region" aria-label="Danh sách đợt xét KKHT" tabIndex={0}>
       <table className={styles.table}><thead><tr><th scope="col">Năm học</th><th scope="col">Học kỳ</th><th scope="col">Đợt</th><th scope="col">Tên đợt xét</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
        <tbody>{data.items.map(row=><tr key={row.id}>
         <td>{row.academicYear}</td><td><button className={styles.semesterLink} type="button" aria-label={`Lọc học kỳ ${row.semesterOrder} năm học ${row.academicYear}`} onClick={()=>chooseSourceId(row.sourceSemesterId)}>Học kỳ {row.semesterOrder}</button></td>
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
  </>}
 </div>;
}
