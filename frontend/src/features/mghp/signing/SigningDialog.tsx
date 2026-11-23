import {useEffect,useId,useRef} from 'react';
import type {ReactNode} from 'react';
import styles from './SigningWorkspace.module.css';
export function SigningDialog({title,busy,onClose,children}:{title:string;busy:boolean;onClose:()=>void;children:ReactNode}){
 const ref=useRef<HTMLDialogElement>(null),id=useId();
 useEffect(()=>{const d=ref.current;d?.showModal();return()=>d?.close();},[]);
 return <dialog ref={ref} className={styles.dialog} aria-labelledby={id} onCancel={e=>{e.preventDefault();if(!busy)onClose();}}><h2 id={id}>{title}</h2>{children}</dialog>;
}
