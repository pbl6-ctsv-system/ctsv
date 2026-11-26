-- SHCN TASK-TK-02: faculty assignment PDF/signing link, reopen-from-pending-sign guard, and RBAC permissions.
-- No changes to dbo columns, Foundation/SIS tables, or synthetic accounts.
SET XACT_ABORT ON;
SET NOCOUNT ON;

CREATE TABLE shcn.shcn_lien_ket_van_ban_khoa (
    id bigint IDENTITY(1,1) NOT NULL,
    ma_danh_sach_khoa bigint NOT NULL,
    phien_ban_van_ban_id bigint NOT NULL,
    noi_dung_hanh_chinh_json nvarchar(max) NULL,
    trang_thai varchar(32) NOT NULL,
    thoi_diem_tao datetimeoffset(7) NOT NULL,
    thoi_diem_cap_nhat datetimeoffset(7) NOT NULL,
    CONSTRAINT pk_shcn_lien_ket_van_ban_khoa PRIMARY KEY CLUSTERED(id),
    CONSTRAINT fk_shcn_lien_ket_van_ban_danh_sach FOREIGN KEY(ma_danh_sach_khoa)
        REFERENCES shcn.shcn_danh_sach_phan_cong_khoa(id),
    CONSTRAINT fk_shcn_lien_ket_van_ban_phien_ban FOREIGN KEY(phien_ban_van_ban_id)
        REFERENCES dbo.phien_ban_van_ban(id),
    CONSTRAINT uq_shcn_lien_ket_van_ban_phien_ban UNIQUE(phien_ban_van_ban_id),
    CONSTRAINT ck_shcn_lien_ket_van_ban_json CHECK(noi_dung_hanh_chinh_json IS NULL OR ISJSON(noi_dung_hanh_chinh_json)=1),
    CONSTRAINT ck_shcn_lien_ket_van_ban_trang_thai CHECK(trang_thai IN ('current','superseded'))
);

CREATE UNIQUE INDEX uq_shcn_lien_ket_van_ban_current
    ON shcn.shcn_lien_ket_van_ban_khoa(ma_danh_sach_khoa)
    WHERE trang_thai='current';

CREATE INDEX ix_shcn_lien_ket_van_ban_danh_sach
    ON shcn.shcn_lien_ket_van_ban_khoa(ma_danh_sach_khoa,id DESC);

CREATE UNIQUE INDEX uq_shcn_tk02_request_document
    ON dbo.yeu_cau_ky(phien_ban_van_ban_id)
    WHERE phien_ban_van_ban_id IS NOT NULL AND phien_ban_adapter=N'ctsv-shcn-tk02-v1';

DECLARE @permissions TABLE(code nvarchar(128),label nvarchar(512));
INSERT @permissions VALUES
    (N'shcn.phanCongKhoa.sign',N'Xuất PDF và ký số phân công GVCN của khoa'),
    (N'shcn.phanCongKhoa.submit',N'Gửi danh sách phân công GVCN của khoa cho chuyên viên');

INSERT dbo.ChucNang(MaChucNang,TenChucNang,TrangThai)
SELECT p.code,p.label,1 FROM @permissions p
WHERE NOT EXISTS(SELECT 1 FROM dbo.ChucNang x WHERE x.MaChucNang=p.code COLLATE Latin1_General_100_BIN2);

INSERT dbo.VaiTro_ChucNang(VaiTroID,ChucNangID)
SELECT r.ID,c.ID FROM dbo.VaiTro r JOIN dbo.ChucNang c
    ON c.MaChucNang IN(N'shcn.phanCongKhoa.sign',N'shcn.phanCongKhoa.submit')
WHERE r.MaVaiTro=N'shcn_truong_khoa'
  AND NOT EXISTS(SELECT 1 FROM dbo.VaiTro_ChucNang e WHERE e.VaiTroID=r.ID AND e.ChucNangID=c.ID);

INSERT dbo.NhatKyAuth(SuKienID,MaTuongQuan,TacNhanHeThong,HanhDong,LyDo)
VALUES(NEWID(),NEWID(),'migration','RBAC_SEEDED',N'SHCN faculty assignment signing/submission permissions V33');
GO

ALTER TRIGGER shcn.tr_shcn_danh_sach_khoa_bao_ve
ON shcn.shcn_danh_sach_phan_cong_khoa AFTER UPDATE,DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id=d.id
              WHERE i.id IS NULL AND d.trang_thai<>'NHAP')
        THROW 51201,'SHCN closed assignment content cannot be deleted.',1;
    IF EXISTS(SELECT 1 FROM deleted d JOIN inserted i ON i.id=d.id WHERE EXISTS(
        SELECT CONVERT(varbinary(max),i.ma_khoa),CONVERT(varbinary(max),i.nam_hoc),i.phien_ban,
               i.ma_danh_sach_truoc,CONVERT(varbinary(max),i.ma_nguoi_lap),i.ngay_tao
        EXCEPT
        SELECT CONVERT(varbinary(max),d.ma_khoa),CONVERT(varbinary(max),d.nam_hoc),d.phien_ban,
               d.ma_danh_sach_truoc,CONVERT(varbinary(max),d.ma_nguoi_lap),d.ngay_tao))
        THROW 51202,'SHCN assignment identity and provenance are immutable.',1;
    -- Allow CHO_KY -> NHAP reopen on the same version when unsigned; DA_KY/DA_GUI_CHUYEN_VIEN/DA_THAY_THE/DA_HUY remain sealed.
    IF EXISTS(SELECT 1 FROM deleted d JOIN inserted i ON i.id=d.id
              WHERE d.trang_thai<>'NHAP' AND (
                    (i.trang_thai='NHAP' AND d.trang_thai<>'CHO_KY') OR EXISTS(
        SELECT i.hieu_luc_tu,i.hieu_luc_den,CONVERT(varbinary(max),i.ly_do_dieu_chinh)
        EXCEPT
        SELECT d.hieu_luc_tu,d.hieu_luc_den,CONVERT(varbinary(max),d.ly_do_dieu_chinh))))
        THROW 51203,'SHCN closed assignment content cannot be edited or reopened.',1;
END;
GO
