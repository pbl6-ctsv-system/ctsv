#requires -Version 7.0
# Deliberate DEV-only mutations. Never run this against evidence databases.
[CmdletBinding()]param([string]$SisRoot=(Join-Path $PSScriptRoot '../../../daotao'))
$ErrorActionPreference='Stop'
. "$PSScriptRoot/Dev.Common.ps1"
$root=(Resolve-Path "$PSScriptRoot/../..").Path
$homePath=Get-DevHome $root;$syncHome=Join-Path $homePath 'sync'
$checks=[Collections.Generic.List[object]]::new()
function Assert($ok,$name){if(-not $ok){throw "FAIL: $name"};$checks.Add(@{name=$name;status='PASS'})}
function Command([string]$Action,[string]$Mode='auto'){
    & (Join-Path $root 'dev-sync.ps1') -Action $Action -SisRoot $SisRoot -Mode $Mode
    if($LASTEXITCODE -ne 0){throw "Command failed: $Action"}
}
function Snapshot{Get-Content (Join-Path $syncHome 'status.json') -Raw|ConvertFrom-Json -AsHashtable}
function RestartProvider{Command stop;Command start}
function Get-CheckpointVersion($state,$resource){[long]($state.checkpoints[$resource].Split(':')[-1])}
function CheckEpoch($a,$b){Assert ($a.sourceEpoch -eq $b.sourceEpoch -and $a.sourceSystemId -eq $b.sourceSystemId) 'Source epoch and registry persistent'}
function CheckRows($state,[int]$count){foreach($kind in @('units','ethnicities')){Assert (@($state[$kind]|Where-Object present).Count -eq $count) "$kind live synthetic count=$count"}}
Push-Location $root
try{
    Command status;$initial=Snapshot
    Assert ($initial.bootstrapCounts.units -gt 0 -and $initial.bootstrapCounts.ethnicities -gt 0) 'A: both engines have successful Full bootstrap evidence'
    CheckRows $initial 1
    Assert ($initial.units[0].id.ToString() -ne $initial.units[0].sourceId) 'CTSV mapping uses independent PK (units fixture)'
    # Approved stores retain exports for 15 min, only two slots. Explicit DEV restarts between quick scenarios;
    # no TTL/limit override, checkpoint reset or retry behind the operator's back.
    RestartProvider;Command update;Command sync incremental;$updated=Snapshot
    foreach($r in @('units','ethnicities')){
        Assert ((Get-CheckpointVersion $updated $r) -gt (Get-CheckpointVersion $initial $r)) "B: $r checkpoint advances after UPDATE"
        Assert ($updated[$r][0].ten -eq "SYNTHETIC DEV revision $($updated.fixture.revision)") "B: $r updated label delivered"
    }
    RestartProvider;Command insert;Command sync incremental;$inserted=Snapshot;CheckRows $inserted 2
    foreach($r in @('units','ethnicities')){Assert ((Get-CheckpointVersion $inserted $r) -gt (Get-CheckpointVersion $updated $r)) "C: $r INSERT checkpoint advances"}
    RestartProvider;Command delete;Command sync incremental;$deleted=Snapshot;CheckRows $deleted 1
    foreach($r in @('units','ethnicities')){
        Assert ((Get-CheckpointVersion $deleted $r) -gt (Get-CheckpointVersion $inserted $r)) "C: $r DELETE checkpoint advances"
        Assert (@($deleted[$r]|Where-Object {-not $_.present}).Count -eq (@($initial[$r]|Where-Object {-not $_.present}).Count+1)) "C: $r delete preserves mapped history as absent"
    }
    Command stop;Command start;Command status;$restarted=Snapshot
    CheckEpoch $deleted $restarted
    Assert (($deleted.checkpoints|ConvertTo-Json -Compress) -eq ($restarted.checkpoints|ConvertTo-Json -Compress)) 'D: restart does not run sync/change checkpoints'
    Command sync incremental;$afterRestart=Snapshot;CheckEpoch $deleted $afterRestart
    Assert ($afterRestart.runs[0].mode -eq 'incremental' -and $afterRestart.ctRuns[0].mode -eq 'incremental') 'D: both engines remain incremental after restart'
    Command stop;Command setup;Command status;$second=Snapshot
    CheckEpoch $afterRestart $second;CheckRows $second 1
    Assert (($afterRestart.checkpoints|ConvertTo-Json -Compress) -eq ($second.checkpoints|ConvertTo-Json -Compress)) 'E: repeated setup preserves checkpoints'
    Assert (($afterRestart.fixture|ConvertTo-Json -Compress) -eq ($second.fixture|ConvertTo-Json -Compress)) 'E: repeated setup preserves fixture IDs/revision'
    Assert ($second.registryCount -eq 1) 'E: one source registry only'
    Assert ($second.migrations.Count -eq 5 -and @($second.migrations|Where-Object {-not $_.success}).Count -eq 0) 'SIS V1-V5 migration successful'
    Assert ($second.options[0].snapshot_state -eq 'ON' -and $second.tracking[0].retention_period -eq 90) 'SNAPSHOT ON / CT retention 90 days'
    @{date=(Get-Date).ToUniversalTime().ToString('o');transport='REAL HTTPS + SQL Server';databases=@('sis_dev','ctsv_dev');checks=$checks.ToArray();before=$initial;after=$second}|ConvertTo-Json -Depth 15|Set-Content (Join-Path $syncHome 'smoke-manifest.json')
    Write-Output "Sync real smoke: $($checks.Count) PASS; private sync/smoke-manifest.json. Provider stopped; regular dev launcher unchanged."
}finally{Pop-Location}
