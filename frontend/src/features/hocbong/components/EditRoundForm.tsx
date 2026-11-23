import {useEffect,useRef,useState,type FormEvent} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {errorMessage} from '../../../shared/api/error-message';
import {Alert} from '../../../shared/ui/Alert';
import {Button} from '../../../shared/ui/Button';
import {readRoundContext,updateRound} from '../api/rounds';
import type {RoundView,RoundContext,ScopeSelection,ScopeCatalogSnapshot} from '../types/round';
import {ScopePicker,ScopeSummary,scopeReady} from './ScopePicker';
import styles from './CreateRoundForm.module.css';
type Props={client:AuthClient;initial:RoundContext;onCancel:()=>void;onSaved:(view:RoundView,recovered:boolean)=>Promise<void>;onForbidden:()=>void};
export function EditRoundForm({client,initial,onCancel,onSaved,onForbidden}:Props){
 const [base,setBase]=useState(initial),[selection,setSelection]=useState<ScopeSelection|null>(initial.scope?initial.scope.mode==='all'?{mode:'all'}:{mode:'groups',groups:initial.scope.groups.map(g=>({cohortId:g.cohortId,majorId:g.majorId}))}:null);
 const [catalog,setCatalog]=useState<ScopeCatalogSnapshot|null>(null),[refresh,setRefresh]=useState(0);
 const [phase,setPhase]=useState<'editing'|'conflict'|'unknown'|'saved'>('editing'),[locked,setLocked]=useState(!initial.scope||initial.round.status!=='draft');
 const [error,setError]=useState(''),[busy,setBusy]=useState(false),[stale,setStale]=useState(false),[latest,setLatest]=useState<RoundContext|null>(null),[saved,setSaved]=useState<RoundView|null>(null);
 const pending=useRef(false),alive=useRef(true),abort=useRef<AbortController|null>(null),heading=useRef<HTMLHeadingElement>(null);
 useEffect(()=>{alive.current=true;heading.current?.focus();return()=>{alive.current=false;abort.current?.abort();};},[]);
 function begin(){if(pending.current)return false;pending.current=true;setBusy(true);setError('');return true;}
 function end(){pending.current=false;if(alive.current)setBusy(false);}
 function denied(e:unknown){if(e instanceof ApiError&&e.status===403){onForbidden();return true;}return false;}
 async function submit(e:FormEvent){
  e.preventDefault();if(phase!=='editing'||locked||stale||!catalog||!selection||!scopeReady(catalog,selection)||!begin())return;
  let written:RoundView|null=null;
  try{
   written=await updateRound(client,base.round.id,{catalogRevision:catalog.catalogRevision,scope:selection},base.etag);
   if(!alive.current)return;setSaved(written);setPhase('saved');await onSaved(written,false);
  }catch(e){
   if(!alive.current||denied(e))return;
   if(written){setError('Phạm vi đã lưu. Hãy đọc lại kết quả để tải đầy đủ dữ liệu; không cần gửi lại lệnh lưu.');return;}
   if(e instanceof ApiError&&e.status<500){
    if(['VERSION_CONFLICT','PRECONDITION_REQUIRED'].includes(e.code)){setPhase('conflict');setLatest(null);setError('Đợt đã thay đổi. Lựa chọn của bạn được giữ; hãy đọc bản mới để đối chiếu trước khi lưu.');}
    else if(['SCOPE_LOCKED','ROUND_NOT_EDITABLE','LEGACY_ROUND_RECONCILIATION_REQUIRED'].includes(e.code)){setLocked(true);setPhase('conflict');setError('Phạm vi không thể sửa vì đợt đã dùng hoặc cần đối chiếu dữ liệu cũ. Hãy đọc bản mới để kiểm tra.');}
    else if(['SCOPE_SOURCE_CHANGED','SCOPE_GROUP_UNAVAILABLE','SCOPE_EMPTY'].includes(e.code)){setStale(true);setError('Danh mục nhóm đã thay đổi. Lựa chọn vẫn được giữ; hãy đọc lại danh mục và đối chiếu trước khi lưu.');}
    else setError(errorMessage(e));
   }else{setPhase('unknown');setLatest(null);setError('Chưa xác nhận được kết quả lưu. Đọc bản mới để đối chiếu; hệ thống không tự gửi lại lệnh lưu.');}
  }finally{end();}
 }
 async function readLatest(){
  if(!begin())return;const controller=new AbortController();abort.current=controller;
  try{const value=await readRoundContext(client,base.round.id,controller.signal);if(alive.current&&!controller.signal.aborted)setLatest(value);}
  catch(e){if(alive.current&&!controller.signal.aborted&&!denied(e))setError(errorMessage(e));}finally{end();}
 }
 function adopt(replace:boolean){
  if(!latest||!latest.scope||locked||latest.round.status!=='draft')return;
  setBase(latest);if(replace)setSelection(latest.scope.mode==='all'?{mode:'all'}:{mode:'groups',groups:latest.scope.groups.map(g=>({cohortId:g.cohortId,majorId:g.majorId}))});
  setLatest(null);setPhase('editing');setCatalog(null);setRefresh(v=>v+1);setError('');setStale(false);
 }
 async function confirm(view:RoundView){if(!begin())return;try{await onSaved(view,true);}catch(e){if(alive.current&&!denied(e))setError(errorMessage(e));}finally{end();}}
 return <section className={styles.formArea} aria-labelledby="edit-round-title">
  <h2 id="edit-round-title" ref={heading} tabIndex={-1}>Sửa phạm vi đợt xét</h2>
  <dl className={styles.readOnly}><div><dt>Đợt xét</dt><dd>{base.round.name}</dd></div></dl>
  <p>Số, tên và học kỳ do hệ thống quản lý. Chỉ thay đổi phạm vi trước khi đợt được sử dụng.</p>
  <form aria-label="Sửa phạm vi đợt xét KKHT" onSubmit={submit} className={styles.form}>
   <ScopePicker client={client} sourceId={base.round.sourceSemesterId} value={selection} onChange={setSelection} onCatalog={data=>{setCatalog(data);if(data)setStale(false);}} onForbidden={onForbidden} refresh={refresh} initialGroups={base.scope?.groups} disabled={busy||locked||phase!=='editing'}/>
   {error&&<Alert>{error}</Alert>}
   {(phase==='conflict'||phase==='unknown')&&<div className={styles.recovery}>
    <Button disabled={busy} onClick={()=>void readLatest()}>Đọc bản mới để đối chiếu</Button>
    {latest&&<><ScopeSummary scope={latest.scope}/><p>Lựa chọn của bạn: {selection?.mode==='all'?'Toàn bộ nhóm':`${selection?.groups.length??0} nhóm`}</p>
     <Button variant="secondary" disabled={busy} onClick={()=>void confirm(latest)}>Mở bản đang lưu</Button>
     {latest.scope&&latest.round.status==='draft'&&!locked&&<>
      <Button disabled={busy} onClick={()=>adopt(false)}>Giữ lựa chọn và dùng bản mới</Button>
      <Button variant="secondary" disabled={busy} onClick={()=>adopt(true)}>Dùng phạm vi đang lưu</Button>
     </>}
    </>}
   </div>}
   <div className={styles.actions}>
    {phase==='editing'&&<Button type="submit" disabled={busy||locked||stale||!scopeReady(catalog,selection)}>Lưu phạm vi</Button>}
    {phase==='saved'&&saved&&<Button disabled={busy} onClick={()=>void confirm(saved)}>Đọc lại kết quả</Button>}
    <Button variant="secondary" disabled={busy} onClick={onCancel}>Quay lại chi tiết</Button>
   </div>
  </form>
 </section>;
}