package pbl6.ctsv.shcn.dotsinhhoat.dto;

import java.time.*;
import java.util.List;

public final class DotDtos {
    private DotDtos(){}
    public record KhoangTiet(int tietDau,int tietCuoi){}
    public record CauHinh(String maHocKy,String tenDot,LocalDate khungNgayBatDau,LocalDate khungNgayKetThuc,
                         List<KhoangTiet> khungTietChoPhep,OffsetDateTime hanChotDangKyLich){
        public CauHinh{khungTietChoPhep=List.copyOf(khungTietChoPhep);}
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
                         String ngayCongBoLich,String ngayTao,String ngayCapNhat){
        public DotDTO{khungTietChoPhep=List.copyOf(khungTietChoPhep);}
    }
}
