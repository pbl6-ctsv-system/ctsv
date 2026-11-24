#requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory)][string]$InputCsv,[switch]$Apply)
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
. (Join-Path $taskRoot 'tools/dev/Dev.Common.ps1')
$taskHome=Get-DevHome $taskRoot
$taskSync=Get-Content -LiteralPath (Join-Path $taskHome 'sync/config.json') -Raw|ConvertFrom-Json
if($taskSync.ProviderPort -ne 9443){throw 'Unexpected saved DEV provider port'}
$taskUrl='https://127.0.0.1:9443'
$taskCsv=(Resolve-Path -LiteralPath $InputCsv).Path
$taskRows=@(Import-Csv -LiteralPath $taskCsv -Encoding utf8)
if(-not $taskRows.Count){throw 'Empty semester file'}
$taskIds=[Collections.Generic.HashSet[string]]::new();$taskCodes=[Collections.Generic.HashSet[string]]::new()
foreach($r in $taskRows){
 foreach($field in @('ID','MaKH','TenKH','NgayDay','TuanDau','SoTuan','NamHoc')){if(-not $r.PSObject.Properties[$field]){throw "Missing approved KeHoach field: $field"}}
 if($r.ID -notmatch '^[0-9]{1,18}$' -or $r.MaKH -notmatch '^[A-Za-z0-9_-]{1,32}$' -or -not $taskIds.Add($r.ID) -or -not $taskCodes.Add($r.MaKH)){throw 'Invalid or duplicate source ID/code'}
 foreach($field in @('TenKH','NgayDay','TuanDau','SoTuan','NamHoc')){if($r.$field.Length -gt 512){throw 'Source field exceeds mapping limit'}}
}
$taskHash=(Get-FileHash -LiteralPath $taskCsv -Algorithm SHA256).Hash
$taskPort=[int](Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll').TcpDynamicPorts
if($taskPort -lt 1){throw 'DEV TCP port unavailable'}
$taskDb=[Data.SqlClient.SqlConnection]::new("Server=127.0.0.1,$taskPort;Database=ctsv_dev;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Connect Timeout=5")
$taskTx=$null
function Command([string]$sql,[object[]]$values=@()){
 $c=$taskDb.CreateCommand();$c.CommandText=$sql;$c.CommandTimeout=15;if($taskTx){$c.Transaction=$taskTx}
 for($i=0;$i -lt $values.Count;$i++){[void]$c.Parameters.AddWithValue("@p$i",$(if($null -eq $values[$i] -or $values[$i] -ceq ''){[DBNull]::Value}else{$values[$i]}))};return $c
}
function Scalar([string]$sql,[object[]]$values=@()){$c=Command $sql $values;try{return $c.ExecuteScalar()}finally{$c.Dispose()}}
function Exec([string]$sql,[object[]]$values=@()){$c=Command $sql $values;try{[void]$c.ExecuteNonQuery()}finally{$c.Dispose()}}
try{
 $taskDb.Open()
 if([int](Scalar "SELECT COUNT(*) FROM sys.databases WHERE database_id=DB_ID() AND name=N'ctsv_dev' AND SUSER_SNAME(owner_sid)=SUSER_SNAME() AND compatibility_level=160 AND collation_name=N'Vietnamese_100_CI_AS_SC'") -ne 1 -or (Scalar "SELECT CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName'))") -cne 'DEV2022' -or (Scalar "SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties WHERE class=0 AND name=N'ctsv_dev_owner'") -cne 'ctsv-local-dev-v1'){throw 'Unexpected DEV target/owner/marker'}
 if([int](Scalar "SELECT COUNT(*) FROM sys.tables WHERE name=N'hoc_ky' AND schema_id=SCHEMA_ID('dbo')") -ne 1){throw 'Existing schema required; this tool never migrates'}
 $taskTx=$taskDb.BeginTransaction([Data.IsolationLevel]::Serializable)
 Exec "SET XACT_ABORT ON;DECLARE @r int;EXEC @r=sys.sp_getapplock @Resource=N'shcn.dev.semester-reference.v1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;IF @r<0 THROW 51921,'SHCN semester recovery busy',1"
 $record=Scalar "SELECT CONVERT(nvarchar(4000),value) FROM sys.extended_properties WHERE class=0 AND name=N'shcn_dev_semester_reference_v1'"
 if($record -and ($record|ConvertFrom-Json).sha256 -cne $taskHash){throw 'Previously imported file differs; no automatic overwrite'}
 $sourceCount=[int](Scalar "SELECT COUNT(*) FROM dbo.he_thong_ngoai WITH(UPDLOCK,HOLDLOCK) WHERE ma=N'DEV-SIS-SYNC'")
 if($sourceCount -gt 1){throw 'Duplicate source registry'}
 if($sourceCount -eq 1 -and [int](Scalar "SELECT COUNT(*) FROM dbo.he_thong_ngoai WHERE ma=N'DEV-SIS-SYNC' AND loai_he_thong=N'SIS' AND che_do_ket_noi=N'API' AND dang_hoat_dong=1 AND url_goc=@p0" @($taskUrl)) -ne 1){throw 'Source configuration conflict; no overwrite'}
 $sourceId=if($sourceCount){[long](Scalar "SELECT id FROM dbo.he_thong_ngoai WHERE ma=N'DEV-SIS-SYNC'")}else{0}
 $existing=if($sourceId){[int](Scalar 'SELECT COUNT(*) FROM dbo.hoc_ky WHERE he_thong_nguon_id=@p0' @($sourceId))}else{0}
 if($existing -gt 0 -and -not $record){throw 'Existing source data belongs to sync/another importer; no takeover'}
 if($record -and $existing -ne $taskRows.Count){throw 'Previously imported rows changed; no automatic restore after deletion'}
 if($Apply -and -not $record){
  if(-not $sourceId){$sourceId=[long](Scalar "INSERT dbo.he_thong_ngoai(ma,ten,loai_he_thong,che_do_ket_noi,url_goc,tham_chieu_thong_tin_xac_thuc,dang_hoat_dong,thoi_diem_tao,thoi_diem_cap_nhat) VALUES(N'DEV-SIS-SYNC',N'SIS DEV SYNTHETIC',N'SIS',N'API',@p0,N'local:sync/service-key',1,SYSUTCDATETIME(),SYSUTCDATETIME());SELECT CONVERT(bigint,SCOPE_IDENTITY())" @($taskUrl))}
  foreach($r in $taskRows){Exec "INSERT dbo.hoc_ky(ma,ten,ngay_day_nguon,tuan_dau_nguon,so_tuan_nguon,nam_hoc_nguon,he_thong_nguon_id,ma_dinh_danh_ben_ngoai,dang_hien_dien_nguon,phien_ban_anh_xa,thoi_diem_tao,thoi_diem_cap_nhat) VALUES(@p0,@p1,@p2,@p3,@p4,@p5,@p6,@p7,1,N'shcn.dev.semester-csv.v1',SYSUTCDATETIME(),SYSUTCDATETIME())" @($r.MaKH,$r.TenKH,$r.NgayDay,$r.TuanDau,$r.SoTuan,$r.NamHoc,$sourceId,$r.ID)}
  $ledger=@{version=1;sha256=$taskHash;rows=$taskRows.Count;sourceId=$sourceId;mapping='KeHoach.ID->external ID;MaKH->ma;TenKH->ten;NamHoc->nam_hoc_nguon'}|ConvertTo-Json -Compress
  Exec "EXEC sys.sp_addextendedproperty @name=N'shcn_dev_semester_reference_v1',@value=@p0" @($ledger)
  Exec "INSERT dbo.nhat_ky_thao_tac(su_kien_id,loai_chu_the_thao_tac,hanh_dong,loai_doi_tuong,doi_tuong_id,ly_do) VALUES(@p0,N'system',N'SHCN_DEV_SEMESTER_REFERENCE_IMPORTED',N'he_thong_ngoai',@p1,@p2)" @([guid]::NewGuid().ToString(),[string]$sourceId,"Owner requested local recovery; KeHoach CSV SHA256=$taskHash; rows=$($taskRows.Count)")
 }
 if($record){foreach($r in $taskRows){if([int](Scalar 'SELECT COUNT(*) FROM dbo.hoc_ky WHERE he_thong_nguon_id=@p0 AND ma_dinh_danh_ben_ngoai=@p1 AND ma=@p2 AND dang_hien_dien_nguon=1 AND ISNULL(ten,N'''')=ISNULL(@p3,N'''') AND ISNULL(nam_hoc_nguon,N'''')=ISNULL(@p4,N'''')' @($sourceId,$r.ID,$r.MaKH,$r.TenKH,$r.NamHoc)) -ne 1){throw 'Imported source data changed; no overwrite'}}}
 if($Apply){$taskTx.Commit()}else{$taskTx.Rollback()}
 @{version=1;mode=$(if($Apply){'APPLY'}else{'CHECK_ONLY'});source='DEV-SIS-SYNC';csvRows=$taskRows.Count;existingSourceRows=$existing;inserted=$(if($Apply -and -not $record){$taskRows.Count}else{0});sha256=$taskHash;migrationExecuted=$false}|ConvertTo-Json
}catch{if($taskTx -and $taskTx.Connection){$taskTx.Rollback()};throw}finally{if($taskTx){$taskTx.Dispose()};$taskDb.Dispose()}
