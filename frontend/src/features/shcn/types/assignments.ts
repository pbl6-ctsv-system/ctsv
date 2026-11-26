export type Page<T>={items:T[];page:number;size:number;totalElements:number};
export type Faculty={
 id:string;
 maKhoa:string;
 tenKhoa:string;
 duocDoc:boolean;
 duocLapNhap:boolean;
 duocKySo?:boolean;
 duocGuiChuyenVien?:boolean;
 soLopHoatDong?:number|null;
 coTheChon:boolean;
 lyDoKhongTheChon:string|null;
};
export type Program={id:string;ma:string;ten:string;tenNganhMoi:string|null};
export type SchoolClass={id:string;maLop:string;tenLop:string;maKhoa:string;chuongTrinh:Program|null;coTheChon:boolean;lyDoKhongTheChon:string|null};
export type Lecturer={idCanBo:string;maGiaoVien:string;hoTen:string;maKhoaCongTac:string|null;coTheChon:boolean;lyDoKhongTheChon:string|null};
export type Assignment={id?:string;maLop:string;maGiaoVien:string;ghiChu:string|null;lopHienTai:SchoolClass|null;giangVienHienTai:Lecturer|null};
export type Summary={id:string;maKhoa:string;namHoc:string;phienBan:number;trangThai:string;hieuLucTu:string;hieuLucDen:string|null;soLop:number;ngayTao:string;ngayCapNhat:string};
export type Draft=Summary&{maDanhSachTruoc:string|null;maNguoiLap:string;lyDoDieuChinh:string|null;version:string;cacPhanCong:Assignment[]};

export type PdfAdminContent={
 kinhGui:string;
 canCu:string;
 loiDan:string;
 diaDanhNgayThang:string;
 hoTenNguoiKy:string;
 ghiChuChanTrang:string;
};

export type PdfFileInfo={
 id:string;
 fileName:string;
 size:number;
 sha256:string;
 ready:boolean;
};

export type SigningStepInfo={
 status:string;
 signerName:string|null;
 signerEmail:string|null;
 signedAt:string|null;
 certificateSerial:string|null;
 page:number|null;
 x:number|null;
 y:number|null;
 width:number|null;
 height:number|null;
};

export type SigningRequestInfo={
 id:string;
 status:string;
 mode:string|null;
 errorCode:string|null;
 canSign:boolean;
 canReconcile:boolean;
 canSendSpecialist:boolean;
 canReopenDraft:boolean;
 canUpdatePdfContent:boolean;
 step:SigningStepInfo|null;
};

export type PdfVersionEntry={
 phienBanVanBanId:string;
 soPhienBan:number;
 current:boolean;
 title:string;
 createdAt:string|null;
 tepGoc:PdfFileInfo|null;
 tepDaKy:PdfFileInfo|null;
};

export type SigningHistoryEntry={
 id:string;
 action:string;
 reason:string|null;
 at:string|null;
};

export type SigningDossier={
 danhSachId:string;
 maKhoa:string;
 tenKhoa:string;
 tenKhoaGocTraiPdf:string;
 namHoc:string;
 phienBan:number;
 trangThaiDanhSach:string;
 ngayGuiChuyenVien:string|null;
 version:string;
 vanBanId:string|null;
 phienBanVanBanId:string|null;
 datasetSha256:string|null;
 noiDungHanhChinh:PdfAdminContent|null;
 tepGoc:PdfFileInfo|null;
 tepDaKy:PdfFileInfo|null;
 yeuCauKy:SigningRequestInfo|null;
 cacPhienBanPdf:PdfVersionEntry[];
 lichSu:SigningHistoryEntry[];
};
