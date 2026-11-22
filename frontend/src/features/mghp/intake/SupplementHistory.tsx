import {useState} from 'react';
import type {AuthClient} from '../../../shared/types/api';
import {Alert} from '../../../shared/ui/Alert';
import {errorMessage} from '../../../shared/api/error-message';
import {claimLabels,dateTime} from '../presentation.mjs';
import {declarationChanges} from './dossier-model.mjs';
import type {Dossier,Declaration,SupplementSummary} from './dossier-types';
import styles from './IntakeWorkspace.module.css';

type Changes={claims:{field:string;before:unknown;after:unknown}[];documents:{requirementId:string;before:unknown;after:unknown}[];statements:{field:string;before:unknown;after:unknown}[];relatedPeople:{index:number;fields:{field:string;before:unknown;after:unknown}[]}[]};
const personLabels:Record<string,string>={relation:'Quan hệ',fullName:'Họ và tên',citizenId:'CCCD',ethnicityId:'Dân tộc',permanentAddress:'Địa chỉ',localityCode:'Địa bàn',workplaceUnitId:'Đơn vị công tác',workplaceName:'Nơi công tác',workplaceGroup:'Nhóm đơn vị',employmentStatus:'Tình trạng công tác',statusFrom:'Ngày bắt đầu',statusUntil:'Ngày kết thúc',matchingStaffCode:'Mã cán bộ đối chiếu'};
const text=(value:unknown)=>value===true?'Có':value===false?'Không':value==null?'Chưa ghi nhận':String(value);
export function SupplementHistory({client,application}:{client:AuthClient;application:Dossier}){
 const [changes,setChanges]=useState<Record<string,Changes>>({}),[busy,setBusy]=useState(''),[error,setError]=useState('');
 async function compare(request:SupplementSummary){
  if(!request.responseVersionId||busy)return;setBusy(request.id);setError('');
  try{
   const url='/api/ctsv/mghp/ho-so/'+application.id+'/phien-ban/';
   const [before,after]=await Promise.all([request.sourceVersionId,request.responseVersionId].map(async id=>({
    ...await (await client.request(url+id)).json() as Declaration,
    documents:application.submittedVersions.find(v=>v.id===id)?.documents??[],
   })));
   setChanges(current=>({...current,[request.id]:declarationChanges(before,after)}));
  }catch(e){setError(errorMessage(e));}finally{setBusy('');}
 }
 return <section className={styles.contentBlock} aria-label="Lịch sử yêu cầu và phản hồi"><h3>Lịch sử yêu cầu và phản hồi</h3>
  {error&&<Alert>{error}</Alert>}
  {!application.supplementRequests?.length&&<p>Chưa có yêu cầu bổ sung.</p>}
  <ul className={styles.history}>{application.supplementRequests?.map(request=>{
   const source=application.submittedVersions.find(v=>v.id===request.sourceVersionId),response=application.submittedVersions.find(v=>v.id===request.responseVersionId),diff=changes[request.id];
   return <li key={request.id}><strong>Yêu cầu bổ sung lần {request.roundNumber}</strong><p>{request.reason}</p>
    <p className={styles.muted}>Đã gửi: {dateTime(request.requestedAt)} · Hạn: {dateTime(request.deadline)}</p>
    <p>Bản nền: {source?'bản nộp '+source.revision:'Chưa ghi nhận'} · Phản hồi: {response?'bản nộp '+response.revision+' · '+dateTime(request.respondedAt):'Chưa có phản hồi'}</p>
    {request.responseVersionId&&!diff&&<button type="button" disabled={!!busy} onClick={()=>void compare(request)}>Xem thay đổi sau yêu cầu lần {request.roundNumber}</button>}
    {diff&&<div className={styles.requestNote}><strong>Nội dung thay đổi so với bản nền</strong>
     {!diff.claims.length&&!diff.documents.length&&!diff.statements.length&&!diff.relatedPeople.length&&<p>Không có thay đổi ở thông tin, người liên quan, cam kết hoặc PDF được đối chiếu. Bản phản hồi và thời điểm nộp vẫn được lưu riêng.</p>}
     {diff.claims.map(item=><p key={item.field}>{claimLabels[item.field as keyof typeof claimLabels]??item.field}: {text(item.before)} → {text(item.after)}</p>)}
     {diff.statements.map(item=><p key={item.field}>{{firstDegree:'Văn bằng thứ nhất',alreadyBenefitedAtSameLevel:'Đã hưởng cùng trình độ',salariedWhileStudying:'Hưởng lương khi đi học',declarationConfirmed:'Cam kết kê khai'}[item.field]??'Cam kết'}: {text(item.before)} → {text(item.after)}</p>)}
     {diff.documents.map(item=><p key={item.requirementId}>{application.documentRequirements?.find(r=>r.id===item.requirementId)?.name||'Minh chứng PDF'}: {item.before&&item.after?'đã thay PDF hoặc thông tin giấy':item.after?'đã bổ sung':'không còn trong bản phản hồi'}</p>)}
     {diff.relatedPeople.map(person=><div key={person.index}><strong>Người liên quan {person.index+1}</strong>{person.fields.map(item=><p key={item.field}>{personLabels[item.field]??'Thông tin liên quan'}: {text(item.before)} → {text(item.after)}</p>)}</div>)}
    </div>}
   </li>;
  })}</ul>
 </section>;
}
