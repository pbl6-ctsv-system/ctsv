import {useState,type FormEvent} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {useRecordApi} from '../../../shared/hooks/useRecordApi';
import {RecordFields} from '../../../shared/ui/RecordFields';
import {RecordView} from '../../../shared/ui/RecordView';
import {Alert} from '../../../shared/ui/Alert';
import {Button} from '../../../shared/ui/Button';
import {AUTH_API as A} from '../api/auth-api';
import {fieldLabels} from '../types/fieldLabels';
import styles from './AuthAdminPage.module.css';

type Mode='inspect'|'profiles'|'create'|'reset'|'roles'|'status';
type Account={accountId?:string;username?:string;active?:boolean;profileLinks?:unknown[];roles?:unknown[]};
export function AuthAdminPage({client,view}:{client:AuthClient;view?:string}){
 const api=useRecordApi(client);
 const [id,setId]=useState(''),[loadedId,setLoadedId]=useState(''),[role,setRole]=useState('');
 const [edit,setEdit]=useState(''),[mode,setMode]=useState<Mode>('inspect'),[reason,setReason]=useState('');
 const [success,setSuccess]=useState('');
 const rbacOnly=view==='rbac';
 const account=loadedId===id&&api.data?api.data as Account:null;
 const root=A+'/tai-khoan/'+id;
 async function read(){
  await api.run(async()=>{
   await api.get(root);setLoadedId(id);setMode('inspect');setSuccess('');setReason('');
  });
 }
 function chooseMode(next:Mode){
  setMode(next);setSuccess('');
  setEdit(JSON.stringify(next==='create'?{username:'',password:'',profileLinks:[]}:
   next==='reset'?{newPassword:'',reason:''}:{profileLinks:account?.profileLinks??[]}));
 }
 async function save(event:FormEvent){
  event.preventDefault();
  await api.run(async()=>{
   const value=JSON.parse(edit);
   if(mode==='create'){
    const response=await client.request(A+'/tai-khoan',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(value)});
    const created=await response.json();
    setId(created.accountId);setLoadedId('');setMode('inspect');api.clearVersion();
    setSuccess('Đã tạo tài khoản '+created.username+'. Bấm “Đọc dữ liệu” để quản lý.');
   }else{
    await api.mutate(mode==='reset'?'PUT':'PATCH',mode==='reset'?root+'/mat-khau':root,value);
    await api.get(root);setMode('inspect');setEdit('');setSuccess('Đã lưu thay đổi tài khoản.');
   }
  });
 }
 async function changeStatus(active:boolean){
  await api.run(async()=>{
   await api.mutate('PUT',root+'/trang-thai',{active,reason});
   await api.get(root);setMode('inspect');setReason('');setSuccess(active?'Đã mở khóa tài khoản.':'Đã khóa tài khoản.');
  });
 }
 async function changeRole(grant:boolean){
  await api.run(async()=>{
   await api.mutate(grant?'PUT':'DELETE',root+'/vai-tro/'+role);
   await api.get(root);setSuccess(grant?'Đã cấp vai trò.':'Đã thu hồi vai trò.');
  });
 }
 return <section className={styles.workspace}>
  <div className={styles.heading}><div><h1>{rbacOnly?'Danh mục quyền Auth':'Quản lý tài khoản'}</h1>
   <p className={styles.hint}>{rbacOnly?'Tra cứu vai trò và chức năng đã được cấu hình.':'Tra cứu tài khoản trước khi thay đổi liên kết hồ sơ, trạng thái hoặc vai trò.'}</p></div>
   {!rbacOnly&&<Button disabled={api.busy} onClick={()=>chooseMode('create')}>Nhập tài khoản mới</Button>}
  </div>
  {api.error&&<Alert>{api.error}</Alert>}
  {success&&<p role="status" className={styles.success}>{success}</p>}
  {rbacOnly?<Button variant="secondary" disabled={api.busy} onClick={()=>api.run(async()=>{await api.get(A+'/phan-quyen');})}>Đọc danh mục quyền Auth</Button>:<>
   {mode!=='create'&&<form className={styles.toolbar} onSubmit={e=>{e.preventDefault();void read();}}>
    <label>ID đối tượng<input required placeholder="UUID tài khoản cần quản lý" value={id} onChange={e=>{setId(e.currentTarget.value);setMode('inspect');api.clearVersion();}}/></label>
    <Button type="submit" disabled={api.busy||!id}>Đọc dữ liệu</Button>
   </form>}
   {mode==='create'&&<div className={styles.card}><h2>Tạo tài khoản</h2>
    <p className={styles.hint}>Nhập tài khoản và liên kết hồ sơ nguồn đã được xác minh.</p>
    <form onSubmit={save}><fieldset disabled={api.busy}><RecordFields value={edit} onChange={setEdit} labels={fieldLabels}
     arrayAddLabel="Thêm liên kết đã xác minh" arrayRemoveLabel="Bỏ liên kết" createArrayItem={()=>({sourceSystem:'',profileType:'student',sourceId:''})}/></fieldset>
     <div className={styles.actions}><Button variant="secondary" disabled={api.busy} onClick={()=>setMode('inspect')}>Hủy tạo tài khoản</Button><Button type="submit" disabled={api.busy}>Tạo tài khoản</Button></div>
    </form>
   </div>}
   {account&&mode!=='create'&&<div className={styles.card}>
    <div className={styles.heading}><div><h2>{account.username??'Tài khoản đã chọn'}</h2><span className={styles.badge}>{account.active?'Đang hoạt động':'Đã khóa'}</span></div>
     <Button variant="secondary" onClick={read} disabled={api.busy}>Đọc lại tài khoản</Button></div>
    <div className={styles.tabs} aria-label="Tác vụ tài khoản">
     {([['inspect','Thông tin tài khoản'],['profiles','Liên kết hồ sơ'],['roles','Vai trò'],['reset','Đặt mật khẩu'],['status','Khóa / mở khóa']] as const).map(([key,label])=>
      <Button key={key} variant={mode===key?'primary':'secondary'} disabled={api.busy} aria-pressed={mode===key} onClick={()=>chooseMode(key)}>{label}</Button>)}
    </div>
    {mode==='inspect'&&<RecordView data={api.data} labels={fieldLabels}/>}
    {(mode==='profiles'||mode==='reset')&&<form onSubmit={save}>
     <h3>{mode==='reset'?'Đặt mật khẩu mới':'Cập nhật liên kết hồ sơ'}</h3>
     {mode==='reset'&&<p className={styles.hint}>Thao tác này thu hồi quyền đăng nhập cũ theo chính sách Auth.</p>}
     <fieldset disabled={api.busy||!api.tag}><RecordFields value={edit} onChange={setEdit} labels={fieldLabels}
      arrayAddLabel="Thêm liên kết đã xác minh" arrayRemoveLabel="Bỏ liên kết" createArrayItem={()=>({sourceSystem:'',profileType:'student',sourceId:''})}/></fieldset>
     <Button type="submit" disabled={api.busy||!api.tag}>Lưu thay đổi đã xác minh</Button>
    </form>}
    {mode==='roles'&&<form onSubmit={e=>{e.preventDefault();void changeRole(true);}} className={styles.form}>
     <p className={styles.hint}>Mã vai trò được tra tại “Danh mục quyền Auth”. Quyền nghiệp vụ CTSV được quản lý ở mục phân quyền riêng.</p>
     <label>ID vai trò chung<input required inputMode="numeric" pattern="[1-9][0-9]*" value={role} onChange={e=>setRole(e.currentTarget.value)}/></label>
     <div className={styles.actions}><Button type="submit" disabled={api.busy||!api.tag||!role}>Cấp vai trò</Button>
      <Button variant="danger" disabled={api.busy||!api.tag||!/^[1-9][0-9]*$/.test(role)} onClick={()=>changeRole(false)}>Thu hồi vai trò</Button></div>
    </form>}
    {mode==='status'&&<form onSubmit={e=>{e.preventDefault();void changeStatus(!account.active);}} className={styles.form}>
     <p className={styles.hint}>{account.active?'Khóa tài khoản sẽ thu hồi khả năng đăng nhập hiện tại.':'Mở khóa cho phép người dùng đăng nhập lại.'}</p>
     <label>Lý do thay đổi trạng thái<textarea required maxLength={4000} value={reason} onChange={e=>setReason(e.currentTarget.value)}/></label>
     <Button type="submit" variant={account.active?'danger':'primary'} disabled={api.busy||!api.tag||!reason.trim()}>{account.active?'Khóa':'Mở khóa'}</Button>
    </form>}
   </div>}
   {!account&&mode!=='create'&&!api.busy&&<p className={styles.empty}>Nhập mã tài khoản để xem thông tin và các thao tác quản trị.</p>}
  </>}
  {api.busy&&<p role="status">Đang xử lý…</p>}
  {rbacOnly&&api.data!=null&&<div className={styles.card}><RecordView data={api.data} labels={fieldLabels}/></div>}
 </section>;
}
