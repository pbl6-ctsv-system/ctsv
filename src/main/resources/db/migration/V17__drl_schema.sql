-- DRL uses the Foundation identities and the shared signed-document version.
CREATE TABLE dbo.RLBieuMau (
    ID bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_RLBieuMau PRIMARY KEY,
    MaBieuMau nvarchar(64) NOT NULL,
    SoPhienBan int NOT NULL,
    TenBieuMau nvarchar(512) NOT NULL,
    DiemToiDa decimal(5,2) NOT NULL,
    HuongDanChung nvarchar(max) NULL,
    CONSTRAINT UQ_RLBieuMau_PhienBan UNIQUE (MaBieuMau, SoPhienBan),
    CONSTRAINT CK_RLBieuMau_PhienBan CHECK (SoPhienBan >= 1),
    CONSTRAINT CK_RLBieuMau_Diem CHECK (DiemToiDa >= 0)
);

CREATE TABLE dbo.RLNhomTieuChi (
    ID bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_RLNhomTieuChi PRIMARY KEY,
    BieuMauID bigint NOT NULL REFERENCES dbo.RLBieuMau(ID),
    TenNhom nvarchar(512) NOT NULL,
    ThuTu int NOT NULL,
    DiemToiDa decimal(5,2) NOT NULL,
    CONSTRAINT UQ_RLNhomTieuChi_ThuTu UNIQUE (BieuMauID, ThuTu),
    CONSTRAINT CK_RLNhomTieuChi_Diem CHECK (DiemToiDa >= 0)
);

CREATE TABLE dbo.RLBieuMauCT (
    ID bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_RLBieuMauCT PRIMARY KEY,
    NhomTieuChiID bigint NOT NULL REFERENCES dbo.RLNhomTieuChi(ID),
    MaNoiDung nvarchar(64) NOT NULL,
    LoaiDong tinyint NOT NULL,
    TieuChiChaID bigint NULL REFERENCES dbo.RLBieuMauCT(ID),
    NoiDung nvarchar(max) NOT NULL,
    ThuTu int NOT NULL,
    MucLuc nvarchar(32) NULL,
    DiemToiDa decimal(5,2) NULL,
    CONSTRAINT UQ_RLBieuMauCT_Ma UNIQUE (NhomTieuChiID, MaNoiDung),
    CONSTRAINT UQ_RLBieuMauCT_ThuTu UNIQUE (NhomTieuChiID, ThuTu),
    CONSTRAINT CK_RLBieuMauCT_Loai CHECK (
        (LoaiDong = 0 AND TieuChiChaID IS NULL AND DiemToiDa IS NOT NULL AND DiemToiDa >= 0)
        OR (LoaiDong = 1 AND TieuChiChaID IS NOT NULL AND DiemToiDa IS NULL)
    )
);
CREATE INDEX IX_RLBieuMauCT_Cha ON dbo.RLBieuMauCT(TieuChiChaID) WHERE TieuChiChaID IS NOT NULL;

CREATE TABLE dbo.RLXepLoai (
    ID bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_RLXepLoai PRIMARY KEY,
    BieuMauID bigint NOT NULL REFERENCES dbo.RLBieuMau(ID),
    DiemToiThieu decimal(5,2) NOT NULL,
    TenXepLoai nvarchar(64) NOT NULL,
    CONSTRAINT UQ_RLXepLoai_Nguong UNIQUE (BieuMauID, DiemToiThieu),
    CONSTRAINT CK_RLXepLoai_Nguong CHECK (DiemToiThieu >= 0)
);

