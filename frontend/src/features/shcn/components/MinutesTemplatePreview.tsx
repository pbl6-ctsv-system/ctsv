import {useState} from 'react';
import type {TemplateDraft,TemplateField} from '../types/minutes-templates';
import styles from './MinutesTemplate.module.css';
const kindName:Record<string,string>={CHUOI:'Chuỗi ngắn',VAN_BAN:'Văn bản dài',SO_NGUYEN:'Số nguyên',NGAY:'Ngày',BOOLEAN:'Có/không'};
export {kindName};
function PreviewField({field,section}:{field:TemplateField;section:string}){
 const label=field.nhan+(field.batBuoc?' *':'');const auto=field.nguonDien==='HE_THONG';
 if(field.kieuDuLieu==='BOOLEAN')return <label className={styles.check}><input type="checkbox" disabled={auto}/>{label}</label>;
 if(field.kieuDuLieu==='VAN_BAN')return <label>{label}<textarea aria-label={field.nhan} readOnly={auto} placeholder={auto?'Nội dung minh họa tự điền':''}/></label>;
 const type=field.kieuDuLieu==='SO_NGUYEN'?'number':field.kieuDuLieu==='NGAY'?'date':'text';
 const attendance:Record<string,number>={TONG_SO_SV:40,CO_MAT:38,VANG_MAT:2};
 return <label>{label}<input aria-label={field.nhan} type={type} min={section==='DIEM_DANH'?0:undefined} readOnly={auto} defaultValue={section==='DIEM_DANH'?attendance[field.maTruong]:auto?(type==='date'?'2026-12-01':type==='number'?0:'Dữ liệu minh họa'):undefined}/></label>;
}
export function MinutesTemplatePreview({draft}:{draft:TemplateDraft}){
 const [mode,setMode]=useState('OFFLINE');
 return <section className={styles.preview} aria-label="Xem trước mẫu"><h3>Xem trước mẫu — dữ liệu minh họa</h3><label>Hình thức xem trước<select aria-label="Hình thức xem trước" value={mode} onChange={e=>setMode(e.target.value)}><option value="OFFLINE">Offline</option><option value="ONLINE">Online</option></select></label>
  <h3>{draft.tenMau||'Biên bản sinh hoạt chủ nhiệm'}</h3><dl className={styles.context}><dt>Lớp</dt><dd>Lớp minh họa</dd><dt>GVCN</dt><dd>Giảng viên minh họa</dd><dt>Lớp trưởng</dt><dd>Sinh viên minh họa</dd><dt>Ngày, tiết</dt><dd>01/12/2026, tiết 11–12</dd><dt>{mode==='OFFLINE'?'Phòng':'Link họp'}</dt><dd>{mode==='OFFLINE'?'Phòng minh họa':'https://example.invalid/hop-minh-hoa'}</dd></dl>
  {draft.muc.map(m=><section key={m.maMuc} className={styles.section}><h4>{m.thuTuHienThi}. {m.tieuDe}{m.batBuoc?' *':''}</h4><div className={m.maMuc==='DIEM_DANH'?styles.attendance:undefined}>{m.cauHinhTruong.map(f=><PreviewField key={f.maTruong} field={f} section={m.maMuc}/>)}</div></section>)}
  {mode==='ONLINE'&&<label>Link minh chứng *<input type="url" aria-label="Link minh chứng" placeholder="https://…" required/></label>}
 </section>;
}
