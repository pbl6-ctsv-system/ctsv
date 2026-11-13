-- Derived without schema changes from nghiepvu/07-sql-server/ctsv_db.sql.
-- Flyway owns target selection, transaction and history. Do not edit after application.
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
CREATE TABLE [dbo].[he_thong_ngoai] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma] nvarchar(128) NOT NULL,
    [ten] nvarchar(512) NULL,
    [loai_he_thong] nvarchar(128) NULL,
    [che_do_ket_noi] nvarchar(128) NULL,
    [url_goc] nvarchar(2048) NULL,
    [tham_chieu_thong_tin_xac_thuc] nvarchar(2048) NULL,
    [dang_hoat_dong] bit NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    CONSTRAINT [pk_he_thong_ngoai] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[nguoi_dung] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [dia_chi_email] nvarchar(254) NOT NULL,
    [ma_bam_mat_khau] nvarchar(128) NULL,
    [ho_ten] nvarchar(512) NULL,
    [so_dien_thoai] nvarchar(64) NULL,
    [dang_hoat_dong] bit NULL,
    [thoi_diem_dang_nhap_gan_nhat] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [thoi_diem_xoa] datetimeoffset(7) NULL,
    CONSTRAINT [pk_nguoi_dung] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[dinh_danh_dang_nhap] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [nguoi_dung_id] bigint NOT NULL,
    [ma_nha_cung_cap] nvarchar(512) NOT NULL,
    [ma_chu_the_dang_nhap] nvarchar(128) NOT NULL,
    [thoi_diem_xac_minh] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo_gan_nhat] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    CONSTRAINT [pk_dinh_danh_dang_nhap] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[vai_tro] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma] nvarchar(128) NOT NULL,
    [ten] nvarchar(512) NULL,
    [mo_ta] nvarchar(max) NULL,
    [dang_hoat_dong] bit NULL,
    CONSTRAINT [pk_vai_tro] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[phan_quyen_nguoi_dung] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [nguoi_dung_id] bigint NOT NULL,
    [vai_tro_id] bigint NOT NULL,
    [ma_phan_he] nvarchar(128) NULL,
    [don_vi_id] bigint NULL,
    [bao_gom_don_vi_con] bit NULL,
    [hieu_luc_tu] datetimeoffset(7) NULL,
    [hieu_luc_den] datetimeoffset(7) NULL,
    [nguoi_cap_quyen_id] bigint NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    CONSTRAINT [pk_phan_quyen_nguoi_dung] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[don_vi] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma] nvarchar(128) NOT NULL,
    [ten] nvarchar(512) NULL,
    [ma_bo_phan_nguon] nvarchar(512) NULL,
    [loai_don_vi] nvarchar(128) NULL,
    [don_vi_cha_id] bigint NULL,
    [dang_hoat_dong] bit NULL,
    [he_thong_nguon_id] bigint NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [luu_tru_nguon] nvarchar(512) NULL,
    [is_us_nguon] nvarchar(512) NULL,
    CONSTRAINT [pk_don_vi] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[dan_toc] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma] nvarchar(128) NULL,
    [ten] nvarchar(512) NULL,
    [he_thong_nguon_id] bigint NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    CONSTRAINT [pk_dan_toc] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[quoc_tich] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma] nvarchar(128) NULL,
    [ten] nvarchar(512) NULL,
    [he_thong_nguon_id] bigint NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    CONSTRAINT [pk_quoc_tich] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[dia_phuong] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma] nvarchar(128) NULL,
    [ten] nvarchar(512) NULL,
    [he_thong_nguon_id] bigint NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    CONSTRAINT [pk_dia_phuong] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[khoa_hoc] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma] nvarchar(128) NULL,
    [nam_tuyen_sinh_nguon] nvarchar(512) NULL,
    [he_thong_nguon_id] bigint NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    CONSTRAINT [pk_khoa_hoc] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[hoc_ky] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma] nvarchar(128) NOT NULL,
    [ten] nvarchar(512) NULL,
    [ngay_day_nguon] nvarchar(512) NULL,
    [tuan_dau_nguon] nvarchar(512) NULL,
    [so_tuan_nguon] nvarchar(512) NULL,
    [so_thu_tu] int NULL,
    [ngay_bat_dau] date NULL,
    [ngay_ket_thuc] date NULL,
    [trang_thai_xac_minh_moc] nvarchar(128) NULL,
    [can_cu_xac_minh_moc] nvarchar(max) NULL,
    [he_thong_nguon_id] bigint NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [ngay_dau_nguon] nvarchar(512) NULL,
    [ma_nganh_nguon] nvarchar(512) NULL,
    [nam_hoc_nguon] nvarchar(512) NULL,
    CONSTRAINT [pk_hoc_ky] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[nganh_quoc_gia] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma] nvarchar(128) NULL,
    [ten] nvarchar(512) NULL,
    [cap_dao_tao_nguon] nvarchar(512) NULL,
    [he_thong_nguon_id] bigint NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    CONSTRAINT [pk_nganh_quoc_gia] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[nganh_noi_bo] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma] nvarchar(128) NULL,
    [ten] nvarchar(512) NULL,
    [nganh_quoc_gia_id] bigint NULL,
    [cap_dao_tao_nguon] nvarchar(512) NULL,
    [khoi_nganh_nguon] nvarchar(512) NULL,
    [he_thong_nguon_id] bigint NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [nganh_quoc_gia_id_goc_nguon] nvarchar(512) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [trang_thai_huy_nguon] nvarchar(512) NULL,
    [ky_hieu_nguon] nvarchar(512) NULL,
    CONSTRAINT [pk_nganh_noi_bo] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[chuong_trinh_dao_tao] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma] nvarchar(128) NULL,
    [ten] nvarchar(512) NULL,
    [nganh_noi_bo_id] bigint NULL,
    [nganh_quoc_gia_id] bigint NULL,
    [cap_dao_tao_so_nguon] nvarchar(512) NULL,
    [so_tin_chi] decimal(28,10) NULL,
    [so_hoc_ky] decimal(28,10) NULL,
    [ngon_ngu_dao_tao] nvarchar(128) NULL,
    [bat_dau_nguon] nvarchar(512) NULL,
    [ket_thuc_nguon] nvarchar(512) NULL,
    [hoc_ky_bat_dau_id] bigint NULL,
    [hoc_ky_ket_thuc_id] bigint NULL,
    [he_thong_nguon_id] bigint NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [nganh_noi_bo_id_goc_nguon] nvarchar(512) NULL,
    [nganh_quoc_gia_id_goc_nguon] nvarchar(512) NULL,
    [hoc_ky_bat_dau_id_goc_nguon] nvarchar(512) NULL,
    [hoc_ky_ket_thuc_id_goc_nguon] nvarchar(512) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [so_tin_chi_goc_nguon] nvarchar(512) NULL,
    [so_hoc_ky_goc_nguon] nvarchar(512) NULL,
    [ky_hieu_nguon] nvarchar(512) NULL,
    [dang_su_dung_nguon] nvarchar(512) NULL,
    [cap_dao_tao_nguon] nvarchar(512) NULL,
    [ten_nganh_moi_nguon] nvarchar(512) NULL,
    [loai_ctdt_nguon] nvarchar(512) NULL,
    CONSTRAINT [pk_chuong_trinh_dao_tao] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[lop_sinh_hoat] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma] nvarchar(128) NULL,
    [ten] nvarchar(512) NULL,
    [chuong_trinh_dao_tao_id] bigint NULL,
    [khoa_quan_ly_id] bigint NULL,
    [hinh_thuc_dao_tao_nguon] nvarchar(512) NULL,
    [cap_dao_tao_nguon] nvarchar(512) NULL,
    [khoa_hoc_id] bigint NULL,
    [he_thong_nguon_id] bigint NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [chuong_trinh_dao_tao_id_goc_nguon] nvarchar(512) NULL,
    [khoa_quan_ly_id_goc_nguon] nvarchar(512) NULL,
    [khoa_hoc_id_goc_nguon] nvarchar(512) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [ma_khung_nguon] nvarchar(512) NULL,
    [lop_ghep_nguon] nvarchar(512) NULL,
    CONSTRAINT [pk_lop_sinh_hoat] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[sinh_vien] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [nguoi_dung_id] bigint NULL,
    [mssv] nvarchar(64) NOT NULL,
    [ho_ten] nvarchar(512) NULL,
    [dia_chi_email] nvarchar(254) NULL,
    [so_dien_thoai] nvarchar(64) NULL,
    [ngay_sinh] date NULL,
    [gioi_tinh] nvarchar(128) NULL,
    [noi_sinh_id] bigint NULL,
    [dan_toc_id] bigint NULL,
    [quoc_tich_id] bigint NULL,
    [so_cccd] nvarchar(64) NULL,
    [lop_hien_tai_id] bigint NULL,
    [khoa_quan_ly_hien_tai_id] bigint NULL,
    [khoa_hoc_hien_tai_id] bigint NULL,
    [chuong_trinh_hien_tai_id] bigint NULL,
    [trang_thai_hoc_tap_nguon] nvarchar(512) NULL,
    [cap_dao_tao_nguon] nvarchar(512) NULL,
    [hoc_ky_ap_dung_nguon_id] bigint NULL,
    [hoc_ky_bat_dau_nguon_id] bigint NULL,
    [hoc_ky_ket_thuc_nguon_id] bigint NULL,
    [ton_giao] nvarchar(128) NULL,
    [dia_chi_thuong_tru] nvarchar(max) NULL,
    [dia_chi_tam_tru] nvarchar(max) NULL,
    [ten_ngan_hang] nvarchar(512) NULL,
    [so_tai_khoan_nh] nvarchar(64) NULL,
    [chu_tai_khoan_nh] nvarchar(128) NULL,
    [thoi_diem_xoa] datetimeoffset(7) NULL,
    [he_thong_nguon_id] bigint NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [noi_sinh_id_goc_nguon] nvarchar(512) NULL,
    [dan_toc_id_goc_nguon] nvarchar(512) NULL,
    [quoc_tich_id_goc_nguon] nvarchar(512) NULL,
    [lop_hien_tai_id_goc_nguon] nvarchar(512) NULL,
    [khoa_quan_ly_hien_tai_id_goc_nguon] nvarchar(512) NULL,
    [khoa_hoc_hien_tai_id_goc_nguon] nvarchar(512) NULL,
    [chuong_trinh_hien_tai_id_goc_nguon] nvarchar(512) NULL,
    [hoc_ky_ap_dung_nguon_id_goc_nguon] nvarchar(512) NULL,
    [hoc_ky_bat_dau_nguon_id_goc_nguon] nvarchar(512) NULL,
    [hoc_ky_ket_thuc_nguon_id_goc_nguon] nvarchar(512) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [lan_sua_doi_danh_ba] bigint NULL,
    [ma_ho_so_nguon] nvarchar(512) NULL,
    [ngay_nhap_hoc_nguon] nvarchar(512) NULL,
    [he_dao_tao_nguon] nvarchar(512) NULL,
    [ngay_luu_nguon] nvarchar(512) NULL,
    [loai_luu_nguon] nvarchar(512) NULL,
    [hoc_ky_han_hoc_nguon_id] bigint NULL,
    [hoc_ky_han_hoc_nguon_id_goc_nguon] nvarchar(512) NULL,
    [clc_nguon] nvarchar(512) NULL,
    CONSTRAINT [pk_sinh_vien] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[qua_trinh_theo_hoc] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [sinh_vien_id] bigint NOT NULL,
    [chuong_trinh_dao_tao_id] bigint NULL,
    [khoa_hoc_id] bigint NULL,
    [hoc_ky_bat_dau_id] bigint NULL,
    [hoc_ky_ket_thuc_nguon_id] bigint NULL,
    [ma_qua_trinh_hoc] nvarchar(128) NOT NULL,
    [khoa_doi_chieu_nguon] nvarchar(512) NULL,
    [trang_thai_doi_chieu] nvarchar(128) NULL,
    [la_chinh] bit NULL,
    [trang_thai_hien_tai] nvarchar(128) NULL,
    [hoc_ky_ap_dung_trang_thai_id] bigint NULL,
    [thoi_diem_bat_dau] date NULL,
    [ngay_ket_thuc_du_kien] date NULL,
    [ngay_ket_thuc_thuc_te] date NULL,
    [can_cu_xac_minh] nvarchar(max) NULL,
    [nguoi_xac_minh_id] bigint NULL,
    [thoi_diem_xac_minh] datetimeoffset(7) NULL,
    [he_thong_nguon_id] bigint NULL,
    [ho_so_sv_nguon_id] nvarchar(128) NULL,
    [nganh_nguon_id] nvarchar(128) NULL,
    [khoa_hoc_nguon_id] nvarchar(128) NULL,
    [hoc_ky_bat_dau_nguon_id] nvarchar(128) NULL,
    [tep_can_cu_id] bigint NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    CONSTRAINT [pk_qua_trinh_theo_hoc] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[lich_su_trang_thai_sv] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [qua_trinh_hoc_id] bigint NULL,
    [hoc_ky_ap_dung_id] bigint NULL,
    [trang_thai] nvarchar(128) NULL,
    [ngay_bat_dau_hieu_luc] date NULL,
    [ngay_ket_thuc_hieu_luc] date NULL,
    [he_thong_nguon_id] bigint NULL,
    [ban_ghi_nguon_id] nvarchar(128) NULL,
    [phien_ban_nguon] nvarchar(512) NULL,
    [so_qd_nguon] nvarchar(512) NULL,
    [ngay_qd_nguon] date NULL,
    [ly_do] nvarchar(max) NULL,
    [can_cu_xac_minh] nvarchar(max) NULL,
    [nguoi_xac_minh_id] bigint NULL,
    [thoi_diem_xac_minh] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_ghi_nhan] datetimeoffset(7) NULL,
    CONSTRAINT [pk_lich_su_trang_thai_sv] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[qua_trinh_thuoc_lop] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [qua_trinh_hoc_id] bigint NULL,
    [lop_id] bigint NULL,
    [hieu_luc_tu] date NULL,
    [hieu_luc_den] date NULL,
    [he_thong_nguon_id] bigint NULL,
    [ban_ghi_nguon_id] nvarchar(128) NULL,
    [can_cu_xac_minh] nvarchar(max) NULL,
    [nguoi_xac_minh_id] bigint NULL,
    [thoi_diem_xac_minh] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    CONSTRAINT [pk_qua_trinh_thuoc_lop] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[phan_cong_can_su_lop] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [qua_trinh_thuoc_lop_id] bigint NULL,
    [vai_tro_can_su] nvarchar(128) NULL,
    [hieu_luc_tu] date NULL,
    [hieu_luc_den] date NULL,
    [nguoi_bo_nhiem_id] bigint NULL,
    [can_cu_xac_minh] nvarchar(max) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    CONSTRAINT [pk_phan_cong_can_su_lop] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[can_bo] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [nguoi_dung_id] bigint NULL,
    [ma_cb] nvarchar(128) NOT NULL,
    [ho_ten] nvarchar(512) NULL,
    [dia_chi_email] nvarchar(254) NULL,
    [so_dien_thoai] nvarchar(64) NULL,
    [he_thong_nguon_id] bigint NULL,
    [la_giang_vien] bit NULL,
    [ma_gv] nvarchar(128) NULL,
    [hoc_ham] nvarchar(128) NULL,
    [can_bo_ben_ngoai_id] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [don_vi_nguon_id] bigint NULL,
    [don_vi_nguon_id_goc_nguon] nvarchar(512) NULL,
    [ma_chuc_vu_nguon] nvarchar(512) NULL,
    [huu_tri_nguon] nvarchar(512) NULL,
    [het_hieu_luc_nguon] nvarchar(512) NULL,
    [thuoc_don_vi_nguon] nvarchar(512) NULL,
    [ma_bo_phan_nguon] nvarchar(512) NULL,
    CONSTRAINT [pk_can_bo] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[qua_trinh_cong_tac] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [can_bo_id] bigint NULL,
    [don_vi_id] bigint NULL,
    [ma_chuc_vu] nvarchar(128) NULL,
    [ten_chuc_vu] nvarchar(512) NULL,
    [trang_thai_cong_tac] nvarchar(128) NULL,
    [la_chinh] bit NULL,
    [hieu_luc_tu] date NULL,
    [hieu_luc_den] date NULL,
    [he_thong_nguon_id] bigint NULL,
    [ban_ghi_nguon_id] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    CONSTRAINT [pk_qua_trinh_cong_tac] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[phan_cong_co_van_lop] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [lop_id] bigint NULL,
    [can_bo_id] bigint NULL,
    [hoc_ky_id] bigint NULL,
    [ngay_gan_nguon] nvarchar(512) NULL,
    [nguoi_gan_nguon] nvarchar(512) NULL,
    [vai_tro_co_van] nvarchar(128) NULL,
    [hieu_luc_tu] date NULL,
    [hieu_luc_den] date NULL,
    [he_thong_nguon_id] bigint NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [lop_id_goc_nguon] nvarchar(512) NULL,
    [hoc_ky_id_goc_nguon] nvarchar(512) NULL,
    [can_bo_id_goc_nguon] nvarchar(512) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [can_bo_chu_nhiem_id] bigint NULL,
    [can_bo_chu_nhiem_id_goc_nguon] nvarchar(512) NULL,
    CONSTRAINT [pk_phan_cong_co_van_lop] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[du_lieu_hoc_vu_dong_bo] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [sinh_vien_id] bigint NULL,
    [hoc_ky_id] bigint NULL,
    [chuong_trinh_dao_tao_id] bigint NULL,
    [qua_trinh_hoc_id] bigint NULL,
    [dot_dong_bo_id] bigint NULL,
    [nam_hoc_nguon] nvarchar(512) NULL,
    [hoc_ky_nguon] nvarchar(512) NULL,
    [hp_dang_ky] decimal(28,10) NULL,
    [hp_bat_buoc] decimal(28,10) NULL,
    [so_tc_hoc_lai] decimal(28,10) NULL,
    [diem_tb_hoc_tap_he_10] decimal(28,10) NULL,
    [diem_tb_hoc_bong] decimal(28,10) NULL,
    [xep_loai_ck] nvarchar(128) NULL,
    [hp_tich_luy] decimal(28,10) NULL,
    [diem_tb_tich_luy] decimal(28,10) NULL,
    [diem_tb_tich_luy_he_10] decimal(28,10) NULL,
    [xep_loai] nvarchar(128) NULL,
    [kl_dang_ky] decimal(28,10) NULL,
    [kl_hoc_lai] decimal(28,10) NULL,
    [tc_khung] decimal(28,10) NULL,
    [tc_tien_do] decimal(28,10) NULL,
    [tc_ty_le] decimal(28,10) NULL,
    [trang_thai_lien_ket_qua_trinh] nvarchar(128) NULL,
    [he_thong_nguon_id] bigint NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(128) NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sinh_vien_id_goc_nguon] nvarchar(512) NULL,
    [hoc_ky_id_goc_nguon] nvarchar(512) NULL,
    [chuong_trinh_dao_tao_id_goc_nguon] nvarchar(512) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [hp_dang_ky_goc_nguon] nvarchar(512) NULL,
    [hp_bat_buoc_goc_nguon] nvarchar(512) NULL,
    [so_tc_hoc_lai_goc_nguon] nvarchar(512) NULL,
    [diem_tb_hoc_tap_he_10_goc_nguon] nvarchar(512) NULL,
    [diem_tb_hoc_bong_goc_nguon] nvarchar(512) NULL,
    [hp_tich_luy_goc_nguon] nvarchar(512) NULL,
    [diem_tb_tich_luy_goc_nguon] nvarchar(512) NULL,
    [diem_tb_tich_luy_he_10_goc_nguon] nvarchar(512) NULL,
    [kl_dang_ky_goc_nguon] nvarchar(512) NULL,
    [kl_hoc_lai_goc_nguon] nvarchar(512) NULL,
    [tc_khung_goc_nguon] nvarchar(512) NULL,
    [tc_tien_do_goc_nguon] nvarchar(512) NULL,
    [tc_ty_le_goc_nguon] nvarchar(512) NULL,
    [diem_ren_luyen_nguon] nvarchar(512) NULL,
    [thoi_hoc_nguon] nvarchar(512) NULL,
    [ngung_hoc_nguon] nvarchar(512) NULL,
    [so_ky_ngung_nguon] nvarchar(512) NULL,
    [ma_ky_hoc_nguon] nvarchar(512) NULL,
    [dung_hoc_nguon] nvarchar(512) NULL,
    CONSTRAINT [pk_du_lieu_hoc_vu_dong_bo] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[tep_dinh_kem] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ten_cloud] nvarchar(128) NULL,
    [ma_tai_nguyen] nvarchar(128) NULL,
    [ma_cong_khai] nvarchar(512) NULL,
    [loai_tai_nguyen] nvarchar(128) NULL,
    [kieu_phan_phoi] nvarchar(128) NULL,
    [phien_ban_luu_tru] bigint NULL,
    [dinh_dang_tep] nvarchar(128) NULL,
    [ten_tep] nvarchar(512) NULL,
    [kieu_noi_dung] nvarchar(2000) NULL,
    [kich_thuoc_tep] bigint NULL,
    [sha256] varchar(64) NULL,
    [trang_thai_luu_tru] nvarchar(128) NULL,
    [trang_thai_quet_tep] nvarchar(128) NULL,
    [pham_vi_hien_thi] nvarchar(128) NULL,
    [nguoi_so_huu_id] bigint NULL,
    [nguoi_tai_len_id] bigint NULL,
    [thoi_diem_tai_len] datetimeoffset(7) NULL,
    [luu_tru_den] datetimeoffset(7) NULL,
    [thoi_diem_xoa] datetimeoffset(7) NULL,
    CONSTRAINT [pk_tep_dinh_kem] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[van_ban] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma_van_ban] uniqueidentifier NOT NULL,
    [ma_phan_he] nvarchar(128) NULL,
    [loai_van_ban] nvarchar(128) NULL,
    [tieu_de] nvarchar(512) NULL,
    [don_vi_so_huu_id] bigint NULL,
    [nguoi_tao_id] bigint NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    CONSTRAINT [pk_van_ban] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[phien_ban_van_ban] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [van_ban_id] bigint NOT NULL,
    [so_phien_ban] int NOT NULL,
    [tieu_de_tai_thoi_diem_chot] nvarchar(512) NULL,
    [tep_goc_id] bigint NULL,
    [ma_tham_chieu_nghiep_vu] nvarchar(512) NULL,
    [lan_sua_doi_nghiep_vu] int NULL,
    [sha256_tap_du_lieu] varchar(64) NULL,
    [trang_thai_noi_dung] nvarchar(2000) NULL,
    [nguoi_doi_chieu_id] bigint NULL,
    [thoi_diem_doi_chieu] datetimeoffset(7) NULL,
    [nguoi_chot_id] bigint NULL,
    [thoi_diem_chot] datetimeoffset(7) NULL,
    [nguoi_tao_id] bigint NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    CONSTRAINT [pk_phien_ban_van_ban] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[ghi_nhan_xem_van_ban] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [phien_ban_van_ban_id] bigint NULL,
    [nguoi_nhan_xet_id] bigint NULL,
    [loai_nhan_xet] nvarchar(128) NULL,
    [ket_qua_nhan_xet] nvarchar(128) NULL,
    [ghi_chu] nvarchar(max) NULL,
    [thoi_diem_nhan_xet] datetimeoffset(7) NULL,
    CONSTRAINT [pk_ghi_nhan_xem_van_ban] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[yeu_cau_ky] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma_yeu_cau] uniqueidentifier NOT NULL,
    [phien_ban_van_ban_id] bigint NULL,
    [he_thong_cung_cap_id] bigint NULL,
    [che_do_thuc_hien] nvarchar(128) NULL,
    [quy_tac_uy_quyen] nvarchar(128) NULL,
    [van_ban_ben_ngoai_id] uniqueidentifier NULL,
    [danh_muc_ca_id] uniqueidentifier NULL,
    [trang_thai_van_ban_phia_ca] nvarchar(128) NULL,
    [phien_ban_van_ban_phia_ca] int NULL,
    [sha256_tep_goc_phia_ca] varchar(64) NULL,
    [trang_thai] nvarchar(128) NULL,
    [phien_ban_dong] int NULL,
    [nguoi_yeu_cau_id] bigint NULL,
    [thoi_diem_yeu_cau] datetimeoffset(7) NULL,
    [thoi_diem_bat_dau] datetimeoffset(7) NULL,
    [thoi_diem_hoan_tat] datetimeoffset(7) NULL,
    [nguoi_huy_id] bigint NULL,
    [thoi_diem_huy] datetimeoffset(7) NULL,
    [ly_do_huy] nvarchar(max) NULL,
    [ma_loi_gan_nhat] nvarchar(128) NULL,
    [noi_dung_loi_gan_nhat] nvarchar(max) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [ban_tin_gui_id] bigint NULL,
    [phien_ban_adapter] nvarchar(128) NULL,
    CONSTRAINT [pk_yeu_cau_ky] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[buoc_ky] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [yeu_cau_ky_id] bigint NOT NULL,
    [nguoi_dung_noi_bo_id] bigint NULL,
    [can_bo_id] bigint NULL,
    [thu_tu_ky] int NOT NULL,
    [vai_tro_nguoi_ky] nvarchar(128) NULL,
    [ten_nguoi_ky] nvarchar(512) NULL,
    [email_nguoi_ky] nvarchar(254) NULL,
    [chuc_danh_nguoi_ky] nvarchar(512) NULL,
    [ma_nguoi_ky_doi_tac] nvarchar(128) NULL,
    [nguoi_dung_ca_id] uniqueidentifier NULL,
    [chung_thu_id] uniqueidentifier NULL,
    [so_seri_chung_thu] nvarchar(128) NULL,
    [lan_kiem_tra_chung_thu_id] bigint NULL,
    [thoi_diem_kiem_tra_chung_thu] datetimeoffset(7) NULL,
    [mau_chu_ky_id] uniqueidentifier NULL,
    [tep_anh_chu_ky_id] bigint NULL,
    [nguon_anh] nvarchar(128) NULL,
    [so_trang] int NULL,
    [toa_do_x] decimal(28,10) NULL,
    [toa_do_y] decimal(28,10) NULL,
    [chieu_rong] decimal(28,10) NULL,
    [chieu_cao] decimal(28,10) NULL,
    [trang_thai] nvarchar(128) NULL,
    [ly_do_tu_choi] nvarchar(max) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    CONSTRAINT [pk_buoc_ky] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[uy_quyen_ky] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [buoc_ky_id] bigint NULL,
    [loai_uy_quyen] nvarchar(128) NULL,
    [email_nguoi_duoc_uy_quyen_ky] nvarchar(254) NULL,
    [tep_minh_chung_id] bigint NULL,
    [tham_chieu_minh_chung] nvarchar(512) NULL,
    [trang_thai] nvarchar(128) NULL,
    [thoi_diem_uy_quyen] datetimeoffset(7) NULL,
    [thoi_diem_het_han] datetimeoffset(7) NULL,
    [nguoi_ghi_nhan_id] bigint NULL,
    [thoi_diem_ghi_nhan] datetimeoffset(7) NULL,
    [thoi_diem_thu_hoi] datetimeoffset(7) NULL,
    [ly_do_thu_hoi] nvarchar(max) NULL,
    CONSTRAINT [pk_uy_quyen_ky] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[lan_goi_dich_vu_ky] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [yeu_cau_ky_id] bigint NULL,
    [buoc_ky_id] bigint NULL,
    [uy_quyen_id] bigint NULL,
    [loai_thao_tac] nvarchar(128) NULL,
    [so_thu_tu_lan_goi] int NULL,
    [ma_lien_ket_yeu_cau] uniqueidentifier NULL,
    [sha256_yeu_cau] varchar(64) NULL,
    [sha256_dau_vao_du_kien] varchar(64) NULL,
    [ket_luan] nvarchar(128) NULL,
    [ma_http] int NULL,
    [co_the_da_thay_doi_ben_ngoai] bit NULL,
    [ma_loi] nvarchar(128) NULL,
    [noi_dung_loi] nvarchar(max) NULL,
    [thoi_diem_bat_dau] datetimeoffset(7) NULL,
    [thoi_diem_ket_thuc] datetimeoffset(7) NULL,
    [phien_ban_adapter] nvarchar(128) NULL,
    [phien_ban_hop_dong] nvarchar(128) NULL,
    [lan_kiem_tra_chung_thu_id] bigint NULL,
    [tep_dau_vao_id] bigint NULL,
    [van_ban_ca_luc_goi_id] uniqueidentifier NULL,
    [phien_ban_van_ban_ca_luc_goi] int NULL,
    [van_ban_ca_phan_hoi_id] uniqueidentifier NULL,
    [chu_ky_ca_phan_hoi_id] uniqueidentifier NULL,
    [phien_ban_van_ban_ca_phan_hoi] int NULL,
    [sha256_phan_hoi] varchar(64) NULL,
    [sha256_pdf_phan_hoi] varchar(64) NULL,
    [trang_thai_ca_phan_hoi] nvarchar(128) NULL,
    CONSTRAINT [pk_lan_goi_dich_vu_ky] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[bien_nhan_ky] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [yeu_cau_ky_id] bigint NULL,
    [buoc_ky_id] bigint NULL,
    [lan_goi_id] bigint NULL,
    [he_thong_cung_cap_id] bigint NOT NULL,
    [chu_ky_ben_ngoai_id] uniqueidentifier NOT NULL,
    [van_ban_ben_ngoai_id] uniqueidentifier NULL,
    [nguoi_thao_tac_phia_ca_id] uniqueidentifier NULL,
    [chung_thu_id] uniqueidentifier NULL,
    [so_seri_chung_thu] nvarchar(128) NULL,
    [ten_nguoi_ky] nvarchar(512) NULL,
    [email_nguoi_ky] nvarchar(254) NULL,
    [ma_nguoi_ky_doi_tac] nvarchar(128) NULL,
    [ung_dung_nguon] nvarchar(512) NULL,
    [sha256_van_ban] varchar(64) NULL,
    [sha256_pdf_da_ky] varchar(64) NULL,
    [trang_thai_chu_ky_phia_ca] nvarchar(128) NULL,
    [duoc_chon] bit NULL,
    [thoi_diem_ky] datetimeoffset(7) NULL,
    [thoi_diem_tiep_nhan] datetimeoffset(7) NULL,
    [phien_ban_van_ban_ca] int NULL,
    [phien_ban_hop_dong] nvarchar(128) NULL,
    [sha256_phan_hoi] varchar(64) NULL,
    CONSTRAINT [pk_bien_nhan_ky] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[tep_ket_qua_ky] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [yeu_cau_ky_id] bigint NULL,
    [bien_nhan_ky_id] bigint NULL,
    [tep_dinh_kem_id] bigint NULL,
    [loai_tep_ket_qua] nvarchar(128) NULL,
    [trang_thai_toan_ven] nvarchar(128) NULL,
    [trang_thai_kiem_tra_chu_ky] nvarchar(128) NULL,
    [phuong_phap_kiem_tra] nvarchar(128) NULL,
    [lan_kiem_tra_tep_id] bigint NULL,
    [thoi_diem_tai_ve] datetimeoffset(7) NULL,
    [thoi_diem_xac_minh] datetimeoffset(7) NULL,
    CONSTRAINT [pk_tep_ket_qua_ky] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[yeu_cau_chung_thu_ca] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_cung_cap_id] bigint NULL,
    [buoc_ky_id] bigint NULL,
    [yeu_cau_ben_ngoai_id] uniqueidentifier NULL,
    [nguoi_dung_ca_id] uniqueidentifier NULL,
    [email_nguoi_ky] nvarchar(254) NULL,
    [loai_yeu_cau] nvarchar(128) NULL,
    [so_ngay_hieu_luc] int NULL,
    [ten_chu_the_chung_thu] nvarchar(512) NULL,
    [ten_don_vi_hien_thi] nvarchar(512) NULL,
    [ly_do] nvarchar(max) NULL,
    [trang_thai] nvarchar(128) NULL,
    [nhan_xet_quan_tri] nvarchar(max) NULL,
    [thoi_diem_xu_ly] datetimeoffset(7) NULL,
    [thoi_diem_kiem_tra_gan_nhat] datetimeoffset(7) NULL,
    [thoi_diem_kiem_tra_tiep] datetimeoffset(7) NULL,
    [nguoi_yeu_cau_id] bigint NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    CONSTRAINT [pk_yeu_cau_chung_thu_ca] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[thong_bao] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma_phan_he] nvarchar(128) NULL,
    [tieu_de] nvarchar(512) NULL,
    [noi_dung] nvarchar(max) NULL,
    [nhom_phan_loai] nvarchar(128) NULL,
    [loai_doi_tuong] nvarchar(128) NULL,
    [doi_tuong_id] nvarchar(128) NULL,
    [lan_sua_doi_doi_tuong] int NULL,
    [url_thao_tac] nvarchar(2048) NULL,
    [han_xu_ly] datetimeoffset(7) NULL,
    [la_quan_trong] bit NULL,
    [trang_thai] nvarchar(128) NULL,
    [thoi_diem_cong_bo] datetimeoffset(7) NULL,
    [nguoi_tao_id] bigint NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    CONSTRAINT [pk_thong_bao] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[doi_tuong_nhan_thong_bao] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [thong_bao_id] bigint NOT NULL,
    [loai_doi_tuong_nhan] nvarchar(128) NOT NULL,
    [vai_tro_id] bigint NULL,
    [ma_phan_he] nvarchar(128) NULL,
    [don_vi_id] bigint NULL,
    [lop_id] bigint NULL,
    [nguoi_dung_id] bigint NULL,
    CONSTRAINT [pk_doi_tuong_nhan_thong_bao] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[nguoi_nhan_thong_bao] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [thong_bao_id] bigint NOT NULL,
    [nguoi_dung_id] bigint NOT NULL,
    [thoi_diem_chuyen_thanh_cong] datetimeoffset(7) NULL,
    [thoi_diem_doc] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    CONSTRAINT [pk_nguoi_nhan_thong_bao] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[cong_viec] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma_phan_he] nvarchar(128) NULL,
    [loai_cong_viec] nvarchar(128) NULL,
    [tieu_de] nvarchar(512) NULL,
    [mo_ta] nvarchar(max) NULL,
    [nguoi_duoc_giao_id] bigint NULL,
    [vai_tro_duoc_giao_id] bigint NULL,
    [don_vi_id] bigint NULL,
    [nguoi_nhan_xu_ly_id] bigint NULL,
    [thoi_diem_nhan_xu_ly] datetimeoffset(7) NULL,
    [loai_doi_tuong] nvarchar(128) NULL,
    [doi_tuong_id] nvarchar(128) NULL,
    [lan_sua_doi_doi_tuong] int NULL,
    [url_thao_tac] nvarchar(2048) NULL,
    [han_xu_ly] datetimeoffset(7) NULL,
    [trang_thai] nvarchar(128) NULL,
    [muc_uu_tien] nvarchar(128) NULL,
    [phien_ban_dong] int NULL,
    [nguoi_hoan_tat_id] bigint NULL,
    [thoi_diem_hoan_tat] datetimeoffset(7) NULL,
    [ket_qua] nvarchar(128) NULL,
    [ghi_chu_ket_qua] nvarchar(max) NULL,
    [nguoi_tao_id] bigint NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    CONSTRAINT [pk_cong_viec] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[nhat_ky_thao_tac] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [su_kien_id] uniqueidentifier NOT NULL,
    [loai_chu_the_thao_tac] nvarchar(128) NULL,
    [nguoi_dung_id] bigint NULL,
    [he_thong_ngoai_id] bigint NULL,
    [hanh_dong] nvarchar(128) NULL,
    [loai_doi_tuong] nvarchar(128) NULL,
    [doi_tuong_id] nvarchar(128) NULL,
    [lan_sua_doi_doi_tuong] int NULL,
    [ma_lien_ket_yeu_cau] uniqueidentifier NULL,
    [ly_do] nvarchar(max) NULL,
    [dia_chi_ip] nvarchar(64) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    CONSTRAINT [pk_nhat_ky_thao_tac] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[cau_hinh_he_thong] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [khoa_cau_hinh] nvarchar(128) NOT NULL,
    [kieu_gia_tri] nvarchar(128) NOT NULL,
    [gia_tri_chuoi] nvarchar(max) NULL,
    [gia_tri_so] decimal(28,10) NULL,
    [gia_tri_dung_sai] bit NULL,
    [gia_tri_ngay] date NULL,
    [gia_tri_thoi_diem] datetimeoffset(7) NULL,
    [phien_ban_cau_hinh] int NOT NULL,
    [mo_ta] nvarchar(max) NULL,
    [nguoi_cap_nhat_id] bigint NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    CONSTRAINT [pk_cau_hinh_he_thong] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[hang_cho_gui] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [su_kien_id] uniqueidentifier NOT NULL,
    [he_thong_dich_id] bigint NOT NULL,
    [khoa_chong_trung] nvarchar(512) NOT NULL,
    [loai_doi_tuong_nghiep_vu] nvarchar(128) NULL,
    [doi_tuong_nghiep_vu_id] nvarchar(128) NULL,
    [lan_sua_doi_doi_tuong] int NULL,
    [loai_su_kien] nvarchar(128) NULL,
    [phien_ban_hop_dong] nvarchar(128) NULL,
    [phien_ban_adapter] nvarchar(128) NULL,
    [sha256_du_lieu_gui] varchar(64) NULL,
    [quy_tac_thu_lai] nvarchar(128) NULL,
    [trang_thai] nvarchar(128) NULL,
    [so_lan_thu] int NULL,
    [thoi_diem_thu_lai_tiep] datetimeoffset(7) NULL,
    [khoa_den] datetimeoffset(7) NULL,
    [ma_loi_gan_nhat] nvarchar(128) NULL,
    [noi_dung_loi_gan_nhat] nvarchar(max) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_gui_thanh_cong] datetimeoffset(7) NULL,
    CONSTRAINT [pk_hang_cho_gui] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[hang_tiep_nhan] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NOT NULL,
    [su_kien_ben_ngoai_id] nvarchar(128) NULL,
    [khoa_chong_trung] nvarchar(512) NOT NULL,
    [loai_doi_tuong_nghiep_vu] nvarchar(128) NULL,
    [doi_tuong_nghiep_vu_id] nvarchar(128) NULL,
    [lan_sua_doi_doi_tuong] int NULL,
    [thu_tu_su_kien] bigint NULL,
    [loai_su_kien] nvarchar(128) NULL,
    [phien_ban_hop_dong] nvarchar(128) NULL,
    [phien_ban_adapter] nvarchar(128) NULL,
    [sha256_du_lieu_gui] varchar(64) NULL,
    [trang_thai] nvarchar(128) NULL,
    [ma_loi] nvarchar(128) NULL,
    [noi_dung_loi] nvarchar(max) NULL,
    [thoi_diem_tiep_nhan] datetimeoffset(7) NULL,
    [thoi_diem_xu_ly] datetimeoffset(7) NULL,
    CONSTRAINT [pk_hang_tiep_nhan] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[dot_dong_bo] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NULL,
    [loai_doi_tuong] nvarchar(128) NULL,
    [che_do_dong_bo] nvarchar(128) NULL,
    [con_tro_truoc] nvarchar(128) NULL,
    [con_tro_sau] nvarchar(128) NULL,
    [du_lieu_nguon_tinh_den] datetimeoffset(7) NULL,
    [trang_thai] nvarchar(128) NULL,
    [tong_so_ban_ghi] int NULL,
    [so_ban_ghi_thanh_cong] int NULL,
    [so_ban_ghi_loi] int NULL,
    [noi_dung_loi] nvarchar(max) NULL,
    [nguoi_yeu_cau_id] bigint NULL,
    [thoi_diem_bat_dau] datetimeoffset(7) NULL,
    [thoi_diem_ket_thuc] datetimeoffset(7) NULL,
    CONSTRAINT [pk_dot_dong_bo] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_chinh_sach] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma] nvarchar(128) NOT NULL,
    [ten] nvarchar(512) NULL,
    [nhom_chinh_sach] nvarchar(128) NULL,
    [dang_hoat_dong] bit NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    CONSTRAINT [pk_mghp_chinh_sach] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_phien_ban_chinh_sach] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [chinh_sach_id] bigint NOT NULL,
    [phien_ban] int NOT NULL,
    [ten_tai_thoi_diem_chot] nvarchar(512) NULL,
    [ty_le_giam] decimal(12,9) NOT NULL,
    [ma_pham_vi] nvarchar(128) NOT NULL,
    [ngay_bat_dau_hieu_luc] date NULL,
    [ngay_ket_thuc_hieu_luc] date NULL,
    [can_cu_phap_ly] nvarchar(max) NULL,
    [phien_ban_quy_tac_xet] nvarchar(128) NULL,
    [thoi_han_huong] nvarchar(128) NULL,
    [uu_tien_khi_cung_muc] int NULL,
    [trang_thai] nvarchar(128) NULL,
    [nguoi_tao_id] bigint NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_khoa] datetimeoffset(7) NULL,
    [so_van_ban_ap_dung] nvarchar(128) NULL,
    [dieu_khoan_ap_dung] nvarchar(128) NULL,
    [url_can_cu] nvarchar(2048) NULL,
    [che_do_cap_nhat_minh_chung] nvarchar(128) NULL,
    [huong_dan_cap_nhat_minh_chung] nvarchar(max) NULL,
    [tinh_trang_xac_nhan_quy_tac] nvarchar(128) NULL,
    [tuoi_toi_thieu] int NULL,
    [tuoi_toi_da] int NULL,
    [quy_tac_moc_tuoi] nvarchar(128) NULL,
    [yeu_cau_van_bang_thu_nhat] bit NULL,
    CONSTRAINT [pk_mghp_phien_ban_chinh_sach] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_nhom_yeu_cau_giay_to] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [phien_ban_chinh_sach_id] bigint NOT NULL,
    [ma] nvarchar(128) NOT NULL,
    [ten] nvarchar(512) NULL,
    [so_yeu_cau_toi_thieu_dat] int NOT NULL,
    [thu_tu_hien_thi] int NULL,
    CONSTRAINT [pk_mghp_nhom_yeu_cau_giay_to] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_giay_to_bat_buoc] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [nhom_id] bigint NOT NULL,
    [ma] nvarchar(128) NOT NULL,
    [ten] nvarchar(512) NULL,
    [loai_giay_to] nvarchar(128) NULL,
    [can_doi_chieu_ban_giay] bit NULL,
    [tuoi_toi_da_giay_to_theo_ngay] int NULL,
    [thu_tu_hien_thi] int NULL,
    [can_ban_sao_chung_thuc] bit NULL,
    [can_dau_xac_nhan] bit NULL,
    [noi_dung_xac_nhan] nvarchar(512) NULL,
    [co_quan_xac_nhan] nvarchar(512) NULL,
    [huong_dan_kiem_tra] nvarchar(max) NULL,
    [chu_the_minh_chung] nvarchar(128) NULL,
    CONSTRAINT [pk_mghp_giay_to_bat_buoc] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_chinh_sach_dot_xet] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [dot_xet_id] bigint NOT NULL,
    [phien_ban_chinh_sach_id] bigint NOT NULL,
    [tep_mau_don_id] bigint NULL,
    [thu_tu_hien_thi] int NULL,
    CONSTRAINT [pk_mghp_chinh_sach_dot_xet] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_dot_xet] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma_dot_xet] nvarchar(128) NOT NULL,
    [hoc_ky_id] bigint NOT NULL,
    [thong_bao_dot_xet_id] bigint NULL,
    [ten] nvarchar(512) NULL,
    [mo_ta] nvarchar(max) NULL,
    [thoi_diem_mo] datetimeoffset(7) NULL,
    [han_nop] datetimeoffset(7) NULL,
    [han_ra_soat] datetimeoffset(7) NULL,
    [trang_thai] nvarchar(128) NULL,
    [phien_ban_dong] int NULL,
    [nguoi_khoa_id] bigint NULL,
    [thoi_diem_khoa] datetimeoffset(7) NULL,
    [nguoi_chot_hoi_dong_id] bigint NULL,
    [thoi_diem_hoi_dong_chot] datetimeoffset(7) NULL,
    [ly_do_huy] nvarchar(max) NULL,
    [nguoi_tao_id] bigint NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [huong_dan_ke_khai] nvarchar(max) NULL,
    [cho_phep_truy_linh] bit NULL,
    [nguoi_khoa_cau_hinh_id] bigint NULL,
    [thoi_diem_khoa_cau_hinh] datetimeoffset(7) NULL,
    [che_do_truy_linh] nvarchar(128) NULL,
    [cach_xet_thay_doi_giua_ky] nvarchar(128) NULL,
    [huong_dan_can_cu_nop_muon] nvarchar(max) NULL,
    CONSTRAINT [pk_mghp_dot_xet] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_ho_so] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma_ho_so] nvarchar(128) NOT NULL,
    [dot_xet_id] bigint NOT NULL,
    [sinh_vien_id] bigint NOT NULL,
    [qua_trinh_hoc_id] bigint NOT NULL,
    [trang_thai] nvarchar(128) NULL,
    [nguon] nvarchar(128) NULL,
    [phien_ban_dong] int NULL,
    [nguoi_duoc_giao_id] bigint NULL,
    [ly_do_rut_ho_so] nvarchar(max) NULL,
    [thoi_diem_nop] datetimeoffset(7) NULL,
    [nguoi_duyet_id] bigint NULL,
    [thoi_diem_duyet] datetimeoffset(7) NULL,
    [nguoi_tao_id] bigint NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [chinh_sach_id] bigint NOT NULL,
    [phien_ban_chinh_sach_id] bigint NOT NULL,
    [ngay_nop_ho_so_hop_le_dau] date NULL,
    [can_cu_ngay_nop_goc] nvarchar(max) NULL,
    [nguoi_xac_minh_ngay_nop_id] bigint NULL,
    [thoi_diem_xac_minh_ngay_nop] datetimeoffset(7) NULL,
    CONSTRAINT [pk_mghp_ho_so] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_phien_ban_ho_so] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ho_so_id] bigint NOT NULL,
    [lan_sua_doi] int NOT NULL,
    [yeu_cau_bo_sung_id] bigint NULL,
    [sha256_du_lieu_gui] varchar(64) NULL,
    [nguoi_nop_id] bigint NOT NULL,
    [thoi_diem_nop] datetimeoffset(7) NOT NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [loai_lan_nop] nvarchar(128) NOT NULL,
    [dot_tiep_nhan_id] bigint NULL,
    [quyen_huong_cap_nhat_id] bigint NULL,
    [phien_ban_chinh_sach_ke_khai_id] bigint NULL,
    [ma_thao_tac_nop] uniqueidentifier NOT NULL,
    [ma_phien_ban_mau] nvarchar(128) NULL,
    [ho_ten_khai_bao] nvarchar(512) NOT NULL,
    [so_cccd_khai_bao] nvarchar(64) NOT NULL,
    [ngay_sinh_khai_bao] date NOT NULL,
    [noi_sinh_khai_bao] nvarchar(512) NOT NULL,
    [gioi_tinh_khai_bao] nvarchar(128) NOT NULL,
    [dan_toc_khai_bao_id] bigint NOT NULL,
    [ten_dan_toc_luc_nop] nvarchar(512) NULL,
    [lop_khai_bao_id] bigint NOT NULL,
    [ma_lop_luc_nop] nvarchar(128) NULL,
    [mssv_khai_bao] nvarchar(64) NOT NULL,
    [khoa_hoc_khai_bao_id] bigint NOT NULL,
    [ma_khoa_hoc_luc_nop] nvarchar(128) NULL,
    [khoa_quan_ly_khai_bao_id] bigint NOT NULL,
    [ten_khoa_luc_nop] nvarchar(512) NULL,
    [dia_chi_thuong_tru_khai_bao] nvarchar(max) NOT NULL,
    [so_dien_thoai_khai_bao] nvarchar(64) NOT NULL,
    [ket_qua_doi_chieu_thong_tin] nvarchar(128) NULL,
    [thoi_diem_lay_du_lieu_sis] datetimeoffset(7) NULL,
    [la_van_bang_thu_nhat] bit NULL,
    [da_huong_mghp_cung_trinh_do] bit NULL,
    [dang_huong_luong_khi_di_hoc] bit NULL,
    [cam_ket_khai_dung] bit NULL,
    CONSTRAINT [pk_mghp_phien_ban_ho_so] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_giay_to_ho_so] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [phien_ban_ho_so_id] bigint NOT NULL,
    [giay_to_bat_buoc_id] bigint NOT NULL,
    [ma_minh_chung] nvarchar(128) NULL,
    [so_giay_to] nvarchar(128) NULL,
    [don_vi_cap] nvarchar(512) NULL,
    [ngay_cap] date NULL,
    [ngay_het_han] date NULL,
    [sha256_noi_dung] varchar(64) NULL,
    [nguoi_nop_id] bigint NULL,
    [thoi_diem_nop] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [nguoi_lien_quan_id] bigint NULL,
    [ngay_bat_dau_hieu_luc] date NULL,
    [loai_hieu_luc] nvarchar(128) NULL,
    [thoi_gian_chung_minh_tu] date NULL,
    [thoi_gian_chung_minh_den] date NULL,
    [hoc_ky_xac_nhan_id] bigint NULL,
    [loai_ho_ngheo] nvarchar(128) NULL,
    [ma_dia_ban] nvarchar(128) NULL,
    [can_cu_phan_loai_dia_ban] nvarchar(max) NULL,
    [trang_thai_xac_minh_hieu_luc] nvarchar(128) NULL,
    [nguoi_xac_minh_id] bigint NULL,
    [thoi_diem_xac_minh] datetimeoffset(7) NULL,
    [ghi_chu] nvarchar(max) NULL,
    CONSTRAINT [pk_mghp_giay_to_ho_so] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_tep_minh_chung] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [giay_to_ho_so_id] bigint NOT NULL,
    [tep_dinh_kem_id] bigint NOT NULL,
    CONSTRAINT [pk_mghp_tep_minh_chung] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_ket_qua_kiem_tra_giay_to] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [loai_kiem_tra] nvarchar(128) NULL,
    [ket_qua] nvarchar(128) NULL,
    [ghi_chu] nvarchar(max) NULL,
    [ket_qua_kiem_tra_duoc_thay_the_id] bigint NULL,
    [nguoi_thuc_hien_id] bigint NULL,
    [thoi_diem_thao_tac] datetimeoffset(7) NULL,
    [giay_to_ho_so_id] bigint NULL,
    CONSTRAINT [pk_mghp_ket_qua_kiem_tra_giay_to] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_yeu_cau_bo_sung] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ho_so_id] bigint NOT NULL,
    [phien_ban_lam_co_so_id] bigint NOT NULL,
    [so_vong] int NOT NULL,
    [trang_thai] nvarchar(128) NULL,
    [ly_do] nvarchar(max) NULL,
    [han_xu_ly] datetimeoffset(7) NULL,
    [nguoi_yeu_cau_id] bigint NULL,
    [thoi_diem_yeu_cau] datetimeoffset(7) NULL,
    [thoi_diem_nhan_phan_hoi] datetimeoffset(7) NULL,
    [thoi_diem_dong] datetimeoffset(7) NULL,
    [dot_tiep_nhan_id] bigint NULL,
    [quyen_huong_id] bigint NULL,
    [loai_dot_cap_nhat] nvarchar(128) NULL,
    CONSTRAINT [pk_mghp_yeu_cau_bo_sung] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_noi_dung_bo_sung] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [yeu_cau_bo_sung_id] bigint NOT NULL,
    [giay_to_bat_buoc_id] bigint NULL,
    [loai_yeu_cau] nvarchar(128) NULL,
    [ly_do] nvarchar(max) NULL,
    [giay_to_ho_so_id] bigint NULL,
    [hoc_ky_ap_dung_id] bigint NULL,
    [danh_gia_dieu_kien_id] bigint NULL,
    [khoa_truong_khai_bao] nvarchar(128) NULL,
    [giay_to_tra_loi_id] bigint NULL,
    [ket_qua_xu_ly] nvarchar(128) NULL,
    [nguoi_xu_ly_id] bigint NULL,
    [thoi_diem_xu_ly] datetimeoffset(7) NULL,
    CONSTRAINT [pk_mghp_noi_dung_bo_sung] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_nhat_ky_ho_so] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ho_so_id] bigint NOT NULL,
    [phien_ban_ho_so_id] bigint NULL,
    [nguoi_thao_tac_id] bigint NULL,
    [loai_chu_the_thao_tac] nvarchar(128) NULL,
    [hanh_dong] nvarchar(128) NULL,
    [trang_thai_cu] nvarchar(128) NULL,
    [trang_thai_moi] nvarchar(128) NULL,
    [ly_do] nvarchar(max) NULL,
    [ma_lien_ket_yeu_cau] uniqueidentifier NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [yeu_cau_bo_sung_id] bigint NULL,
    [han_cu] datetimeoffset(7) NULL,
    [han_moi] datetimeoffset(7) NULL,
    CONSTRAINT [pk_mghp_nhat_ky_ho_so] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_danh_gia_dieu_kien] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [sinh_vien_id] bigint NOT NULL,
    [qua_trinh_hoc_id] bigint NOT NULL,
    [hoc_ky_ap_dung_id] bigint NOT NULL,
    [phien_ban_chinh_sach_id] bigint NOT NULL,
    [quyen_huong_nguon_id] bigint NULL,
    [phien_ban_quy_tac] nvarchar(128) NULL,
    [ket_qua] nvarchar(128) NULL,
    [du_lieu_nguon_tinh_den] datetimeoffset(7) NULL,
    [minh_chung_co_gia_tri_den] date NULL,
    [nguoi_danh_gia_id] bigint NULL,
    [thoi_diem_danh_gia] datetimeoffset(7) NULL,
    [dot_xet_id] bigint NULL,
    [phien_ban_ho_so_nguon_id] bigint NULL,
    [so_phien_ban_danh_gia] int NOT NULL,
    [danh_gia_truoc_id] bigint NULL,
    [trang_thai_ra_soat_minh_chung] nvarchar(128) NULL,
    [ho_so_nguon_id] bigint NULL,
    [phien_ban_cap_nhat_minh_chung_id] bigint NULL,
    [ma_pham_vi] nvarchar(128) NOT NULL,
    [trang_thai_xu_ly] nvarchar(128) NULL,
    [la_ban_hien_hanh] bit NULL,
    [ly_do_ket_luan] nvarchar(max) NULL,
    [ly_do_danh_gia_lai] nvarchar(max) NULL,
    [ngay_xet_tu] date NULL,
    [ngay_xet_den] date NULL,
    [ngay_tinh_tuoi] date NULL,
    [ngay_sinh_dung_de_xet] date NULL,
    [trang_thai_xet_giua_ky] nvarchar(128) NULL,
    [can_cu_xet_thoi_gian] nvarchar(max) NULL,
    [lich_su_trang_thai_sv_id] bigint NULL,
    CONSTRAINT [pk_mghp_danh_gia_dieu_kien] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_bien_ban] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [dot_xet_id] bigint NULL,
    [ma_bb] nvarchar(128) NOT NULL,
    [so_tham_chieu] nvarchar(512) NULL,
    [thoi_diem_hop] datetimeoffset(7) NULL,
    [dia_diem] nvarchar(512) NULL,
    [trang_thai] nvarchar(128) NULL,
    [nguoi_tao_id] bigint NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [phien_ban_qd_nguon_id] bigint NULL,
    [sha256_ds_hop] varchar(64) NULL,
    CONSTRAINT [pk_mghp_bien_ban] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_quyet_dinh] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [dot_xet_id] bigint NULL,
    [ma_qd] nvarchar(128) NOT NULL,
    [loai_qd] nvarchar(128) NULL,
    [don_vi_ban_hanh_id] bigint NULL,
    [nam_danh_so_qd] int NULL,
    [so_qd] nvarchar(128) NULL,
    [ngay_qd] date NULL,
    [quyet_dinh_duoc_thay_the_id] bigint NULL,
    [loai_quan_he] nvarchar(128) NULL,
    [ly_do] nvarchar(max) NULL,
    [nguoi_tao_id] bigint NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    CONSTRAINT [pk_mghp_quyet_dinh] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_phien_ban_qd] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [quyet_dinh_id] bigint NOT NULL,
    [lan_sua_doi] int NOT NULL,
    [trang_thai] nvarchar(128) NULL,
    [ngay_bat_dau_hieu_luc] date NULL,
    [sha256_tap_du_lieu] varchar(64) NULL,
    [so_dong] int NULL,
    [nguoi_chot_id] bigint NULL,
    [thoi_diem_chot] datetimeoffset(7) NULL,
    [thoi_diem_ban_hanh] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [giai_doan] nvarchar(128) NULL,
    [phien_ban_truoc_id] bigint NULL,
    [ly_do_tao_phien_ban] nvarchar(max) NULL,
    [nguoi_tao_id] bigint NULL,
    CONSTRAINT [pk_mghp_phien_ban_qd] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_lien_ket_phien_ban_van_ban] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [loai_van_ban] nvarchar(128) NOT NULL,
    [bien_ban_id] bigint NULL,
    [phien_ban_qd_id] bigint NULL,
    [phien_ban_van_ban_dung_chung_id] bigint NOT NULL,
    [trang_thai] nvarchar(128) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    CONSTRAINT [pk_mghp_lien_ket_phien_ban_van_ban] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_dong_qd] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [phien_ban_qd_id] bigint NOT NULL,
    [sinh_vien_id] bigint NOT NULL,
    [qua_trinh_hoc_id] bigint NOT NULL,
    [hoc_ky_ap_dung_id] bigint NOT NULL,
    [phien_ban_chinh_sach_id] bigint NULL,
    [quyen_huong_nguon_id] bigint NULL,
    [danh_gia_dieu_kien_id] bigint NULL,
    [ma_pham_vi] nvarchar(128) NOT NULL,
    [ty_le_giam] decimal(12,9) NULL,
    [loai_thao_tac] nvarchar(128) NULL,
    [dong_duoc_thay_the_id] bigint NULL,
    [sha256_dong] varchar(64) NULL,
    [phien_ban_ho_so_nguon_id] bigint NULL,
    [ung_vien_id] bigint NULL,
    [mssv_luc_chot] nvarchar(64) NULL,
    [ho_ten_luc_chot] nvarchar(512) NULL,
    [ma_lop_luc_chot] nvarchar(128) NULL,
    [ten_chinh_sach_luc_chot] nvarchar(512) NULL,
    [hoc_ky_bat_dau_huong_id] bigint NULL,
    [hoc_ky_ket_thuc_huong_id] bigint NULL,
    [ngay_huong_tu] date NULL,
    [ngay_huong_den] date NULL,
    [pham_vi_thoi_gian_huong] nvarchar(128) NULL,
    CONSTRAINT [pk_mghp_dong_qd] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_quyen_huong] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma_quyen_huong] nvarchar(128) NOT NULL,
    [sinh_vien_id] bigint NOT NULL,
    [qua_trinh_hoc_id] bigint NOT NULL,
    [phien_ban_chinh_sach_id] bigint NULL,
    [dong_qd_cong_nhan_id] bigint NULL,
    [ma_pham_vi] nvarchar(128) NOT NULL,
    [hoc_ky_bat_dau_huong_id] bigint NULL,
    [hoc_ky_ket_thuc_huong_id] bigint NULL,
    [ty_le_giam] decimal(12,9) NULL,
    [trang_thai] nvarchar(128) NULL,
    [thoi_diem_ra_soat_tiep] date NULL,
    [ly_do_can_ra_soat] nvarchar(max) NULL,
    [dong_qd_thu_hoi_id] bigint NULL,
    [ngay_hieu_luc_thu_hoi] date NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [phien_ban_ho_so_nguon_id] bigint NULL,
    [ngay_cong_nhan_tu] date NULL,
    [ngay_cong_nhan_den] date NULL,
    [che_do_cap_nhat_minh_chung] nvarchar(128) NULL,
    [nhom_don_vi_cong_tac] nvarchar(128) NULL,
    [tinh_trang_xac_nhan_chu_ky] nvarchar(128) NULL,
    [khoang_huong_cong_nhan_id] bigint NULL,
    [chinh_sach_id] bigint NOT NULL,
    CONSTRAINT [pk_mghp_quyen_huong] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_bien_dong_quyen_huong] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [quyen_huong_id] bigint NOT NULL,
    [loai_su_kien] nvarchar(128) NULL,
    [ngay_hieu_luc] date NULL,
    [dong_qd_id] bigint NULL,
    [ly_do] nvarchar(max) NULL,
    [nguoi_ghi_nhan_id] bigint NULL,
    [thoi_diem_ghi_nhan] datetimeoffset(7) NULL,
    [so_phien_ban] int NOT NULL,
    [bien_dong_truoc_id] bigint NULL,
    [trang_thai_sau] nvarchar(128) NULL,
    [hoc_ky_bat_dau_sau_id] bigint NULL,
    [hoc_ky_ket_thuc_sau_id] bigint NULL,
    [ngay_cong_nhan_tu_sau] date NULL,
    [ngay_cong_nhan_den_sau] date NULL,
    [ty_le_giam_sau] decimal(12,9) NULL,
    [ngay_ra_soat_tiep_sau] date NULL,
    [che_do_cap_nhat_sau] nvarchar(128) NULL,
    CONSTRAINT [pk_mghp_bien_dong_quyen_huong] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_ket_qua_hoc_ky] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ma_ket_qua_huong] nvarchar(128) NOT NULL,
    [lan_sua_doi] int NOT NULL,
    [sinh_vien_id] bigint NOT NULL,
    [qua_trinh_hoc_id] bigint NOT NULL,
    [hoc_ky_id] bigint NOT NULL,
    [ma_pham_vi] nvarchar(128) NOT NULL,
    [phien_ban_chinh_sach_id] bigint NULL,
    [dong_qd_id] bigint NOT NULL,
    [quyen_huong_id] bigint NULL,
    [danh_gia_dieu_kien_id] bigint NULL,
    [ket_qua_huong_duoc_thay_the_id] bigint NULL,
    [loai_huong] nvarchar(128) NULL,
    [loai_thao_tac] nvarchar(128) NULL,
    [ty_le_giam] decimal(12,9) NULL,
    [la_ban_hien_hanh] bit NOT NULL,
    [trang_thai] nvarchar(128) NULL,
    [ngay_hieu_luc] date NULL,
    [trang_thai_ke_toan] nvarchar(128) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [ngay_huong_tu] date NULL,
    [ngay_huong_den] date NULL,
    [pham_vi_thoi_gian_huong] nvarchar(128) NULL,
    [ly_do_cho_doi_soat_ke_toan] nvarchar(max) NULL,
    CONSTRAINT [pk_mghp_ket_qua_hoc_ky] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_dot_gui_ke_toan] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [yeu_cau_ben_ngoai_id] uniqueidentifier NOT NULL,
    [he_thong_dich_id] bigint NOT NULL,
    [phien_ban_qd_id] bigint NOT NULL,
    [hoc_ky_id] bigint NOT NULL,
    [phien_ban_cau_truc_du_lieu] nvarchar(128) NOT NULL,
    [sha256_du_lieu_gui] varchar(64) NOT NULL,
    [trang_thai] nvarchar(128) NULL,
    [so_lan_thu] int NULL,
    [thoi_diem_thu_gan_nhat] datetimeoffset(7) NULL,
    [thoi_diem_thu_lai_tiep] datetimeoffset(7) NULL,
    [ma_lien_ket_yeu_cau] uniqueidentifier NULL,
    [ban_tin_gui_id] bigint NULL,
    [nguoi_tao_id] bigint NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [ma_qd_luc_gui] nvarchar(128) NOT NULL,
    [so_qd_luc_gui] nvarchar(128) NOT NULL,
    [ngay_qd_luc_gui] date NOT NULL,
    [lan_sua_doi_qd_luc_gui] int NOT NULL,
    [thoi_diem_cong_bo_luc_gui] datetimeoffset(7) NOT NULL,
    [sha256_pdf_da_ky] varchar(64) NOT NULL,
    [phien_ban_adapter] nvarchar(128) NOT NULL,
    CONSTRAINT [pk_mghp_dot_gui_ke_toan] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_dong_gui_ke_toan] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [dot_gui_id] bigint NOT NULL,
    [dong_id] uniqueidentifier NOT NULL,
    [ket_qua_huong_id] bigint NOT NULL,
    [loai_thao_tac] nvarchar(128) NOT NULL,
    [lan_sua_doi_duoc_thay_the] int NULL,
    [sha256_du_lieu_gui] varchar(64) NOT NULL,
    [trang_thai] nvarchar(128) NULL,
    [dong_ben_ngoai_id] nvarchar(128) NULL,
    [thu_tu_su_kien_gan_nhat] bigint NULL,
    [ma_loi] nvarchar(128) NULL,
    [noi_dung_loi] nvarchar(max) NULL,
    [thoi_diem_xu_ly] datetimeoffset(7) NULL,
    [mssv_luc_gui] nvarchar(64) NOT NULL,
    [ma_qua_trinh_hoc_luc_gui] nvarchar(128) NOT NULL,
    [ma_hoc_ky_luc_gui] nvarchar(128) NOT NULL,
    [ma_pham_vi_luc_gui] nvarchar(128) NOT NULL,
    [ma_chinh_sach_luc_gui] nvarchar(128) NOT NULL,
    [phien_ban_chinh_sach_luc_gui] int NOT NULL,
    [ma_ket_qua_huong_luc_gui] nvarchar(128) NOT NULL,
    [lan_sua_doi_ket_qua_luc_gui] int NOT NULL,
    [ty_le_giam_luc_gui] decimal(12,9) NOT NULL,
    CONSTRAINT [pk_mghp_dong_gui_ke_toan] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_xac_nhan_ke_toan] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [dong_gui_id] bigint NOT NULL,
    [ban_tin_nhan_id] bigint NOT NULL,
    [he_thong_cung_cap_id] bigint NOT NULL,
    [xac_nhan_ben_ngoai_id] uniqueidentifier NOT NULL,
    [so_xac_nhan] nvarchar(128) NOT NULL,
    [loai_bien_nhan] nvarchar(128) NULL,
    [so_tien] decimal(19,4) NOT NULL,
    [don_vi_tien] nvarchar(128) NOT NULL,
    [phien_ban_quy_tac_tinh] nvarchar(128) NOT NULL,
    [phien_ban_co_so_tinh] int NOT NULL,
    [sha256_du_lieu_gui] varchar(64) NULL,
    [thoi_diem_xu_ly] datetimeoffset(7) NULL,
    [thoi_diem_tiep_nhan] datetimeoffset(7) NULL,
    [thu_tu_su_kien] bigint NULL,
    [trang_thai_xac_nhan] nvarchar(128) NULL,
    [so_tien_co_so] decimal(19,4) NOT NULL,
    [ty_le_giam_xac_nhan] decimal(12,9) NOT NULL,
    [ma_loi] nvarchar(128) NULL,
    [noi_dung_loi] nvarchar(max) NULL,
    CONSTRAINT [pk_mghp_xac_nhan_ke_toan] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_cong_bo] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [phien_ban_qd_id] bigint NOT NULL,
    [thong_bao_id] bigint NULL,
    [nguoi_cong_bo_id] bigint NULL,
    [thoi_diem_cong_bo] datetimeoffset(7) NULL,
    [ma_cong_bo] nvarchar(128) NOT NULL,
    [sha256_tap_du_lieu] varchar(64) NULL,
    [ghi_chu_cong_khai] nvarchar(max) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    CONSTRAINT [pk_mghp_cong_bo] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_tieu_chi_chinh_sach] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [phien_ban_chinh_sach_id] bigint NOT NULL,
    [ma] nvarchar(128) NOT NULL,
    [ten] nvarchar(512) NULL,
    [huong_dan_xet] nvarchar(max) NULL,
    [bat_buoc] bit NULL,
    [co_dieu_kien_ap_dung] bit NULL,
    [huong_dan_ap_dung] nvarchar(max) NULL,
    [thu_tu_hien_thi] int NULL,
    CONSTRAINT [pk_mghp_tieu_chi_chinh_sach] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_ket_qua_tieu_chi] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [danh_gia_dieu_kien_id] bigint NOT NULL,
    [tieu_chi_chinh_sach_id] bigint NOT NULL,
    [ket_qua] nvarchar(128) NULL,
    [ma_ly_do] nvarchar(2000) NULL,
    [nhan_xet] nvarchar(max) NULL,
    [nguoi_danh_gia_id] bigint NULL,
    [thoi_diem_danh_gia] datetimeoffset(7) NULL,
    CONSTRAINT [pk_mghp_ket_qua_tieu_chi] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_hoc_ky_de_nghi] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [phien_ban_ho_so_id] bigint NOT NULL,
    [hoc_ky_id] bigint NOT NULL,
    [phien_ban_chinh_sach_id] bigint NOT NULL,
    [loai_de_nghi] nvarchar(128) NULL,
    [ghi_chu] nvarchar(max) NULL,
    CONSTRAINT [pk_mghp_hoc_ky_de_nghi] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_nguoi_lien_quan] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [phien_ban_ho_so_id] bigint NOT NULL,
    [loai_quan_he] nvarchar(128) NULL,
    [ho_ten] nvarchar(512) NULL,
    [so_cccd] nvarchar(64) NULL,
    [dan_toc_id] bigint NULL,
    [dia_chi_thuong_tru] nvarchar(max) NULL,
    [ma_dia_ban] nvarchar(128) NULL,
    [don_vi_cong_tac_id] bigint NULL,
    [ten_don_vi_cong_tac] nvarchar(512) NULL,
    [nhom_don_vi_cong_tac] nvarchar(128) NULL,
    [tinh_trang_cong_tac] nvarchar(128) NULL,
    [ngay_bat_dau_tinh_trang] date NULL,
    [ngay_ket_thuc_tinh_trang] date NULL,
    [ma_can_bo_doi_chieu] nvarchar(128) NULL,
    [trang_thai_xac_minh] nvarchar(128) NULL,
    [nguoi_xac_minh_id] bigint NULL,
    [thoi_diem_xac_minh] datetimeoffset(7) NULL,
    CONSTRAINT [pk_mghp_nguoi_lien_quan] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_ung_vien_phien_ban_qd] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [phien_ban_qd_id] bigint NOT NULL,
    [danh_gia_dieu_kien_id] bigint NOT NULL,
    [qua_trinh_hoc_id] bigint NOT NULL,
    [hoc_ky_id] bigint NOT NULL,
    [ma_pham_vi] nvarchar(128) NOT NULL,
    [ty_le_giam_luc_chot] decimal(12,9) NOT NULL,
    [ngay_du_dieu_kien_tu] date NOT NULL,
    [ngay_du_dieu_kien_den] date NOT NULL,
    [trang_thai_lua_chon] nvarchar(128) NOT NULL,
    [ma_ly_do] nvarchar(2000) NULL,
    [ly_do] nvarchar(max) NULL,
    [khoang_du_dieu_kien_id] bigint NOT NULL,
    CONSTRAINT [pk_mghp_ung_vien_phien_ban_qd] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_doi_chieu_ke_khai] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [phien_ban_ho_so_id] bigint NOT NULL,
    [ma_truong] nvarchar(128) NOT NULL,
    [gia_tri_khai_bao] nvarchar(max) NULL,
    [gia_tri_nguon] nvarchar(max) NULL,
    [ket_qua_doi_chieu] nvarchar(128) NULL,
    [thoi_diem_lay_nguon] datetimeoffset(7) NULL,
    [nguoi_xac_minh_id] bigint NULL,
    [ket_luan_xac_minh] nvarchar(128) NULL,
    [can_cu_xac_minh] nvarchar(max) NULL,
    [thoi_diem_xac_minh] datetimeoffset(7) NULL,
    CONSTRAINT [pk_mghp_doi_chieu_ke_khai] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_can_cu_danh_gia] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [danh_gia_dieu_kien_id] bigint NOT NULL,
    [giay_to_ho_so_id] bigint NOT NULL,
    [ket_qua_tieu_chi_id] bigint NULL,
    [ngay_ap_dung_tu] date NULL,
    [ngay_ap_dung_den] date NULL,
    [ket_qua_su_dung] nvarchar(128) NULL,
    [ly_do] nvarchar(max) NULL,
    [nguoi_xac_nhan_id] bigint NULL,
    [thoi_diem_xac_nhan] datetimeoffset(7) NULL,
    CONSTRAINT [pk_mghp_can_cu_danh_gia] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[cong_sv_thong_tin_sv] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [sinh_vien_nguon_id] bigint NULL,
    [mssv] nvarchar(64) NOT NULL,
    [ho_ten] nvarchar(512) NULL,
    [ma_lop] nvarchar(128) NULL,
    [ma_khoa] nvarchar(128) NULL,
    [ma_chuong_trinh] nvarchar(128) NULL,
    [lan_sua_doi_danh_ba] bigint NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    CONSTRAINT [pk_cong_sv_thong_tin_sv] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[cong_sv_su_kien_cap_nhat] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon] nvarchar(128) NOT NULL,
    [su_kien_id] uniqueidentifier NOT NULL,
    [cong_bo_id] bigint NULL,
    [loai_doi_tuong] nvarchar(128) NOT NULL,
    [ma_doi_tuong] nvarchar(128) NOT NULL,
    [lan_sua_doi_nguon] bigint NOT NULL,
    [sha256_du_lieu_gui] varchar(64) NULL,
    [sinh_vien_nguon_id] bigint NULL,
    [ket_qua_hoc_ky_nguon_id] bigint NULL,
    [phien_ban_bo_chieu] nvarchar(128) NULL,
    [trang_thai] nvarchar(128) NULL,
    [so_lan_thu] int NULL,
    [thoi_diem_thu_lai_tiep] datetimeoffset(7) NULL,
    [ma_loi] nvarchar(128) NULL,
    [loi_gan_nhat] nvarchar(max) NULL,
    [thoi_diem_tiep_nhan] datetimeoffset(7) NULL,
    [thoi_diem_xu_ly] datetimeoffset(7) NULL,
    CONSTRAINT [pk_cong_sv_su_kien_cap_nhat] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[cong_sv_ket_qua_mghp] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [sinh_vien_id] bigint NOT NULL,
    [ket_qua_hoc_ky_id] bigint NOT NULL,
    [cong_bo_id] bigint NOT NULL,
    [su_kien_cap_nhat_id] bigint NOT NULL,
    [ma_ket_qua_huong] nvarchar(128) NOT NULL,
    [lan_sua_doi_ket_qua_huong] int NOT NULL,
    [ma_hoc_ky] nvarchar(128) NOT NULL,
    [ten_hoc_ky] nvarchar(512) NULL,
    [nam_hoc] nvarchar(128) NULL,
    [ma_qua_trinh_hoc] nvarchar(128) NOT NULL,
    [ma_pham_vi] nvarchar(128) NOT NULL,
    [ma_chinh_sach] nvarchar(128) NULL,
    [phien_ban_chinh_sach] nvarchar(128) NULL,
    [ten_chinh_sach] nvarchar(512) NULL,
    [ty_le_giam] decimal(12,9) NULL,
    [loai_huong] nvarchar(128) NULL,
    [ma_qd] nvarchar(128) NULL,
    [lan_sua_doi_qd] int NULL,
    [so_qd] nvarchar(128) NULL,
    [ngay_qd] date NULL,
    [thoi_diem_cong_bo] datetimeoffset(7) NULL,
    [ghi_chu_cong_khai] nvarchar(max) NULL,
    [sha256_du_lieu_nguon] varchar(64) NULL,
    [trang_thai] nvarchar(128) NOT NULL,
    [la_ban_hien_hanh] bit NOT NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [pham_vi_thoi_gian_huong] nvarchar(128) NULL,
    CONSTRAINT [pk_cong_sv_ket_qua_mghp] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_khoang_huong] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [dong_qd_id] bigint NOT NULL,
    [ung_vien_id] bigint NULL,
    [ngay_bat_dau] date NOT NULL,
    [ngay_ket_thuc] date NOT NULL,
    [ty_le_giam] decimal(12,9) NOT NULL,
    [ly_do] nvarchar(max) NULL,
    CONSTRAINT [pk_mghp_khoang_huong] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_khoang_du_dieu_kien] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [danh_gia_dieu_kien_id] bigint NOT NULL,
    [ngay_bat_dau] date NOT NULL,
    [ngay_ket_thuc] date NOT NULL,
    [can_cu_thoi_gian] nvarchar(max) NULL,
    [nguoi_xac_nhan_id] bigint NULL,
    [thoi_diem_xac_nhan] datetimeoffset(7) NULL,
    CONSTRAINT [pk_mghp_khoang_du_dieu_kien] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[lan_kiem_tra_chung_thu] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [buoc_ky_id] bigint NULL,
    [lan_goi_tra_cuu_id] bigint NULL,
    [chung_thu_ca_id] uniqueidentifier NULL,
    [so_seri] nvarchar(128) NULL,
    [nguoi_dung_ca_id] uniqueidentifier NULL,
    [ten_nguoi_so_huu] nvarchar(512) NULL,
    [don_vi_cap] nvarchar(512) NULL,
    [hieu_luc_tu] datetimeoffset(7) NULL,
    [hieu_luc_den] datetimeoffset(7) NULL,
    [trang_thai_ca] nvarchar(128) NULL,
    [hop_le_theo_ca] bit NULL,
    [khoa_rieng_san_sang_theo_ca] bit NULL,
    [nha_cung_cap_khoa] nvarchar(512) NULL,
    [he_thong_ky] nvarchar(128) NULL,
    [thoi_diem_tao_phia_ca] datetimeoffset(7) NULL,
    [thoi_diem_kiem_tra] datetimeoffset(7) NULL,
    [ket_qua] nvarchar(128) NULL,
    [ma_loi] nvarchar(128) NULL,
    [ghi_chu] nvarchar(max) NULL,
    CONSTRAINT [pk_lan_kiem_tra_chung_thu] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[lan_kiem_tra_tep] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [tep_dinh_kem_id] bigint NULL,
    [tep_ket_qua_ky_id] bigint NULL,
    [so_thu_tu] int NULL,
    [sha256_mong_doi] varchar(64) NULL,
    [sha256_thuc_te] varchar(64) NULL,
    [kieu_noi_dung_thuc_te] nvarchar(2000) NULL,
    [kich_thuoc_thuc_te] bigint NULL,
    [so_trang_pdf] int NULL,
    [pdf_co_mat_khau] bit NULL,
    [trang_thai_toan_ven] nvarchar(128) NULL,
    [trang_thai_cau_truc_pdf] nvarchar(128) NULL,
    [trang_thai_quet_tep] nvarchar(128) NULL,
    [trang_thai_kiem_tra_chu_ky] nvarchar(128) NULL,
    [phuong_phap_kiem_tra] nvarchar(128) NULL,
    [phien_ban_cong_cu] nvarchar(128) NULL,
    [ma_loi] nvarchar(128) NULL,
    [ghi_chu] nvarchar(max) NULL,
    [nguoi_kiem_tra_id] bigint NULL,
    [thoi_diem_bat_dau] datetimeoffset(7) NULL,
    [thoi_diem_ket_thuc] datetimeoffset(7) NULL,
    CONSTRAINT [pk_lan_kiem_tra_tep] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[chi_tiet_nhat_ky_thao_tac] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [nhat_ky_id] bigint NOT NULL,
    [ten_truong] nvarchar(512) NOT NULL,
    [kieu_gia_tri] nvarchar(128) NOT NULL,
    [trang_thai_gia_tri_cu] nvarchar(128) NOT NULL,
    [trang_thai_gia_tri_moi] nvarchar(128) NOT NULL,
    [chuoi_cu] nvarchar(max) NULL,
    [chuoi_moi] nvarchar(max) NULL,
    [so_cu] decimal(28,10) NULL,
    [so_moi] decimal(28,10) NULL,
    [dung_sai_cu] bit NULL,
    [dung_sai_moi] bit NULL,
    [ngay_cu] date NULL,
    [ngay_moi] date NULL,
    [thoi_diem_cu] datetimeoffset(7) NULL,
    [thoi_diem_moi] datetimeoffset(7) NULL,
    [tham_chieu_cu] nvarchar(512) NULL,
    [tham_chieu_moi] nvarchar(512) NULL,
    CONSTRAINT [pk_chi_tiet_nhat_ky_thao_tac] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[mghp_ban_tin_ke_toan_nhan] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [ban_tin_nhan_id] bigint NOT NULL,
    [dong_gui_id] bigint NULL,
    [xac_nhan_noi_bo_id] bigint NULL,
    [ma_he_thong_nguon] nvarchar(512) NULL,
    [yeu_cau_ben_ngoai_id] uniqueidentifier NULL,
    [dong_ben_ngoai_id] uniqueidentifier NULL,
    [ma_ket_qua_huong] nvarchar(128) NULL,
    [lan_sua_doi_ket_qua_huong] int NULL,
    [trang_thai_item_luc_nhan] nvarchar(128) NULL,
    [loai_thao_tac] nvarchar(128) NULL,
    [xac_nhan_ben_ngoai_id] uniqueidentifier NULL,
    [so_xac_nhan] nvarchar(128) NULL,
    [so_tien_co_so] decimal(19,4) NULL,
    [phien_ban_co_so_tinh] int NULL,
    [ty_le_giam] decimal(12,9) NULL,
    [so_tien_giam] decimal(19,4) NULL,
    [don_vi_tien] nvarchar(128) NULL,
    [phien_ban_quy_tac_tinh] nvarchar(128) NULL,
    [thoi_diem_xu_ly_phia_ke_toan] datetimeoffset(7) NULL,
    [ma_loi] nvarchar(128) NULL,
    [noi_dung_loi] nvarchar(max) NULL,
    [cho_phep_thu_lai] bit NULL,
    CONSTRAINT [pk_mghp_ban_tin_ke_toan_nhan] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[hoc_phan] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NOT NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(512) NOT NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [dang_hien_dien_nguon] bit NOT NULL,
    [ma_hoc_phan_nguon] nvarchar(128) NULL,
    [ten_hoc_phan_nguon] nvarchar(512) NULL,
    [so_tin_chi_nguon] decimal(28,10) NULL,
    [so_tin_chi_do_an_nguon] decimal(28,10) NULL,
    [co_thuc_tap_nguon] nvarchar(128) NULL,
    [ma_cong_thuc_nguon] nvarchar(128) NULL,
    [loai_hoc_phan_nguon] nvarchar(128) NULL,
    [cap_dao_tao_nguon] nvarchar(128) NULL,
    [loai_hoc_phan_ao_nguon] nvarchar(128) NULL,
    [co_do_an_tot_nghiep_nguon] nvarchar(128) NULL,
    CONSTRAINT [pk_hoc_phan] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[khung_chuong_trinh] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NOT NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(512) NOT NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [dang_hien_dien_nguon] bit NOT NULL,
    [ma_khung_nguon] nvarchar(128) NULL,
    [hoc_phan_id] bigint NOT NULL,
    [hoc_phan_duoc_thay_the_id] bigint NOT NULL,
    [hoc_ky_trong_khung_nguon] smallint NULL,
    [co_tu_chon_nguon] nvarchar(128) NULL,
    [tien_quyet_do_an_nguon] nvarchar(128) NULL,
    [phan_loai_tot_nghiep_nguon] nvarchar(128) NULL,
    CONSTRAINT [pk_khung_chuong_trinh] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[diem_ky_hoc] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NOT NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(512) NOT NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [dang_hien_dien_nguon] bit NOT NULL,
    [hoc_ky_id] bigint NOT NULL,
    [sinh_vien_id] bigint NOT NULL,
    [hoc_phan_id] bigint NOT NULL,
    [hoc_phan_tuong_duong_id] bigint NOT NULL,
    [diem_nguon] decimal(28,10) NULL,
    [ghi_chu_nguon] nvarchar(2000) NULL,
    [co_hoc_lai_nguon] nvarchar(128) NULL,
    [trang_thai_kiem_tra_nguon] nvarchar(128) NULL,
    [hoc_phi_hoc_phan_nguon] decimal(28,10) NULL,
    [co_no_hoc_phi_nguon] nvarchar(128) NULL,
    [so_chung_tu_nguon] nvarchar(128) NULL,
    [ma_doi_chieu_ho_so_hoc_phan_nguon] nvarchar(512) NULL,
    CONSTRAINT [pk_diem_ky_hoc] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[diem_mien] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NOT NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(512) NOT NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [dang_hien_dien_nguon] bit NOT NULL,
    [hoc_ky_id] bigint NOT NULL,
    [hoc_phan_id] bigint NOT NULL,
    [sinh_vien_id] bigint NOT NULL,
    [diem_nguon] decimal(28,10) NULL,
    [loai_diem_nguon] nvarchar(128) NULL,
    [ngay_cong_nhan_nguon] date NULL,
    [ghi_chu_nguon] nvarchar(2000) NULL,
    [hoc_phan_tuong_duong_id] bigint NOT NULL,
    [co_hoc_lai_nguon] nvarchar(128) NULL,
    CONSTRAINT [pk_diem_mien] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[tieu_thuc] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NOT NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(512) NOT NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [dang_hien_dien_nguon] bit NOT NULL,
    [ma_nguon] nvarchar(128) NULL,
    [ten_nguon] nvarchar(512) NULL,
    [gia_tri_nguon] nvarchar(512) NULL,
    [ghi_chu_nguon] nvarchar(2000) NULL,
    CONSTRAINT [pk_tieu_thuc] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[phong_hoc] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NOT NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(512) NOT NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [dang_hien_dien_nguon] bit NOT NULL,
    [ma_phong_nguon] varchar(128) NOT NULL,
    [ma_giang_duong_nguon] varchar(128) NULL,
    [co_khong_su_dung_nguon] smallint NULL,
    [co_khoa_nguon] smallint NULL,
    CONSTRAINT [pk_phong_hoc] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[lich_su_dung_phong] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NOT NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(512) NOT NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [dang_hien_dien_nguon] bit NOT NULL,
    [ma_lop_hoc_phan_nguon] varchar(128) NULL,
    [ma_phong_tham_chieu] varchar(128) NULL,
    [ngay_su_dung_nguon] date NULL,
    [tiet_dau_nguon] smallint NULL,
    [tiet_cuoi_nguon] smallint NULL,
    [ma_lich_tham_chieu] varchar(128) NULL,
    [ma_nghi_bu_tham_chieu] varchar(128) NULL,
    [co_an_nguon] smallint NULL,
    [co_hoc_truc_tuyen_nguon] smallint NULL,
    [co_ngay_le_nguon] smallint NULL,
    [co_lich_thi_nguon] smallint NULL,
    CONSTRAINT [pk_lich_su_dung_phong] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[khoa_phong] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NOT NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(512) NOT NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [dang_hien_dien_nguon] bit NOT NULL,
    [ma_phieu_khoa_nguon] varchar(128) NULL,
    [tu_ngay_nguon] date NULL,
    [den_ngay_nguon] date NULL,
    [co_khoa_thu_bay_nguon] smallint NULL,
    [co_khoa_chu_nhat_nguon] smallint NULL,
    CONSTRAINT [pk_khoa_phong] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[chi_tiet_khoa_phong] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NOT NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(512) NOT NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [dang_hien_dien_nguon] bit NOT NULL,
    [ma_phieu_khoa_tham_chieu] varchar(128) NULL,
    [tu_ngay_nguon] date NULL,
    [den_ngay_nguon] date NULL,
    [ma_phong_tham_chieu] varchar(128) NULL,
    [co_khoa_sang_nguon] smallint NULL,
    [co_khoa_chieu_nguon] smallint NULL,
    [co_khoa_toi_nguon] smallint NULL,
    CONSTRAINT [pk_chi_tiet_khoa_phong] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[phan_lop_ren_luyen] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NOT NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(512) NOT NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [dang_hien_dien_nguon] bit NOT NULL,
    [hoc_ky_id] bigint NOT NULL,
    [sinh_vien_id] bigint NOT NULL,
    [lop_ren_luyen_id] bigint NOT NULL,
    CONSTRAINT [pk_phan_lop_ren_luyen] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[ghep_lop_sinh_hoat] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NOT NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(512) NOT NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [dang_hien_dien_nguon] bit NOT NULL,
    [hoc_ky_id] bigint NOT NULL,
    [lop_goc_id] bigint NOT NULL,
    [lop_nhan_id] bigint NOT NULL,
    CONSTRAINT [pk_ghep_lop_sinh_hoat] PRIMARY KEY CLUSTERED ([id])
);

