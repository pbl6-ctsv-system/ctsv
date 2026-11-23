[CmdletBinding()]
param([string]$JournalName='p11-20261123-real-minutes.intent.json')
$ErrorActionPreference='Stop'
if($JournalName -notmatch '^p11-[a-zA-Z0-9_-]+\.intent\.json$'){throw 'Invalid journal name'}
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path $root 'tools/dev/Dev.Common.ps1')
$private='C:/Users/USER/AppData/Local/CTSV/ca-dut'
$journal=Join-Path $private $JournalName
if(Test-Path -LiteralPath $journal){throw 'Existing P11 intent: inspect request/receipt/phase before continuing. Never repeat signing.'}
$cloud=Get-DevCloudinaryEnvironment 'C:/Users/USER/AppData/Local/CTSV/dev/E73A1C8C2025'
$cred=Import-Clixml -LiteralPath (Join-Path $private 'credential.clixml')
$saved=@{}
$values=@{CA_DUT_URL='https://chukyso.dut.udn.vn';CA_DUT_CLIENT_ID=$cred.UserName;CA_DUT_CLIENT_SECRET=$cred.GetNetworkCredential().Password;
 CA_DUT_TEST_PDF=(Join-Path (Split-Path $root -Parent) 'nghiepvu/05-du-lieu-mau/file ký thử.pdf');P11_REAL_JOURNAL=$journal;
 P00_SQL_PORT=[string](Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server\MSSQL16.DEV2022\MSSQLServer\SuperSocketNetLib\Tcp\IPAll').TcpDynamicPorts}
foreach($key in $cloud.Keys){$values[$key]=$cloud[$key]}
$previousPath=$env:PATH
try{
 foreach($key in $values.Keys){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,[string]$values[$key],'Process')}
 $env:PATH=(Join-Path $env:USERPROFILE '.m2/repository/com/microsoft/sqlserver/mssql-jdbc_auth/12.10.2.x64')+';'+$env:PATH
 Push-Location $root
 try{
  & .\mvnw.cmd -o -q '-Dp11.test.enabled=true' '-Dp11.real.enabled=true' '-Dit.test=SigningRealIT#realMinutesTwoActorsStoreAndReadBothOutputs' test-compile failsafe:integration-test failsafe:verify *> target/p11-real-check.log
  if($LASTEXITCODE -ne 0){Get-Content target/p11-real-check.log -Tail 14;throw 'P11 REAL stopped. Inspect journal and DB, do not retry signing.'}
  Get-Content target/failsafe-reports/pbl6.ctsv.auth.SigningRealIT.txt -TotalCount 5
 }finally{Pop-Location}
}finally{
 $env:PATH=$previousPath
 foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}
 $cred=$null;$cloud=$null;$values=$null
}
