#requires -Version 7.0
param([ValidateSet('audit','verify')][string]$Mode='audit')
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
. "$root/tools/dev/Dev.Common.ps1"
$devHome=Get-DevHome $root;$config=Read-DevConfig $devHome
$port=[int](Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll').TcpDynamicPorts
$cn=[System.Data.SqlClient.SqlConnection]::new("Server=tcp:localhost,$port;Database=ctsv_dev;Integrated Security=true;Encrypt=true;TrustServerCertificate=true;Application Name=student-journey-guard")
$cn.Open()
try {
 $cmd=$cn.CreateCommand();$cmd.CommandText="SELECT @@SERVERNAME,DB_NAME(),SUSER_SNAME(owner_sid),(SELECT CONVERT(nvarchar(100),value) FROM sys.extended_properties WHERE class=0 AND name=N'ctsv_dev_owner'),(SELECT MAX(CONVERT(int,version)) FROM dbo.flyway_schema_history) FROM sys.databases WHERE name=DB_NAME()"
 $r=$cmd.ExecuteReader();if(-not $r.Read()){throw 'DEV guard empty'}
 $guard=@{server=$r.GetString(0);database=$r.GetString(1);owner=$r.GetString(2);marker=$r.GetString(3);version=$r.GetInt32(4)};$r.Close()
 if($guard.server -ne 'DESKTOP-7PPHP5B\DEV2022' -or $guard.database -ne 'ctsv_dev' -or $guard.owner -ne [Security.Principal.WindowsIdentity]::GetCurrent().Name -or $guard.marker -ne 'ctsv-local-dev-v1' -or $guard.version -ne 18){throw 'Wrong DEV SQL target/owner/marker/history'}
 $cmd.CommandText='SELECT sinh_vien_id,COUNT(*) FROM dbo.mghp_ho_so GROUP BY sinh_vien_id';$r=$cmd.ExecuteReader();$owners=@();while($r.Read()){$owners+=@{studentId=$r.GetInt64(0).ToString();applications=$r.GetInt32(1)}};$r.Close()
 $evidence=Join-Path $root 'target/student-journey-dev-20261121';New-Item -ItemType Directory -Path $evidence -Force|Out-Null
 @{guard=$guard;ownership=$owners}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $evidence "$Mode-db-before.json")
}finally{$cn.Close()}
try {
 foreach($kind in @('specialist','student')) {
  [Environment]::SetEnvironmentVariable('RF_'+$kind.ToUpper()+'_USER',$config.Accounts.$kind.Username,'Process')
  [Environment]::SetEnvironmentVariable('RF_'+$kind.ToUpper()+'_PASSWORD',(Open-DevSecret $config.Accounts.$kind.Password),'Process')
 }
 $env:NODE_EXTRA_CA_CERTS=Join-Path $devHome 'https-cert.pem'
 $env:RF_SAMPLE_PDF=Join-Path $root '../nghiepvu/05-du-lieu-mau/du lieu test.pdf'
 & node "$PSScriptRoot/student-journey-dev.mjs" $Mode
 $result=$LASTEXITCODE
} finally {
 foreach($name in @('RF_SPECIALIST_USER','RF_SPECIALIST_PASSWORD','RF_STUDENT_USER','RF_STUDENT_PASSWORD','RF_SAMPLE_PDF','NODE_EXTRA_CA_CERTS')){[Environment]::SetEnvironmentVariable($name,$null,'Process')}
}
exit $result
