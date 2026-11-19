-- READ ONLY: inspect the local CTSV DEV database, not a production target.
USE [ctsv_dev];

-- Auth: account -> role -> permission. These tables exist and are consumed by Auth.
SELECT r.MaVaiTro AS RoleCode,c.MaChucNang AS PermissionCode
FROM dbo.VaiTro_ChucNang e
JOIN dbo.VaiTro r ON r.ID=e.VaiTroID
JOIN dbo.ChucNang c ON c.ID=e.ChucNangID
ORDER BY r.MaVaiTro,c.MaChucNang;

-- Foundation: business role + faculty scope assigned to a local user.
-- The underscore in vai_tro is significant; it is a different table from Auth's VaiTro.
SELECT p.nguoi_dung_id AS LocalUserId,r.ma AS RoleCode,p.ma_phan_he AS ModuleCode,
       d.ma AS FacultyCode,p.bao_gom_don_vi_con AS IncludeChildren,p.hieu_luc_tu,p.hieu_luc_den
FROM dbo.phan_quyen_nguoi_dung p
JOIN dbo.vai_tro r ON r.id=p.vai_tro_id
LEFT JOIN dbo.don_vi d ON d.id=p.don_vi_id
WHERE p.ma_phan_he=N'SHCN'
ORDER BY r.ma,p.nguoi_dung_id;

-- Seed version and checksum, recorded separately in both DEV databases.
SELECT DB_NAME() AS DatabaseName,name,CONVERT(nvarchar(4000),value) AS Metadata
FROM sys.extended_properties WHERE class=0 AND name=N'shcn_dev_seed_package_v1';
SELECT N'sis_dev' AS DatabaseName,name,CONVERT(nvarchar(4000),value) AS Metadata
FROM sis_dev.sys.extended_properties WHERE class=0 AND name=N'shcn_dev_seed_package_v1';

-- V15 adds SHCN read/prepare here. A request also needs this same Auth role assigned to its account
-- and an active Foundation scope grant carrying the exact same canonical role code.
-- A permission from role A cannot be combined with the faculty scope of role B.
