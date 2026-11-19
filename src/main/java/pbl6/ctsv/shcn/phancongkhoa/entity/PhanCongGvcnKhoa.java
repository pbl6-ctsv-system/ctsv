package pbl6.ctsv.shcn.phancongkhoa.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

@Entity
@Table(name="shcn_phan_cong_gvcn_khoa",schema="shcn")
@DynamicUpdate
public class PhanCongGvcnKhoa {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false) private Long id;
    @Column(name="ma_danh_sach_khoa",nullable=false) private Long maDanhSachKhoa;
    @Column(name="ma_lop",nullable=false,length=32) private String maLop;
    @Column(name="ma_giao_vien",nullable=false,length=32) private String maGiaoVien;
    @Nationalized @Column(name="ghi_chu",columnDefinition="nvarchar(max)") private String ghiChu;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="ngay_tao",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayTao;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="ngay_cap_nhat",nullable=false,columnDefinition="datetimeoffset(7)") private OffsetDateTime ngayCapNhat;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="ma_danh_sach_khoa",insertable=false,updatable=false)
    private DanhSachPhanCongKhoa danhSachKhoa;

    protected PhanCongGvcnKhoa() {}
    public PhanCongGvcnKhoa(long danhSach,String maLop,String maGiaoVien,String ghiChu,OffsetDateTime now) {
        this.maDanhSachKhoa=danhSach;this.maLop=maLop;this.maGiaoVien=maGiaoVien;
        this.ghiChu=ghiChu;this.ngayTao=now;this.ngayCapNhat=now;
    }
    public void capNhat(String maGiaoVien,String ghiChu,OffsetDateTime now) {
        this.maGiaoVien=maGiaoVien;this.ghiChu=ghiChu;this.ngayCapNhat=now;
    }
    public Long getId(){return id;}
    public Long getMaDanhSachKhoa(){return maDanhSachKhoa;}
    public String getMaLop(){return maLop;}
    public String getMaGiaoVien(){return maGiaoVien;}
    public String getGhiChu(){return ghiChu;}
    public OffsetDateTime getNgayTao(){return ngayTao;}
    public OffsetDateTime getNgayCapNhat(){return ngayCapNhat;}
    public DanhSachPhanCongKhoa getDanhSachKhoa(){return danhSachKhoa;}
}
