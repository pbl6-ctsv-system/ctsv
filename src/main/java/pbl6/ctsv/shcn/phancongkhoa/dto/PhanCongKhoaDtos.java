package pbl6.ctsv.shcn.phancongkhoa.dto;

import java.util.List;

/** Wire projections owned by SHCN. No Foundation entity, personal contacts or credentials. */
public final class PhanCongKhoaDtos {
    public record Page<T>(List<T> items,int page,int size,long totalElements){
        public Page{items=List.copyOf(items);}
    }
    public record TrangDanhSachDTO(List<DanhSachTomTatDTO> items,int page,int size,long totalElements,int phienBanLonNhat){
        public TrangDanhSachDTO{items=List.copyOf(items);}
    }
    public record KhoaDTO(String id,String maKhoa,String tenKhoa,boolean duocDoc,boolean duocLapNhap,
                          boolean duocKySo,boolean duocGuiChuyenVien,Integer soLopHoatDong,
                          boolean coTheChon,String lyDoKhongTheChon){
        public KhoaDTO(String id,String maKhoa,String tenKhoa,boolean duocDoc,boolean duocLapNhap,
                       boolean coTheChon,String lyDoKhongTheChon){
            this(id,maKhoa,tenKhoa,duocDoc,duocLapNhap,false,false,null,coTheChon,lyDoKhongTheChon);
        }
    }
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
    public record NoiDungHanhChinhDTO(String kinhGui,String canCu,String loiDan,
                                      String diaDanhNgayThang,String hoTenNguoiKy,String ghiChuChanTrang){}
    public record TepTaiLieuDTO(String id,String fileName,long size,String sha256,boolean ready){}
    public record BuocKyKhoaDTO(String status,String signerName,String signerEmail,String signedAt,
                                String certificateSerial,Integer page,Double x,Double y,Double width,Double height){}
    public record YeuCauKyKhoaDTO(String id,String status,String mode,String errorCode,
                                  boolean canSign,boolean canReconcile,boolean canSendSpecialist,
                                  boolean canReopenDraft,boolean canUpdatePdfContent,BuocKyKhoaDTO step){}
    public record PhienBanPdfKhoaDTO(String phienBanVanBanId,int soPhienBan,boolean current,
                                     String title,String createdAt,TepTaiLieuDTO tepGoc,TepTaiLieuDTO tepDaKy){}
    public record MucLichSuKyDTO(String id,String action,String reason,String at){}
    public record HoSoKyKhoaDTO(String danhSachId,String maKhoa,String tenKhoa,String tenKhoaGocTraiPdf,
                                String namHoc,int phienBan,String trangThaiDanhSach,String ngayGuiChuyenVien,
                                String version,String vanBanId,String phienBanVanBanId,String datasetSha256,
                                NoiDungHanhChinhDTO noiDungHanhChinh,TepTaiLieuDTO tepGoc,TepTaiLieuDTO tepDaKy,
                                YeuCauKyKhoaDTO yeuCauKy,List<PhienBanPdfKhoaDTO> cacPhienBanPdf,List<MucLichSuKyDTO> lichSu){
        public HoSoKyKhoaDTO{
            cacPhienBanPdf=cacPhienBanPdf==null?List.of():List.copyOf(cacPhienBanPdf);
            lichSu=lichSu==null?List.of():List.copyOf(lichSu);
        }
    }
    private PhanCongKhoaDtos(){}
}
