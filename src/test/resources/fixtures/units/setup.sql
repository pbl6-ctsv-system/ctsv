-- Optional manual F0 source registry, synthetic test environment only.
IF DB_NAME()<>N'ctsv_test_20261111' OR CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName'))<>N'DEV2022'
    THROW 51090,N'Unexpected P01 manual fixture target.',1;
SET XACT_ABORT ON;
BEGIN TRAN;
IF NOT EXISTS(SELECT 1 FROM dbo.he_thong_ngoai WHERE ma=N'P01-MANUAL-SIS')
    INSERT dbo.he_thong_ngoai(ma,ten,loai_he_thong,dang_hoat_dong)
    VALUES(N'P01-MANUAL-SIS',N'Nguồn SIS thử nghiệm thủ công',N'SIS',1);
COMMIT;
SELECT id,ma FROM dbo.he_thong_ngoai WHERE ma=N'P01-MANUAL-SIS';
