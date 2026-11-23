package pbl6.ctsv.shcn.dotsinhhoat.dto;

import java.time.*;
import java.util.List;

public final class DotDtos {
    private DotDtos(){}
    public record KhoangTiet(int tietDau,int tietCuoi){}
    public record CauHinh(String maHocKy,String tenDot,LocalDate khungNgayBatDau,LocalDate khungNgayKetThuc,
                         List<KhoangTiet> khungTietChoPhep,OffsetDateTime hanChotDangKyLich,List<DoiTuong> doiTuongApDung){
        public CauHinh{khungTietChoPhep=List.copyOf(khungTietChoPhep);doiTuongApDung=doiTuongApDung==null?null:List.copyOf(doiTuongApDung);}
        public CauHinh(String ky,String ten,LocalDate tu,LocalDate den,List<KhoangTiet> tiet,OffsetDateTime han){this(ky,ten,tu,den,tiet,han,null);}
    }
    public record DoiTuong(String loaiDoiTuong,String maDoiTuong,String hinhThucChoPhep,
                           LocalDate khungNgayBatDau,LocalDate khungNgayKetThuc,List<KhoangTiet> khungTietChoPhep){
        public DoiTuong{khungTietChoPhep=khungTietChoPhep==null?null:List.copyOf(khungTietChoPhep);}
    }
    public record KhoaHocDTO(String ma,String ten,long soLop){}
    public record LopTreTienDoDTO(String ma,String ten,long soSinhVien){}
    public record NhomLopDTO(List<KhoaHocDTO> khoaHoc,List<LopTreTienDoDTO> lopTreTienDo,long soLopChuaCoKhoa,String trangThaiLopTreTienDo){
        public NhomLopDTO{khoaHoc=List.copyOf(khoaHoc);lopTreTienDo=List.copyOf(lopTreTienDo);}
    }
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
}
