import {useEffect,useMemo,useRef,useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {Alert} from '../../../shared/ui/Alert';
import {dateTime,displayName} from '../presentation.mjs';
import {createDecisionApi} from './api.mjs';
import {decisionContent,meetingContent,meetingErrors,meetingForm,decisionActions,decisionLabel,shortDate,rateLabel,sameGroup,DecisionInputError,requiresDecisionRead,roundState} from './model.mjs';
import {DecisionDialog,MeetingFields,BasisDialog,DialogForm} from './DecisionDialogs';
import type {Round,SourceContext,Header,Version,Root,History,Page,Line,Candidate,MeetingForm} from './types';
import styles from './DecisionWorkspace.module.css';

type Tab='list'|'meeting'|'history';
type Modal='create'|'meeting'|'confirm'|null;
const emptyPage=<T,>():Page<T>=>({items:[],page:0,size:20,totalElements:0,hasMore:false});
function message(e:unknown){
 if(e instanceof ApiError){
  const errors:Record<string,string>={VERSION_CONFLICT:'Dữ liệu đã thay đổi. Nội dung đang nhập được giữ; đọc lại và đối chiếu trước khi gửi lại.',PRECONDITION_REQUIRED:'Chưa đọc được phiên bản mới nhất. Hãy đọc lại trước khi thao tác.',
   DECISION_NOT_READY:'Chưa đủ điều kiện lập hoặc chốt bộ xét. Kiểm tra Đóng xét, nguồn đánh giá đã xác nhận và nhóm đã được công bố ở đợt khác.',
   DECISION_ALREADY_EXISTS:'Đợt đã có bộ quyết định. Đọc lại để xem bộ hiện hành.',DECISION_CODE_CONFLICT:'Mã bộ quyết định đã tồn tại. Chọn mã khác.',MINUTES_CODE_CONFLICT:'Mã biên bản đã được sử dụng. Kiểm tra lịch sử hoặc nhập mã mới.',
   IMMUTABLE:'Phiên bản này đã khóa hoặc là bản lịch sử. Chỉ được xem, không sửa trực tiếp.',VALIDATION_ERROR:'Thông tin chưa hợp lệ. Kiểm tra các trường và thời gian họp thực tế.',
   FORBIDDEN:'Bạn không còn quyền thực hiện chức năng Hội đồng và quyết định.',UNAUTHENTICATED:'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',TOKEN_REVOKED:'Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại.',NOT_FOUND:'Không tìm thấy bộ xét hoặc phiên bản trong đợt này.',DEPENDENCY_UNAVAILABLE:'Hệ thống tạm chưa sẵn sàng. Đọc lại dữ liệu trước khi thử tiếp.'};
  return errors[e.code]??'Chưa hoàn tất thao tác. Hãy đọc lại để kiểm tra kết quả trước khi gửi lại.';
 }
 return e instanceof Error&&e.message&&!/fetch|network|timeout|abort/i.test(e.message)?e.message:'Chưa nhận được xác nhận từ hệ thống. Đọc lại để kiểm tra kết quả; không gửi lại ngay.';
}
function Pager({page,onPage,busy,label}:{page:Page<unknown>;onPage:(p:number)=>void;busy:boolean;label:string}){
 if(page.totalElements<=page.size)return null;
 return <nav className={styles.pager} aria-label={label}>
  <button className={styles.outline} disabled={busy||page.page===0} onClick={()=>onPage(page.page-1)}>Trang trước</button>
  <span>Trang {page.page+1} · {page.totalElements} mục</span>
  <button className={styles.outline} disabled={busy||!page.hasMore} onClick={()=>onPage(page.page+1)}>Trang sau</button>
 </nav>;
}
function Status({v}:{v:Header}){return <span className={styles.badge+' '+(v.status==='locked'?'':styles.pending)}>{decisionLabel(v)}</span>;}

export function DecisionWorkspace({client}:{client:AuthClient}){
 const api=useMemo(()=>createDecisionApi(client),[client]);
 const [rounds,setRounds]=useState<Round[]>([]),[roundPage,setRoundPage]=useState(0),[moreRounds,setMoreRounds]=useState(false);
 const [round,setRound]=useState<Round|null>(null),[source,setSource]=useState<SourceContext|null>(null),[items,setItems]=useState<Page<Header>>(emptyPage);
 const [root,setRoot]=useState<Root|null>(null),[version,setVersion]=useState<Version|null>(null),[tag,setTag]=useState('');
 const [lines,setLines]=useState<Page<Line>>(emptyPage),[history,setHistory]=useState<Page<History>>(emptyPage);
 const [tab,setTab]=useState<Tab>('list'),[modal,setModal]=useState<Modal>(null),[form,setForm]=useState<MeetingForm>(()=>meetingForm({})),[code,setCode]=useState('');
 const [basis,setBasis]=useState<{line:Line;candidates:Candidate[]}|null>(null),[query,setQuery]=useState('');
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[success,setSuccess]=useState(''),[needsRead,setNeedsRead]=useState(false),[denied,setDenied]=useState(false);
 const flight=useRef(false),alive=useRef(true);
 const actions=decisionActions(version,root?.head.id);
 function url(roundId?:string,versionId?:string){
  const p=new URLSearchParams();if(roundId)p.set('dot-xet',roundId);if(versionId)p.set('phien-ban',versionId);
  window.history.replaceState(null,'',window.location.pathname+(p.size?'?'+p:''));
 }
 function fail(e:unknown){
  setError(message(e));setSuccess('');
  if(e instanceof ApiError&&(e.status===401||e.status===403)){setDenied(true);setVersion(null);setRoot(null);setItems(emptyPage());setRounds([]);setModal(null);setBasis(null);}
  else if(requiresDecisionRead(e))setNeedsRead(true);
 }
 async function run(action:()=>Promise<void>){
  if(flight.current)return;flight.current=true;setBusy(true);setError('');setSuccess('');
  try{await action();}catch(e){if(alive.current)fail(e);}finally{flight.current=false;if(alive.current)setBusy(false);}
 }
 async function detail(id:string,expectedRound:Round,signal?:AbortSignal){
  const v=await api.version(id,signal),rr=await api.root(v.data.decisionId,signal);
  if(rr.data.roundId!==expectedRound.id)throw new Error('Phiên bản không thuộc đợt đang xem. Hãy chọn lại bộ xét.');
  const h=await api.history(rr.data.id,0,signal);if(!alive.current)return;
  setRoot(rr.data);setVersion(v.data);setTag(v.tag);setLines(v.data.lines);setHistory(h.data.versions);setQuery('');setNeedsRead(false);url(expectedRound.id,id);
 }
 async function selectRound(id:string,selectedVersion?:string,signal?:AbortSignal){
  setModal(null);setBasis(null);setVersion(null);setRoot(null);setSource(null);setNeedsRead(false);setQuery('');setTab('list');
  if(!id){setRound(null);setItems(emptyPage());url();return;}
  const [r,list]=await Promise.all([api.round(id,signal),api.list(id,0,signal)]);if(!alive.current)return;
  setRound(r.data);setItems(list.data);
  if(r.data.reviewClosedAt){const s=await api.source(id,signal);if(alive.current)setSource(s.data);}
  if(selectedVersion)await detail(selectedVersion,r.data,signal);else url(id);
 }
 useEffect(()=>{
  alive.current=true;const abort=new AbortController();
  void (async()=>{setBusy(true);flight.current=true;
   try{
    const r=await api.rounds(0,abort.signal);if(abort.signal.aborted)return;
    setRounds(r.data.items);setRoundPage(0);setMoreRounds(r.data.hasMore??r.data.totalElements>100);
    const params=new URLSearchParams(window.location.search),id=params.get('dot-xet'),vid=params.get('phien-ban');
    if(id&&/^[1-9]\d*$/.test(id)){await selectRound(id,vid&&/^[1-9]\d*$/.test(vid)?vid:undefined,abort.signal);}
   }catch(e){if(!abort.signal.aborted)fail(e);}finally{if(!abort.signal.aborted){flight.current=false;setBusy(false);}}
  })();
  return()=>{alive.current=false;abort.abort();};
 },[api]);
 async function readBack(){
  if(!round)return;
  const r=await api.round(round.id),list=await api.list(round.id);
  setRound(r.data);setItems(list.data);
  if(r.data.reviewClosedAt)setSource((await api.source(round.id)).data);
  if(root){const latest=await api.root(root.id);await detail(latest.data.head.id,r.data);}
  else if(list.data.items.length)await detail(list.data.items[0].id,r.data);
  setNeedsRead(false);setError('');setSuccess('Đã đọc lại dữ liệu từ hệ thống. Nội dung đang nhập được giữ; kiểm tra trước khi gửi lại.');
 }
 async function afterWrite(id:string,notice:string){
  if(!round)return;
  // Commit is already done: a failed readback never causes the command to be replayed.
  const [list,r]=await Promise.all([api.list(round.id),api.round(round.id)]);
  setItems(list.data);setRound(r.data);await detail(id,r.data);setModal(null);setBasis(null);setSuccess(notice);
 }
 async function create(){
  if(!round||!code.trim())throw new DecisionInputError('Nhập mã bộ quyết định.');
  const r=await api.create(decisionContent(round.id,code));await afterWrite(r.data.id,'Đã tạo bộ xét và đọc lại toàn bộ trạng thái từ hệ thống.');
 }
 async function saveMeeting(){
  if(!version)return;const errors=meetingErrors(form);if(errors.length)throw new DecisionInputError(errors.join(' '));
  const r=await api.meeting(version.id,meetingContent(form,version),tag);await afterWrite(r.data.id,'Đã lưu bản sau họp. Bản trước được giữ nguyên trong lịch sử.');setTab('meeting');
 }
 async function confirm(){
  if(!version)return;const r=await api.confirm(version.id,'Chuyên viên đã đối chiếu nguồn, danh sách và kết quả cuộc họp trước khi chốt.',tag);
  await afterWrite(r.data.id,'Đã chốt dữ liệu. Danh sách, phiên bản và lịch sử đã được đồng bộ.');
 }
 async function showBasis(line:Line){
  if(!version)return;const candidates=await api.candidates(version.id);
  setBasis({line,candidates:candidates.filter((c:Candidate)=>sameGroup(c,line))});
 }
 const chosenRounds=round?(rounds.some(r=>r.id===round.id)?rounds.map(r=>r.id===round.id?round:r):[round,...rounds]):rounds;
 const filteredLines=lines.items.filter(l=>([l.studentCode,l.fullName].join(' ')).toLocaleLowerCase('vi').includes(query.toLocaleLowerCase('vi')));
 const canCreate=!!round?.reviewClosedAt&&!!source&&source.eligibleCount>0&&source.blockerCount===0&&items.totalElements===0;
 const title=version?version.code:'Hội đồng và quyết định';
 return <div className={styles.page} role="region" aria-label="Hội đồng và quyết định MGHP" aria-busy={busy}>
  <div className={styles.pageHead}><div>
   {version?<button className={styles.link} disabled={busy} onClick={()=>{setVersion(null);setRoot(null);setModal(null);setBasis(null);setError('');setSuccess('');url(round?.id);}}>← Danh sách bộ xét</button>:<div className={styles.eyebrow}>Miễn giảm học phí / Hội đồng &amp; quyết định</div>}
   <h1>{title}</h1>
   <p className={styles.muted}>{version?displayName(round?.name,round?.code)+' / Bản '+version.revision+(root?.head.id!==version.id?' · Bản lịch sử, chỉ đọc':''):'Chuẩn bị danh sách, ghi kết quả cuộc họp và chốt dữ liệu trước khi ký.'}</p>
  </div>{version&&<Status v={version}/>}</div>
  {error&&<><Alert>{error}</Alert><div className={styles.notice+' '+styles.error}>{error}</div></>}
  {success&&<p role="status" className={styles.notice+' '+styles.success}>{success}</p>}
  {busy&&<p role="status">Đang đọc và xử lý dữ liệu…</p>}
  {needsRead&&!denied&&<div className={styles.notice+' '+styles.warn}><p>Chưa gửi lại thao tác. Hãy kiểm tra bản hiện hành trước khi tiếp tục.</p><button className={styles.outline} disabled={busy} onClick={()=>void run(readBack)}>Đọc lại để đối chiếu</button></div>}
  {denied?<p className={styles.notice}>Chức năng này cần quyền chuyên viên MGHP hiện hành. Vui lòng đăng nhập lại hoặc liên hệ người quản trị quyền.</p>:<>
   {!version&&<>
    <section className={styles.card} aria-label="Chọn đợt xét Hội đồng"><div className={styles.toolbar}><label>Đợt xét<select aria-label="Đợt xét" disabled={busy} value={round?.id??''} onChange={e=>void run(()=>selectRound(e.target.value))}><option value="">Chọn đợt xét</option>{chosenRounds.map(r=><option key={r.id} value={r.id}>{displayName(r.name,r.code)} · {roundState(r)}</option>)}</select></label>
     <button className={styles.outline} disabled={busy||!round} onClick={()=>void run(readBack)}>Đọc lại bộ xét</button>
     {moreRounds&&<button className={styles.outline} disabled={busy} onClick={()=>void run(async()=>{const r=await api.rounds(roundPage+1);setRounds(current=>[...current,...r.data.items.filter((x:Round)=>!current.some(c=>c.id===x.id))]);setRoundPage(roundPage+1);setMoreRounds(r.data.hasMore??(roundPage+2)*100<r.data.totalElements);})}>Thêm đợt để chọn</button>}
    </div>
     {round&&!round.reviewClosedAt&&<p className={styles.notice+' '+styles.warn}>Đợt này chưa Đóng xét. Hoàn tất rà soát và Đóng xét trước khi lập bộ xét Hội đồng. <a href="/mghp/dot-xet">Đến quản lý đợt xét</a></p>}
     {round?.reviewClosedAt&&<div className={styles.summary}>
      <div><strong>{source?.eligibleCount??'Đang đọc'}</strong><span>Đánh giá đủ căn cứ trước tổng hợp</span></div><div><strong>{source?.excludedCount??'Đang đọc'}</strong><span>Loại khỏi tập đủ điều kiện</span></div><div><strong>Đã chốt nguồn</strong><span>{dateTime(round.reviewClosedAt)} · giờ Việt Nam</span></div>
     </div>}
    </section>
    <section className={styles.card} aria-label="Bộ xét của đợt">
     {!round?<p className={styles.empty}>Chọn đợt để xem bộ xét Hội đồng và quyết định.</p>:items.totalElements===0?<><div className={styles.cardHead}><div><h2>Chuẩn bị bộ xét Hội đồng</h2><p className={styles.muted}>Lấy toàn bộ nhóm đủ căn cứ. Mỗi đợt có một bộ, các lần sửa giữ thành phiên bản.</p></div>{canCreate&&<button className={styles.primary} disabled={busy||needsRead} onClick={()=>{setCode('');setModal('create');setError('');}}>Tạo bộ xét</button>}</div>
      {!canCreate&&round.reviewClosedAt&&<p className={styles.notice+' '+styles.warn}>Chưa có tập nguồn hợp lệ để tạo bộ xét. Kiểm tra đánh giá và các vướng mắc của đợt.</p>}
      <p className={styles.notice}>Hệ thống chọn một diện có mức hưởng cao nhất cho mỗi nhóm sinh viên/quá trình học/học kỳ/phạm vi. Giữ đúng thời gian được xác minh, không cộng mức hoặc bù thời gian bằng diện thấp hơn.</p></>:
      <><h2>Bộ xét của đợt</h2><div className={styles.tableWrap}><table className={styles.table}><thead><tr><th>Bộ quyết định</th><th>Phiên bản</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{items.items.map(v=><tr key={v.id}><td><strong>{v.code}</strong><small>{displayName(round.name,round.code)}</small></td><td>Bản {v.revision}</td><td><Status v={v}/></td><td><button className={styles.outline} disabled={busy} onClick={()=>void run(()=>detail(v.id,round))}>Xem bộ xét</button></td></tr>)}</tbody></table></div><Pager page={items} busy={busy} label="Phân trang bộ xét" onPage={p=>void run(async()=>setItems((await api.list(round.id,p)).data))}/></>}
    </section>
   </>}
   {version&&root&&<>
    <ol className={styles.steps} aria-label="Tiến trình bộ xét"><li className={styles.done}><b>1</b>Nguồn đã đóng xét</li><li className={version.minutes?styles.done:styles.current}><b>2</b>Ghi cuộc họp</li><li className={version.lockedAt?styles.done:version.minutes?styles.current:''}><b>3</b>Chốt dữ liệu</li></ol>
    <section className={styles.card} aria-label="Chi tiết bộ xét">
     <div className={styles.cardHead}><div><h2>{version.status==='locked'?'Dữ liệu đã chốt':version.minutes?'Sẵn sàng kiểm tra và chốt':'Danh sách trước cuộc họp'}</h2><p className={styles.muted}>{version.status==='locked'?'Bảo toàn dữ liệu và lịch sử. Chuẩn bị PDF và ký được thực hiện tại giai đoạn tiếp theo.':version.minutes?'Xem lại danh sách và thông tin cuộc họp trước khi chốt.':'Ghi kết quả sau khi cuộc họp Hội đồng thực sự diễn ra.'}</p></div>
      <div className={styles.actions}>{actions.meeting&&<button className={version.minutes?styles.outline:styles.primary} disabled={busy||needsRead} onClick={()=>{setForm(meetingForm(version));setModal('meeting');setError('');}}>{version.minutes?'Sửa kết quả cuộc họp':'Ghi kết quả cuộc họp'}</button>}
       {actions.confirm&&<button className={styles.primary} disabled={busy||needsRead} onClick={()=>{setModal('confirm');setError('');}}>Chốt dữ liệu</button>}
      </div>
     </div>
     {version.lockedAt&&<p className={styles.notice+' '+styles.success}>Đã chốt bản {version.revision} lúc {dateTime(version.lockedAt)} (giờ Việt Nam). Không thể sửa trực tiếp. Chưa ký và chưa công bố quyết định.</p>}
     {version.id!==root.head.id&&<p className={styles.notice}>Đang xem bản lịch sử. <button className={styles.link} disabled={busy} onClick={()=>void run(()=>detail(root.head.id,round!))}>Xem bản hiện hành</button></p>}
     <div className={styles.tabs} role="tablist" aria-label="Thông tin bộ xét">{([['list','Danh sách hưởng ('+version.lineCount+')'],['meeting','Cuộc họp & văn bản'],['history','Lịch sử phiên bản']] as const).map(([key,label])=><button key={key} role="tab" aria-selected={tab===key} aria-controls={'decision-'+key} id={'decision-tab-'+key} disabled={busy} onClick={()=>setTab(key)}>{label}</button>)}</div>
     <div role="tabpanel" aria-labelledby={'decision-tab-'+tab} id={'decision-'+tab}>
      {tab==='list'&&<><div className={styles.cardHead}><div><h3>Mỗi nhóm chỉ nhận một mức cao nhất</h3><p className={styles.muted}>Khoảng hưởng lấy đúng nguồn P08 đã xác minh. Tổng số nhóm: {lines.totalElements}.</p></div><label className={styles.search}>Tìm trong trang này<input aria-label="Tìm sinh viên trong trang" type="search" placeholder="MSSV hoặc họ tên" value={query} onChange={e=>setQuery(e.target.value)}/></label></div>
       <div className={styles.tableWrap}><table className={styles.table+' '+styles.mobileTable}><thead><tr><th>Sinh viên</th><th>Diện được chọn</th><th>Mức hưởng</th><th>Thời gian được hưởng</th><th>Căn cứ</th></tr></thead><tbody>{filteredLines.map(l=><tr key={l.id}><td><strong>{l.fullName??'Chưa có họ tên'}</strong><small>{l.studentCode??'Chưa có MSSV'} · {l.classCode??'Chưa có lớp'}</small></td><td data-label="Diện được chọn">{l.policyName??'Diện đã xác minh'}</td><td data-label="Mức hưởng"><strong>{rateLabel(l.rate)}</strong></td><td data-label="Thời gian hưởng">{l.periods.map(p=><div key={p.id}>{shortDate(p.from)} – {shortDate(p.to)}</div>)}</td><td data-label="Căn cứ"><button className={styles.link} disabled={busy} onClick={()=>void run(()=>showBasis(l))}>Xem căn cứ</button></td></tr>)}{!filteredLines.length&&<tr><td colSpan={5} className={styles.empty}>Không có sinh viên phù hợp trong trang này.</td></tr>}</tbody></table></div>
       <Pager page={lines} busy={busy} label="Phân trang danh sách hưởng" onPage={p=>void run(async()=>{setLines((await api.lines(version.id,p)).data);setQuery('');})}/>
      </>}
      {tab==='meeting'&&(version.minutes?<><div className={styles.details}><div><h3>Cuộc họp Hội đồng</h3><dl><dt>Thời gian thực tế · giờ Việt Nam</dt><dd>{dateTime(version.minutes.meetingAt)}</dd><dt>Địa điểm</dt><dd>{version.minutes.place??'Chưa ghi'}</dd><dt>Kết luận / căn cứ</dt><dd>{version.reason??'Chưa ghi'}</dd></dl></div><div><h3>Thông tin văn bản</h3><dl><dt>Mã biên bản</dt><dd>{version.minutes.code}</dd>{version.minutes.reference&&<><dt>Tham chiếu biên bản</dt><dd>{version.minutes.reference}</dd></>}<dt>Số quyết định</dt><dd>{version.number??'Chưa ghi'}</dd><dt>Ngày quyết định</dt><dd>{shortDate(version.date)}</dd></dl></div></div>{version.documents.length?<p className={styles.notice}>Có {version.documents.length} liên kết tài liệu của đúng phiên bản này. Chức năng PDF và ký sẽ được cung cấp tại bước tiếp theo.</p>:<p className={styles.notice}>Chưa có PDF được gắn vào bản này. PDF được soạn ngoài; upload và ký thuộc bước tiếp theo.</p>}</>:<div className={styles.empty}><h3>Chưa ghi kết quả cuộc họp</h3><p>Cuộc họp thực hiện bên ngoài hệ thống. Sau cuộc họp, chuyên viên ghi thời gian thực tế và kết luận tại đây.</p></div>)}
      {tab==='history'&&<><p className={styles.muted}>Mọi phiên bản cùng thuộc một bộ. Xem bản cũ không thay đổi dữ liệu hiện hành.</p>{history.items.map(v=><div className={styles.versionRow} key={v.id}><span className={styles.dot}/><div className={styles.grow}><strong>Bản {v.revision}{v.id===root.head.id?' · Hiện hành':''}</strong> <Status v={v}/><p>{v.reason??'Lập danh sách từ nguồn đã đóng xét'}</p><small>Ghi nhận: {dateTime(v.createdAt)} · giờ Việt Nam</small>{v.lockedAt&&<small>Đã chốt: {dateTime(v.lockedAt)}</small>}</div><button className={styles.outline} disabled={busy} onClick={()=>void run(async()=>{await detail(v.id,round!);setTab('list');})}>Xem bản {v.revision}</button></div>)}<Pager page={history} busy={busy} label="Phân trang lịch sử" onPage={p=>void run(async()=>setHistory((await api.history(root.id,p)).data.versions))}/></>}
     </div>
    </section>
   </>}
  </>}
  {modal&&<DecisionDialog title={modal==='create'?'Tạo bộ xét Hội đồng':modal==='meeting'?(version?.minutes?'Sửa kết quả cuộc họp':'Ghi kết quả cuộc họp'):'Chốt dữ liệu bộ xét?'} busy={busy} onClose={()=>setModal(null)}>
   {error&&<p role="alert" className={styles.notice+' '+styles.error}>{error}</p>}
   {needsRead&&<><p className={styles.notice+' '+styles.warn}>Giữ nguyên nội dung đang nhập. Đọc lại bản hiện hành và đối chiếu trước khi gửi lại.</p><button className={styles.outline} disabled={busy} onClick={()=>void run(readBack)}>Đọc lại để đối chiếu</button></>}
   {modal==='create'&&<DialogForm onSubmit={e=>{e.preventDefault();void run(create);}}><p className={styles.muted}>Lấy toàn bộ nhóm đủ căn cứ của đợt. Đây chưa phải quyết định đã ban hành.</p><div className={styles.fieldGrid}><label className={styles.full}>Mã bộ quyết định<input autoFocus aria-label="Mã bộ quyết định" required maxLength={128} disabled={busy} value={code} onChange={e=>setCode(e.target.value)}/><small>Mã để tra cứu nội bộ; số và ngày quyết định có thể ghi sau cuộc họp.</small></label></div><div className={styles.actions}><button type="button" className={styles.secondary} disabled={busy} onClick={()=>setModal(null)}>Quay lại</button><button type="submit" className={styles.primary} disabled={busy||needsRead||items.totalElements>0}>Tạo bộ xét</button></div></DialogForm>}
   {modal==='meeting'&&<DialogForm onSubmit={e=>{e.preventDefault();void run(saveMeeting);}}><p className={styles.muted}>Tạo bản sau họp mới khi nội dung thay đổi, giữ nguyên bản trước. Toàn bộ nhóm đã chốt nguồn được giữ trong danh sách.</p><MeetingFields form={form} onChange={next=>{setForm(next);if(!needsRead)setError('');}} busy={busy}/>{!actions.meeting&&<p className={styles.notice}>Bản hiện hành đã được khóa; không gửi lại thay đổi vào bản này.</p>}<div className={styles.actions}><button type="button" className={styles.secondary} disabled={busy} onClick={()=>setModal(null)}>Quay lại</button><button type="submit" className={styles.primary} disabled={busy||needsRead||!actions.meeting}>Lưu bản sau họp</button></div></DialogForm>}
   {modal==='confirm'&&<><p>Bản {version?.revision} có <strong>{version?.lineCount} nhóm hưởng</strong> và đã ghi cuộc họp Hội đồng. Hệ thống sẽ kiểm lại nguồn, khóa bản này và ghi người chốt, thời gian chốt.</p><p className={styles.notice+' '+styles.warn}>Sau khi chốt không thể sửa trực tiếp. Thao tác này chưa ký hoặc công bố quyết định.</p><div className={styles.actions}><button className={styles.secondary} disabled={busy} onClick={()=>setModal(null)}>Kiểm tra lại</button><button className={styles.primary} disabled={busy||needsRead||!actions.confirm} onClick={()=>void run(confirm)}>Xác nhận chốt dữ liệu</button></div></>}
  </DecisionDialog>}
  {basis&&<BasisDialog line={basis.line} candidates={basis.candidates} busy={busy} onClose={()=>setBasis(null)}/>}
 </div>;
}

