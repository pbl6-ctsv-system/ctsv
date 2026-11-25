const BASE='/api/ctsv/mghp';
/** Thin API boundary: no client business selection, token storage or automatic write replay. */
export function createDecisionApi(client){
 async function request(operation,path,options){
  try{
   const response=await client.request(path,options);
   return {data:await response.json(),tag:response.headers.get('ETag')??''};
  }catch(error){if(error instanceof Error)error.decisionOperation=operation;throw error;}
 }
 const read=(path,signal,operation='read')=>request(operation,BASE+path,{signal});
 const write=(path,body,tag,operation)=>request(operation,BASE+path,{method:'POST',headers:{'Content-Type':'application/json',...(tag?{'If-Match':tag}:{})},body:JSON.stringify(body)});
 return {
  semesters:(page=0,signal)=>request('semesters','/api/ctsv/nen-tang/danh-muc?kind=semesters&page='+page+'&size=100',{signal}),
  rounds:(page=0,signal,semester='')=>read('/dot-xet?'+(semester?'semesterId='+semester+'&':'')+'page='+page+'&size=100',signal,'rounds'),
  round:(id,signal)=>read('/dot-xet/'+id,signal,'round'),
  source:(id,semester,signal)=>read('/dot-xet/'+id+'/dong-xet?semesterId='+semester,signal,'source'),
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
  create:body=>write('/quyet-dinh',body,undefined,'create'),
  meeting:(id,body,tag)=>write('/phien-ban-quyet-dinh/'+id+'/ket-luan-hop',body,tag,'meeting'),
  confirm:(id,reason,tag)=>write('/phien-ban-quyet-dinh/'+id+'/xac-nhan',{reason},tag,'confirm'),
 };
}

