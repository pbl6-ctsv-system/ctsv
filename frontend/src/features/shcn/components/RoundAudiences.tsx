import {useEffect,useState} from 'react';
import type {Audience,RoundGroups} from '../types/rounds';
import type {RoundsApi} from '../api/rounds';
import {audienceConfigurationReady,copyAudienceConfiguration,roundError} from '../model/round.mjs';
import {Alert} from '../../../shared/ui/Alert';
import {Button} from '../../../shared/ui/Button';
import styles from '../pages/Rounds.module.css';

type Props={api:RoundsApi;semester:string;semesterName?:string;value:Audience[];disabled:boolean;onChange:(value:Audience[])=>void;onReady:(ready:boolean)=>void};
type CopyChoice={key:string;name:string;members:Audience[]};
const keyOf=(v:Audience)=>v.loaiDoiTuong+':'+v.maDoiTuong;
export function RoundAudiences({api,semester,value,disabled,onChange,onReady}:Props){
 const [catalog,setCatalog]=useState<RoundGroups|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState(''),[retry,setRetry]=useState(0);
 const [copySource,setCopySource]=useState(''),[copyTargets,setCopyTargets]=useState<string[]>([]),[copyNotice,setCopyNotice]=useState('');
 useEffect(()=>{
  const abort=new AbortController();setCatalog(null);setError('');onReady(false);setCopySource('');setCopyTargets([]);setCopyNotice('');
  if(!semester){setBusy(false);return ()=>abort.abort();}
  setBusy(true);void api.groups(semester,abort.signal).then(data=>{if(!abort.signal.aborted){setCatalog(data);onReady(true);}}).catch(e=>{if(!abort.signal.aborted)setError(roundError(e));}).finally(()=>{if(!abort.signal.aborted)setBusy(false);});
  return ()=>abort.abort();
 },[api,semester,retry,onReady]);
 const ren=value.filter(v=>v.loaiDoiTuong==='LOP');
 const locked=disabled||busy||!!error;
 function changed(next:Audience[]){setCopyNotice('');onChange(next);}
 function fresh(type:Audience['loaiDoiTuong'],code:string):Audience{return {loaiDoiTuong:type,maDoiTuong:code,hinhThucChoPhep:'',khungNgayBatDau:null,khungNgayKetThuc:null,khungTietChoPhep:null};}
 function replace(type:Audience['loaiDoiTuong'],code:string,next:Audience|null){changed([...value.filter(v=>!(v.loaiDoiTuong===type&&v.maDoiTuong===code)),...(next?[next]:[])]);}
 function row(name:string,current:Audience|undefined,toggle:(yes:boolean)=>void,update:(change:Partial<Audience>)=>void,key:string,unavailable=false){
  return <tr key={key} data-selected={!!current}><th scope="row"><label className={styles.audienceCheck}><input type="checkbox" aria-label={'Chọn '+name} disabled={locked||unavailable} checked={!!current} onChange={e=>toggle(e.target.checked)}/><span>{name}</span></label></th>
   <td><label className={styles.mobileLabel}><span>Từ ngày</span><input required={!!current} aria-label={'Từ ngày – '+name} type="date" max="9999-12-31" disabled={locked||!current} value={current?.khungNgayBatDau??''} onChange={e=>update({khungNgayBatDau:e.target.value||null})}/></label></td>
   <td><label className={styles.mobileLabel}><span>Đến ngày</span><input required={!!current} aria-label={'Đến ngày – '+name} type="date" max="9999-12-31" disabled={locked||!current} value={current?.khungNgayKetThuc??''} onChange={e=>update({khungNgayKetThuc:e.target.value||null})}/></label></td>
   <td><label className={styles.mobileLabel}><span>Hình thức</span><select aria-label={'Hình thức – '+name} disabled={locked||!current} required={!!current} value={['OFFLINE','ONLINE'].includes(current?.hinhThucChoPhep??'')?current!.hinhThucChoPhep:''} onChange={e=>update({hinhThucChoPhep:e.target.value})}><option value="">Chọn…</option><option value="OFFLINE">Offline</option><option value="ONLINE">Online</option></select></label></td></tr>;
 }
 const known=new Set([...(catalog?.khoaHoc??[]).map(k=>'KHOA_HOC:'+k.ma),...(catalog?.lopTreTienDo??[]).map(l=>'LOP:'+l.ma)]);
 const unknown=catalog?value.filter(v=>!known.has(keyOf(v))):[];
 const choices:CopyChoice[]=(catalog?.khoaHoc??[]).flatMap(k=>{const g=value.find(v=>v.loaiDoiTuong==='KHOA_HOC'&&v.maDoiTuong===k.ma);return g?[{key:keyOf(g),name:k.ten,members:[g]}]:[];});
 if(ren.length&&ren.every(v=>known.has(keyOf(v))))choices.push({key:'REN',name:'Lớp trễ tiến độ',members:ren});
 const sources=choices.filter(c=>c.members.every(audienceConfigurationReady)&&c.members.every(g=>g.khungNgayBatDau===c.members[0].khungNgayBatDau&&g.khungNgayKetThuc===c.members[0].khungNgayKetThuc&&g.hinhThucChoPhep===c.members[0].hinhThucChoPhep));
 const source=sources.find(c=>c.key===copySource);
 const targets=choices.filter(c=>c.key!==copySource&&copyTargets.includes(c.key));
 function applyCopy(){
  if(locked||!source||!targets.length)return;
  onChange(copyAudienceConfiguration(value,keyOf(source.members[0]),targets.flatMap(c=>c.members.map(keyOf))));
  setCopyNotice('Đã sao chép thời gian và hình thức.');setCopyTargets([]);
 }
 return <section aria-labelledby="round-audiences-title" className={styles.audienceSection}>
  <h2 id="round-audiences-title">Đối tượng, thời gian &amp; hình thức áp dụng</h2>
  {!semester?<p>Chọn học kỳ để tải các nhóm lớp.</p>:busy?<p role="status">Đang tải nhóm lớp…</p>:null}
  {error&&<><Alert>{error}</Alert><Button variant="secondary" disabled={disabled||busy} onClick={()=>setRetry(x=>x+1)}>Tải lại nhóm lớp</Button></>}
  {catalog&&<>
   {choices.length>1&&<details className={styles.copyPanel}><summary>Sao chép cấu hình</summary><div className={styles.copyControls}>
    <label>Sao chép từ<select aria-label="Sao chép từ" disabled={locked} value={source?copySource:''} onChange={e=>{setCopySource(e.target.value);setCopyTargets([]);setCopyNotice('');}}><option value="">Chọn nhóm đã nhập đủ</option>{sources.map(c=><option key={c.key} value={c.key}>{c.name}</option>)}</select></label>
    <fieldset disabled={locked||!source} className={styles.copyRecipients}><legend>Áp dụng cho</legend>{choices.filter(c=>c.key!==copySource).map(c=><label key={c.key} className={styles.audienceCheck}><input type="checkbox" aria-label={'Sao chép đến '+c.name} checked={copyTargets.includes(c.key)} onChange={e=>{setCopyTargets(prev=>e.target.checked?[...prev,c.key]:prev.filter(k=>k!==c.key));setCopyNotice('');}}/><span>{c.name}</span></label>)}</fieldset>
    <Button variant="secondary" disabled={locked||!source||!targets.length} onClick={applyCopy}>Áp dụng cấu hình</Button>
   </div></details>}
   {copyNotice&&<p role="status">{copyNotice}</p>}
   <div className={styles.table}><table className={styles.audienceTable}><thead><tr><th scope="col">Đối tượng</th><th scope="col">Từ ngày</th><th scope="col">Đến ngày</th><th scope="col">Hình thức</th></tr></thead><tbody>
    {catalog.khoaHoc.map(k=>{const current=value.find(v=>v.loaiDoiTuong==='KHOA_HOC'&&v.maDoiTuong===k.ma);return row(k.ten,current,yes=>replace('KHOA_HOC',k.ma,yes?fresh('KHOA_HOC',k.ma):null),patch=>current&&replace('KHOA_HOC',k.ma,{...current,...patch}),k.ma);})}
    {row('Lớp trễ tiến độ',ren[0],yes=>changed([...value.filter(v=>v.loaiDoiTuong!=='LOP'),...(yes?catalog.lopTreTienDo.map(l=>fresh('LOP',l.ma)):[])]),patch=>changed(value.map(v=>v.loaiDoiTuong==='LOP'?{...v,...patch}:v)),'ren',!catalog.lopTreTienDo.length&&!ren.length)}
   </tbody></table></div>
   {!catalog.khoaHoc.length&&<p>Chưa có dữ liệu khóa trong học kỳ này.</p>}
   {!catalog.lopTreTienDo.length&&<p className={styles.lookupHint}>Chưa có lớp trễ tiến độ trong học kỳ này.</p>}
   {catalog.soLopChuaCoKhoa>0&&<p className={styles.lookupHint}>{catalog.soLopChuaCoKhoa} lớp chưa xác định được khóa.</p>}
   {ren.length>1&&ren.some(v=>v.hinhThucChoPhep!==ren[0].hinhThucChoPhep||v.khungNgayBatDau!==ren[0].khungNgayBatDau||v.khungNgayKetThuc!==ren[0].khungNgayKetThuc)&&<p className={styles.lookupHint}>Các lớp trễ tiến độ có cấu hình khác nhau; sửa hàng nhóm sẽ áp dụng cho tất cả.</p>}
   {unknown.length>0&&<section className={styles.warning}><h3>Đối tượng không còn trong học kỳ này</h3>{unknown.map(v=><p key={keyOf(v)}>{v.loaiDoiTuong==='LOP'?'Lớp':'Khóa'} {v.maDoiTuong} <Button variant="secondary" disabled={disabled} onClick={()=>replace(v.loaiDoiTuong,v.maDoiTuong,null)}>Bỏ {v.maDoiTuong}</Button></p>)}</section>}
  </>}
 </section>;
}
