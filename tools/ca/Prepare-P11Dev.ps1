$ErrorActionPreference='Stop'
$root=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
. (Join-Path $root 'tools/dev/Dev.Common.ps1')
$localHome=Get-DevHome $root;$cfg=Read-DevConfig $localHome
$path=Join-Path $localHome 'p11-accounts.dpapi.json'
if(Test-Path -LiteralPath $path){$accounts=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json -AsHashtable}else{
 $accounts=@{};foreach($role in @('PP','PHT')){$accounts[$role]=@{Username=if($role -eq 'PP'){'dev.p11.phophong'}else{'dev.p11.phohieutruong'};Password=Protect-DevSecret ('P11!'+[guid]::NewGuid().ToString('N')+'!') }}
 $accounts|ConvertTo-Json -Depth 4|Set-Content -LiteralPath $path -Encoding utf8
}
$values=Get-DevCaEnvironment (Join-Path $env:LOCALAPPDATA 'CTSV/ca-dut')
$values.CTSV_DEV_HOME=$localHome;$values.P11_CV_USERNAME=$cfg.Accounts.specialist.Username
foreach($role in @('PP','PHT')){$values['P11_'+$role+'_PASSWORD']=Open-DevSecret $accounts[$role].Password}
$saved=@{};$oldPath=$env:PATH
try{
 foreach($name in $values.Keys){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process');[Environment]::SetEnvironmentVariable($name,[string]$values[$name],'Process')}
 $env:PATH=(Join-Path $env:USERPROFILE '.m2/repository/com/microsoft/sqlserver/mssql-jdbc_auth/12.10.2.x64')+';'+$env:PATH
 $cp=(Join-Path $root 'target/classes')+';'+(Get-Content -LiteralPath (Join-Path $localHome 'classpath.txt') -Raw).Trim()
 $out=Join-Path $root 'target/p11-dev-tools';New-Item -ItemType Directory -Path $out -Force|Out-Null
 & javac -encoding UTF-8 -cp $cp -d $out (Join-Path $root 'tools/dev/java/pbl6/ctsv/auth/P11DevFixture.java');if($LASTEXITCODE -ne 0){throw 'Fixture compile failed'}
 & java -cp ($out+';'+$cp) pbl6.ctsv.auth.P11DevFixture
 if($LASTEXITCODE -ne 0){throw 'Fixture stopped; inspect current state before rerun'}
}finally{$env:PATH=$oldPath;foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')};$values=$null;$accounts=$null}
