import type {Page} from './assignments';
export type Period={tietDau:number;tietCuoi:number};
export type RoundInput={maHocKy:string;tenDot:string;khungNgayBatDau:string;khungNgayKetThuc:string;khungTietChoPhep:Period[];hanChotDangKyLich:string};
export type Round=RoundInput&{id:string;version:string;trangThai:string;trangThaiPhanCong:string;phienBanPhanCongApDung:number|null;trangThaiLichTongHop:string;phienBanLichTongHop:number|null;ngayPhatHanh:string|null;ngayCongBoLich:string|null;ngayTao:string;ngayCapNhat:string};
export type Semester={ma:string;ten:string|null;namHoc:string|null};
export type RoundFilters={q?:string;maHocKy?:string;trangThai?:string};
export type RoundResult<T>={value:T;canPrepare:boolean};
export type RoundPage=Page<Round>;
