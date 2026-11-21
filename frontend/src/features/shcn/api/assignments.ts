import type {AuthClient} from '../../../shared/types/api';
import type {Faculty,Program,SchoolClass,Lecturer,Page,Summary,Draft} from '../types/assignments';
const base='/api/shcn/khoa';
export function assignmentsApi(client:AuthClient){
 const get=async<T>(path:string,params:Record<string,string|number|undefined>={},signal?:AbortSignal):Promise<T>=>{
  const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>{if(v!==undefined&&v!=='')q.set(k,String(v));});
  return (await client.request(base+path+(q.size?'?'+q:''),{signal})).json();
 };
 const write=async(method:string,path:string,body:unknown):Promise<Draft>=>{
  const text=JSON.stringify(body);if(new TextEncoder().encode(text).length>1048576)throw new Error('Nội dung vượt giới hạn 1 MiB. Hãy giảm ghi chú, không bỏ lớp ngoài ý muốn.');
  return (await client.request(base+path,{method,headers:{'Content-Type':'application/json'},body:text})).json();
 };
 return {
  scope:(signal?:AbortSignal)=>get<{khoa:Faculty[]}>('/pham-vi',{},signal),
  versions:(maKhoa:string,namHoc:string,page=0,phienBan?:number)=>get<Page<Summary>&{phienBanLonNhat?:number}>('/phan-cong',{maKhoa,namHoc,page,phienBan}),
  detail:(id:string)=>get<Draft>('/phan-cong/'+encodeURIComponent(id)),
  programs:(maKhoa:string,q:string,page:number,signal?:AbortSignal)=>get<Page<Program>>('/chuong-trinh',{maKhoa,q,page},signal),
  classes:(maKhoa:string,q:string,page:number,chuongTrinhId?:string,signal?:AbortSignal)=>get<Page<SchoolClass>>('/lop',{maKhoa,q,page,chuongTrinhId},signal),
  lecturers:(maKhoa:string,q:string,page:number,signal?:AbortSignal)=>get<Page<Lecturer>>('/giang-vien',{maKhoa,q,page},signal),
  remove:async(id:string,expectedVersion:string):Promise<void>=>{await client.request(base+'/phan-cong/'+encodeURIComponent(id),{method:'DELETE',headers:{'Content-Type':'application/json'},body:JSON.stringify({expectedVersion})});},
  create:(body:unknown)=>write('POST','/phan-cong',body),save:(id:string,body:unknown)=>write('PUT','/phan-cong/'+encodeURIComponent(id),body),
 };
}
export type AssignmentsApi=ReturnType<typeof assignmentsApi>;
