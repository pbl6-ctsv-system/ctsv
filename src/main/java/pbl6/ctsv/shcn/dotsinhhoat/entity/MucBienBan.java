package pbl6.ctsv.shcn.dotsinhhoat.entity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import org.hibernate.annotations.*;
import pbl6.ctsv.shcn.dotsinhhoat.dto.MauDtos.Muc;
@Entity @jakarta.persistence.Table(name="shcn_muc_bien_ban",schema="shcn")
public class MucBienBan {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="ma_mau",nullable=false) private Long maMau;
 @Column(name="ma_muc",nullable=false,length=32) private String maMuc;
 @Nationalized @Column(name="tieu_de",nullable=false,length=250,columnDefinition="nvarchar(250)") private String tieuDe;
 @Column(name="thu_tu_hien_thi",nullable=false) private int thuTu;
 @Column(name="loai_muc",nullable=false,length=32) private String loaiMuc;
 @Column(name="la_muc_he_thong",nullable=false) private boolean heThong;
 @Column(name="bat_buoc",nullable=false) private boolean batBuoc;
 @Nationalized @Column(name="cau_hinh_truong",nullable=false,columnDefinition="nvarchar(max)") private String fields;
 @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="ngay_tao",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayTao;
 @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="ngay_cap_nhat",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayCapNhat;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ma_mau",insertable=false,updatable=false) private MauBienBan mau;
 protected MucBienBan(){}
 public static MucBienBan create(long parent,Muc m,String json,OffsetDateTime now){var r=new MucBienBan();r.maMau=parent;r.maMuc=m.maMuc();r.ngayTao=now;r.configure(m,json,now);return r;}
 public void configure(Muc m,String json,OffsetDateTime now){tieuDe=m.tieuDe();thuTu=m.thuTuHienThi();loaiMuc=m.loaiMuc();heThong=m.laMucHeThong();batBuoc=m.batBuoc();fields=json;ngayCapNhat=now;}
 public void temporaryOrder(int value){thuTu=value;}
 public String key(){return maMuc;}public int order(){return thuTu;}public String fields(){return fields;}
 public Muc dto(java.util.List<pbl6.ctsv.shcn.dotsinhhoat.dto.MauDtos.Truong> values){return new Muc(maMuc,tieuDe,thuTu,loaiMuc,batBuoc,values,heThong);}
}
