import {useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {useRecordApi} from '../../../shared/hooks/useRecordApi';
import {RecordFields} from '../../../shared/ui/RecordFields';
import {RecordView} from '../../../shared/ui/RecordView';
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
   {view==='students'&&<><label>ID sinh viên<input value={id} onChange={e=>setId(e.target.value)}/></label><button disabled={api.busy} onClick={()=>api.run(async()=>{await api.get(F+'/sinh-vien/'+id);})}>Xem chi tiết</button></>}
  </div>:<>
   <div className={styles.toolbar}>{!rolesOnly&&<label>Loại dữ liệu<select value={type} onChange={e=>{setType(e.target.value);setMode('main');setEdit('');api.clearVersion();}}><option value="semester">Mốc học kỳ</option><option value="process">Quá trình & lịch sử</option></select></label>}<label>{rolesOnly?'ID người dùng CTSV':'ID đối tượng'}<input value={id} onChange={e=>{setId(e.target.value);api.clearVersion();}}/></label><button disabled={api.busy||!id} onClick={read}>Đọc dữ liệu</button></div>
   <p className={styles.hint}>Chỉ người có quyền tương ứng được đọc hoặc sửa. Đọc lại dữ liệu trước mỗi thay đổi để dùng đúng phiên bản.</p>
   {type==='roles'?<div className={styles.toolbar}><label>ID vai trò MGHP<input value={role} onChange={e=>setRole(e.target.value)}/></label><button disabled={api.busy||!api.tag||!role} onClick={()=>api.run(()=>api.mutate('PUT',root+'/'+role))}>Cấp quyền</button><button className="danger" disabled={api.busy||!api.tag||!role} onClick={()=>api.run(()=>api.mutate('DELETE',root+'/'+role))}>Thu hồi quyền</button></div>:<>
    <RecordFields value={edit} onChange={setEdit} labels={fieldLabels}/><button disabled={api.busy||!api.tag} onClick={save}>Lưu thay đổi đã xác minh</button>
    {type==='process'&&<div className={styles.toolbar}><button disabled={api.busy||!api.tag} onClick={()=>chooseMode('status')}>Nhập lịch sử trạng thái</button><button disabled={api.busy||!api.tag} onClick={()=>chooseMode('class')}>Nhập lịch sử lớp</button></div>}
   </>}
  </>}
  {api.busy&&<p role="status">Đang xử lý…</p>}{api.data!=null&&<RecordView data={api.data} labels={fieldLabels}/>}
 </section>;
}
