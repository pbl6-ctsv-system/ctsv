package pbl6.ctsv.mghp.application;

import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
import static pbl6.ctsv.mghp.application.ApplicationEntities.Row;

final class EvaluationEntities {
 private EvaluationEntities(){}
 @Entity(name="P08Evaluation") @Table(name="mghp_danh_gia_dieu_kien",schema="dbo")
 static class Evaluation extends Row {
  @Column(name="sinh_vien_id",nullable=false) Long studentId;
  @Column(name="qua_trinh_hoc_id",nullable=false) Long processId;
  @Column(name="hoc_ky_ap_dung_id",nullable=false) Long semesterId;
  @Column(name="phien_ban_chinh_sach_id",nullable=false) Long policyVersionId;
  @Column(name="ho_so_nguon_id") Long applicationId;
  @Column(name="phien_ban_ho_so_nguon_id") Long submissionId;
  @Column(name="quyen_huong_nguon_id") Long benefitId;
  @Column(name="phien_ban_cap_nhat_minh_chung_id") Long updateSubmissionId;
  @Column(name="dot_xet_id") Long roundId;
  @Column(name="ma_pham_vi",nullable=false,columnDefinition="nvarchar(128)") String scopeCode;
  @Column(name="trang_thai_xu_ly",columnDefinition="nvarchar(128)") String status;
  @Column(name="ket_qua",columnDefinition="nvarchar(128)") String result;
  @Column(name="ly_do_ket_luan",columnDefinition="nvarchar(max)") String reason;
  @Column(name="can_cu_xet_thoi_gian",columnDefinition="nvarchar(max)") String timeEvidence;
  @Column(name="so_phien_ban_danh_gia",nullable=false) Integer revision;
  @Column(name="la_ban_hien_hanh") Boolean current;
  @Column(name="danh_gia_truoc_id") Long previousId;
  @Column(name="ly_do_danh_gia_lai",columnDefinition="nvarchar(max)") String revisionReason;
  @Column(name="ngay_tinh_tuoi") LocalDate ageReferenceDate;
  @Column(name="ngay_sinh_dung_de_xet") LocalDate birthDateUsed;
  @Column(name="lich_su_trang_thai_sv_id") Long statusHistoryId;
  @Column(name="nguoi_danh_gia_id") Long assessedBy;
  @Column(name="thoi_diem_danh_gia") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime assessedAt;
  @Column(name="du_lieu_nguon_tinh_den") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime observedAt;
  @Column(name="phien_ban_quy_tac",columnDefinition="nvarchar(128)") String ruleVersion;
  @Column(name="phien_ban_ky_thuat",insertable=false,updatable=false,columnDefinition="rowversion") @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY) byte[] fence;
 }
 @Entity(name="P08Criterion") @Table(name="mghp_ket_qua_tieu_chi",schema="dbo")
 static class Criterion extends Row {
  @Column(name="danh_gia_dieu_kien_id",nullable=false) Long evaluationId;
  @Column(name="tieu_chi_chinh_sach_id",nullable=false) Long criterionId;
  @Column(name="ket_qua",columnDefinition="nvarchar(128)") String result;
  @Column(name="ma_ly_do",columnDefinition="nvarchar(2000)") String reasonCode;
  @Column(name="nhan_xet",columnDefinition="nvarchar(max)") String note;
  @Column(name="nguoi_danh_gia_id") Long actor;
  @Column(name="thoi_diem_danh_gia") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime at;
 }
 @Entity(name="P08Evidence") @Table(name="mghp_can_cu_danh_gia",schema="dbo")
 static class Evidence extends Row {
  @Column(name="danh_gia_dieu_kien_id",nullable=false) Long evaluationId;
  @Column(name="giay_to_ho_so_id",nullable=false) Long documentId;
  @Column(name="ket_qua_tieu_chi_id") Long criterionResultId;
  @Column(name="ngay_ap_dung_tu") LocalDate from;
  @Column(name="ngay_ap_dung_den") LocalDate to;
  @Column(name="ket_qua_su_dung",columnDefinition="nvarchar(128)") String result;
  @Column(name="ly_do",columnDefinition="nvarchar(max)") String reason;
  @Column(name="nguoi_xac_nhan_id") Long actor;
  @Column(name="thoi_diem_xac_nhan") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime at;
 }
 @Entity(name="P08Period") @Table(name="mghp_khoang_du_dieu_kien",schema="dbo")
 static class Period extends Row {
  @Column(name="danh_gia_dieu_kien_id",nullable=false) Long evaluationId;
  @Column(name="ngay_bat_dau",nullable=false) LocalDate from;
  @Column(name="ngay_ket_thuc",nullable=false) LocalDate to;
  @Column(name="can_cu_thoi_gian",columnDefinition="nvarchar(max)") String evidence;
  @Column(name="nguoi_xac_nhan_id") Long actor;
  @Column(name="thoi_diem_xac_nhan") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime at;
 }
 @Entity(name="P08Benefit") @Table(name="mghp_quyen_huong",schema="dbo")
 static class Benefit extends Row {
  @Column(name="ma_quyen_huong",nullable=false,columnDefinition="nvarchar(128)") String code;
  @Column(name="sinh_vien_id",nullable=false) Long studentId;
  @Column(name="qua_trinh_hoc_id",nullable=false) Long processId;
  @Column(name="chinh_sach_id",nullable=false) Long policyId;
  @Column(name="phien_ban_ho_so_nguon_id") Long submissionId;
  @Column(name="phien_ban_chinh_sach_id") Long policyVersionId;
  @Column(name="dong_qd_cong_nhan_id") Long decisionLineId;
  @Column(name="ma_pham_vi",nullable=false,columnDefinition="nvarchar(128)") String scopeCode;
  @Column(name="trang_thai",columnDefinition="nvarchar(128)") String status;
  @Column(name="ty_le_giam",precision=12,scale=9) BigDecimal rate;
  @Column(name="ngay_cong_nhan_tu") LocalDate from;
  @Column(name="ngay_cong_nhan_den") LocalDate to;
  @Column(name="ngay_hieu_luc_thu_hoi") LocalDate revokedOn;
  @Column(name="thoi_diem_ra_soat_tiep") LocalDate nextReviewOn;
  @Column(name="ly_do_can_ra_soat",columnDefinition="nvarchar(max)") String reviewReason;
  @Column(name="che_do_cap_nhat_minh_chung",columnDefinition="nvarchar(128)") String updateMode;
  @Column(name="nhom_don_vi_cong_tac",columnDefinition="nvarchar(128)") String employmentUnitGroup;
  @Column(name="tinh_trang_xac_nhan_chu_ky",columnDefinition="nvarchar(128)") String cycleStatus;
  @Column(name="thoi_diem_cap_nhat") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime updatedAt;
  @Column(name="phien_ban_ky_thuat",insertable=false,updatable=false,columnDefinition="rowversion") @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY) byte[] fence;
 }
 @Entity(name="P08BenefitEvent") @Table(name="mghp_bien_dong_quyen_huong",schema="dbo")
 static class BenefitEvent extends Row {
  @Column(name="quyen_huong_id",nullable=false) Long benefitId;
  @Column(name="loai_su_kien",columnDefinition="nvarchar(128)") String type;
  @Column(name="ngay_hieu_luc") LocalDate effectiveOn;
  @Column(name="dong_qd_id") Long decisionLineId;
  @Column(name="ly_do",columnDefinition="nvarchar(max)") String reason;
  @Column(name="nguoi_ghi_nhan_id") Long actor;
  @Column(name="thoi_diem_ghi_nhan") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime at;
  @Column(name="so_phien_ban",nullable=false) Integer revision;
  @Column(name="bien_dong_truoc_id") Long previousId;
  @Column(name="ngay_ra_soat_tiep_sau") LocalDate nextReviewOn;
  @Column(name="che_do_cap_nhat_sau",columnDefinition="nvarchar(128)") String updateMode;
 }
}
