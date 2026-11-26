import {useEffect,useRef,useState} from 'react';
import type {MinutesTemplatesApi} from '../api/minutes-templates';
import type {MinutesTemplate,TemplateDraft,TemplateSection} from '../types/minutes-templates';
import {createTemplate,setSectionRequired,moveItem,sameTemplate,templateInput,templateKey,validateTemplate,templateError} from '../model/minutes-template.mjs';
import {MinutesTemplatePreview} from './MinutesTemplatePreview';
import {Modal} from './Controls';
import {Button} from '../../../shared/ui/Button';
import styles from './MinutesTemplate.module.css';
const draftOf=(m:MinutesTemplate):TemplateDraft=>m.daCoMau?{tenMau:m.tenMau??'',muc:structuredClone(m.muc)}:createTemplate(m.danhMuc);
export function MinutesTemplateEditor({api,initial,roundName,stored,reconciled,close,apply}:{api:MinutesTemplatesApi;initial:MinutesTemplate;roundName:string;stored:(value:MinutesTemplate)=>Promise<void>;reconciled:(value:MinutesTemplate)=>Promise<void>;close:()=>void;apply?:(draft:TemplateDraft)=>void}){
 const [base,setBase]=useState(initial),[draft,setDraft]=useState<TemplateDraft>(()=>draftOf(initial));
 const [busy,setBusy]=useState(false),[preview,setPreview]=useState(false),[error,setError]=useState(''),[fields,setFields]=useState<string[]>([]),[uncertain,setUncertain]=useState(false),[remote,setRemote]=useState<MinutesTemplate|null>(null),[forbidden,setForbidden]=useState(false);
 const errorRef=useRef<HTMLDivElement>(null),alive=useRef(true),reading=useRef<AbortController|null>(null);const dirty=!sameTemplate(draft,draftOf(base));const disabled=busy||uncertain||forbidden||!base.duocSua;
 useEffect(()=>{alive.current=true;return()=>{alive.current=false;reading.current?.abort();};},[]);
 useEffect(()=>{if(error){errorRef.current?.scrollIntoView({block:'nearest'});errorRef.current?.focus();}},[error]);
 useEffect(()=>{const warn=(e:BeforeUnloadEvent)=>{if(dirty||busy)e.preventDefault();};const leave=(e:Event)=>{if(busy||dirty&&!window.confirm('Bỏ thay đổi mẫu chưa lưu?'))e.preventDefault();};window.addEventListener('beforeunload',warn);window.addEventListener('shcn-leave',leave);return()=>{window.removeEventListener('beforeunload',warn);window.removeEventListener('shcn-leave',leave);};},[dirty,busy]);
 function quit(){if(!busy&&(!dirty||window.confirm('Bỏ thay đổi mẫu chưa lưu?')))close();}
 function change(next:TemplateDraft){setDraft(next);setError('');setFields([]);setRemote(null);}
 function sections(next:TemplateSection[]){change({...draft,muc:next.map((m,i)=>({...m,thuTuHienThi:i+1}))});}
 function section(index:number,patch:Partial<TemplateSection>){sections(draft.muc.map((m,i)=>i===index?{...m,...patch}:m));}
 function bad(path:string){return fields.some(f=>f===path||f.startsWith(path+'.')||f.startsWith(path+'['));}
 async function save(){
  setError('');setFields([]);try{validateTemplate(draft,base.danhMuc);}catch(e){const p=e as Error&{field?:string};setError(p.message);setFields(p.field?[p.field]:[]);return;}
  if(apply){apply(structuredClone(draft));return;}
  setBusy(true);try{const result=await api.save(base.dotId,templateInput(draft,base.dotVersion));if(alive.current){setBase(result);setDraft(draftOf(result));await stored(result);}}
  catch(e){if(!alive.current)return;const p=e as {status?:number;code?:string;fields?:string[]};setError(templateError(e));setFields(p.fields??[]);if(p.status===403||p.status===404)setForbidden(true);if(!p.status||p.status>=500||p.status===409)setUncertain(true);}
  finally{if(alive.current)setBusy(false);}
 }
 async function read(){setBusy(true);setError('');reading.current?.abort();const abort=new AbortController();reading.current=abort;try{const value=await api.read(base.dotId,abort.signal);if(alive.current&&!abort.signal.aborted)setRemote(value);}catch(e){if(alive.current&&!abort.signal.aborted)setError(templateError(e));}finally{if(alive.current&&!abort.signal.aborted)setBusy(false);}}
 function useRemote(){if(remote&&window.confirm('Dùng bản hệ thống và bỏ thay đổi mẫu đang nhập?')){setBase(remote);setDraft(draftOf(remote));setRemote(null);setUncertain(false);setError('');setFields([]);void reconciled(remote);}}
 return <Modal title={'Mẫu biên bản — '+roundName} close={quit} busy={busy}><div className={styles.workspace}>
  {error&&<div ref={errorRef} role="alert" tabIndex={-1} className={styles.error}><strong>Chưa lưu được mẫu</strong><p>{error}</p></div>}
  {uncertain&&<section className={styles.conflict}><h3>Đối chiếu mẫu trên hệ thống</h3><Button variant="secondary" disabled={busy} onClick={()=>void read()}>Đọc bản mới</Button>{remote&&<><p>{remote.daCoMau?remote.tenMau+' · '+remote.muc.length+' mục':'Chưa có mẫu trên hệ thống'}</p><p>{sameTemplate(draft,draftOf(remote))?'Nội dung hệ thống khớp bản đang nhập.':'Nội dung hệ thống khác bản đang nhập.'}</p><Button variant="secondary" disabled={busy} onClick={useRemote}>Dùng bản hệ thống</Button></>}</section>}
  {preview?<><MinutesTemplatePreview draft={draft}/><Button variant="secondary" onClick={()=>setPreview(false)}>Quay lại chỉnh mẫu</Button></>:<form noValidate onSubmit={e=>{e.preventDefault();e.stopPropagation();if(!disabled)void save();}}><fieldset disabled={disabled} className={styles.editorBody}>
   <label>Tên mẫu *<input maxLength={200} value={draft.tenMau} aria-invalid={bad('tenMau')} onChange={e=>change({...draft,tenMau:e.target.value})}/></label>
   {draft.muc.map((m,i)=>{const fixed=base.danhMuc.mucHeThong.some(s=>s.maMuc===m.maMuc);return <section key={m.maMuc} className={styles.section} aria-label={'Mục '+m.tieuDe} data-invalid={bad('muc['+i+']')}>
    {m.maMuc==='DIEM_DANH'?<div className={styles.attendanceLock}><strong>Điểm danh</strong><p>Tổng số sinh viên · Có mặt · Vắng mặt</p></div>:<div className={styles.sectionHeading}>
     <span className={styles.number}>{i}.</span><label className={styles.sectionTitle}><span className={styles.srOnly}>Tiêu đề mục {i+1}</span><input aria-label={'Tiêu đề mục '+(i+1)} maxLength={250} value={m.tieuDe} readOnly={fixed} aria-invalid={bad('muc['+i+'].tieuDe')} onChange={e=>{if(!fixed)section(i,{tieuDe:e.target.value});}}/></label>
     <div className={styles.rowControls}><label className={styles.required}><input type="checkbox" aria-label={'Bắt buộc mục '+m.tieuDe} checked={m.batBuoc} disabled={fixed} onChange={e=>section(i,setSectionRequired(m,e.target.checked))}/>Bắt buộc</label><div className={styles.compactActions}><Button variant="secondary" disabled={i<=1} aria-label={'Đưa lên '+m.tieuDe} onClick={()=>sections(moveItem(draft.muc,i,i-1))}>↑</Button><Button variant="secondary" disabled={i===draft.muc.length-1} aria-label={'Đưa xuống '+m.tieuDe} onClick={()=>sections(moveItem(draft.muc,i,i+1))}>↓</Button>{!fixed&&<Button variant="danger" aria-label={'Xóa mục '+m.tieuDe} onClick={()=>{if(window.confirm('Xóa mục này cùng các trường bên trong khi lưu mẫu?'))sections(draft.muc.filter((_,n)=>n!==i));}}>×</Button>}</div></div>
    </div>}
   </section>;})}
   <Button variant="secondary" disabled={draft.muc.length>=100} onClick={()=>sections([...draft.muc,{maMuc:templateKey('M'),tieuDe:'',thuTuHienThi:0,loaiMuc:'NOI_DUNG',batBuoc:false,laMucHeThong:false,cauHinhTruong:[{maTruong:templateKey('F'),nhan:'Nội dung',kieuDuLieu:'VAN_BAN',batBuoc:false,nguonDien:'NGUOI_NHAP',maNguonHeThong:null}]}])}>Thêm mục</Button>
   </fieldset><div className={styles.footer}><Button variant="secondary" disabled={busy} onClick={quit}>Hủy</Button><Button variant="secondary" disabled={busy} onClick={()=>setPreview(true)}>Xem trước</Button>{base.duocSua&&!forbidden&&<Button type="submit" disabled={disabled||base.daCoMau&&!dirty}>{busy?'Đang lưu…':apply?'Áp dụng mẫu':'Lưu mẫu'}</Button>}</div>
  </form>}
 </div></Modal>;
}