CREATE TABLE dbo.RLDot (
    ID bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_RLDot PRIMARY KEY,
    MaDot nvarchar(64) NOT NULL CONSTRAINT UQ_RLDot_Ma UNIQUE,
    TenDot nvarchar(512) NOT NULL,
    HocKyID bigint NOT NULL REFERENCES dbo.hoc_ky(id),
    BieuMauID bigint NOT NULL REFERENCES dbo.RLBieuMau(ID),
    BatDauSV datetimeoffset(7) NOT NULL,
    KetThucSV datetimeoffset(7) NOT NULL,
    BatDauLop datetimeoffset(7) NOT NULL,
    KetThucLop datetimeoffset(7) NOT NULL,
    BatDauKhoa datetimeoffset(7) NOT NULL,
    KetThucKhoa datetimeoffset(7) NOT NULL,
    TrangThai tinyint NOT NULL CONSTRAINT DF_RLDot_TrangThai DEFAULT 0,
    CONSTRAINT CK_RLDot_TrangThai CHECK (TrangThai IN (0,1,2)),
    CONSTRAINT CK_RLDot_Lich CHECK (BatDauSV < KetThucSV AND KetThucSV <= BatDauLop
        AND BatDauLop < KetThucLop AND BatDauKhoa < KetThucKhoa)
);
CREATE UNIQUE INDEX UQ_RLDot_ChuaCongBo ON dbo.RLDot(HocKyID) WHERE TrangThai < 2;
CREATE INDEX IX_RLDot_BieuMau ON dbo.RLDot(BieuMauID);

CREATE TABLE dbo.RLKhoaDot (
    ID bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_RLKhoaDot PRIMARY KEY,
    DotID bigint NOT NULL REFERENCES dbo.RLDot(ID),
    KhoaID bigint NOT NULL REFERENCES dbo.don_vi(id),
    DaiDienKhoaID bigint NULL REFERENCES dbo.can_bo(id),
    NgayGuiCTSV datetimeoffset(7) NULL,
    CONSTRAINT UQ_RLKhoaDot_Khoa UNIQUE (DotID, KhoaID)
);

CREATE TABLE dbo.RLLopDot (
    ID bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_RLLopDot PRIMARY KEY,
    KhoaDotID bigint NOT NULL REFERENCES dbo.RLKhoaDot(ID),
    LopID bigint NOT NULL REFERENCES dbo.lop_sinh_hoat(id),
    LopTruongID bigint NULL REFERENCES dbo.sinh_vien(id),
    GVCNID bigint NULL REFERENCES dbo.can_bo(id),
    NgayGuiKhoa datetimeoffset(7) NULL,
    NguoiXNCTSVID bigint NULL REFERENCES dbo.nguoi_dung(id),
    NgayXNCTSV datetimeoffset(7) NULL,
    CONSTRAINT UQ_RLLopDot_Lop UNIQUE (KhoaDotID, LopID),
    CONSTRAINT CK_RLLopDot_XacNhan CHECK (
        (NguoiXNCTSVID IS NULL AND NgayXNCTSV IS NULL)
        OR (NguoiXNCTSVID IS NOT NULL AND NgayXNCTSV IS NOT NULL)
    )
);
CREATE INDEX IX_RLLopDot_Lop ON dbo.RLLopDot(LopID, KhoaDotID);

CREATE TABLE dbo.RLPhieu (
    ID bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_RLPhieu PRIMARY KEY,
    LopDotID bigint NOT NULL REFERENCES dbo.RLLopDot(ID),
    SinhVienID bigint NOT NULL REFERENCES dbo.sinh_vien(id),
    XacNhanSV bit NOT NULL CONSTRAINT DF_RLPhieu_XacNhanSV DEFAULT 0,
    NgayXNSV datetimeoffset(7) NULL,
    XacNhanLop bit NOT NULL CONSTRAINT DF_RLPhieu_XacNhanLop DEFAULT 0,
    NgayXNLop datetimeoffset(7) NULL,
    ThamDuHop tinyint NULL,
    KhongDanhGia bit NOT NULL CONSTRAINT DF_RLPhieu_KhongDanhGia DEFAULT 0,
    CONSTRAINT UQ_RLPhieu_SV UNIQUE (LopDotID, SinhVienID),
    CONSTRAINT CK_RLPhieu_XacNhanSV CHECK (
        (XacNhanSV = 0 AND NgayXNSV IS NULL) OR (XacNhanSV = 1 AND NgayXNSV IS NOT NULL)),
    CONSTRAINT CK_RLPhieu_XacNhanLop CHECK (
        (XacNhanLop = 0 AND NgayXNLop IS NULL) OR (XacNhanLop = 1 AND NgayXNLop IS NOT NULL)),
    CONSTRAINT CK_RLPhieu_DiemDanh CHECK (ThamDuHop IN (0,1,2))
);
CREATE INDEX IX_RLPhieu_SinhVien ON dbo.RLPhieu(SinhVienID, LopDotID);

