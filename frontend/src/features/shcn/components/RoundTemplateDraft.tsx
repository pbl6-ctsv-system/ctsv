import {useEffect,useState} from 'react';
import {createPortal} from 'react-dom';
import type {RoundsApi} from '../api/rounds';
import type {Round} from '../types/rounds';
import type {MinutesTemplate,TemplateDraft} from '../types/minutes-templates';
import {templateError} from '../model/minutes-template.mjs';
import {MinutesTemplateEditor} from './MinutesTemplateEditor';
import {MinutesTemplatePreview} from './MinutesTemplatePreview';
import {Modal} from './Controls';
import {Button} from '../../../shared/ui/Button';
import styles from './MinutesTemplate.module.css';
export function RoundTemplateDraft({api,round,value,disabled,loaded,change,pending,conflict}:{api:RoundsApi;round:Round|null;value:TemplateDraft|null;disabled:boolean;loaded:(value:TemplateDraft|null)=>void;change:(value:TemplateDraft)=>void;pending:(ready:boolean)=>void;conflict:()=>void}){
 const [data,setData]=useState<MinutesTemplate|null>(null),[error,setError]=useState(''),[retry,setRetry]=useState(0),[open,setOpen]=useState<'edit'|'view'|null>(null);
 useEffect(()=>{const abort=new AbortController();setData(null);setError('');pending(false);
  const read=round?api.templates.read(round.id,abort.signal):api.templates.catalog(abort.signal).then(danhMuc=>({id:null,dotId:'',dotVersion:'',daCoMau:false,duocSua:true,tenMau:null,trangThai:null,muc:[],danhMuc} as MinutesTemplate));
  void read.then(m=>{if(abort.signal.aborted)return;if(round&&m.dotVersion!==round.version){conflict();throw new Error('Đợt đã thay đổi. Dùng phần đối chiếu bên dưới để đọc bản mới.');}setData(m);loaded(m.daCoMau?{tenMau:m.tenMau??'',muc:structuredClone(m.muc)}:null);pending(true);}).catch(e=>{if(!abort.signal.aborted)setError(templateError(e));});return()=>abort.abort();
 },[api,round?.id,round?.version,retry,loaded,pending,conflict]);
 const initial=data&&{...data,...(value?{daCoMau:true,tenMau:value.tenMau,muc:value.muc}:{} )};
 return <section className={styles.panel} aria-label="Mẫu biên bản của đợt"><h2>Mẫu biên bản</h2>
  {error?<div role="alert"><p>{error}</p><Button variant="secondary" disabled={disabled} onClick={()=>setRetry(n=>n+1)}>Thử tải mẫu lại</Button></div>:!data?<p role="status">Đang tải cấu trúc mẫu…</p>:<><p>{value?value.tenMau+' · '+value.muc.length+' mục':'Chưa có mẫu biên bản'}</p><div className={styles.actions}>{value&&<Button variant="secondary" disabled={disabled} onClick={()=>setOpen('view')}>Xem trước mẫu</Button>}{data.duocSua&&<Button variant="secondary" disabled={disabled} onClick={()=>setOpen('edit')}>{value?'Chỉnh sửa mẫu':'Thiết kế mẫu'}</Button>}</div></>}
  {initial&&open==='edit'&&createPortal(<MinutesTemplateEditor api={api.templates} initial={initial} roundName={round?.tenDot??'Đợt đang tạo'} stored={async()=>{}} reconciled={async()=>{}} apply={draft=>{change(draft);setOpen(null);}} close={()=>setOpen(null)}/>,document.getElementById('ctsv-main')??document.body)}
  {value&&open==='view'&&createPortal(<Modal title="Xem trước mẫu biên bản" close={()=>setOpen(null)}><div className={styles.workspace}><MinutesTemplatePreview draft={value}/></div></Modal>,document.getElementById('ctsv-main')??document.body)}
 </section>;
}
