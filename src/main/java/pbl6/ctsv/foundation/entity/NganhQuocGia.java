package pbl6.ctsv.foundation.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Foundation-owned mapping of the existing schema. See docs/persistence.md before adding writes. */
@Entity
@Table(name="nganh_quoc_gia", schema="dbo")
@DynamicInsert
@DynamicUpdate
public class NganhQuocGia {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false)
    private Long id;

    @Nationalized
    @Column(name="ma", nullable=true, length=128)
    private String ma;

    @Nationalized
    @Column(name="ten", nullable=true, length=512)
    private String ten;

    @Nationalized
    @Column(name="cap_dao_tao_nguon", nullable=true, length=512)
    private String capDaoTaoNguon;

    @Column(name="he_thong_nguon_id", nullable=true)
    private Long heThongNguonId;

    @Nationalized
    @Column(name="ma_dinh_danh_ben_ngoai", nullable=true, length=128)
    private String maDinhDanhBenNgoai;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_cap_nhat_nguon", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemCapNhatNguon;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_dong_bo", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemDongBo;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_tao", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemTao;

    @TimeZoneStorage(TimeZoneStorageType.NATIVE)
    @Column(name="thoi_diem_cap_nhat", nullable=true, columnDefinition="datetimeoffset(7)")
    private OffsetDateTime thoiDiemCapNhat;

    @Column(name="sha256_ban_ghi_nguon", nullable=true, length=64)
    private String sha256BanGhiNguon;

    @Nationalized
    @Column(name="phien_ban_anh_xa", nullable=true, length=128)
    private String phienBanAnhXa;

    @Nationalized
    @Column(name="trang_thai_chuan_hoa", nullable=true, length=128)
    private String trangThaiChuanHoa;

    @Nationalized
    @Column(name="loi_chuan_hoa", nullable=true, columnDefinition="nvarchar(max)")
    private String loiChuanHoa;

    @Column(name="dang_hien_dien_nguon", nullable=false)
    private Boolean dangHienDienNguon;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="he_thong_nguon_id", insertable=false, updatable=false)
    private HeThongNgoai heThongNguon;

    public NganhQuocGia() {}

    public Long getId() { return id; }
    public String getMa() { return ma; }
    public void setMa(String value) { this.ma = value; }
    public String getTen() { return ten; }
    public void setTen(String value) { this.ten = value; }
    public String getCapDaoTaoNguon() { return capDaoTaoNguon; }
    public void setCapDaoTaoNguon(String value) { this.capDaoTaoNguon = value; }
    public Long getHeThongNguonId() { return heThongNguonId; }
    public void setHeThongNguonId(Long value) { this.heThongNguonId = value; }
    public String getMaDinhDanhBenNgoai() { return maDinhDanhBenNgoai; }
    public void setMaDinhDanhBenNgoai(String value) { this.maDinhDanhBenNgoai = value; }
    public OffsetDateTime getThoiDiemCapNhatNguon() { return thoiDiemCapNhatNguon; }
    public void setThoiDiemCapNhatNguon(OffsetDateTime value) { this.thoiDiemCapNhatNguon = value; }
    public OffsetDateTime getThoiDiemDongBo() { return thoiDiemDongBo; }
    public void setThoiDiemDongBo(OffsetDateTime value) { this.thoiDiemDongBo = value; }
    public OffsetDateTime getThoiDiemTao() { return thoiDiemTao; }
    public void setThoiDiemTao(OffsetDateTime value) { this.thoiDiemTao = value; }
    public OffsetDateTime getThoiDiemCapNhat() { return thoiDiemCapNhat; }
    public void setThoiDiemCapNhat(OffsetDateTime value) { this.thoiDiemCapNhat = value; }
    public String getSha256BanGhiNguon() { return sha256BanGhiNguon; }
    public void setSha256BanGhiNguon(String value) { this.sha256BanGhiNguon = value; }
    public String getPhienBanAnhXa() { return phienBanAnhXa; }
    public void setPhienBanAnhXa(String value) { this.phienBanAnhXa = value; }
    public String getTrangThaiChuanHoa() { return trangThaiChuanHoa; }
    public void setTrangThaiChuanHoa(String value) { this.trangThaiChuanHoa = value; }
    public String getLoiChuanHoa() { return loiChuanHoa; }
    public void setLoiChuanHoa(String value) { this.loiChuanHoa = value; }
    public Boolean getDangHienDienNguon() { return dangHienDienNguon; }
    public void setDangHienDienNguon(Boolean value) { this.dangHienDienNguon = value; }
    public HeThongNgoai getHeThongNguon() { return heThongNguon; }
}
