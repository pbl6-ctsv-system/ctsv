-- SHCN TASK-CV-02 / CV-001. NEW code only: owner applies explicitly; no DEV accounts or scope grants.
SET XACT_ABORT ON;
SET NOCOUNT ON;
IF SCHEMA_ID(N'shcn') IS NULL EXEC(N'CREATE SCHEMA shcn AUTHORIZATION dbo');
CREATE TABLE shcn.shcn_dot_sinh_hoat (
    id bigint IDENTITY(1,1) NOT NULL,
    ma_hoc_ky varchar(32) NOT NULL,
    ten_dot nvarchar(200) NOT NULL,
    khung_ngay_bat_dau date NOT NULL,
    khung_ngay_ket_thuc date NOT NULL,
    khung_tiet_cho_phep nvarchar(max) NOT NULL,
    han_chot_dang_ky_lich datetimeoffset(7) NOT NULL,
    trang_thai varchar(40) NOT NULL,
    ma_nguoi_tao varchar(128) NOT NULL,
    ma_ho_so_duyet_dot varchar(128) NULL,
    ma_nguoi_phat_hanh varchar(128) NULL,
    ngay_phat_hanh datetimeoffset(7) NULL,
    ma_nguoi_huy varchar(128) NULL,
    ngay_huy datetimeoffset(7) NULL,
    ly_do_huy nvarchar(max) NULL,
    phien_ban_phan_cong_ap_dung int NULL,
    trang_thai_phan_cong varchar(40) NOT NULL,
    ma_ho_so_tai_lieu_phan_cong varchar(128) NULL,
    trang_thai_lich_tong_hop varchar(40) NOT NULL,
    phien_ban_lich_tong_hop int NULL,
    lich_tong_hop_da_trinh nvarchar(max) NULL,
    ma_ho_so_trinh_lich varchar(128) NULL,
    ma_nguoi_cong_bo_lich varchar(128) NULL,
    ngay_cong_bo_lich datetimeoffset(7) NULL,
    ngay_tao datetimeoffset(7) NOT NULL,
    ngay_cap_nhat datetimeoffset(7) NOT NULL,
    phien_ban_ghi rowversion NOT NULL,
    CONSTRAINT pk_shcn_dot PRIMARY KEY CLUSTERED(id),
    CONSTRAINT ck_shcn_dot_ngay CHECK(khung_ngay_ket_thuc>=khung_ngay_bat_dau),
    CONSTRAINT ck_shcn_dot_tiet_json CHECK(ISJSON(khung_tiet_cho_phep)=1 AND LEFT(LTRIM(khung_tiet_cho_phep),1)='['),
    CONSTRAINT ck_shcn_dot_lich_json CHECK(lich_tong_hop_da_trinh IS NULL OR ISJSON(lich_tong_hop_da_trinh)=1),
    CONSTRAINT ck_shcn_dot_state CHECK(trang_thai IN('NHAP','CHO_DUYET','BI_TRA_LAI','DA_DUYET','DA_PHAT_HANH','DA_HUY')),
    CONSTRAINT ck_shcn_dot_pc_state CHECK(trang_thai_phan_cong IN('CHUA_DONG_BO','NHAP','CHO_PHO_PHONG_DUYET','BI_TRA_LAI','CHO_BGH_KY','CO_HIEU_LUC')),
    CONSTRAINT ck_shcn_dot_lich_state CHECK(trang_thai_lich_tong_hop IN('DANG_CHOT','CHO_XAC_NHAN','BI_TRA_LAI','DA_XAC_NHAN','DANG_XEP_PHONG','DU_DIEU_KIEN_CONG_BO','DA_CONG_BO')),
    CONSTRAINT ck_shcn_dot_pc_version CHECK(phien_ban_phan_cong_ap_dung IS NULL OR phien_ban_phan_cong_ap_dung>=1),
    CONSTRAINT ck_shcn_dot_lich_version CHECK(phien_ban_lich_tong_hop IS NULL OR phien_ban_lich_tong_hop>=1)
);
CREATE INDEX ix_shcn_dot_ky_state_id ON shcn.shcn_dot_sinh_hoat(ma_hoc_ky,trang_thai,id);
GO
CREATE TRIGGER shcn.tr_shcn_dot_bao_ve ON shcn.shcn_dot_sinh_hoat AFTER UPDATE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d JOIN inserted i ON i.id=d.id WHERE EXISTS(
   SELECT CONVERT(varbinary(max),i.ma_nguoi_tao),i.ngay_tao
   EXCEPT SELECT CONVERT(varbinary(max),d.ma_nguoi_tao),d.ngay_tao))
  THROW 51701,'SHCN round provenance is immutable.',1;
 IF EXISTS(SELECT 1 FROM deleted d JOIN inserted i ON i.id=d.id
  WHERE (d.trang_thai NOT IN('NHAP','BI_TRA_LAI') OR d.trang_thai_phan_cong IN('CHO_PHO_PHONG_DUYET','CHO_BGH_KY','CO_HIEU_LUC')) AND EXISTS(
   SELECT CONVERT(varbinary(max),i.ma_hoc_ky),CONVERT(varbinary(max),i.ten_dot),i.khung_ngay_bat_dau,i.khung_ngay_ket_thuc,CONVERT(varbinary(max),i.khung_tiet_cho_phep),i.han_chot_dang_ky_lich
   EXCEPT SELECT CONVERT(varbinary(max),d.ma_hoc_ky),CONVERT(varbinary(max),d.ten_dot),d.khung_ngay_bat_dau,d.khung_ngay_ket_thuc,CONVERT(varbinary(max),d.khung_tiet_cho_phep),d.han_chot_dang_ky_lich))
  THROW 51702,'SHCN round configuration is closed.',1;
