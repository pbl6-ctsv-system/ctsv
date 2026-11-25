-- Owner25/11: explicit online -> hard-copy handoff; preserve legacy closed review dates as-is.
ALTER TABLE dbo.mghp_dot_xet ADD
 nguoi_chot_online_id bigint NULL,
 thoi_diem_chot_online datetimeoffset(7) NULL,
 sha256_nguon_chot_online varchar(64) NULL;
ALTER TABLE dbo.mghp_dot_xet ADD
 CONSTRAINT fk_stage_online_actor FOREIGN KEY(nguoi_chot_online_id) REFERENCES dbo.nguoi_dung(id),
 CONSTRAINT ck_stage_online_marker CHECK(
  (nguoi_chot_online_id IS NULL AND thoi_diem_chot_online IS NULL AND sha256_nguon_chot_online IS NULL) OR
  (nguoi_chot_online_id IS NOT NULL AND thoi_diem_chot_online IS NOT NULL AND LEN(sha256_nguon_chot_online)=64 AND sha256_nguon_chot_online IS NOT NULL));
ALTER TABLE dbo.mghp_lich_su_lich_trinh ADD
 nguoi_chot_online_id bigint NULL,
 thoi_diem_chot_online datetimeoffset(7) NULL,
 sha256_nguon_chot_online varchar(64) NULL;
ALTER TABLE dbo.mghp_lich_su_lich_trinh ADD CONSTRAINT fk_stage_history_online_actor
 FOREIGN KEY(nguoi_chot_online_id) REFERENCES dbo.nguoi_dung(id);
GO
CREATE TRIGGER dbo.tr_stage_online_marker_immutable ON dbo.mghp_dot_xet AFTER UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d JOIN inserted i ON i.id=d.id
  WHERE d.thoi_diem_chot_online IS NOT NULL AND EXISTS(
   SELECT i.nguoi_chot_online_id,i.thoi_diem_chot_online,i.sha256_nguon_chot_online
   EXCEPT SELECT d.nguoi_chot_online_id,d.thoi_diem_chot_online,d.sha256_nguon_chot_online))
  THROW 51981,N'Online handoff is immutable.',1;
END;
