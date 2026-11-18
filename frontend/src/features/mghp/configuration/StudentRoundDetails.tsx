import {useEffect,useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import type {FormContext} from '../intake/DeclarationForm';
import styles from './ConfigurationWorkspace.module.css';
import {displayName} from '../presentation.mjs';

type PublicRound={
 id:string;code:string;name:string;description:string|null;
 opensAt:string|null;submissionDeadline:string|null;
 declarationGuidance:string|null;lateSubmissionGuidance:string|null;
};

const dateTime=new Intl.DateTimeFormat('vi-VN',{
 dateStyle:'short',timeStyle:'short',timeZone:'Asia/Ho_Chi_Minh'
});

export function StudentRoundDetails({client,round,status,semester}:{
 client:AuthClient;round:PublicRound;status:string;semester?:string;
}){
 const [policies,setPolicies]=useState<FormContext['policyVersions']|null>(null);
 const [failed,setFailed]=useState(false);

 useEffect(()=>{
  const controller=new AbortController();
  const path='/api/ctsv/mghp/toi/ngu-canh-ke-khai?dot_xet_id='+encodeURIComponent(round.id);
  client.request(path,{signal:controller.signal})
   .then(response=>response.json() as Promise<FormContext>)
   .then(context=>{if(!controller.signal.aborted)setPolicies(context.policyVersions);})
   .catch(()=>{if(!controller.signal.aborted)setFailed(true);});
  return ()=>controller.abort();
 },[client,round.id]);

 return <section className={styles.card} aria-label="Thông tin đợt xét">
  <h3>{displayName(round.name,round.code)}</h3><p><span className={styles.badge}>{status}</span></p>
  <dl>
   <dt>Mã đợt</dt><dd>{round.code}</dd>
   {semester&&<><dt>Học kỳ</dt><dd>{semester}</dd></>}
   {round.opensAt&&<><dt>Mở nhận lúc</dt><dd>{dateTime.format(new Date(round.opensAt))}</dd></>}
   {round.submissionDeadline&&<><dt>Hạn nộp</dt><dd>{dateTime.format(new Date(round.submissionDeadline))}</dd></>}
  </dl>
  {round.description&&<p>{round.description}</p>}
  {round.declarationGuidance&&<div><h4>Hướng dẫn kê khai</h4><p>{round.declarationGuidance}</p></div>}
  {round.lateSubmissionGuidance&&<div><h4>Hướng dẫn nộp muộn</h4><p>{round.lateSubmissionGuidance}</p></div>}
  <div>
   <h4>Minh chứng cần chuẩn bị</h4>
   {failed&&<p role="status">Chưa tải được yêu cầu minh chứng của đợt.</p>}
   {!failed&&policies===null&&<p role="status">Đang tải yêu cầu minh chứng…</p>}
   {policies?.map(policy=><section key={policy.id}>
    <h5>{policy.name}</h5>
    {policy.documentGroups.map(group=><div key={group.id}>
     <p>{group.name} · Cần ít nhất {group.minimumRequired} mục</p>
     <ul>{group.documents.map(doc=><li key={doc.id}>{doc.name}</li>)}</ul>
    </div>)}
   </section>)}
  </div>
 </section>;
}