CREATE TABLE [dbo].[tien_do_sinh_vien] (
    [id] bigint IDENTITY(1,1) NOT NULL,
    [he_thong_nguon_id] bigint NOT NULL,
    [ma_dinh_danh_ben_ngoai] nvarchar(512) NOT NULL,
    [thoi_diem_cap_nhat_nguon] datetimeoffset(7) NULL,
    [thoi_diem_dong_bo] datetimeoffset(7) NULL,
    [thoi_diem_tao] datetimeoffset(7) NULL,
    [thoi_diem_cap_nhat] datetimeoffset(7) NULL,
    [sha256_ban_ghi_nguon] varchar(64) NULL,
    [phien_ban_anh_xa] nvarchar(128) NULL,
    [trang_thai_chuan_hoa] nvarchar(128) NULL,
    [loi_chuan_hoa] nvarchar(max) NULL,
    [dang_hien_dien_nguon] bit NOT NULL,
    [hoc_ky_id] bigint NOT NULL,
    [sinh_vien_id] bigint NOT NULL,
    [chuong_trinh_dao_tao_id] bigint NOT NULL,
    [chi_so_hoc_ky_hien_tai_nguon] smallint NULL,
    CONSTRAINT [pk_tien_do_sinh_vien] PRIMARY KEY CLUSTERED ([id])
);

