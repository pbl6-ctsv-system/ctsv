const keyPattern=/^[A-Z][A-Z0-9_]{0,31}$/;
export function templateKey(prefix){return prefix+'_'+crypto.randomUUID().replaceAll('-','').slice(0,28).toUpperCase();}
const clone=value=>structuredClone(value);
export function moveItem(items,from,to){if(from<0||to<0||from>=items.length||to>=items.length)return [...items];const next=[...items];const [item]=next.splice(from,1);next.splice(to,0,item);return next;}
export function createTemplate(dictionary){
 const fixed=clone(dictionary.mucHeThong),attendance=fixed.find(m=>m.maMuc==='DIEM_DANH');
 const custom=(maMuc,tieuDe,batBuoc)=>({maMuc,tieuDe,thuTuHienThi:0,loaiMuc:'NOI_DUNG',batBuoc,laMucHeThong:false,cauHinhTruong:[{maTruong:'NOI_DUNG',nhan:'Nội dung',kieuDuLieu:'VAN_BAN',batBuoc,nguonDien:'NGUOI_NHAP',maNguonHeThong:null}]});
 return {tenMau:'Biên bản sinh hoạt chủ nhiệm',muc:[attendance,custom('TRIEN_KHAI','Tình hình triển khai nội dung sinh hoạt lớp',true),custom('KET_QUA_HOC_TAP','Tình hình kết quả học tập',false),...fixed.filter(m=>m.maMuc!=='DIEM_DANH')].map((m,i)=>({...m,thuTuHienThi:i+1}))};
}
export function templateInput(value,expectedVersion){return {expectedVersion,tenMau:value.tenMau,muc:value.muc.map(m=>({maMuc:m.maMuc,tieuDe:m.tieuDe,thuTuHienThi:m.thuTuHienThi,loaiMuc:m.loaiMuc,batBuoc:m.batBuoc,cauHinhTruong:m.cauHinhTruong.map(f=>({maTruong:f.maTruong,nhan:f.nhan,kieuDuLieu:f.kieuDuLieu,batBuoc:f.batBuoc,nguonDien:f.nguonDien,maNguonHeThong:f.maNguonHeThong}))}))};}
export function sameTemplate(a,b){return JSON.stringify(templateInput(a,''))===JSON.stringify(templateInput(b,''));}
function fail(field,message){const error=new Error(message);error.field=field;throw error;}
function text(value,max,field){if(typeof value!=='string'||!value.trim()||value.length>max||!value.isWellFormed())fail(field,'Nội dung bắt buộc, tối đa '+max+' ký tự.');}
export function validateTemplate(value,dictionary){
 text(value.tenMau,200,'tenMau');if(!Array.isArray(value.muc)||!value.muc.length||value.muc.length>100)fail('muc','Mẫu cần từ 3 đến 100 mục.');const keys=new Set();
 value.muc.forEach((m,i)=>{const path='muc['+i+']';text(m.tieuDe,250,path+'.tieuDe');if(!keyPattern.test(m.maMuc)||keys.has(m.maMuc))fail(path+'.maMuc','Mã mục không hợp lệ hoặc bị trùng.');keys.add(m.maMuc);
  if(m.thuTuHienThi!==i+1)fail(path+'.thuTuHienThi','Thứ tự mục không hợp lệ.');if(!m.cauHinhTruong.length||m.cauHinhTruong.length>100)fail(path+'.cauHinhTruong','Mỗi mục cần từ 1 đến 100 trường.');const fieldKeys=new Set();
  m.cauHinhTruong.forEach((f,j)=>{const p=path+'.cauHinhTruong['+j+']';text(f.nhan,200,p+'.nhan');if(!keyPattern.test(f.maTruong)||fieldKeys.has(f.maTruong))fail(p+'.maTruong','Trường bị trùng hoặc không hợp lệ.');fieldKeys.add(f.maTruong);
   if(!dictionary.kieuDuLieu.includes(f.kieuDuLieu))fail(p+'.kieuDuLieu','Chọn kiểu dữ liệu hợp lệ.');if(f.nguonDien==='HE_THONG'){const source=dictionary.nguonHeThong.find(s=>s.ma===f.maNguonHeThong);if(!source||source.kieuDuLieu!==f.kieuDuLieu)fail(p+'.maNguonHeThong','Chọn nguồn tự điền và kiểu dữ liệu tương ứng.');}else if(f.nguonDien!=='NGUOI_NHAP'||f.maNguonHeThong!==null)fail(p+'.nguonDien','Cách điền không hợp lệ.');
  });
  const fixed=dictionary.mucHeThong.find(s=>s.maMuc===m.maMuc);
  if(fixed){if(m.tieuDe!==fixed.tieuDe||m.loaiMuc!==fixed.loaiMuc||m.batBuoc!==fixed.batBuoc||JSON.stringify(m.cauHinhTruong)!==JSON.stringify(fixed.cauHinhTruong)||(m.maMuc==='DIEM_DANH'&&i!==0))fail(path,'Không được thay đổi hoặc xóa mục cố định.');}
  else if(m.loaiMuc!=='NOI_DUNG'||m.batBuoc&&!m.cauHinhTruong.some(f=>f.batBuoc))fail(path+'.batBuoc','Mục bắt buộc cần ít nhất một trường bắt buộc.');
 });
 if(dictionary.mucHeThong.some(s=>!keys.has(s.maMuc)))fail('muc','Phải giữ Điểm danh và hai mục kiến nghị.');
 const body=templateInput(value,'AAAAAAAAAAE=');if(new TextEncoder().encode(JSON.stringify(body)).length>1048576)fail('muc','Cấu hình vượt giới hạn 1 MiB.');return value;
}
export function templateError(error){const messages={SYSTEM_SECTION_IMMUTABLE:'Không được thay đổi hoặc xóa các mục cố định.',VERSION_CONFLICT:'Mẫu hoặc đợt đã được cập nhật ở nơi khác. Hãy đọc bản mới để đối chiếu.',STATE_CONFLICT:'Đợt hoặc mẫu không còn cho phép chỉnh sửa.',FORBIDDEN:'Bạn không còn quyền sửa mẫu biên bản.',NOT_FOUND:'Không tìm thấy đợt SHCN.',VALIDATION_ERROR:'Kiểm tra các mục và trường được báo lỗi.',DEPENDENCY_UNAVAILABLE:'Chưa truy cập được dữ liệu mẫu. Hãy thử đọc lại.',INTERNAL_ERROR:'Chưa xác định kết quả lưu mẫu. Hãy đọc bản mới để đối chiếu.'};return messages[error?.code]??error?.message??'Chưa thể xử lý mẫu biên bản.';}
