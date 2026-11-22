import {useCallback,useEffect,useState} from 'react';
import type {RoundsApi} from '../api/rounds';
import type {Round,RoundInput,RoundResult,RoundPage,Semester} from '../types/rounds';
import {roundInput,roundWritable,roundStates,roundDate,validateRound,sameRound,roundError} from '../model/round.mjs';
import {instant,vietnamInput} from '../model/draft.mjs';
import {AsyncChoice} from './AsyncChoice';
import {formatTime} from './Controls';
import {Alert} from '../../../shared/ui/Alert';
import {Button} from '../../../shared/ui/Button';
import styles from '../pages/Rounds.module.css';
const semesterKey=(s:Semester)=>s.ma;
export const semesterLabel=(s:Semester)=>s.ten?`${s.ten} · ${s.ma}`:s.ma;
export function RoundEditor({api,initial,canPrepare,back,saved,blocked}:{api:RoundsApi;initial:Round|null;canPrepare:boolean;back:()=>void;saved:(result:RoundResult<Round>)=>void;blocked:()=>void}){
 const [base,setBase]=useState(initial),[draft,setDraft]=useState<RoundInput>(()=>roundInput(initial)),[editing,setEditing]=useState(!initial);
 const [invalidFields,setInvalidFields]=useState<string[]>([]),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[uncertain,setUncertain]=useState(false),[remote,setRemote]=useState<RoundResult<Round>|null>(null),[matches,setMatches]=useState<RoundPage|null>(null),[semester,setSemester]=useState<Semester|null>(null);
 const writable=canPrepare&&(!base||roundWritable(base));const dirty=!sameRound(draft,roundInput(base));
 useEffect(()=>{const warn=(e:BeforeUnloadEvent)=>{if(dirty||busy)e.preventDefault();};const leave=(e:Event)=>{if((dirty||busy)&&!window.confirm('Có thay đổi chưa lưu. Bạn muốn rời trang?'))e.preventDefault();};window.addEventListener('beforeunload',warn);window.addEventListener('shcn-leave',leave);return()=>{window.removeEventListener('beforeunload',warn);window.removeEventListener('shcn-leave',leave);};},[dirty,busy]);
 const loadSemesters=useCallback((q:string,p:number,s:AbortSignal)=>api.semesters(q,p,s),[api]);
 const lookupFailure=useCallback((e:unknown)=>setError(roundError(e)),[]);
 useEffect(()=>{if(!base)return;const controller=new AbortController();void api.semesters(base.maHocKy,0,controller.signal).then(p=>{if(!controller.signal.aborted)setSemester(p.items.find(s=>s.ma===base.maHocKy)??null);}).catch(()=>{});return()=>controller.abort();},[api,base?.maHocKy]);
 function change<K extends keyof RoundInput>(key:K,value:RoundInput[K]){setDraft(d=>({...d,[key]:value}));setNotice('');}
 function leave(){if(!dirty||window.confirm('Bỏ thay đổi chưa lưu và về danh sách đợt?'))back();}
 async function submit(){
  setError('');setNotice('');setInvalidFields([]);let input:RoundInput;try{input=validateRound(draft);}catch(e){setError(roundError(e));return;}
  setBusy(true);try{const result=base?await api.update(base.id,{...input,expectedVersion:base.version}):await api.create(input);setBase(result.value);setDraft(roundInput(result.value));setEditing(false);setUncertain(false);setNotice(base?'Đã lưu thay đổi.':'Đã tạo đợt nháp.');saved(result);}
  catch(e){const err=e as {status?:number;code?:string;fields?:string[]};setInvalidFields(err.fields??[]);setError(roundError(e));if(err.status===403||base&&err.status===404){blocked();return;}if(!err.status||err.status>=500||err.status===409)setUncertain(true);}
  finally{setBusy(false);}
 }
 async function readServer(){
  setBusy(true);try{if(base){const result=await api.detail(base.id);setRemote(result);if(sameRound(result.value,draft))setNotice('Bản hệ thống đã khớp nội dung đang nhập. Hãy chọn dùng bản hệ thống để tiếp tục.');}else setMatches((await api.list({maHocKy:draft.maHocKy,q:draft.tenDot||undefined})).value);}
  catch(e){setError(roundError(e));if((e as {status?:number}).status===403)blocked();}finally{setBusy(false);}
 }
 function useRemote(){if(remote&&window.confirm('Dùng bản hệ thống và bỏ nội dung đang sửa?')){setBase(remote.value);setDraft(roundInput(remote.value));setUncertain(false);setRemote(null);setError('');setEditing(false);saved(remote);}}
 const state=base?roundStates[base.trangThai as keyof typeof roundStates]??base.trangThai:'Chưa lưu';
 return <section className={styles.workspace}><Button variant="secondary" disabled={busy} onClick={leave}>← Danh sách đợt</Button>
  <div className={styles.heading}><h1>{base?base.tenDot:'Tạo đợt SHCN'}</h1><span className={styles.badge}>{state}</span></div>
  {base&&<p>Cập nhật {formatTime(base.ngayCapNhat)}{dirty?' · Có thay đổi chưa lưu':''}</p>}
  {!editing?<>
   <dl className={styles.details}><dt>Học kỳ</dt><dd>{semester?semesterLabel(semester):base?.maHocKy}</dd><dt>Năm học</dt><dd>{semester?.namHoc??'Chưa có năm học nguồn'}</dd><dt>Khung ngày</dt><dd>{roundDate(base?.khungNgayBatDau)} → {roundDate(base?.khungNgayKetThuc)}</dd><dt>Khung tiết</dt><dd>{base?.khungTietChoPhep.map((r,i)=><span key={i}>{i?', ':''}{r.tietDau}–{r.tietCuoi}</span>)}</dd><dt>Hạn chốt lịch</dt><dd>{formatTime(base?.hanChotDangKyLich??null)}</dd><dt>Phân công</dt><dd>{base?.trangThaiPhanCong==='CHUA_DONG_BO'?'Chưa đồng bộ':base?.trangThaiPhanCong}</dd></dl>
   {base?.trangThai!=='DA_PHAT_HANH'&&<p>Đợt chưa phát hành; chưa mở chốt lịch cho GVCN.</p>}
   {writable?<Button onClick={()=>setEditing(true)}>Sửa thông tin</Button>:canPrepare&&<p>Trạng thái hiện tại không cho phép sửa thông tin chung.</p>}
  </>:<form onSubmit={e=>{e.preventDefault();void submit();}}><fieldset disabled={busy||uncertain||!writable}>
   <legend>Thông tin chung</legend><label>Tên đợt *<input required aria-invalid={invalidFields.includes('tenDot')} aria-describedby={invalidFields.includes('tenDot')?'round-name-error':undefined} maxLength={200} value={draft.tenDot} onChange={e=>change('tenDot',e.target.value)}/></label>{invalidFields.includes('tenDot')&&<small id="round-name-error">Kiểm tra tên đợt theo thông báo lỗi.</small>}
   <AsyncChoice label="Học kỳ *" value={semester&&semester.ma===draft.maHocKy?semesterLabel(semester):draft.maHocKy} load={loadSemesters} itemKey={semesterKey} itemLabel={semesterLabel} onFailure={lookupFailure} disabled={busy||uncertain||!writable} onChoose={s=>{setSemester(s);change('maHocKy',s.ma);}}/>
   <p>Năm học: {semester?.ma===draft.maHocKy?semester?.namHoc??'Chưa có năm học nguồn':'Chưa có năm học nguồn'}</p>
   <div className={styles.grid}><label>Ngày bắt đầu *<input required type="date" max="9999-12-31" value={draft.khungNgayBatDau} onChange={e=>change('khungNgayBatDau',e.target.value)}/></label><label>Ngày kết thúc *<input required type="date" max="9999-12-31" value={draft.khungNgayKetThuc} onChange={e=>change('khungNgayKetThuc',e.target.value)}/></label></div>
   <h2>Khung tiết cho phép</h2>{draft.khungTietChoPhep.map((r,i)=><div key={i} className={styles.period}><label>Tiết đầu {i+1}<input required aria-label={'Tiết đầu '+(i+1)} type="number" min={1} max={32767} step={1} value={r.tietDau||''} onChange={e=>change('khungTietChoPhep',draft.khungTietChoPhep.map((v,n)=>n===i?{...v,tietDau:Number(e.target.value)}:v))}/></label><label>Tiết cuối {i+1}<input required aria-label={'Tiết cuối '+(i+1)} type="number" min={1} max={32767} step={1} value={r.tietCuoi||''} onChange={e=>change('khungTietChoPhep',draft.khungTietChoPhep.map((v,n)=>n===i?{...v,tietCuoi:Number(e.target.value)}:v))}/></label><Button variant="secondary" disabled={draft.khungTietChoPhep.length===1} onClick={()=>change('khungTietChoPhep',draft.khungTietChoPhep.filter((_,n)=>i!==n))}>Bỏ khoảng {i+1}</Button></div>)}
   <Button variant="secondary" disabled={draft.khungTietChoPhep.length>=100} onClick={()=>change('khungTietChoPhep',[...draft.khungTietChoPhep,{tietDau:0,tietCuoi:0}])}>Thêm khoảng tiết</Button>
   <label>Hạn chốt lịch (giờ Việt Nam) *<input required type="datetime-local" step="1" value={vietnamInput(draft.hanChotDangKyLich)} onChange={e=>change('hanChotDangKyLich',instant(e.target.value)??'')}/></label>
  </fieldset>{!uncertain&&<div className={styles.actions}><Button variant="secondary" disabled={busy} onClick={()=>{if(!dirty||window.confirm('Bỏ các thay đổi chưa lưu?')){if(base){setDraft(roundInput(base));setEditing(false);}else leave();}}}>{base?'Bỏ thay đổi':'Quay lại'}</Button><Button type="submit" disabled={busy||!writable||!!base&&!dirty}>{busy?'Đang lưu…':base?'Lưu thay đổi':'Lưu nháp'}</Button></div>}</form>}
  {error&&<Alert>{error}</Alert>}{notice&&<p role="status">{notice}</p>}
  {uncertain&&<section className={styles.warning}><h2>{base?'Đối chiếu kết quả lưu':'Chưa xác định kết quả tạo'}</h2><p>Không tự gửi lại yêu cầu. Đọc dữ liệu hệ thống trước khi tiếp tục.</p><Button variant="secondary" disabled={busy} onClick={()=>void readServer()}>{base?'Đọc bản mới':'Đối chiếu danh sách'}</Button>
   {remote&&<><div className={styles.table}><table><thead><tr><th>Thông tin</th><th>Đang nhập</th><th>Trên hệ thống</th></tr></thead><tbody>{(['tenDot','maHocKy','khungNgayBatDau','khungNgayKetThuc','khungTietChoPhep','hanChotDangKyLich'] as const).map(key=><tr key={key}><td>{({tenDot:'Tên đợt',maHocKy:'Học kỳ',khungNgayBatDau:'Ngày bắt đầu',khungNgayKetThuc:'Ngày kết thúc',khungTietChoPhep:'Khung tiết',hanChotDangKyLich:'Hạn chốt'})[key]}</td><td>{JSON.stringify(draft[key])}</td><td>{JSON.stringify(remote.value[key])}</td></tr>)}</tbody></table></div><Button variant="secondary" disabled={busy} onClick={useRemote}>Dùng bản hệ thống</Button></>}
   {matches&&<><p>{matches.totalElements} đợt phù hợp bộ lọc; trùng tên không chứng minh cùng một lần tạo.</p>{matches.items.map(d=><p key={d.id}>{d.tenDot} · {d.id} · {formatTime(d.ngayTao)}</p>)}<Button variant="secondary" onClick={()=>{if(window.confirm('Đã kiểm tra kết quả và muốn chủ động cho phép tạo lại?')){setUncertain(false);setMatches(null);}}}>Đã đối chiếu, cho phép tạo lại</Button></>}
  </section>}
 </section>;
}
