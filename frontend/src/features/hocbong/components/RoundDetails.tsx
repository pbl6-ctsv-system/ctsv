import type {RoundDetail} from '../types/round';
import {Button} from '../../../shared/ui/Button';
import styles from '../pages/KkhtRoundsPage.module.css';

export const roundStatus=(status:string)=>status==='draft'?'Nháp':'Chưa xác định';
function date(value:string|null){
 if(value===null)return 'Chưa có';
 const [year,month,day]=value.split('-');
 return `${day}/${month}/${year}`;
}
function instant(value:string|null){
 return value===null?'Chưa cấu hình':new Intl.DateTimeFormat('vi-VN',{dateStyle:'short',timeStyle:'short'}).format(new Date(value));
}

export function RoundDetails({round,onSemester}:{round:RoundDetail;onSemester:()=>void}){
 return <div className={styles.detailGrid}>
  <section className={styles.panel} aria-labelledby="round-information">
   <h3 id="round-information">Thông tin đợt xét</h3>
   <dl className={styles.facts}>
    <div><dt>Tên đợt</dt><dd>{round.name}</dd></div>
    <div><dt>Đợt</dt><dd>{round.number}</dd></div>
    <div><dt>Trạng thái đợt</dt><dd><span className={styles.badge}>{roundStatus(round.status)}</span></dd></div>
   </dl>
  </section>
  <section className={styles.panel} aria-labelledby="semester-information">
   <h3 id="semester-information">Học kỳ học bổng</h3>
   <dl className={styles.facts}>
    <div><dt>Năm học</dt><dd>{round.semester.academicYear}</dd></div>
    <div><dt>Học kỳ</dt><dd>Học kỳ {round.semester.semesterOrder}</dd></div>
    <div><dt>Ngày bắt đầu</dt><dd>{date(round.semester.startDate)}</dd></div>
    <div><dt>Ngày kết thúc</dt><dd>{date(round.semester.endDate)}</dd></div>
    <div><dt>Trạng thái học kỳ</dt><dd>{roundStatus(round.semester.status)}</dd></div>
   </dl>
   <Button variant="secondary" onClick={onSemester}>Xem các đợt cùng học kỳ</Button>
  </section>
  <section className={`${styles.panel} ${styles.fullWidth}`} aria-labelledby="round-configuration">
   <h3 id="round-configuration">Cấu hình xét</h3>
   <dl className={styles.facts}>
    <div><dt>Thời gian mở</dt><dd>{instant(round.opensAt)}</dd></div>
    <div><dt>Thời gian chốt</dt><dd>{instant(round.closedAt)}</dd></div>
    <div><dt>Mốc dữ liệu học phí</dt><dd>{instant(round.tuitionSnapshotAt)}</dd></div>
    <div><dt>Phiên quy tắc</dt><dd>{round.ruleVersionId===null?'Chưa cấu hình':round.ruleVersionId}</dd></div>
   </dl>
  </section>
 </div>;
}
