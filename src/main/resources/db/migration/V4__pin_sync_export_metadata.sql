-- Preserve exact wire metadata (including fractional as_of) independently from SQL datetimeoffset precision.
ALTER TABLE dbo.dong_bo_luot ADD anchor_json nvarchar(max) NULL;
GO
ALTER TABLE dbo.dong_bo_luot ADD CONSTRAINT ck_dong_bo_anchor_json CHECK(anchor_json IS NULL OR ISJSON(anchor_json)=1);