CREATE TABLE dbo.RLPhieuDiemCT (
    ID bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_RLPhieuDiemCT PRIMARY KEY,
    PhieuID bigint NOT NULL REFERENCES dbo.RLPhieu(ID),
    BieuMauCTID bigint NOT NULL REFERENCES dbo.RLBieuMauCT(ID),
    DiemCN decimal(5,2) NULL,
    DiemLop decimal(5,2) NULL,
    CONSTRAINT UQ_RLPhieuDiemCT_Phieu_TieuChi UNIQUE (PhieuID, BieuMauCTID),
    CONSTRAINT CK_RLPhieuDiemCT_Diem CHECK (DiemCN >= 0 AND DiemLop >= 0)
);

CREATE TABLE dbo.RLHopLop (
    ID bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_RLHopLop PRIMARY KEY,
    LopDotID bigint NOT NULL REFERENCES dbo.RLLopDot(ID),
    BatDauHop datetimeoffset(7) NULL,
    KetThucHop datetimeoffset(7) NULL,
    DiaDiem nvarchar(512) NULL,
    NoiDung nvarchar(max) NULL,
    KienNghi nvarchar(max) NULL,
    CONSTRAINT UQ_RLHopLop_LopDot UNIQUE (LopDotID),
    CONSTRAINT CK_RLHopLop_ThoiGian CHECK (KetThucHop >= BatDauHop)
);

CREATE TABLE dbo.RLVanBanKy (
    ID bigint IDENTITY(1,1) NOT NULL CONSTRAINT PK_RLVanBanKy PRIMARY KEY,
    LopDotID bigint NOT NULL REFERENCES dbo.RLLopDot(ID),
    LoaiVanBan tinyint NOT NULL,
    PhienBanVanBanID bigint NOT NULL REFERENCES dbo.phien_ban_van_ban(id),
    CONSTRAINT CK_RLVanBanKy_Loai CHECK (LoaiVanBan IN (1,2)),
    CONSTRAINT UQ_RLVanBanKy_Loai UNIQUE (LopDotID, LoaiVanBan),
    CONSTRAINT UQ_RLVanBanKy_PhienBan UNIQUE (PhienBanVanBanID)
);
GO

CREATE VIEW dbo.RLKetQuaSV AS
SELECT p.ID AS PhieuID, p.SinhVienID, p.LopDotID, d.ID AS DotID, d.HocKyID,
       diem.TongDiemCN, diem.TongDiemLop, xl.TenXepLoai AS XepLoai,
       CONVERT(bit, CASE WHEN d.TrangThai = 2 THEN 1 ELSE 0 END) AS DaCongBo
FROM dbo.RLPhieu AS p
JOIN dbo.RLLopDot AS l ON l.ID = p.LopDotID
JOIN dbo.RLKhoaDot AS k ON k.ID = l.KhoaDotID
JOIN dbo.RLDot AS d ON d.ID = k.DotID
OUTER APPLY (
    SELECT SUM(ct.DiemCN) AS CaNhan, SUM(ct.DiemLop) AS Lop
    FROM dbo.RLPhieuDiemCT AS ct WHERE ct.PhieuID = p.ID
) AS tong
CROSS APPLY (VALUES (
    CONVERT(decimal(5,2), CASE WHEN p.XacNhanSV = 1 THEN COALESCE(tong.CaNhan, 0)
                              WHEN SYSDATETIMEOFFSET() >= d.KetThucSV THEN 0 END),
    CONVERT(decimal(5,2), CASE WHEN p.XacNhanLop = 1 THEN COALESCE(tong.Lop, 0) END)
)) AS diem(TongDiemCN, TongDiemLop)
OUTER APPLY (
    SELECT TOP (1) x.TenXepLoai FROM dbo.RLXepLoai AS x
    WHERE x.BieuMauID = d.BieuMauID AND x.DiemToiThieu <= diem.TongDiemLop
    ORDER BY x.DiemToiThieu DESC
) AS xl;
GO

