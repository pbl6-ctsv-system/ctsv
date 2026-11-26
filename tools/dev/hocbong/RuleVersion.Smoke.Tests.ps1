#requires -Version 7.0
# Execute the real fixture guard in isolation; no DEV config, network, credentials or DB.
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
$taskAstTokens=$null;$taskAstErrors=$null
$taskAst=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'RuleVersion.Smoke.ps1'),[ref]$taskAstTokens,[ref]$taskAstErrors)
if($taskAstErrors.Count){throw 'Smoke script parse failed'}
foreach($taskFunction in $taskAst.FindAll({param($node)$node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -in @('Assert-Hb','Body-Hb','Assert-OwnedHbRule')},$true)){
 . ([scriptblock]::Create($taskFunction.Extent.Text))
}
$taskMarker='SYNTHETIC — T03.3 quy tắc KKHT 26/11/2026';$taskAlternate=$taskMarker+' · đã kiểm sửa'
$taskTemplate=Get-Content -LiteralPath (Join-Path $PSScriptRoot '../../../src/test/resources/fixtures/hocbong/rules/valid-rule.json') -Raw -Encoding utf8 | ConvertFrom-Json
$taskTemplate.legalBasis=$taskMarker
$taskPassed=0
function Copy-Fixture {$taskTemplate | ConvertTo-Json -Depth 30 | ConvertFrom-Json}
function Check-Fixture([string]$Name,[scriptblock]$Check,[bool]$ShouldReject=$false){
 $rejected=$false
 try{& $Check}catch{$rejected=$true;if(-not $ShouldReject){throw}}
 if($rejected -ne $ShouldReject){throw ('FAIL '+$Name+': expected rejected='+$ShouldReject+', got='+$rejected)}
 $script:taskPassed++;Write-Output ($Name+' PASS (isolated PowerShell guard)')
}
Check-Fixture 'HB-RULE-TOOL01-own-marker' {Assert-OwnedHbRule (Copy-Fixture) $taskTemplate $taskMarker $taskAlternate}
Check-Fixture 'HB-RULE-TOOL02-own-alternate' {$detail=Copy-Fixture;$detail.legalBasis=$taskAlternate;Assert-OwnedHbRule $detail $taskTemplate $taskMarker $taskAlternate}
Check-Fixture 'HB-RULE-TOOL03-user-edit-between-list-and-get' {$detail=Copy-Fixture;$detail.legalBasis='User regulation after LIST';Assert-OwnedHbRule $detail $taskTemplate $taskMarker $taskAlternate} $true
Check-Fixture 'HB-RULE-TOOL04-user-changed-threshold' {$detail=Copy-Fixture;$detail.levels.A.academicAverageMin='9.50';Assert-OwnedHbRule $detail $taskTemplate $taskMarker $taskAlternate} $true
Check-Fixture 'HB-RULE-TOOL05-validation-does-not-mutate-detail' {$detail=Copy-Fixture;$detail.legalBasis=$taskAlternate;Assert-OwnedHbRule $detail $taskTemplate $taskMarker $taskAlternate;if($detail.legalBasis -cne $taskAlternate){throw 'Guard modified input'}}
Write-Output ('T03.3 smoke guard: '+$taskPassed+' PASS; no network/DB/config access.')
