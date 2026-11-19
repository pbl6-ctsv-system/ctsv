-- T01: HB periods, source aliases, draft KKHT rounds and audit in the shared CTSV history.
CREATE TABLE dbo.hb_hoc_ky (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_hb_hoc_ky PRIMARY KEY,
    nam_hoc nvarchar(9) NOT NULL,
    thu_tu_ky smallint NOT NULL,
    ngay_bat_dau date NOT NULL,
    ngay_ket_thuc date NOT NULL,
    trang_thai nvarchar(32) NOT NULL,
    CONSTRAINT uq_hb_hoc_ky_nam_hoc_thu_tu_ky UNIQUE (nam_hoc, thu_tu_ky),
    CONSTRAINT ck_hb_hoc_ky_thu_tu CHECK (thu_tu_ky > 0),
    CONSTRAINT ck_hb_hoc_ky_ngay CHECK (ngay_bat_dau <= ngay_ket_thuc)
);

CREATE TABLE dbo.hb_anh_xa_ky_nguon (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_hb_anh_xa_ky_nguon PRIMARY KEY,
    he_thong_nguon nvarchar(128) NOT NULL,
    loai_ma nvarchar(128) NOT NULL,
    ma_ky_nguon nvarchar(128) NOT NULL,
    ma_dot_nguon nvarchar(128) NULL,
    hoc_ky_id bigint NOT NULL,
    xac_nhan_boi bigint NOT NULL,
    xac_nhan_luc datetimeoffset(7) NOT NULL,
    -- SQL Server treats NULL context as a duplicate for the same non-NULL key prefix.
    CONSTRAINT uq_hb_anh_xa_ky_nguon_context UNIQUE NONCLUSTERED
        (he_thong_nguon, loai_ma, ma_ky_nguon, ma_dot_nguon),
    CONSTRAINT uq_hb_anh_xa_ky_nguon_id_hoc_ky UNIQUE (id, hoc_ky_id),
    CONSTRAINT fk_hb_anh_xa_ky_nguon_hoc_ky FOREIGN KEY (hoc_ky_id) REFERENCES dbo.hb_hoc_ky (id),
    CONSTRAINT fk_hb_anh_xa_ky_nguon_xac_nhan_boi FOREIGN KEY (xac_nhan_boi) REFERENCES dbo.nguoi_dung (id)
);

CREATE TABLE dbo.kkht_dot_xet (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_kkht_dot_xet PRIMARY KEY,
    hoc_ky_id bigint NOT NULL,
    anh_xa_ky_nguon_id bigint NOT NULL,
    so_dot int NOT NULL,
    ten_dot nvarchar(512) NOT NULL,
    mo_luc datetimeoffset(7) NULL,
    chot_luc datetimeoffset(7) NULL,
    moc_chot_hoc_phi datetimeoffset(7) NULL,
    quy_tac_id bigint NULL, -- T03 adds the FK when the referenced table exists.
    trang_thai nvarchar(32) NOT NULL,
    nguoi_tao_id bigint NOT NULL,
    phien_ban_ky_thuat rowversion NOT NULL,
    CONSTRAINT uq_kkht_dot_xet_hoc_ky_so_dot UNIQUE (hoc_ky_id, so_dot),
    CONSTRAINT ck_kkht_dot_xet_so_dot CHECK (so_dot > 0),
    CONSTRAINT fk_kkht_dot_xet_hoc_ky FOREIGN KEY (hoc_ky_id) REFERENCES dbo.hb_hoc_ky (id),
    CONSTRAINT fk_kkht_dot_xet_anh_xa_ky_nguon FOREIGN KEY (anh_xa_ky_nguon_id, hoc_ky_id)
        REFERENCES dbo.hb_anh_xa_ky_nguon (id, hoc_ky_id),
    CONSTRAINT fk_kkht_dot_xet_nguoi_tao FOREIGN KEY (nguoi_tao_id) REFERENCES dbo.nguoi_dung (id)
);

CREATE INDEX ix_kkht_dot_xet_anh_xa_ky_nguon ON dbo.kkht_dot_xet (anh_xa_ky_nguon_id, hoc_ky_id);

CREATE TABLE dbo.hb_nhat_ky (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_hb_nhat_ky PRIMARY KEY,
    nguoi_dung_id bigint NOT NULL,
    vai_tro_id bigint NOT NULL,
    loai_doi_tuong nvarchar(128) NOT NULL,
    doi_tuong_id bigint NOT NULL,
    pham_vi nvarchar(128) NOT NULL,
    hanh_dong nvarchar(128) NOT NULL,
    du_lieu_truoc nvarchar(max) NULL,
    du_lieu_sau nvarchar(max) NOT NULL,
    ly_do nvarchar(max) NULL,
    thoi_diem datetimeoffset(7) NOT NULL,
    correlation_id uniqueidentifier NOT NULL,
    CONSTRAINT fk_hb_nhat_ky_nguoi_dung FOREIGN KEY (nguoi_dung_id) REFERENCES dbo.nguoi_dung (id),
    CONSTRAINT fk_hb_nhat_ky_vai_tro FOREIGN KEY (vai_tro_id) REFERENCES dbo.vai_tro (id),
    CONSTRAINT ck_hb_nhat_ky_du_lieu_truoc_json CHECK (du_lieu_truoc IS NULL OR ISJSON(du_lieu_truoc) = 1),
    CONSTRAINT ck_hb_nhat_ky_du_lieu_sau_json CHECK (ISJSON(du_lieu_sau) = 1),
    CONSTRAINT ck_hb_nhat_ky_thoi_diem_utc CHECK (DATEPART(TZOFFSET, thoi_diem) = 0)
);

CREATE INDEX ix_hb_nhat_ky_correlation ON dbo.hb_nhat_ky (correlation_id);

-- Register the role; HOC_BONG assignment and user grants are managed outside migration.
INSERT dbo.vai_tro (ma, ten, dang_hoat_dong)
SELECT N'hoc_bong_chuyen_vien', N'Chuyên viên Học bổng', 1
WHERE NOT EXISTS (SELECT 1 FROM dbo.vai_tro WHERE ma = N'hoc_bong_chuyen_vien');
GO

CREATE TRIGGER dbo.tr_hb_anh_xa_ky_nguon_immutable ON dbo.hb_anh_xa_ky_nguon
AFTER UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM deleted d
        JOIN dbo.kkht_dot_xet r WITH (UPDLOCK, HOLDLOCK) ON r.anh_xa_ky_nguon_id = d.id
    )
        THROW 51901, N'Source semester aliases used by a KKHT round are immutable.', 1;
END;
GO

CREATE TRIGGER dbo.tr_hb_nhat_ky_append_only ON dbo.hb_nhat_ky
AFTER UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted)
        THROW 51902, N'Scholarship audit events are append-only.', 1;
END;
