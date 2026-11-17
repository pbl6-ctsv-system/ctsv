CREATE TABLE dbo.luot_tai_tep (
    id uniqueidentifier NOT NULL CONSTRAINT pk_luot_tai_tep PRIMARY KEY,
    nguoi_dung_id bigint NOT NULL REFERENCES dbo.nguoi_dung(id),
    ten_cloud nvarchar(128) NOT NULL,
    ma_cong_khai varchar(128) NOT NULL CONSTRAINT uq_luot_tai_tep_public UNIQUE,
    loai_tep varchar(16) NOT NULL CHECK (loai_tep IN ('evidence','document')),
    kich_thuoc bigint NOT NULL,
    sha256 varchar(64) COLLATE Latin1_General_100_BIN2 NOT NULL
        CHECK (DATALENGTH(sha256)=64 AND sha256 NOT LIKE '%[^0-9a-f]%'),
    trang_thai varchar(16) NOT NULL CHECK (trang_thai IN ('started','unknown','committed','orphan','not_found')),
    tep_id bigint NULL REFERENCES dbo.tep_dinh_kem(id),
    ma_tai_nguyen nvarchar(128) NULL,
    thoi_diem_tao datetimeoffset(7) NOT NULL DEFAULT TODATETIMEOFFSET(SYSUTCDATETIME(), '+00:00'),
    doi_soat_sau datetimeoffset(7) NOT NULL,
    CONSTRAINT ck_luot_tai_tep_size CHECK (kich_thuoc BETWEEN 1 AND
        CASE loai_tep WHEN 'evidence' THEN 10000000 ELSE 20000000 END),
    CONSTRAINT ck_luot_tai_tep_commit CHECK (
        (trang_thai='committed' AND tep_id IS NOT NULL AND ma_tai_nguyen IS NOT NULL)
        OR (trang_thai<>'committed' AND tep_id IS NULL)),
    CONSTRAINT ck_luot_tai_tep_orphan CHECK (trang_thai<>'orphan' OR ma_tai_nguyen IS NOT NULL)
);
CREATE UNIQUE INDEX ux_luot_tai_tep_file ON dbo.luot_tai_tep(tep_id) WHERE tep_id IS NOT NULL;
CREATE INDEX ix_luot_tai_tep_reconcile ON dbo.luot_tai_tep(ten_cloud, trang_thai, doi_soat_sau);
