import type {Declaration,Dossier} from './dossier-types';
import {claimLabels,dateTime} from '../presentation.mjs';
import styles from './IntakeWorkspace.module.css';

const statementLabels:Record<string,string>={firstDegree:'Đang học văn bằng thứ nhất',alreadyBenefitedAtSameLevel:'Đã hưởng cùng trình độ',salariedWhileStudying:'Đang hưởng lương khi đi học'};
const personLabels:Record<string,string>={relation:'Quan hệ',fullName:'Họ và tên',citizenId:'Số CCCD',permanentAddress:'Địa chỉ thường trú',workplaceName:'Nơi công tác',employmentStatus:'Tình trạng công tác'};
export function DeclarationSummary({value,application,showClaims=true}:{value:Declaration|null;application:Dossier;showClaims?:boolean}) {
 return <>
  {showClaims&&<section className={styles.contentBlock} aria-label="Thông tin đã kê khai"><h3>Thông tin đã kê khai</h3>
   {value?<dl className={styles.details}>{Object.entries(value.claims).map(([field,text])=><div key={field}>
    <dt>{claimLabels[field as keyof typeof claimLabels]??field}</dt><dd>{value.claimDisplayNames?.[field]??text??'Chưa ghi nhận'}</dd>
   </div>)}</dl>:<p>Hồ sơ chưa có bản kê khai để hiển thị.</p>}
  </section>}
  <section className={styles.contentBlock} aria-label="Diện và học kỳ"><h3>Diện và học kỳ</h3><dl className={styles.details}>
   <div><dt>Diện miễn giảm</dt><dd>{[application.policyCode,application.policyName].filter(Boolean).join(' · ')||'Chưa ghi nhận'}</dd></div>
   <div><dt>Đợt tiếp nhận</dt><dd>{application.roundName||'Chưa ghi nhận'}</dd></div>
   <div><dt>Học kỳ của đợt</dt><dd>{application.semesterName||application.semesterCode||'Chưa ghi nhận'}</dd></div>
   <div><dt>Ngày nộp</dt><dd>{dateTime(application.submittedAt)}</dd></div>
  </dl>
  {value&&<><p className={styles.muted}>Kỳ và thời gian được hưởng do CTSV xác định sau khi xét căn cứ hợp lệ.</p>
   <dl className={styles.details}>{Object.entries(statementLabels).map(([field,label])=><div key={field}><dt>{label}</dt><dd>{value.statements[field]===true?'Có':value.statements[field]===false?'Không':'Chưa kê khai'}</dd></div>)}</dl>
   {value.relatedPeople.length>0&&<details className={styles.disclosure}><summary>Người liên quan đã kê khai ({value.relatedPeople.length})</summary>
    {value.relatedPeople.map((person,index)=><dl className={styles.details} key={index}>{Object.entries(personLabels).filter(([key])=>person[key]!=null).map(([key,label])=><div key={key}><dt>{label}</dt><dd>{person[key]}</dd></div>)}</dl>)}</details>}
  </>}
  </section>
 </>;
}
