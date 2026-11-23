import {useEffect,useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {claimLabels} from '../presentation.mjs';
import {reviewComparisonValue} from './model.mjs';
import styles from './ReviewWorkspace.module.css';
type Row={field:string;declaredValue:string|null;sourceValue:string|null;status:string|null};
export function ReviewComparisons({client,rows}:{client:AuthClient;rows:Row[]}){
 const [names,setNames]=useState<Record<string,Record<string,string>>>({}),[failed,setFailed]=useState(false);
 useEffect(()=>{let live=true;setNames({});setFailed(false);
  const kinds:Record<string,string>={ethnicityId:'ethnicities',classId:'classes',cohortId:'cohorts',managementUnitId:'units'};
  void Promise.all(Object.entries(kinds).map(async([field,kind])=>{
   const ids=[...new Set(rows.filter(r=>r.field===field).flatMap(r=>[r.declaredValue,r.sourceValue]).filter((v):v is string=>!!v&&/^[1-9]\d*$/.test(v)))];
   if(!ids.length)return [field,{}] as const;
   const response=await client.request('/api/ctsv/nen-tang/danh-muc?kind='+kind+'&ids='+ids.join(','));const data=await response.json();
   return [field,Object.fromEntries(data.items.map((v:{id:string;name:string|null;code:string|null})=>[v.id,v.name||v.code||'Chưa có tên']))] as const;
  })).then(values=>{if(live)setNames(Object.fromEntries(values));}).catch(()=>{if(live)setFailed(true);});
  return()=>{live=false;};
 },[client,rows]);
 const value=(field:string,v:string|null)=>reviewComparisonValue(field,v,names);
 return <section className={styles.card}><h3>Đối chiếu thông tin sinh viên và Đào tạo</h3>{failed&&<p>Chưa tải được tên một số danh mục. Dữ liệu đối chiếu gốc được giữ nguyên.</p>}
  {!rows.length?<p>Chưa có thông tin đối chiếu cho bản nộp này.</p>:<div className={styles.table}><table><thead><tr><th>Thông tin</th><th>Sinh viên kê khai</th><th>Nguồn Đào tạo</th><th>Đối chiếu</th></tr></thead><tbody>{rows.map(r=><tr key={r.field}><th>{claimLabels[r.field as keyof typeof claimLabels]??r.field}</th><td>{value(r.field,r.declaredValue)}</td><td>{value(r.field,r.sourceValue)}</td><td>{({trung_khop:'Trùng khớp',khac_biet:'Cần đối chiếu',khong_co_nguon:'Chưa có nguồn'} as Record<string,string>)[r.status??'']??'Chưa xác định'}</td></tr>)}</tbody></table></div>}
 </section>;
}
