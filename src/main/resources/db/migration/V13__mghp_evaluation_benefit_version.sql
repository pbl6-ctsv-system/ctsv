ALTER TABLE dbo.mghp_danh_gia_dieu_kien ADD phien_ban_ky_thuat rowversion NOT NULL;
ALTER TABLE dbo.mghp_quyen_huong ADD phien_ban_ky_thuat rowversion NOT NULL;
CREATE UNIQUE INDEX uq_mghp_evaluation_draft_application ON dbo.mghp_danh_gia_dieu_kien
 (ho_so_nguon_id,hoc_ky_ap_dung_id,ma_pham_vi,phien_ban_chinh_sach_id)
 WHERE ho_so_nguon_id IS NOT NULL AND trang_thai_xu_ly=N'draft';
CREATE UNIQUE INDEX uq_mghp_evaluation_draft_benefit ON dbo.mghp_danh_gia_dieu_kien
 (quyen_huong_nguon_id,hoc_ky_ap_dung_id,ma_pham_vi,phien_ban_chinh_sach_id)
 WHERE quyen_huong_nguon_id IS NOT NULL AND trang_thai_xu_ly=N'draft';
CREATE UNIQUE INDEX uq_mghp_evaluation_successor ON dbo.mghp_danh_gia_dieu_kien(danh_gia_truoc_id)
 WHERE danh_gia_truoc_id IS NOT NULL;
-- Keep confirmed snapshots protected even after their current flag is cleared.
DECLARE @trigger nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID(N'dbo.tr_mghp_danh_gia_dieu_kien_immutable'));
IF @trigger IS NULL THROW 51808,N'Evaluation immutable baseline trigger missing',1;
SET @trigger=REPLACE(@trigger,N'CREATE TRIGGER',N'CREATE OR ALTER TRIGGER');
SET @trigger=REPLACE(@trigger,N'x.la_ban_hien_hanh=1 OR EXISTS',N'x.la_ban_hien_hanh=1 OR x.trang_thai_xu_ly=N''confirmed'' OR EXISTS');
EXEC sys.sp_executesql @trigger;
GO
CREATE TRIGGER dbo.tr_p08_criterion_frozen ON dbo.mghp_ket_qua_tieu_chi AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM (SELECT danh_gia_dieu_kien_id id FROM inserted UNION SELECT danh_gia_dieu_kien_id FROM deleted) x
 JOIN dbo.mghp_danh_gia_dieu_kien a ON a.id=x.id WHERE a.trang_thai_xu_ly=N'confirmed')
 THROW 51809,N'Confirmed evaluation children are immutable',1;
END;
GO
CREATE TRIGGER dbo.tr_p08_evidence_frozen ON dbo.mghp_can_cu_danh_gia AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM (SELECT danh_gia_dieu_kien_id id FROM inserted UNION SELECT danh_gia_dieu_kien_id FROM deleted) x
 JOIN dbo.mghp_danh_gia_dieu_kien a ON a.id=x.id WHERE a.trang_thai_xu_ly=N'confirmed')
 THROW 51809,N'Confirmed evaluation children are immutable',1;
END;
GO
CREATE TRIGGER dbo.tr_p08_period_frozen ON dbo.mghp_khoang_du_dieu_kien AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM (SELECT danh_gia_dieu_kien_id id FROM inserted UNION SELECT danh_gia_dieu_kien_id FROM deleted) x
 JOIN dbo.mghp_danh_gia_dieu_kien a ON a.id=x.id WHERE a.trang_thai_xu_ly=N'confirmed')
 THROW 51809,N'Confirmed evaluation children are immutable',1;
END;
