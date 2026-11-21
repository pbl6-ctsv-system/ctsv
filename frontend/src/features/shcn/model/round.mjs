export const roundStates={NHAP:'Nháp',CHO_DUYET:'Chờ duyệt',BI_TRA_LAI:'Bị trả lại',DA_DUYET:'Đã duyệt',DA_PHAT_HANH:'Đã phát hành',DA_HUY:'Đã hủy'};
export function roundInput(d){return {maHocKy:d?.maHocKy??'',tenDot:d?.tenDot??'',khungNgayBatDau:d?.khungNgayBatDau??'',khungNgayKetThuc:d?.khungNgayKetThuc??'',khungTietChoPhep:d?.khungTietChoPhep.map(r=>({...r}))??[{tietDau:0,tietCuoi:0}],hanChotDangKyLich:d?.hanChotDangKyLich??''};}
export function roundWritable(d){return ['NHAP','BI_TRA_LAI'].includes(d.trangThai)&&!['CHO_PHO_PHONG_DUYET','CHO_BGH_KY','CO_HIEU_LUC'].includes(d.trangThaiPhanCong)&&!d.ngayPhatHanh&&!d.ngayCongBoLich;}
export function validateRound(d){
 if(!d.maHocKy||d.maHocKy.length>32||/\s/u.test(d.maHocKy))throw new Error('Hãy chọn học kỳ hợp lệ.');
 if(!d.tenDot||d.tenDot.length>200||d.tenDot.trim()!==d.tenDot)throw new Error('Tên đợt cần có nội dung, tối đa 200 ký tự và không có khoảng trắng đầu/cuối.');
 if(!d.khungNgayBatDau||!d.khungNgayKetThuc||d.khungNgayBatDau>d.khungNgayKetThuc)throw new Error('Kiểm tra ngày bắt đầu và kết thúc.');
 if(!d.khungTietChoPhep.length||d.khungTietChoPhep.length>100||d.khungTietChoPhep.some(r=>!Number.isInteger(r.tietDau)||!Number.isInteger(r.tietCuoi)||r.tietDau<1||r.tietCuoi>32767||r.tietDau>r.tietCuoi))throw new Error('Mỗi khoảng cần tiết đầu/kết thúc là số nguyên hợp lệ, tiết cuối không trước tiết đầu.');
 if(!d.hanChotDangKyLich||!Number.isFinite(Date.parse(d.hanChotDangKyLich)))throw new Error('Hãy nhập hạn chốt lịch hợp lệ.');
 return roundInput(d);
}
export function sameRound(a,b){return JSON.stringify({...roundInput(a),hanChotDangKyLich:timeKey(a.hanChotDangKyLich)})===JSON.stringify({...roundInput(b),hanChotDangKyLich:timeKey(b.hanChotDangKyLich)});}
export function roundError(e){
 const messages={FORBIDDEN:'Bạn chưa được cấp quyền SHCN phù hợp.',NOT_FOUND:'Không tìm thấy đợt SHCN.',VERSION_CONFLICT:'Đợt đã thay đổi ở nơi khác. Hãy đọc bản mới để đối chiếu.',STATE_CONFLICT:'Đợt không còn cho phép sửa thông tin chung.',DEPENDENCY_UNAVAILABLE:'Chưa kiểm tra được dữ liệu phụ thuộc.',VALIDATION_ERROR:'Kiểm tra các trường đã nhập.',INTERNAL_ERROR:'Chưa thể hoàn tất thao tác. Hãy đối chiếu kết quả trước khi gửi lại.'};
 return (messages[e?.code]??e?.message??'Chưa thể hoàn tất thao tác.')+(e?.fields?.length?' · Trường cần kiểm tra: '+e.fields.join(', '):'');
}

// SQL datetimeoffset(7) precision must not be collapsed to JavaScript milliseconds.
function timeKey(v){const ms=Date.parse(v);return Number.isFinite(ms)?String(Math.floor(ms/1000))+':'+(v.match(/\.(\d+)(?:Z|[+-])/u)?.[1]??'').padEnd(7,'0'):null;}
export function roundDate(v){return v?new Intl.DateTimeFormat('vi-VN',{timeZone:'UTC'}).format(new Date(v+'T00:00:00Z')):'';}