-- The round pins a complete rating scale. Published results are calculated from
-- that version, so later template edits require a new version.
CREATE TRIGGER dbo.TR_RLDot_PhienBan ON dbo.RLDot AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM deleted AS old
        LEFT JOIN inserted AS i ON i.ID = old.ID
        WHERE old.TrangThai = 2 OR (i.ID IS NOT NULL AND (i.TrangThai < old.TrangThai OR i.TrangThai > old.TrangThai + 1))
           OR i.HocKyID <> old.HocKyID OR i.BieuMauID <> old.BieuMauID
    )
        THROW 51701, 'DRL: the round identity and published status are fixed.', 1;

    IF EXISTS (
        SELECT 1 FROM inserted AS i
        JOIN dbo.RLBieuMau AS m ON m.ID = i.BieuMauID
        WHERE NOT EXISTS (SELECT 1 FROM dbo.RLXepLoai AS x WITH (HOLDLOCK)
                          WHERE x.BieuMauID = m.ID AND x.DiemToiThieu = 0)
           OR EXISTS (SELECT 1 FROM dbo.RLXepLoai AS x WITH (HOLDLOCK)
                      WHERE x.BieuMauID = m.ID AND x.DiemToiThieu > m.DiemToiDa)
    )
        THROW 51702, 'DRL: the rating scale must cover zero and fit the form maximum.', 1;

    IF EXISTS (
        SELECT 1 FROM inserted AS i
        WHERE i.TrangThai = 2 AND (
            NOT EXISTS (SELECT 1 FROM dbo.RLKhoaDot AS k JOIN dbo.RLLopDot AS l ON l.KhoaDotID = k.ID
                        WHERE k.DotID = i.ID)
            OR EXISTS (SELECT 1 FROM dbo.RLKhoaDot AS k LEFT JOIN dbo.RLLopDot AS l ON l.KhoaDotID = k.ID
                       WHERE k.DotID = i.ID AND (k.NgayGuiCTSV IS NULL OR l.NgayGuiKhoa IS NULL OR l.NgayXNCTSV IS NULL))
        )
    )
        THROW 51703, 'DRL: publication requires CTSV confirmation for every class.', 1;
END;
GO

CREATE TRIGGER dbo.TR_RLBieuMau_DaGhim ON dbo.RLBieuMau AFTER UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM deleted AS m JOIN dbo.RLDot AS d WITH (HOLDLOCK) ON d.BieuMauID = m.ID)
        THROW 51704, 'DRL: the form version is pinned to a round.', 1;
END;
GO

CREATE TRIGGER dbo.TR_RLNhomTieuChi_DaGhim ON dbo.RLNhomTieuChi AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM (SELECT BieuMauID FROM inserted UNION SELECT BieuMauID FROM deleted) AS m
               JOIN dbo.RLDot AS d WITH (HOLDLOCK) ON d.BieuMauID = m.BieuMauID)
        THROW 51704, 'DRL: the criterion group belongs to a pinned version.', 1;
END;
GO

CREATE TRIGGER dbo.TR_RLBieuMauCT_Nhom ON dbo.RLBieuMauCT AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM (SELECT NhomTieuChiID FROM inserted UNION SELECT NhomTieuChiID FROM deleted) AS c
               JOIN dbo.RLNhomTieuChi AS n ON n.ID = c.NhomTieuChiID
               JOIN dbo.RLDot AS d WITH (HOLDLOCK) ON d.BieuMauID = n.BieuMauID)
        THROW 51704, 'DRL: the criterion belongs to a pinned version.', 1;

    -- Check edits to either end of the self-reference, including multi-row edits.
    IF EXISTS (SELECT 1 FROM dbo.RLBieuMauCT AS child WITH (HOLDLOCK)
               JOIN dbo.RLBieuMauCT AS parent WITH (HOLDLOCK) ON parent.ID = child.TieuChiChaID
               WHERE (child.ID IN (SELECT ID FROM inserted) OR parent.ID IN (SELECT ID FROM inserted))
                 AND (parent.LoaiDong <> 0 OR parent.NhomTieuChiID <> child.NhomTieuChiID))
        THROW 51705, 'DRL: a guidance row requires a scoring criterion in the same group.', 1;
END;
GO

CREATE TRIGGER dbo.TR_RLXepLoai_DaGhim ON dbo.RLXepLoai AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM (SELECT BieuMauID FROM inserted UNION SELECT BieuMauID FROM deleted) AS m
               JOIN dbo.RLDot AS d WITH (HOLDLOCK) ON d.BieuMauID = m.BieuMauID)
        THROW 51704, 'DRL: the rating scale belongs to a pinned version.', 1;
END;
GO

