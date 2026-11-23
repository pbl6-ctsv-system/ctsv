import type {AuthClient} from '../../../shared/types/api';
import type {Page} from '../types/assignments';
import type {Round,RoundInput,RoundFilters,RoundPage,RoundResult,Semester,RoundGroups} from '../types/rounds';
const base='/api/shcn/dot-sinh-hoat';
export function roundsApi(client:AuthClient){
 async function get<T>(path:string,params:Record<string,string|number|undefined>={},signal?:AbortSignal):Promise<RoundResult<T>>{
  const q=new URLSearchParams();Object.entries(params).forEach(([key,value])=>{if(value!==undefined&&value!=='')q.set(key,String(value));});
  const r=await client.request(path+(q.size?'?'+q:''),{signal});return {value:await r.json(),canPrepare:r.headers.get('X-SHCN-Can-Prepare')==='true'};
 }
 async function write(method:string,path:string,body:unknown):Promise<RoundResult<Round>>{
  const text=JSON.stringify(body);if(new TextEncoder().encode(text).length>1048576)throw new Error('Nội dung vượt giới hạn 1 MiB.');
  const r=await client.request(path,{method,headers:{'Content-Type':'application/json'},body:text});return {value:await r.json(),canPrepare:r.headers.get('X-SHCN-Can-Prepare')==='true'};
 }
 return {list:(filters:RoundFilters={},page=0,signal?:AbortSignal)=>get<RoundPage>(base,{...filters,page,size:25,sort:'id:desc'},signal),
  detail:(id:string,signal?:AbortSignal)=>get<Round>(base+'/'+encodeURIComponent(id),{},signal),
  semesters:async(q:string,page:number,signal?:AbortSignal)=>(await get<Page<Semester>>('/api/shcn/tra-cuu/hoc-ky',{q,page,size:25,sort:'id:desc'},signal)).value,
  groups:async(maHocKy:string,signal?:AbortSignal)=>(await get<RoundGroups>('/api/shcn/tra-cuu/nhom-lop',{maHocKy},signal)).value,
  create:(body:RoundInput)=>write('POST',base,body),
  update:(id:string,body:RoundInput&{expectedVersion:string})=>write('PATCH',base+'/'+encodeURIComponent(id),body)};
}
export type RoundsApi=ReturnType<typeof roundsApi>;
// Public SHCN integration boundary: verify global scope using an existing read operation, no new permission endpoint.
export async function probeRoundAccess(client:AuthClient,functions:{code:string;active:boolean}[],signal:AbortSignal){
 if(!functions.some(f=>f.active&&['shcn.dot.read','shcn.dot.prepare'].includes(f.code)))return {read:false,prepare:false,error:false,sessionEnded:false};
 try{const r=await client.request(base+'?page=0&size=1',{signal});return {read:true,prepare:r.headers.get('X-SHCN-Can-Prepare')==='true',error:false,sessionEnded:false};}
 catch(e){const err=e as {status?:number};return {read:false,prepare:false,error:err.status!==403,sessionEnded:err.status===401};}
}
