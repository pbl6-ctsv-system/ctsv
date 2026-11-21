import {assignmentError} from '../api/errors';
import {useRef,useState} from 'react';
import type {AssignmentsApi} from '../api/assignments';
import type {Draft,Page,Summary} from '../types/assignments';
import {inheritedStatuses,statusLabels,previousSchoolYear,inheritanceYearError} from '../model/draft.mjs';
import {YearCombobox} from './YearCombobox';
import {Modal,TimeFields,Pager} from './Controls';
import {Button} from '../../../shared/ui/Button';
import {Alert} from '../../../shared/ui/Alert';
export function CreateDraft({api,faculty,year:initialYear,close,created}:{api:AssignmentsApi;faculty:string;year:string;close:()=>void;created:(d:Draft)=>void}){
 const[year,setYear]=useState(initialYear);
 const[sourceYear,setSourceYear]=useState(()=>previousSchoolYear(year)),[sources,setSources]=useState<Page<Summary>|null>(null),[source,setSource]=useState<string|null>(null),[inherit,setInherit]=useState(false);
 const[from,setFrom]=useState(''),[until,setUntil]=useState<string|null>(null),[reason,setReason]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const sourceRequest=useRef(0);
 const[uncertain,setUncertain]=useState<{version:number;message:string}|null>(null);
 async function load(page=0){setError('');const validation=inheritanceYearError(year,sourceYear);if(validation){setError(validation);return;}const generation=++sourceRequest.current;try{const result=await api.versions(faculty,sourceYear,page);if(generation===sourceRequest.current)setSources(result);}catch{if(generation===sourceRequest.current)setError('Chưa tải được phân công năm trước.');}}
 async function create(){setBusy(true);setError('');let version=0;try{
  if(!year||year.trim()!==year||year.length>20)throw new Error('Nhập năm học hợp lệ trước khi tạo phân công.');
  if(!from)throw new Error('Chọn thời gian bắt đầu.');if(until&&Date.parse(until)<=Date.parse(from))throw new Error('Mốc kết thúc phải sau bắt đầu.');
  if(inherit&&inheritanceYearError(year,sourceYear))throw new Error(inheritanceYearError(year,sourceYear));
  if(inherit&&!source)throw new Error('Chọn bản nguồn để kế thừa.');
  const latest=await api.versions(faculty,year);version=(latest.phienBanLonNhat??latest.items[0]?.phienBan??0)+1;
  const result=await api.create({maKhoa:faculty,namHoc:year,phienBanDuKien:version,maDanhSachTruoc:inherit?source:null,hieuLucTu:from,hieuLucDen:until,lyDoDieuChinh:reason||null});created(result);
 }catch(e){const err=e as Error&{status?:number};setError(assignmentError(err));if(version&&(!err.status||err.status>=500||err.status===409))setUncertain({version,message:'Cần đối soát phiên bản trước khi tạo tiếp.'});}finally{setBusy(false);}}
 async function reconcile(){if(!uncertain)return;setBusy(true);try{const found=await api.versions(faculty,year,0,uncertain.version);if(found.items[0]){if(window.confirm('Đã có phiên bản này trên hệ thống; có thể do người khác tạo. Mở để đối chiếu?'))created(await api.detail(found.items[0].id));}else{setUncertain(null);setError('Chưa thấy bản đã tạo. Bạn có thể kiểm tra thông tin rồi chủ động tạo lại.');}}catch{setError('Chưa đối soát được kết quả. Không gửi lại yêu cầu tạo lúc này.');}finally{setBusy(false);}}
 return <Modal title="Tạo phân công GVCN" close={()=>{if(!busy)close();}}><p>Khoa {faculty}</p><form onSubmit={e=>{e.preventDefault();void create();}}><fieldset disabled={busy}>
 <YearCombobox label="Năm học" value={year} required disabled={!!uncertain} known={[initialYear]} onChange={value=>{setYear(value);++sourceRequest.current;setSource(null);setSources(null);setSourceYear(previousSchoolYear(value));}}/>
 <label>Cách tạo<select value={inherit?'inherit':'empty'} onChange={e=>{setInherit(e.target.value==='inherit');setSource(null);}}><option value="empty">Tạo phân công mới</option><option value="inherit">Kế thừa phân công năm trước</option></select></label>
 {inherit&&<div><p>Kế thừa phân công của năm trước để lập cho năm {year}. Chỉ chọn được bản đã ký, đã gửi chuyên viên hoặc đã thay thế.</p><YearCombobox label="Năm học trước" value={sourceYear} known={[previousSchoolYear(year)]} onChange={value=>{++sourceRequest.current;setSourceYear(value);setSource(null);setSources(null);}}/><Button variant="secondary" onClick={()=>void load()}>Tìm phân công năm trước</Button>{sources?.items.length===0&&<p>Chưa có phân công cho năm học trước đã chọn.</p>}{sources?.items.map(s=><label key={s.id}><input type="radio" name="source" checked={source===s.id} disabled={!inheritedStatuses.has(s.trangThai)} onChange={()=>setSource(s.id)}/> {s.namHoc} · Phiên bản {s.phienBan} · {statusLabels[s.trangThai as keyof typeof statusLabels]??s.trangThai} · {s.soLop} lớp</label>)}{sources&&<Pager page={sources.page} total={sources.totalElements} change={p=>void load(p)}/>}</div>}
 <TimeFields from={from} until={until} change={(k,v)=>k==='hieuLucTu'?setFrom(v??''):setUntil(v)}/><label>Lý do điều chỉnh/kế thừa (không bắt buộc)<textarea maxLength={4000} value={reason} onChange={e=>setReason(e.target.value)}/></label>
 <p>Sau khi tạo, bạn tiếp tục phân GVCN cho các lớp và chọn Lưu nháp nếu chưa muốn gửi chuyên viên. Phân công chưa có hiệu lực khi đang ở trạng thái nháp.</p>
 {error&&<Alert>{error}</Alert>}{uncertain&&<p>{uncertain.message} <Button variant="secondary" onClick={()=>void reconcile()}>Đối soát kết quả tạo</Button></p>}
 <Button type="submit" disabled={busy||!!uncertain}>{busy?'Đang tạo…':'Tạo phân công GVCN'}</Button></fieldset></form></Modal>;
}
