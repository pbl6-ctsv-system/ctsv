-- T03.3: scholarship rule snapshots. No default policy, round assignment or Foundation changes.
CREATE TABLE dbo.kkht_phien_ban_quy_tac (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_kkht_phien_ban_quy_tac PRIMARY KEY,
    phien_ban int NOT NULL,
    can_cu nvarchar(4000) NOT NULL,
    hieu_luc_tu date NOT NULL,
    hieu_luc_den date NULL,
    dieu_kien_json nvarchar(max) NOT NULL,
    muc_abc_json nvarchar(max) NOT NULL,
    thu_tu_uu_tien_json nvarchar(max) NOT NULL,
    can_cu_hoc_phi_json nvarchar(max) NOT NULL,
    quy_tac_bang_diem_json nvarchar(max) NOT NULL,
    ty_le_suat_cuoi decimal(19,10) NOT NULL,
    cach_cap_suat_cuoi nvarchar(32) NOT NULL,
    trang_thai nvarchar(32) NOT NULL,
    da_su_dung_luc datetimeoffset(7) NULL,
    phien_ban_ky_thuat rowversion NOT NULL,
    CONSTRAINT uq_kkht_phien_ban_quy_tac_version UNIQUE (phien_ban),
    CONSTRAINT ck_kkht_rule_version CHECK (phien_ban>0),
    CONSTRAINT ck_kkht_rule_basis CHECK (LEN(LTRIM(RTRIM(can_cu)))>0),
    CONSTRAINT ck_kkht_rule_dates CHECK (hieu_luc_den IS NULL OR hieu_luc_den>=hieu_luc_tu),
    CONSTRAINT ck_kkht_rule_conditions CHECK (ISJSON(dieu_kien_json)=1 AND LEFT(LTRIM(dieu_kien_json),1)=N'{'),
    CONSTRAINT ck_kkht_rule_levels CHECK (ISJSON(muc_abc_json)=1 AND LEFT(LTRIM(muc_abc_json),1)=N'{'),
    CONSTRAINT ck_kkht_rule_priority CHECK (ISJSON(thu_tu_uu_tien_json)=1 AND LEFT(LTRIM(thu_tu_uu_tien_json),1)=N'{'),
    CONSTRAINT ck_kkht_rule_tuition CHECK (ISJSON(can_cu_hoc_phi_json)=1 AND LEFT(LTRIM(can_cu_hoc_phi_json),1)=N'{'),
    CONSTRAINT ck_kkht_rule_tie CHECK (ISJSON(quy_tac_bang_diem_json)=1 AND LEFT(LTRIM(quy_tac_bang_diem_json),1)=N'{'),
    CONSTRAINT ck_kkht_rule_last_slot CHECK (ty_le_suat_cuoi=0.5000000000
        AND cach_cap_suat_cuoi COLLATE Latin1_General_100_BIN2=N'REMAINING_FUND'),
    CONSTRAINT ck_kkht_rule_usage CHECK (
        (trang_thai COLLATE Latin1_General_100_BIN2=N'draft' AND da_su_dung_luc IS NULL) OR
        (trang_thai COLLATE Latin1_General_100_BIN2=N'used' AND da_su_dung_luc IS NOT NULL AND DATEPART(TZOFFSET,da_su_dung_luc)=0))
);
GO

-- WITH CHECK intentionally rejects pre-existing orphan references instead of silently reassigning them.
ALTER TABLE dbo.kkht_dot_xet WITH CHECK ADD CONSTRAINT fk_kkht_dot_xet_rule_version
    FOREIGN KEY (quy_tac_id) REFERENCES dbo.kkht_phien_ban_quy_tac(id);
CREATE INDEX ix_kkht_dot_xet_rule_version ON dbo.kkht_dot_xet(quy_tac_id) WHERE quy_tac_id IS NOT NULL;
GO

CREATE TRIGGER dbo.tr_kkht_rule_created_unused ON dbo.kkht_phien_ban_quy_tac
AFTER INSERT AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM inserted WHERE da_su_dung_luc IS NOT NULL)
        THROW 51950,N'A rule must be created unused; first use is recorded by a real round reference.',1;
END;
GO

CREATE TRIGGER dbo.tr_kkht_rule_preserve_history ON dbo.kkht_phien_ban_quy_tac
AFTER UPDATE,DELETE AS
BEGIN
    SET NOCOUNT ON;
    -- No DELETE API: retain all issued version numbers and history, including unused versions.
    IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id=d.id WHERE i.id IS NULL)
        THROW 51950,N'Rule version history cannot be deleted.',1;
    IF EXISTS (
        SELECT 1 FROM deleted d JOIN inserted i ON i.id=d.id
        WHERE i.phien_ban<>d.phien_ban
          OR (d.da_su_dung_luc IS NOT NULL AND (
              i.da_su_dung_luc IS NULL OR i.da_su_dung_luc<>d.da_su_dung_luc OR
              CONVERT(varbinary(max),i.trang_thai)<>CONVERT(varbinary(max),d.trang_thai)))
          OR ((d.da_su_dung_luc IS NOT NULL OR i.da_su_dung_luc IS NOT NULL) AND EXISTS (
              SELECT CONVERT(varbinary(max),i.can_cu),i.hieu_luc_tu,i.hieu_luc_den,
                     CONVERT(varbinary(max),i.dieu_kien_json),CONVERT(varbinary(max),i.muc_abc_json),
                     CONVERT(varbinary(max),i.thu_tu_uu_tien_json),CONVERT(varbinary(max),i.can_cu_hoc_phi_json),
                     CONVERT(varbinary(max),i.quy_tac_bang_diem_json),i.ty_le_suat_cuoi,CONVERT(varbinary(max),i.cach_cap_suat_cuoi)
              EXCEPT
              SELECT CONVERT(varbinary(max),d.can_cu),d.hieu_luc_tu,d.hieu_luc_den,
                     CONVERT(varbinary(max),d.dieu_kien_json),CONVERT(varbinary(max),d.muc_abc_json),
                     CONVERT(varbinary(max),d.thu_tu_uu_tien_json),CONVERT(varbinary(max),d.can_cu_hoc_phi_json),
                     CONVERT(varbinary(max),d.quy_tac_bang_diem_json),d.ty_le_suat_cuoi,CONVERT(varbinary(max),d.cach_cap_suat_cuoi)))
          OR (d.da_su_dung_luc IS NULL AND i.da_su_dung_luc IS NOT NULL AND NOT EXISTS (
              SELECT 1 FROM dbo.kkht_dot_xet r WITH (UPDLOCK,HOLDLOCK) WHERE r.quy_tac_id=i.id))
    ) THROW 51950,N'Used rule versions and their first-use metadata are immutable.',1;
END;
GO

CREATE TRIGGER dbo.tr_kkht_round_record_rule_use ON dbo.kkht_dot_xet
AFTER INSERT,UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE p WITH (UPDLOCK,HOLDLOCK)
    SET trang_thai=N'used',da_su_dung_luc=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')
    FROM dbo.kkht_phien_ban_quy_tac p
    JOIN (SELECT DISTINCT quy_tac_id FROM inserted WHERE quy_tac_id IS NOT NULL) r ON r.quy_tac_id=p.id
    WHERE p.da_su_dung_luc IS NULL;
END;
GO
