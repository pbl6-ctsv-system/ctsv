export const applicationStates={
 submitted:'Đã nộp',withdrawn:'Đã rút',reopened:'Đã mở lại',processing:'Đang xử lý',
 supplementing:'Cần bổ sung',supplement_responded:'Sinh viên đã bổ sung',approved:'Đã duyệt V1 — hồ sơ online',rejected:'Từ chối',completed:'Đã kết thúc (dữ liệu cũ)',
 approved_v1:'Đã duyệt V1 — hồ sơ online',approved_v2:'Đã duyệt V2 — bản cứng',hard_copy_failed:'Đã từ chối bản cứng',awaiting_council:'Chờ Hội đồng',selected:'Được chọn',not_selected:'Không được chọn',
};
export const onlineFilters=[['','Tất cả'],['pending','Chờ xử lý'],['supplement_responded','Sinh viên đã bổ sung'],['approved','Đã duyệt'],['rejected','Từ chối'],['withdrawn','Đã rút']];
export const hardCopyFilters=[['','Tất cả'],['pending','Cần đối chiếu'],['approved','Đã duyệt bản cứng'],['rejected','Đã từ chối bản cứng']];
const commonLabels={submitted:'Đã nộp',processing:'Đang xử lý',supplementing:'Cần bổ sung',approved:'Đã duyệt',rejected:'Từ chối',withdrawn:'Đã rút'};
export function onlineStatusLabel(value){
 const status=value?.onlineStatus??value?.status;
 return {pending:'Chờ xét online',submitted:'Chờ xét online',processing:'Chờ xét online',supplementing:'Cần bổ sung',supplement_responded:'Sinh viên đã bổ sung',approved:'Đã duyệt online — V1',rejected:'Từ chối online',withdrawn:'Đã rút'}[status]??'Chưa xác định kết quả tiếp nhận';
}
export function hardCopyStatusLabel(value){return {not_transferred:'Chưa chuyển đối chiếu',pending:'Chờ đối chiếu',in_progress:'Đang đối chiếu',approved:'Đã duyệt bản cứng — V2',rejected:'Đã từ chối bản cứng'}[value?.hardCopyStatus]??'Chưa xác định kết quả bản cứng';}
export function applicationStatusLabel(value,audience='staff'){
 const status=value?.status??value?.workflowStatus;
 if(audience==='student'){
  if(status==='withdrawn')return 'Đã rút hồ sơ';
  if(status==='rejected')return 'Hồ sơ bị từ chối';
  if(value?.councilResult==='selected')return 'Được chọn hưởng MGHP';
  if(value?.councilResult==='not_selected')return 'Không được chọn hưởng MGHP';
  if(status==='approved')return 'Hồ sơ đã được duyệt — chờ kết quả xét MGHP';
  if(value?.onlineStatus==='approved')return 'Hồ sơ online đã được duyệt — chờ đối chiếu bản cứng';
  if(value?.onlineStatus==='supplement_responded')return 'Đã gửi bổ sung — chờ CTSV kiểm tra';
  if(status==='supplementing')return 'Cần bổ sung hồ sơ';
  if(status==='submitted')return 'Đã nộp hồ sơ';
  if(status==='processing')return 'CTSV đang xét hồ sơ online';
 }
 return commonLabels[status]??'Chưa xác định';
}
export const claimLabels={
 fullName:'Họ và tên',citizenId:'Số CCCD',birthDate:'Ngày sinh',birthPlace:'Nơi sinh',
 gender:'Giới tính',ethnicityId:'Dân tộc',classId:'Lớp',studentCode:'Mã sinh viên',
 cohortId:'Khóa học',managementUnitId:'Đơn vị quản lý',permanentAddress:'Địa chỉ thường trú',phoneNumber:'Số điện thoại',
};
export function displayName(name,code='') {
 if(/^P07-DEV-\d+/.test(code))return 'Đợt thực hành bổ sung hồ sơ';
 if(/^NAV-SYNTH-\d+/.test(code))return 'Đợt thực hành miễn giảm học phí';
 if(/^SYNTHETIC DEV P05\b/.test(name??''))return 'Hỗ trợ học phí · Mẫu thực hành';
 return name||code||'Chưa có tên';
}
export function applicationTitle(id,code) {
 return /^MGHP-[0-9a-f-]{36}$/i.test(code)?'Hồ sơ MGHP #'+id:code||'Hồ sơ MGHP #'+id;
}
export function dateTime(value) {
 return value?new Intl.DateTimeFormat('vi-VN',{dateStyle:'short',timeStyle:'short',timeZone:'Asia/Ho_Chi_Minh'}).format(new Date(value)):'Chưa có thông tin';
}

// UI guidance only: the command still validates server clock, round state and ownership.
export function submissionWindow(round,now=Date.now()) {
 const closed={closed:'Đợt đã đóng nhận hồ sơ.',completed:'Đợt đã hoàn tất.'};
 if(round.status!=='open')return {state:'closed',canStart:false,label:'Chưa thể nộp hồ sơ',hint:closed[round.status]??'Đợt chưa được mở nhận hồ sơ.'};
 const start=round.opensAt?Date.parse(round.opensAt):NaN,end=round.submissionDeadline?Date.parse(round.submissionDeadline):NaN;
 if(!Number.isFinite(start)||!Number.isFinite(end)||start>end)return {state:'unknown',canStart:false,label:'Chưa thể nộp hồ sơ',hint:'Chưa có lịch tiếp nhận hợp lệ. Vui lòng liên hệ bộ phận CTSV.'};
 if(now<start)return {state:'upcoming',canStart:false,label:'Chưa đến thời gian nhận hồ sơ',hint:'Bạn có thể kê khai và nộp từ '+dateTime(round.opensAt)+'. Hãy chuẩn bị minh chứng trước.'};
 if(now>end)return {state:'expired',canStart:false,label:'Đã hết hạn nộp hồ sơ',hint:'Đợt đã hết hạn nhận hồ sơ mới. Yêu cầu bổ sung đã có được xử lý theo hạn riêng.'};
 return {state:'receiving',canStart:true,label:'Kê khai và nộp hồ sơ',hint:'Đang nhận hồ sơ đến '+dateTime(round.submissionDeadline)+'.'};
}
