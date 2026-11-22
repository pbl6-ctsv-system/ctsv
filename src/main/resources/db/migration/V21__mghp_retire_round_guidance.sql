-- Owner-approved round form simplification, 21/11/2026.
-- Only the two retired free-text attributes are removed. Rows, policy bindings,
-- timestamps, source markers, history, notifications and technical versions remain.
ALTER TABLE dbo.mghp_dot_xet DROP COLUMN huong_dan_ke_khai, huong_dan_can_cu_nop_muon;
