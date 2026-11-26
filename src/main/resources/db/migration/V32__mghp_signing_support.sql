-- Additive signing support. Does not alter attempts, receipts, artifacts or existing account grants.
CREATE TABLE dbo.mghp_su_co_ky (
 id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_mghp_su_co_ky PRIMARY KEY,
 lan_goi_id bigint NOT NULL CONSTRAINT fk_mghp_support_attempt REFERENCES dbo.lan_goi_dich_vu_ky(id),
 su_kien_loi_id uniqueidentifier NOT NULL,
 du_lieu_loi nvarchar(max) NOT NULL,
 trang_thai nvarchar(32) NOT NULL,
 thoi_diem_phat_sinh datetimeoffset(7) NOT NULL,
 thoi_diem_cap_nhat datetimeoffset(7) NOT NULL,
 nguoi_bat_dau_id bigint NULL CONSTRAINT fk_mghp_support_started REFERENCES dbo.nguoi_dung(id),
 thoi_diem_bat_dau datetimeoffset(7) NULL,
 nguoi_ket_thuc_id bigint NULL CONSTRAINT fk_mghp_support_closed REFERENCES dbo.nguoi_dung(id),
 thoi_diem_ket_thuc datetimeoffset(7) NULL,
 phien_ban_ky_thuat rowversion NOT NULL,
 CONSTRAINT uq_mghp_support_attempt UNIQUE(lan_goi_id),
 CONSTRAINT ck_mghp_support_json CHECK(ISJSON(du_lieu_loi)=1),
 CONSTRAINT ck_mghp_support_status CHECK(trang_thai IN(N'waiting',N'working',N'resolved')),
 CONSTRAINT ck_mghp_support_times CHECK((trang_thai=N'waiting' AND thoi_diem_bat_dau IS NULL AND thoi_diem_ket_thuc IS NULL) OR (trang_thai=N'working' AND thoi_diem_bat_dau IS NOT NULL AND thoi_diem_ket_thuc IS NULL) OR (trang_thai=N'resolved' AND thoi_diem_bat_dau IS NOT NULL AND thoi_diem_ket_thuc IS NOT NULL))
);
CREATE INDEX ix_mghp_support_status ON dbo.mghp_su_co_ky(trang_thai,id DESC);
CREATE TABLE dbo.mghp_su_co_ky_lich_su (
 id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_mghp_support_history PRIMARY KEY,
 su_co_id bigint NOT NULL CONSTRAINT fk_mghp_support_history REFERENCES dbo.mghp_su_co_ky(id),
 su_kien_id uniqueidentifier NOT NULL CONSTRAINT uq_mghp_support_event UNIQUE,
 loai nvarchar(32) NOT NULL,
 noi_dung nvarchar(2000) NOT NULL,
 tham_chieu nvarchar(200) NULL,
 du_lieu_loi nvarchar(max) NULL,
 nguoi_thao_tac_id bigint NULL CONSTRAINT fk_mghp_support_history_actor REFERENCES dbo.nguoi_dung(id),
 thoi_diem datetimeoffset(7) NOT NULL,
 CONSTRAINT ck_mghp_support_history_json CHECK(du_lieu_loi IS NULL OR ISJSON(du_lieu_loi)=1)
);
CREATE INDEX ix_mghp_support_history ON dbo.mghp_su_co_ky_lich_su(su_co_id,id);
CREATE TABLE dbo.mghp_theo_doi_chung_thu (
 id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_mghp_certificate_monitor PRIMARY KEY,
 email nvarchar(254) COLLATE Latin1_General_100_BIN2 NOT NULL CONSTRAINT uq_mghp_certificate_email UNIQUE,
 quan_sat nvarchar(max) NULL,
 trang_thai nvarchar(32) NOT NULL,
 lan_kiem_tra datetimeoffset(7) NULL,
 lan_doc_thanh_cong datetimeoffset(7) NULL,
 ma_loi nvarchar(128) NULL,
 phien_ban_ky_thuat rowversion NOT NULL,
 CONSTRAINT ck_mghp_certificate_observation CHECK(quan_sat IS NULL OR ISJSON(quan_sat)=1),
 CONSTRAINT ck_mghp_certificate_state CHECK(trang_thai IN(N'valid',N'soon',N'expired',N'not_found',N'not_ready',N'ambiguous',N'unknown'))
);
CREATE TABLE dbo.mghp_chung_thu_lich_su (
 id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_mghp_certificate_history PRIMARY KEY,
 theo_doi_id bigint NOT NULL CONSTRAINT fk_mghp_certificate_history REFERENCES dbo.mghp_theo_doi_chung_thu(id),
 loai nvarchar(32) NOT NULL,
 noi_dung nvarchar(2000) NOT NULL,
 tham_chieu nvarchar(200) NULL,
 quan_sat nvarchar(max) NULL,
 nguoi_thao_tac_id bigint NULL CONSTRAINT fk_mghp_certificate_history_actor REFERENCES dbo.nguoi_dung(id),
 thoi_diem datetimeoffset(7) NOT NULL,
 CONSTRAINT ck_mghp_certificate_history_json CHECK(quan_sat IS NULL OR ISJSON(quan_sat)=1)
);
CREATE INDEX ix_mghp_certificate_history ON dbo.mghp_chung_thu_lich_su(theo_doi_id,id);
CREATE TABLE dbo.mghp_yeu_cau_gia_han (
 id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_mghp_certificate_renewal PRIMARY KEY,
 theo_doi_id bigint NOT NULL CONSTRAINT fk_mghp_certificate_renewal REFERENCES dbo.mghp_theo_doi_chung_thu(id),
 ma_thao_tac uniqueidentifier NOT NULL CONSTRAINT uq_mghp_renewal_operation UNIQUE,
 ma_yeu_cau_ca uniqueidentifier NULL,
 chung_thu_cu uniqueidentifier NULL,
 trang_thai nvarchar(32) NOT NULL,
 dang_mo bit NOT NULL,
 ly_do nvarchar(1000) NOT NULL,
 phan_hoi_ca nvarchar(2000) NULL,
 nguoi_yeu_cau_id bigint NOT NULL CONSTRAINT fk_mghp_renewal_actor REFERENCES dbo.nguoi_dung(id),
 thoi_diem_yeu_cau datetimeoffset(7) NOT NULL,
 thoi_diem_kiem_tra datetimeoffset(7) NULL,
 ma_loi nvarchar(128) NULL,
 phien_ban_ky_thuat rowversion NOT NULL,
 CONSTRAINT ck_mghp_renewal_state CHECK(trang_thai IN(N'intent',N'pending',N'approved',N'rejected',N'unknown',N'failed')),
 CONSTRAINT ck_mghp_renewal_active CHECK((dang_mo=1 AND trang_thai IN(N'intent',N'pending',N'unknown')) OR (dang_mo=0 AND trang_thai IN(N'approved',N'rejected',N'failed')))
);
CREATE UNIQUE INDEX uq_mghp_renewal_open ON dbo.mghp_yeu_cau_gia_han(theo_doi_id) WHERE dang_mo=1;
IF NOT EXISTS(SELECT 1 FROM dbo.vai_tro WHERE ma=N'mghp_quan_tri_ky')
 INSERT dbo.vai_tro(ma,ten,dang_hoat_dong) VALUES(N'mghp_quan_tri_ky',N'Quản trị chứng thư và sự cố ký số MGHP',1);
GO
CREATE TRIGGER dbo.tr_mghp_support_history_immutable ON dbo.mghp_su_co_ky_lich_su AFTER UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 51332,N'Signing support history is append-only.',1;
END;
GO
CREATE TRIGGER dbo.tr_mghp_certificate_history_immutable ON dbo.mghp_chung_thu_lich_su AFTER UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted) THROW 51333,N'Certificate observation history is append-only.',1;
END;
