package pbl6.ctsv.shcn.dotsinhhoat.entity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
@Entity @jakarta.persistence.Table(name="shcn_mau_bien_ban",schema="shcn")
public class MauBienBan {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="ma_dot",nullable=false) private Long maDot;
 @Nationalized @Column(name="ten_mau",nullable=false,length=200,columnDefinition="nvarchar(200)") private String tenMau;
 @Column(name="trang_thai",nullable=false,length=40) private String trangThai;
 @Column(name="ma_nguoi_cap_nhat",nullable=false,length=128) private String maNguoiCapNhat;
 @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="ngay_tao",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayTao;
 @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="ngay_cap_nhat",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayCapNhat;
 @JdbcTypeCode(SqlTypes.BINARY) @Column(name="phien_ban_ghi",nullable=false,columnDefinition="rowversion",insertable=false,updatable=false) private byte[] phienBanGhi;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ma_dot",insertable=false,updatable=false) private DotSinhHoat dot;
 protected MauBienBan(){}
 public static MauBienBan create(long dot,String name,String actor,OffsetDateTime now){var m=new MauBienBan();m.maDot=dot;m.ngayTao=now;m.configure(name,actor,now);return m;}
 public void configure(String name,String actor,OffsetDateTime now){tenMau=name;maNguoiCapNhat=actor;ngayCapNhat=now;trangThai="HOP_LE";}
 public Long id(){return id;}public String name(){return tenMau;}public String state(){return trangThai;}
}
