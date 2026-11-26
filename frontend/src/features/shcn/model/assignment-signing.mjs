export const EMPTY_PDF_ADMIN_CONTENT=Object.freeze({
 kinhGui:'Phòng Công tác Sinh viên',
 canCu:'',
 loiDan:'',
 diaDanhNgayThang:'',
 hoTenNguoiKy:'',
 ghiChuChanTrang:'',
});

export function normalizePdfAdminContent(meta){
 if(!meta)return {...EMPTY_PDF_ADMIN_CONTENT};
 return {
  kinhGui:(meta.kinhGui??'').trim(),
  canCu:(meta.canCu??'').trim(),
  loiDan:(meta.loiDan??'').trim(),
  diaDanhNgayThang:(meta.diaDanhNgayThang??'').trim(),
  hoTenNguoiKy:(meta.hoTenNguoiKy??'').trim(),
  ghiChuChanTrang:(meta.ghiChuChanTrang??'').trim(),
 };
}

export function isPdfAdminContentDirty(base,draft){
 const a=normalizePdfAdminContent(base);
 const b=normalizePdfAdminContent(draft);
 return a.kinhGui!==b.kinhGui
  ||a.canCu!==b.canCu
  ||a.loiDan!==b.loiDan
  ||a.diaDanhNgayThang!==b.diaDanhNgayThang
  ||a.hoTenNguoiKy!==b.hoTenNguoiKy
  ||a.ghiChuChanTrang!==b.ghiChuChanTrang;
}

export function validatePdfAdminContent(draft){
 const errors={};
 const kinhGui=(draft?.kinhGui??'').trim();
 const canCu=(draft?.canCu??'').trim();
 const loiDan=(draft?.loiDan??'').trim();
 const diaDanh=(draft?.diaDanhNgayThang??'').trim();
 const hoTen=(draft?.hoTenNguoiKy??'').trim();
 const ghiChu=(draft?.ghiChuChanTrang??'').trim();

 if(!kinhGui)errors.kinhGui='Nhập nơi nhận (Kính gửi).';
 else if(kinhGui.length>200)errors.kinhGui='Kính gửi tối đa 200 ký tự.';

 if(canCu.length>500)errors.canCu='Tiêu đề phụ / căn cứ tối đa 500 ký tự.';

 if(!loiDan)errors.loiDan='Nhập lời dẫn mở đầu trên văn bản PDF.';
 else if(loiDan.length>1000)errors.loiDan='Lời dẫn mở đầu tối đa 1000 ký tự.';

 if(!diaDanh)errors.diaDanhNgayThang='Nhập địa danh và ngày tháng hiển thị.';
 else if(diaDanh.length>150)errors.diaDanhNgayThang='Địa danh và ngày tháng tối đa 150 ký tự.';

 if(hoTen.length>150)errors.hoTenNguoiKy='Họ tên dưới chữ ký tối đa 150 ký tự.';
 if(ghiChu.length>500)errors.ghiChuChanTrang='Ghi chú chân trang tối đa 500 ký tự.';

 return errors;
}

export function dossierSelectionKey(dossier){
 if(!dossier)return 'none';
 return [
  dossier.danhSachId,
  dossier.version,
  dossier.phienBanVanBanId??'',
  dossier.tepGoc?.id??'',
  dossier.yeuCauKy?.status??''
 ].join(':');
}

export function isSigningWorking(dossier){
 const status=dossier?.yeuCauKy?.status;
 return status==='queued'||status==='signing';
}

export function canPlaceSignatureBox({dossier,faculty,dirtyPdfMeta,historicalPreview,busy}){
 if(!dossier||!faculty?.duocKySo||busy||dirtyPdfMeta||historicalPreview)return false;
 return dossier.trangThaiDanhSach==='CHO_KY'&&dossier.yeuCauKy?.canSign===true;
}

export function signingStatusLabel(status){
 const map={
  prepared:'Chờ Trưởng khoa ký số',
  queued:'Đang xếp hàng gửi ký CA',
  signing:'Đang ký số & lưu PDF',
  completed:'Đã ký số hợp lệ',
  unknown:'Chưa rõ kết quả từ CA — Cần đối soát',
  failed:'Chưa hoàn tất lượt ký',
  cancelled:'Đã hủy lượt chờ ký',
 };
 return map[status]??'Đang kiểm tra trạng thái';
}

