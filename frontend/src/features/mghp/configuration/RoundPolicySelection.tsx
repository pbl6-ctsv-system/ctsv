import {useEffect,useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {displayName} from '../presentation.mjs';
import styles from './ConfigurationWorkspace.module.css';

type Item=Record<string,any>;
export function RoundPolicySelection({client,policies,value,onChange,onInvalid,disabled=false}:{client:AuthClient;policies:Item[];value:Item[];onChange:(v:Item[])=>void;onInvalid:(v:boolean)=>void;disabled?:boolean}){
 const [versions,setVersions]=useState<Record<string,Item[]>>({}),[failed,setFailed]=useState<string[]>([]),[loading,setLoading]=useState(true),[retry,setRetry]=useState(0);
 useEffect(()=>{
  let live=true;setLoading(true);setFailed([]);
  void Promise.allSettled(policies.map(async p=>{const r=await client.request('/api/ctsv/mghp/chinh-sach/'+p.id+'/phien-ban');return [p.id,await r.json()] as const;})).then(results=>{
   if(!live)return;setVersions(Object.fromEntries(results.flatMap(r=>r.status==='fulfilled'?[r.value]:[])));setFailed(results.flatMap((r,i)=>r.status==='rejected'?[policies[i].id]:[]));setLoading(false);
  });return()=>{live=false;};
 },[client,policies,retry]);
 const policyOf=(item:Item)=>item.policyId??Object.keys(versions).find(id=>versions[id].some(v=>v.id===item.policyVersionId));
 const locked=(id:string)=>(versions[id]??[]).filter(v=>v.status==='locked');
 const invalid=loading||value.some(item=>!policyOf(item)||!locked(policyOf(item)!).some(v=>v.id===item.policyVersionId));
 useEffect(()=>onInvalid(invalid),[invalid,onInvalid]);
 function add(selected:Item[],id:string){const options=locked(id);return [...selected,{policyId:id,policyVersionId:options.length===1?options[0].id:'',applicationTemplateFileId:null,order:Math.max(0,...selected.map(x=>x.order??0))+1}];}
 function toggle(id:string,checked:boolean){onChange(checked?add(value,id):value.filter(v=>policyOf(v)!==id));}
 return <section className={styles.policySelection} aria-label="Chính sách áp dụng">
  <div className={styles.policyHeading}><div><h4>Chính sách áp dụng trong đợt</h4><p className={styles.status}>Đánh dấu các nhóm M được tiếp nhận và chọn phiên bản đã xác nhận cho từng nhóm.</p></div><span className={styles.badge}>Đã chọn {value.length} nhóm</span></div>
  {loading&&<p role="status">Đang tải phiên bản chính sách…</p>}
  {!!failed.length&&<p role="status">Một số phiên bản chưa tải được. <button type="button" onClick={()=>setRetry(x=>x+1)}>Tải lại phiên bản</button></p>}
  {!disabled&&<div className={styles.actions}><button type="button" disabled={loading} onClick={()=>{let next=[...value];for(const p of policies)if(locked(p.id).length&&!next.some(v=>policyOf(v)===p.id))next=add(next,p.id);onChange(next);}}>Chọn tất cả nhóm có phiên bản</button><button type="button" disabled={loading||!value.length} onClick={()=>onChange([])}>Bỏ chọn tất cả</button></div>}
  <div className={styles.tableWrap}><table className={styles.policyTable} aria-label="Chính sách áp dụng trong đợt"><thead><tr><th scope="col">Áp dụng</th><th scope="col">Nhóm đối tượng</th><th scope="col">Phiên bản áp dụng</th></tr></thead><tbody>
   {policies.filter(p=>!disabled||value.some(v=>policyOf(v)===p.id)).map(p=>{
    const selection=value.find(v=>policyOf(v)===p.id),options=locked(p.id);
    return <tr key={p.id}><td><input type="checkbox" aria-label={'Áp dụng '+p.code} checked={!!selection} disabled={disabled||loading||!options.length} onChange={e=>toggle(p.id,e.target.checked)}/></td><th scope="row"><strong>{p.code}</strong><span>{displayName(p.name)}</span></th><td>
     {disabled?<span>{options.find(v=>v.id===selection?.policyVersionId)?'Bản '+options.find(v=>v.id===selection?.policyVersionId)!.version:'Đang đọc phiên bản…'}</span>:<select aria-label={'Phiên bản '+p.code} disabled={loading||!selection||!options.length} value={selection?.policyVersionId??''} onChange={e=>onChange(value.map(v=>v===selection?{...v,policyVersionId:e.target.value}:v))}><option value="">{options.length?'Chọn phiên bản đã xác nhận':failed.includes(p.id)?'Chưa tải được phiên bản':'Chưa có phiên bản đã xác nhận'}</option>{options.map(v=><option key={v.id} value={v.id}>Bản {v.version} · Giảm {new Intl.NumberFormat('vi-VN',{style:'percent',maximumFractionDigits:3}).format(Number(v.rate))}{v.effectiveFrom?' · Từ '+v.effectiveFrom.split('-').reverse().join('/'):''}</option>)}</select>}
     {selection?.applicationTemplateFileId&&<small className={styles.status}>Có tệp mẫu đơn đã gắn với lựa chọn này.</small>}
    </td></tr>;
   })}
  </tbody></table></div>
  {!disabled&&!loading&&invalid&&<p role="status">Chọn đủ phiên bản hợp lệ cho các nhóm đã đánh dấu trước khi lưu.</p>}
 </section>;
}
