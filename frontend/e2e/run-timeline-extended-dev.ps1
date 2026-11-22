#requires -Version 7.0
param([ValidateSet('rounds','sources','prepare-student','student','receiving','additional','security-admin')][string]$Phase='rounds',[string]$EvidenceDirectory='target/pre-p10-extended-20261121')
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
. "$root/tools/dev/Dev.Common.ps1"
$devHome=Get-DevHome $root
$config=Read-DevConfig $devHome
$names=@('TLX_SPECIALIST_USER','TLX_SPECIALIST_PASSWORD','TLX_STUDENT_USER','TLX_STUDENT_PASSWORD','TLX_ADMIN_USER','TLX_ADMIN_PASSWORD','TLX_HOME','NODE_EXTRA_CA_CERTS')
try{
 foreach($kind in @('specialist','student','admin')){
  [Environment]::SetEnvironmentVariable('TLX_'+$kind.ToUpper()+'_USER',$config.Accounts.$kind.Username,'Process')
  [Environment]::SetEnvironmentVariable('TLX_'+$kind.ToUpper()+'_PASSWORD',(Open-DevSecret $config.Accounts.$kind.Password),'Process')
 }
 $env:TLX_HOME=[IO.Path]::GetFullPath((Join-Path $root $EvidenceDirectory))
 $env:NODE_EXTRA_CA_CERTS=Join-Path $devHome 'https-cert.pem'
 & node "$PSScriptRoot/timeline-extended-dev.mjs" $Phase
 $result=$LASTEXITCODE
}finally{foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$null,'Process')}}
exit $result
