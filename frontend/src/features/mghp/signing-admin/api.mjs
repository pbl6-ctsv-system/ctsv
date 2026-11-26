const BASE='/api/ctsv/mghp/quan-tri-ky';
export function createSigningAdminApi(client){
 async function read(path,signal){const r=await client.request(BASE+path,{signal});return {data:await r.json(),tag:r.headers.get('ETag')??''};}
 async function write(path,body={},tag){const r=await client.request(BASE+path,{method:'POST',headers:{'Content-Type':'application/json',...(tag?{'If-Match':tag}:{})},body:JSON.stringify(body)});return {data:await r.json(),tag:r.headers.get('ETag')??''};}
 return {
  async access(signal){const r=await client.request('/api/ctsv/nen-tang/toi/quyen-giao-dien',{signal});return r.json();},
  incidents:(status='waiting',q='',page=0,signal)=>read('/su-co?'+new URLSearchParams({status,q,page:String(page),size:'20'}),signal),
  incident:(id,signal)=>read('/su-co/'+id,signal),
  change:(id,action,tag)=>write('/su-co/'+id+'/'+(action==='start'?'bat-dau':'ket-thuc'),{},tag),
  note:(kind,id,text,reference,tag)=>write('/'+(kind==='incident'?'su-co':'chung-thu')+'/'+id+'/ghi-chu',{text,...(reference?{reference}:{})},tag),
  files:(filter='all',signal)=>read('/tep-luu-cho?filter='+filter,signal),
  deleteFile:id=>write('/tep-luu-cho/'+id+'/xoa',{confirm:true}),
  certificates:(filter='all',signal)=>read('/chung-thu?filter='+filter,signal),
  certificate:(id,signal)=>read('/chung-thu/'+id,signal),
  check:id=>write('/chung-thu/'+id+'/kiem-tra'),
  checkAll:()=>write('/chung-thu/kiem-tra'),
  renew:(id,reason,tag)=>write('/chung-thu/'+id+'/gia-han',{reason},tag),
  checkRenewal:id=>write('/chung-thu/'+id+'/kiem-tra-gia-han'),
 };
}