CREATE TRIGGER dbo.TR_RLKhoaDot_PhamVi ON dbo.RLKhoaDot AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM (SELECT DotID FROM inserted UNION SELECT DotID FROM deleted) AS k
               JOIN dbo.RLDot AS d WITH (HOLDLOCK) ON d.ID = k.DotID WHERE d.TrangThai = 2)
        THROW 51709, 'DRL: the published roster is fixed.', 1;

    IF EXISTS (SELECT 1 FROM inserted AS i JOIN deleted AS old ON old.ID = i.ID
               WHERE (i.DotID <> old.DotID OR i.KhoaID <> old.KhoaID)
                 AND EXISTS (SELECT 1 FROM dbo.RLLopDot AS l WITH (HOLDLOCK) WHERE l.KhoaDotID = i.ID))
        THROW 51706, 'DRL: cannot move a faculty with assigned classes.', 1;
END;
GO

CREATE TRIGGER dbo.TR_RLLopDot_PhamVi ON dbo.RLLopDot AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM (SELECT KhoaDotID FROM inserted UNION SELECT KhoaDotID FROM deleted) AS l
               JOIN dbo.RLKhoaDot AS k ON k.ID = l.KhoaDotID
               JOIN dbo.RLDot AS d WITH (HOLDLOCK) ON d.ID = k.DotID WHERE d.TrangThai = 2)
        THROW 51709, 'DRL: the published roster is fixed.', 1;

    IF EXISTS (SELECT 1 FROM inserted AS i JOIN deleted AS old ON old.ID = i.ID
               WHERE (i.KhoaDotID <> old.KhoaDotID OR i.LopID <> old.LopID)
                 AND EXISTS (SELECT 1 FROM dbo.RLPhieu AS p WITH (HOLDLOCK) WHERE p.LopDotID = i.ID))
        THROW 51707, 'DRL: cannot move a class with assigned students.', 1;

    IF EXISTS (SELECT 1 FROM inserted AS i
               JOIN dbo.RLKhoaDot AS k ON k.ID = i.KhoaDotID
               JOIN dbo.RLLopDot AS other WITH (UPDLOCK, HOLDLOCK)
                 ON other.LopID = i.LopID AND other.ID <> i.ID
               JOIN dbo.RLKhoaDot AS k2 ON k2.ID = other.KhoaDotID AND k2.DotID = k.DotID)
        THROW 51708, 'DRL: a class may appear only once per round.', 1;
END;
GO

CREATE TRIGGER dbo.TR_RLPhieu_PhamVi ON dbo.RLPhieu AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM (SELECT LopDotID FROM inserted UNION SELECT LopDotID FROM deleted) AS affected
               JOIN dbo.RLLopDot AS l ON l.ID = affected.LopDotID
               JOIN dbo.RLKhoaDot AS k ON k.ID = l.KhoaDotID
               JOIN dbo.RLDot AS d WITH (HOLDLOCK) ON d.ID = k.DotID WHERE d.TrangThai = 2)
        THROW 51709, 'DRL: the published roster is fixed.', 1;

    IF EXISTS (SELECT 1 FROM inserted AS i JOIN deleted AS old ON old.ID = i.ID
               WHERE i.SinhVienID <> old.SinhVienID OR i.LopDotID <> old.LopDotID
                  OR i.XacNhanSV < old.XacNhanSV OR i.XacNhanLop < old.XacNhanLop)
        THROW 51710, 'DRL: cannot reassign a ballot or clear its confirmation.', 1;

    IF NOT (UPDATE(SinhVienID) OR UPDATE(LopDotID)) RETURN;
    -- Rounds in a semester are sequential: a previous ballot in another round
    -- already contributes that student's official result. Range locks protect concurrent selections.
    IF EXISTS (SELECT 1 FROM inserted AS i
               JOIN dbo.RLLopDot AS l ON l.ID = i.LopDotID
               JOIN dbo.RLKhoaDot AS k ON k.ID = l.KhoaDotID
               JOIN dbo.RLDot AS d ON d.ID = k.DotID
               JOIN dbo.RLPhieu AS other WITH (UPDLOCK, HOLDLOCK)
                 ON other.SinhVienID = i.SinhVienID AND other.ID <> i.ID
               JOIN dbo.RLLopDot AS l2 ON l2.ID = other.LopDotID
               JOIN dbo.RLKhoaDot AS k2 ON k2.ID = l2.KhoaDotID
               JOIN dbo.RLDot AS d2 ON d2.ID = k2.DotID AND d2.HocKyID = d.HocKyID)
        THROW 51711, 'DRL: the student already has a ballot for this semester.', 1;
