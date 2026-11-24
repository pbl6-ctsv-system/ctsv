import {useEffect,useRef,useState} from 'react';
import type {RoundsApi} from '../api/rounds';
import type {MinutesTemplate} from '../types/minutes-templates';
import type {Round,RoundResult} from '../types/rounds';
import {templateError} from '../model/minutes-template.mjs';
import {MinutesTemplateEditor} from './MinutesTemplateEditor';
import {MinutesTemplatePreview} from './MinutesTemplatePreview';
import {Modal} from './Controls';
import {Button} from '../../../shared/ui/Button';
import styles from './MinutesTemplate.module.css';
export function MinutesTemplatePanel({api,round,updated}:{api:RoundsApi;round:Round;updated:(result:RoundResult<Round>)=>void}){
 const [data,setData]=useState<MinutesTemplate|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[retry,setRetry]=useState(0),[open,setOpen]=useState<'edit'|'view'|null>(null),[refreshNeeded,setRefreshNeeded]=useState(false);
 const alive=useRef(true);useEffect(()=>{alive.current=true;return()=>{alive.current=false;};},[]);
 useEffect(()=>{const abort=new AbortController();setBusy(true);setError('');void api.templates.read(round.id,abort.signal).then(d=>{if(!abort.signal.aborted)setData(d);}).catch(e=>{if(!abort.signal.aborted){setError(templateError(e));setData(null);}}).finally(()=>{if(!abort.signal.aborted)setBusy(false);});return()=>abort.abort();},[api,round.id,round.version,retry]);
 async function refresh(){setBusy(true);try{const root=await api.detail(round.id);if(alive.current){updated(root);setRefreshNeeded(false);setError('');}}catch(e){if(alive.current){setRefreshNeeded(true);setError('Mẫu đã lưu nhưng chưa tải lại được thông tin đợt. Hãy tải lại thông tin trước khi sửa tiếp.');}}finally{if(alive.current)setBusy(false);}}
 async function stored(value:MinutesTemplate){setData(value);setOpen(null);setNotice('Đã lưu mẫu biên bản.');await refresh();}
 return <section className={styles.panel} aria-label="Mẫu biên bản"><h2>Mẫu biên bản</h2>
  {busy&&<p role="status">Đang tải mẫu biên bản…</p>}{error&&<div role="alert" className={styles.error}><p>{error}</p><Button variant="secondary" disabled={busy} onClick={()=>{if(refreshNeeded)void refresh();else setRetry(x=>x+1);}}>{refreshNeeded?'Tải lại thông tin đợt':'Thử lại mẫu'}</Button></div>}
  {!busy&&data&&<><p>{data.daCoMau?data.tenMau:'Chưa có mẫu biên bản'}</p>{data.daCoMau&&<p>{data.muc.length} mục</p>}<div className={styles.actions}>{data.daCoMau&&<Button variant="secondary" onClick={()=>setOpen('view')}>Xem mẫu</Button>}{data.duocSua&&!refreshNeeded&&<Button variant="secondary" onClick={()=>setOpen('edit')}>{data.daCoMau?'Sửa mẫu':'Tạo mẫu biên bản'}</Button>}</div></>}
  {notice&&<p role="status">{notice}</p>}
  {data&&open==='edit'&&<MinutesTemplateEditor key={round.id} api={api.templates} initial={data} roundName={round.tenDot} stored={stored} reconciled={async value=>{setData(value);await refresh();}} close={()=>setOpen(null)}/>}
  {data&&open==='view'&&<Modal title={'Mẫu biên bản — '+round.tenDot} close={()=>setOpen(null)}><div className={styles.workspace}><MinutesTemplatePreview draft={{tenMau:data.tenMau??'',muc:data.muc}}/></div></Modal>}
 </section>;
}
