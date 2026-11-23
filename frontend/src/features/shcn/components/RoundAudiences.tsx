import {useEffect,useState} from 'react';
import type {Audience,RoundGroups} from '../types/rounds';
import type {RoundsApi} from '../api/rounds';
import {roundError} from '../model/round.mjs';
import {Alert} from '../../../shared/ui/Alert';
import {Button} from '../../../shared/ui/Button';
import styles from '../pages/Rounds.module.css';

type Props={api:RoundsApi;semester:string;value:Audience[];from:string;until:string;disabled:boolean;onChange:(value:Audience[])=>void;onReady:(ready:boolean)=>void};
export function RoundAudiences({api,semester,value,from,until,disabled,onChange,onReady}:Props){
 const [catalog,setCatalog]=useState<RoundGroups|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState(''),[retry,setRetry]=useState(0);
 useEffect(()=>{const abort=new AbortController();setCatalog(null);setError('');onReady(false);
  if(!semester){setBusy(false);return ()=>abort.abort();}
  setBusy(true);void api.groups(semester,abort.signal).then(data=>{if(!abort.signal.aborted){setCatalog(data);onReady(true);}}).catch(e=>{if(!abort.signal.aborted)setError(roundError(e));}).finally(()=>{if(!abort.signal.aborted)setBusy(false);});
  return ()=>abort.abort();
 },[api,semester,retry,onReady]);
 const ren=value.filter(v=>v.loaiDoiTuong==='LOP');
 function fresh(type:Audience['loaiDoiTuong'],code:string):Audience{return {loaiDoiTuong:type,maDoiTuong:code,hinhThucChoPhep:'',khungNgayBatDau:from||null,khungNgayKetThuc:until||null,khungTietChoPhep:null};}
 function replace(type:Audience['loaiDoiTuong'],code:string,next:Audience|null){onChange([...value.filter(v=>!(v.loaiDoiTuong===type&&v.maDoiTuong===code)),...(next?[next]:[])]);}
 function row(name:string,current:Audience|undefined,toggle:(yes:boolean)=>void,update:(change:Partial<Audience>)=>void,key:string,locked=false){
  return <tr key={key}><th scope="row"><label className={styles.audienceCheck}><input type="checkbox" aria-label={'Chọn '+name} disabled={disabled||busy||!!error||locked} checked={!!current} onChange={e=>toggle(e.target.checked)}/><span>{name}</span></label></th>
   <td><label className={styles.mobileLabel}>Từ ngày<input aria-label={'Từ ngày – '+name} type="date" max="9999-12-31" disabled={disabled||!current||busy||!!error} value={current?.khungNgayBatDau??''} onChange={e=>update({khungNgayBatDau:e.target.value||null})}/></label></td>
   <td><label className={styles.mobileLabel}>Đến ngày<input aria-label={'Đến ngày – '+name} type="date" max="9999-12-31" disabled={disabled||!current||busy||!!error} value={current?.khungNgayKetThuc??''} onChange={e=>update({khungNgayKetThuc:e.target.value||null})}/></label></td>
   <td><label className={styles.mobileLabel}>Hình thức<select aria-label={'Hình thức – '+name} disabled={disabled||!current||busy||!!error} required={!!current} value={current?.hinhThucChoPhep??''} onChange={e=>update({hinhThucChoPhep:e.target.value})}><option value="">Chọn…</option><option value="OFFLINE">Trực tiếp</option><option value="ONLINE">Trực tuyến</option><option value="CA_HAI">Cả hai</option></select></label></td></tr>;
 }
 const known=new Set([...(catalog?.khoaHoc??[]).map(k=>'KHOA_HOC:'+k.ma),...(catalog?.lopTreTienDo??[]).map(l=>'LOP:'+l.ma)]);
 const unknown=catalog?value.filter(v=>!known.has(v.loaiDoiTuong+':'+v.maDoiTuong)):[];
 return <section aria-labelledby="round-audiences-title" className={styles.audienceSection}>
  <h2 id="round-audiences-title">Đối tượng, thời gian &amp; hình thức áp dụng</h2>
  <p>Mỗi khóa hoặc nhóm lớp có khung ngày và hình thức riêng. Để trống cả hai mốc ngày để dùng khung chung của đợt.</p>
  {!semester?<p>Chọn học kỳ để tải các nhóm lớp.</p>:busy?<p role="status">Đang tải nhóm lớp theo học kỳ…</p>:null}
  {error&&<><Alert>{error}</Alert><Button variant="secondary" disabled={disabled||busy} onClick={()=>setRetry(x=>x+1)}>Tải lại nhóm lớp</Button></>}
  {catalog&&<>
   {catalog.soLopChuaCoKhoa>0&&<p className={styles.warning}>{catalog.soLopChuaCoKhoa} lớp nguồn chưa có liên kết khóa hợp lệ; chưa đưa vào lựa chọn theo khóa.</p>}
   <div className={styles.table}><table className={styles.audienceTable}><thead><tr><th>Đối tượng</th><th>Từ ngày</th><th>Đến ngày</th><th>Hình thức</th></tr></thead><tbody>
    {catalog.khoaHoc.map(k=>{const current=value.find(v=>v.loaiDoiTuong==='KHOA_HOC'&&v.maDoiTuong===k.ma);return row(k.ten,current,yes=>replace('KHOA_HOC',k.ma,yes?fresh('KHOA_HOC',k.ma):null),patch=>current&&replace('KHOA_HOC',k.ma,{...current,...patch}),k.ma);})}
    {row('Lớp trễ tiến độ',ren[0],yes=>onChange([...value.filter(v=>v.loaiDoiTuong!=='LOP'),...(yes?catalog.lopTreTienDo.map(l=>fresh('LOP',l.ma)):[])]),patch=>onChange(value.map(v=>v.loaiDoiTuong==='LOP'?{...v,...patch}:v)),'ren',!catalog.lopTreTienDo.length&&!ren.length)}
   </tbody></table></div>
   {!catalog.khoaHoc.length&&<p>Chưa có khóa gắn với lớp trong dữ liệu nguồn.</p>}
   {catalog.trangThaiLopTreTienDo==='CHUA_CO_DU_LIEU'?<p className={styles.warning}>Chưa có danh sách phân lớp rèn luyện cho học kỳ này. Không dùng danh sách kỳ trước thay thế.</p>:<p>{catalog.lopTreTienDo.length} lớp rèn luyện có phân lớp nguồn đúng kỳ · {ren.length} lớp đang chọn.</p>}
   {ren.length>0&&<details><summary>Các lớp rèn luyện đang chọn</summary><ul>{ren.map(v=><li key={v.maDoiTuong}>{catalog.lopTreTienDo.find(l=>l.ma===v.maDoiTuong)?.ten??v.maDoiTuong}</li>)}</ul></details>}
   {ren.length>1&&ren.some(v=>v.hinhThucChoPhep!==ren[0].hinhThucChoPhep||v.khungNgayBatDau!==ren[0].khungNgayBatDau||v.khungNgayKetThuc!==ren[0].khungNgayKetThuc)&&<p className={styles.warning}>Các lớp rèn luyện đã lưu có cấu hình khác nhau. Chỉ khi bạn sửa hàng nhóm, thay đổi mới áp dụng cho các lớp trong nhóm.</p>}
   {unknown.length>0&&<section className={styles.warning}><h3>Đối tượng đã lưu chưa còn trong danh mục kỳ này</h3><p>Kiểm tra nguồn hoặc bỏ lựa chọn trước khi lưu lại cấu hình.</p>{unknown.map(v=><p key={v.loaiDoiTuong+v.maDoiTuong}>{v.loaiDoiTuong==='LOP'?'Lớp':'Khóa'} {v.maDoiTuong} <Button variant="secondary" disabled={disabled} onClick={()=>replace(v.loaiDoiTuong,v.maDoiTuong,null)}>Bỏ {v.maDoiTuong}</Button></p>)}</section>}
  </>}
 </section>;
}
