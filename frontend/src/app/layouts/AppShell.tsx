import type {ReactNode} from 'react';
import {Button} from '../../shared/ui/Button';
import styles from './AppShell.module.css';
import controls from '../../shared/ui/Controls.module.css';
export function AppShell({username,busy,onLogout,children}:{username?:string;busy:boolean;onLogout:()=>Promise<void>;children:ReactNode}){
 return <main className={`${styles.shell} ${controls.controls}`}><header className={styles.header}><div><span className={styles.eyebrow}>CTSV</span><h1>Công tác sinh viên</h1><p>Không gian làm việc chung · Chức năng theo quyền được giao</p></div>{username&&<div className={styles.identity}><strong>{username}</strong><Button onClick={onLogout} disabled={busy}>Đăng xuất</Button></div>}</header>{children}<footer>CTSV · Cổng công tác sinh viên</footer></main>;
}
