import test from 'node:test';
import assert from 'node:assert/strict';
import {probeFacultyScope} from '../src/features/shcn/api/faculty-access.mjs';
import {exportReadiness,listActionLabel} from '../src/features/shcn/model/draft.mjs';
import {
 canPlaceSignatureBox,
 caErrorMessage,
 dossierSelectionKey,
 isPdfAdminContentDirty,
 isSigningWorking,
 signingPhaseDescription,
 validatePdfAdminContent,
} from '../src/features/shcn/model/assignment-signing.mjs';

test('faculty scope probe accepts sign and submit capabilities alongside read and prepare',async()=>{
 const signal=new AbortController().signal;
 for(const code of ['shcn.phanCongKhoa.sign','shcn.phanCongKhoa.submit']){
  let calls=0;
  const scope={khoa:[{maKhoa:'CNTT',duocDoc:true,duocLapNhap:true,duocKySo:true,duocGuiChuyenVien:true,soLopHoatDong:2}]};
  const result=await probeFacultyScope(
   {request:async url=>{calls++;assert.equal(url,'/api/shcn/khoa/pham-vi');return {json:async()=>scope};}},
   [{code,active:true}],
   signal
  );
  assert.equal(calls,1);
  assert.deepEqual(result,scope);
 }
});

test('exportReadiness requires 100% active faculty classes, saved state, and valid references',()=>{
 const faculty={maKhoa:'CNTT',duocDoc:true,duocLapNhap:true,duocKySo:true,duocGuiChuyenVien:true,soLopHoatDong:3,coTheChon:true};
 const validRow=code=>({maLop:code,maGiaoVien:'GV1',ghiChu:null,lopHienTai:{coTheChon:true},giangVienHienTai:{coTheChon:true}});

 const incompleteDraft={soLop:2,cacPhanCong:[validRow('L1'),validRow('L2')]};
 const incomplete=exportReadiness(incompleteDraft,incompleteDraft,faculty,false);
 assert.equal(incomplete.ready,false);
 assert.equal(incomplete.missingCount,1);
 assert.match(incomplete.blockingReason,/2 \/ 3 lớp/);

 const dirtyComplete={soLop:3,cacPhanCong:[validRow('L1'),validRow('L2'),validRow('L3')]};
 const dirtyState=exportReadiness(dirtyComplete,dirtyComplete,faculty,true);
 assert.equal(dirtyState.ready,false);
 assert.match(dirtyState.blockingReason,/Lưu nháp/);

 const invalidRefDraft={soLop:3,cacPhanCong:[validRow('L1'),validRow('L2'),{...validRow('L3'),giangVienHienTai:{coTheChon:false}}]};
 const invalidRef=exportReadiness(invalidRefDraft,invalidRefDraft,faculty,false);
 assert.equal(invalidRef.ready,false);
 assert.equal(invalidRef.hasInvalidReference,true);

 const complete=exportReadiness(dirtyComplete,dirtyComplete,faculty,false);
 assert.equal(complete.ready,true);
 assert.equal(complete.blockingReason,'');
});

test('listActionLabel reflects role capabilities and workflow stage',()=>{
 const fullFaculty={duocDoc:true,duocLapNhap:true,duocKySo:true,duocGuiChuyenVien:true};
 assert.equal(listActionLabel({trangThai:'NHAP'},fullFaculty),'Mở soạn');
 assert.equal(listActionLabel({trangThai:'CHO_KY'},fullFaculty),'Mở ký số');
 assert.equal(listActionLabel({trangThai:'DA_KY'},fullFaculty),'Xem & Gửi CTSV');
 assert.equal(listActionLabel({trangThai:'DA_GUI_CHUYEN_VIEN'},fullFaculty),'Xem');
 assert.equal(listActionLabel({trangThai:'CHO_KY'},{duocDoc:true,duocLapNhap:false,duocKySo:false}),'Xem');
});

test('Option B administrative PDF content validation and dirty guard lock signature placement until updated',()=>{
 const base={
  kinhGui:'Phòng Công tác Sinh viên',
  canCu:'',
  loiDan:'Khoa CNTT lập danh sách phân công GVCN.',
  diaDanhNgayThang:'Đà Nẵng, ngày 26 tháng 11 năm 2026',
  hoTenNguoiKy:'PGS.TS. Nguyễn Văn Trưởng',
  ghiChuChanTrang:'',
 };
 assert.equal(isPdfAdminContentDirty(base,{...base}),false);
 assert.deepEqual(validatePdfAdminContent(base),{});

 const edited={...base,loiDan:'Khoa CNTT điều chỉnh câu dẫn mở đầu.'};
 assert.equal(isPdfAdminContentDirty(base,edited),true);
 assert.ok(validatePdfAdminContent({...base,kinhGui:'   '}).kinhGui);

 const dossier={
  danhSachId:'10',
  version:'AAAAAAAAAAE=',
  phienBanVanBanId:'301',
  trangThaiDanhSach:'CHO_KY',
  tepGoc:{id:'701'},
  yeuCauKy:{status:'prepared',canSign:true},
 };
 const faculty={duocKySo:true};
 assert.equal(canPlaceSignatureBox({dossier,faculty,dirtyPdfMeta:false,historicalPreview:false,busy:false}),true);
 assert.equal(canPlaceSignatureBox({dossier,faculty,dirtyPdfMeta:true,historicalPreview:false,busy:false}),false);
 assert.equal(canPlaceSignatureBox({dossier,faculty,dirtyPdfMeta:false,historicalPreview:true,busy:false}),false);

 const keyBefore=dossierSelectionKey(dossier);
 const keyAfterReRender=dossierSelectionKey({...dossier,phienBanVanBanId:'302',tepGoc:{id:'702'}});
 assert.notEqual(keyBefore,keyAfterReRender);
});

test('signing working states, unknown guidance, and CA error messages remain safe',()=>{
 assert.equal(isSigningWorking({yeuCauKy:{status:'queued'}}),true);
 assert.equal(isSigningWorking({yeuCauKy:{status:'signing'}}),true);
 assert.equal(isSigningWorking({yeuCauKy:{status:'prepared'}}),false);
 assert.match(signingPhaseDescription({trangThaiDanhSach:'CHO_KY',yeuCauKy:{status:'unknown'}}),/đối soát/);
 assert.match(caErrorMessage('CA_CERTIFICATE_SELECTION_REQUIRED'),/chứng thư số/);
});
