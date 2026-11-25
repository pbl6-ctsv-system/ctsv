import {useId,useState,type FormEvent} from 'react';
import {Button} from '../../../shared/ui/Button';
import styles from './LoginPage.module.css';

export function LoginPage({onLogin,onRestore,busy}:{onLogin:(u:string,p:string)=>Promise<void>;onRestore:()=>Promise<void>;busy:boolean}){
 const[username,setUsername]=useState(''),[password,setPassword]=useState('');
 const[visible,setVisible]=useState(false),[help,setHelp]=useState(false);
 const id=useId(),headingId=id+'-heading',usernameId=id+'-username',passwordId=id+'-password',helpId=id+'-help';
 async function submit(event:FormEvent){
  event.preventDefault();
  if(busy)return;
  const value=password;
  setPassword('');
  setVisible(false);
  await onLogin(username,value);
 }
 return <div className={styles.stack}>
  <form className={styles.card} onSubmit={submit} aria-labelledby={headingId} aria-busy={busy}>
   <h2 id={headingId}>Đăng nhập CTSV</h2>
   <p className={styles.lead}>Sử dụng tài khoản CTSV để truy cập hệ thống.</p>
   <button className={styles.office} type="button" disabled>
    <span className={styles.microsoftMark} aria-hidden="true">
     <svg width="22" height="22" viewBox="0 0 21 21">
      <path fill="#f25022" d="M0 0h10v10H0z"/>
      <path fill="#7fba00" d="M11 0h10v10H11z"/>
      <path fill="#00a4ef" d="M0 11h10v10H0z"/>
      <path fill="#ffb900" d="M11 11h10v10H11z"/>
     </svg>
    </span>
    <span className={styles.officeLabel}>Đăng nhập với Office 365</span>
    <span className={styles.unavailable}>Chưa hỗ trợ</span>
   </button>
   <div className={styles.divider}><span>Tài khoản CTSV</span></div>
   <div className={styles.field}>
    <label htmlFor={usernameId}>Tên đăng nhập</label>
    <div className={styles.inputWrap}>
     <input id={usernameId} name="username" required autoComplete="username" autoCapitalize="none" spellCheck={false}
      placeholder="Nhập tên đăng nhập CTSV" value={username} onChange={event=>setUsername(event.target.value)}/>
     <svg className={styles.inputIcon} viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="8" r="4"/><path d="M5 21v-2a7 7 0 0 1 14 0v2"/>
     </svg>
    </div>
   </div>
   <div className={styles.field}>
    <label htmlFor={passwordId}>Mật khẩu</label>
    <div className={styles.inputWrap}>
     <input id={passwordId} name="password" required type={visible?'text':'password'} autoComplete="current-password"
      placeholder="Nhập mật khẩu" value={password} onChange={event=>setPassword(event.target.value)}/>
     <button className={styles.passwordToggle} type="button" onClick={()=>setVisible(value=>!value)}
      aria-label={visible?'Ẩn mật khẩu':'Hiện mật khẩu'} aria-controls={passwordId} aria-pressed={visible}>
      <svg viewBox="0 0 24 24" aria-hidden="true">
       {visible?<path d="m3 3 18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.2A12 12 0 0 1 12 5c7 0 10 7 10 7a19 19 0 0 1-3.1 3.9M6.1 6.1A20 20 0 0 0 2 12s3 7 10 7a12 12 0 0 0 5.9-1.5"/>
        :<><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></>}
      </svg>
     </button>
    </div>
   </div>
   <Button className={styles.submit} disabled={busy} type="submit">Đăng nhập</Button>
   <Button variant="secondary" className={styles.restore} onClick={onRestore} disabled={busy}>Tiếp tục đăng nhập</Button>
   {busy&&<p className={styles.status} role="status">Đang xử lý đăng nhập…</p>}
   <div id={helpId} className={styles.accountHelp} hidden={!help}>
    <strong>Hỗ trợ tài khoản</strong>
    <p>Liên hệ Phòng Công tác Sinh viên để được hướng dẫn cấp lại tài khoản hoặc mật khẩu.</p>
   </div>
  </form>
  <div className={styles.support}>
   <svg viewBox="0 0 24 24" aria-hidden="true">
    <circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="4"/>
    <path d="m5.6 5.6 3.6 3.6m5.6 5.6 3.6 3.6m0-12.8-3.6 3.6m-5.6 5.6-3.6 3.6"/>
   </svg>
   <button className={styles.textButton} type="button" onClick={()=>setHelp(value=>!value)} aria-expanded={help} aria-controls={helpId}>Cần hỗ trợ tài khoản?</button>
  </div>
 </div>;
}
