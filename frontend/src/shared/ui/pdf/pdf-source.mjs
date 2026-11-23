// All consumers use the existing authenticated transport. No tokens, provider URLs or cache here.
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
