import type {AuthClient} from '../../../shared/types/api';
import {DirectoryPicker} from '../../../shared/ui/DirectoryPicker';
type Link={sourceSystem:string;profileType:string;sourceId:string};
const key=(link:Record<string,any>)=>JSON.stringify([link.sourceSystem,link.profileType,link.sourceId]);
export function AccountProfileFields({client,value,onChange}:{client:AuthClient;value:Link[];onChange:(next:Link[])=>void}){
 function change(index:number,next:Link){onChange(value.map((row,i)=>i===index?next:row));}
 return <fieldset><legend>Hồ sơ nguồn đã xác minh</legend><p>Chọn đúng người trong danh bạ nguồn. Không dùng email hoặc MSSV thay định danh nguồn.</p>
 {value.map((row,index)=><fieldset key={index}><legend>Liên kết {index+1}</legend><label>Loại hồ sơ<select aria-label={'Loại hồ sơ liên kết '+(index+1)} value={row.profileType} onChange={e=>change(index,{sourceSystem:'',profileType:e.currentTarget.value,sourceId:''})}><option value="student">Sinh viên</option><option value="staff">Cán bộ</option></select></label>
  <DirectoryPicker client={client} path={'/api/ctsv/nen-tang/ho-so-nguon?profileType='+row.profileType} label={'Người liên kết '+(index+1)} required value={row.sourceId?key(row):''} getKey={key} format={item=>[item.name,item.code,item.sourceSystem].filter(Boolean).join(' · ')} selectedLabel="Hồ sơ đã lưu · đối chiếu danh bạ trước khi thay đổi" onSelect={item=>change(index,item?{sourceSystem:item.sourceSystem,profileType:item.profileType,sourceId:item.sourceId}:{sourceSystem:'',profileType:row.profileType,sourceId:''})}/>
  <button type="button" className="secondary" onClick={()=>onChange(value.filter((_,i)=>i!==index))}>Bỏ liên kết {index+1}</button>
 </fieldset>)}<button type="button" className="secondary" onClick={()=>onChange([...value,{sourceSystem:'',profileType:'student',sourceId:''}])}>Thêm liên kết đã xác minh</button>
 {value.length>0&&<label><input key={JSON.stringify(value)} type="checkbox" required/> Tôi đã đối chiếu đúng người và hồ sơ nguồn trước khi cấp liên kết.</label>}
 </fieldset>;
}
