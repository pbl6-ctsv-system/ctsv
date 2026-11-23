const BASE='/api/ctsv';
export function createSigningApi(client){
 async function read(path,signal){const r=await client.request(BASE+path,{signal});return {data:await r.json(),tag:r.headers.get('ETag')??''};}
 async function write(path,body,tag){const r=await client.request(BASE+path,{method:'POST',headers:{'Content-Type':'application/json',...(tag?{'If-Match':tag}:{})},body:JSON.stringify(body)});return {data:await r.json(),tag:r.headers.get('ETag')??''};}
 return {
  access:signal=>read('/nen-tang/toi/quyen-giao-dien',signal),
  list:(page=0,view='all',signal)=>read('/tai-lieu-ky?size=20&page='+page+'&view='+view,signal),
  request:(id,signal)=>read('/yeu-cau-ky/'+id,signal),
  document:(id,signal)=>read('/phien-ban-van-ban/'+id,signal),
  prepare:id=>write('/phien-ban-van-ban/'+id+'/yeu-cau-ky',{}),
  sign:(id,body,tag)=>write('/yeu-cau-ky/'+id+'/gui-ky',body,tag),
  reconcile:(id,reason,tag)=>write('/yeu-cau-ky/'+id+'/doi-soat',{reason},tag),
  bind:(minutes,id,body,tag)=>write(minutes?'/mghp/bien-ban/'+id+'/van-ban':'/mghp/phien-ban-quyet-dinh/'+id+'/van-ban',body,tag),
  async upload(file){const body=new FormData();body.set('file',file);const r=await client.request(BASE+'/tep/van-ban',{method:'POST',body});return r.json();},
  async bytes(id,signal){const r=await client.request(BASE+'/tep/'+id+'/noi-dung',{signal});return new Uint8Array(await r.arrayBuffer());},
 };
}
