import {assignmentError} from '../api/errors';
import {useEffect,useMemo,useRef,useState} from 'react';
import type {FeaturePageProps} from '../../../shared/types/module';
import {assignmentsApi} from '../api/assignments';
import type {Faculty,Page,Summary,Draft} from '../types/assignments';
import {listActionLabel,statusLabels} from '../model/draft.mjs';
import {Button} from '../../../shared/ui/Button';
import {Alert} from '../../../shared/ui/Alert';
import {Pager,formatTime} from '../components/Controls';
import {YearCombobox} from '../components/YearCombobox';
import {CreateDraft} from '../components/CreateDraft';
import {DraftEditor} from '../components/DraftEditor';
import styles from './Assignments.module.css';

export function FacultyAssignmentsPage({client}:FeaturePageProps){
 const api=useMemo(()=>assignmentsApi(client),[client]);
 const[faculties,setFaculties]=useState<Faculty[]|null>(null),[faculty,setFaculty]=useState(''),[year,setYear]=useState(''),[version,setVersion]=useState(''),[data,setData]=useState<Page<Summary>|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[create,setCreate]=useState(false),[detail,setDetail]=useState<Draft|null>(null),[id,setId]=useState(()=>new URLSearchParams(location.search).get('danhSachId'));
 const request=useRef(0);
 const[deleting,setDeleting]=useState(false),[notice,setNotice]=useState('');

 async function scope(){
  try{
   const v=await api.scope();
   setFaculties(v.khoa);
   const eligible=v.khoa.filter(k=>k.coTheChon&&(k.duocDoc||k.duocLapNhap||k.duocKySo||k.duocGuiChuyenVien));
   setFaculty(eligible.length===1?eligible[0].maKhoa:'');
  }catch{
   setFaculties([]);
   setError('Chưa xác minh được quyền và phạm vi SHCN.');
  }
 }

 useEffect(()=>{
  void scope();
  const visible=()=>{if(document.visibilityState==='visible')void scope();};
  document.addEventListener('visibilitychange',visible);
  return()=>document.removeEventListener('visibilitychange',visible);
 },[api]);

 useEffect(()=>{
  let previous=location.href;
  const pop=()=>{
   if(!window.dispatchEvent(new Event('shcn-leave',{cancelable:true}))){
    history.pushState(null,'',previous);
    return;
   }
   previous=location.href;
   setId(new URLSearchParams(location.search).get('danhSachId'));
  };
  window.addEventListener('popstate',pop);
  return()=>window.removeEventListener('popstate',pop);
 },[]);

 useEffect(()=>{
  if(!id){setDetail(null);return;}
  let alive=true;
  setBusy(true);
  setError('');
  api.detail(id)
   .then(d=>{if(alive){setDetail(d);setFaculty(d.maKhoa);setYear(d.namHoc);}})
   .catch(()=>{if(alive)setError('Không tìm thấy danh sách hoặc bạn không có quyền truy cập.');})
   .finally(()=>{if(alive)setBusy(false);});
  return()=>{alive=false;};
 },[id,api]);

 function navigate(d:Draft|null){
  const url=new URL(location.href);
  if(d)url.searchParams.set('danhSachId',d.id);
  else url.searchParams.delete('danhSachId');
  history.pushState(null,'',url);
  setId(d?.id??null);
  setDetail(d);
  setCreate(false);
 }

 async function load(page=0){
  if(!faculty||!year||year.trim()!==year||year.length>20){
   setError('Chọn khoa và nhập nhãn năm học hợp lệ (1–20 ký tự, không khoảng trắng đầu/cuối).');
   return;
  }
  const generation=++request.current;
  setBusy(true);
  setError('');
  try{
   const result=await api.versions(faculty,year,page,version?Number(version):undefined);
   if(generation===request.current)setData(result);
  }catch{
   if(generation===request.current)setError('Chưa tải được danh sách phiên bản. Hãy kiểm tra bộ lọc và thử lại.');
  }finally{
   if(generation===request.current)setBusy(false);
  }
 }

 async function remove(row:Summary){
  setDeleting(true);setError('');setNotice('');let committed=false;
  try{
   const current=await api.detail(row.id);
   if(current.trangThai!=='NHAP')throw new Error('Danh sách không còn là bản nháp. Hãy tải lại danh sách.');
   if(!window.confirm(`Xóa phân công phiên bản ${current.phienBan} của năm ${current.namHoc}? Bản nháp sẽ được bỏ khỏi danh sách; lịch sử vẫn được giữ.`))return;
   await api.remove(current.id,current.version);committed=true;setNotice('Đã xóa bản nháp.');
   let refreshed=await api.versions(faculty,year,data?.page??0,version?Number(version):undefined);
   if(!refreshed.items.length&&refreshed.page>0)refreshed=await api.versions(faculty,year,refreshed.page-1,version?Number(version):undefined);
   setData(refreshed);
  }catch(e){
   setError(committed?'Đã xóa nháp nhưng chưa tải lại được danh sách. Hãy bấm Xem danh sách.':assignmentError(e));
  }finally{setDeleting(false);}
 }

 const selected=faculties?.find(f=>f.maKhoa===(detail?.maKhoa??faculty));
 if(detail&&faculties){
  if(!selected||!(selected.duocDoc||selected.duocLapNhap||selected.duocKySo||selected.duocGuiChuyenVien)){
   return <Alert>Bạn không còn quyền xem phân công này.</Alert>;
  }
  return (
   <DraftEditor
    key={detail.id}
    client={client}
    api={api}
    initial={detail}
    faculty={selected}
    back={()=>{navigate(null);void load();}}
    saved={()=>{setData(null);}}
   />
  );
 }

 return (
  <section className={styles.workspace}>
   <div className={styles.heading}>
    <div>
     <h1>Phân công GVCN</h1>
     <p>Quản lý danh sách phân công theo khoa và năm học</p>
     <p>Phiên bản được đánh số riêng theo khoa và năm học. Tạo phân công mới làm tăng phiên bản; lưu nháp hoặc hủy chờ ký để sửa trên bản đang mở không tăng phiên bản.</p>
    </div>
    <Button disabled={busy||deleting||!selected?.duocLapNhap||!selected?.coTheChon} onClick={()=>setCreate(true)}>Tạo phân công GVCN</Button>
   </div>
   {!faculties&&<p role="status">Đang kiểm tra phạm vi…</p>}
   {faculties?.length===0&&<p>Chưa có khoa được phân quyền cho tác vụ này.</p>}
   {faculties&&faculties.filter(k=>k.coTheChon&&(k.duocDoc||k.duocLapNhap||k.duocKySo||k.duocGuiChuyenVien)).length>1&&(
    <Alert>Tài khoản đang được gán nhiều khoa. Cần xác định khoa mặc định trước khi lập phân công.</Alert>
   )}
   {error&&<Alert>{error} <Button variant="secondary" onClick={()=>void scope()}>Kiểm tra lại quyền</Button></Alert>}
   <form className={styles.filters} onSubmit={e=>{e.preventDefault();void load();}}>
    <label>
     Khoa
     <input readOnly aria-readonly="true" value={selected?`${selected.tenKhoa} (${selected.maKhoa})`:'Chưa xác định khoa mặc định'} title={selected?.tenKhoa}/>
    </label>
    <YearCombobox label="Năm học" value={year} disabled={deleting} required known={data?.items.map(item=>item.namHoc)??[]} onChange={value=>{++request.current;setBusy(false);setYear(value);setData(null);}}/>
    <label>
     Phiên bản (tùy chọn)
     <input type="number" disabled={deleting} min="1" max="2147483647" step="1" value={version} onChange={e=>{++request.current;setVersion(e.target.value);setData(null);}}/>
    </label>
    <Button className={styles.filterSubmit} type="submit" disabled={busy||deleting||!faculty||!year}>Xem danh sách</Button>
   </form>
   {notice&&<p role="status">{notice}</p>}
   {deleting&&<p role="status">Đang xử lý xóa nháp…</p>}
   {busy&&<p role="status">Đang tải…</p>}
   {data&&<>
    <div className={styles.table}>
     <table>
      <thead>
       <tr>{['Phiên bản','Năm học','Trạng thái','Số lớp đã phân công','Đề nghị hiệu lực','Cập nhật','Thao tác'].map(h=><th key={h}>{h}</th>)}</tr>
      </thead>
      <tbody>
       {data.items.map(d=>(
        <tr key={d.id}>
         <td>Phiên bản {d.phienBan}</td>
         <td>{d.namHoc}</td>
         <td><span className={styles.badge}>{statusLabels[d.trangThai as keyof typeof statusLabels]??d.trangThai}</span></td>
         <td>{d.trangThai==='NHAP'&&typeof selected?.soLopHoatDong==='number'?`${d.soLop} / ${selected.soLopHoatDong}`:d.soLop}</td>
         <td>{formatTime(d.hieuLucTu)} → {formatTime(d.hieuLucDen)}</td>
         <td>{formatTime(d.ngayCapNhat)}</td>
         <td>
          <div className={styles.rowActions}>
           <Button variant="secondary" onClick={()=>{setId(d.id);const u=new URL(location.href);u.searchParams.set('danhSachId',d.id);history.pushState(null,'',u);}}>
            {listActionLabel(d,selected)}
           </Button>
           {d.trangThai==='NHAP'&&selected?.duocLapNhap&&(
            <Button variant="danger" disabled={busy||deleting} onClick={()=>void remove(d)}>Xóa</Button>
           )}
          </div>
         </td>
        </tr>
       ))}
      </tbody>
     </table>
    </div>
    {!data.items.length&&<p>Chưa có danh sách phân công cho khoa và năm học này.</p>}
    <Pager page={data.page} total={data.totalElements} change={p=>void load(p)}/>
   </>}
   {create&&<CreateDraft api={api} faculty={faculty} year={year} close={()=>setCreate(false)} created={navigate}/>}
  </section>
 );
}
