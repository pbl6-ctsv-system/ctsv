-- Owner25/11/2026: scholarship semesters require a start date only.
-- Retain the existing date-range check for a non-NULL end, keys and existing data.
ALTER TABLE dbo.hb_hoc_ky ALTER COLUMN ngay_ket_thuc date NULL;
