-- CV-02 audience configuration from the approved SHCN ERD. Owner applies explicitly.
-- No Foundation/DaoTao schema, permission catalogue or seed changes.
SET XACT_ABORT ON;
SET NOCOUNT ON;
CREATE TABLE shcn.shcn_doi_tuong_ap_dung (
 id bigint IDENTITY(1,1) NOT NULL,
 ma_dot bigint NOT NULL,
 loai_doi_tuong varchar(40) NOT NULL,
 ma_doi_tuong varchar(32) NOT NULL,
 hinh_thuc_cho_phep varchar(40) NOT NULL,
 khung_ngay_bat_dau date NULL,
 khung_ngay_ket_thuc date NULL,
 khung_tiet_cho_phep nvarchar(max) NULL,
 ngay_tao datetimeoffset(7) NOT NULL,
 ngay_cap_nhat datetimeoffset(7) NOT NULL,
 CONSTRAINT pk_shcn_doi_tuong PRIMARY KEY(id),
 CONSTRAINT fk_shcn_doi_tuong_dot FOREIGN KEY(ma_dot) REFERENCES shcn.shcn_dot_sinh_hoat(id),
 CONSTRAINT uq_shcn_doi_tuong UNIQUE(ma_dot,loai_doi_tuong,ma_doi_tuong),
 CONSTRAINT ck_shcn_doi_tuong_loai CHECK(loai_doi_tuong IN('LOP','KHOA','KHOA_HOC')),
 CONSTRAINT ck_shcn_doi_tuong_hinh_thuc CHECK(hinh_thuc_cho_phep IN('OFFLINE','ONLINE','CA_HAI')),
 CONSTRAINT ck_shcn_doi_tuong_ngay CHECK((khung_ngay_bat_dau IS NULL AND khung_ngay_ket_thuc IS NULL) OR (khung_ngay_bat_dau IS NOT NULL AND khung_ngay_ket_thuc IS NOT NULL AND khung_ngay_ket_thuc>=khung_ngay_bat_dau)),
 CONSTRAINT ck_shcn_doi_tuong_tiet CHECK(khung_tiet_cho_phep IS NULL OR (ISJSON(khung_tiet_cho_phep)=1 AND LEFT(LTRIM(khung_tiet_cho_phep),1)='['))
);
GO
CREATE TRIGGER shcn.tr_shcn_doi_tuong_bao_ve ON shcn.shcn_doi_tuong_ap_dung AFTER INSERT,UPDATE,DELETE AS
BEGIN
 SET NOCOUNT ON;
 IF EXISTS(
  SELECT 1 FROM shcn.shcn_dot_sinh_hoat d WITH(UPDLOCK,HOLDLOCK)
  JOIN(SELECT ma_dot FROM inserted UNION SELECT ma_dot FROM deleted) a ON a.ma_dot=d.id
  WHERE d.trang_thai NOT IN('NHAP','BI_TRA_LAI') OR d.trang_thai_phan_cong IN('CHO_PHO_PHONG_DUYET','CHO_BGH_KY','CO_HIEU_LUC')
    OR d.ma_ho_so_duyet_dot IS NOT NULL OR d.ma_ho_so_tai_lieu_phan_cong IS NOT NULL OR d.ma_ho_so_trinh_lich IS NOT NULL
    OR d.ngay_phat_hanh IS NOT NULL OR d.ngay_huy IS NOT NULL OR d.ngay_cong_bo_lich IS NOT NULL
 ) THROW 51901,'SHCN audience configuration is closed.',1;
END;
GO
