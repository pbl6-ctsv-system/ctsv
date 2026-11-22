#requires -Version 7.0
param([string]$Mode='verify')
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
. "$root/tools/dev/Dev.Common.ps1"
$devHome=Get-DevHome $root;$c=Read-DevConfig $devHome
try{
 foreach($kind in @('specialist','student')){
  [Environment]::SetEnvironmentVariable('RF_'+$kind.ToUpper()+'_USER',$c.Accounts.$kind.Username,'Process')
  [Environment]::SetEnvironmentVariable('RF_'+$kind.ToUpper()+'_PASSWORD',(Open-DevSecret $c.Accounts.$kind.Password),'Process')
 }
 $env:NODE_EXTRA_CA_CERTS=Join-Path $devHome 'https-cert.pem'
 & node "$PSScriptRoot/round-form-dev.mjs" $Mode
 $code=$LASTEXITCODE
}finally{foreach($name in @('RF_SPECIALIST_USER','RF_SPECIALIST_PASSWORD','RF_STUDENT_USER','RF_STUDENT_PASSWORD','NODE_EXTRA_CA_CERTS')){[Environment]::SetEnvironmentVariable($name,$null,'Process')}}
exit $code
