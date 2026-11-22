const BASE='/api/ctsv/mghp';
/** Thin API boundary: no client business selection, token storage or automatic write replay. */
export function createDecisionApi(client){
 async function read(path,signal){
  const response=await client.request(BASE+path,{signal});
  return {data:await response.json(),tag:response.headers.get('ETag')??''};
 }
 async function write(path,body,tag){
  const response=await client.request(BASE+path,{method:'POST',headers:{'Content-Type':'application/json',...(tag?{'If-Match':tag}:{})},body:JSON.stringify(body)});
  return {data:await response.json(),tag:response.headers.get('ETag')??''};
 }
 return {
  rounds:(page=0,signal)=>read('/dot-xet?page='+page+'&size=100',signal),
  round:(id,signal)=>read('/dot-xet/'+id,signal),
  source:(id,signal)=>read('/dot-xet/'+id+'/dong-xet',signal),
  list:(round,page=0,signal)=>read('/quyet-dinh?roundId='+round+'&page='+page+'&size=20',signal),
  root:(id,signal)=>read('/quyet-dinh/'+id,signal),
  version:(id,signal)=>read('/phien-ban-quyet-dinh/'+id,signal),
  lines:(id,page=0,signal)=>read('/phien-ban-quyet-dinh/'+id+'/dong?page='+page+'&size=20',signal),
  history:(id,page=0,signal)=>read('/quyet-dinh/'+id+'/lich-su?page='+page+'&size=10',signal),
  async candidates(id,signal){
   const result=[];let page=0;
   while(true){
    const {data}=await read('/phien-ban-quyet-dinh/'+id+'/ung-vien?page='+page+'&size=100',signal);
    result.push(...data.items);if(!data.hasMore)return result;
    if(!data.items.length)throw new Error('Chưa tải đủ căn cứ. Vui lòng thử đọc lại.');
    page++;
   }
  },
  create:body=>write('/quyet-dinh',body),
  meeting:(id,body,tag)=>write('/phien-ban-quyet-dinh/'+id+'/ket-luan-hop',body,tag),
  confirm:(id,reason,tag)=>write('/phien-ban-quyet-dinh/'+id+'/xac-nhan',{reason},tag),
 };
}

