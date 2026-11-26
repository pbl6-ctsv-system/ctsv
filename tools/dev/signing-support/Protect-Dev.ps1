param([ValidateSet('backup','verify','grant')][string]$Action='verify')
$ErrorActionPreference='Stop'
$taskEvidence=Join-Path $PSScriptRoot '../../../target/signing-support-dev'
[void](New-Item -ItemType Directory -Force -Path $taskEvidence)
$connection=[System.Data.SqlClient.SqlConnection]::new('Server=tcp:localhost,56826;Database=ctsv_dev;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connect Timeout=8')
$connection.Open()
function Scalar([string]$sql){$cmd=$connection.CreateCommand();$cmd.CommandTimeout=120;$cmd.CommandText=$sql;$cmd.ExecuteScalar()}
function Execute([string]$sql){$cmd=$connection.CreateCommand();$cmd.CommandTimeout=180;$cmd.CommandText=$sql;[void]$cmd.ExecuteNonQuery()}
try{
 if((Scalar "SELECT IIF(DB_NAME()=N'ctsv_dev' AND CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName'))=N'DEV2022' AND owner_sid=SUSER_SID() AND EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=0 AND name=N'ctsv_dev_owner' AND CONVERT(nvarchar(128),value)=N'ctsv-local-dev-v1'),1,0) FROM sys.databases WHERE database_id=DB_ID()") -ne 1){throw 'DEV target/owner/marker mismatch'}
 $facts=[ordered]@{database='ctsv_dev';instance='DEV2022';maxVersion=[int](Scalar 'SELECT MAX(TRY_CONVERT(int,version)) FROM dbo.flyway_schema_history WHERE success=1');receipts=[int](Scalar 'SELECT COUNT(*) FROM dbo.bien_nhan_ky');outputs=[int](Scalar 'SELECT COUNT(*) FROM dbo.tep_ket_qua_ky')}
 foreach($table in @('bien_nhan_ky','tep_ket_qua_ky')){$facts[$table+'Hash']=Scalar "SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT * FROM dbo.$table ORDER BY id FOR JSON PATH)),2)"}
 if($Action -eq 'backup'){
  if(Test-Path -LiteralPath (Join-Path $taskEvidence 'before.json')){throw 'Baseline exists; inspect instead of overwriting'}
  if($facts.maxVersion -ne 31){throw 'Expected baseline V31'}
  $backupDir=[string](Scalar "SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'))")
  if(-not [IO.Path]::IsPathRooted($backupDir)){throw 'Invalid backup folder'}
  $backupFile=Join-Path $backupDir ('ctsv_dev_pre_support_v32_'+(Get-Date -Format yyyyMMdd_HHmmss)+'.bak')
  $safe=$backupFile.Replace("'","''")
  Execute "BACKUP DATABASE [ctsv_dev] TO DISK=N'$safe' WITH COPY_ONLY,CHECKSUM,INIT"
  Execute "RESTORE VERIFYONLY FROM DISK=N'$safe' WITH CHECKSUM"
  $facts['backup']=$backupFile;$facts['backupVerified']=$true
  $facts|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $taskEvidence 'before.json') -Encoding utf8
 }elseif($Action -eq 'grant'){
  if($facts.maxVersion -ne 32){throw 'V32 required'}
  Execute @'
SET XACT_ABORT ON; BEGIN TRAN;
DECLARE @actor bigint=(SELECT d.nguoi_dung_id FROM dbo.TaiKhoan t JOIN dbo.dinh_danh_dang_nhap d ON d.ma_chu_the_dang_nhap=CONVERT(nvarchar(36),t.ID) WHERE t.TenDangNhapChuanHoa=N'dev.admin' AND d.ma_nha_cung_cap=N'https://127.0.0.1:8443');
DECLARE @role bigint=(SELECT id FROM dbo.vai_tro WHERE ma=N'mghp_quan_tri_ky' AND dang_hoat_dong=1);
IF @actor IS NULL OR @role IS NULL THROW 51997,N'Existing DEV admin identity/role required',1;
IF NOT EXISTS(SELECT 1 FROM dbo.phan_quyen_nguoi_dung WHERE nguoi_dung_id=@actor AND vai_tro_id=@role AND ma_phan_he=N'MGHP' AND don_vi_id IS NULL)
BEGIN
 INSERT dbo.phan_quyen_nguoi_dung(nguoi_dung_id,vai_tro_id,ma_phan_he) VALUES(@actor,@role,N'MGHP');
 INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,nguoi_dung_id,hanh_dong,loai_doi_tuong,doi_tuong_id,ly_do,thoi_diem_tao) VALUES(NEWID(),N'user',@actor,N'SIGNING_SUPPORT_OWNER_GRANT',N'nguoi_dung',CONVERT(nvarchar(32),@actor),N'Owner approved dev.admin signing support grant 25/11/2026',SYSUTCDATETIME());
END;
COMMIT;
'@
  $facts['grant']='dev.admin / MGHP / mghp_quan_tri_ky'
 }else{
  $before=Get-Content -LiteralPath (Join-Path $taskEvidence 'before.json') -Raw|ConvertFrom-Json
  foreach($key in @('receipts','outputs','bien_nhan_kyHash','tep_ket_qua_kyHash')){if([string]$facts[$key] -ne [string]$before.$key){throw "Signed data changed: $key"}}
  $facts['signedDataPreserved']=$true;$facts|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $taskEvidence 'after.json') -Encoding utf8
 }
 $facts|ConvertTo-Json
}finally{$connection.Dispose()}
