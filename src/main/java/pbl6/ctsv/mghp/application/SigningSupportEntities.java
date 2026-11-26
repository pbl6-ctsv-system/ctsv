package pbl6.ctsv.mghp.application;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import static pbl6.ctsv.mghp.application.ApplicationEntities.Row;

/** Typed scalar FK mappings. No file bytes or credential material. */
final class SigningSupportEntities {
 private SigningSupportEntities(){}
 @MappedSuperclass static class Fenced extends Row {
  @Column(name="phien_ban_ky_thuat",insertable=false,updatable=false,columnDefinition="rowversion") @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.BINARY) byte[] fence;
 }
 @Entity(name="SigningIncident") @Table(name="mghp_su_co_ky",schema="dbo") @org.hibernate.annotations.DynamicUpdate
 static class Incident extends Fenced {
  @Column(name="lan_goi_id") Long attempt;
  @Column(name="su_kien_loi_id",columnDefinition="uniqueidentifier") UUID event;
  @Column(name="du_lieu_loi",columnDefinition="nvarchar(max)") String snapshot;
  @Column(name="trang_thai",columnDefinition="nvarchar(32)") String state;
  @Column(name="thoi_diem_phat_sinh") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime occurredAt;
  @Column(name="thoi_diem_cap_nhat") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime updatedAt;
  @Column(name="nguoi_bat_dau_id") Long startedBy;
  @Column(name="thoi_diem_bat_dau") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime startedAt;
  @Column(name="nguoi_ket_thuc_id") Long closedBy;
  @Column(name="thoi_diem_ket_thuc") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime closedAt;
 }
 @Entity(name="SigningIncidentHistory") @Table(name="mghp_su_co_ky_lich_su",schema="dbo")
 static class IncidentHistory extends Row {
  @Column(name="su_co_id") Long incident;
  @Column(name="su_kien_id",columnDefinition="uniqueidentifier") UUID event;
  @Column(name="loai",columnDefinition="nvarchar(32)") String kind;
  @Column(name="noi_dung",columnDefinition="nvarchar(2000)") String text;
  @Column(name="tham_chieu",columnDefinition="nvarchar(200)") String reference;
  @Column(name="du_lieu_loi",columnDefinition="nvarchar(max)") String snapshot;
  @Column(name="nguoi_thao_tac_id") Long actor;
  @Column(name="thoi_diem") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime at;
 }
 @Entity(name="SigningCertificate") @Table(name="mghp_theo_doi_chung_thu",schema="dbo") @org.hibernate.annotations.DynamicUpdate
 static class CertificateMonitor extends Fenced {
  @Column(name="email",columnDefinition="nvarchar(254)") String email;
  @Column(name="quan_sat",columnDefinition="nvarchar(max)") String observation;
  @Column(name="trang_thai",columnDefinition="nvarchar(32)") String state;
  @Column(name="lan_kiem_tra") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime checkedAt;
  @Column(name="lan_doc_thanh_cong") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime successfulAt;
  @Column(name="ma_loi",columnDefinition="nvarchar(128)") String error;
 }
 @Entity(name="SigningCertificateHistory") @Table(name="mghp_chung_thu_lich_su",schema="dbo")
 static class CertificateHistory extends Row {
  @Column(name="theo_doi_id") Long certificate;
  @Column(name="loai",columnDefinition="nvarchar(32)") String kind;
  @Column(name="noi_dung",columnDefinition="nvarchar(2000)") String text;
  @Column(name="tham_chieu",columnDefinition="nvarchar(200)") String reference;
  @Column(name="quan_sat",columnDefinition="nvarchar(max)") String observation;
  @Column(name="nguoi_thao_tac_id") Long actor;
  @Column(name="thoi_diem") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime at;
 }
 @Entity(name="SigningRenewal") @Table(name="mghp_yeu_cau_gia_han",schema="dbo") @org.hibernate.annotations.DynamicUpdate
 static class Renewal extends Fenced {
  @Column(name="theo_doi_id") Long certificate;
  @Column(name="ma_thao_tac",columnDefinition="uniqueidentifier") UUID operation;
  @Column(name="ma_yeu_cau_ca",columnDefinition="uniqueidentifier") UUID providerRequest;
  @Column(name="chung_thu_cu",columnDefinition="uniqueidentifier") UUID previousCertificate;
  @Column(name="trang_thai",columnDefinition="nvarchar(32)") String state;
  @Column(name="dang_mo") Boolean open;
  @Column(name="ly_do",columnDefinition="nvarchar(1000)") String reason;
  @Column(name="phan_hoi_ca",columnDefinition="nvarchar(2000)") String comment;
  @Column(name="nguoi_yeu_cau_id") Long actor;
  @Column(name="thoi_diem_yeu_cau") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime at;
  @Column(name="thoi_diem_kiem_tra") @org.hibernate.annotations.TimeZoneStorage(org.hibernate.annotations.TimeZoneStorageType.NATIVE) OffsetDateTime checkedAt;
  @Column(name="ma_loi",columnDefinition="nvarchar(128)") String error;
 }
}
