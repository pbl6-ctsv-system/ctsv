import type {Page} from './assignments';
export type Period={tietDau:number;tietCuoi:number};
export type Audience={loaiDoiTuong:'KHOA_HOC'|'LOP';maDoiTuong:string;hinhThucChoPhep:string;khungNgayBatDau:string|null;khungNgayKetThuc:string|null;khungTietChoPhep:Period[]|null};
export type RoundGroups={khoaHoc:{ma:string;ten:string;soLop:number}[];lopTreTienDo:{ma:string;ten:string;soSinhVien:number}[];soLopChuaCoKhoa:number;trangThaiLopTreTienDo:'CO_DU_LIEU'|'CHUA_CO_DU_LIEU';trangThaiDuLieuHocVu:'CO_DU_LIEU'|'CHUA_CO_DU_LIEU';trangThaiPhanNhom:'CAN_XAC_MINH';canhBao:AudienceWarning[]};
export type RoundInput={maHocKy:string;tenDot:string;khungNgayBatDau:string;khungNgayKetThuc:string;khungTietChoPhep:Period[];hanChotDangKyLich:string;doiTuongApDung?:Audience[]};
export type Round=RoundInput&{id:string;version:string;trangThai:string;trangThaiPhanCong:string;phienBanPhanCongApDung:number|null;trangThaiLichTongHop:string;phienBanLichTongHop:number|null;ngayPhatHanh:string|null;ngayCongBoLich:string|null;ngayTao:string;ngayCapNhat:string};
export type Semester={ma:string;ten:string|null;namHoc:string|null};
export type RoundFilters={q?:string;maHocKy?:string;trangThai?:string};
export type RoundResult<T>={value:T;canPrepare:boolean};
export type RoundPage=Page<Round>;

export type AudienceWarning={ma:string;thongDiep:string};
export type AudienceClass={ma:string;ten:string;loaiLop:string;maKhoaHoc:string|null;tenKhoaHoc:string|null;soSinhVien:number;soSinhVienCanXacMinh:number};
export type AudienceStudent={id:string;maSinhVien:string;hoTen:string;maLop:string;tenLop:string;loaiLop:string;canCu:string;canCuLop:string;trangThaiXacMinh:string;duCanCuNguon:boolean};
export type AppliedClass={maLop:string;tenLop:string;loaiLop:string;tenKhoaHoc:string|null;maKhoa:string|null;tenKhoa:string|null;maChuongTrinh:string|null;tenChuongTrinh:string|null;tuNgay:string;denNgay:string;khungTietChoPhep:Period[];hinhThucChoPhep:string;boChonNguon:{loaiDoiTuong:string;maDoiTuong:string}[];soSinhVien:number;soSinhVienCanXacMinh:number};
export type AudiencePreview={dotId:string;dotVersion:string;items:Audience[];tongHop:{soBoChon:number;tongSoLop:number;soLopThuong:number;soLopTreTienDo:number};lopApDung:Page<AppliedClass>;trangThaiPhanNhom:string;canhBao:AudienceWarning[]};
