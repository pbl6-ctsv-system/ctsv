#requires -Version 7.0
param([ValidateSet('before','after')][string]$Stage='before')
$ErrorActionPreference='Stop'
$port=[int](Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll').TcpDynamicPorts
$cn=[System.Data.SqlClient.SqlConnection]::new("Server=tcp:localhost,$port;Database=ctsv_dev;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Application Name=round-form-readonly-guard")
$cn.Open()
function Rows([string]$sql){$cmd=$cn.CreateCommand();$cmd.CommandText=$sql;$r=$cmd.ExecuteReader();$rows=[Collections.Generic.List[object]]::new();do{while($r.Read()){$x=[ordered]@{};for($i=0;$i -lt $r.FieldCount;$i++){$x[$r.GetName($i)]=if($r.IsDBNull($i)){$null}else{$r.GetValue($i)}};$rows.Add([pscustomobject]$x)}}while($r.NextResult());$r.Close();return ,$rows.ToArray()}
try{
 $guard=Rows "SELECT @@SERVERNAME serverName,DB_NAME() databaseName,SUSER_SNAME(owner_sid) ownerName FROM sys.databases WHERE name=DB_NAME();SELECT CONVERT(nvarchar(100),value) marker FROM sys.extended_properties WHERE class=0 AND name=N'ctsv_dev_owner';SELECT MAX(CONVERT(int,version)) version FROM dbo.flyway_schema_history;"
 if($guard[0].serverName -ne 'DESKTOP-7PPHP5B\DEV2022' -or $guard[0].databaseName -ne 'ctsv_dev' -or $guard[0].ownerName -ne [Security.Principal.WindowsIdentity]::GetCurrent().Name -or $guard[1].marker -ne 'ctsv-local-dev-v1' -or $guard[2].version -ne $(if($Stage -eq 'before'){17}else{18})){throw 'Wrong DEV target/history'}
 $snapshot=Rows @'
DECLARE @cols nvarchar(max)=(SELECT STRING_AGG(CONVERT(nvarchar(max),QUOTENAME(name)),N',') WITHIN GROUP(ORDER BY column_id) FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.mghp_dot_xet') AND name NOT IN(N'huong_dan_ke_khai',N'huong_dan_can_cu_nop_muon'));
EXEC(N'SELECT N''rounds'' entity,COUNT(*) [rowCount],CONVERT(varchar(64),HASHBYTES(''SHA2_256'',(SELECT '+@cols+N' FROM dbo.mghp_dot_xet ORDER BY id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) digest FROM dbo.mghp_dot_xet');
SELECT N'bindings' entity,COUNT(*) [rowCount],CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT * FROM dbo.mghp_chinh_sach_dot_xet ORDER BY id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) digest FROM dbo.mghp_chinh_sach_dot_xet;
SELECT N'applications' entity,COUNT(*) [rowCount],CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT * FROM dbo.mghp_ho_so ORDER BY id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) digest FROM dbo.mghp_ho_so;
SELECT N'accounts' entity,COUNT(*) [rowCount],CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT * FROM dbo.TaiKhoan ORDER BY ID FOR JSON PATH,INCLUDE_NULL_VALUES)),2) digest FROM dbo.TaiKhoan;
SELECT N'history' entity,COUNT(*) [rowCount],CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT * FROM dbo.mghp_lich_su_lich_trinh ORDER BY id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) digest FROM dbo.mghp_lich_su_lich_trinh;
'@
 $columns=Rows "SELECT name FROM sys.columns WHERE object_id=OBJECT_ID(N'dbo.mghp_dot_xet') AND name IN(N'huong_dan_ke_khai',N'huong_dan_can_cu_nop_muon');"
 $deps=Rows "SELECT OBJECT_SCHEMA_NAME(object_id)+N'.'+OBJECT_NAME(object_id) objectName FROM sys.sql_modules WHERE definition LIKE N'%huong_dan_ke_khai%' OR definition LIKE N'%huong_dan_can_cu_nop_muon%';"
 if($deps.Count){throw 'Unexpected SQL object depends on retired columns'}
 if($Stage -eq 'before' -and $columns.Count -ne 2 -or $Stage -eq 'after' -and $columns.Count -ne 0){throw 'Retired columns mismatch'}
 $history=Rows 'SELECT version,checksum,CONVERT(int,success) success FROM dbo.flyway_schema_history ORDER BY installed_rank'
 $dir=Join-Path $PSScriptRoot '../../target/round-form-dev-20261121';New-Item -ItemType Directory -Path $dir -Force|Out-Null
 if($Stage -eq 'after'){$old=Get-Content (Join-Path $dir 'db-before.json') -Raw|ConvertFrom-Json;if(($old.snapshot|ConvertTo-Json -Compress) -ne ($snapshot|ConvertTo-Json -Compress)){throw 'Unrelated data changed during migration'};if(($old.history|ConvertTo-Json -Compress) -ne (@($history|Where-Object version -ne '18')|ConvertTo-Json -Compress)){throw 'Applied migration history changed'}}
 @{guard=$guard;port=$port;snapshot=$snapshot;history=$history;retiredColumns=$columns.Count}|ConvertTo-Json -Depth 8|Set-Content (Join-Path $dir "db-$Stage.json")
 Write-Output "Guard/snapshot $Stage PASS: ctsv_dev, retired columns $($columns.Count)"
}finally{$cn.Close()}
