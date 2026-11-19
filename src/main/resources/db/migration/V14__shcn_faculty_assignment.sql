-- SHCN faculty assignment persistence, ERD tables 1-2. No changes to dbo/Foundation/SIS.
SET XACT_ABORT ON;
SET NOCOUNT ON;
IF SCHEMA_ID(N'shcn') IS NULL EXEC(N'CREATE SCHEMA shcn AUTHORIZATION dbo');

CREATE TABLE shcn.shcn_danh_sach_phan_cong_khoa (
    id bigint IDENTITY(1,1) NOT NULL,
    ma_khoa varchar(32) NOT NULL,
    nam_hoc nvarchar(20) NOT NULL,
    phien_ban int NOT NULL,
    ma_danh_sach_truoc bigint NULL,
    hieu_luc_tu datetimeoffset(7) NOT NULL,
    hieu_luc_den datetimeoffset(7) NULL,
    trang_thai varchar(40) NOT NULL,
    ma_nguoi_lap varchar(128) NOT NULL,
    ma_ho_so_tai_lieu varchar(128) NULL,
    ngay_gui_chuyen_vien datetimeoffset(7) NULL,
    ly_do_dieu_chinh nvarchar(max) NULL,
    ly_do_huy nvarchar(max) NULL,
    ngay_tao datetimeoffset(7) NOT NULL,
    ngay_cap_nhat datetimeoffset(7) NOT NULL,
    phien_ban_ghi rowversion NOT NULL,
    CONSTRAINT pk_shcn_danh_sach_khoa PRIMARY KEY CLUSTERED(id),
    CONSTRAINT uq_shcn_danh_sach_khoa_phien_ban UNIQUE(ma_khoa,nam_hoc,phien_ban),
    -- Candidate key supports the same-faculty predecessor FK; no new business field.
    CONSTRAINT uq_shcn_danh_sach_khoa_id_khoa UNIQUE(id,ma_khoa),
    CONSTRAINT ck_shcn_danh_sach_khoa_phien_ban CHECK(phien_ban>=1),
    CONSTRAINT ck_shcn_danh_sach_khoa_hieu_luc CHECK(hieu_luc_den IS NULL OR hieu_luc_den>hieu_luc_tu),
    -- An inherited version must already exist; decreasing identity prevents self-links/cycles.
    CONSTRAINT ck_shcn_danh_sach_khoa_truoc CHECK(ma_danh_sach_truoc IS NULL OR ma_danh_sach_truoc<id),
    CONSTRAINT ck_shcn_danh_sach_khoa_trang_thai CHECK(trang_thai IN
        ('NHAP','CHO_KY','DA_KY','DA_GUI_CHUYEN_VIEN','DA_THAY_THE','DA_HUY')),
    CONSTRAINT fk_shcn_danh_sach_khoa_truoc FOREIGN KEY(ma_danh_sach_truoc,ma_khoa)
        REFERENCES shcn.shcn_danh_sach_phan_cong_khoa(id,ma_khoa)
);

CREATE TABLE shcn.shcn_phan_cong_gvcn_khoa (
    id bigint IDENTITY(1,1) NOT NULL,
    ma_danh_sach_khoa bigint NOT NULL,
    ma_lop varchar(32) NOT NULL,
    ma_giao_vien varchar(32) NOT NULL,
    ghi_chu nvarchar(max) NULL,
    ngay_tao datetimeoffset(7) NOT NULL,
    ngay_cap_nhat datetimeoffset(7) NOT NULL,
    CONSTRAINT pk_shcn_phan_cong_khoa PRIMARY KEY CLUSTERED(id),
    CONSTRAINT uq_shcn_phan_cong_khoa_lop UNIQUE(ma_danh_sach_khoa,ma_lop),
    CONSTRAINT uq_shcn_phan_cong_khoa_nguon UNIQUE(id,ma_lop,ma_giao_vien),
    CONSTRAINT fk_shcn_phan_cong_khoa_danh_sach FOREIGN KEY(ma_danh_sach_khoa)
        REFERENCES shcn.shcn_danh_sach_phan_cong_khoa(id)
);
GO

CREATE TRIGGER shcn.tr_shcn_danh_sach_khoa_bao_ve
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
    IF EXISTS(SELECT 1 FROM deleted d JOIN inserted i ON i.id=d.id
              WHERE d.trang_thai<>'NHAP' AND (i.trang_thai='NHAP' OR EXISTS(
        SELECT i.hieu_luc_tu,i.hieu_luc_den,CONVERT(varbinary(max),i.ly_do_dieu_chinh)
        EXCEPT
        SELECT d.hieu_luc_tu,d.hieu_luc_den,CONVERT(varbinary(max),d.ly_do_dieu_chinh))))
        THROW 51203,'SHCN closed assignment content cannot be edited or reopened.',1;
END;
GO

CREATE TRIGGER shcn.tr_shcn_phan_cong_khoa_bao_ve
ON shcn.shcn_phan_cong_gvcn_khoa AFTER INSERT,UPDATE,DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM deleted d JOIN inserted i ON i.id=d.id
              WHERE d.ma_danh_sach_khoa<>i.ma_danh_sach_khoa
                OR CONVERT(varbinary(max),d.ma_lop)<>CONVERT(varbinary(max),i.ma_lop))
        THROW 51204,'SHCN assignment line identity cannot be moved.',1;
    IF EXISTS(SELECT 1 FROM shcn.shcn_danh_sach_phan_cong_khoa r WITH(UPDLOCK,HOLDLOCK)
              JOIN (SELECT ma_danh_sach_khoa FROM inserted UNION SELECT ma_danh_sach_khoa FROM deleted) x
                ON x.ma_danh_sach_khoa=r.id WHERE r.trang_thai<>'NHAP')
        THROW 51205,'SHCN assignment lines are writable only while draft.',1;
    -- Any child mutation consumes the aggregate token, including direct SQL/JPA writes.
    UPDATE r SET ngay_cap_nhat=TODATETIMEOFFSET(SYSUTCDATETIME(),'+00:00')
    FROM shcn.shcn_danh_sach_phan_cong_khoa r
    JOIN (SELECT ma_danh_sach_khoa FROM inserted UNION SELECT ma_danh_sach_khoa FROM deleted) x
      ON x.ma_danh_sach_khoa=r.id;
END;
GO
