-- P10 snapshots are immutable from creation, including superseded pre-meeting versions.
-- V22 has already been verified on the isolated test DB; this additive guard never rewrites it.
CREATE TRIGGER dbo.tr_p10_candidate_append_only ON dbo.mghp_ung_vien_phien_ban_qd AFTER UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d JOIN dbo.mghp_phien_ban_qd v ON v.id=d.phien_ban_qd_id WHERE v.sha256_nguon IS NOT NULL)
 THROW 51934,N'P10 candidate snapshot is append-only.',1;
END;
GO
CREATE TRIGGER dbo.tr_p10_line_append_only ON dbo.mghp_dong_qd AFTER UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d JOIN dbo.mghp_phien_ban_qd v ON v.id=d.phien_ban_qd_id WHERE v.sha256_nguon IS NOT NULL)
 THROW 51934,N'P10 decision line snapshot is append-only.',1;
END;
GO
CREATE TRIGGER dbo.tr_p10_period_append_only ON dbo.mghp_khoang_huong AFTER UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d JOIN dbo.mghp_dong_qd l ON l.id=d.dong_qd_id JOIN dbo.mghp_phien_ban_qd v ON v.id=l.phien_ban_qd_id WHERE v.sha256_nguon IS NOT NULL)
 THROW 51934,N'P10 granted period snapshot is append-only.',1;
END;

