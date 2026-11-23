package pbl6.ctsv.mghp.application;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import static pbl6.ctsv.mghp.application.ApplicationEntities.Row;

/** P11 scalar FK writes. All binary content stays in private storage. */
final class SigningEntities {
 private SigningEntities(){}
 @Entity(name="P11Document") @Table(name="van_ban",schema="dbo")
 static class Document extends Row {
  @Column(name="ma_van_ban",columnDefinition="uniqueidentifier") UUID code;
  @Column(name="ma_phan_he",columnDefinition="nvarchar(128)") String module;
  @Column(name="loai_van_ban",columnDefinition="nvarchar(128)") String type;
  @Column(name="tieu_de",columnDefinition="nvarchar(512)") String title;
  @Column(name="nguoi_tao_id") Long actor;
  @Column(name="thoi_diem_tao") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime createdAt;
 }
 @Entity(name="P11DocumentVersion") @Table(name="phien_ban_van_ban",schema="dbo")
 static class DocumentVersion extends Row {
  @Column(name="van_ban_id") Long documentId;
  @Column(name="so_phien_ban") Integer revision;
  @Column(name="tieu_de_tai_thoi_diem_chot",columnDefinition="nvarchar(512)") String title;
  @Column(name="tep_goc_id") Long fileId;
  @Column(name="ma_tham_chieu_nghiep_vu",columnDefinition="nvarchar(512)") String businessReference;
  @Column(name="lan_sua_doi_nghiep_vu") Integer businessRevision;
  @Column(name="sha256_tap_du_lieu",columnDefinition="varchar(64)") String dataset;
  @Column(name="trang_thai_noi_dung",columnDefinition="nvarchar(2000)") String state;
  @Column(name="nguoi_chot_id") Long lockedBy;
  @Column(name="thoi_diem_chot") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime lockedAt;
  @Column(name="nguoi_tao_id") Long actor;
  @Column(name="thoi_diem_tao") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime createdAt;
 }
 @Entity(name="P11Link") @Table(name="mghp_lien_ket_phien_ban_van_ban",schema="dbo")
 @org.hibernate.annotations.DynamicUpdate
 static class Link extends Row {
  @Column(name="loai_van_ban",columnDefinition="nvarchar(128)") String type;
  @Column(name="bien_ban_id") Long minutesId;
  @Column(name="phien_ban_qd_id") Long decisionVersionId;
  @Column(name="phien_ban_van_ban_dung_chung_id") Long versionId;
  @Column(name="trang_thai",columnDefinition="nvarchar(128)") String state;
  @Column(name="thoi_diem_tao") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime createdAt;
 }
 @Entity(name="P11Request") @Table(name="yeu_cau_ky",schema="dbo")
 @org.hibernate.annotations.DynamicUpdate
 static class Request extends Row {
  @Column(name="ma_yeu_cau",columnDefinition="uniqueidentifier") UUID code;
  @Column(name="phien_ban_van_ban_id") Long versionId;
  @Column(name="he_thong_cung_cap_id") Long providerId;
  @Column(name="che_do_thuc_hien",columnDefinition="nvarchar(128)") String mode;
  @Column(name="quy_tac_uy_quyen",columnDefinition="nvarchar(128)") String authority;
  @Column(name="van_ban_ben_ngoai_id",columnDefinition="uniqueidentifier") UUID externalId;
  @Column(name="danh_muc_ca_id",columnDefinition="uniqueidentifier") UUID category;
  @Column(name="trang_thai",columnDefinition="nvarchar(128)") String state;
  @Column(name="nguoi_yeu_cau_id") Long actor;
  @Column(name="thoi_diem_yeu_cau") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime createdAt;
  @Column(name="thoi_diem_bat_dau") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime startedAt;
  @Column(name="thoi_diem_hoan_tat") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime completedAt;
  @Column(name="ma_loi_gan_nhat",columnDefinition="nvarchar(128)") String error;
  @Column(name="phien_ban_adapter",columnDefinition="nvarchar(128)") String adapter;
  @Column(name="phien_ban_ky_thuat",insertable=false,updatable=false,columnDefinition="rowversion") @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY) byte[] fence;
 }
 @Entity(name="P11Step") @Table(name="buoc_ky",schema="dbo")
 @org.hibernate.annotations.DynamicUpdate
 static class Step extends Row {
  @Column(name="yeu_cau_ky_id") Long requestId;
  @Column(name="nguoi_dung_noi_bo_id") Long userId;
  @Column(name="thu_tu_ky") Integer order;
  @Column(name="vai_tro_nguoi_ky",columnDefinition="nvarchar(128)") String role;
  @Column(name="ten_nguoi_ky",columnDefinition="nvarchar(512)") String name;
  @Column(name="email_nguoi_ky",columnDefinition="nvarchar(254)") String email;
  @Column(name="nguoi_dung_ca_id",columnDefinition="uniqueidentifier") UUID caUser;
  @Column(name="chung_thu_id",columnDefinition="uniqueidentifier") UUID certificate;
  @Column(name="so_seri_chung_thu",columnDefinition="nvarchar(128)") String serial;
  @Column(name="so_trang") Integer page;
  @Column(name="toa_do_x",precision=28,scale=10) java.math.BigDecimal x;
  @Column(name="toa_do_y",precision=28,scale=10) java.math.BigDecimal y;
  @Column(name="chieu_rong",precision=28,scale=10) java.math.BigDecimal width;
  @Column(name="chieu_cao",precision=28,scale=10) java.math.BigDecimal height;
  @Column(name="trang_thai",columnDefinition="nvarchar(128)") String state;
  @Column(name="ly_do_tu_choi",columnDefinition="nvarchar(max)") String reason;
  @Column(name="thoi_diem_tao") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime createdAt;
  @Column(name="thoi_diem_cap_nhat") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime updatedAt;
 }
}
