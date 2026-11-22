package pbl6.ctsv.mghp.application;

import jakarta.persistence.*;
import java.time.*;
import java.math.BigDecimal;
import static pbl6.ctsv.mghp.application.ApplicationEntities.Row;

/** Scalar FK writes; decision snapshots never escape as API entities. */
final class DecisionEntities {
 private DecisionEntities(){}
 @Entity(name="P10Root") @Table(name="mghp_quyet_dinh",schema="dbo")
 @org.hibernate.annotations.DynamicUpdate
 static class Root extends Row {
  @Column(name="dot_xet_id") Long roundId;
  @Column(name="ma_qd",columnDefinition="nvarchar(128)") String code;
  @Column(name="loai_qd",columnDefinition="nvarchar(128)") String type;
  @Column(name="don_vi_ban_hanh_id") Long unitId;
  @Column(name="so_qd",columnDefinition="nvarchar(128)") String number;
  @Column(name="ngay_qd") LocalDate date;
  @Column(name="nguoi_tao_id") Long actor;
  @Column(name="thoi_diem_tao") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime createdAt;
  @Column(name="phien_ban_ky_thuat",insertable=false,updatable=false,columnDefinition="rowversion") @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY) byte[] fence;
 }
 @Entity(name="P10Version") @Table(name="mghp_phien_ban_qd",schema="dbo")
 @org.hibernate.annotations.DynamicUpdate
 static class Version extends Row {
  @Column(name="quyet_dinh_id") Long rootId;
  @Column(name="lan_sua_doi") Integer revision;
  @Column(name="trang_thai",columnDefinition="nvarchar(128)") String status;
  @Column(name="giai_doan",columnDefinition="nvarchar(128)") String stage;
  @Column(name="phien_ban_truoc_id") Long previousId;
  @Column(name="ly_do_tao_phien_ban",columnDefinition="nvarchar(max)") String reason;
  @Column(name="nguoi_tao_id") Long actor;
  @Column(name="thoi_diem_tao") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime createdAt;
  @Column(name="nguoi_chot_id") Long lockedBy;
  @Column(name="thoi_diem_chot") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime lockedAt;
  @Column(name="sha256_tap_du_lieu",columnDefinition="varchar(64)") String hash;
  @Column(name="so_dong") Integer lineCount;
  @Column(name="ma_qd_luc_chot",columnDefinition="nvarchar(128)") String code;
  @Column(name="so_qd_luc_chot",columnDefinition="nvarchar(128)") String number;
  @Column(name="ngay_qd_luc_chot") LocalDate date;
  @Column(name="don_vi_ban_hanh_luc_chot_id") Long unitId;
  @Column(name="sha256_nguon",columnDefinition="varchar(64)") String sourceHash;
  @Column(name="thoi_diem_dong_xet_nguon") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime reviewClosedAt;
  @Column(name="sha256_lenh_hop",columnDefinition="varchar(64)") String meetingHash;
  @Column(name="phien_ban_ky_thuat",insertable=false,updatable=false,columnDefinition="rowversion") @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY) byte[] fence;
 }
 @Entity(name="P10Minutes") @Table(name="mghp_bien_ban",schema="dbo")
 @org.hibernate.annotations.DynamicUpdate
 static class Minutes extends Row {
  @Column(name="dot_xet_id") Long roundId;
  @Column(name="ma_bb",columnDefinition="nvarchar(128)") String code;
  @Column(name="so_tham_chieu",columnDefinition="nvarchar(512)") String reference;
  @Column(name="thoi_diem_hop") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime meetingAt;
  @Column(name="dia_diem",columnDefinition="nvarchar(512)") String place;
  @Column(name="trang_thai",columnDefinition="nvarchar(128)") String status;
  @Column(name="nguoi_tao_id") Long actor;
  @Column(name="thoi_diem_tao") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime createdAt;
  @Column(name="phien_ban_qd_nguon_id") Long versionId;
  @Column(name="sha256_ds_hop",columnDefinition="varchar(64)") String hash;
  @Column(name="phien_ban_ky_thuat",insertable=false,updatable=false,columnDefinition="rowversion") @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY) byte[] fence;
 }
 @Entity(name="P10Candidate") @Table(name="mghp_ung_vien_phien_ban_qd",schema="dbo")
 @org.hibernate.annotations.DynamicUpdate
 static class Candidate extends Row {
  @Column(name="phien_ban_qd_id") Long versionId;
  @Column(name="danh_gia_dieu_kien_id") Long evaluationId;
  @Column(name="qua_trinh_hoc_id") Long processId;
  @Column(name="hoc_ky_id") Long semesterId;
  @Column(name="ma_pham_vi",columnDefinition="nvarchar(128)") String scope;
  @Column(name="ty_le_giam_luc_chot",precision=12,scale=9) BigDecimal rate;
  @Column(name="ngay_du_dieu_kien_tu") LocalDate from;
  @Column(name="ngay_du_dieu_kien_den") LocalDate to;
  @Column(name="trang_thai_lua_chon",columnDefinition="nvarchar(128)") String selection;
  @Column(name="ma_ly_do",columnDefinition="nvarchar(2000)") String reasonCode;
  @Column(name="ly_do",columnDefinition="nvarchar(max)") String reason;
  @Column(name="khoang_du_dieu_kien_id") Long periodId;
 }
 @Entity(name="P10Line") @Table(name="mghp_dong_qd",schema="dbo")
 @org.hibernate.annotations.DynamicUpdate
 static class Line extends Row {
  @Column(name="phien_ban_qd_id") Long versionId;
  @Column(name="sinh_vien_id") Long studentId;
  @Column(name="qua_trinh_hoc_id") Long processId;
  @Column(name="hoc_ky_ap_dung_id") Long semesterId;
  @Column(name="phien_ban_chinh_sach_id") Long policyVersionId;
  @Column(name="quyen_huong_nguon_id") Long benefitId;
  @Column(name="danh_gia_dieu_kien_id") Long evaluationId;
  @Column(name="ma_pham_vi",columnDefinition="nvarchar(128)") String scope;
  @Column(name="ty_le_giam",precision=12,scale=9) BigDecimal rate;
  @Column(name="loai_thao_tac",columnDefinition="nvarchar(128)") String operation;
  @Column(name="sha256_dong",columnDefinition="varchar(64)") String hash;
  @Column(name="phien_ban_ho_so_nguon_id") Long submissionId;
  @Column(name="ung_vien_id") Long candidateId;
  @Column(name="mssv_luc_chot",columnDefinition="nvarchar(64)") String studentCode;
  @Column(name="ho_ten_luc_chot",columnDefinition="nvarchar(512)") String fullName;
  @Column(name="ma_lop_luc_chot",columnDefinition="nvarchar(128)") String classCode;
  @Column(name="ten_chinh_sach_luc_chot",columnDefinition="nvarchar(512)") String policyName;
  @Column(name="hoc_ky_bat_dau_huong_id") Long startSemesterId;
  @Column(name="hoc_ky_ket_thuc_huong_id") Long endSemesterId;
  @Column(name="ngay_huong_tu") LocalDate from;
  @Column(name="ngay_huong_den") LocalDate to;
  @Column(name="pham_vi_thoi_gian_huong",columnDefinition="nvarchar(128)") String timeScope;
 }
 @Entity(name="P10GrantedPeriod") @Table(name="mghp_khoang_huong",schema="dbo")
 @org.hibernate.annotations.DynamicUpdate
 static class GrantedPeriod extends Row {
  @Column(name="dong_qd_id") Long lineId;
  @Column(name="ung_vien_id") Long candidateId;
  @Column(name="ngay_bat_dau") LocalDate from;
  @Column(name="ngay_ket_thuc") LocalDate to;
  @Column(name="ty_le_giam",precision=12,scale=9) BigDecimal rate;
 }
}

