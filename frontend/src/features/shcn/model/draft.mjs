export const statusLabels={NHAP:'Nháp',CHO_KY:'Chờ ký',DA_KY:'Đã ký',DA_GUI_CHUYEN_VIEN:'Đã gửi chuyên viên',DA_THAY_THE:'Đã thay thế',DA_HUY:'Đã hủy'};
export const inheritedStatuses=new Set(['DA_KY','DA_GUI_CHUYEN_VIEN','DA_THAY_THE']);
export function saveBody(draft){
 if(draft.cacPhanCong.length>2000)throw new Error('Danh sách vượt giới hạn 2000 phân công.');
 if(draft.hieuLucDen&&Date.parse(draft.hieuLucDen)<=Date.parse(draft.hieuLucTu))throw new Error('Mốc kết thúc phải sau mốc bắt đầu.');
 const seen=new Set();
 const rows=draft.cacPhanCong.map(row=>{
  if(!row.maLop||!row.maGiaoVien||seen.has(row.maLop))throw new Error('Mỗi dòng cần đủ lớp và GVCN; không được trùng lớp.');
  seen.add(row.maLop);if((row.ghiChu?.length??0)>4000)throw new Error('Ghi chú tối đa 4000 ký tự.');
  return {maLop:row.maLop,maGiaoVien:row.maGiaoVien,ghiChu:row.ghiChu||null};
 });
 if((draft.lyDoDieuChinh?.length??0)>4000)throw new Error('Lý do tối đa 4000 ký tự.');
 return {expectedVersion:draft.version,hieuLucTu:draft.hieuLucTu,hieuLucDen:draft.hieuLucDen,lyDoDieuChinh:draft.lyDoDieuChinh||null,cacPhanCong:rows};
}
export function vietnamInput(iso){return iso?new Date(Date.parse(iso)+7*3600000).toISOString().slice(0,19):'';}
export function instant(input){return input?(input.length===16?input+':00':input)+'+07:00':null;}
export function matchingContent(a,b){
 const normalize=d=>({from:Date.parse(d.hieuLucTu),until:d.hieuLucDen?Date.parse(d.hieuLucDen):null,reason:d.lyDoDieuChinh||null,rows:d.cacPhanCong.map(r=>[r.maLop,r.maGiaoVien,r.ghiChu||null]).sort((x,y)=>x[0]<y[0]?-1:x[0]>y[0]?1:0)});
 return JSON.stringify(normalize(a))===JSON.stringify(normalize(b));
}

function schoolYearStart(value){
 const pair=/^(\d{4})-(\d{4})$/.exec(value);
 if(pair&&Number(pair[2])===Number(pair[1])+1)return Number(pair[1]);
 return /^\d{4}$/.test(value)?Number(value):null;
}
export function previousSchoolYear(year){
 const start=schoolYearStart(year);
 if(start===null||start<=1)return '';
 return year.includes('-')?`${String(start-1).padStart(4,'0')}-${String(start).padStart(4,'0')}`:String(start-1).padStart(4,'0');
}
export function inheritanceYearError(target,source){
 if(!source||source.trim()!==source||source.length>20)return 'Nhập năm học trước để tìm phân công kế thừa.';
 if(source===target)return 'Hãy chọn phân công của năm trước, không phải năm đang tạo.';
 const from=schoolYearStart(source),to=schoolYearStart(target);
 if(from!==null&&to!==null&&from>=to)return 'Năm học kế thừa phải trước năm học đang tạo.';
 return '';
}

export function rankYearOptions(query,known=[],anchorYear=new Date().getFullYear()){
 const suggested=Array.from({length:9},(_,i)=>{const start=anchorYear+2-i;return `${start}-${start+1}`;});
 const values=[...new Set([...known.filter(Boolean),...suggested])];
 const key=query.trim().toLocaleLowerCase('vi');
 const score=v=>!key?3:v.toLocaleLowerCase('vi')===key?0:v.toLocaleLowerCase('vi').startsWith(key)?1:v.toLocaleLowerCase('vi').includes(key)?2:3;
 return values.sort((a,b)=>score(a)-score(b));
}