ALTER TABLE [dbo].[he_thong_ngoai] ADD CONSTRAINT [df_he_thong_ngoai_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[nguoi_dung] ADD CONSTRAINT [df_nguoi_dung_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[dinh_danh_dang_nhap] ADD CONSTRAINT [df_dinh_danh_dang_nhap_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[phan_quyen_nguoi_dung] ADD CONSTRAINT [df_phan_quyen_nguoi_dung_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[don_vi] ADD CONSTRAINT [df_don_vi_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[dan_toc] ADD CONSTRAINT [df_dan_toc_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[quoc_tich] ADD CONSTRAINT [df_quoc_tich_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[dia_phuong] ADD CONSTRAINT [df_dia_phuong_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[khoa_hoc] ADD CONSTRAINT [df_khoa_hoc_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[hoc_ky] ADD CONSTRAINT [df_hoc_ky_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[nganh_quoc_gia] ADD CONSTRAINT [df_nganh_quoc_gia_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[nganh_noi_bo] ADD CONSTRAINT [df_nganh_noi_bo_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[chuong_trinh_dao_tao] ADD CONSTRAINT [df_chuong_trinh_dao_tao_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[lop_sinh_hoat] ADD CONSTRAINT [df_lop_sinh_hoat_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[sinh_vien] ADD CONSTRAINT [df_sinh_vien_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[qua_trinh_theo_hoc] ADD CONSTRAINT [df_qua_trinh_theo_hoc_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[phan_cong_can_su_lop] ADD CONSTRAINT [df_phan_cong_can_su_lop_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[can_bo] ADD CONSTRAINT [df_can_bo_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[phan_cong_co_van_lop] ADD CONSTRAINT [df_phan_cong_co_van_lop_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[du_lieu_hoc_vu_dong_bo] ADD CONSTRAINT [df_du_lieu_hoc_vu_dong_bo_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[van_ban] ADD CONSTRAINT [df_van_ban_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[phien_ban_van_ban] ADD CONSTRAINT [df_phien_ban_van_ban_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[yeu_cau_ky] ADD CONSTRAINT [df_yeu_cau_ky_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[buoc_ky] ADD CONSTRAINT [df_buoc_ky_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[yeu_cau_chung_thu_ca] ADD CONSTRAINT [df_yeu_cau_chung_thu_ca_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[thong_bao] ADD CONSTRAINT [df_thong_bao_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[nguoi_nhan_thong_bao] ADD CONSTRAINT [df_nguoi_nhan_thong_bao_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[cong_viec] ADD CONSTRAINT [df_cong_viec_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[nhat_ky_thao_tac] ADD CONSTRAINT [df_nhat_ky_thao_tac_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[hang_cho_gui] ADD CONSTRAINT [df_hang_cho_gui_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_chinh_sach] ADD CONSTRAINT [df_mghp_chinh_sach_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_phien_ban_chinh_sach] ADD CONSTRAINT [df_mghp_phien_ban_chinh_sach_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_dot_xet] ADD CONSTRAINT [df_mghp_dot_xet_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_ho_so] ADD CONSTRAINT [df_mghp_ho_so_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_phien_ban_ho_so] ADD CONSTRAINT [df_mghp_phien_ban_ho_so_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_giay_to_ho_so] ADD CONSTRAINT [df_mghp_giay_to_ho_so_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_nhat_ky_ho_so] ADD CONSTRAINT [df_mghp_nhat_ky_ho_so_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_bien_ban] ADD CONSTRAINT [df_mghp_bien_ban_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_quyet_dinh] ADD CONSTRAINT [df_mghp_quyet_dinh_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_phien_ban_qd] ADD CONSTRAINT [df_mghp_phien_ban_qd_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_lien_ket_phien_ban_van_ban] ADD CONSTRAINT [df_mghp_lien_ket_phien_ban_van_ban_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_quyen_huong] ADD CONSTRAINT [df_mghp_quyen_huong_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_ket_qua_hoc_ky] ADD CONSTRAINT [df_mghp_ket_qua_hoc_ky_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_dot_gui_ke_toan] ADD CONSTRAINT [df_mghp_dot_gui_ke_toan_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[mghp_cong_bo] ADD CONSTRAINT [df_mghp_cong_bo_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[hoc_phan] ADD CONSTRAINT [df_hoc_phan_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[khung_chuong_trinh] ADD CONSTRAINT [df_khung_chuong_trinh_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[diem_ky_hoc] ADD CONSTRAINT [df_diem_ky_hoc_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[diem_mien] ADD CONSTRAINT [df_diem_mien_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[tieu_thuc] ADD CONSTRAINT [df_tieu_thuc_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[phong_hoc] ADD CONSTRAINT [df_phong_hoc_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[lich_su_dung_phong] ADD CONSTRAINT [df_lich_su_dung_phong_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[khoa_phong] ADD CONSTRAINT [df_khoa_phong_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[chi_tiet_khoa_phong] ADD CONSTRAINT [df_chi_tiet_khoa_phong_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[phan_lop_ren_luyen] ADD CONSTRAINT [df_phan_lop_ren_luyen_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[ghep_lop_sinh_hoat] ADD CONSTRAINT [df_ghep_lop_sinh_hoat_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[tien_do_sinh_vien] ADD CONSTRAINT [df_tien_do_sinh_vien_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[hoc_phan] ADD CONSTRAINT [df_hoc_phan_dang_hien_dien_nguon] DEFAULT (1) FOR [dang_hien_dien_nguon];
ALTER TABLE [dbo].[khung_chuong_trinh] ADD CONSTRAINT [df_khung_chuong_trinh_dang_hien_dien_nguon] DEFAULT (1) FOR [dang_hien_dien_nguon];
ALTER TABLE [dbo].[diem_ky_hoc] ADD CONSTRAINT [df_diem_ky_hoc_dang_hien_dien_nguon] DEFAULT (1) FOR [dang_hien_dien_nguon];
ALTER TABLE [dbo].[diem_mien] ADD CONSTRAINT [df_diem_mien_dang_hien_dien_nguon] DEFAULT (1) FOR [dang_hien_dien_nguon];
ALTER TABLE [dbo].[tieu_thuc] ADD CONSTRAINT [df_tieu_thuc_dang_hien_dien_nguon] DEFAULT (1) FOR [dang_hien_dien_nguon];
ALTER TABLE [dbo].[phong_hoc] ADD CONSTRAINT [df_phong_hoc_dang_hien_dien_nguon] DEFAULT (1) FOR [dang_hien_dien_nguon];
ALTER TABLE [dbo].[lich_su_dung_phong] ADD CONSTRAINT [df_lich_su_dung_phong_dang_hien_dien_nguon] DEFAULT (1) FOR [dang_hien_dien_nguon];
ALTER TABLE [dbo].[khoa_phong] ADD CONSTRAINT [df_khoa_phong_dang_hien_dien_nguon] DEFAULT (1) FOR [dang_hien_dien_nguon];
ALTER TABLE [dbo].[chi_tiet_khoa_phong] ADD CONSTRAINT [df_chi_tiet_khoa_phong_dang_hien_dien_nguon] DEFAULT (1) FOR [dang_hien_dien_nguon];
ALTER TABLE [dbo].[phan_lop_ren_luyen] ADD CONSTRAINT [df_phan_lop_ren_luyen_dang_hien_dien_nguon] DEFAULT (1) FOR [dang_hien_dien_nguon];
ALTER TABLE [dbo].[ghep_lop_sinh_hoat] ADD CONSTRAINT [df_ghep_lop_sinh_hoat_dang_hien_dien_nguon] DEFAULT (1) FOR [dang_hien_dien_nguon];
ALTER TABLE [dbo].[tien_do_sinh_vien] ADD CONSTRAINT [df_tien_do_sinh_vien_dang_hien_dien_nguon] DEFAULT (1) FOR [dang_hien_dien_nguon];
CREATE UNIQUE NONCLUSTERED INDEX [uq_he_thong_ngoai_ma] ON [dbo].[he_thong_ngoai] ([ma]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_nguoi_dung_dia_chi_email] ON [dbo].[nguoi_dung] ([dia_chi_email]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_vai_tro_ma] ON [dbo].[vai_tro] ([ma]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_don_vi_ma] ON [dbo].[don_vi] ([ma]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_hoc_ky_ma] ON [dbo].[hoc_ky] ([ma]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_hoc_ky_so_thu_tu_filtered] ON [dbo].[hoc_ky] ([so_thu_tu]) WHERE [so_thu_tu] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_sinh_vien_nguoi_dung_id_filtered] ON [dbo].[sinh_vien] ([nguoi_dung_id]) WHERE [nguoi_dung_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_sinh_vien_mssv] ON [dbo].[sinh_vien] ([mssv]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_qua_trinh_theo_hoc_ma_qua_trinh_hoc] ON [dbo].[qua_trinh_theo_hoc] ([ma_qua_trinh_hoc]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_can_bo_nguoi_dung_id_filtered] ON [dbo].[can_bo] ([nguoi_dung_id]) WHERE [nguoi_dung_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_can_bo_ma_cb] ON [dbo].[can_bo] ([ma_cb]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_van_ban_ma_van_ban] ON [dbo].[van_ban] ([ma_van_ban]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_yeu_cau_ky_ma_yeu_cau] ON [dbo].[yeu_cau_ky] ([ma_yeu_cau]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_yeu_cau_ky_ban_tin_gui_id_filtered] ON [dbo].[yeu_cau_ky] ([ban_tin_gui_id]) WHERE [ban_tin_gui_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_nhat_ky_thao_tac_su_kien_id] ON [dbo].[nhat_ky_thao_tac] ([su_kien_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_cau_hinh_he_thong_khoa_cau_hinh] ON [dbo].[cau_hinh_he_thong] ([khoa_cau_hinh]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_hang_cho_gui_su_kien_id] ON [dbo].[hang_cho_gui] ([su_kien_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_chinh_sach_ma] ON [dbo].[mghp_chinh_sach] ([ma]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_dot_xet_ma_dot_xet] ON [dbo].[mghp_dot_xet] ([ma_dot_xet]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_ho_so_ma_ho_so] ON [dbo].[mghp_ho_so] ([ma_ho_so]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_phien_ban_ho_so_ma_thao_tac_nop] ON [dbo].[mghp_phien_ban_ho_so] ([ma_thao_tac_nop]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_tep_minh_chung_giay_to_ho_so_id_filtered] ON [dbo].[mghp_tep_minh_chung] ([giay_to_ho_so_id]) WHERE [giay_to_ho_so_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_bien_ban_ma_bb] ON [dbo].[mghp_bien_ban] ([ma_bb]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_quyet_dinh_ma_qd] ON [dbo].[mghp_quyet_dinh] ([ma_qd]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_lien_ket_phien_ban_van_ban_phien_ban_van_ban_dung_chung_id_filtered] ON [dbo].[mghp_lien_ket_phien_ban_van_ban] ([phien_ban_van_ban_dung_chung_id]) WHERE [phien_ban_van_ban_dung_chung_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_quyen_huong_ma_quyen_huong] ON [dbo].[mghp_quyen_huong] ([ma_quyen_huong]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_ket_qua_hoc_ky_dong_qd_id_filtered] ON [dbo].[mghp_ket_qua_hoc_ky] ([dong_qd_id]) WHERE [dong_qd_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_dot_gui_ke_toan_yeu_cau_ben_ngoai_id] ON [dbo].[mghp_dot_gui_ke_toan] ([yeu_cau_ben_ngoai_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_dot_gui_ke_toan_ban_tin_gui_id_filtered] ON [dbo].[mghp_dot_gui_ke_toan] ([ban_tin_gui_id]) WHERE [ban_tin_gui_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_cong_bo_phien_ban_qd_id_filtered] ON [dbo].[mghp_cong_bo] ([phien_ban_qd_id]) WHERE [phien_ban_qd_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_cong_bo_ma_cong_bo] ON [dbo].[mghp_cong_bo] ([ma_cong_bo]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_cong_sv_thong_tin_sv_sinh_vien_nguon_id_filtered] ON [dbo].[cong_sv_thong_tin_sv] ([sinh_vien_nguon_id]) WHERE [sinh_vien_nguon_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_cong_sv_thong_tin_sv_mssv] ON [dbo].[cong_sv_thong_tin_sv] ([mssv]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_cong_sv_ket_qua_mghp_ket_qua_hoc_ky_id_filtered] ON [dbo].[cong_sv_ket_qua_mghp] ([ket_qua_hoc_ky_id]) WHERE [ket_qua_hoc_ky_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_ban_tin_ke_toan_nhan_ban_tin_nhan_id_filtered] ON [dbo].[mghp_ban_tin_ke_toan_nhan] ([ban_tin_nhan_id]) WHERE [ban_tin_nhan_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_dinh_danh_dang_nhap_ma_nha_cung_cap_ma_chu_the_dang_nhap] ON [dbo].[dinh_danh_dang_nhap] ([ma_nha_cung_cap], [ma_chu_the_dang_nhap]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_nguoi_nhan_thong_bao_thong_bao_id_nguoi_dung_id] ON [dbo].[nguoi_nhan_thong_bao] ([thong_bao_id], [nguoi_dung_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_phien_ban_van_ban_van_ban_id_so_phien_ban] ON [dbo].[phien_ban_van_ban] ([van_ban_id], [so_phien_ban]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_buoc_ky_yeu_cau_ky_id_thu_tu_ky] ON [dbo].[buoc_ky] ([yeu_cau_ky_id], [thu_tu_ky]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_bien_nhan_ky_he_thong_cung_cap_id_chu_ky_ben_ngoai_id] ON [dbo].[bien_nhan_ky] ([he_thong_cung_cap_id], [chu_ky_ben_ngoai_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_hang_tiep_nhan_he_thong_nguon_id_khoa_chong_trung] ON [dbo].[hang_tiep_nhan] ([he_thong_nguon_id], [khoa_chong_trung]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_phien_ban_chinh_sach_chinh_sach_id_phien_ban] ON [dbo].[mghp_phien_ban_chinh_sach] ([chinh_sach_id], [phien_ban]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_nhom_yeu_cau_giay_to_phien_ban_chinh_sach_id_ma] ON [dbo].[mghp_nhom_yeu_cau_giay_to] ([phien_ban_chinh_sach_id], [ma]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_giay_to_bat_buoc_nhom_id_ma] ON [dbo].[mghp_giay_to_bat_buoc] ([nhom_id], [ma]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_chinh_sach_dot_xet_dot_xet_id_phien_ban_chinh_sach_id] ON [dbo].[mghp_chinh_sach_dot_xet] ([dot_xet_id], [phien_ban_chinh_sach_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_ho_so_dot_xet_id_qua_trinh_hoc_id_chinh_sach_id] ON [dbo].[mghp_ho_so] ([dot_xet_id], [qua_trinh_hoc_id], [chinh_sach_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_phien_ban_ho_so_ho_so_id_lan_sua_doi] ON [dbo].[mghp_phien_ban_ho_so] ([ho_so_id], [lan_sua_doi]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_giay_to_ho_so_phien_ban_ho_so_id_giay_to_bat_buoc_id] ON [dbo].[mghp_giay_to_ho_so] ([phien_ban_ho_so_id], [giay_to_bat_buoc_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_yeu_cau_bo_sung_ho_so_id_so_vong] ON [dbo].[mghp_yeu_cau_bo_sung] ([ho_so_id], [so_vong]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_phien_ban_qd_quyet_dinh_id_lan_sua_doi] ON [dbo].[mghp_phien_ban_qd] ([quyet_dinh_id], [lan_sua_doi]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_dong_qd_phien_ban_qd_id_qua_trinh_hoc_id_hoc_ky_ap_dung_id_ma_pham_vi] ON [dbo].[mghp_dong_qd] ([phien_ban_qd_id], [qua_trinh_hoc_id], [hoc_ky_ap_dung_id], [ma_pham_vi]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_quyen_huong_qua_trinh_hoc_id_chinh_sach_id_ma_pham_vi] ON [dbo].[mghp_quyen_huong] ([qua_trinh_hoc_id], [chinh_sach_id], [ma_pham_vi]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_bien_dong_quyen_huong_quyen_huong_id_so_phien_ban] ON [dbo].[mghp_bien_dong_quyen_huong] ([quyen_huong_id], [so_phien_ban]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_ket_qua_hoc_ky_ma_ket_qua_huong_lan_sua_doi] ON [dbo].[mghp_ket_qua_hoc_ky] ([ma_ket_qua_huong], [lan_sua_doi]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_tieu_chi_chinh_sach_phien_ban_chinh_sach_id_ma] ON [dbo].[mghp_tieu_chi_chinh_sach] ([phien_ban_chinh_sach_id], [ma]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_ket_qua_tieu_chi_danh_gia_dieu_kien_id_tieu_chi_chinh_sach_id] ON [dbo].[mghp_ket_qua_tieu_chi] ([danh_gia_dieu_kien_id], [tieu_chi_chinh_sach_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_hoc_ky_de_nghi_phien_ban_ho_so_id_hoc_ky_id] ON [dbo].[mghp_hoc_ky_de_nghi] ([phien_ban_ho_so_id], [hoc_ky_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_ung_vien_phien_ban_qd_phien_ban_qd_id_khoang_du_dieu_kien_id] ON [dbo].[mghp_ung_vien_phien_ban_qd] ([phien_ban_qd_id], [khoang_du_dieu_kien_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_doi_chieu_ke_khai_phien_ban_ho_so_id_ma_truong] ON [dbo].[mghp_doi_chieu_ke_khai] ([phien_ban_ho_so_id], [ma_truong]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_khoang_huong_dong_qd_id_ngay_bat_dau] ON [dbo].[mghp_khoang_huong] ([dong_qd_id], [ngay_bat_dau]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_khoang_du_dieu_kien_danh_gia_dieu_kien_id_ngay_bat_dau] ON [dbo].[mghp_khoang_du_dieu_kien] ([danh_gia_dieu_kien_id], [ngay_bat_dau]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_cong_sv_su_kien_cap_nhat_he_thong_nguon_su_kien_id] ON [dbo].[cong_sv_su_kien_cap_nhat] ([he_thong_nguon], [su_kien_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_cong_sv_ket_qua_mghp_ma_ket_qua_huong_lan_sua_doi_ket_qua_huong] ON [dbo].[cong_sv_ket_qua_mghp] ([ma_ket_qua_huong], [lan_sua_doi_ket_qua_huong]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_chi_tiet_nhat_ky_thao_tac_nhat_ky_id_ten_truong] ON [dbo].[chi_tiet_nhat_ky_thao_tac] ([nhat_ky_id], [ten_truong]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_cong_sv_su_kien_cap_nhat_loai_doi_tuong_ma_doi_tuong_lan_sua_doi_nguon] ON [dbo].[cong_sv_su_kien_cap_nhat] ([loai_doi_tuong], [ma_doi_tuong], [lan_sua_doi_nguon]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_ket_qua_hoc_ky_qua_trinh_hoc_id_hoc_ky_id_ma_pham_vi_filtered] ON [dbo].[mghp_ket_qua_hoc_ky] ([qua_trinh_hoc_id], [hoc_ky_id], [ma_pham_vi]) WHERE [la_ban_hien_hanh] = 1;
CREATE UNIQUE NONCLUSTERED INDEX [uq_cong_sv_ket_qua_mghp_sinh_vien_id_ma_qua_trinh_hoc_ma_hoc_ky_ma_pham_vi_filtered] ON [dbo].[cong_sv_ket_qua_mghp] ([sinh_vien_id], [ma_qua_trinh_hoc], [ma_hoc_ky], [ma_pham_vi]) WHERE [la_ban_hien_hanh] = 1;
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_xac_nhan_ke_toan_he_thong_cung_cap_id_xac_nhan_ben_ngoai_id_filtered] ON [dbo].[mghp_xac_nhan_ke_toan] ([he_thong_cung_cap_id], [xac_nhan_ben_ngoai_id]) WHERE [xac_nhan_ben_ngoai_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_dong_gui_ke_toan_dot_gui_id_dong_id] ON [dbo].[mghp_dong_gui_ke_toan] ([dot_gui_id], [dong_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_danh_gia_dieu_kien_ho_so_nguon_id_hoc_ky_ap_dung_id_ma_pham_vi_phien_ban_chinh_sach_id_so_phien_ban_danh_gia_filtered] ON [dbo].[mghp_danh_gia_dieu_kien] ([ho_so_nguon_id], [hoc_ky_ap_dung_id], [ma_pham_vi], [phien_ban_chinh_sach_id], [so_phien_ban_danh_gia]) WHERE [ho_so_nguon_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_danh_gia_dieu_kien_ho_so_nguon_id_hoc_ky_ap_dung_id_ma_pham_vi_phien_ban_chinh_sach_id_filtered] ON [dbo].[mghp_danh_gia_dieu_kien] ([ho_so_nguon_id], [hoc_ky_ap_dung_id], [ma_pham_vi], [phien_ban_chinh_sach_id]) WHERE [ho_so_nguon_id] IS NOT NULL AND [la_ban_hien_hanh] = 1;
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_danh_gia_dieu_kien_quyen_huong_nguon_id_hoc_ky_ap_dung_id_ma_pham_vi_phien_ban_chinh_sach_id_so_phien_b_22482a9f97d2] ON [dbo].[mghp_danh_gia_dieu_kien] ([quyen_huong_nguon_id], [hoc_ky_ap_dung_id], [ma_pham_vi], [phien_ban_chinh_sach_id], [so_phien_ban_danh_gia]) WHERE [quyen_huong_nguon_id] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_mghp_danh_gia_dieu_kien_quyen_huong_nguon_id_hoc_ky_ap_dung_id_ma_pham_vi_phien_ban_chinh_sach_id_filtered] ON [dbo].[mghp_danh_gia_dieu_kien] ([quyen_huong_nguon_id], [hoc_ky_ap_dung_id], [ma_pham_vi], [phien_ban_chinh_sach_id]) WHERE [quyen_huong_nguon_id] IS NOT NULL AND [la_ban_hien_hanh] = 1;
CREATE UNIQUE NONCLUSTERED INDEX [uq_tep_dinh_kem_ten_cloud_ma_tai_nguyen_filtered] ON [dbo].[tep_dinh_kem] ([ten_cloud], [ma_tai_nguyen]) WHERE [ten_cloud] IS NOT NULL AND [ma_tai_nguyen] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_don_vi_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[don_vi] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_dan_toc_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[dan_toc] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_quoc_tich_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[quoc_tich] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_dia_phuong_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[dia_phuong] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_khoa_hoc_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[khoa_hoc] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_hoc_ky_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[hoc_ky] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_nganh_quoc_gia_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[nganh_quoc_gia] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_nganh_noi_bo_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[nganh_noi_bo] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_chuong_trinh_dao_tao_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[chuong_trinh_dao_tao] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_lop_sinh_hoat_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[lop_sinh_hoat] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_sinh_vien_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[sinh_vien] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_du_lieu_hoc_vu_dong_bo_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[du_lieu_hoc_vu_dong_bo] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_phan_cong_co_van_lop_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[phan_cong_co_van_lop] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_lan_kiem_tra_tep_tep_dinh_kem_id_so_thu_tu_filtered] ON [dbo].[lan_kiem_tra_tep] ([tep_dinh_kem_id], [so_thu_tu]) WHERE [tep_dinh_kem_id] IS NOT NULL AND [so_thu_tu] IS NOT NULL;
CREATE UNIQUE NONCLUSTERED INDEX [uq_hoc_phan_he_thong_nguon_id_ma_dinh_danh_ben_ngoai] ON [dbo].[hoc_phan] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_khung_chuong_trinh_he_thong_nguon_id_ma_dinh_danh_ben_ngoai] ON [dbo].[khung_chuong_trinh] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_diem_ky_hoc_he_thong_nguon_id_ma_dinh_danh_ben_ngoai] ON [dbo].[diem_ky_hoc] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_diem_mien_he_thong_nguon_id_ma_dinh_danh_ben_ngoai] ON [dbo].[diem_mien] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_tieu_thuc_he_thong_nguon_id_ma_dinh_danh_ben_ngoai] ON [dbo].[tieu_thuc] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_phong_hoc_he_thong_nguon_id_ma_dinh_danh_ben_ngoai] ON [dbo].[phong_hoc] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_phong_hoc_he_thong_nguon_id_ma_phong_nguon] ON [dbo].[phong_hoc] ([he_thong_nguon_id], [ma_phong_nguon]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_lich_su_dung_phong_he_thong_nguon_id_ma_dinh_danh_ben_ngoai] ON [dbo].[lich_su_dung_phong] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_khoa_phong_he_thong_nguon_id_ma_dinh_danh_ben_ngoai] ON [dbo].[khoa_phong] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_chi_tiet_khoa_phong_he_thong_nguon_id_ma_dinh_danh_ben_ngoai] ON [dbo].[chi_tiet_khoa_phong] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_phan_lop_ren_luyen_he_thong_nguon_id_ma_dinh_danh_ben_ngoai] ON [dbo].[phan_lop_ren_luyen] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_phan_lop_ren_luyen_he_thong_nguon_id_hoc_ky_id_sinh_vien_id_lop_ren_luyen_id] ON [dbo].[phan_lop_ren_luyen] ([he_thong_nguon_id], [hoc_ky_id], [sinh_vien_id], [lop_ren_luyen_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_ghep_lop_sinh_hoat_he_thong_nguon_id_ma_dinh_danh_ben_ngoai] ON [dbo].[ghep_lop_sinh_hoat] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_ghep_lop_sinh_hoat_he_thong_nguon_id_hoc_ky_id_lop_goc_id_lop_nhan_id] ON [dbo].[ghep_lop_sinh_hoat] ([he_thong_nguon_id], [hoc_ky_id], [lop_goc_id], [lop_nhan_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_tien_do_sinh_vien_he_thong_nguon_id_ma_dinh_danh_ben_ngoai] ON [dbo].[tien_do_sinh_vien] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_tien_do_sinh_vien_he_thong_nguon_id_hoc_ky_id_sinh_vien_id] ON [dbo].[tien_do_sinh_vien] ([he_thong_nguon_id], [hoc_ky_id], [sinh_vien_id]);
ALTER TABLE [dbo].[dinh_danh_dang_nhap] WITH CHECK ADD CONSTRAINT [fk_dinh_danh_dang_nhap_nguoi_dung_id] FOREIGN KEY ([nguoi_dung_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_quyen_nguoi_dung] WITH CHECK ADD CONSTRAINT [fk_phan_quyen_nguoi_dung_nguoi_dung_id] FOREIGN KEY ([nguoi_dung_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_quyen_nguoi_dung] WITH CHECK ADD CONSTRAINT [fk_phan_quyen_nguoi_dung_vai_tro_id] FOREIGN KEY ([vai_tro_id]) REFERENCES [dbo].[vai_tro] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_quyen_nguoi_dung] WITH CHECK ADD CONSTRAINT [fk_phan_quyen_nguoi_dung_don_vi_id] FOREIGN KEY ([don_vi_id]) REFERENCES [dbo].[don_vi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_quyen_nguoi_dung] WITH CHECK ADD CONSTRAINT [fk_phan_quyen_nguoi_dung_nguoi_cap_quyen_id] FOREIGN KEY ([nguoi_cap_quyen_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[don_vi] WITH CHECK ADD CONSTRAINT [fk_don_vi_don_vi_cha_id] FOREIGN KEY ([don_vi_cha_id]) REFERENCES [dbo].[don_vi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[don_vi] WITH CHECK ADD CONSTRAINT [fk_don_vi_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[dan_toc] WITH CHECK ADD CONSTRAINT [fk_dan_toc_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[quoc_tich] WITH CHECK ADD CONSTRAINT [fk_quoc_tich_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[dia_phuong] WITH CHECK ADD CONSTRAINT [fk_dia_phuong_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[khoa_hoc] WITH CHECK ADD CONSTRAINT [fk_khoa_hoc_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[hoc_ky] WITH CHECK ADD CONSTRAINT [fk_hoc_ky_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[nganh_quoc_gia] WITH CHECK ADD CONSTRAINT [fk_nganh_quoc_gia_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[nganh_noi_bo] WITH CHECK ADD CONSTRAINT [fk_nganh_noi_bo_nganh_quoc_gia_id] FOREIGN KEY ([nganh_quoc_gia_id]) REFERENCES [dbo].[nganh_quoc_gia] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[nganh_noi_bo] WITH CHECK ADD CONSTRAINT [fk_nganh_noi_bo_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[chuong_trinh_dao_tao] WITH CHECK ADD CONSTRAINT [fk_chuong_trinh_dao_tao_nganh_noi_bo_id] FOREIGN KEY ([nganh_noi_bo_id]) REFERENCES [dbo].[nganh_noi_bo] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[chuong_trinh_dao_tao] WITH CHECK ADD CONSTRAINT [fk_chuong_trinh_dao_tao_nganh_quoc_gia_id] FOREIGN KEY ([nganh_quoc_gia_id]) REFERENCES [dbo].[nganh_quoc_gia] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[chuong_trinh_dao_tao] WITH CHECK ADD CONSTRAINT [fk_chuong_trinh_dao_tao_hoc_ky_bat_dau_id] FOREIGN KEY ([hoc_ky_bat_dau_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[chuong_trinh_dao_tao] WITH CHECK ADD CONSTRAINT [fk_chuong_trinh_dao_tao_hoc_ky_ket_thuc_id] FOREIGN KEY ([hoc_ky_ket_thuc_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[chuong_trinh_dao_tao] WITH CHECK ADD CONSTRAINT [fk_chuong_trinh_dao_tao_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lop_sinh_hoat] WITH CHECK ADD CONSTRAINT [fk_lop_sinh_hoat_chuong_trinh_dao_tao_id] FOREIGN KEY ([chuong_trinh_dao_tao_id]) REFERENCES [dbo].[chuong_trinh_dao_tao] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lop_sinh_hoat] WITH CHECK ADD CONSTRAINT [fk_lop_sinh_hoat_khoa_quan_ly_id] FOREIGN KEY ([khoa_quan_ly_id]) REFERENCES [dbo].[don_vi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lop_sinh_hoat] WITH CHECK ADD CONSTRAINT [fk_lop_sinh_hoat_khoa_hoc_id] FOREIGN KEY ([khoa_hoc_id]) REFERENCES [dbo].[khoa_hoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lop_sinh_hoat] WITH CHECK ADD CONSTRAINT [fk_lop_sinh_hoat_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[sinh_vien] WITH CHECK ADD CONSTRAINT [fk_sinh_vien_nguoi_dung_id] FOREIGN KEY ([nguoi_dung_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[sinh_vien] WITH CHECK ADD CONSTRAINT [fk_sinh_vien_noi_sinh_id] FOREIGN KEY ([noi_sinh_id]) REFERENCES [dbo].[dia_phuong] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[sinh_vien] WITH CHECK ADD CONSTRAINT [fk_sinh_vien_dan_toc_id] FOREIGN KEY ([dan_toc_id]) REFERENCES [dbo].[dan_toc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[sinh_vien] WITH CHECK ADD CONSTRAINT [fk_sinh_vien_quoc_tich_id] FOREIGN KEY ([quoc_tich_id]) REFERENCES [dbo].[quoc_tich] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[sinh_vien] WITH CHECK ADD CONSTRAINT [fk_sinh_vien_lop_hien_tai_id] FOREIGN KEY ([lop_hien_tai_id]) REFERENCES [dbo].[lop_sinh_hoat] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[sinh_vien] WITH CHECK ADD CONSTRAINT [fk_sinh_vien_khoa_quan_ly_hien_tai_id] FOREIGN KEY ([khoa_quan_ly_hien_tai_id]) REFERENCES [dbo].[don_vi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[sinh_vien] WITH CHECK ADD CONSTRAINT [fk_sinh_vien_khoa_hoc_hien_tai_id] FOREIGN KEY ([khoa_hoc_hien_tai_id]) REFERENCES [dbo].[khoa_hoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[sinh_vien] WITH CHECK ADD CONSTRAINT [fk_sinh_vien_chuong_trinh_hien_tai_id] FOREIGN KEY ([chuong_trinh_hien_tai_id]) REFERENCES [dbo].[chuong_trinh_dao_tao] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[sinh_vien] WITH CHECK ADD CONSTRAINT [fk_sinh_vien_hoc_ky_ap_dung_nguon_id] FOREIGN KEY ([hoc_ky_ap_dung_nguon_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[sinh_vien] WITH CHECK ADD CONSTRAINT [fk_sinh_vien_hoc_ky_bat_dau_nguon_id] FOREIGN KEY ([hoc_ky_bat_dau_nguon_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[sinh_vien] WITH CHECK ADD CONSTRAINT [fk_sinh_vien_hoc_ky_ket_thuc_nguon_id] FOREIGN KEY ([hoc_ky_ket_thuc_nguon_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[sinh_vien] WITH CHECK ADD CONSTRAINT [fk_sinh_vien_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_theo_hoc] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_theo_hoc_sinh_vien_id] FOREIGN KEY ([sinh_vien_id]) REFERENCES [dbo].[sinh_vien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_theo_hoc] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_theo_hoc_chuong_trinh_dao_tao_id] FOREIGN KEY ([chuong_trinh_dao_tao_id]) REFERENCES [dbo].[chuong_trinh_dao_tao] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_theo_hoc] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_theo_hoc_khoa_hoc_id] FOREIGN KEY ([khoa_hoc_id]) REFERENCES [dbo].[khoa_hoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_theo_hoc] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_theo_hoc_hoc_ky_bat_dau_id] FOREIGN KEY ([hoc_ky_bat_dau_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_theo_hoc] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_theo_hoc_hoc_ky_ket_thuc_nguon_id] FOREIGN KEY ([hoc_ky_ket_thuc_nguon_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_theo_hoc] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_theo_hoc_hoc_ky_ap_dung_trang_thai_id] FOREIGN KEY ([hoc_ky_ap_dung_trang_thai_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_theo_hoc] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_theo_hoc_nguoi_xac_minh_id] FOREIGN KEY ([nguoi_xac_minh_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_theo_hoc] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_theo_hoc_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lich_su_trang_thai_sv] WITH CHECK ADD CONSTRAINT [fk_lich_su_trang_thai_sv_qua_trinh_hoc_id] FOREIGN KEY ([qua_trinh_hoc_id]) REFERENCES [dbo].[qua_trinh_theo_hoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lich_su_trang_thai_sv] WITH CHECK ADD CONSTRAINT [fk_lich_su_trang_thai_sv_hoc_ky_ap_dung_id] FOREIGN KEY ([hoc_ky_ap_dung_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lich_su_trang_thai_sv] WITH CHECK ADD CONSTRAINT [fk_lich_su_trang_thai_sv_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lich_su_trang_thai_sv] WITH CHECK ADD CONSTRAINT [fk_lich_su_trang_thai_sv_nguoi_xac_minh_id] FOREIGN KEY ([nguoi_xac_minh_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_thuoc_lop] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_thuoc_lop_qua_trinh_hoc_id] FOREIGN KEY ([qua_trinh_hoc_id]) REFERENCES [dbo].[qua_trinh_theo_hoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_thuoc_lop] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_thuoc_lop_lop_id] FOREIGN KEY ([lop_id]) REFERENCES [dbo].[lop_sinh_hoat] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_thuoc_lop] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_thuoc_lop_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_thuoc_lop] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_thuoc_lop_nguoi_xac_minh_id] FOREIGN KEY ([nguoi_xac_minh_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_cong_can_su_lop] WITH CHECK ADD CONSTRAINT [fk_phan_cong_can_su_lop_qua_trinh_thuoc_lop_id] FOREIGN KEY ([qua_trinh_thuoc_lop_id]) REFERENCES [dbo].[qua_trinh_thuoc_lop] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_cong_can_su_lop] WITH CHECK ADD CONSTRAINT [fk_phan_cong_can_su_lop_nguoi_bo_nhiem_id] FOREIGN KEY ([nguoi_bo_nhiem_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[can_bo] WITH CHECK ADD CONSTRAINT [fk_can_bo_nguoi_dung_id] FOREIGN KEY ([nguoi_dung_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[can_bo] WITH CHECK ADD CONSTRAINT [fk_can_bo_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_cong_tac] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_cong_tac_can_bo_id] FOREIGN KEY ([can_bo_id]) REFERENCES [dbo].[can_bo] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_cong_tac] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_cong_tac_don_vi_id] FOREIGN KEY ([don_vi_id]) REFERENCES [dbo].[don_vi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_cong_tac] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_cong_tac_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_cong_co_van_lop] WITH CHECK ADD CONSTRAINT [fk_phan_cong_co_van_lop_lop_id] FOREIGN KEY ([lop_id]) REFERENCES [dbo].[lop_sinh_hoat] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_cong_co_van_lop] WITH CHECK ADD CONSTRAINT [fk_phan_cong_co_van_lop_can_bo_id] FOREIGN KEY ([can_bo_id]) REFERENCES [dbo].[can_bo] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_cong_co_van_lop] WITH CHECK ADD CONSTRAINT [fk_phan_cong_co_van_lop_hoc_ky_id] FOREIGN KEY ([hoc_ky_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_cong_co_van_lop] WITH CHECK ADD CONSTRAINT [fk_phan_cong_co_van_lop_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[du_lieu_hoc_vu_dong_bo] WITH CHECK ADD CONSTRAINT [fk_du_lieu_hoc_vu_dong_bo_sinh_vien_id] FOREIGN KEY ([sinh_vien_id]) REFERENCES [dbo].[sinh_vien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[du_lieu_hoc_vu_dong_bo] WITH CHECK ADD CONSTRAINT [fk_du_lieu_hoc_vu_dong_bo_hoc_ky_id] FOREIGN KEY ([hoc_ky_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[du_lieu_hoc_vu_dong_bo] WITH CHECK ADD CONSTRAINT [fk_du_lieu_hoc_vu_dong_bo_chuong_trinh_dao_tao_id] FOREIGN KEY ([chuong_trinh_dao_tao_id]) REFERENCES [dbo].[chuong_trinh_dao_tao] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[du_lieu_hoc_vu_dong_bo] WITH CHECK ADD CONSTRAINT [fk_du_lieu_hoc_vu_dong_bo_qua_trinh_hoc_id] FOREIGN KEY ([qua_trinh_hoc_id]) REFERENCES [dbo].[qua_trinh_theo_hoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[du_lieu_hoc_vu_dong_bo] WITH CHECK ADD CONSTRAINT [fk_du_lieu_hoc_vu_dong_bo_dot_dong_bo_id] FOREIGN KEY ([dot_dong_bo_id]) REFERENCES [dbo].[dot_dong_bo] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[du_lieu_hoc_vu_dong_bo] WITH CHECK ADD CONSTRAINT [fk_du_lieu_hoc_vu_dong_bo_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[tep_dinh_kem] WITH CHECK ADD CONSTRAINT [fk_tep_dinh_kem_nguoi_so_huu_id] FOREIGN KEY ([nguoi_so_huu_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[tep_dinh_kem] WITH CHECK ADD CONSTRAINT [fk_tep_dinh_kem_nguoi_tai_len_id] FOREIGN KEY ([nguoi_tai_len_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[van_ban] WITH CHECK ADD CONSTRAINT [fk_van_ban_don_vi_so_huu_id] FOREIGN KEY ([don_vi_so_huu_id]) REFERENCES [dbo].[don_vi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[van_ban] WITH CHECK ADD CONSTRAINT [fk_van_ban_nguoi_tao_id] FOREIGN KEY ([nguoi_tao_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phien_ban_van_ban] WITH CHECK ADD CONSTRAINT [fk_phien_ban_van_ban_van_ban_id] FOREIGN KEY ([van_ban_id]) REFERENCES [dbo].[van_ban] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phien_ban_van_ban] WITH CHECK ADD CONSTRAINT [fk_phien_ban_van_ban_tep_goc_id] FOREIGN KEY ([tep_goc_id]) REFERENCES [dbo].[tep_dinh_kem] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phien_ban_van_ban] WITH CHECK ADD CONSTRAINT [fk_phien_ban_van_ban_nguoi_doi_chieu_id] FOREIGN KEY ([nguoi_doi_chieu_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phien_ban_van_ban] WITH CHECK ADD CONSTRAINT [fk_phien_ban_van_ban_nguoi_chot_id] FOREIGN KEY ([nguoi_chot_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phien_ban_van_ban] WITH CHECK ADD CONSTRAINT [fk_phien_ban_van_ban_nguoi_tao_id] FOREIGN KEY ([nguoi_tao_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[ghi_nhan_xem_van_ban] WITH CHECK ADD CONSTRAINT [fk_ghi_nhan_xem_van_ban_phien_ban_van_ban_id] FOREIGN KEY ([phien_ban_van_ban_id]) REFERENCES [dbo].[phien_ban_van_ban] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[ghi_nhan_xem_van_ban] WITH CHECK ADD CONSTRAINT [fk_ghi_nhan_xem_van_ban_nguoi_nhan_xet_id] FOREIGN KEY ([nguoi_nhan_xet_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[yeu_cau_ky] WITH CHECK ADD CONSTRAINT [fk_yeu_cau_ky_phien_ban_van_ban_id] FOREIGN KEY ([phien_ban_van_ban_id]) REFERENCES [dbo].[phien_ban_van_ban] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[yeu_cau_ky] WITH CHECK ADD CONSTRAINT [fk_yeu_cau_ky_he_thong_cung_cap_id] FOREIGN KEY ([he_thong_cung_cap_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[yeu_cau_ky] WITH CHECK ADD CONSTRAINT [fk_yeu_cau_ky_nguoi_yeu_cau_id] FOREIGN KEY ([nguoi_yeu_cau_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[yeu_cau_ky] WITH CHECK ADD CONSTRAINT [fk_yeu_cau_ky_nguoi_huy_id] FOREIGN KEY ([nguoi_huy_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[buoc_ky] WITH CHECK ADD CONSTRAINT [fk_buoc_ky_yeu_cau_ky_id] FOREIGN KEY ([yeu_cau_ky_id]) REFERENCES [dbo].[yeu_cau_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[buoc_ky] WITH CHECK ADD CONSTRAINT [fk_buoc_ky_nguoi_dung_noi_bo_id] FOREIGN KEY ([nguoi_dung_noi_bo_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[buoc_ky] WITH CHECK ADD CONSTRAINT [fk_buoc_ky_can_bo_id] FOREIGN KEY ([can_bo_id]) REFERENCES [dbo].[can_bo] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[buoc_ky] WITH CHECK ADD CONSTRAINT [fk_buoc_ky_tep_anh_chu_ky_id] FOREIGN KEY ([tep_anh_chu_ky_id]) REFERENCES [dbo].[tep_dinh_kem] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[uy_quyen_ky] WITH CHECK ADD CONSTRAINT [fk_uy_quyen_ky_buoc_ky_id] FOREIGN KEY ([buoc_ky_id]) REFERENCES [dbo].[buoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[uy_quyen_ky] WITH CHECK ADD CONSTRAINT [fk_uy_quyen_ky_tep_minh_chung_id] FOREIGN KEY ([tep_minh_chung_id]) REFERENCES [dbo].[tep_dinh_kem] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[uy_quyen_ky] WITH CHECK ADD CONSTRAINT [fk_uy_quyen_ky_nguoi_ghi_nhan_id] FOREIGN KEY ([nguoi_ghi_nhan_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lan_goi_dich_vu_ky] WITH CHECK ADD CONSTRAINT [fk_lan_goi_dich_vu_ky_yeu_cau_ky_id] FOREIGN KEY ([yeu_cau_ky_id]) REFERENCES [dbo].[yeu_cau_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lan_goi_dich_vu_ky] WITH CHECK ADD CONSTRAINT [fk_lan_goi_dich_vu_ky_buoc_ky_id] FOREIGN KEY ([buoc_ky_id]) REFERENCES [dbo].[buoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lan_goi_dich_vu_ky] WITH CHECK ADD CONSTRAINT [fk_lan_goi_dich_vu_ky_uy_quyen_id] FOREIGN KEY ([uy_quyen_id]) REFERENCES [dbo].[uy_quyen_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[bien_nhan_ky] WITH CHECK ADD CONSTRAINT [fk_bien_nhan_ky_yeu_cau_ky_id] FOREIGN KEY ([yeu_cau_ky_id]) REFERENCES [dbo].[yeu_cau_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[bien_nhan_ky] WITH CHECK ADD CONSTRAINT [fk_bien_nhan_ky_buoc_ky_id] FOREIGN KEY ([buoc_ky_id]) REFERENCES [dbo].[buoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[bien_nhan_ky] WITH CHECK ADD CONSTRAINT [fk_bien_nhan_ky_lan_goi_id] FOREIGN KEY ([lan_goi_id]) REFERENCES [dbo].[lan_goi_dich_vu_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[bien_nhan_ky] WITH CHECK ADD CONSTRAINT [fk_bien_nhan_ky_he_thong_cung_cap_id] FOREIGN KEY ([he_thong_cung_cap_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[tep_ket_qua_ky] WITH CHECK ADD CONSTRAINT [fk_tep_ket_qua_ky_yeu_cau_ky_id] FOREIGN KEY ([yeu_cau_ky_id]) REFERENCES [dbo].[yeu_cau_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[tep_ket_qua_ky] WITH CHECK ADD CONSTRAINT [fk_tep_ket_qua_ky_bien_nhan_ky_id] FOREIGN KEY ([bien_nhan_ky_id]) REFERENCES [dbo].[bien_nhan_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[tep_ket_qua_ky] WITH CHECK ADD CONSTRAINT [fk_tep_ket_qua_ky_tep_dinh_kem_id] FOREIGN KEY ([tep_dinh_kem_id]) REFERENCES [dbo].[tep_dinh_kem] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[yeu_cau_chung_thu_ca] WITH CHECK ADD CONSTRAINT [fk_yeu_cau_chung_thu_ca_he_thong_cung_cap_id] FOREIGN KEY ([he_thong_cung_cap_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[yeu_cau_chung_thu_ca] WITH CHECK ADD CONSTRAINT [fk_yeu_cau_chung_thu_ca_buoc_ky_id] FOREIGN KEY ([buoc_ky_id]) REFERENCES [dbo].[buoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[yeu_cau_chung_thu_ca] WITH CHECK ADD CONSTRAINT [fk_yeu_cau_chung_thu_ca_nguoi_yeu_cau_id] FOREIGN KEY ([nguoi_yeu_cau_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[thong_bao] WITH CHECK ADD CONSTRAINT [fk_thong_bao_nguoi_tao_id] FOREIGN KEY ([nguoi_tao_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[doi_tuong_nhan_thong_bao] WITH CHECK ADD CONSTRAINT [fk_doi_tuong_nhan_thong_bao_thong_bao_id] FOREIGN KEY ([thong_bao_id]) REFERENCES [dbo].[thong_bao] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[doi_tuong_nhan_thong_bao] WITH CHECK ADD CONSTRAINT [fk_doi_tuong_nhan_thong_bao_vai_tro_id] FOREIGN KEY ([vai_tro_id]) REFERENCES [dbo].[vai_tro] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[doi_tuong_nhan_thong_bao] WITH CHECK ADD CONSTRAINT [fk_doi_tuong_nhan_thong_bao_don_vi_id] FOREIGN KEY ([don_vi_id]) REFERENCES [dbo].[don_vi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[doi_tuong_nhan_thong_bao] WITH CHECK ADD CONSTRAINT [fk_doi_tuong_nhan_thong_bao_lop_id] FOREIGN KEY ([lop_id]) REFERENCES [dbo].[lop_sinh_hoat] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[doi_tuong_nhan_thong_bao] WITH CHECK ADD CONSTRAINT [fk_doi_tuong_nhan_thong_bao_nguoi_dung_id] FOREIGN KEY ([nguoi_dung_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[nguoi_nhan_thong_bao] WITH CHECK ADD CONSTRAINT [fk_nguoi_nhan_thong_bao_thong_bao_id] FOREIGN KEY ([thong_bao_id]) REFERENCES [dbo].[thong_bao] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[nguoi_nhan_thong_bao] WITH CHECK ADD CONSTRAINT [fk_nguoi_nhan_thong_bao_nguoi_dung_id] FOREIGN KEY ([nguoi_dung_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_viec] WITH CHECK ADD CONSTRAINT [fk_cong_viec_nguoi_duoc_giao_id] FOREIGN KEY ([nguoi_duoc_giao_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_viec] WITH CHECK ADD CONSTRAINT [fk_cong_viec_vai_tro_duoc_giao_id] FOREIGN KEY ([vai_tro_duoc_giao_id]) REFERENCES [dbo].[vai_tro] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_viec] WITH CHECK ADD CONSTRAINT [fk_cong_viec_don_vi_id] FOREIGN KEY ([don_vi_id]) REFERENCES [dbo].[don_vi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_viec] WITH CHECK ADD CONSTRAINT [fk_cong_viec_nguoi_nhan_xu_ly_id] FOREIGN KEY ([nguoi_nhan_xu_ly_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_viec] WITH CHECK ADD CONSTRAINT [fk_cong_viec_nguoi_hoan_tat_id] FOREIGN KEY ([nguoi_hoan_tat_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_viec] WITH CHECK ADD CONSTRAINT [fk_cong_viec_nguoi_tao_id] FOREIGN KEY ([nguoi_tao_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [fk_nhat_ky_thao_tac_nguoi_dung_id] FOREIGN KEY ([nguoi_dung_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [fk_nhat_ky_thao_tac_he_thong_ngoai_id] FOREIGN KEY ([he_thong_ngoai_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cau_hinh_he_thong] WITH CHECK ADD CONSTRAINT [fk_cau_hinh_he_thong_nguoi_cap_nhat_id] FOREIGN KEY ([nguoi_cap_nhat_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[hang_cho_gui] WITH CHECK ADD CONSTRAINT [fk_hang_cho_gui_he_thong_dich_id] FOREIGN KEY ([he_thong_dich_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[hang_tiep_nhan] WITH CHECK ADD CONSTRAINT [fk_hang_tiep_nhan_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[dot_dong_bo] WITH CHECK ADD CONSTRAINT [fk_dot_dong_bo_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[dot_dong_bo] WITH CHECK ADD CONSTRAINT [fk_dot_dong_bo_nguoi_yeu_cau_id] FOREIGN KEY ([nguoi_yeu_cau_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_chinh_sach] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_chinh_sach_chinh_sach_id] FOREIGN KEY ([chinh_sach_id]) REFERENCES [dbo].[mghp_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_chinh_sach] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_chinh_sach_nguoi_tao_id] FOREIGN KEY ([nguoi_tao_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_nhom_yeu_cau_giay_to] WITH CHECK ADD CONSTRAINT [fk_mghp_nhom_yeu_cau_giay_to_phien_ban_chinh_sach_id] FOREIGN KEY ([phien_ban_chinh_sach_id]) REFERENCES [dbo].[mghp_phien_ban_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_giay_to_bat_buoc] WITH CHECK ADD CONSTRAINT [fk_mghp_giay_to_bat_buoc_nhom_id] FOREIGN KEY ([nhom_id]) REFERENCES [dbo].[mghp_nhom_yeu_cau_giay_to] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_chinh_sach_dot_xet] WITH CHECK ADD CONSTRAINT [fk_mghp_chinh_sach_dot_xet_dot_xet_id] FOREIGN KEY ([dot_xet_id]) REFERENCES [dbo].[mghp_dot_xet] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_chinh_sach_dot_xet] WITH CHECK ADD CONSTRAINT [fk_mghp_chinh_sach_dot_xet_phien_ban_chinh_sach_id] FOREIGN KEY ([phien_ban_chinh_sach_id]) REFERENCES [dbo].[mghp_phien_ban_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_chinh_sach_dot_xet] WITH CHECK ADD CONSTRAINT [fk_mghp_chinh_sach_dot_xet_tep_mau_don_id] FOREIGN KEY ([tep_mau_don_id]) REFERENCES [dbo].[tep_dinh_kem] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dot_xet] WITH CHECK ADD CONSTRAINT [fk_mghp_dot_xet_hoc_ky_id] FOREIGN KEY ([hoc_ky_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dot_xet] WITH CHECK ADD CONSTRAINT [fk_mghp_dot_xet_thong_bao_dot_xet_id] FOREIGN KEY ([thong_bao_dot_xet_id]) REFERENCES [dbo].[thong_bao] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dot_xet] WITH CHECK ADD CONSTRAINT [fk_mghp_dot_xet_nguoi_khoa_id] FOREIGN KEY ([nguoi_khoa_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dot_xet] WITH CHECK ADD CONSTRAINT [fk_mghp_dot_xet_nguoi_chot_hoi_dong_id] FOREIGN KEY ([nguoi_chot_hoi_dong_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dot_xet] WITH CHECK ADD CONSTRAINT [fk_mghp_dot_xet_nguoi_tao_id] FOREIGN KEY ([nguoi_tao_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dot_xet] WITH CHECK ADD CONSTRAINT [fk_mghp_dot_xet_nguoi_khoa_cau_hinh_id] FOREIGN KEY ([nguoi_khoa_cau_hinh_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_ho_so_dot_xet_id] FOREIGN KEY ([dot_xet_id]) REFERENCES [dbo].[mghp_dot_xet] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_ho_so_sinh_vien_id] FOREIGN KEY ([sinh_vien_id]) REFERENCES [dbo].[sinh_vien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_ho_so_qua_trinh_hoc_id] FOREIGN KEY ([qua_trinh_hoc_id]) REFERENCES [dbo].[qua_trinh_theo_hoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_ho_so_nguoi_duoc_giao_id] FOREIGN KEY ([nguoi_duoc_giao_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_ho_so_nguoi_duyet_id] FOREIGN KEY ([nguoi_duyet_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_ho_so_nguoi_tao_id] FOREIGN KEY ([nguoi_tao_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_ho_so_chinh_sach_id] FOREIGN KEY ([chinh_sach_id]) REFERENCES [dbo].[mghp_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_ho_so_phien_ban_chinh_sach_id] FOREIGN KEY ([phien_ban_chinh_sach_id]) REFERENCES [dbo].[mghp_phien_ban_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_ho_so_ho_so_id] FOREIGN KEY ([ho_so_id]) REFERENCES [dbo].[mghp_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_ho_so_yeu_cau_bo_sung_id] FOREIGN KEY ([yeu_cau_bo_sung_id]) REFERENCES [dbo].[mghp_yeu_cau_bo_sung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_ho_so_nguoi_nop_id] FOREIGN KEY ([nguoi_nop_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_giay_to_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_giay_to_ho_so_phien_ban_ho_so_id] FOREIGN KEY ([phien_ban_ho_so_id]) REFERENCES [dbo].[mghp_phien_ban_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_giay_to_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_giay_to_ho_so_giay_to_bat_buoc_id] FOREIGN KEY ([giay_to_bat_buoc_id]) REFERENCES [dbo].[mghp_giay_to_bat_buoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_giay_to_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_giay_to_ho_so_nguoi_nop_id] FOREIGN KEY ([nguoi_nop_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_tep_minh_chung] WITH CHECK ADD CONSTRAINT [fk_mghp_tep_minh_chung_giay_to_ho_so_id] FOREIGN KEY ([giay_to_ho_so_id]) REFERENCES [dbo].[mghp_giay_to_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_tep_minh_chung] WITH CHECK ADD CONSTRAINT [fk_mghp_tep_minh_chung_tep_dinh_kem_id] FOREIGN KEY ([tep_dinh_kem_id]) REFERENCES [dbo].[tep_dinh_kem] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_kiem_tra_giay_to] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_kiem_tra_giay_to_ket_qua_kiem_tra_duoc_thay_the_id] FOREIGN KEY ([ket_qua_kiem_tra_duoc_thay_the_id]) REFERENCES [dbo].[mghp_ket_qua_kiem_tra_giay_to] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_kiem_tra_giay_to] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_kiem_tra_giay_to_nguoi_thuc_hien_id] FOREIGN KEY ([nguoi_thuc_hien_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_kiem_tra_giay_to] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_kiem_tra_giay_to_giay_to_ho_so_id] FOREIGN KEY ([giay_to_ho_so_id]) REFERENCES [dbo].[mghp_giay_to_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_yeu_cau_bo_sung] WITH CHECK ADD CONSTRAINT [fk_mghp_yeu_cau_bo_sung_ho_so_id] FOREIGN KEY ([ho_so_id]) REFERENCES [dbo].[mghp_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_yeu_cau_bo_sung] WITH CHECK ADD CONSTRAINT [fk_mghp_yeu_cau_bo_sung_phien_ban_lam_co_so_id] FOREIGN KEY ([phien_ban_lam_co_so_id]) REFERENCES [dbo].[mghp_phien_ban_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_yeu_cau_bo_sung] WITH CHECK ADD CONSTRAINT [fk_mghp_yeu_cau_bo_sung_nguoi_yeu_cau_id] FOREIGN KEY ([nguoi_yeu_cau_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_noi_dung_bo_sung] WITH CHECK ADD CONSTRAINT [fk_mghp_noi_dung_bo_sung_yeu_cau_bo_sung_id] FOREIGN KEY ([yeu_cau_bo_sung_id]) REFERENCES [dbo].[mghp_yeu_cau_bo_sung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_noi_dung_bo_sung] WITH CHECK ADD CONSTRAINT [fk_mghp_noi_dung_bo_sung_giay_to_bat_buoc_id] FOREIGN KEY ([giay_to_bat_buoc_id]) REFERENCES [dbo].[mghp_giay_to_bat_buoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_noi_dung_bo_sung] WITH CHECK ADD CONSTRAINT [fk_mghp_noi_dung_bo_sung_giay_to_ho_so_id] FOREIGN KEY ([giay_to_ho_so_id]) REFERENCES [dbo].[mghp_giay_to_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_noi_dung_bo_sung] WITH CHECK ADD CONSTRAINT [fk_mghp_noi_dung_bo_sung_hoc_ky_ap_dung_id] FOREIGN KEY ([hoc_ky_ap_dung_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_noi_dung_bo_sung] WITH CHECK ADD CONSTRAINT [fk_mghp_noi_dung_bo_sung_danh_gia_dieu_kien_id] FOREIGN KEY ([danh_gia_dieu_kien_id]) REFERENCES [dbo].[mghp_danh_gia_dieu_kien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_noi_dung_bo_sung] WITH CHECK ADD CONSTRAINT [fk_mghp_noi_dung_bo_sung_giay_to_tra_loi_id] FOREIGN KEY ([giay_to_tra_loi_id]) REFERENCES [dbo].[mghp_giay_to_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_noi_dung_bo_sung] WITH CHECK ADD CONSTRAINT [fk_mghp_noi_dung_bo_sung_nguoi_xu_ly_id] FOREIGN KEY ([nguoi_xu_ly_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_nhat_ky_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_nhat_ky_ho_so_ho_so_id] FOREIGN KEY ([ho_so_id]) REFERENCES [dbo].[mghp_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_nhat_ky_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_nhat_ky_ho_so_phien_ban_ho_so_id] FOREIGN KEY ([phien_ban_ho_so_id]) REFERENCES [dbo].[mghp_phien_ban_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_nhat_ky_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_nhat_ky_ho_so_nguoi_thao_tac_id] FOREIGN KEY ([nguoi_thao_tac_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_danh_gia_dieu_kien_sinh_vien_id] FOREIGN KEY ([sinh_vien_id]) REFERENCES [dbo].[sinh_vien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_danh_gia_dieu_kien_qua_trinh_hoc_id] FOREIGN KEY ([qua_trinh_hoc_id]) REFERENCES [dbo].[qua_trinh_theo_hoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_danh_gia_dieu_kien_hoc_ky_ap_dung_id] FOREIGN KEY ([hoc_ky_ap_dung_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_danh_gia_dieu_kien_phien_ban_chinh_sach_id] FOREIGN KEY ([phien_ban_chinh_sach_id]) REFERENCES [dbo].[mghp_phien_ban_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_danh_gia_dieu_kien_quyen_huong_nguon_id] FOREIGN KEY ([quyen_huong_nguon_id]) REFERENCES [dbo].[mghp_quyen_huong] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_danh_gia_dieu_kien_nguoi_danh_gia_id] FOREIGN KEY ([nguoi_danh_gia_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_danh_gia_dieu_kien_dot_xet_id] FOREIGN KEY ([dot_xet_id]) REFERENCES [dbo].[mghp_dot_xet] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_danh_gia_dieu_kien_phien_ban_ho_so_nguon_id] FOREIGN KEY ([phien_ban_ho_so_nguon_id]) REFERENCES [dbo].[mghp_phien_ban_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_danh_gia_dieu_kien_danh_gia_truoc_id] FOREIGN KEY ([danh_gia_truoc_id]) REFERENCES [dbo].[mghp_danh_gia_dieu_kien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_bien_ban] WITH CHECK ADD CONSTRAINT [fk_mghp_bien_ban_dot_xet_id] FOREIGN KEY ([dot_xet_id]) REFERENCES [dbo].[mghp_dot_xet] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_bien_ban] WITH CHECK ADD CONSTRAINT [fk_mghp_bien_ban_nguoi_tao_id] FOREIGN KEY ([nguoi_tao_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_bien_ban] WITH CHECK ADD CONSTRAINT [fk_mghp_bien_ban_phien_ban_qd_nguon_id] FOREIGN KEY ([phien_ban_qd_nguon_id]) REFERENCES [dbo].[mghp_phien_ban_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyet_dinh] WITH CHECK ADD CONSTRAINT [fk_mghp_quyet_dinh_dot_xet_id] FOREIGN KEY ([dot_xet_id]) REFERENCES [dbo].[mghp_dot_xet] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyet_dinh] WITH CHECK ADD CONSTRAINT [fk_mghp_quyet_dinh_don_vi_ban_hanh_id] FOREIGN KEY ([don_vi_ban_hanh_id]) REFERENCES [dbo].[don_vi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyet_dinh] WITH CHECK ADD CONSTRAINT [fk_mghp_quyet_dinh_quyet_dinh_duoc_thay_the_id] FOREIGN KEY ([quyet_dinh_duoc_thay_the_id]) REFERENCES [dbo].[mghp_quyet_dinh] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyet_dinh] WITH CHECK ADD CONSTRAINT [fk_mghp_quyet_dinh_nguoi_tao_id] FOREIGN KEY ([nguoi_tao_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_qd_quyet_dinh_id] FOREIGN KEY ([quyet_dinh_id]) REFERENCES [dbo].[mghp_quyet_dinh] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_qd_nguoi_chot_id] FOREIGN KEY ([nguoi_chot_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_qd_phien_ban_truoc_id] FOREIGN KEY ([phien_ban_truoc_id]) REFERENCES [dbo].[mghp_phien_ban_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_qd_nguoi_tao_id] FOREIGN KEY ([nguoi_tao_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_lien_ket_phien_ban_van_ban] WITH CHECK ADD CONSTRAINT [fk_mghp_lien_ket_phien_ban_van_ban_bien_ban_id] FOREIGN KEY ([bien_ban_id]) REFERENCES [dbo].[mghp_bien_ban] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_lien_ket_phien_ban_van_ban] WITH CHECK ADD CONSTRAINT [fk_mghp_lien_ket_phien_ban_van_ban_phien_ban_qd_id] FOREIGN KEY ([phien_ban_qd_id]) REFERENCES [dbo].[mghp_phien_ban_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_lien_ket_phien_ban_van_ban] WITH CHECK ADD CONSTRAINT [fk_mghp_lien_ket_phien_ban_van_ban_phien_ban_van_ban_dung_chung_id] FOREIGN KEY ([phien_ban_van_ban_dung_chung_id]) REFERENCES [dbo].[phien_ban_van_ban] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_qd_phien_ban_qd_id] FOREIGN KEY ([phien_ban_qd_id]) REFERENCES [dbo].[mghp_phien_ban_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_qd_sinh_vien_id] FOREIGN KEY ([sinh_vien_id]) REFERENCES [dbo].[sinh_vien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_qd_qua_trinh_hoc_id] FOREIGN KEY ([qua_trinh_hoc_id]) REFERENCES [dbo].[qua_trinh_theo_hoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_qd_hoc_ky_ap_dung_id] FOREIGN KEY ([hoc_ky_ap_dung_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_qd_phien_ban_chinh_sach_id] FOREIGN KEY ([phien_ban_chinh_sach_id]) REFERENCES [dbo].[mghp_phien_ban_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_qd_quyen_huong_nguon_id] FOREIGN KEY ([quyen_huong_nguon_id]) REFERENCES [dbo].[mghp_quyen_huong] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_qd_danh_gia_dieu_kien_id] FOREIGN KEY ([danh_gia_dieu_kien_id]) REFERENCES [dbo].[mghp_danh_gia_dieu_kien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_qd_dong_duoc_thay_the_id] FOREIGN KEY ([dong_duoc_thay_the_id]) REFERENCES [dbo].[mghp_dong_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_qd_phien_ban_ho_so_nguon_id] FOREIGN KEY ([phien_ban_ho_so_nguon_id]) REFERENCES [dbo].[mghp_phien_ban_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_quyen_huong_sinh_vien_id] FOREIGN KEY ([sinh_vien_id]) REFERENCES [dbo].[sinh_vien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_quyen_huong_qua_trinh_hoc_id] FOREIGN KEY ([qua_trinh_hoc_id]) REFERENCES [dbo].[qua_trinh_theo_hoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_quyen_huong_phien_ban_chinh_sach_id] FOREIGN KEY ([phien_ban_chinh_sach_id]) REFERENCES [dbo].[mghp_phien_ban_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_quyen_huong_dong_qd_cong_nhan_id] FOREIGN KEY ([dong_qd_cong_nhan_id]) REFERENCES [dbo].[mghp_dong_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_quyen_huong_hoc_ky_bat_dau_huong_id] FOREIGN KEY ([hoc_ky_bat_dau_huong_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_quyen_huong_hoc_ky_ket_thuc_huong_id] FOREIGN KEY ([hoc_ky_ket_thuc_huong_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_quyen_huong_dong_qd_thu_hoi_id] FOREIGN KEY ([dong_qd_thu_hoi_id]) REFERENCES [dbo].[mghp_dong_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_quyen_huong_phien_ban_ho_so_nguon_id] FOREIGN KEY ([phien_ban_ho_so_nguon_id]) REFERENCES [dbo].[mghp_phien_ban_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_bien_dong_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_bien_dong_quyen_huong_quyen_huong_id] FOREIGN KEY ([quyen_huong_id]) REFERENCES [dbo].[mghp_quyen_huong] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_bien_dong_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_bien_dong_quyen_huong_dong_qd_id] FOREIGN KEY ([dong_qd_id]) REFERENCES [dbo].[mghp_dong_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_bien_dong_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_bien_dong_quyen_huong_nguoi_ghi_nhan_id] FOREIGN KEY ([nguoi_ghi_nhan_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_hoc_ky] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_hoc_ky_sinh_vien_id] FOREIGN KEY ([sinh_vien_id]) REFERENCES [dbo].[sinh_vien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_hoc_ky] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_hoc_ky_qua_trinh_hoc_id] FOREIGN KEY ([qua_trinh_hoc_id]) REFERENCES [dbo].[qua_trinh_theo_hoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_hoc_ky] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_hoc_ky_hoc_ky_id] FOREIGN KEY ([hoc_ky_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_hoc_ky] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_hoc_ky_phien_ban_chinh_sach_id] FOREIGN KEY ([phien_ban_chinh_sach_id]) REFERENCES [dbo].[mghp_phien_ban_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_hoc_ky] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_hoc_ky_dong_qd_id] FOREIGN KEY ([dong_qd_id]) REFERENCES [dbo].[mghp_dong_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_hoc_ky] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_hoc_ky_quyen_huong_id] FOREIGN KEY ([quyen_huong_id]) REFERENCES [dbo].[mghp_quyen_huong] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_hoc_ky] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_hoc_ky_danh_gia_dieu_kien_id] FOREIGN KEY ([danh_gia_dieu_kien_id]) REFERENCES [dbo].[mghp_danh_gia_dieu_kien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_hoc_ky] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_hoc_ky_ket_qua_huong_duoc_thay_the_id] FOREIGN KEY ([ket_qua_huong_duoc_thay_the_id]) REFERENCES [dbo].[mghp_ket_qua_hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dot_gui_ke_toan] WITH CHECK ADD CONSTRAINT [fk_mghp_dot_gui_ke_toan_he_thong_dich_id] FOREIGN KEY ([he_thong_dich_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dot_gui_ke_toan] WITH CHECK ADD CONSTRAINT [fk_mghp_dot_gui_ke_toan_phien_ban_qd_id] FOREIGN KEY ([phien_ban_qd_id]) REFERENCES [dbo].[mghp_phien_ban_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dot_gui_ke_toan] WITH CHECK ADD CONSTRAINT [fk_mghp_dot_gui_ke_toan_hoc_ky_id] FOREIGN KEY ([hoc_ky_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dot_gui_ke_toan] WITH CHECK ADD CONSTRAINT [fk_mghp_dot_gui_ke_toan_ban_tin_gui_id] FOREIGN KEY ([ban_tin_gui_id]) REFERENCES [dbo].[hang_cho_gui] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dot_gui_ke_toan] WITH CHECK ADD CONSTRAINT [fk_mghp_dot_gui_ke_toan_nguoi_tao_id] FOREIGN KEY ([nguoi_tao_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_gui_ke_toan] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_gui_ke_toan_dot_gui_id] FOREIGN KEY ([dot_gui_id]) REFERENCES [dbo].[mghp_dot_gui_ke_toan] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_gui_ke_toan] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_gui_ke_toan_ket_qua_huong_id] FOREIGN KEY ([ket_qua_huong_id]) REFERENCES [dbo].[mghp_ket_qua_hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_xac_nhan_ke_toan] WITH CHECK ADD CONSTRAINT [fk_mghp_xac_nhan_ke_toan_dong_gui_id] FOREIGN KEY ([dong_gui_id]) REFERENCES [dbo].[mghp_dong_gui_ke_toan] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_xac_nhan_ke_toan] WITH CHECK ADD CONSTRAINT [fk_mghp_xac_nhan_ke_toan_ban_tin_nhan_id] FOREIGN KEY ([ban_tin_nhan_id]) REFERENCES [dbo].[hang_tiep_nhan] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_xac_nhan_ke_toan] WITH CHECK ADD CONSTRAINT [fk_mghp_xac_nhan_ke_toan_he_thong_cung_cap_id] FOREIGN KEY ([he_thong_cung_cap_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_cong_bo] WITH CHECK ADD CONSTRAINT [fk_mghp_cong_bo_phien_ban_qd_id] FOREIGN KEY ([phien_ban_qd_id]) REFERENCES [dbo].[mghp_phien_ban_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_cong_bo] WITH CHECK ADD CONSTRAINT [fk_mghp_cong_bo_thong_bao_id] FOREIGN KEY ([thong_bao_id]) REFERENCES [dbo].[thong_bao] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_cong_bo] WITH CHECK ADD CONSTRAINT [fk_mghp_cong_bo_nguoi_cong_bo_id] FOREIGN KEY ([nguoi_cong_bo_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_sv_thong_tin_sv] WITH CHECK ADD CONSTRAINT [fk_cong_sv_thong_tin_sv_sinh_vien_nguon_id] FOREIGN KEY ([sinh_vien_nguon_id]) REFERENCES [dbo].[sinh_vien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_sv_su_kien_cap_nhat] WITH CHECK ADD CONSTRAINT [fk_cong_sv_su_kien_cap_nhat_cong_bo_id] FOREIGN KEY ([cong_bo_id]) REFERENCES [dbo].[mghp_cong_bo] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_sv_ket_qua_mghp] WITH CHECK ADD CONSTRAINT [fk_cong_sv_ket_qua_mghp_sinh_vien_id] FOREIGN KEY ([sinh_vien_id]) REFERENCES [dbo].[cong_sv_thong_tin_sv] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_sv_ket_qua_mghp] WITH CHECK ADD CONSTRAINT [fk_cong_sv_ket_qua_mghp_ket_qua_hoc_ky_id] FOREIGN KEY ([ket_qua_hoc_ky_id]) REFERENCES [dbo].[mghp_ket_qua_hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_sv_ket_qua_mghp] WITH CHECK ADD CONSTRAINT [fk_cong_sv_ket_qua_mghp_cong_bo_id] FOREIGN KEY ([cong_bo_id]) REFERENCES [dbo].[mghp_cong_bo] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_sv_ket_qua_mghp] WITH CHECK ADD CONSTRAINT [fk_cong_sv_ket_qua_mghp_su_kien_cap_nhat_id] FOREIGN KEY ([su_kien_cap_nhat_id]) REFERENCES [dbo].[cong_sv_su_kien_cap_nhat] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_ho_so_nguoi_xac_minh_ngay_nop_id] FOREIGN KEY ([nguoi_xac_minh_ngay_nop_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_ho_so_dot_tiep_nhan_id] FOREIGN KEY ([dot_tiep_nhan_id]) REFERENCES [dbo].[mghp_dot_xet] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_ho_so_quyen_huong_cap_nhat_id] FOREIGN KEY ([quyen_huong_cap_nhat_id]) REFERENCES [dbo].[mghp_quyen_huong] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_ho_so_phien_ban_chinh_sach_ke_khai_id] FOREIGN KEY ([phien_ban_chinh_sach_ke_khai_id]) REFERENCES [dbo].[mghp_phien_ban_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_ho_so_dan_toc_khai_bao_id] FOREIGN KEY ([dan_toc_khai_bao_id]) REFERENCES [dbo].[dan_toc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_ho_so_lop_khai_bao_id] FOREIGN KEY ([lop_khai_bao_id]) REFERENCES [dbo].[lop_sinh_hoat] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_ho_so_khoa_hoc_khai_bao_id] FOREIGN KEY ([khoa_hoc_khai_bao_id]) REFERENCES [dbo].[khoa_hoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_phien_ban_ho_so_khoa_quan_ly_khai_bao_id] FOREIGN KEY ([khoa_quan_ly_khai_bao_id]) REFERENCES [dbo].[don_vi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_giay_to_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_giay_to_ho_so_nguoi_lien_quan_id] FOREIGN KEY ([nguoi_lien_quan_id]) REFERENCES [dbo].[mghp_nguoi_lien_quan] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_giay_to_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_giay_to_ho_so_hoc_ky_xac_nhan_id] FOREIGN KEY ([hoc_ky_xac_nhan_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_giay_to_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_giay_to_ho_so_nguoi_xac_minh_id] FOREIGN KEY ([nguoi_xac_minh_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_yeu_cau_bo_sung] WITH CHECK ADD CONSTRAINT [fk_mghp_yeu_cau_bo_sung_dot_tiep_nhan_id] FOREIGN KEY ([dot_tiep_nhan_id]) REFERENCES [dbo].[mghp_dot_xet] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_yeu_cau_bo_sung] WITH CHECK ADD CONSTRAINT [fk_mghp_yeu_cau_bo_sung_quyen_huong_id] FOREIGN KEY ([quyen_huong_id]) REFERENCES [dbo].[mghp_quyen_huong] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_nhat_ky_ho_so] WITH CHECK ADD CONSTRAINT [fk_mghp_nhat_ky_ho_so_yeu_cau_bo_sung_id] FOREIGN KEY ([yeu_cau_bo_sung_id]) REFERENCES [dbo].[mghp_yeu_cau_bo_sung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_danh_gia_dieu_kien_ho_so_nguon_id] FOREIGN KEY ([ho_so_nguon_id]) REFERENCES [dbo].[mghp_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_danh_gia_dieu_kien_phien_ban_cap_nhat_minh_chung_id] FOREIGN KEY ([phien_ban_cap_nhat_minh_chung_id]) REFERENCES [dbo].[mghp_phien_ban_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_danh_gia_dieu_kien_lich_su_trang_thai_sv_id] FOREIGN KEY ([lich_su_trang_thai_sv_id]) REFERENCES [dbo].[lich_su_trang_thai_sv] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_qd_ung_vien_id] FOREIGN KEY ([ung_vien_id]) REFERENCES [dbo].[mghp_ung_vien_phien_ban_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_qd_hoc_ky_bat_dau_huong_id] FOREIGN KEY ([hoc_ky_bat_dau_huong_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_dong_qd_hoc_ky_ket_thuc_huong_id] FOREIGN KEY ([hoc_ky_ket_thuc_huong_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_bien_dong_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_bien_dong_quyen_huong_bien_dong_truoc_id] FOREIGN KEY ([bien_dong_truoc_id]) REFERENCES [dbo].[mghp_bien_dong_quyen_huong] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_bien_dong_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_bien_dong_quyen_huong_hoc_ky_bat_dau_sau_id] FOREIGN KEY ([hoc_ky_bat_dau_sau_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_bien_dong_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_bien_dong_quyen_huong_hoc_ky_ket_thuc_sau_id] FOREIGN KEY ([hoc_ky_ket_thuc_sau_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_tieu_chi_chinh_sach] WITH CHECK ADD CONSTRAINT [fk_mghp_tieu_chi_chinh_sach_phien_ban_chinh_sach_id] FOREIGN KEY ([phien_ban_chinh_sach_id]) REFERENCES [dbo].[mghp_phien_ban_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_tieu_chi] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_tieu_chi_danh_gia_dieu_kien_id] FOREIGN KEY ([danh_gia_dieu_kien_id]) REFERENCES [dbo].[mghp_danh_gia_dieu_kien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_tieu_chi] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_tieu_chi_tieu_chi_chinh_sach_id] FOREIGN KEY ([tieu_chi_chinh_sach_id]) REFERENCES [dbo].[mghp_tieu_chi_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ket_qua_tieu_chi] WITH CHECK ADD CONSTRAINT [fk_mghp_ket_qua_tieu_chi_nguoi_danh_gia_id] FOREIGN KEY ([nguoi_danh_gia_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_hoc_ky_de_nghi] WITH CHECK ADD CONSTRAINT [fk_mghp_hoc_ky_de_nghi_phien_ban_ho_so_id] FOREIGN KEY ([phien_ban_ho_so_id]) REFERENCES [dbo].[mghp_phien_ban_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_hoc_ky_de_nghi] WITH CHECK ADD CONSTRAINT [fk_mghp_hoc_ky_de_nghi_hoc_ky_id] FOREIGN KEY ([hoc_ky_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_hoc_ky_de_nghi] WITH CHECK ADD CONSTRAINT [fk_mghp_hoc_ky_de_nghi_phien_ban_chinh_sach_id] FOREIGN KEY ([phien_ban_chinh_sach_id]) REFERENCES [dbo].[mghp_phien_ban_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_nguoi_lien_quan] WITH CHECK ADD CONSTRAINT [fk_mghp_nguoi_lien_quan_phien_ban_ho_so_id] FOREIGN KEY ([phien_ban_ho_so_id]) REFERENCES [dbo].[mghp_phien_ban_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_nguoi_lien_quan] WITH CHECK ADD CONSTRAINT [fk_mghp_nguoi_lien_quan_dan_toc_id] FOREIGN KEY ([dan_toc_id]) REFERENCES [dbo].[dan_toc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_nguoi_lien_quan] WITH CHECK ADD CONSTRAINT [fk_mghp_nguoi_lien_quan_don_vi_cong_tac_id] FOREIGN KEY ([don_vi_cong_tac_id]) REFERENCES [dbo].[don_vi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_nguoi_lien_quan] WITH CHECK ADD CONSTRAINT [fk_mghp_nguoi_lien_quan_nguoi_xac_minh_id] FOREIGN KEY ([nguoi_xac_minh_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ung_vien_phien_ban_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_ung_vien_phien_ban_qd_phien_ban_qd_id] FOREIGN KEY ([phien_ban_qd_id]) REFERENCES [dbo].[mghp_phien_ban_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ung_vien_phien_ban_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_ung_vien_phien_ban_qd_danh_gia_dieu_kien_id] FOREIGN KEY ([danh_gia_dieu_kien_id]) REFERENCES [dbo].[mghp_danh_gia_dieu_kien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ung_vien_phien_ban_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_ung_vien_phien_ban_qd_qua_trinh_hoc_id] FOREIGN KEY ([qua_trinh_hoc_id]) REFERENCES [dbo].[qua_trinh_theo_hoc] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ung_vien_phien_ban_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_ung_vien_phien_ban_qd_hoc_ky_id] FOREIGN KEY ([hoc_ky_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_doi_chieu_ke_khai] WITH CHECK ADD CONSTRAINT [fk_mghp_doi_chieu_ke_khai_phien_ban_ho_so_id] FOREIGN KEY ([phien_ban_ho_so_id]) REFERENCES [dbo].[mghp_phien_ban_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_doi_chieu_ke_khai] WITH CHECK ADD CONSTRAINT [fk_mghp_doi_chieu_ke_khai_nguoi_xac_minh_id] FOREIGN KEY ([nguoi_xac_minh_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_can_cu_danh_gia] WITH CHECK ADD CONSTRAINT [fk_mghp_can_cu_danh_gia_danh_gia_dieu_kien_id] FOREIGN KEY ([danh_gia_dieu_kien_id]) REFERENCES [dbo].[mghp_danh_gia_dieu_kien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_can_cu_danh_gia] WITH CHECK ADD CONSTRAINT [fk_mghp_can_cu_danh_gia_giay_to_ho_so_id] FOREIGN KEY ([giay_to_ho_so_id]) REFERENCES [dbo].[mghp_giay_to_ho_so] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_can_cu_danh_gia] WITH CHECK ADD CONSTRAINT [fk_mghp_can_cu_danh_gia_ket_qua_tieu_chi_id] FOREIGN KEY ([ket_qua_tieu_chi_id]) REFERENCES [dbo].[mghp_ket_qua_tieu_chi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_can_cu_danh_gia] WITH CHECK ADD CONSTRAINT [fk_mghp_can_cu_danh_gia_nguoi_xac_nhan_id] FOREIGN KEY ([nguoi_xac_nhan_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_khoang_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_khoang_huong_dong_qd_id] FOREIGN KEY ([dong_qd_id]) REFERENCES [dbo].[mghp_dong_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_khoang_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_khoang_huong_ung_vien_id] FOREIGN KEY ([ung_vien_id]) REFERENCES [dbo].[mghp_ung_vien_phien_ban_qd] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_khoang_du_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_khoang_du_dieu_kien_danh_gia_dieu_kien_id] FOREIGN KEY ([danh_gia_dieu_kien_id]) REFERENCES [dbo].[mghp_danh_gia_dieu_kien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_khoang_du_dieu_kien] WITH CHECK ADD CONSTRAINT [fk_mghp_khoang_du_dieu_kien_nguoi_xac_nhan_id] FOREIGN KEY ([nguoi_xac_nhan_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ung_vien_phien_ban_qd] WITH CHECK ADD CONSTRAINT [fk_mghp_ung_vien_phien_ban_qd_khoang_du_dieu_kien_id] FOREIGN KEY ([khoang_du_dieu_kien_id]) REFERENCES [dbo].[mghp_khoang_du_dieu_kien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_quyen_huong_khoang_huong_cong_nhan_id] FOREIGN KEY ([khoang_huong_cong_nhan_id]) REFERENCES [dbo].[mghp_khoang_huong] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_quyen_huong] WITH CHECK ADD CONSTRAINT [fk_mghp_quyen_huong_chinh_sach_id] FOREIGN KEY ([chinh_sach_id]) REFERENCES [dbo].[mghp_chinh_sach] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[qua_trinh_theo_hoc] WITH CHECK ADD CONSTRAINT [fk_qua_trinh_theo_hoc_tep_can_cu_id] FOREIGN KEY ([tep_can_cu_id]) REFERENCES [dbo].[tep_dinh_kem] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[yeu_cau_ky] WITH CHECK ADD CONSTRAINT [fk_yeu_cau_ky_ban_tin_gui_id] FOREIGN KEY ([ban_tin_gui_id]) REFERENCES [dbo].[hang_cho_gui] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[buoc_ky] WITH CHECK ADD CONSTRAINT [fk_buoc_ky_lan_kiem_tra_chung_thu_id] FOREIGN KEY ([lan_kiem_tra_chung_thu_id]) REFERENCES [dbo].[lan_kiem_tra_chung_thu] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lan_kiem_tra_chung_thu] WITH CHECK ADD CONSTRAINT [fk_lan_kiem_tra_chung_thu_buoc_ky_id] FOREIGN KEY ([buoc_ky_id]) REFERENCES [dbo].[buoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lan_kiem_tra_chung_thu] WITH CHECK ADD CONSTRAINT [fk_lan_kiem_tra_chung_thu_lan_goi_tra_cuu_id] FOREIGN KEY ([lan_goi_tra_cuu_id]) REFERENCES [dbo].[lan_goi_dich_vu_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lan_goi_dich_vu_ky] WITH CHECK ADD CONSTRAINT [fk_lan_goi_dich_vu_ky_lan_kiem_tra_chung_thu_id] FOREIGN KEY ([lan_kiem_tra_chung_thu_id]) REFERENCES [dbo].[lan_kiem_tra_chung_thu] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lan_goi_dich_vu_ky] WITH CHECK ADD CONSTRAINT [fk_lan_goi_dich_vu_ky_tep_dau_vao_id] FOREIGN KEY ([tep_dau_vao_id]) REFERENCES [dbo].[tep_dinh_kem] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[tep_ket_qua_ky] WITH CHECK ADD CONSTRAINT [fk_tep_ket_qua_ky_lan_kiem_tra_tep_id] FOREIGN KEY ([lan_kiem_tra_tep_id]) REFERENCES [dbo].[lan_kiem_tra_tep] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lan_kiem_tra_tep] WITH CHECK ADD CONSTRAINT [fk_lan_kiem_tra_tep_tep_dinh_kem_id] FOREIGN KEY ([tep_dinh_kem_id]) REFERENCES [dbo].[tep_dinh_kem] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lan_kiem_tra_tep] WITH CHECK ADD CONSTRAINT [fk_lan_kiem_tra_tep_tep_ket_qua_ky_id] FOREIGN KEY ([tep_ket_qua_ky_id]) REFERENCES [dbo].[tep_ket_qua_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lan_kiem_tra_tep] WITH CHECK ADD CONSTRAINT [fk_lan_kiem_tra_tep_nguoi_kiem_tra_id] FOREIGN KEY ([nguoi_kiem_tra_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[chi_tiet_nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [fk_chi_tiet_nhat_ky_thao_tac_nhat_ky_id] FOREIGN KEY ([nhat_ky_id]) REFERENCES [dbo].[nhat_ky_thao_tac] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_sv_su_kien_cap_nhat] WITH CHECK ADD CONSTRAINT [fk_cong_sv_su_kien_cap_nhat_sinh_vien_nguon_id] FOREIGN KEY ([sinh_vien_nguon_id]) REFERENCES [dbo].[sinh_vien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[cong_sv_su_kien_cap_nhat] WITH CHECK ADD CONSTRAINT [fk_cong_sv_su_kien_cap_nhat_ket_qua_hoc_ky_nguon_id] FOREIGN KEY ([ket_qua_hoc_ky_nguon_id]) REFERENCES [dbo].[mghp_ket_qua_hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ban_tin_ke_toan_nhan] WITH CHECK ADD CONSTRAINT [fk_mghp_ban_tin_ke_toan_nhan_ban_tin_nhan_id] FOREIGN KEY ([ban_tin_nhan_id]) REFERENCES [dbo].[hang_tiep_nhan] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ban_tin_ke_toan_nhan] WITH CHECK ADD CONSTRAINT [fk_mghp_ban_tin_ke_toan_nhan_dong_gui_id] FOREIGN KEY ([dong_gui_id]) REFERENCES [dbo].[mghp_dong_gui_ke_toan] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_ban_tin_ke_toan_nhan] WITH CHECK ADD CONSTRAINT [fk_mghp_ban_tin_ke_toan_nhan_xac_nhan_noi_bo_id] FOREIGN KEY ([xac_nhan_noi_bo_id]) REFERENCES [dbo].[mghp_xac_nhan_ke_toan] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[sinh_vien] WITH CHECK ADD CONSTRAINT [fk_sinh_vien_hoc_ky_han_hoc_nguon_id] FOREIGN KEY ([hoc_ky_han_hoc_nguon_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[can_bo] WITH CHECK ADD CONSTRAINT [fk_can_bo_don_vi_nguon_id] FOREIGN KEY ([don_vi_nguon_id]) REFERENCES [dbo].[don_vi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_cong_co_van_lop] WITH CHECK ADD CONSTRAINT [fk_phan_cong_co_van_lop_can_bo_chu_nhiem_id] FOREIGN KEY ([can_bo_chu_nhiem_id]) REFERENCES [dbo].[can_bo] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[hoc_phan] WITH CHECK ADD CONSTRAINT [fk_hoc_phan_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[khung_chuong_trinh] WITH CHECK ADD CONSTRAINT [fk_khung_chuong_trinh_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[khung_chuong_trinh] WITH CHECK ADD CONSTRAINT [fk_khung_chuong_trinh_hoc_phan_id] FOREIGN KEY ([hoc_phan_id]) REFERENCES [dbo].[hoc_phan] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[khung_chuong_trinh] WITH CHECK ADD CONSTRAINT [fk_khung_chuong_trinh_hoc_phan_duoc_thay_the_id] FOREIGN KEY ([hoc_phan_duoc_thay_the_id]) REFERENCES [dbo].[hoc_phan] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[diem_ky_hoc] WITH CHECK ADD CONSTRAINT [fk_diem_ky_hoc_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[diem_ky_hoc] WITH CHECK ADD CONSTRAINT [fk_diem_ky_hoc_hoc_ky_id] FOREIGN KEY ([hoc_ky_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[diem_ky_hoc] WITH CHECK ADD CONSTRAINT [fk_diem_ky_hoc_sinh_vien_id] FOREIGN KEY ([sinh_vien_id]) REFERENCES [dbo].[sinh_vien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[diem_ky_hoc] WITH CHECK ADD CONSTRAINT [fk_diem_ky_hoc_hoc_phan_id] FOREIGN KEY ([hoc_phan_id]) REFERENCES [dbo].[hoc_phan] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[diem_ky_hoc] WITH CHECK ADD CONSTRAINT [fk_diem_ky_hoc_hoc_phan_tuong_duong_id] FOREIGN KEY ([hoc_phan_tuong_duong_id]) REFERENCES [dbo].[hoc_phan] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[diem_mien] WITH CHECK ADD CONSTRAINT [fk_diem_mien_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[diem_mien] WITH CHECK ADD CONSTRAINT [fk_diem_mien_hoc_ky_id] FOREIGN KEY ([hoc_ky_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[diem_mien] WITH CHECK ADD CONSTRAINT [fk_diem_mien_hoc_phan_id] FOREIGN KEY ([hoc_phan_id]) REFERENCES [dbo].[hoc_phan] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[diem_mien] WITH CHECK ADD CONSTRAINT [fk_diem_mien_sinh_vien_id] FOREIGN KEY ([sinh_vien_id]) REFERENCES [dbo].[sinh_vien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[diem_mien] WITH CHECK ADD CONSTRAINT [fk_diem_mien_hoc_phan_tuong_duong_id] FOREIGN KEY ([hoc_phan_tuong_duong_id]) REFERENCES [dbo].[hoc_phan] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[tieu_thuc] WITH CHECK ADD CONSTRAINT [fk_tieu_thuc_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phong_hoc] WITH CHECK ADD CONSTRAINT [fk_phong_hoc_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[lich_su_dung_phong] WITH CHECK ADD CONSTRAINT [fk_lich_su_dung_phong_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[khoa_phong] WITH CHECK ADD CONSTRAINT [fk_khoa_phong_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[chi_tiet_khoa_phong] WITH CHECK ADD CONSTRAINT [fk_chi_tiet_khoa_phong_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_lop_ren_luyen] WITH CHECK ADD CONSTRAINT [fk_phan_lop_ren_luyen_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_lop_ren_luyen] WITH CHECK ADD CONSTRAINT [fk_phan_lop_ren_luyen_hoc_ky_id] FOREIGN KEY ([hoc_ky_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_lop_ren_luyen] WITH CHECK ADD CONSTRAINT [fk_phan_lop_ren_luyen_sinh_vien_id] FOREIGN KEY ([sinh_vien_id]) REFERENCES [dbo].[sinh_vien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[phan_lop_ren_luyen] WITH CHECK ADD CONSTRAINT [fk_phan_lop_ren_luyen_lop_ren_luyen_id] FOREIGN KEY ([lop_ren_luyen_id]) REFERENCES [dbo].[lop_sinh_hoat] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[ghep_lop_sinh_hoat] WITH CHECK ADD CONSTRAINT [fk_ghep_lop_sinh_hoat_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[ghep_lop_sinh_hoat] WITH CHECK ADD CONSTRAINT [fk_ghep_lop_sinh_hoat_hoc_ky_id] FOREIGN KEY ([hoc_ky_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[ghep_lop_sinh_hoat] WITH CHECK ADD CONSTRAINT [fk_ghep_lop_sinh_hoat_lop_goc_id] FOREIGN KEY ([lop_goc_id]) REFERENCES [dbo].[lop_sinh_hoat] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[ghep_lop_sinh_hoat] WITH CHECK ADD CONSTRAINT [fk_ghep_lop_sinh_hoat_lop_nhan_id] FOREIGN KEY ([lop_nhan_id]) REFERENCES [dbo].[lop_sinh_hoat] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[tien_do_sinh_vien] WITH CHECK ADD CONSTRAINT [fk_tien_do_sinh_vien_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[tien_do_sinh_vien] WITH CHECK ADD CONSTRAINT [fk_tien_do_sinh_vien_hoc_ky_id] FOREIGN KEY ([hoc_ky_id]) REFERENCES [dbo].[hoc_ky] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[tien_do_sinh_vien] WITH CHECK ADD CONSTRAINT [fk_tien_do_sinh_vien_sinh_vien_id] FOREIGN KEY ([sinh_vien_id]) REFERENCES [dbo].[sinh_vien] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[tien_do_sinh_vien] WITH CHECK ADD CONSTRAINT [fk_tien_do_sinh_vien_chuong_trinh_dao_tao_id] FOREIGN KEY ([chuong_trinh_dao_tao_id]) REFERENCES [dbo].[chuong_trinh_dao_tao] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[mghp_phien_ban_chinh_sach] WITH CHECK ADD CONSTRAINT [ck_mghp_phien_ban_chinh_sach_ty_le_giam_range] CHECK ([ty_le_giam] IS NULL OR ([ty_le_giam] >= 0 AND [ty_le_giam] <= 1));
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [ck_mghp_dong_qd_ty_le_giam_range] CHECK ([ty_le_giam] IS NULL OR ([ty_le_giam] >= 0 AND [ty_le_giam] <= 1));
ALTER TABLE [dbo].[mghp_quyen_huong] WITH CHECK ADD CONSTRAINT [ck_mghp_quyen_huong_ty_le_giam_range] CHECK ([ty_le_giam] IS NULL OR ([ty_le_giam] >= 0 AND [ty_le_giam] <= 1));
ALTER TABLE [dbo].[mghp_bien_dong_quyen_huong] WITH CHECK ADD CONSTRAINT [ck_mghp_bien_dong_quyen_huong_ty_le_giam_sau_range] CHECK ([ty_le_giam_sau] IS NULL OR ([ty_le_giam_sau] >= 0 AND [ty_le_giam_sau] <= 1));
ALTER TABLE [dbo].[mghp_ket_qua_hoc_ky] WITH CHECK ADD CONSTRAINT [ck_mghp_ket_qua_hoc_ky_ty_le_giam_range] CHECK ([ty_le_giam] IS NULL OR ([ty_le_giam] >= 0 AND [ty_le_giam] <= 1));
ALTER TABLE [dbo].[mghp_dong_gui_ke_toan] WITH CHECK ADD CONSTRAINT [ck_mghp_dong_gui_ke_toan_ty_le_giam_luc_gui_range] CHECK ([ty_le_giam_luc_gui] IS NULL OR ([ty_le_giam_luc_gui] >= 0 AND [ty_le_giam_luc_gui] <= 1));
ALTER TABLE [dbo].[mghp_xac_nhan_ke_toan] WITH CHECK ADD CONSTRAINT [ck_mghp_xac_nhan_ke_toan_ty_le_giam_xac_nhan_range] CHECK ([ty_le_giam_xac_nhan] IS NULL OR ([ty_le_giam_xac_nhan] >= 0 AND [ty_le_giam_xac_nhan] <= 1));
ALTER TABLE [dbo].[mghp_ung_vien_phien_ban_qd] WITH CHECK ADD CONSTRAINT [ck_mghp_ung_vien_phien_ban_qd_ty_le_giam_luc_chot_range] CHECK ([ty_le_giam_luc_chot] IS NULL OR ([ty_le_giam_luc_chot] >= 0 AND [ty_le_giam_luc_chot] <= 1));
ALTER TABLE [dbo].[cong_sv_ket_qua_mghp] WITH CHECK ADD CONSTRAINT [ck_cong_sv_ket_qua_mghp_ty_le_giam_range] CHECK ([ty_le_giam] IS NULL OR ([ty_le_giam] >= 0 AND [ty_le_giam] <= 1));
ALTER TABLE [dbo].[mghp_khoang_huong] WITH CHECK ADD CONSTRAINT [ck_mghp_khoang_huong_ty_le_giam_range] CHECK ([ty_le_giam] IS NULL OR ([ty_le_giam] >= 0 AND [ty_le_giam] <= 1));
ALTER TABLE [dbo].[phan_quyen_nguoi_dung] WITH CHECK ADD CONSTRAINT [ck_phan_quyen_nguoi_dung_hieu_luc_tu_order] CHECK ([hieu_luc_tu] IS NULL OR [hieu_luc_den] IS NULL OR [hieu_luc_tu] <= [hieu_luc_den]);
ALTER TABLE [dbo].[hoc_ky] WITH CHECK ADD CONSTRAINT [ck_hoc_ky_ngay_bat_dau_order] CHECK ([ngay_bat_dau] IS NULL OR [ngay_ket_thuc] IS NULL OR [ngay_bat_dau] <= [ngay_ket_thuc]);
ALTER TABLE [dbo].[lich_su_trang_thai_sv] WITH CHECK ADD CONSTRAINT [ck_lich_su_trang_thai_sv_ngay_bat_dau_hieu_luc_order] CHECK ([ngay_bat_dau_hieu_luc] IS NULL OR [ngay_ket_thuc_hieu_luc] IS NULL OR [ngay_bat_dau_hieu_luc] <= [ngay_ket_thuc_hieu_luc]);
ALTER TABLE [dbo].[qua_trinh_thuoc_lop] WITH CHECK ADD CONSTRAINT [ck_qua_trinh_thuoc_lop_hieu_luc_tu_order] CHECK ([hieu_luc_tu] IS NULL OR [hieu_luc_den] IS NULL OR [hieu_luc_tu] <= [hieu_luc_den]);
ALTER TABLE [dbo].[phan_cong_can_su_lop] WITH CHECK ADD CONSTRAINT [ck_phan_cong_can_su_lop_hieu_luc_tu_order] CHECK ([hieu_luc_tu] IS NULL OR [hieu_luc_den] IS NULL OR [hieu_luc_tu] <= [hieu_luc_den]);
ALTER TABLE [dbo].[qua_trinh_cong_tac] WITH CHECK ADD CONSTRAINT [ck_qua_trinh_cong_tac_hieu_luc_tu_order] CHECK ([hieu_luc_tu] IS NULL OR [hieu_luc_den] IS NULL OR [hieu_luc_tu] <= [hieu_luc_den]);
ALTER TABLE [dbo].[phan_cong_co_van_lop] WITH CHECK ADD CONSTRAINT [ck_phan_cong_co_van_lop_hieu_luc_tu_order] CHECK ([hieu_luc_tu] IS NULL OR [hieu_luc_den] IS NULL OR [hieu_luc_tu] <= [hieu_luc_den]);
ALTER TABLE [dbo].[mghp_phien_ban_chinh_sach] WITH CHECK ADD CONSTRAINT [ck_mghp_phien_ban_chinh_sach_ngay_bat_dau_hieu_luc_order] CHECK ([ngay_bat_dau_hieu_luc] IS NULL OR [ngay_ket_thuc_hieu_luc] IS NULL OR [ngay_bat_dau_hieu_luc] <= [ngay_ket_thuc_hieu_luc]);
ALTER TABLE [dbo].[mghp_giay_to_ho_so] WITH CHECK ADD CONSTRAINT [ck_mghp_giay_to_ho_so_ngay_cap_order] CHECK ([ngay_cap] IS NULL OR [ngay_het_han] IS NULL OR [ngay_cap] <= [ngay_het_han]);
ALTER TABLE [dbo].[mghp_giay_to_ho_so] WITH CHECK ADD CONSTRAINT [ck_mghp_giay_to_ho_so_thoi_gian_chung_minh_tu_order] CHECK ([thoi_gian_chung_minh_tu] IS NULL OR [thoi_gian_chung_minh_den] IS NULL OR [thoi_gian_chung_minh_tu] <= [thoi_gian_chung_minh_den]);
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [ck_mghp_danh_gia_dieu_kien_ngay_xet_tu_order] CHECK ([ngay_xet_tu] IS NULL OR [ngay_xet_den] IS NULL OR [ngay_xet_tu] <= [ngay_xet_den]);
ALTER TABLE [dbo].[mghp_dong_qd] WITH CHECK ADD CONSTRAINT [ck_mghp_dong_qd_ngay_huong_tu_order] CHECK ([ngay_huong_tu] IS NULL OR [ngay_huong_den] IS NULL OR [ngay_huong_tu] <= [ngay_huong_den]);
ALTER TABLE [dbo].[mghp_quyen_huong] WITH CHECK ADD CONSTRAINT [ck_mghp_quyen_huong_ngay_cong_nhan_tu_order] CHECK ([ngay_cong_nhan_tu] IS NULL OR [ngay_cong_nhan_den] IS NULL OR [ngay_cong_nhan_tu] <= [ngay_cong_nhan_den]);
ALTER TABLE [dbo].[mghp_ket_qua_hoc_ky] WITH CHECK ADD CONSTRAINT [ck_mghp_ket_qua_hoc_ky_ngay_huong_tu_order] CHECK ([ngay_huong_tu] IS NULL OR [ngay_huong_den] IS NULL OR [ngay_huong_tu] <= [ngay_huong_den]);
ALTER TABLE [dbo].[mghp_khoang_huong] WITH CHECK ADD CONSTRAINT [ck_mghp_khoang_huong_ngay_bat_dau_order] CHECK ([ngay_bat_dau] IS NULL OR [ngay_ket_thuc] IS NULL OR [ngay_bat_dau] <= [ngay_ket_thuc]);
ALTER TABLE [dbo].[mghp_khoang_du_dieu_kien] WITH CHECK ADD CONSTRAINT [ck_mghp_khoang_du_dieu_kien_ngay_bat_dau_order] CHECK ([ngay_bat_dau] IS NULL OR [ngay_ket_thuc] IS NULL OR [ngay_bat_dau] <= [ngay_ket_thuc]);
ALTER TABLE [dbo].[lan_kiem_tra_chung_thu] WITH CHECK ADD CONSTRAINT [ck_lan_kiem_tra_chung_thu_hieu_luc_tu_order] CHECK ([hieu_luc_tu] IS NULL OR [hieu_luc_den] IS NULL OR [hieu_luc_tu] <= [hieu_luc_den]);
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [ck_mghp_danh_gia_dieu_kien_source_xor] CHECK ((CASE WHEN [phien_ban_ho_so_nguon_id] IS NOT NULL THEN 1 ELSE 0 END + CASE WHEN [quyen_huong_nguon_id] IS NOT NULL THEN 1 ELSE 0 END) = 1);
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [ck_mghp_danh_gia_dieu_kien_stable_source] CHECK (([phien_ban_ho_so_nguon_id] IS NOT NULL AND [ho_so_nguon_id] IS NOT NULL) OR ([quyen_huong_nguon_id] IS NOT NULL AND [ho_so_nguon_id] IS NULL));
ALTER TABLE [dbo].[mghp_lien_ket_phien_ban_van_ban] WITH CHECK ADD CONSTRAINT [ck_mghp_lien_ket_phien_ban_van_ban_owner_xor] CHECK ((CASE WHEN [bien_ban_id] IS NOT NULL THEN 1 ELSE 0 END + CASE WHEN [phien_ban_qd_id] IS NOT NULL THEN 1 ELSE 0 END) = 1);
ALTER TABLE [dbo].[cong_sv_su_kien_cap_nhat] WITH CHECK ADD CONSTRAINT [ck_cong_sv_su_kien_cap_nhat_source_xor] CHECK (([sinh_vien_nguon_id] IS NOT NULL AND [ket_qua_hoc_ky_nguon_id] IS NULL AND [cong_bo_id] IS NULL) OR ([sinh_vien_nguon_id] IS NULL AND [ket_qua_hoc_ky_nguon_id] IS NOT NULL AND [cong_bo_id] IS NOT NULL));
ALTER TABLE [dbo].[mghp_nhom_yeu_cau_giay_to] WITH CHECK ADD CONSTRAINT [ck_mghp_nhom_yeu_cau_giay_to_minimum_positive] CHECK ([so_yeu_cau_toi_thieu_dat] > 0);
ALTER TABLE [dbo].[mghp_phien_ban_ho_so] WITH CHECK ADD CONSTRAINT [ck_mghp_phien_ban_ho_so_loai_lan_nop_domain] CHECK ([loai_lan_nop] IS NULL OR [loai_lan_nop] COLLATE Latin1_General_100_BIN2 IN (N'initial', N'supplement', N'resubmission', N'periodic_update', N'information_change', N'legal_refresh'));
ALTER TABLE [dbo].[cong_sv_su_kien_cap_nhat] WITH CHECK ADD CONSTRAINT [ck_cong_sv_su_kien_cap_nhat_trang_thai_domain] CHECK ([trang_thai] IS NULL OR [trang_thai] COLLATE Latin1_General_100_BIN2 IN (N'pending', N'processing', N'processed', N'failed', N'ignored'));
ALTER TABLE [dbo].[cong_sv_ket_qua_mghp] WITH CHECK ADD CONSTRAINT [ck_cong_sv_ket_qua_mghp_trang_thai_domain] CHECK ([trang_thai] IS NULL OR [trang_thai] COLLATE Latin1_General_100_BIN2 IN (N'granted', N'adjusted', N'revoked'));
ALTER TABLE [dbo].[mghp_ung_vien_phien_ban_qd] WITH CHECK ADD CONSTRAINT [ck_mghp_ung_vien_phien_ban_qd_trang_thai_lua_chon_domain] CHECK ([trang_thai_lua_chon] IS NULL OR [trang_thai_lua_chon] COLLATE Latin1_General_100_BIN2 IN (N'undecided', N'fully_selected', N'partly_selected', N'not_selected'));
ALTER TABLE [dbo].[hang_cho_gui] WITH CHECK ADD CONSTRAINT [ck_hang_cho_gui_quy_tac_thu_lai_domain] CHECK ([quy_tac_thu_lai] IS NULL OR [quy_tac_thu_lai] COLLATE Latin1_General_100_BIN2 IN (N'safe', N'remote_idempotent', N'reconcile_on_timeout'));
ALTER TABLE [dbo].[mghp_chinh_sach] WITH CHECK ADD CONSTRAINT [ck_mghp_chinh_sach_ma_domain] CHECK ([ma] IS NULL OR [ma] COLLATE Latin1_General_100_BIN2 IN (N'M1', N'M2', N'M3', N'M4', N'M5', N'M6', N'M7', N'M8', N'M9'));
ALTER TABLE [dbo].[phien_ban_van_ban] WITH CHECK ADD CONSTRAINT [ck_phien_ban_van_ban_so_phien_ban_positive] CHECK ([so_phien_ban] IS NULL OR [so_phien_ban] > 0);
ALTER TABLE [dbo].[yeu_cau_ky] WITH CHECK ADD CONSTRAINT [ck_yeu_cau_ky_phien_ban_dong_nonnegative] CHECK ([phien_ban_dong] IS NULL OR [phien_ban_dong] >= 0);
ALTER TABLE [dbo].[buoc_ky] WITH CHECK ADD CONSTRAINT [ck_buoc_ky_thu_tu_ky_positive] CHECK ([thu_tu_ky] IS NULL OR [thu_tu_ky] > 0);
ALTER TABLE [dbo].[cong_viec] WITH CHECK ADD CONSTRAINT [ck_cong_viec_phien_ban_dong_nonnegative] CHECK ([phien_ban_dong] IS NULL OR [phien_ban_dong] >= 0);
ALTER TABLE [dbo].[hang_cho_gui] WITH CHECK ADD CONSTRAINT [ck_hang_cho_gui_so_lan_thu_nonnegative] CHECK ([so_lan_thu] IS NULL OR [so_lan_thu] >= 0);
ALTER TABLE [dbo].[mghp_phien_ban_chinh_sach] WITH CHECK ADD CONSTRAINT [ck_mghp_phien_ban_chinh_sach_phien_ban_positive] CHECK ([phien_ban] IS NULL OR [phien_ban] > 0);
ALTER TABLE [dbo].[mghp_dot_xet] WITH CHECK ADD CONSTRAINT [ck_mghp_dot_xet_phien_ban_dong_nonnegative] CHECK ([phien_ban_dong] IS NULL OR [phien_ban_dong] >= 0);
ALTER TABLE [dbo].[mghp_ho_so] WITH CHECK ADD CONSTRAINT [ck_mghp_ho_so_phien_ban_dong_nonnegative] CHECK ([phien_ban_dong] IS NULL OR [phien_ban_dong] >= 0);
ALTER TABLE [dbo].[mghp_phien_ban_ho_so] WITH CHECK ADD CONSTRAINT [ck_mghp_phien_ban_ho_so_lan_sua_doi_positive] CHECK ([lan_sua_doi] IS NULL OR [lan_sua_doi] > 0);
ALTER TABLE [dbo].[mghp_yeu_cau_bo_sung] WITH CHECK ADD CONSTRAINT [ck_mghp_yeu_cau_bo_sung_so_vong_positive] CHECK ([so_vong] IS NULL OR [so_vong] > 0);
ALTER TABLE [dbo].[mghp_danh_gia_dieu_kien] WITH CHECK ADD CONSTRAINT [ck_mghp_danh_gia_dieu_kien_so_phien_ban_danh_gia_positive] CHECK ([so_phien_ban_danh_gia] IS NULL OR [so_phien_ban_danh_gia] > 0);
ALTER TABLE [dbo].[mghp_phien_ban_qd] WITH CHECK ADD CONSTRAINT [ck_mghp_phien_ban_qd_lan_sua_doi_positive] CHECK ([lan_sua_doi] IS NULL OR [lan_sua_doi] > 0);
ALTER TABLE [dbo].[mghp_bien_dong_quyen_huong] WITH CHECK ADD CONSTRAINT [ck_mghp_bien_dong_quyen_huong_so_phien_ban_positive] CHECK ([so_phien_ban] IS NULL OR [so_phien_ban] > 0);
ALTER TABLE [dbo].[mghp_ket_qua_hoc_ky] WITH CHECK ADD CONSTRAINT [ck_mghp_ket_qua_hoc_ky_lan_sua_doi_positive] CHECK ([lan_sua_doi] IS NULL OR [lan_sua_doi] > 0);
ALTER TABLE [dbo].[mghp_dot_gui_ke_toan] WITH CHECK ADD CONSTRAINT [ck_mghp_dot_gui_ke_toan_so_lan_thu_nonnegative] CHECK ([so_lan_thu] IS NULL OR [so_lan_thu] >= 0);
ALTER TABLE [dbo].[cong_sv_su_kien_cap_nhat] WITH CHECK ADD CONSTRAINT [ck_cong_sv_su_kien_cap_nhat_so_lan_thu_nonnegative] CHECK ([so_lan_thu] IS NULL OR [so_lan_thu] >= 0);
ALTER TABLE [dbo].[cong_sv_ket_qua_mghp] WITH CHECK ADD CONSTRAINT [ck_cong_sv_ket_qua_mghp_lan_sua_doi_ket_qua_huong_positive] CHECK ([lan_sua_doi_ket_qua_huong] IS NULL OR [lan_sua_doi_ket_qua_huong] > 0);
ALTER TABLE [dbo].[mghp_ban_tin_ke_toan_nhan] WITH CHECK ADD CONSTRAINT [ck_mghp_ban_tin_ke_toan_nhan_lan_sua_doi_ket_qua_huong_positive] CHECK ([lan_sua_doi_ket_qua_huong] IS NULL OR [lan_sua_doi_ket_qua_huong] > 0);
ALTER TABLE [dbo].[cau_hinh_he_thong] WITH CHECK ADD CONSTRAINT [ck_cau_hinh_he_thong_typed_value] CHECK (([kieu_gia_tri] = N'chuoi' AND [gia_tri_chuoi] IS NOT NULL AND [gia_tri_so] IS NULL AND [gia_tri_dung_sai] IS NULL AND [gia_tri_ngay] IS NULL AND [gia_tri_thoi_diem] IS NULL) OR ([kieu_gia_tri] = N'so' AND [gia_tri_chuoi] IS NULL AND [gia_tri_so] IS NOT NULL AND [gia_tri_dung_sai] IS NULL AND [gia_tri_ngay] IS NULL AND [gia_tri_thoi_diem] IS NULL) OR ([kieu_gia_tri] = N'dung_sai' AND [gia_tri_chuoi] IS NULL AND [gia_tri_so] IS NULL AND [gia_tri_dung_sai] IS NOT NULL AND [gia_tri_ngay] IS NULL AND [gia_tri_thoi_diem] IS NULL) OR ([kieu_gia_tri] = N'ngay' AND [gia_tri_chuoi] IS NULL AND [gia_tri_so] IS NULL AND [gia_tri_dung_sai] IS NULL AND [gia_tri_ngay] IS NOT NULL AND [gia_tri_thoi_diem] IS NULL) OR ([kieu_gia_tri] = N'thoi_diem' AND [gia_tri_chuoi] IS NULL AND [gia_tri_so] IS NULL AND [gia_tri_dung_sai] IS NULL AND [gia_tri_ngay] IS NULL AND [gia_tri_thoi_diem] IS NOT NULL));
ALTER TABLE [dbo].[chi_tiet_nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [ck_chi_tiet_nhat_ky_thao_tac_value_cu] CHECK (([trang_thai_gia_tri_cu]=N'co_gia_tri' AND (CASE WHEN [chuoi_cu] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [so_cu] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [dung_sai_cu] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [ngay_cu] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [thoi_diem_cu] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [tham_chieu_cu] IS NOT NULL THEN 1 ELSE 0 END)=1) OR ([trang_thai_gia_tri_cu] IN (N'null',N'khong_ton_tai',N'da_che') AND [chuoi_cu] IS NULL AND [so_cu] IS NULL AND [dung_sai_cu] IS NULL AND [ngay_cu] IS NULL AND [thoi_diem_cu] IS NULL AND [tham_chieu_cu] IS NULL));
ALTER TABLE [dbo].[chi_tiet_nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [ck_chi_tiet_nhat_ky_thao_tac_slot_type_cu] CHECK ([trang_thai_gia_tri_cu]<>N'co_gia_tri' OR (([kieu_gia_tri]=N'chuoi' AND [chuoi_cu] IS NOT NULL) OR ([kieu_gia_tri]=N'so' AND [so_cu] IS NOT NULL) OR ([kieu_gia_tri]=N'dung_sai' AND [dung_sai_cu] IS NOT NULL) OR ([kieu_gia_tri]=N'ngay' AND [ngay_cu] IS NOT NULL) OR ([kieu_gia_tri]=N'thoi_diem' AND [thoi_diem_cu] IS NOT NULL) OR ([kieu_gia_tri]=N'tham_chieu' AND [tham_chieu_cu] IS NOT NULL)));
ALTER TABLE [dbo].[chi_tiet_nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [ck_chi_tiet_nhat_ky_thao_tac_value_moi] CHECK (([trang_thai_gia_tri_moi]=N'co_gia_tri' AND (CASE WHEN [chuoi_moi] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [so_moi] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [dung_sai_moi] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [ngay_moi] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [thoi_diem_moi] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [tham_chieu_moi] IS NOT NULL THEN 1 ELSE 0 END)=1) OR ([trang_thai_gia_tri_moi] IN (N'null',N'khong_ton_tai',N'da_che') AND [chuoi_moi] IS NULL AND [so_moi] IS NULL AND [dung_sai_moi] IS NULL AND [ngay_moi] IS NULL AND [thoi_diem_moi] IS NULL AND [tham_chieu_moi] IS NULL));
ALTER TABLE [dbo].[chi_tiet_nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [ck_chi_tiet_nhat_ky_thao_tac_slot_type_moi] CHECK ([trang_thai_gia_tri_moi]<>N'co_gia_tri' OR (([kieu_gia_tri]=N'chuoi' AND [chuoi_moi] IS NOT NULL) OR ([kieu_gia_tri]=N'so' AND [so_moi] IS NOT NULL) OR ([kieu_gia_tri]=N'dung_sai' AND [dung_sai_moi] IS NOT NULL) OR ([kieu_gia_tri]=N'ngay' AND [ngay_moi] IS NOT NULL) OR ([kieu_gia_tri]=N'thoi_diem' AND [thoi_diem_moi] IS NOT NULL) OR ([kieu_gia_tri]=N'tham_chieu' AND [tham_chieu_moi] IS NOT NULL)));
CREATE NONCLUSTERED INDEX [ix_dinh_danh_dang_nhap_nguoi_dung_id] ON [dbo].[dinh_danh_dang_nhap] ([nguoi_dung_id]);
CREATE NONCLUSTERED INDEX [ix_phan_quyen_nguoi_dung_nguoi_dung_id] ON [dbo].[phan_quyen_nguoi_dung] ([nguoi_dung_id]);
CREATE NONCLUSTERED INDEX [ix_phan_quyen_nguoi_dung_vai_tro_id] ON [dbo].[phan_quyen_nguoi_dung] ([vai_tro_id]);
CREATE NONCLUSTERED INDEX [ix_phan_quyen_nguoi_dung_don_vi_id] ON [dbo].[phan_quyen_nguoi_dung] ([don_vi_id]);
CREATE NONCLUSTERED INDEX [ix_phan_quyen_nguoi_dung_nguoi_cap_quyen_id] ON [dbo].[phan_quyen_nguoi_dung] ([nguoi_cap_quyen_id]);
CREATE NONCLUSTERED INDEX [ix_don_vi_don_vi_cha_id] ON [dbo].[don_vi] ([don_vi_cha_id]);
CREATE NONCLUSTERED INDEX [ix_nganh_noi_bo_nganh_quoc_gia_id] ON [dbo].[nganh_noi_bo] ([nganh_quoc_gia_id]);
CREATE NONCLUSTERED INDEX [ix_chuong_trinh_dao_tao_nganh_noi_bo_id] ON [dbo].[chuong_trinh_dao_tao] ([nganh_noi_bo_id]);
CREATE NONCLUSTERED INDEX [ix_chuong_trinh_dao_tao_nganh_quoc_gia_id] ON [dbo].[chuong_trinh_dao_tao] ([nganh_quoc_gia_id]);
CREATE NONCLUSTERED INDEX [ix_chuong_trinh_dao_tao_hoc_ky_bat_dau_id] ON [dbo].[chuong_trinh_dao_tao] ([hoc_ky_bat_dau_id]);
CREATE NONCLUSTERED INDEX [ix_chuong_trinh_dao_tao_hoc_ky_ket_thuc_id] ON [dbo].[chuong_trinh_dao_tao] ([hoc_ky_ket_thuc_id]);
CREATE NONCLUSTERED INDEX [ix_lop_sinh_hoat_chuong_trinh_dao_tao_id] ON [dbo].[lop_sinh_hoat] ([chuong_trinh_dao_tao_id]);
CREATE NONCLUSTERED INDEX [ix_lop_sinh_hoat_khoa_quan_ly_id] ON [dbo].[lop_sinh_hoat] ([khoa_quan_ly_id]);
CREATE NONCLUSTERED INDEX [ix_lop_sinh_hoat_khoa_hoc_id] ON [dbo].[lop_sinh_hoat] ([khoa_hoc_id]);
CREATE NONCLUSTERED INDEX [ix_sinh_vien_noi_sinh_id] ON [dbo].[sinh_vien] ([noi_sinh_id]);
CREATE NONCLUSTERED INDEX [ix_sinh_vien_dan_toc_id] ON [dbo].[sinh_vien] ([dan_toc_id]);
CREATE NONCLUSTERED INDEX [ix_sinh_vien_quoc_tich_id] ON [dbo].[sinh_vien] ([quoc_tich_id]);
CREATE NONCLUSTERED INDEX [ix_sinh_vien_lop_hien_tai_id] ON [dbo].[sinh_vien] ([lop_hien_tai_id]);
CREATE NONCLUSTERED INDEX [ix_sinh_vien_khoa_quan_ly_hien_tai_id] ON [dbo].[sinh_vien] ([khoa_quan_ly_hien_tai_id]);
CREATE NONCLUSTERED INDEX [ix_sinh_vien_khoa_hoc_hien_tai_id] ON [dbo].[sinh_vien] ([khoa_hoc_hien_tai_id]);
CREATE NONCLUSTERED INDEX [ix_sinh_vien_chuong_trinh_hien_tai_id] ON [dbo].[sinh_vien] ([chuong_trinh_hien_tai_id]);
CREATE NONCLUSTERED INDEX [ix_sinh_vien_hoc_ky_ap_dung_nguon_id] ON [dbo].[sinh_vien] ([hoc_ky_ap_dung_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_sinh_vien_hoc_ky_bat_dau_nguon_id] ON [dbo].[sinh_vien] ([hoc_ky_bat_dau_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_sinh_vien_hoc_ky_ket_thuc_nguon_id] ON [dbo].[sinh_vien] ([hoc_ky_ket_thuc_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_theo_hoc_sinh_vien_id] ON [dbo].[qua_trinh_theo_hoc] ([sinh_vien_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_theo_hoc_chuong_trinh_dao_tao_id] ON [dbo].[qua_trinh_theo_hoc] ([chuong_trinh_dao_tao_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_theo_hoc_khoa_hoc_id] ON [dbo].[qua_trinh_theo_hoc] ([khoa_hoc_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_theo_hoc_hoc_ky_bat_dau_id] ON [dbo].[qua_trinh_theo_hoc] ([hoc_ky_bat_dau_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_theo_hoc_hoc_ky_ket_thuc_nguon_id] ON [dbo].[qua_trinh_theo_hoc] ([hoc_ky_ket_thuc_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_theo_hoc_hoc_ky_ap_dung_trang_thai_id] ON [dbo].[qua_trinh_theo_hoc] ([hoc_ky_ap_dung_trang_thai_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_theo_hoc_nguoi_xac_minh_id] ON [dbo].[qua_trinh_theo_hoc] ([nguoi_xac_minh_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_theo_hoc_he_thong_nguon_id] ON [dbo].[qua_trinh_theo_hoc] ([he_thong_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_lich_su_trang_thai_sv_qua_trinh_hoc_id] ON [dbo].[lich_su_trang_thai_sv] ([qua_trinh_hoc_id]);
CREATE NONCLUSTERED INDEX [ix_lich_su_trang_thai_sv_hoc_ky_ap_dung_id] ON [dbo].[lich_su_trang_thai_sv] ([hoc_ky_ap_dung_id]);
CREATE NONCLUSTERED INDEX [ix_lich_su_trang_thai_sv_he_thong_nguon_id] ON [dbo].[lich_su_trang_thai_sv] ([he_thong_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_lich_su_trang_thai_sv_nguoi_xac_minh_id] ON [dbo].[lich_su_trang_thai_sv] ([nguoi_xac_minh_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_thuoc_lop_qua_trinh_hoc_id] ON [dbo].[qua_trinh_thuoc_lop] ([qua_trinh_hoc_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_thuoc_lop_lop_id] ON [dbo].[qua_trinh_thuoc_lop] ([lop_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_thuoc_lop_he_thong_nguon_id] ON [dbo].[qua_trinh_thuoc_lop] ([he_thong_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_thuoc_lop_nguoi_xac_minh_id] ON [dbo].[qua_trinh_thuoc_lop] ([nguoi_xac_minh_id]);
CREATE NONCLUSTERED INDEX [ix_phan_cong_can_su_lop_qua_trinh_thuoc_lop_id] ON [dbo].[phan_cong_can_su_lop] ([qua_trinh_thuoc_lop_id]);
CREATE NONCLUSTERED INDEX [ix_phan_cong_can_su_lop_nguoi_bo_nhiem_id] ON [dbo].[phan_cong_can_su_lop] ([nguoi_bo_nhiem_id]);
CREATE NONCLUSTERED INDEX [ix_can_bo_he_thong_nguon_id] ON [dbo].[can_bo] ([he_thong_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_cong_tac_can_bo_id] ON [dbo].[qua_trinh_cong_tac] ([can_bo_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_cong_tac_don_vi_id] ON [dbo].[qua_trinh_cong_tac] ([don_vi_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_cong_tac_he_thong_nguon_id] ON [dbo].[qua_trinh_cong_tac] ([he_thong_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_phan_cong_co_van_lop_lop_id] ON [dbo].[phan_cong_co_van_lop] ([lop_id]);
CREATE NONCLUSTERED INDEX [ix_phan_cong_co_van_lop_can_bo_id] ON [dbo].[phan_cong_co_van_lop] ([can_bo_id]);
CREATE NONCLUSTERED INDEX [ix_phan_cong_co_van_lop_hoc_ky_id] ON [dbo].[phan_cong_co_van_lop] ([hoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_du_lieu_hoc_vu_dong_bo_sinh_vien_id] ON [dbo].[du_lieu_hoc_vu_dong_bo] ([sinh_vien_id]);
CREATE NONCLUSTERED INDEX [ix_du_lieu_hoc_vu_dong_bo_hoc_ky_id] ON [dbo].[du_lieu_hoc_vu_dong_bo] ([hoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_du_lieu_hoc_vu_dong_bo_chuong_trinh_dao_tao_id] ON [dbo].[du_lieu_hoc_vu_dong_bo] ([chuong_trinh_dao_tao_id]);
CREATE NONCLUSTERED INDEX [ix_du_lieu_hoc_vu_dong_bo_qua_trinh_hoc_id] ON [dbo].[du_lieu_hoc_vu_dong_bo] ([qua_trinh_hoc_id]);
CREATE NONCLUSTERED INDEX [ix_du_lieu_hoc_vu_dong_bo_dot_dong_bo_id] ON [dbo].[du_lieu_hoc_vu_dong_bo] ([dot_dong_bo_id]);
CREATE NONCLUSTERED INDEX [ix_tep_dinh_kem_nguoi_so_huu_id] ON [dbo].[tep_dinh_kem] ([nguoi_so_huu_id]);
CREATE NONCLUSTERED INDEX [ix_tep_dinh_kem_nguoi_tai_len_id] ON [dbo].[tep_dinh_kem] ([nguoi_tai_len_id]);
CREATE NONCLUSTERED INDEX [ix_van_ban_don_vi_so_huu_id] ON [dbo].[van_ban] ([don_vi_so_huu_id]);
CREATE NONCLUSTERED INDEX [ix_van_ban_nguoi_tao_id] ON [dbo].[van_ban] ([nguoi_tao_id]);
CREATE NONCLUSTERED INDEX [ix_phien_ban_van_ban_tep_goc_id] ON [dbo].[phien_ban_van_ban] ([tep_goc_id]);
CREATE NONCLUSTERED INDEX [ix_phien_ban_van_ban_nguoi_doi_chieu_id] ON [dbo].[phien_ban_van_ban] ([nguoi_doi_chieu_id]);
CREATE NONCLUSTERED INDEX [ix_phien_ban_van_ban_nguoi_chot_id] ON [dbo].[phien_ban_van_ban] ([nguoi_chot_id]);
CREATE NONCLUSTERED INDEX [ix_phien_ban_van_ban_nguoi_tao_id] ON [dbo].[phien_ban_van_ban] ([nguoi_tao_id]);
CREATE NONCLUSTERED INDEX [ix_ghi_nhan_xem_van_ban_phien_ban_van_ban_id] ON [dbo].[ghi_nhan_xem_van_ban] ([phien_ban_van_ban_id]);
CREATE NONCLUSTERED INDEX [ix_ghi_nhan_xem_van_ban_nguoi_nhan_xet_id] ON [dbo].[ghi_nhan_xem_van_ban] ([nguoi_nhan_xet_id]);
CREATE NONCLUSTERED INDEX [ix_yeu_cau_ky_phien_ban_van_ban_id] ON [dbo].[yeu_cau_ky] ([phien_ban_van_ban_id]);
CREATE NONCLUSTERED INDEX [ix_yeu_cau_ky_he_thong_cung_cap_id] ON [dbo].[yeu_cau_ky] ([he_thong_cung_cap_id]);
CREATE NONCLUSTERED INDEX [ix_yeu_cau_ky_nguoi_yeu_cau_id] ON [dbo].[yeu_cau_ky] ([nguoi_yeu_cau_id]);
CREATE NONCLUSTERED INDEX [ix_yeu_cau_ky_nguoi_huy_id] ON [dbo].[yeu_cau_ky] ([nguoi_huy_id]);
CREATE NONCLUSTERED INDEX [ix_buoc_ky_nguoi_dung_noi_bo_id] ON [dbo].[buoc_ky] ([nguoi_dung_noi_bo_id]);
CREATE NONCLUSTERED INDEX [ix_buoc_ky_can_bo_id] ON [dbo].[buoc_ky] ([can_bo_id]);
CREATE NONCLUSTERED INDEX [ix_buoc_ky_tep_anh_chu_ky_id] ON [dbo].[buoc_ky] ([tep_anh_chu_ky_id]);
CREATE NONCLUSTERED INDEX [ix_uy_quyen_ky_buoc_ky_id] ON [dbo].[uy_quyen_ky] ([buoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_uy_quyen_ky_tep_minh_chung_id] ON [dbo].[uy_quyen_ky] ([tep_minh_chung_id]);
CREATE NONCLUSTERED INDEX [ix_uy_quyen_ky_nguoi_ghi_nhan_id] ON [dbo].[uy_quyen_ky] ([nguoi_ghi_nhan_id]);
CREATE NONCLUSTERED INDEX [ix_lan_goi_dich_vu_ky_yeu_cau_ky_id] ON [dbo].[lan_goi_dich_vu_ky] ([yeu_cau_ky_id]);
CREATE NONCLUSTERED INDEX [ix_lan_goi_dich_vu_ky_buoc_ky_id] ON [dbo].[lan_goi_dich_vu_ky] ([buoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_lan_goi_dich_vu_ky_uy_quyen_id] ON [dbo].[lan_goi_dich_vu_ky] ([uy_quyen_id]);
CREATE NONCLUSTERED INDEX [ix_bien_nhan_ky_yeu_cau_ky_id] ON [dbo].[bien_nhan_ky] ([yeu_cau_ky_id]);
CREATE NONCLUSTERED INDEX [ix_bien_nhan_ky_buoc_ky_id] ON [dbo].[bien_nhan_ky] ([buoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_bien_nhan_ky_lan_goi_id] ON [dbo].[bien_nhan_ky] ([lan_goi_id]);
CREATE NONCLUSTERED INDEX [ix_tep_ket_qua_ky_yeu_cau_ky_id] ON [dbo].[tep_ket_qua_ky] ([yeu_cau_ky_id]);
CREATE NONCLUSTERED INDEX [ix_tep_ket_qua_ky_bien_nhan_ky_id] ON [dbo].[tep_ket_qua_ky] ([bien_nhan_ky_id]);
CREATE NONCLUSTERED INDEX [ix_tep_ket_qua_ky_tep_dinh_kem_id] ON [dbo].[tep_ket_qua_ky] ([tep_dinh_kem_id]);
CREATE NONCLUSTERED INDEX [ix_yeu_cau_chung_thu_ca_he_thong_cung_cap_id] ON [dbo].[yeu_cau_chung_thu_ca] ([he_thong_cung_cap_id]);
CREATE NONCLUSTERED INDEX [ix_yeu_cau_chung_thu_ca_buoc_ky_id] ON [dbo].[yeu_cau_chung_thu_ca] ([buoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_yeu_cau_chung_thu_ca_nguoi_yeu_cau_id] ON [dbo].[yeu_cau_chung_thu_ca] ([nguoi_yeu_cau_id]);
CREATE NONCLUSTERED INDEX [ix_thong_bao_nguoi_tao_id] ON [dbo].[thong_bao] ([nguoi_tao_id]);
CREATE NONCLUSTERED INDEX [ix_doi_tuong_nhan_thong_bao_thong_bao_id] ON [dbo].[doi_tuong_nhan_thong_bao] ([thong_bao_id]);
CREATE NONCLUSTERED INDEX [ix_doi_tuong_nhan_thong_bao_vai_tro_id] ON [dbo].[doi_tuong_nhan_thong_bao] ([vai_tro_id]);
CREATE NONCLUSTERED INDEX [ix_doi_tuong_nhan_thong_bao_don_vi_id] ON [dbo].[doi_tuong_nhan_thong_bao] ([don_vi_id]);
CREATE NONCLUSTERED INDEX [ix_doi_tuong_nhan_thong_bao_lop_id] ON [dbo].[doi_tuong_nhan_thong_bao] ([lop_id]);
CREATE NONCLUSTERED INDEX [ix_doi_tuong_nhan_thong_bao_nguoi_dung_id] ON [dbo].[doi_tuong_nhan_thong_bao] ([nguoi_dung_id]);
CREATE NONCLUSTERED INDEX [ix_nguoi_nhan_thong_bao_nguoi_dung_id] ON [dbo].[nguoi_nhan_thong_bao] ([nguoi_dung_id]);
CREATE NONCLUSTERED INDEX [ix_cong_viec_nguoi_duoc_giao_id] ON [dbo].[cong_viec] ([nguoi_duoc_giao_id]);
CREATE NONCLUSTERED INDEX [ix_cong_viec_vai_tro_duoc_giao_id] ON [dbo].[cong_viec] ([vai_tro_duoc_giao_id]);
CREATE NONCLUSTERED INDEX [ix_cong_viec_don_vi_id] ON [dbo].[cong_viec] ([don_vi_id]);
CREATE NONCLUSTERED INDEX [ix_cong_viec_nguoi_nhan_xu_ly_id] ON [dbo].[cong_viec] ([nguoi_nhan_xu_ly_id]);
CREATE NONCLUSTERED INDEX [ix_cong_viec_nguoi_hoan_tat_id] ON [dbo].[cong_viec] ([nguoi_hoan_tat_id]);
CREATE NONCLUSTERED INDEX [ix_cong_viec_nguoi_tao_id] ON [dbo].[cong_viec] ([nguoi_tao_id]);
CREATE NONCLUSTERED INDEX [ix_nhat_ky_thao_tac_nguoi_dung_id] ON [dbo].[nhat_ky_thao_tac] ([nguoi_dung_id]);
CREATE NONCLUSTERED INDEX [ix_nhat_ky_thao_tac_he_thong_ngoai_id] ON [dbo].[nhat_ky_thao_tac] ([he_thong_ngoai_id]);
CREATE NONCLUSTERED INDEX [ix_cau_hinh_he_thong_nguoi_cap_nhat_id] ON [dbo].[cau_hinh_he_thong] ([nguoi_cap_nhat_id]);
CREATE NONCLUSTERED INDEX [ix_hang_cho_gui_he_thong_dich_id] ON [dbo].[hang_cho_gui] ([he_thong_dich_id]);
CREATE NONCLUSTERED INDEX [ix_dot_dong_bo_he_thong_nguon_id] ON [dbo].[dot_dong_bo] ([he_thong_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_dot_dong_bo_nguoi_yeu_cau_id] ON [dbo].[dot_dong_bo] ([nguoi_yeu_cau_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_phien_ban_chinh_sach_nguoi_tao_id] ON [dbo].[mghp_phien_ban_chinh_sach] ([nguoi_tao_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_chinh_sach_dot_xet_phien_ban_chinh_sach_id] ON [dbo].[mghp_chinh_sach_dot_xet] ([phien_ban_chinh_sach_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_chinh_sach_dot_xet_tep_mau_don_id] ON [dbo].[mghp_chinh_sach_dot_xet] ([tep_mau_don_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dot_xet_hoc_ky_id] ON [dbo].[mghp_dot_xet] ([hoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dot_xet_thong_bao_dot_xet_id] ON [dbo].[mghp_dot_xet] ([thong_bao_dot_xet_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dot_xet_nguoi_khoa_id] ON [dbo].[mghp_dot_xet] ([nguoi_khoa_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dot_xet_nguoi_chot_hoi_dong_id] ON [dbo].[mghp_dot_xet] ([nguoi_chot_hoi_dong_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dot_xet_nguoi_tao_id] ON [dbo].[mghp_dot_xet] ([nguoi_tao_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dot_xet_nguoi_khoa_cau_hinh_id] ON [dbo].[mghp_dot_xet] ([nguoi_khoa_cau_hinh_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ho_so_sinh_vien_id] ON [dbo].[mghp_ho_so] ([sinh_vien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ho_so_qua_trinh_hoc_id] ON [dbo].[mghp_ho_so] ([qua_trinh_hoc_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ho_so_nguoi_duoc_giao_id] ON [dbo].[mghp_ho_so] ([nguoi_duoc_giao_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ho_so_nguoi_duyet_id] ON [dbo].[mghp_ho_so] ([nguoi_duyet_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ho_so_nguoi_tao_id] ON [dbo].[mghp_ho_so] ([nguoi_tao_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ho_so_chinh_sach_id] ON [dbo].[mghp_ho_so] ([chinh_sach_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ho_so_phien_ban_chinh_sach_id] ON [dbo].[mghp_ho_so] ([phien_ban_chinh_sach_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_phien_ban_ho_so_yeu_cau_bo_sung_id] ON [dbo].[mghp_phien_ban_ho_so] ([yeu_cau_bo_sung_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_phien_ban_ho_so_nguoi_nop_id] ON [dbo].[mghp_phien_ban_ho_so] ([nguoi_nop_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_giay_to_ho_so_giay_to_bat_buoc_id] ON [dbo].[mghp_giay_to_ho_so] ([giay_to_bat_buoc_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_giay_to_ho_so_nguoi_nop_id] ON [dbo].[mghp_giay_to_ho_so] ([nguoi_nop_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_tep_minh_chung_tep_dinh_kem_id] ON [dbo].[mghp_tep_minh_chung] ([tep_dinh_kem_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ket_qua_kiem_tra_giay_to_ket_qua_kiem_tra_duoc_thay_the_id] ON [dbo].[mghp_ket_qua_kiem_tra_giay_to] ([ket_qua_kiem_tra_duoc_thay_the_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ket_qua_kiem_tra_giay_to_nguoi_thuc_hien_id] ON [dbo].[mghp_ket_qua_kiem_tra_giay_to] ([nguoi_thuc_hien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ket_qua_kiem_tra_giay_to_giay_to_ho_so_id] ON [dbo].[mghp_ket_qua_kiem_tra_giay_to] ([giay_to_ho_so_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_yeu_cau_bo_sung_phien_ban_lam_co_so_id] ON [dbo].[mghp_yeu_cau_bo_sung] ([phien_ban_lam_co_so_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_yeu_cau_bo_sung_nguoi_yeu_cau_id] ON [dbo].[mghp_yeu_cau_bo_sung] ([nguoi_yeu_cau_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_noi_dung_bo_sung_yeu_cau_bo_sung_id] ON [dbo].[mghp_noi_dung_bo_sung] ([yeu_cau_bo_sung_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_noi_dung_bo_sung_giay_to_bat_buoc_id] ON [dbo].[mghp_noi_dung_bo_sung] ([giay_to_bat_buoc_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_noi_dung_bo_sung_giay_to_ho_so_id] ON [dbo].[mghp_noi_dung_bo_sung] ([giay_to_ho_so_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_noi_dung_bo_sung_hoc_ky_ap_dung_id] ON [dbo].[mghp_noi_dung_bo_sung] ([hoc_ky_ap_dung_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_noi_dung_bo_sung_danh_gia_dieu_kien_id] ON [dbo].[mghp_noi_dung_bo_sung] ([danh_gia_dieu_kien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_noi_dung_bo_sung_giay_to_tra_loi_id] ON [dbo].[mghp_noi_dung_bo_sung] ([giay_to_tra_loi_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_noi_dung_bo_sung_nguoi_xu_ly_id] ON [dbo].[mghp_noi_dung_bo_sung] ([nguoi_xu_ly_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_nhat_ky_ho_so_ho_so_id] ON [dbo].[mghp_nhat_ky_ho_so] ([ho_so_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_nhat_ky_ho_so_phien_ban_ho_so_id] ON [dbo].[mghp_nhat_ky_ho_so] ([phien_ban_ho_so_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_nhat_ky_ho_so_nguoi_thao_tac_id] ON [dbo].[mghp_nhat_ky_ho_so] ([nguoi_thao_tac_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_danh_gia_dieu_kien_sinh_vien_id] ON [dbo].[mghp_danh_gia_dieu_kien] ([sinh_vien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_danh_gia_dieu_kien_qua_trinh_hoc_id] ON [dbo].[mghp_danh_gia_dieu_kien] ([qua_trinh_hoc_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_danh_gia_dieu_kien_hoc_ky_ap_dung_id] ON [dbo].[mghp_danh_gia_dieu_kien] ([hoc_ky_ap_dung_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_danh_gia_dieu_kien_phien_ban_chinh_sach_id] ON [dbo].[mghp_danh_gia_dieu_kien] ([phien_ban_chinh_sach_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_danh_gia_dieu_kien_nguoi_danh_gia_id] ON [dbo].[mghp_danh_gia_dieu_kien] ([nguoi_danh_gia_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_danh_gia_dieu_kien_dot_xet_id] ON [dbo].[mghp_danh_gia_dieu_kien] ([dot_xet_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_danh_gia_dieu_kien_phien_ban_ho_so_nguon_id] ON [dbo].[mghp_danh_gia_dieu_kien] ([phien_ban_ho_so_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_danh_gia_dieu_kien_danh_gia_truoc_id] ON [dbo].[mghp_danh_gia_dieu_kien] ([danh_gia_truoc_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_bien_ban_dot_xet_id] ON [dbo].[mghp_bien_ban] ([dot_xet_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_bien_ban_nguoi_tao_id] ON [dbo].[mghp_bien_ban] ([nguoi_tao_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_bien_ban_phien_ban_qd_nguon_id] ON [dbo].[mghp_bien_ban] ([phien_ban_qd_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_quyet_dinh_dot_xet_id] ON [dbo].[mghp_quyet_dinh] ([dot_xet_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_quyet_dinh_don_vi_ban_hanh_id] ON [dbo].[mghp_quyet_dinh] ([don_vi_ban_hanh_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_quyet_dinh_quyet_dinh_duoc_thay_the_id] ON [dbo].[mghp_quyet_dinh] ([quyet_dinh_duoc_thay_the_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_quyet_dinh_nguoi_tao_id] ON [dbo].[mghp_quyet_dinh] ([nguoi_tao_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_phien_ban_qd_nguoi_chot_id] ON [dbo].[mghp_phien_ban_qd] ([nguoi_chot_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_phien_ban_qd_phien_ban_truoc_id] ON [dbo].[mghp_phien_ban_qd] ([phien_ban_truoc_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_phien_ban_qd_nguoi_tao_id] ON [dbo].[mghp_phien_ban_qd] ([nguoi_tao_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_lien_ket_phien_ban_van_ban_bien_ban_id] ON [dbo].[mghp_lien_ket_phien_ban_van_ban] ([bien_ban_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_lien_ket_phien_ban_van_ban_phien_ban_qd_id] ON [dbo].[mghp_lien_ket_phien_ban_van_ban] ([phien_ban_qd_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dong_qd_sinh_vien_id] ON [dbo].[mghp_dong_qd] ([sinh_vien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dong_qd_qua_trinh_hoc_id] ON [dbo].[mghp_dong_qd] ([qua_trinh_hoc_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dong_qd_hoc_ky_ap_dung_id] ON [dbo].[mghp_dong_qd] ([hoc_ky_ap_dung_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dong_qd_phien_ban_chinh_sach_id] ON [dbo].[mghp_dong_qd] ([phien_ban_chinh_sach_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dong_qd_quyen_huong_nguon_id] ON [dbo].[mghp_dong_qd] ([quyen_huong_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dong_qd_danh_gia_dieu_kien_id] ON [dbo].[mghp_dong_qd] ([danh_gia_dieu_kien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dong_qd_dong_duoc_thay_the_id] ON [dbo].[mghp_dong_qd] ([dong_duoc_thay_the_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dong_qd_phien_ban_ho_so_nguon_id] ON [dbo].[mghp_dong_qd] ([phien_ban_ho_so_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_quyen_huong_sinh_vien_id] ON [dbo].[mghp_quyen_huong] ([sinh_vien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_quyen_huong_phien_ban_chinh_sach_id] ON [dbo].[mghp_quyen_huong] ([phien_ban_chinh_sach_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_quyen_huong_dong_qd_cong_nhan_id] ON [dbo].[mghp_quyen_huong] ([dong_qd_cong_nhan_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_quyen_huong_hoc_ky_bat_dau_huong_id] ON [dbo].[mghp_quyen_huong] ([hoc_ky_bat_dau_huong_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_quyen_huong_hoc_ky_ket_thuc_huong_id] ON [dbo].[mghp_quyen_huong] ([hoc_ky_ket_thuc_huong_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_quyen_huong_dong_qd_thu_hoi_id] ON [dbo].[mghp_quyen_huong] ([dong_qd_thu_hoi_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_quyen_huong_phien_ban_ho_so_nguon_id] ON [dbo].[mghp_quyen_huong] ([phien_ban_ho_so_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_bien_dong_quyen_huong_dong_qd_id] ON [dbo].[mghp_bien_dong_quyen_huong] ([dong_qd_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_bien_dong_quyen_huong_nguoi_ghi_nhan_id] ON [dbo].[mghp_bien_dong_quyen_huong] ([nguoi_ghi_nhan_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ket_qua_hoc_ky_sinh_vien_id] ON [dbo].[mghp_ket_qua_hoc_ky] ([sinh_vien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ket_qua_hoc_ky_hoc_ky_id] ON [dbo].[mghp_ket_qua_hoc_ky] ([hoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ket_qua_hoc_ky_phien_ban_chinh_sach_id] ON [dbo].[mghp_ket_qua_hoc_ky] ([phien_ban_chinh_sach_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ket_qua_hoc_ky_quyen_huong_id] ON [dbo].[mghp_ket_qua_hoc_ky] ([quyen_huong_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ket_qua_hoc_ky_danh_gia_dieu_kien_id] ON [dbo].[mghp_ket_qua_hoc_ky] ([danh_gia_dieu_kien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ket_qua_hoc_ky_ket_qua_huong_duoc_thay_the_id] ON [dbo].[mghp_ket_qua_hoc_ky] ([ket_qua_huong_duoc_thay_the_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dot_gui_ke_toan_he_thong_dich_id] ON [dbo].[mghp_dot_gui_ke_toan] ([he_thong_dich_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dot_gui_ke_toan_phien_ban_qd_id] ON [dbo].[mghp_dot_gui_ke_toan] ([phien_ban_qd_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dot_gui_ke_toan_hoc_ky_id] ON [dbo].[mghp_dot_gui_ke_toan] ([hoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dot_gui_ke_toan_nguoi_tao_id] ON [dbo].[mghp_dot_gui_ke_toan] ([nguoi_tao_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dong_gui_ke_toan_ket_qua_huong_id] ON [dbo].[mghp_dong_gui_ke_toan] ([ket_qua_huong_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_xac_nhan_ke_toan_dong_gui_id] ON [dbo].[mghp_xac_nhan_ke_toan] ([dong_gui_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_xac_nhan_ke_toan_ban_tin_nhan_id] ON [dbo].[mghp_xac_nhan_ke_toan] ([ban_tin_nhan_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_cong_bo_thong_bao_id] ON [dbo].[mghp_cong_bo] ([thong_bao_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_cong_bo_nguoi_cong_bo_id] ON [dbo].[mghp_cong_bo] ([nguoi_cong_bo_id]);
CREATE NONCLUSTERED INDEX [ix_cong_sv_su_kien_cap_nhat_cong_bo_id] ON [dbo].[cong_sv_su_kien_cap_nhat] ([cong_bo_id]);
CREATE NONCLUSTERED INDEX [ix_cong_sv_ket_qua_mghp_cong_bo_id] ON [dbo].[cong_sv_ket_qua_mghp] ([cong_bo_id]);
CREATE NONCLUSTERED INDEX [ix_cong_sv_ket_qua_mghp_su_kien_cap_nhat_id] ON [dbo].[cong_sv_ket_qua_mghp] ([su_kien_cap_nhat_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ho_so_nguoi_xac_minh_ngay_nop_id] ON [dbo].[mghp_ho_so] ([nguoi_xac_minh_ngay_nop_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_phien_ban_ho_so_dot_tiep_nhan_id] ON [dbo].[mghp_phien_ban_ho_so] ([dot_tiep_nhan_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_phien_ban_ho_so_quyen_huong_cap_nhat_id] ON [dbo].[mghp_phien_ban_ho_so] ([quyen_huong_cap_nhat_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_phien_ban_ho_so_phien_ban_chinh_sach_ke_khai_id] ON [dbo].[mghp_phien_ban_ho_so] ([phien_ban_chinh_sach_ke_khai_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_phien_ban_ho_so_dan_toc_khai_bao_id] ON [dbo].[mghp_phien_ban_ho_so] ([dan_toc_khai_bao_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_phien_ban_ho_so_lop_khai_bao_id] ON [dbo].[mghp_phien_ban_ho_so] ([lop_khai_bao_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_phien_ban_ho_so_khoa_hoc_khai_bao_id] ON [dbo].[mghp_phien_ban_ho_so] ([khoa_hoc_khai_bao_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_phien_ban_ho_so_khoa_quan_ly_khai_bao_id] ON [dbo].[mghp_phien_ban_ho_so] ([khoa_quan_ly_khai_bao_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_giay_to_ho_so_nguoi_lien_quan_id] ON [dbo].[mghp_giay_to_ho_so] ([nguoi_lien_quan_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_giay_to_ho_so_hoc_ky_xac_nhan_id] ON [dbo].[mghp_giay_to_ho_so] ([hoc_ky_xac_nhan_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_giay_to_ho_so_nguoi_xac_minh_id] ON [dbo].[mghp_giay_to_ho_so] ([nguoi_xac_minh_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_yeu_cau_bo_sung_dot_tiep_nhan_id] ON [dbo].[mghp_yeu_cau_bo_sung] ([dot_tiep_nhan_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_yeu_cau_bo_sung_quyen_huong_id] ON [dbo].[mghp_yeu_cau_bo_sung] ([quyen_huong_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_nhat_ky_ho_so_yeu_cau_bo_sung_id] ON [dbo].[mghp_nhat_ky_ho_so] ([yeu_cau_bo_sung_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_danh_gia_dieu_kien_phien_ban_cap_nhat_minh_chung_id] ON [dbo].[mghp_danh_gia_dieu_kien] ([phien_ban_cap_nhat_minh_chung_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_danh_gia_dieu_kien_lich_su_trang_thai_sv_id] ON [dbo].[mghp_danh_gia_dieu_kien] ([lich_su_trang_thai_sv_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dong_qd_ung_vien_id] ON [dbo].[mghp_dong_qd] ([ung_vien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dong_qd_hoc_ky_bat_dau_huong_id] ON [dbo].[mghp_dong_qd] ([hoc_ky_bat_dau_huong_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_dong_qd_hoc_ky_ket_thuc_huong_id] ON [dbo].[mghp_dong_qd] ([hoc_ky_ket_thuc_huong_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_bien_dong_quyen_huong_bien_dong_truoc_id] ON [dbo].[mghp_bien_dong_quyen_huong] ([bien_dong_truoc_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_bien_dong_quyen_huong_hoc_ky_bat_dau_sau_id] ON [dbo].[mghp_bien_dong_quyen_huong] ([hoc_ky_bat_dau_sau_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_bien_dong_quyen_huong_hoc_ky_ket_thuc_sau_id] ON [dbo].[mghp_bien_dong_quyen_huong] ([hoc_ky_ket_thuc_sau_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ket_qua_tieu_chi_tieu_chi_chinh_sach_id] ON [dbo].[mghp_ket_qua_tieu_chi] ([tieu_chi_chinh_sach_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ket_qua_tieu_chi_nguoi_danh_gia_id] ON [dbo].[mghp_ket_qua_tieu_chi] ([nguoi_danh_gia_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_hoc_ky_de_nghi_hoc_ky_id] ON [dbo].[mghp_hoc_ky_de_nghi] ([hoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_hoc_ky_de_nghi_phien_ban_chinh_sach_id] ON [dbo].[mghp_hoc_ky_de_nghi] ([phien_ban_chinh_sach_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_nguoi_lien_quan_phien_ban_ho_so_id] ON [dbo].[mghp_nguoi_lien_quan] ([phien_ban_ho_so_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_nguoi_lien_quan_dan_toc_id] ON [dbo].[mghp_nguoi_lien_quan] ([dan_toc_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_nguoi_lien_quan_don_vi_cong_tac_id] ON [dbo].[mghp_nguoi_lien_quan] ([don_vi_cong_tac_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_nguoi_lien_quan_nguoi_xac_minh_id] ON [dbo].[mghp_nguoi_lien_quan] ([nguoi_xac_minh_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ung_vien_phien_ban_qd_danh_gia_dieu_kien_id] ON [dbo].[mghp_ung_vien_phien_ban_qd] ([danh_gia_dieu_kien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ung_vien_phien_ban_qd_qua_trinh_hoc_id] ON [dbo].[mghp_ung_vien_phien_ban_qd] ([qua_trinh_hoc_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ung_vien_phien_ban_qd_hoc_ky_id] ON [dbo].[mghp_ung_vien_phien_ban_qd] ([hoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_doi_chieu_ke_khai_nguoi_xac_minh_id] ON [dbo].[mghp_doi_chieu_ke_khai] ([nguoi_xac_minh_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_can_cu_danh_gia_danh_gia_dieu_kien_id] ON [dbo].[mghp_can_cu_danh_gia] ([danh_gia_dieu_kien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_can_cu_danh_gia_giay_to_ho_so_id] ON [dbo].[mghp_can_cu_danh_gia] ([giay_to_ho_so_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_can_cu_danh_gia_ket_qua_tieu_chi_id] ON [dbo].[mghp_can_cu_danh_gia] ([ket_qua_tieu_chi_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_can_cu_danh_gia_nguoi_xac_nhan_id] ON [dbo].[mghp_can_cu_danh_gia] ([nguoi_xac_nhan_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_khoang_huong_ung_vien_id] ON [dbo].[mghp_khoang_huong] ([ung_vien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_khoang_du_dieu_kien_nguoi_xac_nhan_id] ON [dbo].[mghp_khoang_du_dieu_kien] ([nguoi_xac_nhan_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ung_vien_phien_ban_qd_khoang_du_dieu_kien_id] ON [dbo].[mghp_ung_vien_phien_ban_qd] ([khoang_du_dieu_kien_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_quyen_huong_khoang_huong_cong_nhan_id] ON [dbo].[mghp_quyen_huong] ([khoang_huong_cong_nhan_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_quyen_huong_chinh_sach_id] ON [dbo].[mghp_quyen_huong] ([chinh_sach_id]);
CREATE NONCLUSTERED INDEX [ix_qua_trinh_theo_hoc_tep_can_cu_id] ON [dbo].[qua_trinh_theo_hoc] ([tep_can_cu_id]);
CREATE NONCLUSTERED INDEX [ix_buoc_ky_lan_kiem_tra_chung_thu_id] ON [dbo].[buoc_ky] ([lan_kiem_tra_chung_thu_id]);
CREATE NONCLUSTERED INDEX [ix_lan_kiem_tra_chung_thu_buoc_ky_id] ON [dbo].[lan_kiem_tra_chung_thu] ([buoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_lan_kiem_tra_chung_thu_lan_goi_tra_cuu_id] ON [dbo].[lan_kiem_tra_chung_thu] ([lan_goi_tra_cuu_id]);
CREATE NONCLUSTERED INDEX [ix_lan_goi_dich_vu_ky_lan_kiem_tra_chung_thu_id] ON [dbo].[lan_goi_dich_vu_ky] ([lan_kiem_tra_chung_thu_id]);
CREATE NONCLUSTERED INDEX [ix_lan_goi_dich_vu_ky_tep_dau_vao_id] ON [dbo].[lan_goi_dich_vu_ky] ([tep_dau_vao_id]);
CREATE NONCLUSTERED INDEX [ix_tep_ket_qua_ky_lan_kiem_tra_tep_id] ON [dbo].[tep_ket_qua_ky] ([lan_kiem_tra_tep_id]);
CREATE NONCLUSTERED INDEX [ix_lan_kiem_tra_tep_tep_ket_qua_ky_id] ON [dbo].[lan_kiem_tra_tep] ([tep_ket_qua_ky_id]);
CREATE NONCLUSTERED INDEX [ix_lan_kiem_tra_tep_nguoi_kiem_tra_id] ON [dbo].[lan_kiem_tra_tep] ([nguoi_kiem_tra_id]);
CREATE NONCLUSTERED INDEX [ix_cong_sv_su_kien_cap_nhat_sinh_vien_nguon_id] ON [dbo].[cong_sv_su_kien_cap_nhat] ([sinh_vien_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_cong_sv_su_kien_cap_nhat_ket_qua_hoc_ky_nguon_id] ON [dbo].[cong_sv_su_kien_cap_nhat] ([ket_qua_hoc_ky_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ban_tin_ke_toan_nhan_dong_gui_id] ON [dbo].[mghp_ban_tin_ke_toan_nhan] ([dong_gui_id]);
CREATE NONCLUSTERED INDEX [ix_mghp_ban_tin_ke_toan_nhan_xac_nhan_noi_bo_id] ON [dbo].[mghp_ban_tin_ke_toan_nhan] ([xac_nhan_noi_bo_id]);
CREATE NONCLUSTERED INDEX [ix_sinh_vien_hoc_ky_han_hoc_nguon_id] ON [dbo].[sinh_vien] ([hoc_ky_han_hoc_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_can_bo_don_vi_nguon_id] ON [dbo].[can_bo] ([don_vi_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_phan_cong_co_van_lop_can_bo_chu_nhiem_id] ON [dbo].[phan_cong_co_van_lop] ([can_bo_chu_nhiem_id]);
CREATE NONCLUSTERED INDEX [ix_khung_chuong_trinh_hoc_phan_id] ON [dbo].[khung_chuong_trinh] ([hoc_phan_id]);
CREATE NONCLUSTERED INDEX [ix_khung_chuong_trinh_hoc_phan_duoc_thay_the_id] ON [dbo].[khung_chuong_trinh] ([hoc_phan_duoc_thay_the_id]);
CREATE NONCLUSTERED INDEX [ix_diem_ky_hoc_hoc_ky_id] ON [dbo].[diem_ky_hoc] ([hoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_diem_ky_hoc_sinh_vien_id] ON [dbo].[diem_ky_hoc] ([sinh_vien_id]);
CREATE NONCLUSTERED INDEX [ix_diem_ky_hoc_hoc_phan_id] ON [dbo].[diem_ky_hoc] ([hoc_phan_id]);
CREATE NONCLUSTERED INDEX [ix_diem_ky_hoc_hoc_phan_tuong_duong_id] ON [dbo].[diem_ky_hoc] ([hoc_phan_tuong_duong_id]);
CREATE NONCLUSTERED INDEX [ix_diem_mien_hoc_ky_id] ON [dbo].[diem_mien] ([hoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_diem_mien_hoc_phan_id] ON [dbo].[diem_mien] ([hoc_phan_id]);
CREATE NONCLUSTERED INDEX [ix_diem_mien_sinh_vien_id] ON [dbo].[diem_mien] ([sinh_vien_id]);
CREATE NONCLUSTERED INDEX [ix_diem_mien_hoc_phan_tuong_duong_id] ON [dbo].[diem_mien] ([hoc_phan_tuong_duong_id]);
CREATE NONCLUSTERED INDEX [ix_phan_lop_ren_luyen_hoc_ky_id] ON [dbo].[phan_lop_ren_luyen] ([hoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_phan_lop_ren_luyen_sinh_vien_id] ON [dbo].[phan_lop_ren_luyen] ([sinh_vien_id]);
CREATE NONCLUSTERED INDEX [ix_phan_lop_ren_luyen_lop_ren_luyen_id] ON [dbo].[phan_lop_ren_luyen] ([lop_ren_luyen_id]);
CREATE NONCLUSTERED INDEX [ix_ghep_lop_sinh_hoat_hoc_ky_id] ON [dbo].[ghep_lop_sinh_hoat] ([hoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_ghep_lop_sinh_hoat_lop_goc_id] ON [dbo].[ghep_lop_sinh_hoat] ([lop_goc_id]);
CREATE NONCLUSTERED INDEX [ix_ghep_lop_sinh_hoat_lop_nhan_id] ON [dbo].[ghep_lop_sinh_hoat] ([lop_nhan_id]);
CREATE NONCLUSTERED INDEX [ix_tien_do_sinh_vien_hoc_ky_id] ON [dbo].[tien_do_sinh_vien] ([hoc_ky_id]);
CREATE NONCLUSTERED INDEX [ix_tien_do_sinh_vien_sinh_vien_id] ON [dbo].[tien_do_sinh_vien] ([sinh_vien_id]);
CREATE NONCLUSTERED INDEX [ix_tien_do_sinh_vien_chuong_trinh_dao_tao_id] ON [dbo].[tien_do_sinh_vien] ([chuong_trinh_dao_tao_id]);
CREATE NONCLUSTERED INDEX [ix_hang_cho_gui_retry] ON [dbo].[hang_cho_gui] ([trang_thai], [thoi_diem_thu_lai_tiep]);
CREATE NONCLUSTERED INDEX [ix_cong_sv_su_kien_cap_nhat_retry] ON [dbo].[cong_sv_su_kien_cap_nhat] ([trang_thai], [thoi_diem_thu_lai_tiep]);
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_ho_so_source_owner] ON [dbo].[mghp_ho_so] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.qua_trinh_theo_hoc p ON p.id=i.qua_trinh_hoc_id JOIN dbo.mghp_phien_ban_chinh_sach v ON v.id=i.phien_ban_chinh_sach_id WHERE p.sinh_vien_id<>i.sinh_vien_id OR v.chinh_sach_id<>i.chinh_sach_id) THROW 51030, N''Application student/enrollment/policy identity mismatch.'', 1;
IF EXISTS (SELECT 1 FROM inserted i JOIN deleted x ON x.id=i.id WHERE i.dot_xet_id<>x.dot_xet_id OR i.qua_trinh_hoc_id<>x.qua_trinh_hoc_id OR i.chinh_sach_id<>x.chinh_sach_id OR i.sinh_vien_id<>x.sinh_vien_id OR i.phien_ban_chinh_sach_id<>x.phien_ban_chinh_sach_id) THROW 51031, N''Application identity cannot change; use another application for another object.'', 1;
IF EXISTS (SELECT 1 FROM inserted i WHERE NOT EXISTS (SELECT 1 FROM dbo.mghp_chinh_sach_dot_xet b WHERE b.dot_xet_id=i.dot_xet_id AND b.phien_ban_chinh_sach_id=i.phien_ban_chinh_sach_id)) THROW 51044,N''Application policy is not configured for its receiving batch.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_chinh_sach_dot_xet_one_policy_version] ON [dbo].[mghp_chinh_sach_dot_xet] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM dbo.mghp_chinh_sach_dot_xet a WITH (UPDLOCK,HOLDLOCK) JOIN dbo.mghp_phien_ban_chinh_sach x ON x.id=a.phien_ban_chinh_sach_id JOIN dbo.mghp_chinh_sach_dot_xet b WITH (UPDLOCK,HOLDLOCK) ON b.dot_xet_id=a.dot_xet_id AND b.id<>a.id JOIN dbo.mghp_phien_ban_chinh_sach y ON y.id=b.phien_ban_chinh_sach_id WHERE x.chinh_sach_id=y.chinh_sach_id) THROW 51032,N''One policy version per policy per batch.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_danh_gia_dieu_kien_source_owner] ON [dbo].[mghp_danh_gia_dieu_kien] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.mghp_phien_ban_ho_so v ON v.id=i.phien_ban_ho_so_nguon_id JOIN dbo.mghp_ho_so h ON h.id=v.ho_so_id JOIN dbo.mghp_phien_ban_chinh_sach p ON p.id=i.phien_ban_chinh_sach_id WHERE h.id<>i.ho_so_nguon_id OR h.sinh_vien_id<>i.sinh_vien_id OR h.qua_trinh_hoc_id<>i.qua_trinh_hoc_id OR h.chinh_sach_id<>p.chinh_sach_id) THROW 51033,N''Assessment source ownership mismatch.'',1;
IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.mghp_quyen_huong r ON r.id=i.quyen_huong_nguon_id JOIN dbo.mghp_phien_ban_chinh_sach p ON p.id=i.phien_ban_chinh_sach_id WHERE r.sinh_vien_id<>i.sinh_vien_id OR r.qua_trinh_hoc_id<>i.qua_trinh_hoc_id OR r.chinh_sach_id<>p.chinh_sach_id OR r.ma_pham_vi<>i.ma_pham_vi) THROW 51034,N''Entitlement assessment context mismatch.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_ket_qua_tieu_chi_same_policy] ON [dbo].[mghp_ket_qua_tieu_chi] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.mghp_danh_gia_dieu_kien a ON a.id=i.danh_gia_dieu_kien_id JOIN dbo.mghp_tieu_chi_chinh_sach c ON c.id=i.tieu_chi_chinh_sach_id WHERE a.phien_ban_chinh_sach_id<>c.phien_ban_chinh_sach_id) THROW 51035,N''Criterion and assessment use different policy versions.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_khoang_du_dieu_kien_nonoverlap] ON [dbo].[mghp_khoang_du_dieu_kien] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM dbo.mghp_khoang_du_dieu_kien a WITH (UPDLOCK,HOLDLOCK) JOIN dbo.mghp_khoang_du_dieu_kien b WITH (UPDLOCK,HOLDLOCK) ON b.danh_gia_dieu_kien_id=a.danh_gia_dieu_kien_id AND b.id>a.id AND a.ngay_bat_dau<=b.ngay_ket_thuc AND b.ngay_bat_dau<=a.ngay_ket_thuc) THROW 51036,N''Eligible intervals overlap within one assessment.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_khoang_huong_nonoverlap] ON [dbo].[mghp_khoang_huong] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM dbo.mghp_khoang_huong a WITH (UPDLOCK,HOLDLOCK) JOIN dbo.mghp_dong_qd x ON x.id=a.dong_qd_id JOIN dbo.mghp_khoang_huong b WITH (UPDLOCK,HOLDLOCK) ON b.id>a.id AND a.ngay_bat_dau<=b.ngay_ket_thuc AND b.ngay_bat_dau<=a.ngay_ket_thuc JOIN dbo.mghp_dong_qd y ON y.id=b.dong_qd_id WHERE x.phien_ban_qd_id=y.phien_ban_qd_id AND x.qua_trinh_hoc_id=y.qua_trinh_hoc_id AND x.hoc_ky_ap_dung_id=y.hoc_ky_ap_dung_id AND x.ma_pham_vi=y.ma_pham_vi) THROW 51037,N''Granted intervals overlap in the same decision context.'',1;
IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.mghp_ung_vien_phien_ban_qd c ON c.id=i.ung_vien_id WHERE i.ngay_bat_dau<c.ngay_du_dieu_kien_tu OR i.ngay_ket_thuc>c.ngay_du_dieu_kien_den OR c.ngay_du_dieu_kien_tu IS NULL OR c.ngay_du_dieu_kien_den IS NULL) THROW 51038,N''Granted interval exceeds its selected candidate.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_ung_vien_phien_ban_qd_context] ON [dbo].[mghp_ung_vien_phien_ban_qd] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.mghp_khoang_du_dieu_kien k ON k.id=i.khoang_du_dieu_kien_id JOIN dbo.mghp_danh_gia_dieu_kien a ON a.id=i.danh_gia_dieu_kien_id WHERE k.danh_gia_dieu_kien_id<>a.id OR a.qua_trinh_hoc_id<>i.qua_trinh_hoc_id OR a.hoc_ky_ap_dung_id<>i.hoc_ky_id OR a.ma_pham_vi<>i.ma_pham_vi OR i.ngay_du_dieu_kien_tu IS NULL OR i.ngay_du_dieu_kien_den IS NULL OR i.ngay_du_dieu_kien_tu<>k.ngay_bat_dau OR i.ngay_du_dieu_kien_den<>k.ngay_ket_thuc) THROW 51046,N''Candidate does not match its exact eligible interval/assessment.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_khoang_huong_context] ON [dbo].[mghp_khoang_huong] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.mghp_dong_qd r ON r.id=i.dong_qd_id LEFT JOIN dbo.mghp_ung_vien_phien_ban_qd c ON c.id=i.ung_vien_id LEFT JOIN dbo.mghp_khoang_du_dieu_kien e ON e.id=c.khoang_du_dieu_kien_id JOIN dbo.hoc_ky h ON h.id=r.hoc_ky_ap_dung_id WHERE h.ngay_bat_dau IS NULL OR h.ngay_ket_thuc IS NULL OR i.ngay_bat_dau<h.ngay_bat_dau OR i.ngay_ket_thuc>h.ngay_ket_thuc OR (i.ung_vien_id IS NULL AND (i.ty_le_giam<>0 OR r.loai_thao_tac IS NULL OR r.loai_thao_tac<>N''revoke'' OR r.dong_duoc_thay_the_id IS NULL)) OR (i.ung_vien_id IS NOT NULL AND (c.phien_ban_qd_id<>r.phien_ban_qd_id OR c.qua_trinh_hoc_id<>r.qua_trinh_hoc_id OR c.hoc_ky_id<>r.hoc_ky_ap_dung_id OR c.ma_pham_vi<>r.ma_pham_vi OR i.ty_le_giam<>c.ty_le_giam_luc_chot OR c.ty_le_giam_luc_chot IS NULL OR c.trang_thai_lua_chon NOT IN (N''fully_selected'',N''partly_selected'') OR c.trang_thai_lua_chon IS NULL OR e.danh_gia_dieu_kien_id<>c.danh_gia_dieu_kien_id))) THROW 51047,N''Granted interval has no valid same-context candidate or revocation basis.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_yeu_cau_ky_outbox_xor] ON [dbo].[yeu_cau_ky] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM dbo.yeu_cau_ky a WITH (UPDLOCK,HOLDLOCK) JOIN dbo.mghp_dot_gui_ke_toan b WITH (UPDLOCK,HOLDLOCK) ON b.ban_tin_gui_id=a.ban_tin_gui_id) THROW 51048,N''Outbox job cannot belong to both signing and accounting.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_dot_gui_ke_toan_outbox_xor] ON [dbo].[mghp_dot_gui_ke_toan] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM dbo.yeu_cau_ky a WITH (UPDLOCK,HOLDLOCK) JOIN dbo.mghp_dot_gui_ke_toan b WITH (UPDLOCK,HOLDLOCK) ON b.ban_tin_gui_id=a.ban_tin_gui_id) THROW 51048,N''Outbox job cannot belong to both signing and accounting.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_buoc_ky_selected_owner] ON [dbo].[buoc_ky] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM dbo.buoc_ky b JOIN dbo.lan_kiem_tra_chung_thu c ON c.id=b.lan_kiem_tra_chung_thu_id WHERE c.buoc_ky_id IS NULL OR c.buoc_ky_id<>b.id) THROW 51049,N''Selected certificate check belongs to another signing step.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_lan_kiem_tra_chung_thu_selected_owner] ON [dbo].[lan_kiem_tra_chung_thu] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM dbo.buoc_ky b JOIN dbo.lan_kiem_tra_chung_thu c ON c.id=b.lan_kiem_tra_chung_thu_id WHERE c.buoc_ky_id IS NULL OR c.buoc_ky_id<>b.id) THROW 51049,N''Selected certificate check belongs to another signing step.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_tep_ket_qua_ky_selected_owner] ON [dbo].[tep_ket_qua_ky] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM dbo.tep_ket_qua_ky a JOIN dbo.lan_kiem_tra_tep c ON c.id=a.lan_kiem_tra_tep_id WHERE c.tep_dinh_kem_id IS NULL OR a.tep_dinh_kem_id IS NULL OR c.tep_dinh_kem_id<>a.tep_dinh_kem_id OR (c.tep_ket_qua_ky_id IS NOT NULL AND c.tep_ket_qua_ky_id<>a.id)) THROW 51050,N''Selected check belongs to another file/artifact.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_lan_kiem_tra_tep_selected_owner] ON [dbo].[lan_kiem_tra_tep] AFTER INSERT, UPDATE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM dbo.tep_ket_qua_ky a JOIN dbo.lan_kiem_tra_tep c ON c.id=a.lan_kiem_tra_tep_id WHERE c.tep_dinh_kem_id IS NULL OR a.tep_dinh_kem_id IS NULL OR c.tep_dinh_kem_id<>a.tep_dinh_kem_id OR (c.tep_ket_qua_ky_id IS NOT NULL AND c.tep_ket_qua_ky_id<>a.id)) THROW 51050,N''Selected check belongs to another file/artifact.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_phien_ban_ho_so_immutable] ON [dbo].[mghp_phien_ban_ho_so] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[ho_so_id],i.[lan_sua_doi],i.[yeu_cau_bo_sung_id],CONVERT(varbinary(max),i.[sha256_du_lieu_gui]),i.[nguoi_nop_id],i.[thoi_diem_nop],i.[thoi_diem_tao],CONVERT(varbinary(max),i.[loai_lan_nop]),i.[dot_tiep_nhan_id],i.[quyen_huong_cap_nhat_id],i.[phien_ban_chinh_sach_ke_khai_id],i.[ma_thao_tac_nop],CONVERT(varbinary(max),i.[ma_phien_ban_mau]),CONVERT(varbinary(max),i.[ho_ten_khai_bao]),CONVERT(varbinary(max),i.[so_cccd_khai_bao]),i.[ngay_sinh_khai_bao],CONVERT(varbinary(max),i.[noi_sinh_khai_bao]),CONVERT(varbinary(max),i.[gioi_tinh_khai_bao]),i.[dan_toc_khai_bao_id],CONVERT(varbinary(max),i.[ten_dan_toc_luc_nop]),i.[lop_khai_bao_id],CONVERT(varbinary(max),i.[ma_lop_luc_nop]),CONVERT(varbinary(max),i.[mssv_khai_bao]),i.[khoa_hoc_khai_bao_id],CONVERT(varbinary(max),i.[ma_khoa_hoc_luc_nop]),i.[khoa_quan_ly_khai_bao_id],CONVERT(varbinary(max),i.[ten_khoa_luc_nop]),CONVERT(varbinary(max),i.[dia_chi_thuong_tru_khai_bao]),CONVERT(varbinary(max),i.[so_dien_thoai_khai_bao]),CONVERT(varbinary(max),i.[ket_qua_doi_chieu_thong_tin]),i.[thoi_diem_lay_du_lieu_sis],i.[la_van_bang_thu_nhat],i.[da_huong_mghp_cung_trinh_do],i.[dang_huong_luong_khi_di_hoc],i.[cam_ket_khai_dung] EXCEPT SELECT x.[id],x.[ho_so_id],x.[lan_sua_doi],x.[yeu_cau_bo_sung_id],CONVERT(varbinary(max),x.[sha256_du_lieu_gui]),x.[nguoi_nop_id],x.[thoi_diem_nop],x.[thoi_diem_tao],CONVERT(varbinary(max),x.[loai_lan_nop]),x.[dot_tiep_nhan_id],x.[quyen_huong_cap_nhat_id],x.[phien_ban_chinh_sach_ke_khai_id],x.[ma_thao_tac_nop],CONVERT(varbinary(max),x.[ma_phien_ban_mau]),CONVERT(varbinary(max),x.[ho_ten_khai_bao]),CONVERT(varbinary(max),x.[so_cccd_khai_bao]),x.[ngay_sinh_khai_bao],CONVERT(varbinary(max),x.[noi_sinh_khai_bao]),CONVERT(varbinary(max),x.[gioi_tinh_khai_bao]),x.[dan_toc_khai_bao_id],CONVERT(varbinary(max),x.[ten_dan_toc_luc_nop]),x.[lop_khai_bao_id],CONVERT(varbinary(max),x.[ma_lop_luc_nop]),CONVERT(varbinary(max),x.[mssv_khai_bao]),x.[khoa_hoc_khai_bao_id],CONVERT(varbinary(max),x.[ma_khoa_hoc_luc_nop]),x.[khoa_quan_ly_khai_bao_id],CONVERT(varbinary(max),x.[ten_khoa_luc_nop]),CONVERT(varbinary(max),x.[dia_chi_thuong_tru_khai_bao]),CONVERT(varbinary(max),x.[so_dien_thoai_khai_bao]),CONVERT(varbinary(max),x.[ket_qua_doi_chieu_thong_tin]),x.[thoi_diem_lay_du_lieu_sis],x.[la_van_bang_thu_nhat],x.[da_huong_mghp_cung_trinh_do],x.[dang_huong_luong_khi_di_hoc],x.[cam_ket_khai_dung]))) THROW 51051,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_giay_to_ho_so_immutable] ON [dbo].[mghp_giay_to_ho_so] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[phien_ban_ho_so_id],i.[giay_to_bat_buoc_id],CONVERT(varbinary(max),i.[ma_minh_chung]),CONVERT(varbinary(max),i.[so_giay_to]),CONVERT(varbinary(max),i.[don_vi_cap]),i.[ngay_cap],i.[ngay_het_han],CONVERT(varbinary(max),i.[sha256_noi_dung]),i.[nguoi_nop_id],i.[thoi_diem_nop],i.[thoi_diem_tao],i.[nguoi_lien_quan_id],i.[ngay_bat_dau_hieu_luc],CONVERT(varbinary(max),i.[loai_hieu_luc]),i.[thoi_gian_chung_minh_tu],i.[thoi_gian_chung_minh_den],i.[hoc_ky_xac_nhan_id],CONVERT(varbinary(max),i.[loai_ho_ngheo]),CONVERT(varbinary(max),i.[ma_dia_ban]),CONVERT(varbinary(max),i.[can_cu_phan_loai_dia_ban]) EXCEPT SELECT x.[id],x.[phien_ban_ho_so_id],x.[giay_to_bat_buoc_id],CONVERT(varbinary(max),x.[ma_minh_chung]),CONVERT(varbinary(max),x.[so_giay_to]),CONVERT(varbinary(max),x.[don_vi_cap]),x.[ngay_cap],x.[ngay_het_han],CONVERT(varbinary(max),x.[sha256_noi_dung]),x.[nguoi_nop_id],x.[thoi_diem_nop],x.[thoi_diem_tao],x.[nguoi_lien_quan_id],x.[ngay_bat_dau_hieu_luc],CONVERT(varbinary(max),x.[loai_hieu_luc]),x.[thoi_gian_chung_minh_tu],x.[thoi_gian_chung_minh_den],x.[hoc_ky_xac_nhan_id],CONVERT(varbinary(max),x.[loai_ho_ngheo]),CONVERT(varbinary(max),x.[ma_dia_ban]),CONVERT(varbinary(max),x.[can_cu_phan_loai_dia_ban])))) THROW 51051,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_tep_minh_chung_immutable] ON [dbo].[mghp_tep_minh_chung] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[giay_to_ho_so_id],i.[tep_dinh_kem_id] EXCEPT SELECT x.[id],x.[giay_to_ho_so_id],x.[tep_dinh_kem_id]))) THROW 51051,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_ket_qua_kiem_tra_giay_to_immutable] ON [dbo].[mghp_ket_qua_kiem_tra_giay_to] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],CONVERT(varbinary(max),i.[loai_kiem_tra]),CONVERT(varbinary(max),i.[ket_qua]),CONVERT(varbinary(max),i.[ghi_chu]),i.[ket_qua_kiem_tra_duoc_thay_the_id],i.[nguoi_thuc_hien_id],i.[thoi_diem_thao_tac],i.[giay_to_ho_so_id] EXCEPT SELECT x.[id],CONVERT(varbinary(max),x.[loai_kiem_tra]),CONVERT(varbinary(max),x.[ket_qua]),CONVERT(varbinary(max),x.[ghi_chu]),x.[ket_qua_kiem_tra_duoc_thay_the_id],x.[nguoi_thuc_hien_id],x.[thoi_diem_thao_tac],x.[giay_to_ho_so_id]))) THROW 51051,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_nhat_ky_ho_so_immutable] ON [dbo].[mghp_nhat_ky_ho_so] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[ho_so_id],i.[phien_ban_ho_so_id],i.[nguoi_thao_tac_id],CONVERT(varbinary(max),i.[loai_chu_the_thao_tac]),CONVERT(varbinary(max),i.[hanh_dong]),CONVERT(varbinary(max),i.[trang_thai_cu]),CONVERT(varbinary(max),i.[trang_thai_moi]),CONVERT(varbinary(max),i.[ly_do]),i.[ma_lien_ket_yeu_cau],i.[thoi_diem_tao],i.[yeu_cau_bo_sung_id],i.[han_cu],i.[han_moi] EXCEPT SELECT x.[id],x.[ho_so_id],x.[phien_ban_ho_so_id],x.[nguoi_thao_tac_id],CONVERT(varbinary(max),x.[loai_chu_the_thao_tac]),CONVERT(varbinary(max),x.[hanh_dong]),CONVERT(varbinary(max),x.[trang_thai_cu]),CONVERT(varbinary(max),x.[trang_thai_moi]),CONVERT(varbinary(max),x.[ly_do]),x.[ma_lien_ket_yeu_cau],x.[thoi_diem_tao],x.[yeu_cau_bo_sung_id],x.[han_cu],x.[han_moi]))) THROW 51051,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_nhat_ky_thao_tac_immutable] ON [dbo].[nhat_ky_thao_tac] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[su_kien_id],CONVERT(varbinary(max),i.[loai_chu_the_thao_tac]),i.[nguoi_dung_id],i.[he_thong_ngoai_id],CONVERT(varbinary(max),i.[hanh_dong]),CONVERT(varbinary(max),i.[loai_doi_tuong]),CONVERT(varbinary(max),i.[doi_tuong_id]),i.[lan_sua_doi_doi_tuong],i.[ma_lien_ket_yeu_cau],CONVERT(varbinary(max),i.[ly_do]),CONVERT(varbinary(max),i.[dia_chi_ip]),i.[thoi_diem_tao] EXCEPT SELECT x.[id],x.[su_kien_id],CONVERT(varbinary(max),x.[loai_chu_the_thao_tac]),x.[nguoi_dung_id],x.[he_thong_ngoai_id],CONVERT(varbinary(max),x.[hanh_dong]),CONVERT(varbinary(max),x.[loai_doi_tuong]),CONVERT(varbinary(max),x.[doi_tuong_id]),x.[lan_sua_doi_doi_tuong],x.[ma_lien_ket_yeu_cau],CONVERT(varbinary(max),x.[ly_do]),CONVERT(varbinary(max),x.[dia_chi_ip]),x.[thoi_diem_tao]))) THROW 51051,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_chi_tiet_nhat_ky_thao_tac_immutable] ON [dbo].[chi_tiet_nhat_ky_thao_tac] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[nhat_ky_id],CONVERT(varbinary(max),i.[ten_truong]),CONVERT(varbinary(max),i.[kieu_gia_tri]),CONVERT(varbinary(max),i.[trang_thai_gia_tri_cu]),CONVERT(varbinary(max),i.[trang_thai_gia_tri_moi]),CONVERT(varbinary(max),i.[chuoi_cu]),CONVERT(varbinary(max),i.[chuoi_moi]),i.[so_cu],i.[so_moi],i.[dung_sai_cu],i.[dung_sai_moi],i.[ngay_cu],i.[ngay_moi],i.[thoi_diem_cu],i.[thoi_diem_moi],CONVERT(varbinary(max),i.[tham_chieu_cu]),CONVERT(varbinary(max),i.[tham_chieu_moi]) EXCEPT SELECT x.[id],x.[nhat_ky_id],CONVERT(varbinary(max),x.[ten_truong]),CONVERT(varbinary(max),x.[kieu_gia_tri]),CONVERT(varbinary(max),x.[trang_thai_gia_tri_cu]),CONVERT(varbinary(max),x.[trang_thai_gia_tri_moi]),CONVERT(varbinary(max),x.[chuoi_cu]),CONVERT(varbinary(max),x.[chuoi_moi]),x.[so_cu],x.[so_moi],x.[dung_sai_cu],x.[dung_sai_moi],x.[ngay_cu],x.[ngay_moi],x.[thoi_diem_cu],x.[thoi_diem_moi],CONVERT(varbinary(max),x.[tham_chieu_cu]),CONVERT(varbinary(max),x.[tham_chieu_moi])))) THROW 51051,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_chinh_sach_dot_xet_immutable] ON [dbo].[mghp_chinh_sach_dot_xet] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (EXISTS (SELECT 1 FROM dbo.mghp_ho_so h WHERE h.dot_xet_id=x.dot_xet_id) OR EXISTS (SELECT 1 FROM dbo.mghp_dot_xet b WHERE b.id=x.dot_xet_id AND b.thoi_diem_khoa_cau_hinh IS NOT NULL)) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[dot_xet_id],i.[phien_ban_chinh_sach_id],i.[tep_mau_don_id],i.[thu_tu_hien_thi] EXCEPT SELECT x.[id],x.[dot_xet_id],x.[phien_ban_chinh_sach_id],x.[tep_mau_don_id],x.[thu_tu_hien_thi]))) THROW 51052,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_phien_ban_chinh_sach_immutable] ON [dbo].[mghp_phien_ban_chinh_sach] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (x.thoi_diem_khoa IS NOT NULL OR EXISTS (SELECT 1 FROM dbo.mghp_ho_so h WHERE h.phien_ban_chinh_sach_id=x.id) OR EXISTS (SELECT 1 FROM dbo.mghp_danh_gia_dieu_kien a WHERE a.phien_ban_chinh_sach_id=x.id)) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[chinh_sach_id],i.[phien_ban],CONVERT(varbinary(max),i.[ten_tai_thoi_diem_chot]),i.[ty_le_giam],CONVERT(varbinary(max),i.[ma_pham_vi]),i.[ngay_bat_dau_hieu_luc],i.[ngay_ket_thuc_hieu_luc],CONVERT(varbinary(max),i.[can_cu_phap_ly]),CONVERT(varbinary(max),i.[phien_ban_quy_tac_xet]),CONVERT(varbinary(max),i.[thoi_han_huong]),i.[uu_tien_khi_cung_muc],i.[nguoi_tao_id],i.[thoi_diem_tao],i.[thoi_diem_khoa],CONVERT(varbinary(max),i.[so_van_ban_ap_dung]),CONVERT(varbinary(max),i.[dieu_khoan_ap_dung]),CONVERT(varbinary(max),i.[url_can_cu]),CONVERT(varbinary(max),i.[che_do_cap_nhat_minh_chung]),CONVERT(varbinary(max),i.[huong_dan_cap_nhat_minh_chung]),CONVERT(varbinary(max),i.[tinh_trang_xac_nhan_quy_tac]),i.[tuoi_toi_thieu],i.[tuoi_toi_da],CONVERT(varbinary(max),i.[quy_tac_moc_tuoi]),i.[yeu_cau_van_bang_thu_nhat] EXCEPT SELECT x.[id],x.[chinh_sach_id],x.[phien_ban],CONVERT(varbinary(max),x.[ten_tai_thoi_diem_chot]),x.[ty_le_giam],CONVERT(varbinary(max),x.[ma_pham_vi]),x.[ngay_bat_dau_hieu_luc],x.[ngay_ket_thuc_hieu_luc],CONVERT(varbinary(max),x.[can_cu_phap_ly]),CONVERT(varbinary(max),x.[phien_ban_quy_tac_xet]),CONVERT(varbinary(max),x.[thoi_han_huong]),x.[uu_tien_khi_cung_muc],x.[nguoi_tao_id],x.[thoi_diem_tao],x.[thoi_diem_khoa],CONVERT(varbinary(max),x.[so_van_ban_ap_dung]),CONVERT(varbinary(max),x.[dieu_khoan_ap_dung]),CONVERT(varbinary(max),x.[url_can_cu]),CONVERT(varbinary(max),x.[che_do_cap_nhat_minh_chung]),CONVERT(varbinary(max),x.[huong_dan_cap_nhat_minh_chung]),CONVERT(varbinary(max),x.[tinh_trang_xac_nhan_quy_tac]),x.[tuoi_toi_thieu],x.[tuoi_toi_da],CONVERT(varbinary(max),x.[quy_tac_moc_tuoi]),x.[yeu_cau_van_bang_thu_nhat]))) THROW 51053,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_ung_vien_phien_ban_qd_immutable] ON [dbo].[mghp_ung_vien_phien_ban_qd] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (EXISTS (SELECT 1 FROM dbo.mghp_khoang_huong k WHERE k.ung_vien_id=x.id)) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[phien_ban_qd_id],i.[danh_gia_dieu_kien_id],i.[qua_trinh_hoc_id],i.[hoc_ky_id],CONVERT(varbinary(max),i.[ma_pham_vi]),i.[ty_le_giam_luc_chot],i.[ngay_du_dieu_kien_tu],i.[ngay_du_dieu_kien_den],CONVERT(varbinary(max),i.[trang_thai_lua_chon]),CONVERT(varbinary(max),i.[ma_ly_do]),CONVERT(varbinary(max),i.[ly_do]),i.[khoang_du_dieu_kien_id] EXCEPT SELECT x.[id],x.[phien_ban_qd_id],x.[danh_gia_dieu_kien_id],x.[qua_trinh_hoc_id],x.[hoc_ky_id],CONVERT(varbinary(max),x.[ma_pham_vi]),x.[ty_le_giam_luc_chot],x.[ngay_du_dieu_kien_tu],x.[ngay_du_dieu_kien_den],CONVERT(varbinary(max),x.[trang_thai_lua_chon]),CONVERT(varbinary(max),x.[ma_ly_do]),CONVERT(varbinary(max),x.[ly_do]),x.[khoang_du_dieu_kien_id]))) THROW 51054,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_khoang_du_dieu_kien_immutable] ON [dbo].[mghp_khoang_du_dieu_kien] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (EXISTS (SELECT 1 FROM dbo.mghp_ung_vien_phien_ban_qd c WHERE c.khoang_du_dieu_kien_id=x.id)) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[danh_gia_dieu_kien_id],i.[ngay_bat_dau],i.[ngay_ket_thuc],CONVERT(varbinary(max),i.[can_cu_thoi_gian]),i.[nguoi_xac_nhan_id],i.[thoi_diem_xac_nhan] EXCEPT SELECT x.[id],x.[danh_gia_dieu_kien_id],x.[ngay_bat_dau],x.[ngay_ket_thuc],CONVERT(varbinary(max),x.[can_cu_thoi_gian]),x.[nguoi_xac_nhan_id],x.[thoi_diem_xac_nhan]))) THROW 51054,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_qua_trinh_theo_hoc_immutable] ON [dbo].[qua_trinh_theo_hoc] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[sinh_vien_id],CONVERT(varbinary(max),i.[ma_qua_trinh_hoc]) EXCEPT SELECT x.[id],x.[sinh_vien_id],CONVERT(varbinary(max),x.[ma_qua_trinh_hoc])))) THROW 51055,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_quyen_huong_immutable] ON [dbo].[mghp_quyen_huong] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[sinh_vien_id],i.[qua_trinh_hoc_id],CONVERT(varbinary(max),i.[ma_pham_vi]),i.[chinh_sach_id] EXCEPT SELECT x.[id],x.[sinh_vien_id],x.[qua_trinh_hoc_id],CONVERT(varbinary(max),x.[ma_pham_vi]),x.[chinh_sach_id]))) THROW 51055,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_ket_qua_hoc_ky_immutable] ON [dbo].[mghp_ket_qua_hoc_ky] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],CONVERT(varbinary(max),i.[ma_ket_qua_huong]),i.[lan_sua_doi],i.[sinh_vien_id],i.[qua_trinh_hoc_id],i.[hoc_ky_id],CONVERT(varbinary(max),i.[ma_pham_vi]),i.[phien_ban_chinh_sach_id],i.[dong_qd_id],i.[quyen_huong_id],i.[danh_gia_dieu_kien_id],i.[ket_qua_huong_duoc_thay_the_id],CONVERT(varbinary(max),i.[loai_huong]),CONVERT(varbinary(max),i.[loai_thao_tac]),i.[ty_le_giam],CONVERT(varbinary(max),i.[trang_thai]),i.[ngay_hieu_luc],i.[thoi_diem_tao],i.[ngay_huong_tu],i.[ngay_huong_den],CONVERT(varbinary(max),i.[pham_vi_thoi_gian_huong]) EXCEPT SELECT x.[id],CONVERT(varbinary(max),x.[ma_ket_qua_huong]),x.[lan_sua_doi],x.[sinh_vien_id],x.[qua_trinh_hoc_id],x.[hoc_ky_id],CONVERT(varbinary(max),x.[ma_pham_vi]),x.[phien_ban_chinh_sach_id],x.[dong_qd_id],x.[quyen_huong_id],x.[danh_gia_dieu_kien_id],x.[ket_qua_huong_duoc_thay_the_id],CONVERT(varbinary(max),x.[loai_huong]),CONVERT(varbinary(max),x.[loai_thao_tac]),x.[ty_le_giam],CONVERT(varbinary(max),x.[trang_thai]),x.[ngay_hieu_luc],x.[thoi_diem_tao],x.[ngay_huong_tu],x.[ngay_huong_den],CONVERT(varbinary(max),x.[pham_vi_thoi_gian_huong])))) THROW 51056,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_phien_ban_van_ban_immutable] ON [dbo].[phien_ban_van_ban] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (x.thoi_diem_chot IS NOT NULL) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[van_ban_id],i.[so_phien_ban],CONVERT(varbinary(max),i.[tieu_de_tai_thoi_diem_chot]),i.[tep_goc_id],CONVERT(varbinary(max),i.[ma_tham_chieu_nghiep_vu]),i.[lan_sua_doi_nghiep_vu],CONVERT(varbinary(max),i.[sha256_tap_du_lieu]),CONVERT(varbinary(max),i.[trang_thai_noi_dung]),i.[nguoi_doi_chieu_id],i.[thoi_diem_doi_chieu],i.[nguoi_chot_id],i.[thoi_diem_chot],i.[nguoi_tao_id],i.[thoi_diem_tao] EXCEPT SELECT x.[id],x.[van_ban_id],x.[so_phien_ban],CONVERT(varbinary(max),x.[tieu_de_tai_thoi_diem_chot]),x.[tep_goc_id],CONVERT(varbinary(max),x.[ma_tham_chieu_nghiep_vu]),x.[lan_sua_doi_nghiep_vu],CONVERT(varbinary(max),x.[sha256_tap_du_lieu]),CONVERT(varbinary(max),x.[trang_thai_noi_dung]),x.[nguoi_doi_chieu_id],x.[thoi_diem_doi_chieu],x.[nguoi_chot_id],x.[thoi_diem_chot],x.[nguoi_tao_id],x.[thoi_diem_tao]))) THROW 51057,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_cong_bo_immutable] ON [dbo].[mghp_cong_bo] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[phien_ban_qd_id],i.[thong_bao_id],i.[nguoi_cong_bo_id],i.[thoi_diem_cong_bo],CONVERT(varbinary(max),i.[ma_cong_bo]),CONVERT(varbinary(max),i.[sha256_tap_du_lieu]),CONVERT(varbinary(max),i.[ghi_chu_cong_khai]),i.[thoi_diem_tao] EXCEPT SELECT x.[id],x.[phien_ban_qd_id],x.[thong_bao_id],x.[nguoi_cong_bo_id],x.[thoi_diem_cong_bo],CONVERT(varbinary(max),x.[ma_cong_bo]),CONVERT(varbinary(max),x.[sha256_tap_du_lieu]),CONVERT(varbinary(max),x.[ghi_chu_cong_khai]),x.[thoi_diem_tao]))) THROW 51056,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_bien_dong_quyen_huong_immutable] ON [dbo].[mghp_bien_dong_quyen_huong] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[quyen_huong_id],CONVERT(varbinary(max),i.[loai_su_kien]),i.[ngay_hieu_luc],i.[dong_qd_id],CONVERT(varbinary(max),i.[ly_do]),i.[nguoi_ghi_nhan_id],i.[thoi_diem_ghi_nhan],i.[so_phien_ban],i.[bien_dong_truoc_id],CONVERT(varbinary(max),i.[trang_thai_sau]),i.[hoc_ky_bat_dau_sau_id],i.[hoc_ky_ket_thuc_sau_id],i.[ngay_cong_nhan_tu_sau],i.[ngay_cong_nhan_den_sau],i.[ty_le_giam_sau],i.[ngay_ra_soat_tiep_sau],CONVERT(varbinary(max),i.[che_do_cap_nhat_sau]) EXCEPT SELECT x.[id],x.[quyen_huong_id],CONVERT(varbinary(max),x.[loai_su_kien]),x.[ngay_hieu_luc],x.[dong_qd_id],CONVERT(varbinary(max),x.[ly_do]),x.[nguoi_ghi_nhan_id],x.[thoi_diem_ghi_nhan],x.[so_phien_ban],x.[bien_dong_truoc_id],CONVERT(varbinary(max),x.[trang_thai_sau]),x.[hoc_ky_bat_dau_sau_id],x.[hoc_ky_ket_thuc_sau_id],x.[ngay_cong_nhan_tu_sau],x.[ngay_cong_nhan_den_sau],x.[ty_le_giam_sau],x.[ngay_ra_soat_tiep_sau],CONVERT(varbinary(max),x.[che_do_cap_nhat_sau])))) THROW 51056,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_xac_nhan_ke_toan_immutable] ON [dbo].[mghp_xac_nhan_ke_toan] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[dong_gui_id],i.[ban_tin_nhan_id],i.[he_thong_cung_cap_id],i.[xac_nhan_ben_ngoai_id],CONVERT(varbinary(max),i.[so_xac_nhan]),CONVERT(varbinary(max),i.[loai_bien_nhan]),i.[so_tien],CONVERT(varbinary(max),i.[don_vi_tien]),CONVERT(varbinary(max),i.[phien_ban_quy_tac_tinh]),i.[phien_ban_co_so_tinh],CONVERT(varbinary(max),i.[sha256_du_lieu_gui]),i.[thoi_diem_xu_ly],i.[thoi_diem_tiep_nhan],i.[thu_tu_su_kien],CONVERT(varbinary(max),i.[trang_thai_xac_nhan]),i.[so_tien_co_so],i.[ty_le_giam_xac_nhan],CONVERT(varbinary(max),i.[ma_loi]),CONVERT(varbinary(max),i.[noi_dung_loi]) EXCEPT SELECT x.[id],x.[dong_gui_id],x.[ban_tin_nhan_id],x.[he_thong_cung_cap_id],x.[xac_nhan_ben_ngoai_id],CONVERT(varbinary(max),x.[so_xac_nhan]),CONVERT(varbinary(max),x.[loai_bien_nhan]),x.[so_tien],CONVERT(varbinary(max),x.[don_vi_tien]),CONVERT(varbinary(max),x.[phien_ban_quy_tac_tinh]),x.[phien_ban_co_so_tinh],CONVERT(varbinary(max),x.[sha256_du_lieu_gui]),x.[thoi_diem_xu_ly],x.[thoi_diem_tiep_nhan],x.[thu_tu_su_kien],CONVERT(varbinary(max),x.[trang_thai_xac_nhan]),x.[so_tien_co_so],x.[ty_le_giam_xac_nhan],CONVERT(varbinary(max),x.[ma_loi]),CONVERT(varbinary(max),x.[noi_dung_loi])))) THROW 51056,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_bien_nhan_ky_immutable] ON [dbo].[bien_nhan_ky] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[yeu_cau_ky_id],i.[buoc_ky_id],i.[lan_goi_id],i.[he_thong_cung_cap_id],i.[chu_ky_ben_ngoai_id],i.[van_ban_ben_ngoai_id],i.[nguoi_thao_tac_phia_ca_id],i.[chung_thu_id],CONVERT(varbinary(max),i.[so_seri_chung_thu]),CONVERT(varbinary(max),i.[ten_nguoi_ky]),CONVERT(varbinary(max),i.[email_nguoi_ky]),CONVERT(varbinary(max),i.[ma_nguoi_ky_doi_tac]),CONVERT(varbinary(max),i.[ung_dung_nguon]),CONVERT(varbinary(max),i.[sha256_van_ban]),CONVERT(varbinary(max),i.[sha256_pdf_da_ky]),CONVERT(varbinary(max),i.[trang_thai_chu_ky_phia_ca]),i.[thoi_diem_ky],i.[thoi_diem_tiep_nhan],i.[phien_ban_van_ban_ca],CONVERT(varbinary(max),i.[phien_ban_hop_dong]),CONVERT(varbinary(max),i.[sha256_phan_hoi]) EXCEPT SELECT x.[id],x.[yeu_cau_ky_id],x.[buoc_ky_id],x.[lan_goi_id],x.[he_thong_cung_cap_id],x.[chu_ky_ben_ngoai_id],x.[van_ban_ben_ngoai_id],x.[nguoi_thao_tac_phia_ca_id],x.[chung_thu_id],CONVERT(varbinary(max),x.[so_seri_chung_thu]),CONVERT(varbinary(max),x.[ten_nguoi_ky]),CONVERT(varbinary(max),x.[email_nguoi_ky]),CONVERT(varbinary(max),x.[ma_nguoi_ky_doi_tac]),CONVERT(varbinary(max),x.[ung_dung_nguon]),CONVERT(varbinary(max),x.[sha256_van_ban]),CONVERT(varbinary(max),x.[sha256_pdf_da_ky]),CONVERT(varbinary(max),x.[trang_thai_chu_ky_phia_ca]),x.[thoi_diem_ky],x.[thoi_diem_tiep_nhan],x.[phien_ban_van_ban_ca],CONVERT(varbinary(max),x.[phien_ban_hop_dong]),CONVERT(varbinary(max),x.[sha256_phan_hoi])))) THROW 51057,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_phien_ban_qd_immutable] ON [dbo].[mghp_phien_ban_qd] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (x.thoi_diem_chot IS NOT NULL OR EXISTS (SELECT 1 FROM dbo.mghp_cong_bo p WHERE p.phien_ban_qd_id=x.id)) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[quyet_dinh_id],i.[lan_sua_doi],i.[ngay_bat_dau_hieu_luc],CONVERT(varbinary(max),i.[sha256_tap_du_lieu]),i.[so_dong],i.[nguoi_chot_id],i.[thoi_diem_chot],i.[thoi_diem_tao],CONVERT(varbinary(max),i.[giai_doan]),i.[phien_ban_truoc_id],CONVERT(varbinary(max),i.[ly_do_tao_phien_ban]),i.[nguoi_tao_id] EXCEPT SELECT x.[id],x.[quyet_dinh_id],x.[lan_sua_doi],x.[ngay_bat_dau_hieu_luc],CONVERT(varbinary(max),x.[sha256_tap_du_lieu]),x.[so_dong],x.[nguoi_chot_id],x.[thoi_diem_chot],x.[thoi_diem_tao],CONVERT(varbinary(max),x.[giai_doan]),x.[phien_ban_truoc_id],CONVERT(varbinary(max),x.[ly_do_tao_phien_ban]),x.[nguoi_tao_id]))) THROW 51057,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_dong_qd_immutable] ON [dbo].[mghp_dong_qd] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (EXISTS (SELECT 1 FROM dbo.mghp_phien_ban_qd v WHERE v.id=x.phien_ban_qd_id AND v.thoi_diem_chot IS NOT NULL) OR EXISTS (SELECT 1 FROM dbo.mghp_cong_bo p WHERE p.phien_ban_qd_id=x.phien_ban_qd_id)) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[phien_ban_qd_id],i.[sinh_vien_id],i.[qua_trinh_hoc_id],i.[hoc_ky_ap_dung_id],i.[phien_ban_chinh_sach_id],i.[quyen_huong_nguon_id],i.[danh_gia_dieu_kien_id],CONVERT(varbinary(max),i.[ma_pham_vi]),i.[ty_le_giam],CONVERT(varbinary(max),i.[loai_thao_tac]),i.[dong_duoc_thay_the_id],CONVERT(varbinary(max),i.[sha256_dong]),i.[phien_ban_ho_so_nguon_id],i.[ung_vien_id],CONVERT(varbinary(max),i.[mssv_luc_chot]),CONVERT(varbinary(max),i.[ho_ten_luc_chot]),CONVERT(varbinary(max),i.[ma_lop_luc_chot]),CONVERT(varbinary(max),i.[ten_chinh_sach_luc_chot]),i.[hoc_ky_bat_dau_huong_id],i.[hoc_ky_ket_thuc_huong_id],i.[ngay_huong_tu],i.[ngay_huong_den],CONVERT(varbinary(max),i.[pham_vi_thoi_gian_huong]) EXCEPT SELECT x.[id],x.[phien_ban_qd_id],x.[sinh_vien_id],x.[qua_trinh_hoc_id],x.[hoc_ky_ap_dung_id],x.[phien_ban_chinh_sach_id],x.[quyen_huong_nguon_id],x.[danh_gia_dieu_kien_id],CONVERT(varbinary(max),x.[ma_pham_vi]),x.[ty_le_giam],CONVERT(varbinary(max),x.[loai_thao_tac]),x.[dong_duoc_thay_the_id],CONVERT(varbinary(max),x.[sha256_dong]),x.[phien_ban_ho_so_nguon_id],x.[ung_vien_id],CONVERT(varbinary(max),x.[mssv_luc_chot]),CONVERT(varbinary(max),x.[ho_ten_luc_chot]),CONVERT(varbinary(max),x.[ma_lop_luc_chot]),CONVERT(varbinary(max),x.[ten_chinh_sach_luc_chot]),x.[hoc_ky_bat_dau_huong_id],x.[hoc_ky_ket_thuc_huong_id],x.[ngay_huong_tu],x.[ngay_huong_den],CONVERT(varbinary(max),x.[pham_vi_thoi_gian_huong])))) THROW 51057,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_khoang_huong_immutable] ON [dbo].[mghp_khoang_huong] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (EXISTS (SELECT 1 FROM dbo.mghp_dong_qd r JOIN dbo.mghp_phien_ban_qd v ON v.id=r.phien_ban_qd_id WHERE r.id=x.dong_qd_id AND (v.thoi_diem_chot IS NOT NULL OR EXISTS (SELECT 1 FROM dbo.mghp_cong_bo p WHERE p.phien_ban_qd_id=v.id)))) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[dong_qd_id],i.[ung_vien_id],i.[ngay_bat_dau],i.[ngay_ket_thuc],i.[ty_le_giam],CONVERT(varbinary(max),i.[ly_do]) EXCEPT SELECT x.[id],x.[dong_qd_id],x.[ung_vien_id],x.[ngay_bat_dau],x.[ngay_ket_thuc],x.[ty_le_giam],CONVERT(varbinary(max),x.[ly_do])))) THROW 51057,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_danh_gia_dieu_kien_immutable] ON [dbo].[mghp_danh_gia_dieu_kien] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (x.la_ban_hien_hanh=1 OR EXISTS (SELECT 1 FROM dbo.mghp_ung_vien_phien_ban_qd c WHERE c.danh_gia_dieu_kien_id=x.id)) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[sinh_vien_id],i.[qua_trinh_hoc_id],i.[hoc_ky_ap_dung_id],i.[phien_ban_chinh_sach_id],i.[quyen_huong_nguon_id],CONVERT(varbinary(max),i.[phien_ban_quy_tac]),CONVERT(varbinary(max),i.[ket_qua]),i.[du_lieu_nguon_tinh_den],i.[minh_chung_co_gia_tri_den],i.[nguoi_danh_gia_id],i.[thoi_diem_danh_gia],i.[dot_xet_id],i.[phien_ban_ho_so_nguon_id],i.[so_phien_ban_danh_gia],i.[danh_gia_truoc_id],CONVERT(varbinary(max),i.[trang_thai_ra_soat_minh_chung]),i.[ho_so_nguon_id],i.[phien_ban_cap_nhat_minh_chung_id],CONVERT(varbinary(max),i.[ma_pham_vi]),CONVERT(varbinary(max),i.[trang_thai_xu_ly]),CONVERT(varbinary(max),i.[ly_do_ket_luan]),CONVERT(varbinary(max),i.[ly_do_danh_gia_lai]),i.[ngay_xet_tu],i.[ngay_xet_den],i.[ngay_tinh_tuoi],i.[ngay_sinh_dung_de_xet],CONVERT(varbinary(max),i.[trang_thai_xet_giua_ky]),CONVERT(varbinary(max),i.[can_cu_xet_thoi_gian]),i.[lich_su_trang_thai_sv_id] EXCEPT SELECT x.[id],x.[sinh_vien_id],x.[qua_trinh_hoc_id],x.[hoc_ky_ap_dung_id],x.[phien_ban_chinh_sach_id],x.[quyen_huong_nguon_id],CONVERT(varbinary(max),x.[phien_ban_quy_tac]),CONVERT(varbinary(max),x.[ket_qua]),x.[du_lieu_nguon_tinh_den],x.[minh_chung_co_gia_tri_den],x.[nguoi_danh_gia_id],x.[thoi_diem_danh_gia],x.[dot_xet_id],x.[phien_ban_ho_so_nguon_id],x.[so_phien_ban_danh_gia],x.[danh_gia_truoc_id],CONVERT(varbinary(max),x.[trang_thai_ra_soat_minh_chung]),x.[ho_so_nguon_id],x.[phien_ban_cap_nhat_minh_chung_id],CONVERT(varbinary(max),x.[ma_pham_vi]),CONVERT(varbinary(max),x.[trang_thai_xu_ly]),CONVERT(varbinary(max),x.[ly_do_ket_luan]),CONVERT(varbinary(max),x.[ly_do_danh_gia_lai]),x.[ngay_xet_tu],x.[ngay_xet_den],x.[ngay_tinh_tuoi],x.[ngay_sinh_dung_de_xet],CONVERT(varbinary(max),x.[trang_thai_xet_giua_ky]),CONVERT(varbinary(max),x.[can_cu_xet_thoi_gian]),x.[lich_su_trang_thai_sv_id]))) THROW 51057,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_dot_gui_ke_toan_immutable] ON [dbo].[mghp_dot_gui_ke_toan] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[yeu_cau_ben_ngoai_id],i.[he_thong_dich_id],i.[phien_ban_qd_id],i.[hoc_ky_id],CONVERT(varbinary(max),i.[phien_ban_cau_truc_du_lieu]),CONVERT(varbinary(max),i.[sha256_du_lieu_gui]),i.[ma_lien_ket_yeu_cau],i.[nguoi_tao_id],i.[thoi_diem_tao],CONVERT(varbinary(max),i.[ma_qd_luc_gui]),CONVERT(varbinary(max),i.[so_qd_luc_gui]),i.[ngay_qd_luc_gui],i.[lan_sua_doi_qd_luc_gui],i.[thoi_diem_cong_bo_luc_gui],CONVERT(varbinary(max),i.[sha256_pdf_da_ky]),CONVERT(varbinary(max),i.[phien_ban_adapter]) EXCEPT SELECT x.[id],x.[yeu_cau_ben_ngoai_id],x.[he_thong_dich_id],x.[phien_ban_qd_id],x.[hoc_ky_id],CONVERT(varbinary(max),x.[phien_ban_cau_truc_du_lieu]),CONVERT(varbinary(max),x.[sha256_du_lieu_gui]),x.[ma_lien_ket_yeu_cau],x.[nguoi_tao_id],x.[thoi_diem_tao],CONVERT(varbinary(max),x.[ma_qd_luc_gui]),CONVERT(varbinary(max),x.[so_qd_luc_gui]),x.[ngay_qd_luc_gui],x.[lan_sua_doi_qd_luc_gui],x.[thoi_diem_cong_bo_luc_gui],CONVERT(varbinary(max),x.[sha256_pdf_da_ky]),CONVERT(varbinary(max),x.[phien_ban_adapter])))) THROW 51056,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_dong_gui_ke_toan_immutable] ON [dbo].[mghp_dong_gui_ke_toan] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[dot_gui_id],i.[dong_id],i.[ket_qua_huong_id],CONVERT(varbinary(max),i.[loai_thao_tac]),i.[lan_sua_doi_duoc_thay_the],CONVERT(varbinary(max),i.[sha256_du_lieu_gui]),CONVERT(varbinary(max),i.[mssv_luc_gui]),CONVERT(varbinary(max),i.[ma_qua_trinh_hoc_luc_gui]),CONVERT(varbinary(max),i.[ma_hoc_ky_luc_gui]),CONVERT(varbinary(max),i.[ma_pham_vi_luc_gui]),CONVERT(varbinary(max),i.[ma_chinh_sach_luc_gui]),i.[phien_ban_chinh_sach_luc_gui],CONVERT(varbinary(max),i.[ma_ket_qua_huong_luc_gui]),i.[lan_sua_doi_ket_qua_luc_gui],i.[ty_le_giam_luc_gui] EXCEPT SELECT x.[id],x.[dot_gui_id],x.[dong_id],x.[ket_qua_huong_id],CONVERT(varbinary(max),x.[loai_thao_tac]),x.[lan_sua_doi_duoc_thay_the],CONVERT(varbinary(max),x.[sha256_du_lieu_gui]),CONVERT(varbinary(max),x.[mssv_luc_gui]),CONVERT(varbinary(max),x.[ma_qua_trinh_hoc_luc_gui]),CONVERT(varbinary(max),x.[ma_hoc_ky_luc_gui]),CONVERT(varbinary(max),x.[ma_pham_vi_luc_gui]),CONVERT(varbinary(max),x.[ma_chinh_sach_luc_gui]),x.[phien_ban_chinh_sach_luc_gui],CONVERT(varbinary(max),x.[ma_ket_qua_huong_luc_gui]),x.[lan_sua_doi_ket_qua_luc_gui],x.[ty_le_giam_luc_gui]))) THROW 51056,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_ban_tin_ke_toan_nhan_immutable] ON [dbo].[mghp_ban_tin_ke_toan_nhan] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[ban_tin_nhan_id],CONVERT(varbinary(max),i.[ma_he_thong_nguon]),i.[yeu_cau_ben_ngoai_id],i.[dong_ben_ngoai_id],CONVERT(varbinary(max),i.[ma_ket_qua_huong]),i.[lan_sua_doi_ket_qua_huong],CONVERT(varbinary(max),i.[trang_thai_item_luc_nhan]),CONVERT(varbinary(max),i.[loai_thao_tac]),i.[xac_nhan_ben_ngoai_id],CONVERT(varbinary(max),i.[so_xac_nhan]),i.[so_tien_co_so],i.[phien_ban_co_so_tinh],i.[ty_le_giam],i.[so_tien_giam],CONVERT(varbinary(max),i.[don_vi_tien]),CONVERT(varbinary(max),i.[phien_ban_quy_tac_tinh]),i.[thoi_diem_xu_ly_phia_ke_toan],CONVERT(varbinary(max),i.[ma_loi]),CONVERT(varbinary(max),i.[noi_dung_loi]),i.[cho_phep_thu_lai] EXCEPT SELECT x.[id],x.[ban_tin_nhan_id],CONVERT(varbinary(max),x.[ma_he_thong_nguon]),x.[yeu_cau_ben_ngoai_id],x.[dong_ben_ngoai_id],CONVERT(varbinary(max),x.[ma_ket_qua_huong]),x.[lan_sua_doi_ket_qua_huong],CONVERT(varbinary(max),x.[trang_thai_item_luc_nhan]),CONVERT(varbinary(max),x.[loai_thao_tac]),x.[xac_nhan_ben_ngoai_id],CONVERT(varbinary(max),x.[so_xac_nhan]),x.[so_tien_co_so],x.[phien_ban_co_so_tinh],x.[ty_le_giam],x.[so_tien_giam],CONVERT(varbinary(max),x.[don_vi_tien]),CONVERT(varbinary(max),x.[phien_ban_quy_tac_tinh]),x.[thoi_diem_xu_ly_phia_ke_toan],CONVERT(varbinary(max),x.[ma_loi]),CONVERT(varbinary(max),x.[noi_dung_loi]),x.[cho_phep_thu_lai]))) THROW 51056,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_tep_dinh_kem_immutable] ON [dbo].[tep_dinh_kem] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (x.trang_thai_luu_tru=N''available'') AND (i.id IS NULL OR EXISTS (SELECT i.[id],CONVERT(varbinary(max),i.[ten_cloud]),CONVERT(varbinary(max),i.[ma_tai_nguyen]),CONVERT(varbinary(max),i.[ma_cong_khai]),CONVERT(varbinary(max),i.[loai_tai_nguyen]),CONVERT(varbinary(max),i.[kieu_phan_phoi]),i.[phien_ban_luu_tru],CONVERT(varbinary(max),i.[dinh_dang_tep]),CONVERT(varbinary(max),i.[ten_tep]),CONVERT(varbinary(max),i.[kieu_noi_dung]),i.[kich_thuoc_tep],CONVERT(varbinary(max),i.[sha256]),i.[nguoi_so_huu_id],i.[nguoi_tai_len_id],i.[thoi_diem_tai_len] EXCEPT SELECT x.[id],CONVERT(varbinary(max),x.[ten_cloud]),CONVERT(varbinary(max),x.[ma_tai_nguyen]),CONVERT(varbinary(max),x.[ma_cong_khai]),CONVERT(varbinary(max),x.[loai_tai_nguyen]),CONVERT(varbinary(max),x.[kieu_phan_phoi]),x.[phien_ban_luu_tru],CONVERT(varbinary(max),x.[dinh_dang_tep]),CONVERT(varbinary(max),x.[ten_tep]),CONVERT(varbinary(max),x.[kieu_noi_dung]),x.[kich_thuoc_tep],CONVERT(varbinary(max),x.[sha256]),x.[nguoi_so_huu_id],x.[nguoi_tai_len_id],x.[thoi_diem_tai_len]))) THROW 51057,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_nhom_yeu_cau_giay_to_locked_policy] ON [dbo].[mghp_nhom_yeu_cau_giay_to] AFTER INSERT, UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM (SELECT i.phien_ban_chinh_sach_id AS version_id FROM inserted i UNION SELECT i.phien_ban_chinh_sach_id AS version_id FROM deleted i) used JOIN dbo.mghp_phien_ban_chinh_sach p ON p.id=used.version_id WHERE p.thoi_diem_khoa IS NOT NULL OR EXISTS (SELECT 1 FROM dbo.mghp_ho_so h WHERE h.phien_ban_chinh_sach_id=p.id) OR EXISTS (SELECT 1 FROM dbo.mghp_chinh_sach_dot_xet b JOIN dbo.mghp_dot_xet d ON d.id=b.dot_xet_id WHERE b.phien_ban_chinh_sach_id=p.id AND d.thoi_diem_khoa_cau_hinh IS NOT NULL)) THROW 51052,N''Criteria/evidence configuration of a locked or used policy is immutable.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_tieu_chi_chinh_sach_locked_policy] ON [dbo].[mghp_tieu_chi_chinh_sach] AFTER INSERT, UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM (SELECT i.phien_ban_chinh_sach_id AS version_id FROM inserted i UNION SELECT i.phien_ban_chinh_sach_id AS version_id FROM deleted i) used JOIN dbo.mghp_phien_ban_chinh_sach p ON p.id=used.version_id WHERE p.thoi_diem_khoa IS NOT NULL OR EXISTS (SELECT 1 FROM dbo.mghp_ho_so h WHERE h.phien_ban_chinh_sach_id=p.id) OR EXISTS (SELECT 1 FROM dbo.mghp_chinh_sach_dot_xet b JOIN dbo.mghp_dot_xet d ON d.id=b.dot_xet_id WHERE b.phien_ban_chinh_sach_id=p.id AND d.thoi_diem_khoa_cau_hinh IS NOT NULL)) THROW 51052,N''Criteria/evidence configuration of a locked or used policy is immutable.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[tr_mghp_giay_to_bat_buoc_locked_policy] ON [dbo].[mghp_giay_to_bat_buoc] AFTER INSERT, UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM (SELECT n.phien_ban_chinh_sach_id AS version_id FROM inserted i JOIN dbo.mghp_nhom_yeu_cau_giay_to n ON n.id=i.nhom_id UNION SELECT n.phien_ban_chinh_sach_id AS version_id FROM deleted i JOIN dbo.mghp_nhom_yeu_cau_giay_to n ON n.id=i.nhom_id) used JOIN dbo.mghp_phien_ban_chinh_sach p ON p.id=used.version_id WHERE p.thoi_diem_khoa IS NOT NULL OR EXISTS (SELECT 1 FROM dbo.mghp_ho_so h WHERE h.phien_ban_chinh_sach_id=p.id) OR EXISTS (SELECT 1 FROM dbo.mghp_chinh_sach_dot_xet b JOIN dbo.mghp_dot_xet d ON d.id=b.dot_xet_id WHERE b.phien_ban_chinh_sach_id=p.id AND d.thoi_diem_khoa_cau_hinh IS NOT NULL)) THROW 51052,N''Criteria/evidence configuration of a locked or used policy is immutable.'',1;
END;');
