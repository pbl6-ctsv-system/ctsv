-- Preserve attempts recorded under V9. SQL still enforces the new cap on future inserts.
IF EXISTS (SELECT 1 FROM dbo.luot_tai_tep WHERE kich_thuoc > 10000000)
BEGIN
    ALTER TABLE dbo.luot_tai_tep WITH NOCHECK
        ADD CONSTRAINT ck_luot_tai_tep_upload_limit CHECK (kich_thuoc <= 10000000);
END
ELSE
BEGIN
    ALTER TABLE dbo.luot_tai_tep WITH CHECK
        ADD CONSTRAINT ck_luot_tai_tep_upload_limit CHECK (kich_thuoc <= 10000000);
END;
