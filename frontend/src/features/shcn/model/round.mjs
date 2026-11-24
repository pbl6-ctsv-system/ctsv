export const roundStates={NHAP:'Nháp',CHO_DUYET:'Chờ duyệt',BI_TRA_LAI:'Bị trả lại',DA_DUYET:'Đã duyệt',DA_PHAT_HANH:'Đã phát hành',DA_HUY:'Đã hủy'};
export function roundInput(d){return {maHocKy:d?.maHocKy??'',tenDot:d?.tenDot??'',khungNgayBatDau:d?.khungNgayBatDau??'',khungNgayKetThuc:d?.khungNgayKetThuc??'',khungTietChoPhep:d?.khungTietChoPhep.map(r=>({...r}))??[{tietDau:0,tietCuoi:0}],hanChotDangKyLich:d?.hanChotDangKyLich??'',...(d?.doiTuongApDung!==undefined?{doiTuongApDung:d.doiTuongApDung.map(g=>({...g,khungTietChoPhep:g.khungTietChoPhep?.map(r=>({...r}))??null}))}:{})};}
export function roundWritable(d){return ['NHAP','BI_TRA_LAI'].includes(d.trangThai)&&!['CHO_PHO_PHONG_DUYET','CHO_BGH_KY','CO_HIEU_LUC'].includes(d.trangThaiPhanCong)&&!d.ngayPhatHanh&&!d.ngayCongBoLich;}
export function validateRound(d){
 if(!d.maHocKy||d.maHocKy.length>32||/\s/u.test(d.maHocKy))throw new Error('Hãy chọn học kỳ hợp lệ.');
 if(!d.tenDot||d.tenDot.length>200||d.tenDot.trim()!==d.tenDot)throw new Error('Tên đợt cần có nội dung, tối đa 200 ký tự và không có khoảng trắng đầu/cuối.');
 if(!d.khungTietChoPhep.length||d.khungTietChoPhep.length>100||d.khungTietChoPhep.some(r=>!Number.isInteger(r.tietDau)||!Number.isInteger(r.tietCuoi)||r.tietDau<1||r.tietCuoi>32767||r.tietDau>r.tietCuoi))throw new Error('Mỗi khoảng cần tiết đầu/kết thúc là số nguyên hợp lệ, tiết cuối không trước tiết đầu.');
 if(!d.hanChotDangKyLich||!Number.isFinite(Date.parse(d.hanChotDangKyLich)))throw new Error('Hãy nhập hạn chốt lịch hợp lệ.');
 if(!Array.isArray(d.doiTuongApDung)||!d.doiTuongApDung.length)throw new Error('Hãy chọn ít nhất một khóa hoặc nhóm lớp trước khi lưu nháp.');
 if(d.doiTuongApDung!==undefined){
  if(!Array.isArray(d.doiTuongApDung)||d.doiTuongApDung.length>1000)throw new Error('Danh sách đối tượng không hợp lệ.');
  const keys=new Set();
  for(const g of d.doiTuongApDung){
   const k=g.loaiDoiTuong+':'+g.maDoiTuong;
   if(!['KHOA_HOC','LOP'].includes(g.loaiDoiTuong)||!g.maDoiTuong||g.maDoiTuong.length>32||keys.has(k))throw new Error('Đối tượng áp dụng bị trùng hoặc không hợp lệ.');keys.add(k);
   if(!['OFFLINE','ONLINE'].includes(g.hinhThucChoPhep))throw new Error('Hãy chọn hình thức cho từng nhóm đã chọn.');
   if(!validAudienceDate(g.khungNgayBatDau)||!validAudienceDate(g.khungNgayKetThuc)||g.khungNgayKetThuc<g.khungNgayBatDau)throw new Error('Hãy nhập đủ từ ngày và đến ngày hợp lệ cho từng nhóm đã chọn; đến ngày không trước từ ngày.');
   if(g.khungTietChoPhep!==null&&(!Array.isArray(g.khungTietChoPhep)||!g.khungTietChoPhep.length||g.khungTietChoPhep.length>100||g.khungTietChoPhep.some(r=>!Number.isInteger(r.tietDau)||!Number.isInteger(r.tietCuoi)||r.tietDau<1||r.tietCuoi>32767||r.tietDau>r.tietCuoi)))throw new Error('Khung tiết riêng của nhóm không hợp lệ.');
  }
 }
 return roundInput({...d,...audienceDateRange(d.doiTuongApDung)});
}
export function sameRound(a,b){return JSON.stringify({...roundInput(a),doiTuongApDung:roundInput(a).doiTuongApDung??[],hanChotDangKyLich:timeKey(a.hanChotDangKyLich)})===JSON.stringify({...roundInput(b),doiTuongApDung:roundInput(b).doiTuongApDung??[],hanChotDangKyLich:timeKey(b.hanChotDangKyLich)});}
export function roundError(e){
 const messages={SOURCE_MAPPING_INCOMPLETE:'Chưa đủ ánh xạ dữ liệu nguồn để xác định nhóm lớp và sinh viên. Hãy kiểm tra nguồn trước khi tiếp tục.',TARGET_CONFIG_CONFLICT:'Một lớp đang nhận các cấu hình khác nhau. Hãy kiểm tra lại nhóm đã chọn.',FORBIDDEN:'Bạn chưa được cấp quyền SHCN phù hợp.',NOT_FOUND:'Không tìm thấy đợt SHCN.',VERSION_CONFLICT:'Đợt đã thay đổi ở nơi khác. Hãy đọc bản mới để đối chiếu.',STATE_CONFLICT:'Đợt không còn cho phép sửa thông tin chung.',DEPENDENCY_UNAVAILABLE:'Chưa kiểm tra được dữ liệu phụ thuộc.',VALIDATION_ERROR:'Kiểm tra các trường đã nhập.',INTERNAL_ERROR:'Chưa thể hoàn tất thao tác. Hãy đối chiếu kết quả trước khi gửi lại.'};
 return (messages[e?.code]??e?.message??'Chưa thể hoàn tất thao tác.')+(e?.fields?.length?' · Trường cần kiểm tra: '+e.fields.join(', '):'');
}

