package pbl6.ctsv.mghp.application;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

final class ApplicationEntities {
    private ApplicationEntities() {}

    @MappedSuperclass
    abstract static class Row {
        @Id
        @GeneratedValue(strategy=GenerationType.IDENTITY)
        Long id;
    }

    @Entity(name="P06Application")
    @Table(name="mghp_ho_so",schema="dbo")
    static class Application extends Row {
        @Column(name="ma_ho_so",nullable=false,columnDefinition="nvarchar(128)") String code;
        @Column(name="dot_xet_id",nullable=false) Long roundId;
        @Column(name="sinh_vien_id",nullable=false) Long studentId;
        @Column(name="qua_trinh_hoc_id",nullable=false) Long processId;
        @Column(name="trang_thai",columnDefinition="nvarchar(128)") String status;
        @Column(name="nguon",columnDefinition="nvarchar(128)") String source;
        @Column(name="phien_ban_dong") Integer closedRevision;
        @Column(name="ly_do_rut_ho_so",columnDefinition="nvarchar(max)") String withdrawReason;
        @Column(name="thoi_diem_nop",columnDefinition="datetimeoffset(7)") OffsetDateTime submittedAt;
        @Column(name="nguoi_tao_id") Long createdBy;
        @Column(name="thoi_diem_tao",columnDefinition="datetimeoffset(7)") OffsetDateTime createdAt;
        @Column(name="thoi_diem_cap_nhat",columnDefinition="datetimeoffset(7)") OffsetDateTime updatedAt;
        @Column(name="chinh_sach_id",nullable=false) Long policyId;
        @Column(name="phien_ban_chinh_sach_id",nullable=false) Long policyVersionId;
        @JdbcTypeCode(SqlTypes.BINARY)
        @Column(name="phien_ban_ky_thuat",nullable=false,insertable=false,updatable=false,length=8,columnDefinition="rowversion") byte[] fence;
    }

    @Entity(name="P06Submission")
    @Table(name="mghp_phien_ban_ho_so",schema="dbo")
    static class Submission extends Row {
        @Column(name="ho_so_id",nullable=false) Long applicationId;
        @Column(name="lan_sua_doi",nullable=false) Integer revision;
        @Column(name="sha256_du_lieu_gui",length=64) String hash;
        @Column(name="nguoi_nop_id",nullable=false) Long actorId;
        @Column(name="thoi_diem_nop",nullable=false,columnDefinition="datetimeoffset(7)") OffsetDateTime submittedAt;
        @Column(name="thoi_diem_tao",columnDefinition="datetimeoffset(7)") OffsetDateTime createdAt;
        @Column(name="loai_lan_nop",nullable=false,columnDefinition="nvarchar(128)") String type;
        @Column(name="dot_tiep_nhan_id") Long roundId;
        @Column(name="phien_ban_chinh_sach_ke_khai_id") Long policyVersionId;
        @JdbcTypeCode(SqlTypes.UUID)
        @Column(name="ma_thao_tac_nop",nullable=false,columnDefinition="uniqueidentifier") UUID operationId;
        @Column(name="ho_ten_khai_bao",nullable=false,columnDefinition="nvarchar(512)") String fullName;
        @Column(name="so_cccd_khai_bao",nullable=false,columnDefinition="nvarchar(64)") String citizenId;
        @Column(name="ngay_sinh_khai_bao",nullable=false) LocalDate birthDate;
        @Column(name="noi_sinh_khai_bao",nullable=false,columnDefinition="nvarchar(512)") String birthPlace;
        @Column(name="gioi_tinh_khai_bao",nullable=false,columnDefinition="nvarchar(128)") String gender;
        @Column(name="dan_toc_khai_bao_id",nullable=false) Long ethnicityId;
        @Column(name="ten_dan_toc_luc_nop",columnDefinition="nvarchar(512)") String ethnicityName;
        @Column(name="lop_khai_bao_id",nullable=false) Long classId;
        @Column(name="ma_lop_luc_nop",columnDefinition="nvarchar(128)") String classCode;
        @Column(name="mssv_khai_bao",nullable=false,columnDefinition="nvarchar(64)") String studentCode;
        @Column(name="khoa_hoc_khai_bao_id",nullable=false) Long cohortId;
        @Column(name="ma_khoa_hoc_luc_nop",columnDefinition="nvarchar(128)") String cohortCode;
        @Column(name="khoa_quan_ly_khai_bao_id",nullable=false) Long managementUnitId;
        @Column(name="ten_khoa_luc_nop",columnDefinition="nvarchar(512)") String managementUnitName;
        @Column(name="dia_chi_thuong_tru_khai_bao",nullable=false,columnDefinition="nvarchar(max)") String permanentAddress;
        @Column(name="so_dien_thoai_khai_bao",nullable=false,columnDefinition="nvarchar(64)") String phoneNumber;
        @Column(name="ket_qua_doi_chieu_thong_tin",columnDefinition="nvarchar(128)") String comparisonStatus;
        @Column(name="thoi_diem_lay_du_lieu_sis",columnDefinition="datetimeoffset(7)") OffsetDateTime sourceAt;
        @Column(name="la_van_bang_thu_nhat") Boolean firstDegree;
        @Column(name="da_huong_mghp_cung_trinh_do") Boolean alreadyBenefitedAtSameLevel;
        @Column(name="dang_huong_luong_khi_di_hoc") Boolean salariedWhileStudying;
        @Column(name="cam_ket_khai_dung") Boolean declarationConfirmed;
    }

