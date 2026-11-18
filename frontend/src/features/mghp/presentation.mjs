export const applicationStates={
 submitted:'Đã nộp',withdrawn:'Đã rút',reopened:'Đã mở lại',processing:'Đang xử lý',
 supplementing:'Cần bổ sung',completed:'Hoàn tất',
};
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
