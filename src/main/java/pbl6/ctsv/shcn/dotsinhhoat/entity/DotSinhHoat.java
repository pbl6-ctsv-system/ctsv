package pbl6.ctsv.shcn.dotsinhhoat.entity;
import jakarta.persistence.*;
import java.time.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import pbl6.ctsv.shcn.dotsinhhoat.dto.DotDtos.CauHinh;

/** SHCN-owned root. Reference codes remain scalar; no Foundation mapping changes. */
@Entity @jakarta.persistence.Table(name="shcn_dot_sinh_hoat",schema="shcn")
public class DotSinhHoat {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false) private Long id;
    @Column(name="ma_hoc_ky",nullable=false,length=32) private String maHocKy;
    @Nationalized
    @Column(name="ten_dot",nullable=false,length=200,columnDefinition="nvarchar(200)") private String tenDot;
    @Column(name="khung_ngay_bat_dau",nullable=false) private LocalDate khungNgayBatDau;
    @Column(name="khung_ngay_ket_thuc",nullable=false) private LocalDate khungNgayKetThuc;
    @Nationalized
    @Column(name="khung_tiet_cho_phep",nullable=false,columnDefinition="nvarchar(max)") private String khungTietChoPhep;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="han_chot_dang_ky_lich",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime hanChotDangKyLich;
    @Column(name="trang_thai",nullable=false,length=40) private String trangThai;
    @Column(name="ma_nguoi_tao",nullable=false,length=128) private String maNguoiTao;
    @Column(name="ma_ho_so_duyet_dot",nullable=true,length=128) private String maHoSoDuyetDot;
    @Column(name="ma_nguoi_phat_hanh",nullable=true,length=128) private String maNguoiPhatHanh;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="ngay_phat_hanh",nullable=true,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayPhatHanh;
    @Column(name="ma_nguoi_huy",nullable=true,length=128) private String maNguoiHuy;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="ngay_huy",nullable=true,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayHuy;
    @Nationalized
    @Column(name="ly_do_huy",nullable=true,columnDefinition="nvarchar(max)") private String lyDoHuy;
    @Column(name="phien_ban_phan_cong_ap_dung",nullable=true) private Integer phienBanPhanCongApDung;
    @Column(name="trang_thai_phan_cong",nullable=false,length=40) private String trangThaiPhanCong;
    @Column(name="ma_ho_so_tai_lieu_phan_cong",nullable=true,length=128) private String maHoSoTaiLieuPhanCong;
    @Column(name="trang_thai_lich_tong_hop",nullable=false,length=40) private String trangThaiLichTongHop;
    @Column(name="phien_ban_lich_tong_hop",nullable=true) private Integer phienBanLichTongHop;
    @Nationalized
    @Column(name="lich_tong_hop_da_trinh",nullable=true,columnDefinition="nvarchar(max)") private String lichTongHopDaTrinh;
    @Column(name="ma_ho_so_trinh_lich",nullable=true,length=128) private String maHoSoTrinhLich;
    @Column(name="ma_nguoi_cong_bo_lich",nullable=true,length=128) private String maNguoiCongBoLich;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="ngay_cong_bo_lich",nullable=true,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayCongBoLich;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="ngay_tao",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayTao;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="ngay_cap_nhat",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayCapNhat;
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name="phien_ban_ghi",nullable=false,columnDefinition="rowversion",insertable=false,updatable=false) private byte[] phienBanGhi;
    protected DotSinhHoat(){}
    public static DotSinhHoat nhap(CauHinh input,String ranges,String actor,OffsetDateTime now){
        var d=new DotSinhHoat();d.maHocKy=input.maHocKy();d.tenDot=input.tenDot();d.khungNgayBatDau=input.khungNgayBatDau();
        d.khungNgayKetThuc=input.khungNgayKetThuc();d.khungTietChoPhep=ranges;d.hanChotDangKyLich=input.hanChotDangKyLich();
        d.trangThai="NHAP";d.trangThaiPhanCong="CHUA_DONG_BO";d.trangThaiLichTongHop="DANG_CHOT";
        d.maNguoiTao=actor;d.ngayTao=now;d.ngayCapNhat=now;return d;
    }
    public Long getId(){return id;}
    public String getMaHocKy(){return maHocKy;}
    public String getTenDot(){return tenDot;}
    public LocalDate getKhungNgayBatDau(){return khungNgayBatDau;}
    public LocalDate getKhungNgayKetThuc(){return khungNgayKetThuc;}
    public String getKhungTietChoPhep(){return khungTietChoPhep;}
    public OffsetDateTime getHanChotDangKyLich(){return hanChotDangKyLich;}
    public String getTrangThai(){return trangThai;}
    public String getMaNguoiTao(){return maNguoiTao;}
    public String getMaHoSoDuyetDot(){return maHoSoDuyetDot;}
    public String getMaNguoiPhatHanh(){return maNguoiPhatHanh;}
    public OffsetDateTime getNgayPhatHanh(){return ngayPhatHanh;}
    public String getMaNguoiHuy(){return maNguoiHuy;}
    public OffsetDateTime getNgayHuy(){return ngayHuy;}
    public String getLyDoHuy(){return lyDoHuy;}
    public Integer getPhienBanPhanCongApDung(){return phienBanPhanCongApDung;}
    public String getTrangThaiPhanCong(){return trangThaiPhanCong;}
    public String getMaHoSoTaiLieuPhanCong(){return maHoSoTaiLieuPhanCong;}
    public String getTrangThaiLichTongHop(){return trangThaiLichTongHop;}
    public Integer getPhienBanLichTongHop(){return phienBanLichTongHop;}
    public String getLichTongHopDaTrinh(){return lichTongHopDaTrinh;}
    public String getMaHoSoTrinhLich(){return maHoSoTrinhLich;}
    public String getMaNguoiCongBoLich(){return maNguoiCongBoLich;}
    public OffsetDateTime getNgayCongBoLich(){return ngayCongBoLich;}
    public OffsetDateTime getNgayTao(){return ngayTao;}
    public OffsetDateTime getNgayCapNhat(){return ngayCapNhat;}
    public byte[] getPhienBanGhi(){return phienBanGhi==null?null:phienBanGhi.clone();}
}