END;
GO

CREATE TRIGGER dbo.TR_RLPhieuDiemCT_Diem ON dbo.RLPhieuDiemCT AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM (SELECT PhieuID FROM inserted UNION SELECT PhieuID FROM deleted) AS affected
               JOIN dbo.RLPhieu AS p ON p.ID = affected.PhieuID
               JOIN dbo.RLLopDot AS l ON l.ID = p.LopDotID
               JOIN dbo.RLKhoaDot AS k ON k.ID = l.KhoaDotID
               JOIN dbo.RLDot AS d WITH (HOLDLOCK) ON d.ID = k.DotID
               WHERE d.TrangThai = 2 OR EXISTS (
                   SELECT 1 FROM dbo.nhat_ky_thao_tac AS nk WITH (HOLDLOCK)
                   WHERE nk.hanh_dong = N'RL_CHOT_LOP' AND nk.loai_doi_tuong = N'RLLopDot'
                     AND nk.doi_tuong_id = CONVERT(nvarchar(128), l.ID)))
        THROW 51712, 'DRL: class scores are closed.', 1;

    IF EXISTS (SELECT 1 FROM inserted AS i FULL JOIN deleted AS old
                 ON old.PhieuID = i.PhieuID AND old.BieuMauCTID = i.BieuMauCTID
               JOIN dbo.RLPhieu AS p WITH (HOLDLOCK) ON p.ID = COALESCE(i.PhieuID, old.PhieuID)
               WHERE p.XacNhanSV = 1 AND EXISTS (SELECT i.DiemCN EXCEPT SELECT old.DiemCN))
        THROW 51713, 'DRL: confirmed individual scores are fixed.', 1;

    IF EXISTS (SELECT 1 FROM inserted AS i
               JOIN dbo.RLBieuMauCT AS c ON c.ID = i.BieuMauCTID
               JOIN dbo.RLNhomTieuChi AS n ON n.ID = c.NhomTieuChiID
               JOIN dbo.RLPhieu AS p ON p.ID = i.PhieuID
               JOIN dbo.RLLopDot AS l ON l.ID = p.LopDotID
               JOIN dbo.RLKhoaDot AS k ON k.ID = l.KhoaDotID
               JOIN dbo.RLDot AS d ON d.ID = k.DotID
               WHERE c.LoaiDong <> 0 OR n.BieuMauID <> d.BieuMauID
                  OR i.DiemCN > c.DiemToiDa OR i.DiemLop > c.DiemToiDa)
        THROW 51714, 'DRL: invalid criterion, form version or criterion score.', 1;

    IF EXISTS (
        SELECT 1 FROM (
            SELECT n.DiemToiDa AS TranNhom, m.DiemToiDa AS TranPhieu,
                   SUM(ct.DiemCN) OVER (PARTITION BY ct.PhieuID, n.ID) AS CaNhanNhom,
                   SUM(ct.DiemLop) OVER (PARTITION BY ct.PhieuID, n.ID) AS LopNhom,
                   SUM(ct.DiemCN) OVER (PARTITION BY ct.PhieuID) AS CaNhanPhieu,
                   SUM(ct.DiemLop) OVER (PARTITION BY ct.PhieuID) AS LopPhieu
            FROM dbo.RLPhieuDiemCT AS ct WITH (UPDLOCK, HOLDLOCK)
            JOIN dbo.RLBieuMauCT AS c ON c.ID = ct.BieuMauCTID
            JOIN dbo.RLNhomTieuChi AS n ON n.ID = c.NhomTieuChiID
            JOIN dbo.RLBieuMau AS m ON m.ID = n.BieuMauID
            WHERE ct.PhieuID IN (SELECT PhieuID FROM inserted)
        ) AS totals
        WHERE CaNhanNhom > TranNhom OR LopNhom > TranNhom OR CaNhanPhieu > TranPhieu OR LopPhieu > TranPhieu
    )
        THROW 51715, 'DRL: score exceeds the group or ballot maximum.', 1;
END;
GO