    @Entity(name="P06Document")
    @Table(name="mghp_giay_to_ho_so",schema="dbo")
    static class Document extends Row {
        @Column(name="phien_ban_ho_so_id",nullable=false) Long versionId;
        @Column(name="giay_to_bat_buoc_id",nullable=false) Long requirementId;
        @Column(name="ma_minh_chung",columnDefinition="nvarchar(128)") String code;
        @Column(name="so_giay_to",columnDefinition="nvarchar(128)") String documentNumber;
        @Column(name="don_vi_cap",columnDefinition="nvarchar(512)") String issuer;
        @Column(name="ngay_cap") LocalDate issuedOn;
        @Column(name="ngay_het_han") LocalDate expiresOn;
        @Column(name="sha256_noi_dung",length=64) String hash;
        @Column(name="nguoi_lien_quan_id") Long personId;
        @Column(name="nguoi_nop_id") Long actorId;
        @Column(name="thoi_diem_nop",columnDefinition="datetimeoffset(7)") OffsetDateTime submittedAt;
        @Column(name="thoi_diem_tao",columnDefinition="datetimeoffset(7)") OffsetDateTime createdAt;
    }

    @Entity(name="P06EvidenceLink")
    @Table(name="mghp_tep_minh_chung",schema="dbo")
    static class EvidenceLink extends Row {
        @Column(name="giay_to_ho_so_id",nullable=false) Long documentId;
        @Column(name="tep_dinh_kem_id",nullable=false) Long fileId;
    }

    @Entity(name="P06SemesterRequest")
    @Table(name="mghp_hoc_ky_de_nghi",schema="dbo")
    static class SemesterRequest extends Row {
        @Column(name="phien_ban_ho_so_id",nullable=false) Long versionId;
        @Column(name="hoc_ky_id",nullable=false) Long semesterId;
        @Column(name="phien_ban_chinh_sach_id",nullable=false) Long policyVersionId;
        @Column(name="loai_de_nghi",columnDefinition="nvarchar(128)") String requestType;
        @Column(name="ghi_chu",columnDefinition="nvarchar(max)") String note;
    }

    @Entity(name="P06RelatedPerson")
    @Table(name="mghp_nguoi_lien_quan",schema="dbo")
    static class RelatedPerson extends Row {
        @Column(name="phien_ban_ho_so_id",nullable=false) Long versionId;
        @Column(name="loai_quan_he",columnDefinition="nvarchar(128)") String relation;
        @Column(name="ho_ten",columnDefinition="nvarchar(512)") String fullName;
        @Column(name="so_cccd",columnDefinition="nvarchar(64)") String citizenId;
        @Column(name="dan_toc_id") Long ethnicityId;
        @Column(name="dia_chi_thuong_tru",columnDefinition="nvarchar(max)") String permanentAddress;
        @Column(name="ma_dia_ban",columnDefinition="nvarchar(128)") String localityCode;
        @Column(name="don_vi_cong_tac_id") Long workplaceUnitId;
        @Column(name="ten_don_vi_cong_tac",columnDefinition="nvarchar(512)") String workplaceName;
        @Column(name="nhom_don_vi_cong_tac",columnDefinition="nvarchar(128)") String workplaceGroup;
        @Column(name="tinh_trang_cong_tac",columnDefinition="nvarchar(128)") String employmentStatus;
        @Column(name="ngay_bat_dau_tinh_trang") LocalDate statusFrom;
        @Column(name="ngay_ket_thuc_tinh_trang") LocalDate statusUntil;
        @Column(name="ma_can_bo_doi_chieu",columnDefinition="nvarchar(128)") String matchingStaffCode;
    }

