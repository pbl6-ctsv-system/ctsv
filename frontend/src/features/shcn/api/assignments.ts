import type {AuthClient} from '../../../shared/types/api';
import type {PdfGeometry} from '../../../shared/ui/pdf';
import type {Faculty,Program,SchoolClass,Lecturer,Page,Summary,Draft,PdfAdminContent,SigningDossier} from '../types/assignments';

const base='/api/shcn/khoa';

export function assignmentsApi(client:AuthClient){
 const get=async<T>(path:string,params:Record<string,string|number|undefined>={},signal?:AbortSignal):Promise<T>=>{
  const q=new URLSearchParams();Object.entries(params).forEach(([k,v])=>{if(v!==undefined&&v!=='')q.set(k,String(v));});
  return (await client.request(base+path+(q.size?'?'+q:''),{signal})).json();
 };
 const sendJson=async<T>(method:string,path:string,body:unknown):Promise<T>=>{
  const text=JSON.stringify(body);
  if(new TextEncoder().encode(text).length>1048576){
   throw new Error('Nội dung vượt giới hạn 1 MiB. Hãy giảm ghi chú, không bỏ lớp ngoài ý muốn.');
  }
  return (await client.request(base+path,{method,headers:{'Content-Type':'application/json'},body:text})).json();
 };
 return {
  scope:(signal?:AbortSignal)=>get<{khoa:Faculty[]}>('/pham-vi',{},signal),
  versions:(maKhoa:string,namHoc:string,page=0,phienBan?:number)=>get<Page<Summary>&{phienBanLonNhat?:number}>('/phan-cong',{maKhoa,namHoc,page,phienBan}),
  detail:(id:string,signal?:AbortSignal)=>get<Draft>('/phan-cong/'+encodeURIComponent(id),{},signal),
  programs:(maKhoa:string,q:string,page:number,signal?:AbortSignal)=>get<Page<Program>>('/chuong-trinh',{maKhoa,q,page},signal),
  classes:(maKhoa:string,q:string,page:number,chuongTrinhId?:string,signal?:AbortSignal)=>get<Page<SchoolClass>>('/lop',{maKhoa,q,page,chuongTrinhId},signal),
  lecturers:(maKhoa:string,q:string,page:number,signal?:AbortSignal)=>get<Page<Lecturer>>('/giang-vien',{maKhoa,q,page},signal),
  remove:async(id:string,expectedVersion:string):Promise<void>=>{
   await client.request(base+'/phan-cong/'+encodeURIComponent(id),{method:'DELETE',headers:{'Content-Type':'application/json'},body:JSON.stringify({expectedVersion})});
  },
  create:(body:unknown)=>sendJson<Draft>('POST','/phan-cong',body),
  save:(id:string,body:unknown)=>sendJson<Draft>('PUT','/phan-cong/'+encodeURIComponent(id),body),
  signingDossier:(id:string,signal?:AbortSignal)=>get<SigningDossier>('/phan-cong/'+encodeURIComponent(id)+'/ho-so-ky',{},signal),
  exportPdf:(id:string,expectedVersion:string,noiDungHanhChinh?:PdfAdminContent)=>
   sendJson<SigningDossier>('POST','/phan-cong/'+encodeURIComponent(id)+'/xuat-pdf',noiDungHanhChinh?{expectedVersion,noiDungHanhChinh}:{expectedVersion}),
  updatePdfContent:(id:string,expectedVersion:string,noiDungHanhChinh:PdfAdminContent)=>
   sendJson<SigningDossier>('PUT','/phan-cong/'+encodeURIComponent(id)+'/noi-dung-pdf',{expectedVersion,noiDungHanhChinh}),
  signPdf:(id:string,expectedVersion:string,geometry:PdfGeometry)=>
   sendJson<SigningDossier>('POST','/phan-cong/'+encodeURIComponent(id)+'/ky-so',{expectedVersion,geometry}),
  reconcileSigning:(id:string,expectedVersion:string,reason:string)=>
   sendJson<SigningDossier>('POST','/phan-cong/'+encodeURIComponent(id)+'/ky-so/doi-soat',{expectedVersion,reason}),
  submitSpecialist:(id:string,expectedVersion:string)=>
   sendJson<Draft>('POST','/phan-cong/'+encodeURIComponent(id)+'/gui-chuyen-vien',{expectedVersion}),
  reopenDraft:(id:string,expectedVersion:string,lyDoHuy?:string|null)=>
   sendJson<Draft>('POST','/phan-cong/'+encodeURIComponent(id)+'/huy-de-sua',lyDoHuy?{expectedVersion,lyDoHuy}:{expectedVersion}),
 };
}
export type AssignmentsApi=ReturnType<typeof assignmentsApi>;
