package pbl6.ctsv.shcn.phancongkhoa.dto;

import java.util.List;

/** Wire projections owned by SHCN. No Foundation entity, personal contacts or credentials. */
public final class PhanCongKhoaDtos {
    public record Page<T>(List<T> items,int page,int size,long totalElements){
        public Page{items=List.copyOf(items);}
    }
    public record KhoaDTO(String id,String maKhoa,String tenKhoa,boolean duocDoc,boolean duocLapNhap,
                          boolean coTheChon,String lyDoKhongTheChon){}
    public record PhamViDTO(List<KhoaDTO> khoa){public PhamViDTO{khoa=List.copyOf(khoa);}}
    public record ChuongTrinhDTO(String id,String ma,String ten,String tenNganhMoi){}
    public record LopDTO(String id,String maLop,String tenLop,String maKhoa,ChuongTrinhDTO chuongTrinh,
                         boolean coTheChon,String lyDoKhongTheChon){}
    public record GiangVienDTO(String idCanBo,String maGiaoVien,String hoTen,String maKhoaCongTac,
                              boolean coTheChon,String lyDoKhongTheChon){}
    public record DongPhanCongDTO(String id,String maLop,String maGiaoVien,String ghiChu,
                                 LopDTO lopHienTai,GiangVienDTO giangVienHienTai){}
    public record DanhSachTomTatDTO(String id,String maKhoa,String namHoc,int phienBan,String trangThai,
                                    String hieuLucTu,String hieuLucDen,int soLop,String ngayTao,String ngayCapNhat){}
    public record DanhSachDTO(String id,String maKhoa,String namHoc,int phienBan,String trangThai,
                              String hieuLucTu,String hieuLucDen,int soLop,String ngayTao,String ngayCapNhat,
                              String maDanhSachTruoc,String maNguoiLap,String lyDoDieuChinh,String version,
                              List<DongPhanCongDTO> cacPhanCong){
        public DanhSachDTO{cacPhanCong=List.copyOf(cacPhanCong);}
    }
    private PhanCongKhoaDtos(){}
}
