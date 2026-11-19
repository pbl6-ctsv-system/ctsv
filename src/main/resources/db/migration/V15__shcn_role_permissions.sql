-- SHCN: stable role/permission catalogue only. No account, password, faculty grant or schema change.
SET XACT_ABORT ON;
SET NOCOUNT ON;

DECLARE @roles TABLE(code nvarchar(128),label nvarchar(512));
INSERT @roles VALUES(N'shcn_truong_khoa',N'Trưởng khoa SHCN'),(N'shcn_giang_vien',N'Giảng viên SHCN');
INSERT dbo.VaiTro(MaVaiTro,TenVaiTro,TrangThai)
SELECT r.code,r.label,1 FROM @roles r
WHERE NOT EXISTS(SELECT 1 FROM dbo.VaiTro x WHERE x.MaVaiTro=r.code COLLATE Latin1_General_100_BIN2);

-- Foundation stores scope grants; Auth owns the shared role/permission catalogue.
-- The canonical code is the bridge; independent identity values are never assumed equal.
INSERT dbo.vai_tro(ma,ten,dang_hoat_dong)
SELECT r.code,r.label,1 FROM @roles r WHERE NOT EXISTS(SELECT 1 FROM dbo.vai_tro x WHERE x.ma=r.code);
IF EXISTS(SELECT 1 FROM dbo.vai_tro f JOIN @roles r ON f.ma=r.code
          WHERE f.ma COLLATE Latin1_General_100_BIN2<>r.code COLLATE Latin1_General_100_BIN2)
    THROW 51501,N'SHCN role code differs from its canonical Auth code; review mapping.',1;

DECLARE @permissions TABLE(code nvarchar(128),label nvarchar(512));
INSERT @permissions VALUES
    (N'shcn.phanCongKhoa.read',N'Đọc phân công GVCN của khoa'),
    (N'shcn.phanCongKhoa.prepare',N'Lập và sửa nháp phân công GVCN của khoa');
INSERT dbo.ChucNang(MaChucNang,TenChucNang,TrangThai)
SELECT p.code,p.label,1 FROM @permissions p
WHERE NOT EXISTS(SELECT 1 FROM dbo.ChucNang x WHERE x.MaChucNang=p.code COLLATE Latin1_General_100_BIN2);

INSERT dbo.VaiTro_ChucNang(VaiTroID,ChucNangID)
SELECT r.ID,c.ID FROM dbo.VaiTro r JOIN dbo.ChucNang c
    ON c.MaChucNang IN(N'shcn.phanCongKhoa.read',N'shcn.phanCongKhoa.prepare')
WHERE r.MaVaiTro=N'shcn_truong_khoa'
  AND NOT EXISTS(SELECT 1 FROM dbo.VaiTro_ChucNang e WHERE e.VaiTroID=r.ID AND e.ChucNangID=c.ID);

-- Lecturer has no faculty-assignment permission by default. CTSV_USER remains the separate access role.
INSERT dbo.NhatKyAuth(SuKienID,MaTuongQuan,TacNhanHeThong,HanhDong,LyDo)
VALUES(NEWID(),NEWID(),'migration','RBAC_SEEDED',N'SHCN faculty assignment role/permission catalogue V15');
