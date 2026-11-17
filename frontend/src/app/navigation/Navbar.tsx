import {useEffect,useRef,useState,type MouseEvent} from 'react';
import type {navigationSections} from './modules.mjs';
import styles from './Navbar.module.css';

type Section=ReturnType<typeof navigationSections>[number];
type RouteClick=(event:MouseEvent<HTMLAnchorElement>,path:string)=>void;

function DropdownMenu({section,path,open,onToggle,onNavigate}:{
 section:Extract<Section,{kind:'dropdown'}>;path:string;open:boolean;onToggle:()=>void;onNavigate:RouteClick;
}){
 const id='nav-panel-'+section.id;
 const active=section.groups.some(group=>group.items.some(item=>item.path===path));
 return <>
  <button type="button" className={styles.item} data-active={active} aria-expanded={open} aria-controls={id} onClick={onToggle}>
   {section.label}<span className={styles.chevron} aria-hidden="true"/>
  </button>
  <div id={id} className={styles.dropdown} hidden={!open}>
   {section.groups.map(group=><div className={styles.group} key={group.id}>
    {section.groups.length>1&&<p className={styles.groupLabel}>{group.label}</p>}
    <ul>{group.items.map(item=><li key={item.id}>
     <a href={item.path} aria-current={item.path===path?'page':undefined} onClick={event=>onNavigate(event,item.path)}>{item.label}</a>
    </li>)}</ul>
   </div>)}
  </div>
 </>;
}

export function Navbar({sections,path,onNavigate}:{sections:Section[];path:string;onNavigate:(path:string)=>void}){
 const root=useRef<HTMLElement>(null);
 const [opened,setOpened]=useState<{id:string;path:string}|null>(null);
 const openId=opened?.path===path?opened.id:null;
 useEffect(()=>{
  if(!openId)return;
  function outside(event:PointerEvent){
   if(event.target instanceof Node&&!root.current?.contains(event.target))setOpened(null);
  }
  function escape(event:KeyboardEvent){
   if(event.key!=='Escape'||event.defaultPrevented)return;
   const trigger=root.current?.querySelector<HTMLButtonElement>('[aria-expanded="true"]');
   setOpened(null);
   trigger?.focus();
  }
  document.addEventListener('pointerdown',outside);
  document.addEventListener('keydown',escape);
  return ()=>{
   document.removeEventListener('pointerdown',outside);
   document.removeEventListener('keydown',escape);
  };
 },[openId]);
 function follow(event:MouseEvent<HTMLAnchorElement>,next:string){
  setOpened(null);
  if(event.defaultPrevented||event.button!==0||event.ctrlKey||event.metaKey||event.shiftKey||event.altKey)return;
  event.preventDefault();
  onNavigate(next);
  document.getElementById('ctsv-main')?.focus();
 }
 return <nav ref={root} className={styles.nav} aria-label="Chức năng CTSV">
  <ul className={styles.items}>
   {sections.map(section=><li className={styles.entry} key={section.id}>
    {section.kind==='dropdown'?<DropdownMenu section={section} path={path} open={openId===section.id}
      onToggle={()=>setOpened(openId===section.id?null:{id:section.id,path})} onNavigate={follow}/>
     :section.kind==='link'?<a className={styles.item} href={section.path}
       aria-current={section.path===path?'page':undefined} onClick={event=>follow(event,section.path)}>{section.label}</a>
     :<span className={styles.unavailable} aria-disabled="true" title="Chưa triển khai" aria-label={section.label+' — Chưa triển khai'}>
       {section.label}<small>Chưa triển khai</small>
      </span>}
   </li>)}
  </ul>
 </nav>;
}
