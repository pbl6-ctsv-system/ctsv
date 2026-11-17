import type {ReactNode} from 'react';
import {Button} from '../../shared/ui/Button';
import styles from './AppShell.module.css';
import controls from '../../shared/ui/Controls.module.css';
export function AppShell({username,busy,onLogout,navigation,children}:{username?:string;busy:boolean;onLogout:()=>Promise<void>;navigation?:ReactNode;children:ReactNode}){
 return <div className={styles.shell}>
  <header className={styles.header}>
   <div className={`${styles.headerBody} ${controls.controls}`}>
    <span className={styles.brand}>Cổng công tác sinh viên</span>
    {username&&<div className={styles.identity}><strong>{username}</strong><Button onClick={onLogout} disabled={busy}>Đăng xuất</Button></div>}
   </div>
   {navigation}
  </header>
  <main id="ctsv-main" tabIndex={-1} className={`${styles.content} ${controls.controls}`}>{children}</main>
 </div>;
}
