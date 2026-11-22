import {useCallback,useEffect,useState} from 'react';
import type {AssignmentsApi} from '../api/assignments';
import type {Assignment,Lecturer,Page,Program,SchoolClass} from '../types/assignments';
import {assignmentError} from '../api/errors';
import {AsyncChoice} from './AsyncChoice';
import {Pager} from './Controls';
import {Button} from '../../../shared/ui/Button';
import styles from '../pages/Assignments.module.css';

const programKey=(p:Program)=>p.id;
const programLabel=(p:Program)=>`${p.tenNganhMoi||p.ten} · ${p.ma}${p.tenNganhMoi&&p.ten!==p.tenNganhMoi?' — '+p.ten:''}`;
const lecturerKey=(g:Lecturer)=>g.idCanBo;
const lecturerLabel=(g:Lecturer)=>`${g.hoTen} · ${g.maGiaoVien}`;
const lecturerUnavailable=(g:Lecturer)=>g.coTheChon?null:g.lyDoKhongTheChon||'Không thể chọn giảng viên này.';

export function IndustryAssignments({api,faculty,rows,writable,disabled,showReview,onChange,onFailure}:{
 api:AssignmentsApi;faculty:string;rows:Assignment[];writable:boolean;disabled:boolean;showReview:boolean;
 onChange:(rows:Assignment[])=>void;onFailure:(error:unknown)=>void;
}){
 const [program,setProgram]=useState<Program|null>(null),[query,setQuery]=useState(''),[page,setPage]=useState(0);
 const [classes,setClasses]=useState<Page<SchoolClass>|null>(null),[loading,setLoading]=useState(false),[error,setError]=useState(''),[retry,setRetry]=useState(0);
 const [review,setReview]=useState(!writable);
 const loadPrograms=useCallback((q:string,p:number,s:AbortSignal)=>api.programs(faculty,q,p,s),[api,faculty]);
 const loadLecturers=useCallback((q:string,p:number,s:AbortSignal)=>api.lecturers(faculty,q,p,s),[api,faculty]);
 useEffect(()=>{if(showReview)setReview(true);},[showReview]);
 useEffect(()=>{
  if(!program||!writable)return;
  const controller=new AbortController();setLoading(true);setClasses(null);setError('');
  const timer=window.setTimeout(()=>{void api.classes(faculty,query,page,program.id,controller.signal).then(result=>{
   if(!controller.signal.aborted){setClasses(result);setLoading(false);}
  }).catch(e=>{if(!controller.signal.aborted){setError(assignmentError(e));setLoading(false);onFailure(e);}});},180);
  return()=>{window.clearTimeout(timer);controller.abort();};
 },[api,faculty,program,query,page,retry,writable,onFailure]);
 const assigned=new Map(rows.map(r=>[r.maLop,r]));
 function setTeacher(schoolClass:SchoolClass,teacher:Lecturer){
  const current=assigned.get(schoolClass.maLop);
  const updated={...current,maLop:schoolClass.maLop,maGiaoVien:teacher.maGiaoVien,ghiChu:current?.ghiChu??null,lopHienTai:schoolClass,giangVienHienTai:teacher};
  onChange(current?rows.map(r=>r.maLop===schoolClass.maLop?updated:r):[...rows,updated]);
 }
 function note(code:string,value:string){onChange(rows.map(r=>r.maLop===code?{...r,ghiChu:value}:r));}
 function remove(code:string){if(window.confirm('Bỏ phân công lớp '+code+' khi lưu nháp?'))onChange(rows.filter(r=>r.maLop!==code));}
 function teacherPicker(row:Assignment|undefined,schoolClass:SchoolClass,label:string){return <AsyncChoice label={label} value={row?(row.giangVienHienTai?lecturerLabel(row.giangVienHienTai):row.maGiaoVien):''}
  load={loadLecturers} itemKey={lecturerKey} itemLabel={lecturerLabel} unavailable={lecturerUnavailable} disabled={disabled||!schoolClass.coTheChon}
  onChoose={g=>setTeacher(schoolClass,g)} onFailure={onFailure}/>;}
 function warnings(row:Assignment){return <>{!row.lopHienTai?.coTheChon&&<small className={styles.referenceWarning}>Kiểm tra lại tham chiếu lớp {row.maLop}.</small>}{!row.giangVienHienTai?.coTheChon&&<small className={styles.referenceWarning}>Kiểm tra lại giảng viên {row.maGiaoVien}.</small>}</>;}
 return <section aria-label="Phân công theo ngành" className={styles.industrySection}>
  <h2>{writable?'Phân công theo ngành':'Danh sách phân công'}</h2>
  {writable&&<>
   <div className={styles.industryFilters}>
    <AsyncChoice label="Ngành" value={program?programLabel(program):''} load={loadPrograms} itemKey={programKey} itemLabel={programLabel} disabled={disabled}
     onChoose={p=>{setProgram(p);setPage(0);setQuery('');setClasses(null);}} onFailure={onFailure}/>
    <label>Tìm lớp<input value={query} disabled={!program||disabled} placeholder="Mã hoặc tên lớp" onChange={e=>{setQuery(e.target.value);setPage(0);setClasses(null);}}/></label>
   </div>
   {!program?<p>Chọn ngành để xem các lớp và phân công GVCN.</p>:<>
    <h3>Lớp thuộc ngành đang chọn</h3><p className={styles.muted}>{programLabel(program)}</p>
    {loading&&<p role="status">Đang tải lớp…</p>}
    {error&&<div role="alert"><p>{error}</p><Button variant="secondary" onClick={()=>setRetry(v=>v+1)}>Tải lại lớp</Button></div>}
    {classes&&<><p>{classes.totalElements} lớp thuộc bộ lọc.</p>
     <div className={styles.table}><table className={styles.classAssignments}><thead><tr><th>Mã lớp</th><th>Tên lớp</th><th>GVCN</th><th>Ghi chú</th><th>Thao tác</th></tr></thead>
      <tbody>{classes.items.map(c=>{const row=assigned.get(c.maLop);return <tr key={c.maLop}>
       <td>{c.maLop}</td><td><strong>{c.tenLop}</strong>{!c.coTheChon&&<small className={styles.referenceWarning}>{c.lyDoKhongTheChon||'Không thể phân công mới cho lớp này.'}</small>}</td>
       <td>{teacherPicker(row,c,'GVCN lớp '+c.maLop)}{row&&warnings(row)}</td>
       <td><textarea aria-label={'Ghi chú '+c.maLop} disabled={disabled||!row} maxLength={4000} value={row?.ghiChu??''} placeholder={row?'Ghi chú (tùy chọn)':'Chọn GVCN trước'} onChange={e=>note(c.maLop,e.target.value)}/></td>
       <td>{row?<Button variant="danger" disabled={disabled} onClick={()=>remove(c.maLop)}>Bỏ phân công</Button>:<span className={styles.muted}>Chưa phân công</span>}</td>
      </tr>;})}</tbody></table></div>
     {!classes.items.length&&<p>Không có lớp phù hợp. Các phân công đã soạn vẫn được giữ.</p>}
     <Pager page={page} total={classes.totalElements} change={p=>{setPage(p);setClasses(null);}}/>
    </>}
   </>}
  </>}
  <div className={styles.heading}><p aria-live="polite">Phân công đang soạn: <strong>{rows.length}</strong> lớp trên toàn bộ danh sách.</p>
   {writable&&<Button variant="secondary" aria-expanded={review} onClick={()=>setReview(v=>!v)}>{review?'Thu gọn phân công':'Xem toàn bộ phân công'}</Button>}
  </div>
  {review&&<section aria-label="Toàn bộ phân công"><h3>Toàn bộ phân công</h3>
   {!rows.length?<p>Chưa có lớp được phân công. Nháp có thể được lưu trống.</p>:<div className={styles.table}><table><thead><tr><th>Lớp</th><th>Ngành/chương trình</th><th>GVCN</th><th>Ghi chú</th>{writable&&<th>Thao tác</th>}</tr></thead>
    <tbody>{rows.map(row=><tr key={row.maLop}><td><strong>{row.lopHienTai?.tenLop||'Chưa đối chiếu được lớp'}</strong><small>{row.maLop}</small>{warnings(row)}</td>
     <td>{row.lopHienTai?.chuongTrinh?programLabel(row.lopHienTai.chuongTrinh):'Chưa có thông tin ngành/chương trình'}</td>
     <td>{writable&&row.lopHienTai?.coTheChon?teacherPicker(row,row.lopHienTai,'Đổi GVCN lớp '+row.maLop):<>{row.giangVienHienTai?.hoTen||'Chưa đối chiếu được giảng viên'}<small>{row.maGiaoVien}</small></>}</td>
     <td>{writable?<textarea aria-label={'Ghi chú rà soát '+row.maLop} disabled={disabled} maxLength={4000} value={row.ghiChu??''} onChange={e=>note(row.maLop,e.target.value)}/>:row.ghiChu||'—'}</td>
     {writable&&<td><Button variant="danger" disabled={disabled} onClick={()=>remove(row.maLop)}>Bỏ phân công</Button></td>}
    </tr>)}</tbody></table></div>}
  </section>}
 </section>;
}
