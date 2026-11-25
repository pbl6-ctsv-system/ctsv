-- TASK-CV-04. SHCN-owned tables only. Owner applies explicitly; no seed/accounts/reference writes.
SET XACT_ABORT ON;
SET NOCOUNT ON;
IF OBJECT_ID(N'shcn.shcn_dot_sinh_hoat',N'U') IS NULL THROW 51800,'SHCN round migration must be applied first.',1;
CREATE TABLE shcn.shcn_mau_bien_ban (
 id bigint IDENTITY(1,1) NOT NULL,
 ma_dot bigint NOT NULL,
 ten_mau nvarchar(200) NOT NULL,
 trang_thai varchar(40) NOT NULL,
 ma_nguoi_cap_nhat varchar(128) NOT NULL,
 ngay_tao datetimeoffset(7) NOT NULL,
 ngay_cap_nhat datetimeoffset(7) NOT NULL,
 phien_ban_ghi rowversion NOT NULL,
 CONSTRAINT pk_shcn_mau PRIMARY KEY CLUSTERED(id),
 CONSTRAINT uq_shcn_mau_dot UNIQUE(ma_dot),
 CONSTRAINT uq_shcn_mau_id_dot UNIQUE(id,ma_dot),
 CONSTRAINT fk_shcn_mau_dot FOREIGN KEY(ma_dot) REFERENCES shcn.shcn_dot_sinh_hoat(id),
 CONSTRAINT ck_shcn_mau_state CHECK(trang_thai IN('NHAP','HOP_LE','DA_KHOA')),
 CONSTRAINT ck_shcn_mau_name CHECK(LEN(LTRIM(RTRIM(ten_mau)))>0)
);
CREATE TABLE shcn.shcn_muc_bien_ban (
 id bigint IDENTITY(1,1) NOT NULL,
 ma_mau bigint NOT NULL,
 ma_muc varchar(32) NOT NULL,
 tieu_de nvarchar(250) NOT NULL,
 thu_tu_hien_thi int NOT NULL,
 loai_muc varchar(32) NOT NULL,
 la_muc_he_thong bit NOT NULL,
 bat_buoc bit NOT NULL,
 cau_hinh_truong nvarchar(max) NOT NULL,
 ngay_tao datetimeoffset(7) NOT NULL,
 ngay_cap_nhat datetimeoffset(7) NOT NULL,
 CONSTRAINT pk_shcn_muc PRIMARY KEY CLUSTERED(id),
 CONSTRAINT fk_shcn_muc_mau FOREIGN KEY(ma_mau) REFERENCES shcn.shcn_mau_bien_ban(id),
 CONSTRAINT uq_shcn_muc_key UNIQUE(ma_mau,ma_muc),
 CONSTRAINT uq_shcn_muc_order UNIQUE(ma_mau,thu_tu_hien_thi),
 CONSTRAINT ck_shcn_muc_order CHECK(thu_tu_hien_thi>=1),
 CONSTRAINT ck_shcn_muc_json CHECK(ISJSON(cau_hinh_truong)=1 AND LEFT(LTRIM(cau_hinh_truong),1)='['),
 CONSTRAINT ck_shcn_muc_kind CHECK(loai_muc IN('DIEM_DANH','NOI_DUNG')),
 CONSTRAINT ck_shcn_muc_system CHECK((ma_muc IN('DIEM_DANH','KIEN_NGHI_SV','KIEN_NGHI_GV') AND la_muc_he_thong=1) OR (ma_muc NOT IN('DIEM_DANH','KIEN_NGHI_SV','KIEN_NGHI_GV') AND la_muc_he_thong=0)),
 -- DIEM_DANH order=1 is enforced at final application validation: temporary positive orders permit safe reorder.
 CONSTRAINT ck_shcn_muc_attendance CHECK(ma_muc<>'DIEM_DANH' OR (loai_muc='DIEM_DANH' AND bat_buoc=1 AND tieu_de=N'Điểm danh')),
 CONSTRAINT ck_shcn_muc_recommendation CHECK(ma_muc NOT IN('KIEN_NGHI_SV','KIEN_NGHI_GV') OR (loai_muc='NOI_DUNG' AND bat_buoc=0))
);
GO
CREATE TRIGGER shcn.tr_shcn_mau_bao_ve ON shcn.shcn_mau_bien_ban AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d JOIN inserted i ON i.id=d.id WHERE i.ma_dot<>d.ma_dot OR i.ngay_tao<>d.ngay_tao)
  THROW 51801,'Template provenance is immutable.',1;
 IF EXISTS(SELECT 1 FROM deleted WHERE trang_thai='DA_KHOA')
  THROW 51802,'Template is locked.',1;
 IF EXISTS(SELECT 1 FROM (SELECT ma_dot FROM inserted UNION SELECT ma_dot FROM deleted) x JOIN shcn.shcn_dot_sinh_hoat d WITH(HOLDLOCK) ON d.id=x.ma_dot
  WHERE d.trang_thai NOT IN('NHAP','BI_TRA_LAI') OR d.trang_thai_phan_cong IN('CHO_PHO_PHONG_DUYET','CHO_BGH_KY','CO_HIEU_LUC')
   OR d.ma_ho_so_duyet_dot IS NOT NULL OR d.ma_ho_so_tai_lieu_phan_cong IS NOT NULL OR d.ma_ho_so_trinh_lich IS NOT NULL
   OR d.ngay_phat_hanh IS NOT NULL OR d.ngay_huy IS NOT NULL OR d.ngay_cong_bo_lich IS NOT NULL)
  THROW 51803,'Round configuration is closed.',1;
