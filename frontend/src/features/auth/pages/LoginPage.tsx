import {useState,type FormEvent} from 'react';
import {Button} from '../../../shared/ui/Button';
import styles from './LoginPage.module.css';
export function LoginPage({onLogin,onRestore,busy}:{onLogin:(u:string,p:string)=>Promise<void>;onRestore:()=>Promise<void>;busy:boolean}){
 const[username,setUsername]=useState(''),[password,setPassword]=useState('');
 async function submit(event:FormEvent){event.preventDefault();const value=password;setPassword('');await onLogin(username,value);}
 return <section className={styles.login}><h2>Đăng nhập CTSV</h2><form onSubmit={submit}><label>Tên đăng nhập<input required autoComplete="username" value={username} onChange={e=>setUsername(e.target.value)}/></label><label>Mật khẩu<input required type="password" autoComplete="current-password" value={password} onChange={e=>setPassword(e.target.value)}/></label><Button disabled={busy} type="submit">Đăng nhập</Button></form><Button variant="secondary" className={styles.restore} onClick={onRestore} disabled={busy}>Tiếp tục đăng nhập</Button></section>;
}