// SQL datetimeoffset(7) precision must not be collapsed to JavaScript milliseconds.
function timeKey(v){const ms=Date.parse(v);return Number.isFinite(ms)?String(Math.floor(ms/1000))+':'+(v.match(/\.(\d+)(?:Z|[+-])/u)?.[1]??'').padEnd(7,'0'):null;}
export function roundDate(v){return v?new Intl.DateTimeFormat('vi-VN',{timeZone:'UTC'}).format(new Date(v+'T00:00:00Z')):'';}

// The round envelope is derived from explicitly selected audiences; dates are never guessed.
function validAudienceDate(value){
 if(typeof value!=='string'||!/^\d{4}-\d{2}-\d{2}$/u.test(value)||value<'0001-01-01')return false;
 const date=new Date(value+'T00:00:00Z');return Number.isFinite(date.getTime())&&date.toISOString().slice(0,10)===value;
}
export function audienceDateRange(groups){
 const empty={khungNgayBatDau:'',khungNgayKetThuc:''};
 if(!groups?.length||groups.some(g=>!validAudienceDate(g.khungNgayBatDau)||!validAudienceDate(g.khungNgayKetThuc)||g.khungNgayBatDau>g.khungNgayKetThuc))return empty;
 return {khungNgayBatDau:groups.reduce((first,g)=>g.khungNgayBatDau<first?g.khungNgayBatDau:first,groups[0].khungNgayBatDau),khungNgayKetThuc:groups.reduce((last,g)=>g.khungNgayKetThuc>last?g.khungNgayKetThuc:last,groups[0].khungNgayKetThuc)};
}
export function roundEditorInput(source){
 const d=roundInput(source);
 d.doiTuongApDung=(d.doiTuongApDung??[]).map(g=>g.khungNgayBatDau==null&&g.khungNgayKetThuc==null?{...g,khungNgayBatDau:d.khungNgayBatDau||null,khungNgayKetThuc:d.khungNgayKetThuc||null}:g);
 return {...d,...audienceDateRange(d.doiTuongApDung)};
}
export function semesterLabel(semester){return semester?.ten?.trim()||'Chưa có tên học kỳ';}

export function audienceConfigurationReady(group){
 return !!group&&['OFFLINE','ONLINE'].includes(group.hinhThucChoPhep)&&validAudienceDate(group.khungNgayBatDau)&&validAudienceDate(group.khungNgayKetThuc)&&group.khungNgayBatDau<=group.khungNgayKetThuc;
}
// One-time copy of dates/mode. Group identity and private period overrides remain independent.
export function copyAudienceConfiguration(groups,sourceKey,targetKeys){
 const key=g=>g.loaiDoiTuong+':'+g.maDoiTuong,source=groups.find(g=>key(g)===sourceKey),targets=new Set(targetKeys);
 if(!audienceConfigurationReady(source))throw new Error('Nhập đủ ngày và hình thức hợp lệ cho nhóm nguồn.');
 if(!targets.size||targets.has(sourceKey)||[...targets].some(k=>!groups.some(g=>key(g)===k)))throw new Error('Chọn nhóm nhận đang được áp dụng trong đợt.');
 return groups.map(g=>targets.has(key(g))?{...g,khungNgayBatDau:source.khungNgayBatDau,khungNgayKetThuc:source.khungNgayKetThuc,hinhThucChoPhep:source.hinhThucChoPhep}:g);
}
