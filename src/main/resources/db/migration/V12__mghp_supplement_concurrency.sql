ALTER TABLE dbo.mghp_yeu_cau_bo_sung ADD phien_ban_ky_thuat rowversion NOT NULL;

CREATE UNIQUE INDEX uq_mghp_supplement_active_root
    ON dbo.mghp_yeu_cau_bo_sung (ho_so_id)
    WHERE trang_thai IN (N'open', N'responded', N'expired');

CREATE UNIQUE INDEX uq_mghp_submission_supplement
    ON dbo.mghp_phien_ban_ho_so (yeu_cau_bo_sung_id)
    WHERE yeu_cau_bo_sung_id IS NOT NULL;
