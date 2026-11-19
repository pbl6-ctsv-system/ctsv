package pbl6.ctsv.shcn.phancongkhoa.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.hibernate.type.SqlTypes;

/** SHCN-owned entity; reference codes remain scalar and do not map Foundation entities. */
@Entity
@Table(name="shcn_danh_sach_phan_cong_khoa",schema="shcn")
@DynamicUpdate
public class DanhSachPhanCongKhoa {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false) private Long id;
    @Column(name="ma_khoa",nullable=false,length=32) private String maKhoa;
    @Nationalized @Column(name="nam_hoc",nullable=false,length=20) private String namHoc;
    @Column(name="phien_ban",nullable=false) private Integer phienBan;
    @Column(name="ma_danh_sach_truoc") private Long maDanhSachTruoc;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="hieu_luc_tu",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime hieuLucTu;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="hieu_luc_den",columnDefinition="datetimeoffset(7)") private OffsetDateTime hieuLucDen;
    @Column(name="trang_thai",nullable=false,length=40) private String trangThai;
    @Column(name="ma_nguoi_lap",nullable=false,length=128) private String maNguoiLap;
    @Column(name="ma_ho_so_tai_lieu",length=128) private String maHoSoTaiLieu;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="ngay_gui_chuyen_vien",columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayGuiChuyenVien;
    @Nationalized @Column(name="ly_do_dieu_chinh",columnDefinition="nvarchar(max)") private String lyDoDieuChinh;
    @Nationalized @Column(name="ly_do_huy",columnDefinition="nvarchar(max)") private String lyDoHuy;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="ngay_tao",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayTao;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="ngay_cap_nhat",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayCapNhat;
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name="phien_ban_ghi",nullable=false,insertable=false,updatable=false,columnDefinition="rowversion")
    private byte[] phienBanGhi;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="ma_danh_sach_truoc",insertable=false,updatable=false)
    private DanhSachPhanCongKhoa danhSachTruoc;

    protected DanhSachPhanCongKhoa() {}

    public static DanhSachPhanCongKhoa nhap(String maKhoa,String namHoc,int phienBan,Long maDanhSachTruoc,
            OffsetDateTime hieuLucTu,OffsetDateTime hieuLucDen,String maNguoiLap,String lyDoDieuChinh,OffsetDateTime now) {
        var row=new DanhSachPhanCongKhoa();
        row.maKhoa=maKhoa;row.namHoc=namHoc;row.phienBan=phienBan;row.maDanhSachTruoc=maDanhSachTruoc;
        row.hieuLucTu=hieuLucTu;row.hieuLucDen=hieuLucDen;row.maNguoiLap=maNguoiLap;
        row.lyDoDieuChinh=lyDoDieuChinh;row.ngayTao=now;row.ngayCapNhat=now;row.trangThai="NHAP";
        return row;
    }

    public Long getId(){return id;}
    public String getMaKhoa(){return maKhoa;}
    public String getNamHoc(){return namHoc;}
    public Integer getPhienBan(){return phienBan;}
    public Long getMaDanhSachTruoc(){return maDanhSachTruoc;}
    public OffsetDateTime getHieuLucTu(){return hieuLucTu;}
    public OffsetDateTime getHieuLucDen(){return hieuLucDen;}
    public String getTrangThai(){return trangThai;}
    public String getMaNguoiLap(){return maNguoiLap;}
    public String getMaHoSoTaiLieu(){return maHoSoTaiLieu;}
    public OffsetDateTime getNgayGuiChuyenVien(){return ngayGuiChuyenVien;}
    public String getLyDoDieuChinh(){return lyDoDieuChinh;}
    public String getLyDoHuy(){return lyDoHuy;}
    public OffsetDateTime getNgayTao(){return ngayTao;}
    public OffsetDateTime getNgayCapNhat(){return ngayCapNhat;}
    public byte[] getPhienBanGhi(){return phienBanGhi==null?null:phienBanGhi.clone();}
    public DanhSachPhanCongKhoa getDanhSachTruoc(){return danhSachTruoc;}
}
