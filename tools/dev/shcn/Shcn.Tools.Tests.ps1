#requires -Version 7.0
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Seed.Package.ps1')
$taskPassed=0
function Check([bool]$Condition,[string]$Message){if(-not $Condition){throw $Message};$script:taskPassed++}
function Must-Reject([scriptblock]$Action,[string]$Message){$rejected=$false;try{[void](&$Action)}catch{$rejected=$true};Check $rejected $Message}
$package=Read-ShcnSeedPackage $PSScriptRoot
Check ($package.Manifest.version -eq '1.1.0') 'Expected DB-permission seed version'
$policy=New-ShcnDevSettings $package
Check ($policy['ctsv.shcn.phan-cong-khoa.staff-code-column'] -ceq 'MA_CB') 'Pinned MaHS mapping'
Check (-not $policy.ContainsKey('ctsv.shcn.phan-cong-khoa.prepare-role-codes')) 'Configuration cannot grant prepare'
Check (Assert-ShcnSeedRecord $package $null $true) 'Fresh seed may initialize its version record'
Must-Reject {Assert-ShcnSeedRecord $package $null} 'Accounts/start must reject missing DB package version'
$record=@{PackageId=$package.Manifest.packageId;Version=$package.Manifest.version;ManifestSHA256=$package.ManifestSHA256;SourceCode=$package.Manifest.sourceCode}
Check (Assert-ShcnSeedRecord $package $record) 'Matching DB package version/checksum accepted'
foreach($field in @('Version','ManifestSHA256','SourceCode')){
 $badRecord=$record.Clone();$badRecord[$field]='wrong'
 Must-Reject {Assert-ShcnSeedRecord $package $badRecord} "DB package mismatch must stop: $field"
}
$legacy=@{PackageId=$package.Manifest.packageId;Version='1.0.0';ManifestSHA256='3F86E56BDCE0F0A398C5E5A3A4FFE7F6B5208F707DC9C3A1488B63F38A584649';SourceCode=$package.Manifest.sourceCode}
Check (Assert-ShcnUpgradeRecord $package $legacy) 'Reviewed legacy v1.0.0 accepted by explicit upgrade'
Must-Reject {Assert-ShcnSeedRecord $package $legacy} 'Normal seed/start cannot silently upgrade'
$wrongLegacy=$legacy.Clone();$wrongLegacy.ManifestSHA256='wrong'
Must-Reject {Assert-ShcnUpgradeRecord $package $wrongLegacy} 'Unknown legacy checksum cannot upgrade'
Check (Assert-ShcnUpgradeRecord $package $record) 'Already upgraded database supports resume/no-op'
foreach($name in @('dev-shcn.ps1','Seed.Package.ps1','Shcn.Tools.Tests.ps1')){
 $errors=$null;[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $name),[ref]$null,[ref]$errors)|Out-Null
 Check ($errors.Count -eq 0) "PowerShell syntax: $name"
}
$scriptText=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'dev-shcn.ps1') -Raw
Check (-not $scriptText.Contains('G:\26-27')) 'No author machine drive/path'
Check (-not $scriptText.Contains('Miu Miu')) 'No author username'
& (Join-Path $PSHOME 'pwsh.exe') -NoProfile -File (Join-Path $PSScriptRoot 'dev-shcn.ps1') check-package -ProjectRoot (Join-Path ([IO.Path]::GetTempPath()) 'shcn-no-dev-project-needed')
Check ($LASTEXITCODE -eq 0) 'Package validation must not need a project, database, registry or credentials'
$temp=Join-Path ([IO.Path]::GetTempPath()) ('shcn-package-tests-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $temp|Out-Null
$names=@($package.Manifest.files.Keys)+@('seed-manifest.json')
try{
 foreach($name in $names){Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $temp $name)}
 $manifest=Join-Path $temp 'seed-manifest.json'
 $script=Join-Path $temp 'dev-shcn.ps1'
 [IO.File]::AppendAllText($script,"`n# modified file`n")
 Must-Reject {Read-ShcnSeedPackage $temp} 'Changed seed content must fail checksum'
 Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'dev-shcn.ps1') -Destination $script -Force
 $lf=[IO.File]::ReadAllText($script).Replace("`r`n","`n");[IO.File]::WriteAllText($script,$lf.Replace("`n","`r`n"))
 Check ((Read-ShcnSeedPackage $temp).ManifestSHA256 -ceq $package.ManifestSHA256) 'Git LF/CRLF conversion must not change the package identity'
 $bad=Get-Content -LiteralPath $manifest -Raw|ConvertFrom-Json -AsHashtable;$bad.files.Remove('dev-shcn.ps1');$bad.files['../outside.ps1']='0'*64
 $bad|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $manifest -Encoding utf8
 Must-Reject {Read-ShcnSeedPackage $temp} 'Manifest path traversal must fail'
 Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'seed-manifest.json') -Destination $manifest -Force
 $bad=Get-Content -LiteralPath $manifest -Raw|ConvertFrom-Json -AsHashtable;$bad.version='not-a-version';$bad|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $manifest -Encoding utf8
 Must-Reject {Read-ShcnSeedPackage $temp} 'Malformed seed version must fail'
}finally{
 # Only known files in the exact temporary directory are removed; no recursive deletion.
 foreach($name in $names){$file=Join-Path $temp $name;if(Test-Path -LiteralPath $file){Remove-Item -LiteralPath $file}}
 Remove-Item -LiteralPath $temp
}
Write-Output "SHCN seed package checks passed: $taskPassed; no SQL/Auth calls."
