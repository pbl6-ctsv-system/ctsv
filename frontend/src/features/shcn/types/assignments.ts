export type Page<T>={items:T[];page:number;size:number;totalElements:number};
export type Faculty={id:string;maKhoa:string;tenKhoa:string;duocDoc:boolean;duocLapNhap:boolean;coTheChon:boolean;lyDoKhongTheChon:string|null};
export type Program={id:string;ma:string;ten:string;tenNganhMoi:string|null};
export type SchoolClass={id:string;maLop:string;tenLop:string;maKhoa:string;chuongTrinh:Program|null;coTheChon:boolean;lyDoKhongTheChon:string|null};
export type Lecturer={idCanBo:string;maGiaoVien:string;hoTen:string;maKhoaCongTac:string|null;coTheChon:boolean;lyDoKhongTheChon:string|null};
export type Assignment={id?:string;maLop:string;maGiaoVien:string;ghiChu:string|null;lopHienTai:SchoolClass|null;giangVienHienTai:Lecturer|null};
export type Summary={id:string;maKhoa:string;namHoc:string;phienBan:number;trangThai:string;hieuLucTu:string;hieuLucDen:string|null;soLop:number;ngayTao:string;ngayCapNhat:string};
export type Draft=Summary&{maDanhSachTruoc:string|null;maNguoiLap:string;lyDoDieuChinh:string|null;version:string;cacPhanCong:Assignment[]};
