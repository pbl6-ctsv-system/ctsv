import type {Data,FieldLabels} from '../types/api';
import styles from './RecordView.module.css';
export function RecordView({data,labels}:{data:unknown;labels:FieldLabels}){
 if(typeof data==='object'&&data!==null&&'items'in data){
  const items=(data as {items:Data[]}).items;
  return <div><p>{String((data as Data).totalElements)} kết quả · trang {Number((data as Data).page)+1}</p>{items.length===0?<p>Không có dữ liệu phù hợp.</p>:<div className={styles.table}><table><thead><tr>{Object.keys(items[0]).map(k=><th key={k}>{labels[k]??k}</th>)}</tr></thead><tbody>{items.map((row,i)=><tr key={String(row.id??i)}>{Object.values(row).map((v,j)=><td key={j}>{v==null?'—':String(v)}</td>)}</tr>)}</tbody></table></div>}</div>;
 }
 if(Array.isArray(data))return <div>{data.length===0?<p>Chưa có dữ liệu.</p>:data.map((value,i)=><RecordView labels={labels} key={i} data={value}/>)}</div>;
 if(typeof data==='object'&&data!==null)return <dl className={styles.details}>{Object.entries(data).map(([key,v])=><div key={key}><dt>{labels[key]??key}</dt><dd>{typeof v==='object'&&v!==null?<RecordView labels={labels} data={v}/>:v==null?'—':typeof v==='boolean'?(v?'Có':'Không'):String(v)}</dd></div>)}</dl>;
 return <span>{String(data??'—')}</span>;
}
