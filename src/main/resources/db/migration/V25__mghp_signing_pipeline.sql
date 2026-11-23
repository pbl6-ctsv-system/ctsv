-- P11 only. Existing business snapshots/bytes remain untouched; no account is granted a role.
ALTER TABLE dbo.yeu_cau_ky ADD phien_ban_ky_thuat rowversion;
ALTER TABLE dbo.lan_goi_dich_vu_ky ADD
 giai_doan_xu_ly nvarchar(64) NULL,
 lease_token uniqueidentifier NULL,
 lease_den datetimeoffset(7) NULL,
 nha_phat_hanh_auth nvarchar(512) NULL,
 chu_the_auth uniqueidentifier NULL,
 phien_ban_auth bigint NULL,
 tep_dau_ra_id bigint NULL,
 luot_luu_tep_id uniqueidentifier NULL;
ALTER TABLE dbo.lan_goi_dich_vu_ky ADD CONSTRAINT fk_p11_attempt_output FOREIGN KEY(tep_dau_ra_id) REFERENCES dbo.tep_dinh_kem(id);
ALTER TABLE dbo.lan_goi_dich_vu_ky ADD CONSTRAINT fk_p11_attempt_upload FOREIGN KEY(luot_luu_tep_id) REFERENCES dbo.luot_tai_tep(id);
CREATE UNIQUE INDEX uq_p11_request_document ON dbo.yeu_cau_ky(phien_ban_van_ban_id)
 WHERE phien_ban_van_ban_id IS NOT NULL AND phien_ban_adapter=N'ctsv-p11-v1';
CREATE UNIQUE INDEX uq_p11_attempt_command ON dbo.lan_goi_dich_vu_ky(ma_lien_ket_yeu_cau)
 WHERE ma_lien_ket_yeu_cau IS NOT NULL;
CREATE INDEX ix_p11_attempt_due ON dbo.lan_goi_dich_vu_ky(giai_doan_xu_ly,lease_den,id) INCLUDE(yeu_cau_ky_id,buoc_ky_id);
CREATE UNIQUE INDEX uq_p11_current_minutes_document ON dbo.mghp_lien_ket_phien_ban_van_ban(bien_ban_id)
 WHERE bien_ban_id IS NOT NULL AND trang_thai=N'current';
CREATE UNIQUE INDEX uq_p11_current_decision_document ON dbo.mghp_lien_ket_phien_ban_van_ban(phien_ban_qd_id)
 WHERE phien_ban_qd_id IS NOT NULL AND trang_thai=N'current';
IF NOT EXISTS(SELECT 1 FROM dbo.vai_tro WHERE ma=N'mghp_pho_hieu_truong')
 INSERT dbo.vai_tro(ma,ten,dang_hoat_dong) VALUES(N'mghp_pho_hieu_truong',N'Phó Hiệu trưởng MGHP',1);
GO
CREATE TRIGGER dbo.tr_p11_artifact_immutable ON dbo.tep_ket_qua_ky AFTER UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d WHERE d.trang_thai_toan_ven=N'verified')
  THROW 51201,N'Verified signing artifact is immutable.',1;
END;
GO
