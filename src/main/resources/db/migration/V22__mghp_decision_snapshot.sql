-- P10 additive snapshot fences. Historical migrations and rows remain untouched.
ALTER TABLE dbo.mghp_quyet_dinh ADD phien_ban_ky_thuat rowversion;
ALTER TABLE dbo.mghp_bien_ban ADD phien_ban_ky_thuat rowversion;
ALTER TABLE dbo.mghp_phien_ban_qd ADD
 phien_ban_ky_thuat rowversion,
 ma_qd_luc_chot nvarchar(128) NULL,
 so_qd_luc_chot nvarchar(128) NULL,
 ngay_qd_luc_chot date NULL,
 don_vi_ban_hanh_luc_chot_id bigint NULL,
 sha256_nguon varchar(64) NULL,
 thoi_diem_dong_xet_nguon datetimeoffset(7) NULL,
 sha256_lenh_hop varchar(64) NULL;
ALTER TABLE dbo.mghp_phien_ban_qd ADD CONSTRAINT fk_p10_header_unit FOREIGN KEY(don_vi_ban_hanh_luc_chot_id) REFERENCES dbo.don_vi(id);
CREATE UNIQUE INDEX uq_p10_one_root_per_round ON dbo.mghp_quyet_dinh(dot_xet_id) WHERE dot_xet_id IS NOT NULL;
CREATE UNIQUE INDEX uq_p10_minutes_version ON dbo.mghp_bien_ban(phien_ban_qd_nguon_id) WHERE phien_ban_qd_nguon_id IS NOT NULL;
GO
CREATE TRIGGER dbo.tr_p10_header_snapshot ON dbo.mghp_phien_ban_qd AFTER UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id=d.id
 WHERE i.id IS NULL OR EXISTS(
 SELECT i.quyet_dinh_id,i.lan_sua_doi,i.ma_qd_luc_chot,i.so_qd_luc_chot,i.ngay_qd_luc_chot,i.don_vi_ban_hanh_luc_chot_id,
 i.sha256_nguon,i.thoi_diem_dong_xet_nguon,i.sha256_lenh_hop,i.sha256_tap_du_lieu,i.so_dong,i.giai_doan,i.phien_ban_truoc_id,
 CONVERT(varbinary(max),i.ly_do_tao_phien_ban),i.nguoi_tao_id,i.thoi_diem_tao
 EXCEPT
 SELECT d.quyet_dinh_id,d.lan_sua_doi,d.ma_qd_luc_chot,d.so_qd_luc_chot,d.ngay_qd_luc_chot,d.don_vi_ban_hanh_luc_chot_id,
 d.sha256_nguon,d.thoi_diem_dong_xet_nguon,d.sha256_lenh_hop,d.sha256_tap_du_lieu,d.so_dong,d.giai_doan,d.phien_ban_truoc_id,
 CONVERT(varbinary(max),d.ly_do_tao_phien_ban),d.nguoi_tao_id,d.thoi_diem_tao))
 THROW 51930,N'P10 version snapshot is append-only.',1;
END;
GO
CREATE TRIGGER dbo.tr_p10_minutes_snapshot ON dbo.mghp_bien_ban AFTER UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id=d.id WHERE i.id IS NULL OR EXISTS(
 SELECT i.dot_xet_id,i.ma_bb,i.so_tham_chieu,i.thoi_diem_hop,i.dia_diem,i.nguoi_tao_id,i.thoi_diem_tao,i.phien_ban_qd_nguon_id,i.sha256_ds_hop
 EXCEPT SELECT d.dot_xet_id,d.ma_bb,d.so_tham_chieu,d.thoi_diem_hop,d.dia_diem,d.nguoi_tao_id,d.thoi_diem_tao,d.phien_ban_qd_nguon_id,d.sha256_ds_hop))
 THROW 51931,N'P10 meeting snapshot is append-only.',1;
END;
GO
-- Reject inserts into locked versions, not only UPDATE/DELETE handled by baseline guards.
CREATE TRIGGER dbo.tr_p10_candidate_frozen ON dbo.mghp_ung_vien_phien_ban_qd AFTER INSERT AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.mghp_phien_ban_qd v ON v.id=i.phien_ban_qd_id WHERE v.thoi_diem_chot IS NOT NULL)
 THROW 51932,N'Cannot append candidates to a locked version.',1;
END;
GO
CREATE TRIGGER dbo.tr_p10_line_frozen ON dbo.mghp_dong_qd AFTER INSERT AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.mghp_phien_ban_qd v ON v.id=i.phien_ban_qd_id WHERE v.thoi_diem_chot IS NOT NULL)
 THROW 51932,N'Cannot append lines to a locked version.',1;
END;
GO
CREATE TRIGGER dbo.tr_p10_period_frozen ON dbo.mghp_khoang_huong AFTER INSERT AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM inserted i JOIN dbo.mghp_dong_qd l ON l.id=i.dong_qd_id JOIN dbo.mghp_phien_ban_qd v ON v.id=l.phien_ban_qd_id WHERE v.thoi_diem_chot IS NOT NULL)
 THROW 51932,N'Cannot append periods to a locked version.',1;
END;

