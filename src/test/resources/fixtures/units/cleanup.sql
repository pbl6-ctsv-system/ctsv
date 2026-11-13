-- Stop the manual consumer before execution. Only projection rows of this synthetic source.
-- Leave immutable audit, source registry and sync history for inspection. Any FK prevents cleanup.
IF DB_NAME()<>N'ctsv_test_20261111' OR CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName'))<>N'DEV2022'
    THROW 51090,N'Unexpected P01 manual fixture target.',1;
SET XACT_ABORT ON;
BEGIN TRAN;
DELETE dbo.don_vi WHERE he_thong_nguon_id IN(SELECT id FROM dbo.he_thong_ngoai WHERE ma=N'P01-MANUAL-SIS');
COMMIT;
