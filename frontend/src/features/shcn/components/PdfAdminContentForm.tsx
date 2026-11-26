import {useEffect,useState} from 'react';
import type {PdfAdminContent} from '../types/assignments';
import {validatePdfAdminContent} from '../model/assignment-signing.mjs';
import {Button} from '../../../shared/ui/Button';
import styles from '../pages/Assignments.module.css';

export function PdfAdminContentForm({
 tenKhoaGocTraiPdf,
 value,
 dirty,
 busy,
 disabled,
 onChange,
 onReset,
 onSave,
}:{
 tenKhoaGocTraiPdf:string;
 value:PdfAdminContent;
 dirty:boolean;
 busy:boolean;
 disabled:boolean;
 onChange:(next:PdfAdminContent)=>void;
 onReset:()=>void;
 onSave:()=>Promise<void>;
}){
 const [open,setOpen]=useState(false);
 const [errors,setErrors]=useState<Partial<Record<keyof PdfAdminContent,string>>>({});

 useEffect(()=>{
  if(dirty||Object.keys(errors).length>0)setOpen(true);
 },[dirty,errors]);

 function updateField<K extends keyof PdfAdminContent>(key:K,nextVal:string){
  setErrors(prev=>{
   if(!prev[key])return prev;
   const copy={...prev};delete copy[key];return copy;
  });
  onChange({...value,[key]:nextVal});
 }

 async function handleSubmit(e:React.FormEvent){
  e.preventDefault();
  const found=validatePdfAdminContent(value) as Partial<Record<keyof PdfAdminContent,string>>;
  setErrors(found);
  if(Object.keys(found).length>0){
   setOpen(true);
   return;
  }
  await onSave();
 }

 return (
  <details className={styles.sideCard} open={open} onToggle={e=>setOpen((e.currentTarget as HTMLDetailsElement).open)}>
   <summary className={styles.cardSummary}>
    <span>1. Nội dung hành chính trên PDF {dirty?'(Chưa cập nhật)':''}</span>
   </summary>
   <form className={styles.metaForm} onSubmit={e=>{void handleSubmit(e);}}>
    <p className={styles.muted}>
     Chỉnh các dòng chữ hành chính trước khi ký số; bảng phân công GVCN–Lớp giữ nguyên theo dữ liệu đã lưu.
    </p>
    <div className={styles.readonlyMeta}>
     <div>
      <span>Đơn vị góc trái PDF (Cố định theo phân quyền)</span>
      <strong>{tenKhoaGocTraiPdf}</strong>
     </div>
     <div>
      <span>Chức danh người ký (Cố định)</span>
      <strong>TRƯỞNG KHOA</strong>
     </div>
    </div>

    <label>
     Kính gửi *
     <input
      value={value.kinhGui}
      maxLength={200}
      disabled={busy||disabled}
      aria-invalid={!!errors.kinhGui}
      aria-describedby={errors.kinhGui?'err-kinhGui':undefined}
      onChange={e=>updateField('kinhGui',e.target.value)}
     />
     {errors.kinhGui&&<small id="err-kinhGui" className={styles.fieldError}>{errors.kinhGui}</small>}
    </label>

    <label>
     Tiêu đề phụ / Căn cứ (tùy chọn)
     <input
      value={value.canCu}
      maxLength={500}
      disabled={busy||disabled}
      placeholder="Ví dụ: Căn cứ Thông báo số .../TB-ĐHBK..."
      aria-invalid={!!errors.canCu}
      aria-describedby={errors.canCu?'err-canCu':undefined}
      onChange={e=>updateField('canCu',e.target.value)}
     />
     {errors.canCu&&<small id="err-canCu" className={styles.fieldError}>{errors.canCu}</small>}
    </label>

    <label>
     Lời dẫn mở đầu *
     <textarea
      value={value.loiDan}
      maxLength={1000}
      disabled={busy||disabled}
      aria-invalid={!!errors.loiDan}
      aria-describedby={errors.loiDan?'err-loiDan':undefined}
      onChange={e=>updateField('loiDan',e.target.value)}
     />
     {errors.loiDan&&<small id="err-loiDan" className={styles.fieldError}>{errors.loiDan}</small>}
    </label>

    <label>
     Địa danh &amp; ngày tháng hiển thị *
     <input
      value={value.diaDanhNgayThang}
      maxLength={150}
      disabled={busy||disabled}
      aria-invalid={!!errors.diaDanhNgayThang}
      aria-describedby={errors.diaDanhNgayThang?'err-diaDanh':undefined}
      onChange={e=>updateField('diaDanhNgayThang',e.target.value)}
     />
     {errors.diaDanhNgayThang&&<small id="err-diaDanh" className={styles.fieldError}>{errors.diaDanhNgayThang}</small>}
    </label>

    <label>
     Họ tên in dưới chữ ký (tùy chọn)
     <input
      value={value.hoTenNguoiKy}
      maxLength={150}
      disabled={busy||disabled}
      placeholder="Để trống nếu chỉ hiển thị con dấu chứng thư số"
      aria-invalid={!!errors.hoTenNguoiKy}
      aria-describedby={errors.hoTenNguoiKy?'err-hoTen':undefined}
      onChange={e=>updateField('hoTenNguoiKy',e.target.value)}
     />
     {errors.hoTenNguoiKy&&<small id="err-hoTen" className={styles.fieldError}>{errors.hoTenNguoiKy}</small>}
    </label>

    <label>
     Ghi chú chân trang (tùy chọn)
     <textarea
      value={value.ghiChuChanTrang}
      maxLength={500}
      disabled={busy||disabled}
      aria-invalid={!!errors.ghiChuChanTrang}
      aria-describedby={errors.ghiChuChanTrang?'err-ghiChuChan':undefined}
      onChange={e=>updateField('ghiChuChanTrang',e.target.value)}
     />
     {errors.ghiChuChanTrang&&<small id="err-ghiChuChan" className={styles.fieldError}>{errors.ghiChuChanTrang}</small>}
    </label>

    <div className={styles.actions}>
     <Button
      type="button"
      variant="secondary"
      disabled={busy||disabled||!dirty}
      onClick={()=>{setErrors({});onReset();}}
     >
      Hoàn tác chữ
     </Button>
     <Button type="submit" disabled={busy||disabled||!dirty}>
      {busy?'Đang sinh lại PDF…':'Cập nhật bản PDF'}
     </Button>
    </div>
   </form>
  </details>
 );
}
