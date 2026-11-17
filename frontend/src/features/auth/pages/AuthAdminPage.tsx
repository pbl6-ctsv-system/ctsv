import {useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {useRecordApi} from '../../../shared/hooks/useRecordApi';
import {RecordFields} from '../../../shared/ui/RecordFields';
import {RecordView} from '../../../shared/ui/RecordView';
import {Alert} from '../../../shared/ui/Alert';
import {AUTH_API as A} from '../api/auth-api';
import {fieldLabels} from '../types/fieldLabels';
import styles from './AuthAdminPage.module.css';

export function AuthAdminPage({client,view}:{client:AuthClient;view?:string}){
 const api=useRecordApi(client);
 const[id,setId]=useState(''),[role,setRole]=useState(''),[edit,setEdit]=useState(''),[mode,setMode]=useState('main');
 const root=A+'/tai-khoan/'+id;
 async function read(){setMode('main');await api.run(async()=>{const value=await api.get(root);setEdit(JSON.stringify({profileLinks:value.profileLinks}));});}
 function chooseMode(next:string){setMode(next);setEdit(JSON.stringify(next==='reset'?{newPassword:'',reason:''}:{username:'',password:'',profileLinks:[]}));}
 async function save(){await api.run(()=>api.mutate(mode==='create'?'POST':mode==='main'?'PATCH':'PUT',mode==='create'?A+'/tai-khoan':mode==='reset'?root+'/mat-khau':root,JSON.parse(edit)));}
 const rbacOnly=view==='rbac';
 return <section className={styles.workspace}><h2>{rbacOnly?'Danh mục quyền Auth':'Quản trị Auth'}</h2>
  {api.error&&<Alert>{api.error}</Alert>}
  {rbacOnly?<button disabled={api.busy} onClick={()=>api.run(async()=>{await api.get(A+'/phan-quyen');})}>Đọc danh mục quyền Auth</button>:<>
  <div className={styles.toolbar}><label>Loại dữ liệu<select value="account" onChange={()=>{}}><option value="account">Tài khoản Auth</option></select></label><label>ID đối tượng<input value={id} onChange={e=>{setId(e.target.value);api.clearVersion();}}/></label><button disabled={api.busy||!id} onClick={read}>Đọc dữ liệu</button></div>
  <p className={styles.hint}>Chỉ người có quyền tương ứng được đọc hoặc sửa. Đọc lại dữ liệu trước mỗi thay đổi để dùng đúng phiên bản.</p>
  <RecordFields value={edit} onChange={setEdit} labels={fieldLabels} arrayAddLabel="Thêm liên kết đã xác minh" arrayRemoveLabel="Bỏ liên kết" createArrayItem={()=>({sourceSystem:'',profileType:'student',sourceId:''})}/>
  <button disabled={api.busy||(!api.tag&&mode!=='create')} onClick={save}>Lưu thay đổi đã xác minh</button>
  <div className={styles.toolbar}>
   <button disabled={api.busy||!api.tag} onClick={()=>api.run(()=>api.mutate('PUT',root+'/trang-thai',{active:false,reason:'Khóa tài khoản theo xác minh của quản trị viên'}))}>Khóa</button>
   <button disabled={api.busy||!api.tag} onClick={()=>api.run(()=>api.mutate('PUT',root+'/trang-thai',{active:true,reason:'Mở khóa theo xác minh của quản trị viên'}))}>Mở khóa</button>
   <button disabled={api.busy||!api.tag} onClick={()=>chooseMode('reset')}>Nhập mật khẩu mới</button>
   <label>ID vai trò chung<input value={role} onChange={e=>setRole(e.target.value)}/></label>
   <button disabled={api.busy||!api.tag} onClick={()=>api.run(()=>api.mutate('PUT',root+'/vai-tro/'+role))}>Cấp vai trò</button>
   <button disabled={api.busy||!api.tag} onClick={()=>api.run(()=>api.mutate('DELETE',root+'/vai-tro/'+role))}>Thu hồi vai trò</button>
  </div>
  <div className={styles.toolbar}><button disabled={api.busy} onClick={()=>chooseMode('create')}>Nhập tài khoản mới</button></div>
  </>}
  {api.busy&&<p role="status">Đang xử lý…</p>}{api.data!=null&&<RecordView data={api.data} labels={fieldLabels}/>}
 </section>;
}
