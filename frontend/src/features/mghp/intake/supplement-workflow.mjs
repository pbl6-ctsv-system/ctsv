import {ApiError} from '../../../shared/auth/auth-client.mjs';

export async function finishGeneralSupplement(client,id,result,reason,expectedTag){
 const path='/api/ctsv/mghp/yeu-cau-bo-sung/'+id;
 const read=async()=>{const r=await client.request(path);const tag=r.headers.get('ETag');if(!tag)throw Error('Không đọc được phiên bản yêu cầu bổ sung.');return {data:await r.json(),tag};};
 let current=await read();
 if(current.data.status==='completed')return;
 if(expectedTag&&current.tag!==expectedTag)throw new ApiError(412,'VERSION_CONFLICT');
 if(!['satisfied','unsatisfied'].includes(result)||!reason.trim()||current.data.status!=='responded'||!current.data.items.length||current.data.items.some(i=>i.type!=='general'))
  throw Error('Chỉ xử lý tiếp yêu cầu chung sau khi sinh viên đã phản hồi. Hãy đọc lại hồ sơ.');
 for(const item of current.data.items){
  if(item.reviewResult===result)continue;
  await client.request('/api/ctsv/mghp/muc-bo-sung/'+item.id+'/danh-gia',{method:'PUT',headers:{'Content-Type':'application/json','If-Match':current.tag},body:JSON.stringify({responseDocumentId:null,result,reason})});
  current=await read();
 }
 await client.request(path+'/hoan-tat',{method:'POST',headers:{'Content-Type':'application/json','If-Match':current.tag},body:JSON.stringify({reason})});
}