    @Entity(name="P06Comparison")
    @Table(name="mghp_doi_chieu_ke_khai",schema="dbo")
    static class Comparison extends Row {
        @Column(name="phien_ban_ho_so_id",nullable=false) Long versionId;
        @Column(name="ma_truong",nullable=false,columnDefinition="nvarchar(128)") String field;
        @Column(name="gia_tri_khai_bao",columnDefinition="nvarchar(max)") String claimed;
        @Column(name="gia_tri_nguon",columnDefinition="nvarchar(max)") String source;
        @Column(name="ket_qua_doi_chieu",columnDefinition="nvarchar(128)") String result;
        @Column(name="thoi_diem_lay_nguon",columnDefinition="datetimeoffset(7)") OffsetDateTime observedAt;
    }

    @Entity(name="P06ApplicationEvent")
    @Table(name="mghp_nhat_ky_ho_so",schema="dbo")
    static class ApplicationEvent extends Row {
        @Column(name="ho_so_id",nullable=false) Long applicationId;
        @Column(name="phien_ban_ho_so_id") Long versionId;
        @Column(name="nguoi_thao_tac_id") Long actorId;
        @Column(name="loai_chu_the_thao_tac",columnDefinition="nvarchar(128)") String actorType;
        @Column(name="hanh_dong",columnDefinition="nvarchar(128)") String action;
        @Column(name="trang_thai_cu",columnDefinition="nvarchar(128)") String previous;
        @Column(name="trang_thai_moi",columnDefinition="nvarchar(128)") String current;
        @Column(name="ly_do",columnDefinition="nvarchar(max)") String reason;
        @JdbcTypeCode(SqlTypes.UUID)
        @Column(name="ma_lien_ket_yeu_cau",columnDefinition="uniqueidentifier") UUID requestId;
        @Column(name="thoi_diem_tao",columnDefinition="datetimeoffset(7)") OffsetDateTime createdAt;
        @Column(name="yeu_cau_bo_sung_id") Long supplementId;
    }

    @Entity(name="P06Supplement")
    @Table(name="mghp_yeu_cau_bo_sung",schema="dbo")
    static class Supplement extends Row {
        @Column(name="ho_so_id",nullable=false) Long applicationId;
        @Column(name="phien_ban_lam_co_so_id",nullable=false) Long baseVersionId;
        @Column(name="so_vong",nullable=false) Integer number;
        @Column(name="trang_thai",columnDefinition="nvarchar(128)") String status;
        @Column(name="thoi_diem_dong",columnDefinition="datetimeoffset(7)") OffsetDateTime closedAt;
    }

    @Entity(name="P06Notification")
    @Table(name="thong_bao",schema="dbo")
    static class Notification extends Row {
        @Column(name="ma_phan_he",columnDefinition="nvarchar(128)") String module;
        @Column(name="tieu_de",columnDefinition="nvarchar(512)") String title;
        @Column(name="noi_dung",columnDefinition="nvarchar(max)") String content;
        @Column(name="nhom_phan_loai",columnDefinition="nvarchar(128)") String category;
        @Column(name="loai_doi_tuong",columnDefinition="nvarchar(128)") String objectType;
        @Column(name="doi_tuong_id",columnDefinition="nvarchar(128)") String objectId;
        @Column(name="url_thao_tac",columnDefinition="nvarchar(2048)") String actionUrl;
        @Column(name="han_xu_ly",columnDefinition="datetimeoffset(7)") OffsetDateTime deadline;
        @Column(name="la_quan_trong") Boolean important;
        @Column(name="trang_thai",columnDefinition="nvarchar(128)") String status;
        @Column(name="thoi_diem_cong_bo",columnDefinition="datetimeoffset(7)") OffsetDateTime publishedAt;
        @Column(name="nguoi_tao_id") Long createdBy;
        @Column(name="thoi_diem_tao",columnDefinition="datetimeoffset(7)") OffsetDateTime createdAt;
    }

    @Entity(name="P06Recipient")
    @Table(name="nguoi_nhan_thong_bao",schema="dbo")
    static class Recipient extends Row {
        @Column(name="thong_bao_id",nullable=false) Long notificationId;
        @Column(name="nguoi_dung_id",nullable=false) Long userId;
        @Column(name="thoi_diem_doc",columnDefinition="datetimeoffset(7)") OffsetDateTime readAt;
        @Column(name="thoi_diem_tao",columnDefinition="datetimeoffset(7)") OffsetDateTime createdAt;
    }
}
