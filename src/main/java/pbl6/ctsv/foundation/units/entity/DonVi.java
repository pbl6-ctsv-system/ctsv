package pbl6.ctsv.foundation.units.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import org.hibernate.annotations.Nationalized;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;

/** Foundation-owned projection. Sync writes source fields through its guarded JDBC connection. */
@Entity
@Table(name="don_vi",schema="dbo")
public class DonVi {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Nationalized @Column(name="ma",length=128,nullable=false) private String ma;
    @Nationalized @Column(name="ten",length=512) private String ten;
    @Nationalized @Column(name="ma_bo_phan_nguon",length=512) private String maBoPhanNguon;
    @Nationalized @Column(name="loai_don_vi",length=128) private String loaiDonVi;
    @Column(name="don_vi_cha_id") private Long donViChaId;
    @Column(name="dang_hoat_dong") private Boolean dangHoatDong;
    @Column(name="he_thong_nguon_id") private Long heThongNguonId;
    @Nationalized @Column(name="ma_dinh_danh_ben_ngoai",length=128) private String maDinhDanhBenNgoai;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="thoi_diem_cap_nhat_nguon") private OffsetDateTime thoiDiemCapNhatNguon;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="thoi_diem_dong_bo") private OffsetDateTime thoiDiemDongBo;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="thoi_diem_tao") private OffsetDateTime thoiDiemTao;
    @TimeZoneStorage(TimeZoneStorageType.NATIVE) @Column(name="thoi_diem_cap_nhat") private OffsetDateTime thoiDiemCapNhat;
    @Column(name="sha256_ban_ghi_nguon",length=64) private String sha256BanGhiNguon;
    @Nationalized @Column(name="phien_ban_anh_xa",length=128) private String phienBanAnhXa;
    @Nationalized @Column(name="trang_thai_chuan_hoa",length=128) private String trangThaiChuanHoa;
    @Nationalized @Column(name="loi_chuan_hoa",columnDefinition="nvarchar(max)") private String loiChuanHoa;
    @Nationalized @Column(name="luu_tru_nguon",length=512) private String luuTruNguon;
    @Nationalized @Column(name="is_us_nguon",length=512) private String isUsNguon;
    @Column(name="dang_hoat_dong_nguon") private Boolean dangHoatDongNguon;
    protected DonVi() {}
    public Long getId(){return id;}
}
