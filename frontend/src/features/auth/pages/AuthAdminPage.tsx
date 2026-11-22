import {useState,type FormEvent} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {useRecordApi} from '../../../shared/hooks/useRecordApi';
import {DirectoryPicker} from '../../../shared/ui/DirectoryPicker';
import {AccountProfileFields} from './AccountProfileFields';
import {accountFormErrors} from './account-model.mjs';
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
 const [errors,setErrors]=useState<Record<string,string>>({}),[roleName,setRoleName]=useState(''),[roleActive,setRoleActive]=useState(true);
 const editValue=edit?JSON.parse(edit):{};
 function field(key:string,value:unknown){setEdit(JSON.stringify({...editValue,[key]:value}));setErrors({});}
 const [success,setSuccess]=useState('');
 const rbacOnly=view==='rbac';
 const account=loadedId===id&&api.data?api.data as Account:null;
 const root=A+'/tai-khoan/'+id;
 async function read(target=id){
  setRole('');setRoleName('');
  await api.run(async()=>{
   await api.get(A+'/tai-khoan/'+target);setLoadedId(target);setMode('inspect');setSuccess('');setReason('');
  });
 }
 function chooseMode(next:Mode){
  setMode(next);setSuccess('');setErrors({});
  setEdit(JSON.stringify(next==='create'?{username:'',password:'',profileLinks:[]}:
   next==='reset'?{newPassword:'',reason:''}:{profileLinks:account?.profileLinks??[]}));
 }
 async function save(event:FormEvent){
  event.preventDefault();
  await api.run(async()=>{
   const value=JSON.parse(edit);const problems=accountFormErrors(value,mode==='create',mode==='profiles');setErrors(Object.fromEntries(Object.entries(problems)));if(Object.keys(problems).length)throw new Error('Kiểm tra các trường được đánh dấu bên dưới.');
   if(mode==='create'){
    const response=await client.request(A+'/tai-khoan',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(value)});
    const created=await response.json();
    setId(created.accountId);await api.get(A+'/tai-khoan/'+created.accountId);setLoadedId(created.accountId);setMode('inspect');
    setSuccess('Đã tạo tài khoản '+created.username+'. Có thể quản lý ngay bên dưới.');
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
    <DirectoryPicker client={client} path={A+'/tai-khoan'} label="Tài khoản cần quản lý" idField="accountId" value={id} disabled={api.busy} format={row=>row.username+(row.active?' · Đang hoạt động':' · Đã khóa')} onSelect={row=>{setId(row?.accountId??'');setLoadedId('');setMode('inspect');api.clearRecord();if(row)void read(row.accountId);}}/>
    <Button type="submit" disabled={api.busy||!id}>Đọc dữ liệu</Button>
   </form>}
   {mode==='create'&&<div className={styles.card}><h2>Tạo tài khoản</h2>
    <p className={styles.hint}>Nhập tài khoản và liên kết hồ sơ nguồn đã được xác minh.</p>
    <form onSubmit={save}><fieldset disabled={api.busy}>
     <label>Tên đăng nhập<input aria-label="Tên đăng nhập" required maxLength={128} autoComplete="off" value={editValue.username??''} aria-invalid={!!errors.username} onChange={e=>field('username',e.currentTarget.value)}/>{errors.username&&<small role="alert">{errors.username}</small>}</label>
     <label>Mật khẩu<input aria-label="Mật khẩu" aria-describedby="account-password-help" required type="password" autoComplete="new-password" value={editValue.password??''} aria-invalid={!!errors.password} onChange={e=>field('password',e.currentTarget.value)}/><small id="account-password-help">Từ 12–1024 ký tự; giữ nguyên khoảng trắng nếu có.</small>{errors.password&&<small role="alert">{errors.password}</small>}</label>
     <AccountProfileFields client={client} value={editValue.profileLinks??[]} onChange={value=>field('profileLinks',value)}/>{errors.profileLinks&&<p role="alert">{errors.profileLinks}</p>}</fieldset>
     <div className={styles.actions}><Button variant="secondary" disabled={api.busy} onClick={()=>setMode('inspect')}>Hủy tạo tài khoản</Button><Button type="submit" disabled={api.busy}>Tạo tài khoản</Button></div>
    </form>
   </div>}
   {account&&mode!=='create'&&<div className={styles.card}>
    <div className={styles.heading}><div><h2>{account.username??'Tài khoản đã chọn'}</h2><span className={styles.badge}>{account.active?'Đang hoạt động':'Đã khóa'}</span></div>
     <Button variant="secondary" onClick={()=>void read()} disabled={api.busy}>Đọc lại tài khoản</Button></div>
    <div className={styles.tabs} aria-label="Tác vụ tài khoản">
     {([['inspect','Thông tin tài khoản'],['profiles','Liên kết hồ sơ'],['roles','Vai trò'],['reset','Đặt mật khẩu'],['status','Khóa / mở khóa']] as const).map(([key,label])=>
      <Button key={key} variant={mode===key?'primary':'secondary'} disabled={api.busy} aria-pressed={mode===key} onClick={()=>chooseMode(key)}>{label}</Button>)}
    </div>
    {mode==='inspect'&&<RecordView data={api.data} labels={fieldLabels}/>}
    {(mode==='profiles'||mode==='reset')&&<form onSubmit={save}>
     <h3>{mode==='reset'?'Đặt mật khẩu mới':'Cập nhật liên kết hồ sơ'}</h3>
     {mode==='reset'&&<p className={styles.hint}>Thao tác này thu hồi quyền đăng nhập cũ theo chính sách Auth.</p>}
     <fieldset disabled={api.busy||!api.tag}>{mode==='profiles'?<AccountProfileFields client={client} value={editValue.profileLinks??[]} onChange={value=>field('profileLinks',value)}/>:<><label>Mật khẩu mới<input aria-label="Mật khẩu mới" required type="password" autoComplete="new-password" value={editValue.newPassword??''} aria-invalid={!!errors.password} onChange={e=>field('newPassword',e.currentTarget.value)}/><small>Từ 12–1024 ký tự Unicode.</small>{errors.password&&<small role="alert">{errors.password}</small>}</label><label>Lý do<textarea required maxLength={4000} value={editValue.reason??''} onChange={e=>field('reason',e.currentTarget.value)}/>{errors.reason&&<small role="alert">{errors.reason}</small>}</label></>}{errors.profileLinks&&<p role="alert">{errors.profileLinks}</p>}</fieldset>
     <Button type="submit" disabled={api.busy||!api.tag}>Lưu thay đổi đã xác minh</Button>
    </form>}
    {mode==='roles'&&<form onSubmit={e=>{e.preventDefault();if(window.confirm('Cấp '+roleName+' cho '+account?.username+'?'))void changeRole(true);}} className={styles.form}>
     <p className={styles.hint}>Mã vai trò được tra tại “Danh mục quyền Auth”. Quyền nghiệp vụ CTSV được quản lý ở mục phân quyền riêng.</p>
     <DirectoryPicker client={client} path={A+'/phan-quyen'} paged={false} itemsKey="roles" label="Vai trò hệ thống" value={role} required format={row=>(row.name??row.code)+(row.active?'':' · Đã ngừng sử dụng')} onSelect={row=>{setRole(row?.id??'');setRoleName(row?.name??row?.code??'');setRoleActive(row?.active===true);}}/>
     <div className={styles.actions}><Button type="submit" disabled={api.busy||!api.tag||!role||!roleActive}>Cấp vai trò</Button>
      <Button variant="danger" disabled={api.busy||!api.tag||!/^[1-9][0-9]*$/.test(role)} onClick={()=>{if(window.confirm('Thu hồi '+roleName+' của '+account?.username+'? Các phiên hiện tại sẽ bị thu hồi.'))void changeRole(false);}}>Thu hồi vai trò</Button></div>
    </form>}
    {mode==='status'&&<form onSubmit={e=>{e.preventDefault();if(window.confirm((account.active?'Khóa ':'Mở khóa ')+account.username+'?'+(account.active?' Các phiên đăng nhập hiện tại sẽ bị thu hồi.':'')))void changeStatus(!account.active);}} className={styles.form}>
     <p className={styles.hint}>{account.active?'Khóa tài khoản sẽ thu hồi khả năng đăng nhập hiện tại.':'Mở khóa cho phép người dùng đăng nhập lại.'}</p>
     <label>Lý do thay đổi trạng thái<textarea required maxLength={4000} value={reason} onChange={e=>setReason(e.currentTarget.value)}/></label>
     <Button type="submit" variant={account.active?'danger':'primary'} disabled={api.busy||!api.tag||!reason.trim()}>{account.active?'Khóa':'Mở khóa'}</Button>
    </form>}
   </div>}
   {!account&&mode!=='create'&&!api.busy&&<p className={styles.empty}>Chọn tài khoản bằng tên đăng nhập để xem thông tin và thao tác quản trị.</p>}
  </>}
  {api.busy&&<p role="status">Đang xử lý…</p>}
  {rbacOnly&&api.data!=null&&<div className={styles.card}><RecordView data={api.data} labels={fieldLabels}/></div>}
 </section>;
}
