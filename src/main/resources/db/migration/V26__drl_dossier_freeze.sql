-- DRL-02.1 / C10: dossier completion freezes meeting facts and the original pair.
-- RL_CHOT_LOP still locks scores independently. Signing outputs remain appendable.
ALTER TABLE dbo.RLLopDot ADD NgayLapHoSo datetimeoffset(7) NULL;
GO

CREATE TRIGGER dbo.TR_RLLopDot_HoSo ON dbo.RLLopDot AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1 FROM deleted AS old LEFT JOIN inserted AS i ON i.ID = old.ID
        WHERE old.NgayLapHoSo IS NOT NULL
          AND (i.ID IS NULL OR EXISTS (
              SELECT CONVERT(varbinary(16), i.NgayLapHoSo)
              EXCEPT SELECT CONVERT(varbinary(16), old.NgayLapHoSo)))
    )
        THROW 51716, 'DRL: a completed dossier cannot be reopened or replaced.', 1;

    -- Root DML already owns the root's write lock. Hold the document ranges and
    -- shared metadata until commit, including when READ_COMMITTED_SNAPSHOT is on.
    IF EXISTS (
        SELECT 1 FROM inserted AS i LEFT JOIN deleted AS old ON old.ID = i.ID
        WHERE i.NgayLapHoSo IS NOT NULL AND old.NgayLapHoSo IS NULL
          AND NOT EXISTS (
              SELECT 1
              FROM dbo.RLVanBanKy AS k WITH (UPDLOCK, HOLDLOCK)
              JOIN dbo.phien_ban_van_ban AS v WITH (UPDLOCK, HOLDLOCK) ON v.id = k.PhienBanVanBanID
              JOIN dbo.tep_dinh_kem AS t WITH (UPDLOCK, HOLDLOCK) ON t.id = v.tep_goc_id
              WHERE k.LopDotID = i.ID
              HAVING COUNT_BIG(*) = 2
                 AND COUNT(DISTINCT k.LoaiVanBan) = 2
                 AND COUNT(DISTINCT v.van_ban_id) = 2
                 AND COUNT(v.thoi_diem_chot) = 2
                 AND COUNT(v.sha256_tap_du_lieu) = 2
                 AND COUNT(DISTINCT CONVERT(varbinary(64), v.sha256_tap_du_lieu)) = 1
                 AND SUM(CASE WHEN t.trang_thai_luu_tru = N'available' THEN 1 ELSE 0 END) = 2
          )
    )
        THROW 51716, 'DRL: dossier completion requires two stored originals from the same snapshot.', 1;
END;
GO

CREATE TRIGGER dbo.TR_RLHopLop_HoSo ON dbo.RLHopLop AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    -- Child writes serialize with completion on the same root. Looking up both
    -- sides also protects a move away from a completed class and multi-row DML.
    DECLARE @roots TABLE (ID bigint PRIMARY KEY, NgayLapHoSo datetimeoffset(7) NULL);
    INSERT @roots (ID, NgayLapHoSo)
    SELECT l.ID, l.NgayLapHoSo FROM dbo.RLLopDot AS l WITH (UPDLOCK, HOLDLOCK)
    JOIN (SELECT LopDotID FROM inserted UNION SELECT LopDotID FROM deleted) AS affected ON affected.LopDotID = l.ID;

    IF EXISTS (SELECT 1 FROM @roots WHERE NgayLapHoSo IS NOT NULL)
        THROW 51716, 'DRL: meeting facts used by a completed dossier are fixed.', 1;
END;
GO

CREATE TRIGGER dbo.TR_RLPhieu_HoSo ON dbo.RLPhieu AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    -- Use the marker read under the root lock, not a second snapshot read.
    DECLARE @roots TABLE (ID bigint PRIMARY KEY, NgayLapHoSo datetimeoffset(7) NULL);
    INSERT @roots (ID, NgayLapHoSo)
    SELECT l.ID, l.NgayLapHoSo FROM dbo.RLLopDot AS l WITH (UPDLOCK, HOLDLOCK)
    JOIN (SELECT LopDotID FROM inserted UNION SELECT LopDotID FROM deleted) AS affected ON affected.LopDotID = l.ID;

    -- All ballot fields feed the frozen dossier/result view, including the
    -- confirmation flags and their timestamps. Even no-op UPDATEs are refused.
    IF EXISTS (SELECT 1 FROM @roots WHERE NgayLapHoSo IS NOT NULL)
        THROW 51716, 'DRL: ballots used by a completed dossier are fixed.', 1;
END;
GO

CREATE TRIGGER dbo.TR_RLVanBanKy_HoSo ON dbo.RLVanBanKy AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    DECLARE @roots TABLE (ID bigint PRIMARY KEY, NgayLapHoSo datetimeoffset(7) NULL);
    INSERT @roots (ID, NgayLapHoSo)
    SELECT l.ID, l.NgayLapHoSo FROM dbo.RLLopDot AS l WITH (UPDLOCK, HOLDLOCK)
    JOIN (SELECT LopDotID FROM inserted UNION SELECT LopDotID FROM deleted) AS affected ON affected.LopDotID = l.ID;

    IF EXISTS (SELECT 1 FROM @roots WHERE NgayLapHoSo IS NOT NULL)
        THROW 51716, 'DRL: original dossier document links are fixed.', 1;
END;
GO
