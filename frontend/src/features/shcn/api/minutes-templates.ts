import type {AuthClient} from '../../../shared/types/api';
import type {MinutesTemplate,TemplateWrite,TemplateDictionary} from '../types/minutes-templates';
export function minutesTemplatesApi(client:AuthClient){
 const path=(id:string)=>'/api/shcn/dot-sinh-hoat/'+encodeURIComponent(id)+'/mau-bien-ban';
 return {
  catalog:async(signal?:AbortSignal):Promise<TemplateDictionary>=>{const r=await client.request('/api/shcn/tra-cuu/cau-truc-mau-bien-ban',{signal});return r.json();},
  read:async(id:string,signal?:AbortSignal):Promise<MinutesTemplate>=>{const r=await client.request(path(id),{signal});return r.json();},
  save:async(id:string,body:TemplateWrite):Promise<MinutesTemplate>=>{const text=JSON.stringify(body);if(new TextEncoder().encode(text).length>1048576)throw new Error('Cấu hình vượt giới hạn 1 MiB.');const r=await client.request(path(id),{method:'PUT',headers:{'Content-Type':'application/json'},body:text});return r.json();}
 };
}
export type MinutesTemplatesApi=ReturnType<typeof minutesTemplatesApi>;
