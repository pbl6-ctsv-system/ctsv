package pbl6.ctsv.shcn.dotsinhhoat.dto;

import java.time.*;
import java.util.List;

public final class DotDtos {
    private DotDtos(){}
    public record KhoangTiet(int tietDau,int tietCuoi){}
    public record CauHinh(String maHocKy,String tenDot,LocalDate khungNgayBatDau,LocalDate khungNgayKetThuc,
                         List<KhoangTiet> khungTietChoPhep,OffsetDateTime hanChotDangKyLich,List<DoiTuong> doiTuongApDung,MauDtos.Draft mauBienBan){
        public CauHinh{khungTietChoPhep=List.copyOf(khungTietChoPhep);doiTuongApDung=doiTuongApDung==null?null:List.copyOf(doiTuongApDung);}
        public CauHinh(String ky,String ten,LocalDate tu,LocalDate den,List<KhoangTiet> tiet,OffsetDateTime han,List<DoiTuong> nhom){this(ky,ten,tu,den,tiet,han,nhom,null);}
        public CauHinh(String ky,String ten,LocalDate tu,LocalDate den,List<KhoangTiet> tiet,OffsetDateTime han){this(ky,ten,tu,den,tiet,han,null);}
    }
    public record DoiTuong(String loaiDoiTuong,String maDoiTuong,String hinhThucChoPhep,
                           LocalDate khungNgayBatDau,LocalDate khungNgayKetThuc,List<KhoangTiet> khungTietChoPhep){
        public DoiTuong{khungTietChoPhep=khungTietChoPhep==null?null:List.copyOf(khungTietChoPhep);}
    }
    public record KhoaHocDTO(String ma,String ten,long soLop){}
    public record LopTreTienDoDTO(String ma,String ten,long soSinhVien){}
    public record CanhBao(String ma,String thongDiep){}
    public record NhomLopDTO(List<KhoaHocDTO> khoaHoc,List<LopTreTienDoDTO> lopTreTienDo,long soLopChuaCoKhoa,String trangThaiLopTreTienDo,
                             String trangThaiDuLieuHocVu,String trangThaiPhanNhom,List<CanhBao> canhBao){
        public NhomLopDTO{khoaHoc=List.copyOf(khoaHoc);lopTreTienDo=List.copyOf(lopTreTienDo);canhBao=List.copyOf(canhBao);}
        public NhomLopDTO(List<KhoaHocDTO> khoaHoc,List<LopTreTienDoDTO> lopTreTienDo,long missing,String status){
            this(khoaHoc,lopTreTienDo,missing,status,"CHUA_CO_DU_LIEU","CAN_XAC_MINH",List.of());
        }
    }
    public record BoChonNguon(String loaiDoiTuong,String maDoiTuong){}
    public record TongHopDoiTuong(long soBoChon,long tongSoLop,long soLopThuong,long soLopTreTienDo){}
    public record LopApDungDTO(String maLop,String tenLop,String loaiLop,String tenKhoaHoc,String maKhoa,String tenKhoa,
                               String maChuongTrinh,String tenChuongTrinh,LocalDate tuNgay,LocalDate denNgay,
                               List<KhoangTiet> khungTietChoPhep,String hinhThucChoPhep,List<BoChonNguon> boChonNguon,
                               long soSinhVien,long soSinhVienCanXacMinh){}
    public record DoiTuongDTO(String dotId,String dotVersion,List<DoiTuong> items,TongHopDoiTuong tongHop,
                             Page<LopApDungDTO> lopApDung,String trangThaiPhanNhom,List<CanhBao> canhBao){}

    public record SuaDot(byte[] expectedVersion,CauHinh cauHinh){
        public SuaDot{expectedVersion=expectedVersion.clone();}
        @Override public byte[] expectedVersion(){return expectedVersion.clone();}
    }
    public record Query(int page,int size,int offset,boolean descending,String q,String maHocKy,String maKhoa,String trangThai){}
    public record Page<T>(List<T> items,int page,int size,long totalElements){public Page{items=List.copyOf(items);}}
    public record HocKyDTO(String ma,String ten,String namHoc){}
    public record Quyen(boolean read,boolean prepare,boolean reference){
        public boolean canRead(){return read||prepare;}
    }
    public record DotDTO(String id,String version,String maHocKy,String tenDot,LocalDate khungNgayBatDau,
                         LocalDate khungNgayKetThuc,List<KhoangTiet> khungTietChoPhep,String hanChotDangKyLich,
                         String trangThai,String trangThaiPhanCong,Integer phienBanPhanCongApDung,
                         String trangThaiLichTongHop,Integer phienBanLichTongHop,String ngayPhatHanh,
                         String ngayCongBoLich,String ngayTao,String ngayCapNhat,List<DoiTuong> doiTuongApDung){
        public DotDTO{khungTietChoPhep=List.copyOf(khungTietChoPhep);doiTuongApDung=List.copyOf(doiTuongApDung);}
    }
    public record LopTheoKyDTO(String ma,String ten,String loaiLop,String maKhoaHoc,String tenKhoaHoc,long soSinhVien,long soSinhVienCanXacMinh){}
    public record SinhVienTheoKyDTO(String id,String maSinhVien,String hoTen,String maLop,String tenLop,String loaiLop,String canCu,String canCuLop,String trangThaiXacMinh,boolean duCanCuNguon){}
}