END;
GO
-- Register business roles/permissions in the existing shared catalogues only.
DECLARE @role nvarchar(128)=N'shcn_chuyen_vien';
INSERT dbo.VaiTro(MaVaiTro,TenVaiTro,TrangThai)
 SELECT @role,N'Chuyên viên SHCN',1 WHERE NOT EXISTS(SELECT 1 FROM dbo.VaiTro WHERE MaVaiTro=@role COLLATE Latin1_General_100_BIN2);
INSERT dbo.vai_tro(ma,ten,dang_hoat_dong)
 SELECT @role,N'Chuyên viên SHCN',1 WHERE NOT EXISTS(SELECT 1 FROM dbo.vai_tro WHERE ma=@role);
IF EXISTS(SELECT 1 FROM dbo.vai_tro WHERE ma=@role AND ma COLLATE Latin1_General_100_BIN2<>@role COLLATE Latin1_General_100_BIN2)
 THROW 51703,'SHCN specialist role code is not canonical.',1;
DECLARE @p TABLE(code nvarchar(128),label nvarchar(512));
INSERT @p VALUES(N'shcn.dot.read',N'Xem đợt SHCN toàn trường'),(N'shcn.dot.prepare',N'Tạo và sửa nháp đợt SHCN toàn trường'),(N'shcn.reference.read',N'Tra cứu học kỳ phục vụ SHCN');
INSERT dbo.ChucNang(MaChucNang,TenChucNang,TrangThai)
 SELECT p.code,p.label,1 FROM @p p WHERE NOT EXISTS(SELECT 1 FROM dbo.ChucNang c WHERE c.MaChucNang=p.code COLLATE Latin1_General_100_BIN2);
INSERT dbo.VaiTro_ChucNang(VaiTroID,ChucNangID)
 SELECT r.ID,c.ID FROM dbo.VaiTro r JOIN dbo.ChucNang c ON c.MaChucNang IN(SELECT code FROM @p)
 WHERE r.MaVaiTro=@role AND NOT EXISTS(SELECT 1 FROM dbo.VaiTro_ChucNang g WHERE g.VaiTroID=r.ID AND g.ChucNangID=c.ID);
INSERT dbo.NhatKyAuth(SuKienID,MaTuongQuan,TacNhanHeThong,HanhDong,LyDo)
 VALUES(NEWID(),NEWID(),'migration','RBAC_SEEDED',N'SHCN draft round and semester lookup permissions; no account grants');
