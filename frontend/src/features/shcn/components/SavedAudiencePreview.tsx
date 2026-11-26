import {useEffect,useState} from 'react';
import type {RoundsApi} from '../api/rounds';
import type {Round,AudiencePreview,AppliedClass} from '../types/rounds';
import {roundError,roundDate} from '../model/round.mjs';
import {RoundStudents} from './RoundAudienceBrowser';
import {Pager} from './Controls';
import {Button} from '../../../shared/ui/Button';
import {Alert} from '../../../shared/ui/Alert';
import styles from '../pages/Rounds.module.css';

export function SavedAudiencePreview({api,round}:{api:RoundsApi;round:Round}){
 const [open,setOpen]=useState(false),[data,setData]=useState<AudiencePreview|null>(null),[text,setText]=useState(''),[q,setQ]=useState(''),[page,setPage]=useState(0),[retry,setRetry]=useState(0),[busy,setBusy]=useState(false),[error,setError]=useState(''),[selected,setSelected]=useState<AppliedClass|null>(null);
 useEffect(()=>{const abort=new AbortController();if(!open)return()=>abort.abort();setBusy(true);setError('');setData(null);
  void api.audiences(round.id,q,page,abort.signal).then(d=>{if(abort.signal.aborted)return;if(d.dotVersion!==round.version)throw new Error('Đợt đã thay đổi. Hãy mở lại chi tiết đợt để đối chiếu trước khi xem lớp.');setData(d);}).catch(e=>{if(!abort.signal.aborted)setError(roundError(e));}).finally(()=>{if(!abort.signal.aborted)setBusy(false);});return()=>abort.abort();
 },[api,open,round.id,round.version,q,page,retry]);
 return <section className={styles.audienceSection} aria-label="Đối tượng áp dụng đã lưu"><h2>Đối tượng áp dụng đã lưu</h2>
  <Button variant="secondary" onClick={()=>{setOpen(v=>!v);setSelected(null);}}>{open?'Thu gọn các lớp áp dụng':'Xem các lớp áp dụng'}</Button>
  {open&&<>
   {busy&&<p role="status">Đang tải lớp áp dụng…</p>}{error&&<><Alert>{error}</Alert><Button variant="secondary" onClick={()=>setRetry(v=>v+1)}>Thử lại lớp áp dụng</Button></>}
   {data&&!busy&&!error&&<><p>Tổng: {data.tongHop.tongSoLop} lớp · {data.tongHop.soLopThuong} lớp thường · {data.tongHop.soLopTreTienDo} lớp trễ tiến độ</p>
    {selected?<RoundStudents key={round.maHocKy+':'+selected.maLop} api={api} semester={round.maHocKy} classroom={{ma:selected.maLop,ten:selected.tenLop}} back={()=>setSelected(null)}/>:<>
     <div className={styles.lookupFilters}><label>Tìm mã hoặc tên lớp áp dụng<input maxLength={200} value={text} onChange={e=>setText(e.target.value)} onKeyDown={e=>{if(e.key==='Enter'){e.preventDefault();setQ(text.trim());setPage(0);}}}/></label><Button variant="secondary" onClick={()=>{setQ(text.trim());setPage(0);}}>Tìm</Button></div>
     <div className={styles.table}><table><thead><tr><th>Lớp</th><th>Khóa/nhóm</th><th>Khoa</th><th>Từ ngày</th><th>Đến ngày</th><th>Hình thức</th><th>Thao tác</th></tr></thead><tbody>
      {data.lopApDung.items.map(l=><tr key={l.maLop}><td>{l.tenLop}</td><td>{l.loaiLop==='TRE_TIEN_DO'?'Lớp trễ tiến độ':l.tenKhoaHoc??'Chưa có thông tin nguồn'}</td><td>{l.tenKhoa?.trim()||l.maKhoa?.trim()||'Chưa có khoa'}</td><td>{roundDate(l.tuNgay)}</td><td>{roundDate(l.denNgay)}</td><td>{{OFFLINE:'Offline',ONLINE:'Online',CA_HAI:'Cả hai'}[l.hinhThucChoPhep]??l.hinhThucChoPhep}</td><td><Button variant="secondary" onClick={()=>setSelected(l)} aria-label={'Xem SV lớp '+l.tenLop}>Xem SV</Button></td></tr>)}
      {!data.lopApDung.items.length&&<tr><td colSpan={7}>Không có lớp phù hợp bộ lọc.</td></tr>}
     </tbody></table></div><Pager page={page} total={data.lopApDung.totalElements} change={setPage}/>
    </>}
   </>}
  </>}
 </section>;
}
