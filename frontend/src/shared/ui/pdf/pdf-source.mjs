// All consumers use the existing authenticated transport. No tokens, provider URLs or cache here.
export function pdfErrorText(error){
 if(error?.code==='FILE_INTEGRITY_FAILED')return 'Hệ thống chưa xác minh được tính toàn vẹn của tệp. Vui lòng thử mở lại hoặc báo CTSV kiểm tra tài liệu.';
}
export async function readPdfBytes(client,fileId,signal){
 signal.throwIfAborted();
 const response=await client.request('/api/ctsv/tep/'+encodeURIComponent(fileId)+'/noi-dung',{signal});
 signal.throwIfAborted();
 if(!response.headers.get('Content-Type')?.toLowerCase().includes('application/pdf'))throw new Error('Nội dung nhận được không phải PDF.');
 const bytes=new Uint8Array(await response.arrayBuffer());
 signal.throwIfAborted();
 if(!new TextDecoder('ascii').decode(bytes.subarray(0,1024)).includes('%PDF-'))throw new Error('Nội dung nhận được không phải PDF.');
 return bytes;
}
