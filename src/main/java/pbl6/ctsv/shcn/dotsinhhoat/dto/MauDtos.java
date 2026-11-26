package pbl6.ctsv.shcn.dotsinhhoat.dto;

import java.util.List;

public final class MauDtos {
 private MauDtos(){}
 public record Truong(String maTruong,String nhan,String kieuDuLieu,boolean batBuoc,String nguonDien,String maNguonHeThong){}
 public record Muc(String maMuc,String tieuDe,int thuTuHienThi,String loaiMuc,boolean batBuoc,List<Truong> cauHinhTruong,boolean laMucHeThong){
  public Muc{cauHinhTruong=List.copyOf(cauHinhTruong);}
  public Muc order(int value){return new Muc(maMuc,tieuDe,value,loaiMuc,batBuoc,cauHinhTruong,laMucHeThong);}
 }
 public record Nguon(String ma,String nhan,String kieuDuLieu){}
 public record DanhMuc(List<String> kieuDuLieu,List<Nguon> nguonHeThong,List<Muc> mucHeThong){
  public DanhMuc{kieuDuLieu=List.copyOf(kieuDuLieu);nguonHeThong=List.copyOf(nguonHeThong);mucHeThong=List.copyOf(mucHeThong);}
 }
 public record Draft(String tenMau,List<Muc> muc){public Draft{muc=List.copyOf(muc);}}
 public record Input(byte[] expectedVersion,String tenMau,List<Muc> muc){
  public Input{expectedVersion=expectedVersion.clone();muc=List.copyOf(muc);}
  @Override public byte[] expectedVersion(){return expectedVersion.clone();}
 }
 public record MauDTO(String id,String dotId,String dotVersion,boolean daCoMau,boolean duocSua,String tenMau,String trangThai,List<Muc> muc,DanhMuc danhMuc){
  public MauDTO{muc=List.copyOf(muc);}
 }
}
