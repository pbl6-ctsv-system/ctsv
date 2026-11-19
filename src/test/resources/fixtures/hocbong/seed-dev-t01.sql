-- Manual, opt-in DEV fixture; never called by dev.ps1 start or by Flyway.
-- Set this to the VERIFIED Foundation nguoi_dung.id bound to the DEV specialist account.
-- This is not an Auth account UUID or a SIS student/staff identifier.
DECLARE @SpecialistLocalUserId bigint = NULL;

SET XACT_ABORT ON;
IF DB_NAME() <> N'ctsv_dev'
    THROW 51910, N'HB fixture requires ctsv_dev.', 1;
IF ISNULL(CONVERT(nvarchar(128), SERVERPROPERTY('InstanceName')), N'') <> N'DEV2022'
    THROW 51910, N'HB fixture requires DEV2022.', 1;
IF NOT EXISTS (
    SELECT 1 FROM sys.databases WHERE database_id=DB_ID()
      AND SUSER_SNAME(owner_sid)=ORIGINAL_LOGIN() AND compatibility_level=160
      AND collation_name=N'Vietnamese_100_CI_AS_SC'
) OR NOT EXISTS (
    SELECT 1 FROM sys.extended_properties WHERE class=0 AND name=N'ctsv_dev_owner'
      AND CONVERT(nvarchar(128),value)=N'ctsv-local-dev-v1'
)
    THROW 51910, N'HB fixture database owner/options/marker mismatch.', 1;
IF NOT EXISTS (SELECT 1 FROM dbo.flyway_schema_history WHERE version=N'16' AND success=1)
    THROW 51910, N'Apply and validate V16 through the DEV migration workflow first.', 1;
IF @SpecialistLocalUserId IS NULL OR NOT EXISTS (
    SELECT 1 FROM dbo.nguoi_dung u WHERE u.id=@SpecialistLocalUserId
      AND u.dang_hoat_dong=1 AND u.thoi_diem_xoa IS NULL
      AND EXISTS (SELECT 1 FROM dbo.dinh_danh_dang_nhap d
                  WHERE d.nguoi_dung_id=u.id AND d.thoi_diem_xac_minh IS NOT NULL)
)
    THROW 51910, N'Set a verified active DEV specialist local user ID; no default grant.', 1;

BEGIN TRANSACTION;
DECLARE @lockResult int;
EXEC @lockResult=sys.sp_getapplock @Resource=N'ctsv.hocbong.t01.dev-fixture',
    @LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
IF @lockResult<0 THROW 51910, N'HB fixture is already running.', 1;

DECLARE @roleId bigint=(SELECT id FROM dbo.vai_tro WHERE ma=N'hoc_bong_chuyen_vien' AND dang_hoat_dong=1);
IF @roleId IS NULL THROW 51910, N'HB specialist role missing or inactive.', 1;
DECLARE @now datetimeoffset(7)=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00');
IF NOT EXISTS (
    SELECT 1 FROM dbo.phan_quyen_nguoi_dung WITH(UPDLOCK,HOLDLOCK)
    WHERE nguoi_dung_id=@SpecialistLocalUserId AND vai_tro_id=@roleId
      AND ma_phan_he=N'HOC_BONG' AND don_vi_id IS NULL
      AND (hieu_luc_tu IS NULL OR hieu_luc_tu<=@now)
      AND (hieu_luc_den IS NULL OR hieu_luc_den>@now)
)
BEGIN
    -- Preserve a scheduled open-ended grant instead of silently changing its effective date.
    IF EXISTS (SELECT 1 FROM dbo.phan_quyen_nguoi_dung
               WHERE nguoi_dung_id=@SpecialistLocalUserId AND vai_tro_id=@roleId
                 AND ma_phan_he=N'HOC_BONG' AND don_vi_id IS NULL AND hieu_luc_den IS NULL)
        THROW 51910, N'A planned grant already exists; review it rather than overwriting it.', 1;
    INSERT dbo.phan_quyen_nguoi_dung
        (nguoi_dung_id,vai_tro_id,ma_phan_he,hieu_luc_tu,nguoi_cap_quyen_id,thoi_diem_tao)
    VALUES (@SpecialistLocalUserId,@roleId,N'HOC_BONG',@now,@SpecialistLocalUserId,@now);
END;

DECLARE @periods TABLE (ma nvarchar(128),ten nvarchar(512),bat_dau date,ket_thuc date);
INSERT @periods VALUES
    (N'9410',N'SYN-HB-T01-DEV HK1',CONVERT(date,'20940801',112),CONVERT(date,'20950115',112)),
    (N'9420',N'SYN-HB-T01-DEV HK2',CONVERT(date,'20950116',112),CONVERT(date,'20950630',112)),
    (N'9421',N'SYN-HB-T01-DEV hè',CONVERT(date,'20950701',112),CONVERT(date,'20950815',112));
IF EXISTS (
    SELECT 1 FROM @periods p JOIN dbo.hoc_ky h WITH(UPDLOCK,HOLDLOCK) ON h.ma=p.ma
    WHERE h.ten IS NULL OR h.ten<>p.ten OR h.ngay_bat_dau IS NULL OR h.ngay_bat_dau<>p.bat_dau
      OR h.ngay_ket_thuc IS NULL OR h.ngay_ket_thuc<>p.ket_thuc
      OR h.trang_thai_xac_minh_moc IS NULL OR h.trang_thai_xac_minh_moc<>N'verified'
      OR h.dang_hien_dien_nguon<>1
)
    THROW 51910, N'Fixture code exists with other or edited data; no overwrite performed.', 1;
INSERT dbo.hoc_ky (ma,ten,nam_hoc_nguon,ngay_bat_dau,ngay_ket_thuc,
    trang_thai_xac_minh_moc,can_cu_xac_minh_moc,dang_hien_dien_nguon)
SELECT p.ma,p.ten,N'2094-2095',p.bat_dau,p.ket_thuc,N'verified',
       N'SYNTHETIC HB T01 owner-invoked DEV fixture; not SIS production data.',1
FROM @periods p WHERE NOT EXISTS (SELECT 1 FROM dbo.hoc_ky h WHERE h.ma=p.ma);
COMMIT TRANSACTION;

-- Select 9410 or 9420 for create; 9421 is displayed as merged summer/non-selectable.
-- The first API POST creates hb_hoc_ky/alias/round/audit. No grades, funds or rules are seeded here.
SELECT id,ma,ten,ngay_bat_dau,ngay_ket_thuc FROM dbo.hoc_ky WHERE ma IN (N'9410',N'9420',N'9421') ORDER BY ma;
