import {useEffect,useRef,useState,type FormEvent} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {ApiError} from '../../../shared/auth/auth-client.mjs';
import {errorMessage} from '../../../shared/api/error-message';
import {Alert} from '../../../shared/ui/Alert';
import {Button} from '../../../shared/ui/Button';
import {createRound,lookupCreation,readRoundContext} from '../api/rounds';
import type {CreateRoundCommand,RoundView,SourceSemester,ScopeSelection,ScopeCatalogSnapshot} from '../types/round';
import {ScopePicker,scopeReady} from './ScopePicker';
import styles from './CreateRoundForm.module.css';
type Intent={command:CreateRoundCommand;key:string};
export type CreateRoundMemory={selection:ScopeSelection|null;intent:Intent|null;phase:'editing'|'unknown'|'saved';saved:RoundView|null};
type Props={client:AuthClient;initialSource:SourceSemester;memory:CreateRoundMemory;onCancel:()=>void;onCreated:(view:RoundView,recovered:boolean)=>Promise<void>;onForbidden:()=>void};
export function CreateRoundForm({client,initialSource:source,memory,onCancel,onCreated,onForbidden}:Props){
 const [selection,setSelection]=useState<ScopeSelection|null>(memory.selection),[catalog,setCatalog]=useState<ScopeCatalogSnapshot|null>(null);
 const [phase,setPhase]=useState(memory.phase),[error,setError]=useState(''),[busy,setBusy]=useState(false),[stale,setStale]=useState(false);
 const [saved,setSaved]=useState<RoundView|null>(memory.saved);
 const intent=useRef<Intent|null>(memory.intent),pending=useRef(false),alive=useRef(true),heading=useRef<HTMLHeadingElement>(null),abort=useRef<AbortController|null>(null);
 useEffect(()=>{alive.current=true;heading.current?.focus();return()=>{alive.current=false;abort.current?.abort();};},[]);
 function begin(){if(pending.current)return false;pending.current=true;setBusy(true);setError('');return true;}
 function end(){pending.current=false;if(alive.current)setBusy(false);}
 function denied(e:unknown){if(e instanceof ApiError&&e.status===403){onForbidden();return true;}return false;}
 async function send(current:Intent){
  if(!begin())return;
  let written:RoundView|null=null;
  try{
   const result=await createRound(client,current.command,current.key);written=result;
   memory.saved=result;memory.phase='saved';
   if(!alive.current)return;setSaved(result);setPhase('saved');await onCreated(result,result.replayed);
  }catch(e){
   if(!written&&e instanceof ApiError&&e.status<500&&e.code!=='IDEMPOTENCY_KEY_REUSED'){memory.phase='editing';memory.intent=null;}
   if(!alive.current||denied(e))return;
   if(written){setError('Đợt đã được lưu. Hãy đọc lại kết quả để tải đầy đủ phạm vi; không cần tạo lại.');return;}
   if(e instanceof ApiError&&e.status<500){
    setPhase('editing');intent.current=null;
    const messages:Record<string,string>={
     SCOPE_SOURCE_CHANGED:'Danh mục nhóm đã thay đổi. Lựa chọn vẫn được giữ; hãy đọc lại danh mục và kiểm tra trước khi tạo.',
     SCOPE_GROUP_UNAVAILABLE:'Một số nhóm không còn trong nguồn. Hãy đọc lại danh mục và đối chiếu lựa chọn.',
     SCOPE_EMPTY:'Học kỳ chưa có nhóm sinh viên hợp lệ để tạo đợt.',
     ROUND_SEQUENCE_NOT_READY:'Đợt trước chưa hoàn tất chạy xét. Hãy quay lại danh sách để tiếp tục xử lý đợt đó.',
     LEGACY_ROUND_RECONCILIATION_REQUIRED:'Học kỳ có đợt cũ chưa được đối chiếu phạm vi. Cần xử lý dữ liệu đợt cũ trước khi tạo tiếp.',
     IDEMPOTENCY_KEY_REUSED:'Yêu cầu tạo đã được dùng với nội dung khác. Hãy kiểm tra kết quả trước khi tạo tiếp.',
     VALIDATION_ERROR:'Phạm vi chưa hợp lệ. Hãy kiểm tra các nhóm đã chọn (tối đa 2000 nhóm).',
    };
    if(['SCOPE_SOURCE_CHANGED','SCOPE_GROUP_UNAVAILABLE','SCOPE_EMPTY'].includes(e.code))setStale(true);
    if(e.code==='IDEMPOTENCY_KEY_REUSED'){intent.current=current;setPhase('unknown');}
    setError(messages[e.code]??errorMessage(e));
   }else{setPhase('unknown');setError('Chưa xác nhận được kết quả tạo đợt. Hãy kiểm tra kết quả; hệ thống không tự gửi lại yêu cầu.');}
  }finally{end();}
 }
 function submit(e:FormEvent){e.preventDefault();if(phase!=='editing'||pending.current||stale||!scopeReady(catalog,selection)||!catalog||!selection)return;
  const current={key:crypto.randomUUID(),command:{sourceSemesterId:source.sourceSemesterId,catalogRevision:catalog.catalogRevision,scope:selection}};intent.current=current;memory.intent=current;memory.phase='unknown';void send(current);
 }
 async function checkSaved(){
  if(!begin())return;const controller=new AbortController();abort.current=controller;
  try{
   let view=saved;
   if(!view){
    if(!intent.current)throw new Error('Chưa có yêu cầu tạo để kiểm tra.');
    const found=await lookupCreation(client,intent.current.key,controller.signal);
    if(!alive.current)return;
    if(!found){setError('Chưa thấy yêu cầu đã hoàn tất. Yêu cầu ban đầu có thể vẫn đang xử lý; hãy kiểm tra lại hoặc gửi lại chính yêu cầu đó.');return;}
    view=await readRoundContext(client,found.roundId,controller.signal);
   }
   memory.saved=view;memory.phase='saved';if(!alive.current)return;setSaved(view);setPhase('saved');await onCreated(view,true);
  }catch(e){if(alive.current&&!controller.signal.aborted&&!denied(e))setError(errorMessage(e));}finally{end();}
 }
 return <section className={styles.formArea} aria-labelledby="create-round-title">
  <h2 id="create-round-title" ref={heading} tabIndex={-1}>Tạo đợt xét</h2>
  <p>Chọn phạm vi sinh viên cho học kỳ này. Hệ thống tự cấp số và tên đợt sau khi lưu.</p>
  <dl className={styles.readOnly}><div><dt>Học kỳ xét</dt><dd>{source.academicYear} · Học kỳ {source.semesterOrder}</dd></div></dl>
  <form aria-label="Tạo đợt xét KKHT" className={styles.form} onSubmit={submit}>
   <ScopePicker client={client} sourceId={source.sourceSemesterId} value={selection} onChange={value=>{memory.selection=value;setSelection(value);}} onCatalog={data=>{setCatalog(data);if(data)setStale(false);}} onForbidden={onForbidden} disabled={busy||phase!=='editing'}/>
   {error&&<Alert>{error}</Alert>}
   {phase==='unknown'&&<div className={styles.recovery}>
    <Button disabled={busy} onClick={()=>void checkSaved()}>Kiểm tra kết quả tạo đợt</Button>
    <Button variant="secondary" disabled={busy} onClick={()=>{if(intent.current)void send(intent.current);}}>Gửi lại cùng yêu cầu</Button>
   </div>}
   <div className={styles.actions}>
    {phase==='editing'&&<Button type="submit" disabled={busy||stale||!source.selectable||!scopeReady(catalog,selection)}>{busy?'Đang tạo…':'Tạo đợt xét'}</Button>}
    {phase==='saved'&&<Button disabled={busy} onClick={()=>void checkSaved()}>Đọc lại kết quả</Button>}
    <Button variant="secondary" disabled={busy} onClick={onCancel}>Quay lại danh sách</Button>
   </div>
  </form>
 </section>;
}
