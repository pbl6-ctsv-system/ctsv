-- PRE-P10-01. Additive: existing dates, histories and immutable versions remain untouched.
ALTER TABLE dbo.mghp_dot_xet ADD
    bo_sung_du_kien_tu datetimeoffset(7) NULL,
    bo_sung_du_kien_den datetimeoffset(7) NULL,
    ra_soat_du_kien_tu datetimeoffset(7) NULL,
    ngay_hop_du_kien date NULL,
    ky_du_kien_tu datetimeoffset(7) NULL,
    ky_du_kien_den datetimeoffset(7) NULL,
    cong_bo_du_kien_tu datetimeoffset(7) NULL,
    cong_bo_du_kien_den datetimeoffset(7) NULL,
    nguoi_dong_xet_id bigint NULL,
    thoi_diem_dong_xet datetimeoffset(7) NULL,
    sha256_nguon_dong_xet varchar(64) NULL;
GO
ALTER TABLE dbo.mghp_dot_xet ADD CONSTRAINT fk_tl_round_closed_by FOREIGN KEY(nguoi_dong_xet_id) REFERENCES dbo.nguoi_dung(id);
ALTER TABLE dbo.mghp_dot_xet ADD CONSTRAINT ck_tl_round_closed CHECK(
 (nguoi_dong_xet_id IS NULL AND thoi_diem_dong_xet IS NULL AND sha256_nguon_dong_xet IS NULL) OR
 (nguoi_dong_xet_id IS NOT NULL AND thoi_diem_dong_xet IS NOT NULL AND sha256_nguon_dong_xet IS NOT NULL AND LEN(sha256_nguon_dong_xet)=64));
ALTER TABLE dbo.mghp_dot_xet ADD CONSTRAINT ck_tl_schedule_order CHECK(
 (bo_sung_du_kien_tu IS NULL OR bo_sung_du_kien_den IS NULL OR bo_sung_du_kien_tu<=bo_sung_du_kien_den) AND
 (ky_du_kien_tu IS NULL OR ky_du_kien_den IS NULL OR ky_du_kien_tu<=ky_du_kien_den) AND
 (cong_bo_du_kien_tu IS NULL OR cong_bo_du_kien_den IS NULL OR cong_bo_du_kien_tu<=cong_bo_du_kien_den));
CREATE TABLE dbo.mghp_lich_su_lich_trinh (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_tl_history PRIMARY KEY,
    dot_xet_id bigint NOT NULL,
    lan_thay_doi int NOT NULL,
    loai_thay_doi nvarchar(64) NOT NULL,
    ly_do nvarchar(4000) NOT NULL,
    nguoi_thao_tac_id bigint NOT NULL,
    nhat_ky_thao_tac_id bigint NOT NULL,
    thoi_diem_ghi_nhan datetimeoffset(7) NOT NULL,
    thoi_diem_mo datetimeoffset(7) NULL,
    han_nop datetimeoffset(7) NULL,
    han_ra_soat datetimeoffset(7) NULL,
    bo_sung_du_kien_tu datetimeoffset(7) NULL,
    bo_sung_du_kien_den datetimeoffset(7) NULL,
    ra_soat_du_kien_tu datetimeoffset(7) NULL,
    ngay_hop_du_kien date NULL,
    ky_du_kien_tu datetimeoffset(7) NULL,
    ky_du_kien_den datetimeoffset(7) NULL,
    cong_bo_du_kien_tu datetimeoffset(7) NULL,
    cong_bo_du_kien_den datetimeoffset(7) NULL,
    nguoi_dong_xet_id bigint NULL,
    thoi_diem_dong_xet datetimeoffset(7) NULL,
    sha256_nguon_dong_xet varchar(64) NULL,
    CONSTRAINT uq_tl_history_revision UNIQUE(dot_xet_id,lan_thay_doi),
    CONSTRAINT fk_tl_history_round FOREIGN KEY(dot_xet_id) REFERENCES dbo.mghp_dot_xet(id),
    CONSTRAINT fk_tl_history_actor FOREIGN KEY(nguoi_thao_tac_id) REFERENCES dbo.nguoi_dung(id),
    CONSTRAINT fk_tl_history_audit FOREIGN KEY(nhat_ky_thao_tac_id) REFERENCES dbo.nhat_ky_thao_tac(id),
    CONSTRAINT fk_tl_history_closer FOREIGN KEY(nguoi_dong_xet_id) REFERENCES dbo.nguoi_dung(id),
    CONSTRAINT ck_tl_history_revision CHECK(lan_thay_doi>=0)
);
GO
CREATE TRIGGER dbo.tr_tl_history_immutable ON dbo.mghp_lich_su_lich_trinh AFTER UPDATE,DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM deleted) THROW 51917,N'Timeline history is append-only.',1;
END;
GO
CREATE TRIGGER dbo.tr_tl_review_marker_immutable ON dbo.mghp_dot_xet AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM deleted d JOIN inserted i ON i.id=d.id
       WHERE d.thoi_diem_dong_xet IS NOT NULL AND EXISTS(
        SELECT i.nguoi_dong_xet_id,i.thoi_diem_dong_xet,i.sha256_nguon_dong_xet
        EXCEPT SELECT d.nguoi_dong_xet_id,d.thoi_diem_dong_xet,d.sha256_nguon_dong_xet))
      THROW 51918,N'Review closure is immutable; ordinary writes cannot reopen it.',1;
END;
