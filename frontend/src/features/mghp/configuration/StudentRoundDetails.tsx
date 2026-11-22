import {useEffect,useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import type {FormContext} from '../intake/DeclarationForm';
import styles from './StudentRoundDetails.module.css';
import {displayName,dateTime,submissionWindow} from '../presentation.mjs';
import {Alert} from '../../../shared/ui/Alert';

type PublicRound={
 id:string;code:string;name:string;description:string|null;status:string;
 opensAt:string|null;submissionDeadline:string|null;

};

export function StudentRoundDetails({client,round,status,semester}:{
 client:AuthClient;round:PublicRound;status:string;semester?:string;
}){
 const [policies,setPolicies]=useState<FormContext['policyVersions']|null>(null);
 const [failed,setFailed]=useState(false);
 const [retry,setRetry]=useState(0),[now,setNow]=useState(Date.now);
 const window=submissionWindow(round,now);
 useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),30000);return()=>clearInterval(timer);},[]);

 useEffect(()=>{
  const controller=new AbortController();
  setPolicies(null);setFailed(false);
  const path='/api/ctsv/mghp/toi/ngu-canh-ke-khai?dot_xet_id='+encodeURIComponent(round.id);
  client.request(path,{signal:controller.signal})
   .then(response=>response.json() as Promise<FormContext>)
   .then(context=>{if(!controller.signal.aborted)setPolicies(context.policyVersions);})
   .catch(()=>{if(!controller.signal.aborted)setFailed(true);});
  return ()=>controller.abort();
 },[client,round.id,retry]);

 return <section className={styles.round} aria-label="Thông tin đợt xét">
  <header className={styles.heading}><div><span className={styles.badge}>{status}</span><h2>{displayName(round.name,round.code)}</h2>{semester&&<p>{semester}</p>}</div></header>
  <dl className={styles.dates}>
   <div><dt>Bắt đầu nhận hồ sơ</dt><dd>{dateTime(round.opensAt)}</dd></div>
   <div><dt>Hạn nộp hồ sơ · giờ Việt Nam</dt><dd>{dateTime(round.submissionDeadline)}</dd></div>
  </dl>
  <section className={styles.nextStep} aria-label="Nộp hồ sơ cho đợt">
   <h3>{window.canStart?'Bạn có thể bắt đầu kê khai':window.label}</h3><p>{window.hint}</p>
   <div className={styles.actions}>{window.canStart?<a className={styles.primaryLink} href={'/mghp/ho-so?dot_xet_id='+encodeURIComponent(round.id)}>Kê khai và nộp hồ sơ</a>:<button type="button" disabled>{window.label}</button>}<a className={styles.secondaryLink} href="/mghp/ho-so">Xem hồ sơ của tôi</a></div>
  </section>
  {round.description&&<p>{round.description}</p>}
  <ol className={styles.steps} aria-label="Các bước nộp hồ sơ"><li><strong>1. Chuẩn bị giấy tờ</strong><span>Xem nhóm đối tượng và giấy cần nộp bên dưới.</span></li><li><strong>2. Kê khai và tải PDF</strong><span>Chọn nhóm phù hợp, kiểm tra thông tin và gắn minh chứng.</span></li><li><strong>3. Nộp và theo dõi</strong><span>Hồ sơ đã gửi nằm trong “Hồ sơ của tôi”.</span></li></ol>
  <section className={styles.evidence} aria-label="Minh chứng cần chuẩn bị">
   <h3>Minh chứng cần chuẩn bị</h3><p>Mở nhóm đối tượng phù hợp để xem giấy tờ. Mỗi hồ sơ áp dụng một nhóm M; bạn có thể nộp các hồ sơ phù hợp khác trong cùng đợt.</p>
   {failed&&<><Alert>Chưa tải được yêu cầu minh chứng của đợt.</Alert><button type="button" onClick={()=>setRetry(v=>v+1)}>Tải lại yêu cầu minh chứng</button></>}
   {!failed&&policies===null&&<p role="status">Đang tải yêu cầu minh chứng…</p>}
   {policies?.length===0&&<p>Đợt chưa có cấu hình minh chứng để kê khai. Vui lòng liên hệ bộ phận CTSV.</p>}
   {policies?.map((policy,index)=><details className={styles.policy} key={policy.id} open={index===0}>
    <summary>{displayName(policy.name)}<span>{policy.documentGroups.length} nhóm giấy tờ</span></summary>
    {policy.documentGroups.map(group=><div className={styles.documentGroup} key={group.id}>
     <h4>{group.name}</h4><p>Cần ít nhất {group.minimumRequired} giấy tờ trong nhóm này.</p>
     <ul>{group.documents.map(doc=><li key={doc.id}>{doc.name}</li>)}</ul>
    </div>)}
   </details>)}
  </section>
  <details className={styles.reference}><summary>Mã đợt để đối chiếu</summary><p>{round.code}</p></details>
 </section>;
}