export function caErrorMessage(errorCode){
 if(!errorCode)return '';
 const map={
  CA_NOT_CONFIGURED:'Dịch vụ ký số CA DUT chưa được cấu hình trên môi trường này.',
  CA_CERTIFICATE_SELECTION_REQUIRED:'Không tìm thấy đúng 1 chứng thư số đang hoạt động và sẵn sàng khóa riêng cho email người ký trên CA DUT.',
  CA_CERTIFICATE_NOT_READY:'Chứng thư số của người ký chưa sẵn sàng hoặc đã hết hạn trên CA DUT.',
  CA_BINDING_INVALID:'Thông tin chứng thư trả về từ CA DUT không khớp người ký dự kiến.',
  CA_UPLOAD_MISMATCH:'Tệp PDF tải lên CA DUT không khớp mã băm kiểm tra toàn vẹn.',
  CA_RECEIPT_MISMATCH:'Biên nhận ký trả về từ CA DUT không khớp thông tin văn bản hoặc chứng thư.',
  CA_SIGNED_HASH_MISMATCH:'Tệp PDF sau ký không khớp mã băm SHA-256 trong biên nhận CA DUT.',
  CA_REVISION_MISMATCH:'Tệp PDF sau ký đã làm thay đổi nội dung gốc trước khi ký.',
  CA_CRYPTO_NOT_VERIFIED:'Không xác thực được chữ ký số CMS trên tệp PDF nhận về.',
  SIGNING_AUTHORIZATION_REVOKED:'Quyền ký số của tài khoản đã thay đổi trước khi gửi lệnh sang CA DUT.',
  SIGNING_RESULT_UNKNOWN:'Yêu cầu ký bị gián đoạn khi đang gọi dịch vụ ngoài. Cần thực hiện đối soát trước khi thao tác tiếp.',
  STORAGE_UNAVAILABLE:'Kho lưu trữ PDF tạm thời gián đoạn khi lưu tệp kết quả.',
 };
 return map[errorCode]??`Lượt ký dừng với mã kiểm tra: ${errorCode}.`;
}

export function signingPhaseDescription(dossier){
 if(!dossier)return '';
 if(dossier.trangThaiDanhSach==='DA_GUI_CHUYEN_VIEN'){
  return 'Danh sách phân công và bản PDF đã ký số đã được gửi chính thức cho Chuyên viên Phòng CTSV.';
 }
 if(dossier.trangThaiDanhSach==='DA_THAY_THE'){
  return 'Bản phân công này đã được thay thế bởi một phiên bản điều chỉnh mới hơn.';
 }
 if(dossier.trangThaiDanhSach==='DA_KY'){
  return 'Bản PDF đã có chữ ký số của Trưởng khoa và được xác thực lưu trữ tại CTSV. Hãy kiểm tra lại PDF rồi bấm "Gửi chuyên viên CTSV".';
 }
 const q=dossier.yeuCauKy;
 if(!q)return 'Chưa khởi tạo hồ sơ ký số cho phiên bản này.';
 if(q.status==='prepared'){
  return 'Kiểm tra nội dung PDF (có thể chỉnh phần chữ hành chính nếu cần), sau đó chọn vùng chữ ký dưới mục TRƯỞNG KHOA và bấm Ký số.';
 }
 if(q.status==='queued'||q.status==='signing'){
  return 'Hệ thống đang gửi tệp sang dịch vụ CA DUT, kiểm tra chữ ký CMS và lưu bản PDF đã ký về CTSV. Bạn có thể đợi tại đây hoặc rời trang.';
 }
 if(q.status==='unknown'){
  return 'Phản hồi từ CA DUT bị gián đoạn nên chưa xác định rõ trạng thái bên ngoài. Tuyệt đối không ký lại mù hoặc hủy danh sách; hãy thực hiện đối soát.';
 }
 if(q.status==='failed'){
  return caErrorMessage(q.errorCode)||'Lượt ký chưa hoàn tất. Kiểm tra thông báo lỗi để đối soát hoặc mở lại bản nháp.';
 }
 return 'Đang đồng bộ trạng thái hồ sơ ký.';
}

export function historyActionLabel(action){
 const map={
  SHCN_KHOA_CREATE_DRAFT:'Tạo bản nháp phân công',
  SHCN_KHOA_SAVE_DRAFT:'Lưu cập nhật bản nháp',
  SHCN_KHOA_DELETE_DRAFT:'Xóa bản nháp',
  SHCN_KHOA_EXPORT_PDF:'Chốt bản nháp & Xuất PDF chờ ký',
  SHCN_KHOA_UPDATE_PDF_CONTENT:'Cập nhật chữ hành chính & Render lại PDF',
  SHCN_KHOA_SIGN_CONFIRM:'Trưởng khoa xác nhận vùng ký & Gửi ký số',
  SHCN_KHOA_SIGN_COMPLETED:'Hoàn tất ký số CA DUT & Lưu PDF đã ký',
  SHCN_KHOA_SIGN_RECONCILE:'Đối soát / phục hồi tiến trình ký số',
  SHCN_KHOA_REOPEN_DRAFT:'Hủy chờ ký & Mở lại bản nháp để chỉnh sửa',
  SHCN_KHOA_SUBMIT_SPECIALIST:'Gửi danh sách đã ký số cho Chuyên viên CTSV',
 };
 return map[action]??action;
}
