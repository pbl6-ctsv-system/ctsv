import {useEffect,useId,useRef} from 'react';
import type {ReactNode} from 'react';
import styles from '../pages/Assignments.module.css';

export function AssignmentActionDialog({
 title,
 busy,
 onClose,
 children,
}:{
 title:string;
 busy:boolean;
 onClose:()=>void;
 children:ReactNode;
}){
 const ref=useRef<HTMLDialogElement>(null);
 const titleId=useId();

 useEffect(()=>{
  const dialog=ref.current;
  const previousFocus=document.activeElement instanceof HTMLElement?document.activeElement:null;
  if(dialog&&!dialog.open){
   dialog.showModal();
  }
  return()=>{
   if(dialog?.open)dialog.close();
   previousFocus?.focus?.();
  };
 },[]);

 return (
  <dialog
   ref={ref}
   className={styles.dialog}
   aria-labelledby={titleId}
   onCancel={e=>{
    e.preventDefault();
    if(!busy)onClose();
   }}
  >
   <header>
    <h2 id={titleId}>{title}</h2>
   </header>
   {children}
  </dialog>
 );
}
