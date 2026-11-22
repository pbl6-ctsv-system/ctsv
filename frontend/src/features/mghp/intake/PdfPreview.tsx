import {useEffect,useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {errorMessage} from '../../../shared/api/error-message';
import styles from './IntakeWorkspace.module.css';

export function PdfPreview({client,fileId,title,onDownload}:{client:AuthClient;fileId:string;title:string;onDownload:()=>void}) {
 const [url,setUrl]=useState(''),[error,setError]=useState(''),[attempt,setAttempt]=useState(0);
 useEffect(()=>{
  let live=true,object='';const controller=new AbortController();setUrl('');setError('');
  void client.request('/api/ctsv/tep/'+fileId+'/noi-dung',{signal:controller.signal}).then(async response=>{
   const blob=await response.blob();if(!live)return;
   if(blob.type&&!blob.type.includes('pdf'))throw new Error('Nội dung nhận được không phải PDF.');
   object=URL.createObjectURL(blob);setUrl(object);
  }).catch(e=>{if(live)setError(errorMessage(e));});
  return ()=>{live=false;controller.abort();if(object)URL.revokeObjectURL(object);};
 },[client,fileId,attempt]);
 return <section className={styles.pdfPanel} aria-label="PDF đang xem">
  <div className={styles.heading}><strong>{title}</strong><button type="button" onClick={onDownload}>Tải PDF đang xem</button></div>
  {error?<div><p role="alert">Không mở được PDF: {error}</p><button type="button" className="secondary" onClick={()=>setAttempt(value=>value+1)}>Thử mở lại PDF</button></div>:url?<iframe className={styles.pdfFrame} title="Nội dung PDF đang xem" src={url}/>:<p role="status">Đang mở PDF…</p>}
 </section>;
}
