#requires -Version 7.0
[CmdletBinding()]
param([switch]$Restart)
$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
. (Join-Path $taskRoot 'tools/dev/Dev.Common.ps1')
$taskHome=Get-DevHome $taskRoot
$taskSettings=Get-Content -LiteralPath (Join-Path $taskHome 'shcn.settings.json') -Raw|ConvertFrom-Json -AsHashtable
$taskSource=$taskSettings['ctsv.shcn.dot.source-code']
if(-not $taskSource){$taskSource=$taskSettings['ctsv.shcn.phan-cong-khoa.source-code']}
if($taskSource -cne 'DEV-SIS-SYNC'){throw 'Expected reviewed DEV-SIS-SYNC source in SHCN local settings'}
# Configure only the SHCN semester source. This command never seeds or migrates.
$previous=[Environment]::GetEnvironmentVariable('SPRING_APPLICATION_JSON','Process')
$combined=if($previous){$previous|ConvertFrom-Json -AsHashtable}else{@{}}
$combined['ctsv.shcn.dot.source-code']=$taskSource
try{
 $global:LASTEXITCODE=0
 if($Restart){& (Join-Path $taskRoot 'dev.ps1') stop;if($LASTEXITCODE -ne 0){throw 'DEV stop failed'}}
 $env:SPRING_APPLICATION_JSON=$combined|ConvertTo-Json -Depth 8 -Compress
 & (Join-Path $taskRoot 'dev.ps1') start
 if($LASTEXITCODE -ne 0){throw 'DEV start failed'}
}finally{[Environment]::SetEnvironmentVariable('SPRING_APPLICATION_JSON',$previous,'Process')}
