-- T03.1: owner-approved MaHPTT null semantics; preserve existing keys and data.
ALTER TABLE dbo.diem_ky_hoc ALTER COLUMN hoc_phan_tuong_duong_id bigint NULL;
ALTER TABLE dbo.diem_mien ALTER COLUMN hoc_phan_tuong_duong_id bigint NULL;
ALTER TABLE dbo.khung_chuong_trinh ALTER COLUMN hoc_phan_duoc_thay_the_id bigint NULL;
GO

CREATE TABLE dbo.hb_to_hop_dao_tao (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_hb_to_hop_dao_tao PRIMARY KEY,
    chuong_trinh_dao_tao_id bigint NOT NULL,
    khoa_hoc_id bigint NOT NULL,
    nganh_noi_bo_id bigint NOT NULL,
    ma_ctdt nvarchar(128) NOT NULL,
    ten_ctdt nvarchar(512) NULL,
    khoa_tuyen_sinh nvarchar(128) NOT NULL,
    ma_nganh_tong_quat nvarchar(128) NOT NULL,
    ma_khung_nguon nvarchar(512) NOT NULL,
    so_ky_chuan smallint NOT NULL,
    loai_ctdt nvarchar(2) NOT NULL,
    hieu_luc_tu date NOT NULL,
    hieu_luc_den date NULL,
    phien_ban_ky_thuat rowversion NOT NULL,
    CONSTRAINT fk_hb_to_hop_chuong_trinh FOREIGN KEY (chuong_trinh_dao_tao_id) REFERENCES dbo.chuong_trinh_dao_tao(id),
    CONSTRAINT fk_hb_to_hop_khoa_hoc FOREIGN KEY (khoa_hoc_id) REFERENCES dbo.khoa_hoc(id),
    CONSTRAINT fk_hb_to_hop_nganh FOREIGN KEY (nganh_noi_bo_id) REFERENCES dbo.nganh_noi_bo(id),
    CONSTRAINT ck_hb_to_hop_so_ky CHECK (so_ky_chuan > 0),
    CONSTRAINT ck_hb_to_hop_loai CHECK (loai_ctdt COLLATE Latin1_General_100_BIN2 IN (N'CN', N'KS')),
    CONSTRAINT ck_hb_to_hop_ngay CHECK (hieu_luc_den IS NULL OR hieu_luc_den >= hieu_luc_tu),
    CONSTRAINT ck_hb_to_hop_ma CHECK (LEN(LTRIM(RTRIM(ma_ctdt))) > 0 AND LEN(LTRIM(RTRIM(khoa_tuyen_sinh))) > 0
        AND LEN(LTRIM(RTRIM(ma_nganh_tong_quat))) > 0 AND LEN(LTRIM(RTRIM(ma_khung_nguon))) > 0),
    CONSTRAINT uq_hb_to_hop_key UNIQUE (chuong_trinh_dao_tao_id, khoa_hoc_id, nganh_noi_bo_id, hieu_luc_tu)
);
GO

-- Also protect direct SQL writes and concurrent insert/update of effective ranges.
CREATE TRIGGER dbo.tr_hb_to_hop_no_overlap ON dbo.hb_to_hop_dao_tao
AFTER INSERT, UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted i
        JOIN dbo.hb_to_hop_dao_tao t WITH (UPDLOCK, HOLDLOCK, INDEX(uq_hb_to_hop_key))
          ON t.chuong_trinh_dao_tao_id = i.chuong_trinh_dao_tao_id
         AND t.khoa_hoc_id = i.khoa_hoc_id AND t.nganh_noi_bo_id = i.nganh_noi_bo_id
         AND t.id <> i.id
         AND t.hieu_luc_tu <= ISNULL(i.hieu_luc_den, CONVERT(date, '99991231',112))
         AND ISNULL(t.hieu_luc_den, CONVERT(date, '99991231',112)) >= i.hieu_luc_tu
    ) THROW 51940, N'COMBINATION_OVERLAP', 1;
END;