END;
GO
CREATE TRIGGER shcn.tr_shcn_muc_bao_ve ON shcn.shcn_muc_bien_ban AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.id=d.id WHERE d.la_muc_he_thong=1 AND i.id IS NULL)
  THROW 51804,'System sections cannot be deleted.',1;
 IF EXISTS(SELECT 1 FROM deleted d JOIN inserted i ON i.id=d.id WHERE i.ma_mau<>d.ma_mau OR i.ma_muc COLLATE Latin1_General_100_BIN2<>d.ma_muc COLLATE Latin1_General_100_BIN2 OR i.ngay_tao<>d.ngay_tao)
  THROW 51805,'Section provenance is immutable.',1;
 IF EXISTS(SELECT 1 FROM deleted d JOIN inserted i ON i.id=d.id WHERE d.la_muc_he_thong=1 AND EXISTS(
  SELECT CONVERT(varbinary(max),i.tieu_de),i.loai_muc,i.la_muc_he_thong,i.bat_buoc,CONVERT(varbinary(max),i.cau_hinh_truong)
  EXCEPT SELECT CONVERT(varbinary(max),d.tieu_de),d.loai_muc,d.la_muc_he_thong,d.bat_buoc,CONVERT(varbinary(max),d.cau_hinh_truong)))
  THROW 51806,'System section configuration is immutable.',1;
 IF EXISTS(SELECT 1 FROM (SELECT ma_mau FROM inserted UNION SELECT ma_mau FROM deleted) x
  JOIN shcn.shcn_mau_bien_ban m WITH(HOLDLOCK) ON m.id=x.ma_mau JOIN shcn.shcn_dot_sinh_hoat d WITH(HOLDLOCK) ON d.id=m.ma_dot
  WHERE m.trang_thai='DA_KHOA' OR d.trang_thai NOT IN('NHAP','BI_TRA_LAI') OR d.trang_thai_phan_cong IN('CHO_PHO_PHONG_DUYET','CHO_BGH_KY','CO_HIEU_LUC')
   OR d.ma_ho_so_duyet_dot IS NOT NULL OR d.ma_ho_so_tai_lieu_phan_cong IS NOT NULL OR d.ma_ho_so_trinh_lich IS NOT NULL
   OR d.ngay_phat_hanh IS NOT NULL OR d.ngay_huy IS NOT NULL OR d.ngay_cong_bo_lich IS NOT NULL)
  THROW 51807,'Template or round is closed.',1;
END;
GO
