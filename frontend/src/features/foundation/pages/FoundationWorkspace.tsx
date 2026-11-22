import {useRef,useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {useRecordApi} from '../../../shared/hooks/useRecordApi';
import {RecordFields} from '../../../shared/ui/RecordFields';
import {RecordView} from '../../../shared/ui/RecordView';
import {DirectoryPicker} from '../../../shared/ui/DirectoryPicker';
import {Alert} from '../../../shared/ui/Alert';
import {FOUNDATION_API as F} from '../api/foundation-api';
import {fieldLabels} from '../types/fieldLabels';
import styles from './FoundationWorkspace.module.css';

export default function FoundationWorkspace({client,view}:{client:AuthClient;view:string}){
 const api=useRecordApi(client);
 const rolesOnly=view==='roles',editor=rolesOnly||view==='manage';
 const[kind,setKind]=useState('semesters'),[q,setQ]=useState(''),[id,setId]=useState(''),[role,setRole]=useState(''),[selectedType,setType]=useState('semester');
 const type=rolesOnly?'roles':selectedType;
 const[edit,setEdit]=useState(''),[mode,setMode]=useState('main');
 const [targetLabel,setTargetLabel]=useState(''),[roleLabel,setRoleLabel]=useState('');
 const [studentProcesses,setStudentProcesses]=useState<Record<string,any>[]>([]),[studentChoice,setStudentChoice]=useState('');
 const studentRequest=useRef(0);
 function chooseId(value:string){setId(value);setEdit('');setRole('');setRoleLabel('');api.clearRecord();}
 async function chooseStudent(row:Record<string,any>|null){const ticket=++studentRequest.current;setStudentChoice(row?.id??'');setStudentProcesses([]);chooseId('');if(row){const response=await client.request(F+'/sinh-vien/'+row.id);const value=await response.json();if(ticket===studentRequest.current)setStudentProcesses(value.studyProcesses);}}
 const root=type==='semester'?F+'/hoc-ky/'+id:type==='process'?F+'/qua-trinh-hoc/'+id+'/lich-su':F+'/nguoi-dung/'+id+'/vai-tro-mghp';
 async function read(){setMode('main');await api.run(async()=>{
  const value=await api.get(root);
  if(type==='semester')setEdit(JSON.stringify({order:value.order??1,startDate:value.startDate??'',endDate:value.endDate??'',evidence:value.evidence??''}));
  if(type==='process')setEdit(JSON.stringify(Object.fromEntries(['programId','cohortId','startSemesterId','endSemesterId','actualStart','expectedEnd','actualEnd','evidence','evidenceFileId'].map(key=>[key,value[key]??null]))));
 });}
 function chooseMode(next:string){setMode(next);setEdit(JSON.stringify(next==='status'?{semesterId:'',status:'',from:'',to:null,decisionNumber:null,decisionDate:null,evidence:''}:{classId:'',from:'',until:null,evidence:''}));}
 async function save(){await api.run(async()=>{
  const path=mode==='status'?F+'/qua-trinh-hoc/'+id+'/lich-su-trang-thai':mode==='class'?F+'/qua-trinh-hoc/'+id+'/lich-su-lop':type==='semester'?root+'/xac-minh':F+'/qua-trinh-hoc/'+id+'/xac-minh';
  await api.mutate(mode==='status'||mode==='class'?'POST':'PUT',path,JSON.parse(edit));
 });}
 return <section className={styles.workspace}>
  <h1>{rolesOnly?'Phân quyền MGHP':view==='lookup'?'Danh mục nguồn':view==='students'?'Tra cứu sinh viên':'Xác minh dữ liệu'}</h1>
  {api.error&&<Alert>{api.error}</Alert>}
  {!editor?<div className={styles.toolbar}>
   {view==='lookup'&&<label>Danh mục<select value={kind} onChange={e=>setKind(e.target.value)}>{[['semesters','Học kỳ'],['units','Đơn vị'],['classes','Lớp'],['cohorts','Khóa học'],['ethnicities','Dân tộc']].map(([value,name])=><option key={value} value={value}>{name}</option>)}</select></label>}
   <label>Tìm kiếm<input value={q} onChange={e=>setQ(e.target.value)} maxLength={128}/></label>
   <button disabled={api.busy} onClick={()=>api.run(async()=>{await api.get(view==='lookup'?F+'/danh-muc?kind='+kind+'&q='+encodeURIComponent(q):F+'/sinh-vien?q='+encodeURIComponent(q));})}>Tra cứu</button>
   {view==='students'&&<DirectoryPicker client={client} path={F+'/sinh-vien'} label="Sinh viên cần xem" value={id} format={row=>[row.studentCode,row.fullName].filter(Boolean).join(' · ')} onSelect={row=>{chooseId(row?.id??'');if(row)void api.run(async()=>{await api.get(F+'/sinh-vien/'+row.id);});}}/>}
  </div>:<>
   <div className={styles.toolbar}>{!rolesOnly&&<label>Loại dữ liệu<select value={type} onChange={e=>{setType(e.target.value);setMode('main');chooseId('');setStudentChoice('');setStudentProcesses([]);studentRequest.current++;}}><option value="semester">Mốc học kỳ</option><option value="process">Quá trình & lịch sử</option></select></label>}{rolesOnly?<DirectoryPicker client={client} path={F+'/nguoi-dung'} label="Người dùng cần phân quyền" value={id} onSelect={row=>{chooseId(row?.id??'');setTargetLabel(row?[row.name,row.code].filter(Boolean).join(' · '):'');}}/>:type==='semester'?<DirectoryPicker client={client} path={F+'/danh-muc?kind=semesters'} label="Học kỳ cần xác minh" value={id} onSelect={row=>chooseId(row?.id??'')}/>:<><DirectoryPicker client={client} path={F+'/sinh-vien'} label="Sinh viên cần xác minh" value={studentChoice} format={row=>[row.studentCode,row.fullName].filter(Boolean).join(' · ')} onSelect={row=>void api.run(()=>chooseStudent(row))}/><label>Quá trình học<select aria-label="Quá trình học" value={id} onChange={e=>chooseId(e.currentTarget.value)}><option value="">Chọn quá trình của sinh viên</option>{studentProcesses.map(row=><option key={row.id} value={row.id}>{[row.programName,row.cohortCode,row.code].filter(Boolean).join(' · ')}</option>)}</select></label></>}<button disabled={api.busy||!id} onClick={read}>Đọc dữ liệu</button></div>
   <p className={styles.hint}>Chỉ người có quyền tương ứng được đọc hoặc sửa. Đọc lại dữ liệu trước mỗi thay đổi để dùng đúng phiên bản.</p>
   {type==='roles'?<div className={styles.toolbar}><DirectoryPicker client={client} path={F+'/vai-tro-mghp'} paged={false} label="Vai trò MGHP" value={role} onSelect={row=>{setRole(row?.id??'');setRoleLabel(row?.name??row?.code??'');}}/><button disabled={api.busy||!api.tag||!role} onClick={()=>api.run(()=>api.mutate('PUT',root+'/'+role))}>Cấp quyền</button><button className="danger" disabled={api.busy||!api.tag||!role} onClick={()=>{if(window.confirm('Thu hồi '+roleLabel+' của '+targetLabel+'?'))void api.run(()=>api.mutate('DELETE',root+'/'+role));}}>Thu hồi quyền</button></div>:<>
    <RecordFields value={edit} onChange={setEdit} labels={fieldLabels}/><button disabled={api.busy||!api.tag} onClick={save}>Lưu thay đổi đã xác minh</button>
    {type==='process'&&<div className={styles.toolbar}><button disabled={api.busy||!api.tag} onClick={()=>chooseMode('status')}>Nhập lịch sử trạng thái</button><button disabled={api.busy||!api.tag} onClick={()=>chooseMode('class')}>Nhập lịch sử lớp</button></div>}
   </>}
  </>}
  {api.busy&&<p role="status">Đang xử lý…</p>}{view==='students'&&api.data&&typeof api.data==='object'&&'items' in api.data?<div className={styles.table}><table><thead><tr><th>MSSV</th><th>Họ tên</th><th>Lớp</th><th>Đơn vị quản lý</th><th>Thao tác</th></tr></thead><tbody>{(api.data as {items:Record<string,any>[]}).items.map(row=><tr key={row.id}><td>{row.studentCode}</td><td>{row.fullName??'Chưa có tên'}</td><td>{row.classCode??'Chưa có thông tin'}</td><td>{row.managementUnitName??'Chưa có thông tin'}</td><td><button type="button" disabled={api.busy} onClick={()=>{chooseId(row.id);void api.run(async()=>{await api.get(F+'/sinh-vien/'+row.id);});}}>Xem hồ sơ sinh viên</button></td></tr>)}</tbody></table></div>:api.data!=null&&<RecordView data={api.data} labels={fieldLabels}/>}
 </section>;
}
