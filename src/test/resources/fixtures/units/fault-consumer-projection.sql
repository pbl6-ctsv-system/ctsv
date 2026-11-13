-- TEST-ONLY consumer projection co-located in the isolated SIS fault database.
-- Derived exactly from CTSV V1/V2, not a new production database design or Flyway history.
-- Source V1 SHA256: 847293262f3797176b3764c1de10c80050790b87325e4a8fb84eabccb6273ef9
IF DB_NAME()<>N'p01_sis_fault_20261111' OR CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName'))<>N'DEV2022' THROW 51095,N'Unexpected fault projection target',1;
GO
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

ALTER TABLE [dbo].[he_thong_ngoai] ADD CONSTRAINT [df_he_thong_ngoai_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[nguoi_dung] ADD CONSTRAINT [df_nguoi_dung_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[don_vi] ADD CONSTRAINT [df_don_vi_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
ALTER TABLE [dbo].[nhat_ky_thao_tac] ADD CONSTRAINT [df_nhat_ky_thao_tac_thoi_diem_tao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [thoi_diem_tao];
CREATE UNIQUE NONCLUSTERED INDEX [uq_he_thong_ngoai_ma] ON [dbo].[he_thong_ngoai] ([ma]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_nguoi_dung_dia_chi_email] ON [dbo].[nguoi_dung] ([dia_chi_email]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_don_vi_ma] ON [dbo].[don_vi] ([ma]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_nhat_ky_thao_tac_su_kien_id] ON [dbo].[nhat_ky_thao_tac] ([su_kien_id]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_chi_tiet_nhat_ky_thao_tac_nhat_ky_id_ten_truong] ON [dbo].[chi_tiet_nhat_ky_thao_tac] ([nhat_ky_id], [ten_truong]);
CREATE UNIQUE NONCLUSTERED INDEX [uq_don_vi_he_thong_nguon_id_ma_dinh_danh_ben_ngoai_filtered] ON [dbo].[don_vi] ([he_thong_nguon_id], [ma_dinh_danh_ben_ngoai]) WHERE [he_thong_nguon_id] IS NOT NULL AND [ma_dinh_danh_ben_ngoai] IS NOT NULL;
ALTER TABLE [dbo].[don_vi] WITH CHECK ADD CONSTRAINT [fk_don_vi_don_vi_cha_id] FOREIGN KEY ([don_vi_cha_id]) REFERENCES [dbo].[don_vi] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[don_vi] WITH CHECK ADD CONSTRAINT [fk_don_vi_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [fk_nhat_ky_thao_tac_nguoi_dung_id] FOREIGN KEY ([nguoi_dung_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [fk_nhat_ky_thao_tac_he_thong_ngoai_id] FOREIGN KEY ([he_thong_ngoai_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[dot_dong_bo] WITH CHECK ADD CONSTRAINT [fk_dot_dong_bo_he_thong_nguon_id] FOREIGN KEY ([he_thong_nguon_id]) REFERENCES [dbo].[he_thong_ngoai] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[dot_dong_bo] WITH CHECK ADD CONSTRAINT [fk_dot_dong_bo_nguoi_yeu_cau_id] FOREIGN KEY ([nguoi_yeu_cau_id]) REFERENCES [dbo].[nguoi_dung] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[chi_tiet_nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [fk_chi_tiet_nhat_ky_thao_tac_nhat_ky_id] FOREIGN KEY ([nhat_ky_id]) REFERENCES [dbo].[nhat_ky_thao_tac] ([id]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[chi_tiet_nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [ck_chi_tiet_nhat_ky_thao_tac_value_cu] CHECK (([trang_thai_gia_tri_cu]=N'co_gia_tri' AND (CASE WHEN [chuoi_cu] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [so_cu] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [dung_sai_cu] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [ngay_cu] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [thoi_diem_cu] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [tham_chieu_cu] IS NOT NULL THEN 1 ELSE 0 END)=1) OR ([trang_thai_gia_tri_cu] IN (N'null',N'khong_ton_tai',N'da_che') AND [chuoi_cu] IS NULL AND [so_cu] IS NULL AND [dung_sai_cu] IS NULL AND [ngay_cu] IS NULL AND [thoi_diem_cu] IS NULL AND [tham_chieu_cu] IS NULL));
ALTER TABLE [dbo].[chi_tiet_nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [ck_chi_tiet_nhat_ky_thao_tac_slot_type_cu] CHECK ([trang_thai_gia_tri_cu]<>N'co_gia_tri' OR (([kieu_gia_tri]=N'chuoi' AND [chuoi_cu] IS NOT NULL) OR ([kieu_gia_tri]=N'so' AND [so_cu] IS NOT NULL) OR ([kieu_gia_tri]=N'dung_sai' AND [dung_sai_cu] IS NOT NULL) OR ([kieu_gia_tri]=N'ngay' AND [ngay_cu] IS NOT NULL) OR ([kieu_gia_tri]=N'thoi_diem' AND [thoi_diem_cu] IS NOT NULL) OR ([kieu_gia_tri]=N'tham_chieu' AND [tham_chieu_cu] IS NOT NULL)));
ALTER TABLE [dbo].[chi_tiet_nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [ck_chi_tiet_nhat_ky_thao_tac_value_moi] CHECK (([trang_thai_gia_tri_moi]=N'co_gia_tri' AND (CASE WHEN [chuoi_moi] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [so_moi] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [dung_sai_moi] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [ngay_moi] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [thoi_diem_moi] IS NOT NULL THEN 1 ELSE 0 END+CASE WHEN [tham_chieu_moi] IS NOT NULL THEN 1 ELSE 0 END)=1) OR ([trang_thai_gia_tri_moi] IN (N'null',N'khong_ton_tai',N'da_che') AND [chuoi_moi] IS NULL AND [so_moi] IS NULL AND [dung_sai_moi] IS NULL AND [ngay_moi] IS NULL AND [thoi_diem_moi] IS NULL AND [tham_chieu_moi] IS NULL));
ALTER TABLE [dbo].[chi_tiet_nhat_ky_thao_tac] WITH CHECK ADD CONSTRAINT [ck_chi_tiet_nhat_ky_thao_tac_slot_type_moi] CHECK ([trang_thai_gia_tri_moi]<>N'co_gia_tri' OR (([kieu_gia_tri]=N'chuoi' AND [chuoi_moi] IS NOT NULL) OR ([kieu_gia_tri]=N'so' AND [so_moi] IS NOT NULL) OR ([kieu_gia_tri]=N'dung_sai' AND [dung_sai_moi] IS NOT NULL) OR ([kieu_gia_tri]=N'ngay' AND [ngay_moi] IS NOT NULL) OR ([kieu_gia_tri]=N'thoi_diem' AND [thoi_diem_moi] IS NOT NULL) OR ([kieu_gia_tri]=N'tham_chieu' AND [tham_chieu_moi] IS NOT NULL)));
CREATE NONCLUSTERED INDEX [ix_don_vi_don_vi_cha_id] ON [dbo].[don_vi] ([don_vi_cha_id]);
CREATE NONCLUSTERED INDEX [ix_nhat_ky_thao_tac_nguoi_dung_id] ON [dbo].[nhat_ky_thao_tac] ([nguoi_dung_id]);
CREATE NONCLUSTERED INDEX [ix_nhat_ky_thao_tac_he_thong_ngoai_id] ON [dbo].[nhat_ky_thao_tac] ([he_thong_ngoai_id]);
CREATE NONCLUSTERED INDEX [ix_dot_dong_bo_he_thong_nguon_id] ON [dbo].[dot_dong_bo] ([he_thong_nguon_id]);
CREATE NONCLUSTERED INDEX [ix_dot_dong_bo_nguoi_yeu_cau_id] ON [dbo].[dot_dong_bo] ([nguoi_yeu_cau_id]);

GO
ALTER TABLE dbo.don_vi ADD dang_hoat_dong_nguon bit NULL;


GO
EXEC (N'CREATE TRIGGER [dbo].[tr_nhat_ky_thao_tac_immutable] ON [dbo].[nhat_ky_thao_tac] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[su_kien_id],CONVERT(varbinary(max),i.[loai_chu_the_thao_tac]),i.[nguoi_dung_id],i.[he_thong_ngoai_id],CONVERT(varbinary(max),i.[hanh_dong]),CONVERT(varbinary(max),i.[loai_doi_tuong]),CONVERT(varbinary(max),i.[doi_tuong_id]),i.[lan_sua_doi_doi_tuong],i.[ma_lien_ket_yeu_cau],CONVERT(varbinary(max),i.[ly_do]),CONVERT(varbinary(max),i.[dia_chi_ip]),i.[thoi_diem_tao] EXCEPT SELECT x.[id],x.[su_kien_id],CONVERT(varbinary(max),x.[loai_chu_the_thao_tac]),x.[nguoi_dung_id],x.[he_thong_ngoai_id],CONVERT(varbinary(max),x.[hanh_dong]),CONVERT(varbinary(max),x.[loai_doi_tuong]),CONVERT(varbinary(max),x.[doi_tuong_id]),x.[lan_sua_doi_doi_tuong],x.[ma_lien_ket_yeu_cau],CONVERT(varbinary(max),x.[ly_do]),CONVERT(varbinary(max),x.[dia_chi_ip]),x.[thoi_diem_tao]))) THROW 51051,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');

GO
EXEC (N'CREATE TRIGGER [dbo].[tr_chi_tiet_nhat_ky_thao_tac_immutable] ON [dbo].[chi_tiet_nhat_ky_thao_tac] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted x LEFT JOIN inserted i ON i.id=x.id WHERE (1=1) AND (i.id IS NULL OR EXISTS (SELECT i.[id],i.[nhat_ky_id],CONVERT(varbinary(max),i.[ten_truong]),CONVERT(varbinary(max),i.[kieu_gia_tri]),CONVERT(varbinary(max),i.[trang_thai_gia_tri_cu]),CONVERT(varbinary(max),i.[trang_thai_gia_tri_moi]),CONVERT(varbinary(max),i.[chuoi_cu]),CONVERT(varbinary(max),i.[chuoi_moi]),i.[so_cu],i.[so_moi],i.[dung_sai_cu],i.[dung_sai_moi],i.[ngay_cu],i.[ngay_moi],i.[thoi_diem_cu],i.[thoi_diem_moi],CONVERT(varbinary(max),i.[tham_chieu_cu]),CONVERT(varbinary(max),i.[tham_chieu_moi]) EXCEPT SELECT x.[id],x.[nhat_ky_id],CONVERT(varbinary(max),x.[ten_truong]),CONVERT(varbinary(max),x.[kieu_gia_tri]),CONVERT(varbinary(max),x.[trang_thai_gia_tri_cu]),CONVERT(varbinary(max),x.[trang_thai_gia_tri_moi]),CONVERT(varbinary(max),x.[chuoi_cu]),CONVERT(varbinary(max),x.[chuoi_moi]),x.[so_cu],x.[so_moi],x.[dung_sai_cu],x.[dung_sai_moi],x.[ngay_cu],x.[ngay_moi],x.[thoi_diem_cu],x.[thoi_diem_moi],CONVERT(varbinary(max),x.[tham_chieu_cu]),CONVERT(varbinary(max),x.[tham_chieu_moi])))) THROW 51051,N''Immutable business snapshot cannot be changed or deleted.'',1;
END;');
