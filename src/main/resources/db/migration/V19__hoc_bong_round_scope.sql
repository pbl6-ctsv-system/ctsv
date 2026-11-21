-- T02.7. Append-only scope revisions; legacy rounds remain readable and are not rewritten.
CREATE TABLE dbo.kkht_pham_vi_dot (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_kkht_pham_vi_dot PRIMARY KEY,
    dot_xet_id bigint NOT NULL,
    phien_ban int NOT NULL,
    cach_chon nvarchar(16) NOT NULL,
    phien_nguon varchar(43) NOT NULL,
    tao_boi bigint NOT NULL,
    tao_luc datetimeoffset(7) NOT NULL,
    CONSTRAINT uq_kkht_pham_vi_dot_revision UNIQUE (dot_xet_id,phien_ban),
    CONSTRAINT uq_kkht_pham_vi_dot_parent UNIQUE (id,dot_xet_id),
    CONSTRAINT ck_kkht_pham_vi_dot_revision CHECK (phien_ban>0),
    CONSTRAINT ck_kkht_pham_vi_dot_mode CHECK (cach_chon COLLATE Latin1_General_100_BIN2 IN (N'all',N'groups')),
    CONSTRAINT ck_kkht_pham_vi_dot_revision_format CHECK (DATALENGTH(phien_nguon)=43 AND phien_nguon COLLATE Latin1_General_100_BIN2 NOT LIKE '%[^A-Za-z0-9_-]%'),
    CONSTRAINT ck_kkht_pham_vi_dot_utc CHECK (DATEPART(TZOFFSET,tao_luc)=0),
    CONSTRAINT fk_kkht_pham_vi_dot_round FOREIGN KEY (dot_xet_id) REFERENCES dbo.kkht_dot_xet(id),
    CONSTRAINT fk_kkht_pham_vi_dot_actor FOREIGN KEY (tao_boi) REFERENCES dbo.nguoi_dung(id)
);

CREATE TABLE dbo.kkht_nhom_pham_vi (
    id bigint IDENTITY(1,1) NOT NULL CONSTRAINT pk_kkht_nhom_pham_vi PRIMARY KEY,
    pham_vi_id bigint NOT NULL,
    khoa_hoc_id bigint NOT NULL,
    nganh_noi_bo_id bigint NOT NULL,
    ma_khoa_snapshot nvarchar(128) NULL,
    ma_nganh_snapshot nvarchar(128) NULL,
    ten_nganh_snapshot nvarchar(512) NULL,
    so_sv_nguon int NOT NULL,
    CONSTRAINT uq_kkht_nhom_pham_vi_pair UNIQUE (pham_vi_id,khoa_hoc_id,nganh_noi_bo_id),
    CONSTRAINT ck_kkht_nhom_pham_vi_count CHECK (so_sv_nguon>=0),
    CONSTRAINT fk_kkht_nhom_pham_vi_scope FOREIGN KEY (pham_vi_id) REFERENCES dbo.kkht_pham_vi_dot(id),
    CONSTRAINT fk_kkht_nhom_pham_vi_cohort FOREIGN KEY (khoa_hoc_id) REFERENCES dbo.khoa_hoc(id),
    CONSTRAINT fk_kkht_nhom_pham_vi_major FOREIGN KEY (nganh_noi_bo_id) REFERENCES dbo.nganh_noi_bo(id)
);
CREATE INDEX ix_kkht_nhom_pham_vi_group ON dbo.kkht_nhom_pham_vi(khoa_hoc_id,nganh_noi_bo_id,pham_vi_id);

ALTER TABLE dbo.kkht_dot_xet ADD pham_vi_hien_tai_id bigint NULL,
    ma_lenh_khoi_tao uniqueidentifier NULL, bam_lenh_khoi_tao binary(32) NULL;
GO
ALTER TABLE dbo.kkht_dot_xet ADD
    CONSTRAINT ck_kkht_dot_xet_creation_pair CHECK (
        (ma_lenh_khoi_tao IS NULL AND bam_lenh_khoi_tao IS NULL) OR
        (ma_lenh_khoi_tao IS NOT NULL AND bam_lenh_khoi_tao IS NOT NULL)),
    CONSTRAINT fk_kkht_dot_xet_current_scope FOREIGN KEY (pham_vi_hien_tai_id,id)
        REFERENCES dbo.kkht_pham_vi_dot(id,dot_xet_id);
CREATE UNIQUE INDEX uq_kkht_dot_xet_actor_command ON dbo.kkht_dot_xet(nguoi_tao_id,ma_lenh_khoi_tao)
    WHERE ma_lenh_khoi_tao IS NOT NULL;
GO
CREATE TRIGGER dbo.tr_kkht_pham_vi_dot_append_only ON dbo.kkht_pham_vi_dot
AFTER UPDATE,DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM deleted) THROW 51920,N'KKHT scope revisions are append-only.',1;
END;
GO
CREATE TRIGGER dbo.tr_kkht_nhom_pham_vi_append_only ON dbo.kkht_nhom_pham_vi
AFTER UPDATE,DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS(SELECT 1 FROM deleted) THROW 51921,N'KKHT scope groups are append-only.',1;
END;
