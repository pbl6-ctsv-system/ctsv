#requires -Version 7.0
param([ValidateSet('before','after')][string]$Stage='before',[string]$CreatedAccount='')
$ErrorActionPreference='Stop'
$sqlPort=[int](Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll').TcpDynamicPorts
$connection=[System.Data.SqlClient.SqlConnection]::new("Server=tcp:localhost,$sqlPort;Database=ctsv_dev;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Application Name=p1-usability-readonly-guard")
$connection.Open()
function Read-Rows([string]$sql){$cmd=$connection.CreateCommand();$cmd.CommandText=$sql;if($sql.Contains('@createdAccount')){$null=$cmd.Parameters.Add('@createdAccount',[System.Data.SqlDbType]::NVarChar,128);$cmd.Parameters['@createdAccount'].Value=$CreatedAccount};$r=$cmd.ExecuteReader();$items=[Collections.Generic.List[object]]::new();do{while($r.Read()){$row=[ordered]@{};for($i=0;$i -lt $r.FieldCount;$i++){$row[$r.GetName($i)]=if($r.IsDBNull($i)){$null}else{$r.GetValue($i)}};$items.Add([pscustomobject]$row)}}while($r.NextResult());$r.Close();return ,$items.ToArray()}
try{
 $guard=Read-Rows "SELECT @@SERVERNAME serverName,DB_NAME() databaseName,IIF(owner_sid=SUSER_SID(),1,0) owns FROM sys.databases WHERE name=DB_NAME();SELECT CONVERT(nvarchar(100),value) marker FROM sys.extended_properties WHERE class=0 AND name=N'ctsv_dev_owner';SELECT MAX(CONVERT(int,version)) version,SUM(IIF(success=0,1,0)) failed FROM dbo.flyway_schema_history;"
 if($guard[0].serverName -ne 'DESKTOP-7PPHP5B\DEV2022' -or $guard[0].databaseName -ne 'ctsv_dev' -or $guard[0].owns -ne 1 -or $guard[1].marker -ne 'ctsv-local-dev-v1' -or $guard[2].version -ne 18 -or $guard[2].failed -ne 0){throw 'DEV server/database/owner/marker/Flyway guard failed'}
 $snapshot=Read-Rows @'
SELECT N'applications' entity,COUNT(*) [rowCount],CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT h.* FROM dbo.mghp_ho_so h WHERE h.dot_xet_id NOT IN (SELECT id FROM dbo.mghp_dot_xet WHERE ma_dot_xet LIKE N'UX-INTAKE-%') ORDER BY id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) digest FROM dbo.mghp_ho_so WHERE dot_xet_id NOT IN (SELECT id FROM dbo.mghp_dot_xet WHERE ma_dot_xet LIKE N'UX-INTAKE-%');
SELECT N'accounts' entity,COUNT(*) [rowCount],CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT ID,TenDangNhap,MatKhauHash FROM dbo.TaiKhoan WHERE TenDangNhap<>@createdAccount ORDER BY ID FOR JSON PATH,INCLUDE_NULL_VALUES)),2) digest FROM dbo.TaiKhoan WHERE TenDangNhap<>@createdAccount;
SELECT N'submissions' entity,COUNT(*) [rowCount],CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT v.* FROM dbo.mghp_phien_ban_ho_so v JOIN dbo.mghp_ho_so h ON h.id=v.ho_so_id WHERE h.dot_xet_id NOT IN (SELECT id FROM dbo.mghp_dot_xet WHERE ma_dot_xet LIKE N'UX-INTAKE-%') ORDER BY v.id FOR JSON PATH,INCLUDE_NULL_VALUES)),2) digest FROM dbo.mghp_phien_ban_ho_so v JOIN dbo.mghp_ho_so h ON h.id=v.ho_so_id WHERE h.dot_xet_id NOT IN (SELECT id FROM dbo.mghp_dot_xet WHERE ma_dot_xet LIKE N'UX-INTAKE-%');
'@
 $history=Read-Rows 'SELECT version,checksum,CONVERT(int,success) success FROM dbo.flyway_schema_history ORDER BY installed_rank'
 $backendProcessId=(Get-NetTCPConnection -State Listen -LocalPort 8443 | Select-Object -First 1).OwningProcess
 $runtime=Read-Rows "SELECT DISTINCT host_process_id backendPid,DB_NAME(database_id) databaseName FROM sys.dm_exec_sessions WHERE host_process_id=$backendProcessId AND is_user_process=1;"
 if(!$runtime.Count -or @($runtime|Where-Object databaseName -ne 'ctsv_dev').Count){throw 'Running Backend is not verified on ctsv_dev'}
 $out=Join-Path $PSScriptRoot '../../target/p1-usability-20261122';New-Item -ItemType Directory -Path $out -Force|Out-Null
 if($Stage -eq 'after'){
  if($CreatedAccount -and $CreatedAccount -cne 'p1.ux.20261122.1795323491856'){throw 'Only the recorded P1-created account may be excluded'}
  $prior=Get-Content -LiteralPath (Join-Path $out 'db-before.json') -Raw|ConvertFrom-Json
  if(($prior.snapshot|ConvertTo-Json -Compress) -ne ($snapshot|ConvertTo-Json -Compress)){throw 'Pre-existing applications/submissions or credentials changed'}
  if(($prior.history|ConvertTo-Json -Compress) -ne ($history|ConvertTo-Json -Compress)){throw 'Flyway history changed'}
 }
 @{guard=$guard;history=$history;snapshot=$snapshot;runtime=$runtime}|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $out "db-$Stage.json")
 Write-Output "Read-only guard $Stage PASS: localhost\DEV2022 / ctsv_dev V18; owner and marker verified."
}finally{$connection.Close()}
