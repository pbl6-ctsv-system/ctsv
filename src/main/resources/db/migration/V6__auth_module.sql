-- P03 Auth module on the existing CTSV Flyway history. Flyway owns the transaction.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
IF OBJECT_ID(N'dbo.dinh_danh_dang_nhap',N'U') IS NULL OR OBJECT_ID(N'dbo.dong_bo_trang_thai',N'U') IS NULL
    THROW 51233,N'CTSV baseline prerequisite missing',1;
CREATE TABLE [dbo].[TaiKhoan] (
    [ID] uniqueidentifier NOT NULL,
    [TenDangNhap] nvarchar(128) NOT NULL,
    [TenDangNhapChuanHoa] nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [MatKhauHash] varchar(512) NOT NULL,
    [TrangThai] bit NOT NULL,
    [PhienBanToken] bigint NOT NULL,
    [ThoiDiemTao] datetimeoffset(7) NOT NULL,
    [ThoiDiemCapNhat] datetimeoffset(7) NOT NULL,
    [PhienBan] rowversion NOT NULL,
    CONSTRAINT [PK_TaiKhoan] PRIMARY KEY CLUSTERED ([ID])
);
CREATE TABLE [dbo].[VaiTro] (
    [ID] bigint IDENTITY(1,1) NOT NULL,
    [MaVaiTro] nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [TenVaiTro] nvarchar(512) NULL,
    [TrangThai] bit NOT NULL,
    CONSTRAINT [PK_VaiTro] PRIMARY KEY CLUSTERED ([ID])
);
CREATE TABLE [dbo].[ChucNang] (
    [ID] bigint IDENTITY(1,1) NOT NULL,
    [MaChucNang] nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [TenChucNang] nvarchar(512) NULL,
    [TrangThai] bit NOT NULL,
    CONSTRAINT [PK_ChucNang] PRIMARY KEY CLUSTERED ([ID])
);
CREATE TABLE [dbo].[TaiKhoan_VaiTro] (
    [TaiKhoanID] uniqueidentifier NOT NULL,
    [VaiTroID] bigint NOT NULL,
    CONSTRAINT [PK_TaiKhoan_VaiTro] PRIMARY KEY CLUSTERED ([TaiKhoanID], [VaiTroID])
);
CREATE TABLE [dbo].[VaiTro_ChucNang] (
    [VaiTroID] bigint NOT NULL,
    [ChucNangID] bigint NOT NULL,
    CONSTRAINT [PK_VaiTro_ChucNang] PRIMARY KEY CLUSTERED ([VaiTroID], [ChucNangID])
);
CREATE TABLE [dbo].[RefreshToken] (
    [ID] uniqueidentifier NOT NULL,
    [TaiKhoanID] uniqueidentifier NOT NULL,
    [TokenHash] binary(32) NOT NULL,
    [PhienBanToken] bigint NOT NULL,
    [TaoLuc] datetimeoffset(7) NOT NULL,
    [HetHanLuc] datetimeoffset(7) NOT NULL,
    [SuDungLuc] datetimeoffset(7) NULL,
    [ThuHoiLuc] datetimeoffset(7) NULL,
    CONSTRAINT [PK_RefreshToken] PRIMARY KEY CLUSTERED ([ID])
);
CREATE TABLE [dbo].[NhatKyAuth] (
    [ID] bigint IDENTITY(1,1) NOT NULL,
    [SuKienID] uniqueidentifier NOT NULL,
    [MaTuongQuan] uniqueidentifier NOT NULL,
    [ThoiDiem] datetimeoffset(7) NOT NULL,
    [ActorID] uniqueidentifier NULL,
    [TacNhanHeThong] varchar(32) COLLATE Latin1_General_100_BIN2 NULL,
    [TaiKhoanDichID] uniqueidentifier NULL,
    [HanhDong] varchar(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [LyDo] nvarchar(4000) NULL,
    [VaiTroID] bigint NULL,
    [ChucNangID] bigint NULL,
    [TokenID] uniqueidentifier NULL,
    [PhienBanTokenTruoc] bigint NULL,
    [PhienBanTokenSau] bigint NULL,
    [TrangThaiTruoc] bit NULL,
    [TrangThaiSau] bit NULL,
    CONSTRAINT [PK_NhatKyAuth] PRIMARY KEY CLUSTERED ([ID])
);
CREATE TABLE [dbo].[LienKetHoSo] (
    [ID] bigint IDENTITY(1,1) NOT NULL,
    [TaiKhoanID] uniqueidentifier NOT NULL,
    [HeThongNguon] nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [LoaiHoSo] varchar(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [DinhDanhNguon] nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
    [TaoLuc] datetimeoffset(7) NOT NULL,
    [XacMinhLuc] datetimeoffset(7) NOT NULL,
    [CanCuXacMinh] nvarchar(4000) NOT NULL,
    [KetThucLuc] datetimeoffset(7) NULL,
    [NhatKyTaoID] bigint NOT NULL,
    [NhatKyKetThucID] bigint NULL,
    CONSTRAINT [PK_LienKetHoSo] PRIMARY KEY CLUSTERED ([ID])
);
ALTER TABLE [dbo].[TaiKhoan] ADD CONSTRAINT [DF_TaiKhoan_TrangThai] DEFAULT (1) FOR [TrangThai];
ALTER TABLE [dbo].[TaiKhoan] ADD CONSTRAINT [DF_TaiKhoan_PhienBanToken] DEFAULT (0) FOR [PhienBanToken];
ALTER TABLE [dbo].[TaiKhoan] ADD CONSTRAINT [DF_TaiKhoan_ThoiDiemTao] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [ThoiDiemTao];
ALTER TABLE [dbo].[TaiKhoan] ADD CONSTRAINT [DF_TaiKhoan_ThoiDiemCapNhat] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [ThoiDiemCapNhat];
ALTER TABLE [dbo].[VaiTro] ADD CONSTRAINT [DF_VaiTro_TrangThai] DEFAULT (1) FOR [TrangThai];
ALTER TABLE [dbo].[ChucNang] ADD CONSTRAINT [DF_ChucNang_TrangThai] DEFAULT (1) FOR [TrangThai];
ALTER TABLE [dbo].[RefreshToken] ADD CONSTRAINT [DF_RefreshToken_TaoLuc] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [TaoLuc];
ALTER TABLE [dbo].[NhatKyAuth] ADD CONSTRAINT [DF_NhatKyAuth_ThoiDiem] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [ThoiDiem];
ALTER TABLE [dbo].[LienKetHoSo] ADD CONSTRAINT [DF_LienKetHoSo_TaoLuc] DEFAULT (TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00')) FOR [TaoLuc];
ALTER TABLE [dbo].[TaiKhoan] ADD CONSTRAINT [UQ_TaiKhoan_TenChuanHoa] UNIQUE NONCLUSTERED ([TenDangNhapChuanHoa]);
ALTER TABLE [dbo].[VaiTro] ADD CONSTRAINT [UQ_VaiTro_Ma] UNIQUE NONCLUSTERED ([MaVaiTro]);
ALTER TABLE [dbo].[ChucNang] ADD CONSTRAINT [UQ_ChucNang_Ma] UNIQUE NONCLUSTERED ([MaChucNang]);
ALTER TABLE [dbo].[RefreshToken] ADD CONSTRAINT [UQ_Refresh_Hash] UNIQUE NONCLUSTERED ([TokenHash]);
ALTER TABLE [dbo].[NhatKyAuth] ADD CONSTRAINT [UQ_NhatKy_SuKien] UNIQUE NONCLUSTERED ([SuKienID]);
ALTER TABLE [dbo].[NhatKyAuth] ADD CONSTRAINT [UQ_NhatKy_ID_Dich] UNIQUE NONCLUSTERED ([ID], [TaiKhoanDichID]);
CREATE UNIQUE NONCLUSTERED INDEX [UX_Link_HienHanh] ON [dbo].[LienKetHoSo] ([HeThongNguon], [LoaiHoSo], [DinhDanhNguon]) WHERE [KetThucLuc] IS NULL;
ALTER TABLE [dbo].[TaiKhoan_VaiTro] WITH CHECK ADD CONSTRAINT [FK_TKVT_TaiKhoan] FOREIGN KEY ([TaiKhoanID]) REFERENCES [dbo].[TaiKhoan] ([ID]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[TaiKhoan_VaiTro] WITH CHECK ADD CONSTRAINT [FK_TKVT_VaiTro] FOREIGN KEY ([VaiTroID]) REFERENCES [dbo].[VaiTro] ([ID]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[VaiTro_ChucNang] WITH CHECK ADD CONSTRAINT [FK_VTCN_VaiTro] FOREIGN KEY ([VaiTroID]) REFERENCES [dbo].[VaiTro] ([ID]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[VaiTro_ChucNang] WITH CHECK ADD CONSTRAINT [FK_VTCN_ChucNang] FOREIGN KEY ([ChucNangID]) REFERENCES [dbo].[ChucNang] ([ID]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[RefreshToken] WITH CHECK ADD CONSTRAINT [FK_Refresh_TaiKhoan] FOREIGN KEY ([TaiKhoanID]) REFERENCES [dbo].[TaiKhoan] ([ID]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [FK_Audit_Actor] FOREIGN KEY ([ActorID]) REFERENCES [dbo].[TaiKhoan] ([ID]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [FK_Audit_Dich] FOREIGN KEY ([TaiKhoanDichID]) REFERENCES [dbo].[TaiKhoan] ([ID]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [FK_Audit_VaiTro] FOREIGN KEY ([VaiTroID]) REFERENCES [dbo].[VaiTro] ([ID]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [FK_Audit_ChucNang] FOREIGN KEY ([ChucNangID]) REFERENCES [dbo].[ChucNang] ([ID]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [FK_Link_TaiKhoan] FOREIGN KEY ([TaiKhoanID]) REFERENCES [dbo].[TaiKhoan] ([ID]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [FK_Link_AuditTao] FOREIGN KEY ([NhatKyTaoID], [TaiKhoanID]) REFERENCES [dbo].[NhatKyAuth] ([ID], [TaiKhoanDichID]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [FK_Link_AuditDong] FOREIGN KEY ([NhatKyKetThucID], [TaiKhoanID]) REFERENCES [dbo].[NhatKyAuth] ([ID], [TaiKhoanDichID]) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE [dbo].[TaiKhoan] WITH CHECK ADD CONSTRAINT [CK_TaiKhoan_TenDangNhap_TextKey] CHECK ([TenDangNhap] IS NULL OR (DATALENGTH([TenDangNhap])>0 AND LEFT([TenDangNhap],1) COLLATE Latin1_General_100_BIN2<>N' ' AND RIGHT([TenDangNhap],1) COLLATE Latin1_General_100_BIN2<>N' '));
ALTER TABLE [dbo].[TaiKhoan] WITH CHECK ADD CONSTRAINT [CK_TaiKhoan_TenDangNhapChuanHoa_TextKey] CHECK ([TenDangNhapChuanHoa] IS NULL OR (DATALENGTH([TenDangNhapChuanHoa])>0 AND LEFT([TenDangNhapChuanHoa],1) COLLATE Latin1_General_100_BIN2<>N' ' AND RIGHT([TenDangNhapChuanHoa],1) COLLATE Latin1_General_100_BIN2<>N' '));
ALTER TABLE [dbo].[TaiKhoan] WITH CHECK ADD CONSTRAINT [CK_TaiKhoan_ThoiDiemTao_UTC] CHECK ([ThoiDiemTao] IS NULL OR DATEPART(TZOFFSET,[ThoiDiemTao])=0);
ALTER TABLE [dbo].[TaiKhoan] WITH CHECK ADD CONSTRAINT [CK_TaiKhoan_ThoiDiemCapNhat_UTC] CHECK ([ThoiDiemCapNhat] IS NULL OR DATEPART(TZOFFSET,[ThoiDiemCapNhat])=0);
ALTER TABLE [dbo].[VaiTro] WITH CHECK ADD CONSTRAINT [CK_VaiTro_ID_Positive] CHECK ([ID]>0);
ALTER TABLE [dbo].[VaiTro] WITH CHECK ADD CONSTRAINT [CK_VaiTro_MaVaiTro_TextKey] CHECK ([MaVaiTro] IS NULL OR (DATALENGTH([MaVaiTro])>0 AND LEFT([MaVaiTro],1) COLLATE Latin1_General_100_BIN2<>N' ' AND RIGHT([MaVaiTro],1) COLLATE Latin1_General_100_BIN2<>N' '));
ALTER TABLE [dbo].[ChucNang] WITH CHECK ADD CONSTRAINT [CK_ChucNang_ID_Positive] CHECK ([ID]>0);
ALTER TABLE [dbo].[ChucNang] WITH CHECK ADD CONSTRAINT [CK_ChucNang_MaChucNang_TextKey] CHECK ([MaChucNang] IS NULL OR (DATALENGTH([MaChucNang])>0 AND LEFT([MaChucNang],1) COLLATE Latin1_General_100_BIN2<>N' ' AND RIGHT([MaChucNang],1) COLLATE Latin1_General_100_BIN2<>N' '));
ALTER TABLE [dbo].[RefreshToken] WITH CHECK ADD CONSTRAINT [CK_RefreshToken_TaoLuc_UTC] CHECK ([TaoLuc] IS NULL OR DATEPART(TZOFFSET,[TaoLuc])=0);
ALTER TABLE [dbo].[RefreshToken] WITH CHECK ADD CONSTRAINT [CK_RefreshToken_HetHanLuc_UTC] CHECK ([HetHanLuc] IS NULL OR DATEPART(TZOFFSET,[HetHanLuc])=0);
ALTER TABLE [dbo].[RefreshToken] WITH CHECK ADD CONSTRAINT [CK_RefreshToken_SuDungLuc_UTC] CHECK ([SuDungLuc] IS NULL OR DATEPART(TZOFFSET,[SuDungLuc])=0);
ALTER TABLE [dbo].[RefreshToken] WITH CHECK ADD CONSTRAINT [CK_RefreshToken_ThuHoiLuc_UTC] CHECK ([ThuHoiLuc] IS NULL OR DATEPART(TZOFFSET,[ThuHoiLuc])=0);
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [CK_NhatKyAuth_ID_Positive] CHECK ([ID]>0);
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [CK_NhatKyAuth_ThoiDiem_UTC] CHECK ([ThoiDiem] IS NULL OR DATEPART(TZOFFSET,[ThoiDiem])=0);
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [CK_NhatKyAuth_TacNhanHeThong_TextKey] CHECK ([TacNhanHeThong] IS NULL OR (DATALENGTH([TacNhanHeThong])>0 AND LEFT([TacNhanHeThong],1) COLLATE Latin1_General_100_BIN2<>N' ' AND RIGHT([TacNhanHeThong],1) COLLATE Latin1_General_100_BIN2<>N' '));
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [CK_NhatKyAuth_HanhDong_TextKey] CHECK ([HanhDong] IS NULL OR (DATALENGTH([HanhDong])>0 AND LEFT([HanhDong],1) COLLATE Latin1_General_100_BIN2<>N' ' AND RIGHT([HanhDong],1) COLLATE Latin1_General_100_BIN2<>N' '));
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [CK_LienKetHoSo_ID_Positive] CHECK ([ID]>0);
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [CK_LienKetHoSo_HeThongNguon_TextKey] CHECK ([HeThongNguon] IS NULL OR (DATALENGTH([HeThongNguon])>0 AND LEFT([HeThongNguon],1) COLLATE Latin1_General_100_BIN2<>N' ' AND RIGHT([HeThongNguon],1) COLLATE Latin1_General_100_BIN2<>N' '));
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [CK_LienKetHoSo_LoaiHoSo_TextKey] CHECK ([LoaiHoSo] IS NULL OR (DATALENGTH([LoaiHoSo])>0 AND LEFT([LoaiHoSo],1) COLLATE Latin1_General_100_BIN2<>N' ' AND RIGHT([LoaiHoSo],1) COLLATE Latin1_General_100_BIN2<>N' '));
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [CK_LienKetHoSo_DinhDanhNguon_TextKey] CHECK ([DinhDanhNguon] IS NULL OR (DATALENGTH([DinhDanhNguon])>0 AND LEFT([DinhDanhNguon],1) COLLATE Latin1_General_100_BIN2<>N' ' AND RIGHT([DinhDanhNguon],1) COLLATE Latin1_General_100_BIN2<>N' '));
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [CK_LienKetHoSo_TaoLuc_UTC] CHECK ([TaoLuc] IS NULL OR DATEPART(TZOFFSET,[TaoLuc])=0);
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [CK_LienKetHoSo_XacMinhLuc_UTC] CHECK ([XacMinhLuc] IS NULL OR DATEPART(TZOFFSET,[XacMinhLuc])=0);
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [CK_LienKetHoSo_KetThucLuc_UTC] CHECK ([KetThucLuc] IS NULL OR DATEPART(TZOFFSET,[KetThucLuc])=0);
ALTER TABLE [dbo].[TaiKhoan] WITH CHECK ADD CONSTRAINT [CK_TaiKhoan_PasswordPHC] CHECK (DATALENGTH([MatKhauHash])>0 AND LEFT([MatKhauHash],10) COLLATE Latin1_General_100_BIN2='$argon2id$');
ALTER TABLE [dbo].[TaiKhoan] WITH CHECK ADD CONSTRAINT [CK_TaiKhoan_TokenVersion] CHECK ([PhienBanToken]>=0);
ALTER TABLE [dbo].[TaiKhoan] WITH CHECK ADD CONSTRAINT [CK_TaiKhoan_UpdatedOrder] CHECK ([ThoiDiemCapNhat]>=[ThoiDiemTao]);
ALTER TABLE [dbo].[RefreshToken] WITH CHECK ADD CONSTRAINT [CK_RefreshToken_Version] CHECK ([PhienBanToken]>=0);
ALTER TABLE [dbo].[RefreshToken] WITH CHECK ADD CONSTRAINT [CK_RefreshToken_Expiry] CHECK ([TaoLuc]<[HetHanLuc] AND [HetHanLuc]<=DATEADD(day,7,[TaoLuc]));
ALTER TABLE [dbo].[RefreshToken] WITH CHECK ADD CONSTRAINT [CK_RefreshToken_Used] CHECK ([SuDungLuc] IS NULL OR ([SuDungLuc]>=[TaoLuc] AND [SuDungLuc]<[HetHanLuc]));
ALTER TABLE [dbo].[RefreshToken] WITH CHECK ADD CONSTRAINT [CK_RefreshToken_Revoked] CHECK ([ThuHoiLuc] IS NULL OR [ThuHoiLuc]>=[TaoLuc]);
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [CK_NhatKyAuth_Actor] CHECK (([ActorID] IS NOT NULL AND [TacNhanHeThong] IS NULL) OR ([ActorID] IS NULL AND [TacNhanHeThong] IS NOT NULL AND [TacNhanHeThong] IN ('bootstrap','migration','auth')));
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [CK_NhatKyAuth_Action] CHECK ([HanhDong] IN ('ACCOUNT_CREATED','BOOTSTRAP_ADMIN','ACCOUNT_LINKS_CHANGED','ACCOUNT_ROLE_GRANTED','ACCOUNT_ROLE_REVOKED','ACCOUNT_STATUS_CHANGED','PASSWORD_RESET','LOGIN_SUCCEEDED','REFRESH_ROTATED','TOKENS_REVOKED','RBAC_SEEDED'));
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [CK_NhatKyAuth_Target] CHECK ([HanhDong]='RBAC_SEEDED' OR [TaiKhoanDichID] IS NOT NULL);
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [CK_NhatKyAuth_Role] CHECK ([HanhDong] NOT IN ('ACCOUNT_ROLE_GRANTED','ACCOUNT_ROLE_REVOKED') OR [VaiTroID] IS NOT NULL);
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [CK_NhatKyAuth_Reason] CHECK ([HanhDong] NOT IN ('ACCOUNT_LINKS_CHANGED','ACCOUNT_STATUS_CHANGED','PASSWORD_RESET') OR ([LyDo] IS NOT NULL AND DATALENGTH([LyDo])>0));
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [CK_NhatKyAuth_VersionPair] CHECK (([PhienBanTokenTruoc] IS NULL AND [PhienBanTokenSau] IS NULL) OR ([PhienBanTokenTruoc] IS NOT NULL AND [PhienBanTokenSau] IS NOT NULL AND [PhienBanTokenTruoc]>=0 AND [PhienBanTokenSau]>=[PhienBanTokenTruoc]));
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [CK_NhatKyAuth_Revocation] CHECK ([HanhDong]<>'TOKENS_REVOKED' OR ([PhienBanTokenTruoc] IS NOT NULL AND [PhienBanTokenSau] IS NOT NULL AND CONVERT(decimal(20,0),[PhienBanTokenSau])=CONVERT(decimal(20,0),[PhienBanTokenTruoc])+1));
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [CK_NhatKyAuth_StatusPair] CHECK (([TrangThaiTruoc] IS NULL AND [TrangThaiSau] IS NULL) OR ([TrangThaiTruoc] IS NOT NULL AND [TrangThaiSau] IS NOT NULL));
ALTER TABLE [dbo].[NhatKyAuth] WITH CHECK ADD CONSTRAINT [CK_NhatKyAuth_StatusRequired] CHECK ([HanhDong]<>'ACCOUNT_STATUS_CHANGED' OR ([TrangThaiTruoc] IS NOT NULL AND [TrangThaiSau] IS NOT NULL));
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [CK_LienKetHoSo_ProfileType] CHECK ([LoaiHoSo] IN ('student','staff'));
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [CK_LienKetHoSo_Evidence] CHECK (DATALENGTH([CanCuXacMinh])>0);
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [CK_LienKetHoSo_Verified] CHECK ([XacMinhLuc]<=[TaoLuc]);
ALTER TABLE [dbo].[LienKetHoSo] WITH CHECK ADD CONSTRAINT [CK_LienKetHoSo_End] CHECK (([KetThucLuc] IS NULL AND [NhatKyKetThucID] IS NULL) OR ([KetThucLuc] IS NOT NULL AND [NhatKyKetThucID] IS NOT NULL AND [KetThucLuc]>=[TaoLuc]));
CREATE NONCLUSTERED INDEX [IX_TKVT_VaiTro] ON [dbo].[TaiKhoan_VaiTro] ([VaiTroID], [TaiKhoanID]);
CREATE NONCLUSTERED INDEX [IX_VTCN_ChucNang] ON [dbo].[VaiTro_ChucNang] ([ChucNangID], [VaiTroID]);
CREATE NONCLUSTERED INDEX [IX_Refresh_AccountVersion] ON [dbo].[RefreshToken] ([TaiKhoanID], [PhienBanToken]) INCLUDE ([HetHanLuc], [SuDungLuc], [ThuHoiLuc]);
CREATE NONCLUSTERED INDEX [IX_Refresh_Expiry] ON [dbo].[RefreshToken] ([HetHanLuc], [ID]);
CREATE NONCLUSTERED INDEX [IX_Link_Account] ON [dbo].[LienKetHoSo] ([TaiKhoanID], [ID]) INCLUDE ([HeThongNguon], [LoaiHoSo], [DinhDanhNguon], [KetThucLuc]);
CREATE NONCLUSTERED INDEX [IX_Link_History] ON [dbo].[LienKetHoSo] ([HeThongNguon], [LoaiHoSo], [DinhDanhNguon], [TaoLuc], [ID]) INCLUDE ([TaiKhoanID], [KetThucLuc]);
CREATE NONCLUSTERED INDEX [IX_Audit_TargetTime] ON [dbo].[NhatKyAuth] ([TaiKhoanDichID], [ThoiDiem], [ID]);
CREATE NONCLUSTERED INDEX [IX_Audit_Correlation] ON [dbo].[NhatKyAuth] ([MaTuongQuan], [ID]);
EXEC (N'CREATE TRIGGER [dbo].[TR_TaiKhoan_Guard] ON [dbo].[TaiKhoan] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.ID=d.ID WHERE i.ID IS NULL OR EXISTS (SELECT CONVERT(varbinary(max),i.[ID]),CONVERT(varbinary(max),i.[TenDangNhap]),CONVERT(varbinary(max),i.[TenDangNhapChuanHoa]),CONVERT(varbinary(max),i.[ThoiDiemTao]) EXCEPT SELECT CONVERT(varbinary(max),d.[ID]),CONVERT(varbinary(max),d.[TenDangNhap]),CONVERT(varbinary(max),d.[TenDangNhapChuanHoa]),CONVERT(varbinary(max),d.[ThoiDiemTao])) OR i.PhienBanToken<d.PhienBanToken OR ((CONVERT(varbinary(max),i.MatKhauHash)<>CONVERT(varbinary(max),d.MatKhauHash) OR (d.TrangThai=1 AND i.TrangThai=0)) AND i.PhienBanToken<=d.PhienBanToken)) THROW 51101,N''Auth account identity or security generation invariant.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[TR_RefreshToken_Guard] ON [dbo].[RefreshToken] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.ID=d.ID WHERE (i.ID IS NULL AND TODATETIMEOFFSET(SYSUTCDATETIME(),''+00:00'')<DATEADD(day,7,d.HetHanLuc)) OR (i.ID IS NOT NULL AND (EXISTS (SELECT CONVERT(varbinary(max),i.[ID]),CONVERT(varbinary(max),i.[TaiKhoanID]),CONVERT(varbinary(max),i.[TokenHash]),CONVERT(varbinary(max),i.[PhienBanToken]),CONVERT(varbinary(max),i.[TaoLuc]),CONVERT(varbinary(max),i.[HetHanLuc]) EXCEPT SELECT CONVERT(varbinary(max),d.[ID]),CONVERT(varbinary(max),d.[TaiKhoanID]),CONVERT(varbinary(max),d.[TokenHash]),CONVERT(varbinary(max),d.[PhienBanToken]),CONVERT(varbinary(max),d.[TaoLuc]),CONVERT(varbinary(max),d.[HetHanLuc])) OR (d.SuDungLuc IS NOT NULL AND (i.SuDungLuc IS NULL OR i.SuDungLuc<>d.SuDungLuc)) OR (d.ThuHoiLuc IS NOT NULL AND (i.ThuHoiLuc IS NULL OR i.ThuHoiLuc<>d.ThuHoiLuc))))) THROW 51102,N''Refresh ledger is immutable except first consume/revoke; cleanup only after expiry plus7days.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[TR_LienKetHoSo_Guard] ON [dbo].[LienKetHoSo] AFTER INSERT, UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.ID=d.ID WHERE i.ID IS NULL OR EXISTS (SELECT CONVERT(varbinary(max),i.[ID]),CONVERT(varbinary(max),i.[TaiKhoanID]),CONVERT(varbinary(max),i.[HeThongNguon]),CONVERT(varbinary(max),i.[LoaiHoSo]),CONVERT(varbinary(max),i.[DinhDanhNguon]),CONVERT(varbinary(max),i.[TaoLuc]),CONVERT(varbinary(max),i.[XacMinhLuc]),CONVERT(varbinary(max),i.[CanCuXacMinh]),CONVERT(varbinary(max),i.[NhatKyTaoID]) EXCEPT SELECT CONVERT(varbinary(max),d.[ID]),CONVERT(varbinary(max),d.[TaiKhoanID]),CONVERT(varbinary(max),d.[HeThongNguon]),CONVERT(varbinary(max),d.[LoaiHoSo]),CONVERT(varbinary(max),d.[DinhDanhNguon]),CONVERT(varbinary(max),d.[TaoLuc]),CONVERT(varbinary(max),d.[XacMinhLuc]),CONVERT(varbinary(max),d.[CanCuXacMinh]),CONVERT(varbinary(max),d.[NhatKyTaoID])) OR (d.KetThucLuc IS NOT NULL AND (EXISTS (SELECT CONVERT(varbinary(max),i.[KetThucLuc]),CONVERT(varbinary(max),i.[NhatKyKetThucID]) EXCEPT SELECT CONVERT(varbinary(max),d.[KetThucLuc]),CONVERT(varbinary(max),d.[NhatKyKetThucID]))))) THROW 51103,N''Profile history cannot be deleted, edited or reopened.'',1;
IF EXISTS (SELECT 1 FROM inserted i LEFT JOIN deleted d ON d.ID=i.ID WHERE d.ID IS NULL AND (i.KetThucLuc IS NOT NULL OR i.NhatKyKetThucID IS NOT NULL)) THROW 51103,N''New profile links must be current; historical rows cannot be inserted to bypass closing.'',1;
IF EXISTS (SELECT 1 FROM inserted i JOIN dbo.LienKetHoSo x WITH (UPDLOCK,HOLDLOCK) ON x.HeThongNguon=i.HeThongNguon AND x.LoaiHoSo=i.LoaiHoSo AND x.DinhDanhNguon=i.DinhDanhNguon AND x.ID<>i.ID WHERE (i.KetThucLuc IS NULL OR x.TaoLuc<i.KetThucLuc) AND (x.KetThucLuc IS NULL OR i.TaoLuc<x.KetThucLuc)) THROW 51103,N''Profile link intervals overlap.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[TR_NhatKyAuth_Immutable] ON [dbo].[NhatKyAuth] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted) THROW 51104,N''Auth audit is append-only.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[TR_VaiTro_Guard] ON [dbo].[VaiTro] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.ID=d.ID WHERE i.ID IS NULL OR EXISTS (SELECT CONVERT(varbinary(max),i.[ID]),CONVERT(varbinary(max),i.[MaVaiTro]) EXCEPT SELECT CONVERT(varbinary(max),d.[ID]),CONVERT(varbinary(max),d.[MaVaiTro]))) THROW 51105,N''Auth role/function ID and code are immutable; no delete.'',1;
END;');
EXEC (N'CREATE TRIGGER [dbo].[TR_ChucNang_Guard] ON [dbo].[ChucNang] AFTER UPDATE, DELETE AS
BEGIN
SET NOCOUNT ON;
IF EXISTS (SELECT 1 FROM deleted d LEFT JOIN inserted i ON i.ID=d.ID WHERE i.ID IS NULL OR EXISTS (SELECT CONVERT(varbinary(max),i.[ID]),CONVERT(varbinary(max),i.[MaChucNang]) EXCEPT SELECT CONVERT(varbinary(max),d.[ID]),CONVERT(varbinary(max),d.[MaChucNang]))) THROW 51105,N''Auth role/function ID and code are immutable; no delete.'',1;
END;');
INSERT dbo.VaiTro(MaVaiTro,TenVaiTro) VALUES(N'AUTH_ADMIN',N'Quản trị tài khoản'),(N'CTSV_USER',N'Người dùng CTSV');
INSERT dbo.ChucNang(MaChucNang,TenChucNang) VALUES
 (N'auth.accounts.manage',N'Quản lý tài khoản'),(N'auth.rbac.read',N'Đọc phân quyền'),(N'ctsv.access',N'Truy cập CTSV');
INSERT dbo.VaiTro_ChucNang(VaiTroID,ChucNangID)
SELECT r.ID,f.ID FROM dbo.VaiTro r CROSS JOIN dbo.ChucNang f
WHERE (r.MaVaiTro=N'AUTH_ADMIN' AND f.MaChucNang IN(N'auth.accounts.manage',N'auth.rbac.read'))
   OR (r.MaVaiTro=N'CTSV_USER' AND f.MaChucNang=N'ctsv.access');
INSERT dbo.NhatKyAuth(SuKienID,MaTuongQuan,TacNhanHeThong,HanhDong)
VALUES(NEWID(),NEWID(),'migration','RBAC_SEEDED');
