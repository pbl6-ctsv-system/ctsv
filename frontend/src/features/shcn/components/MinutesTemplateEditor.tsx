import {useEffect,useRef,useState} from 'react';
import type {MinutesTemplatesApi} from '../api/minutes-templates';
import type {MinutesTemplate,TemplateDraft,TemplateSection,TemplateField} from '../types/minutes-templates';
import {createTemplate,moveItem,sameTemplate,templateInput,templateKey,validateTemplate,templateError} from '../model/minutes-template.mjs';
import {MinutesTemplatePreview,kindName} from './MinutesTemplatePreview';
import {Modal} from './Controls';
import {Button} from '../../../shared/ui/Button';
import styles from './MinutesTemplate.module.css';
const draftOf=(m:MinutesTemplate):TemplateDraft=>m.daCoMau?{tenMau:m.tenMau??'',muc:structuredClone(m.muc)}:createTemplate(m.danhMuc);
export function MinutesTemplateEditor({api,initial,roundName,stored,reconciled,close}:{api:MinutesTemplatesApi;initial:MinutesTemplate;roundName:string;stored:(value:MinutesTemplate)=>Promise<void>;reconciled:(value:MinutesTemplate)=>Promise<void>;close:()=>void}){
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
 function field(index:number,at:number,patch:Partial<TemplateField>){section(index,{cauHinhTruong:draft.muc[index].cauHinhTruong.map((f,i)=>i===at?{...f,...patch}:f)});}
 function bad(path:string){return fields.some(f=>f===path||f.startsWith(path+'.'));}
 async function save(){
  setError('');setFields([]);try{validateTemplate(draft,base.danhMuc);}catch(e){const p=e as Error&{field?:string};setError(p.message);setFields(p.field?[p.field]:[]);return;}
  setBusy(true);try{const result=await api.save(base.dotId,templateInput(draft,base.dotVersion));if(alive.current){setBase(result);setDraft(draftOf(result));await stored(result);}}
  catch(e){if(!alive.current)return;const p=e as {status?:number;code?:string;fields?:string[]};setError(templateError(e));setFields(p.fields??[]);if(p.status===403||p.status===404)setForbidden(true);if(!p.status||p.status>=500||p.status===409)setUncertain(true);}
  finally{if(alive.current)setBusy(false);}
 }
 async function read(){setBusy(true);setError('');reading.current?.abort();const abort=new AbortController();reading.current=abort;try{const value=await api.read(base.dotId,abort.signal);if(alive.current&&!abort.signal.aborted)setRemote(value);}catch(e){if(alive.current&&!abort.signal.aborted)setError(templateError(e));}finally{if(alive.current&&!abort.signal.aborted)setBusy(false);}}
 function useRemote(){if(remote&&window.confirm('Dùng bản hệ thống và bỏ thay đổi mẫu đang nhập?')){setBase(remote);setDraft(draftOf(remote));setRemote(null);setUncertain(false);setError('');setFields([]);void reconciled(remote);}}
 return <Modal title={'Mẫu biên bản — '+roundName} close={quit} busy={busy}><div className={styles.workspace}>
  {error&&<div ref={errorRef} role="alert" tabIndex={-1} className={styles.error}><strong>Chưa lưu được mẫu</strong><p>{error}</p></div>}
  {uncertain&&<section className={styles.conflict}><h3>Đối chiếu mẫu trên hệ thống</h3><Button variant="secondary" disabled={busy} onClick={()=>void read()}>Đọc bản mới</Button>{remote&&<><p>{remote.daCoMau?remote.tenMau+' · '+remote.muc.length+' mục':'Chưa có mẫu trên hệ thống'}</p><p>{sameTemplate(draft,draftOf(remote))?'Nội dung hệ thống khớp bản đang nhập.':'Nội dung hệ thống khác bản đang nhập.'}</p><Button variant="secondary" disabled={busy} onClick={useRemote}>Dùng bản hệ thống</Button></>}</section>}
  {preview?<><MinutesTemplatePreview draft={draft}/><Button variant="secondary" onClick={()=>setPreview(false)}>Quay lại chỉnh mẫu</Button></>:<form noValidate onSubmit={e=>{e.preventDefault();if(!disabled)void save();}}><fieldset disabled={disabled} className={styles.editorBody}>
   <label>Tên mẫu *<input maxLength={200} value={draft.tenMau} aria-invalid={bad('tenMau')} onChange={e=>change({...draft,tenMau:e.target.value})}/></label>
   <details className={styles.contextHelp}><summary>Thông tin buổi họp — hệ thống tự điền</summary><p>Đợt, học kỳ, lớp, khoa/ngành, GVCN, lớp trưởng, ngày/tiết và phòng hoặc link theo lịch thực tế.</p></details>
   {draft.muc.map((m,i)=>{const fixed=base.danhMuc.mucHeThong.some(s=>s.maMuc===m.maMuc);return <section key={m.maMuc} className={styles.section} aria-label={'Mục '+m.tieuDe} data-invalid={bad('muc['+i+']')}>
    <div className={styles.sectionHeading}><h3>{i+1}. {m.tieuDe||'Mục mới'} {fixed&&<span className={styles.badge}>Cố định</span>}</h3>{i>0&&<div className={styles.actions}><Button variant="secondary" disabled={i<=1} aria-label={'Đưa lên '+m.tieuDe} onClick={()=>sections(moveItem(draft.muc,i,i-1))}>↑</Button><Button variant="secondary" disabled={i===draft.muc.length-1} aria-label={'Đưa xuống '+m.tieuDe} onClick={()=>sections(moveItem(draft.muc,i,i+1))}>↓</Button>{!fixed&&<Button variant="danger" aria-label={'Xóa mục '+m.tieuDe} onClick={()=>{if(window.confirm('Xóa mục này cùng các trường bên trong khi lưu mẫu?'))sections(draft.muc.filter((_,n)=>n!==i));}}>Xóa mục</Button>}</div>}</div>
    {fixed?<p>{m.maMuc==='DIEM_DANH'?'Tổng số sinh viên · Có mặt · Vắng':'Nội dung không bắt buộc.'}</p>:<>
     <label>Tiêu đề mục<input aria-label={'Tiêu đề mục '+(i+1)} maxLength={250} value={m.tieuDe} aria-invalid={bad('muc['+i+'].tieuDe')} onChange={e=>section(i,{tieuDe:e.target.value})}/></label>
     <label className={styles.check}><input type="checkbox" checked={m.batBuoc} onChange={e=>section(i,{batBuoc:e.target.checked})}/>Mục bắt buộc</label>
     {m.cauHinhTruong.map((f,j)=><div key={f.maTruong} className={styles.field} data-invalid={bad('muc['+i+'].cauHinhTruong['+j+']')}>
      <div className={styles.grid}><label>Nhãn trường<input aria-label={'Nhãn trường '+(i+1)+'.'+(j+1)} maxLength={200} value={f.nhan} aria-invalid={bad('muc['+i+'].cauHinhTruong['+j+'].nhan')} onChange={e=>field(i,j,{nhan:e.target.value})}/></label>
       <label>Kiểu dữ liệu<select aria-label={'Kiểu trường '+(i+1)+'.'+(j+1)} aria-invalid={bad('muc['+i+'].cauHinhTruong['+j+'].kieuDuLieu')} disabled={f.nguonDien==='HE_THONG'} value={f.kieuDuLieu} onChange={e=>field(i,j,{kieuDuLieu:e.target.value})}>{base.danhMuc.kieuDuLieu.map(t=><option key={t} value={t}>{kindName[t]??t}</option>)}</select></label>
       <label>Cách điền<select aria-label={'Cách điền '+(i+1)+'.'+(j+1)} aria-invalid={bad('muc['+i+'].cauHinhTruong['+j+'].nguonDien')} value={f.nguonDien} onChange={e=>field(i,j,{nguonDien:e.target.value,maNguonHeThong:null})}><option value="NGUOI_NHAP">Người dùng nhập</option><option value="HE_THONG">Hệ thống tự điền</option></select></label>
       {f.nguonDien==='HE_THONG'&&<label>Nguồn tự điền<select aria-label={'Nguồn tự điền '+(i+1)+'.'+(j+1)} aria-invalid={bad('muc['+i+'].cauHinhTruong['+j+'].maNguonHeThong')} value={f.maNguonHeThong??''} onChange={e=>{const s=base.danhMuc.nguonHeThong.find(s=>s.ma===e.target.value);field(i,j,{maNguonHeThong:s?.ma??null,...(s?{kieuDuLieu:s.kieuDuLieu}:{})});}}><option value="">Chọn nguồn…</option>{base.danhMuc.nguonHeThong.map(s=><option key={s.ma} value={s.ma}>{s.nhan}</option>)}</select></label>}
      </div><div className={styles.actions}><label className={styles.check}><input type="checkbox" checked={f.batBuoc} aria-label={'Bắt buộc trường '+(i+1)+'.'+(j+1)} onChange={e=>field(i,j,{batBuoc:e.target.checked})}/>Trường bắt buộc</label><Button variant="secondary" disabled={j===0} aria-label={'Đưa trường lên '+(i+1)+'.'+(j+1)} onClick={()=>section(i,{cauHinhTruong:moveItem(m.cauHinhTruong,j,j-1)})}>↑</Button><Button variant="secondary" disabled={j===m.cauHinhTruong.length-1} aria-label={'Đưa trường xuống '+(i+1)+'.'+(j+1)} onClick={()=>section(i,{cauHinhTruong:moveItem(m.cauHinhTruong,j,j+1)})}>↓</Button><Button variant="secondary" aria-label={'Xóa trường '+(i+1)+'.'+(j+1)} onClick={()=>section(i,{cauHinhTruong:m.cauHinhTruong.filter((_,n)=>n!==j)})}>Xóa trường</Button></div>
     </div>)}
     <Button variant="secondary" disabled={m.cauHinhTruong.length>=100} aria-label={'Thêm trường vào '+m.tieuDe} onClick={()=>section(i,{cauHinhTruong:[...m.cauHinhTruong,{maTruong:templateKey('F'),nhan:'',kieuDuLieu:'VAN_BAN',batBuoc:false,nguonDien:'NGUOI_NHAP',maNguonHeThong:null}]})}>Thêm trường</Button>
    </>}
   </section>;})}
   <Button variant="secondary" disabled={draft.muc.length>=100} onClick={()=>sections([...draft.muc,{maMuc:templateKey('M'),tieuDe:'',thuTuHienThi:0,loaiMuc:'NOI_DUNG',batBuoc:false,laMucHeThong:false,cauHinhTruong:[{maTruong:templateKey('F'),nhan:'Nội dung',kieuDuLieu:'VAN_BAN',batBuoc:false,nguonDien:'NGUOI_NHAP',maNguonHeThong:null}]}])}>Thêm mục</Button>
   </fieldset><div className={styles.footer}><Button variant="secondary" disabled={busy} onClick={()=>setPreview(true)}>Xem trước</Button>{base.duocSua&&!forbidden&&<Button type="submit" disabled={disabled||base.daCoMau&&!dirty}>{busy?'Đang lưu…':'Lưu mẫu'}</Button>}</div>
  </form>}
 </div></Modal>;
}
