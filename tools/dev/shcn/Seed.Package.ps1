#requires -Version 7.0
Set-StrictMode -Version Latest

function Get-ShcnTextHash([string]$File){
 $text=[IO.File]::ReadAllText($File).Replace("`r`n","`n").Replace("`r","`n")
 $sha=[Security.Cryptography.SHA256]::Create()
 try{return [Convert]::ToHexString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($text)))}finally{$sha.Dispose()}
}

function Read-ShcnSeedPackage([string]$Directory){
 $root=[IO.Path]::GetFullPath($Directory)
 $manifestFile=Join-Path $root 'seed-manifest.json'
 if(-not(Test-Path -LiteralPath $manifestFile -PathType Leaf)){throw 'SHCN seed manifest missing'}
 $manifest=Get-Content -LiteralPath $manifestFile -Raw|ConvertFrom-Json -AsHashtable
 if($manifest.schemaVersion -ne 1 -or $manifest.packageId -ne 'ctsv.shcn.dev' -or $manifest.version -notmatch '^\d+\.\d+\.\d+$'){throw 'Invalid SHCN seed package identity/version'}
 if($manifest.sourceCode -cne 'DEV-SIS-SYNC'){throw 'Unexpected DEV SIS source code'}
 $codes=@('FacultyA','FacultyB','ProgramA','ProgramB','ClassA1','ClassA2','ClassB','StaffGV','StaffHead','StaffOther')
 if(@($manifest.fixtureCodes.Keys).Count -ne $codes.Count){throw 'Invalid fixture code inventory'}
 foreach($code in $codes){if(-not $manifest.fixtureCodes.ContainsKey($code) -or $manifest.fixtureCodes[$code] -notmatch '^DEV-SHCN-[A-Za-z0-9-]{1,23}$'){throw 'Fixture codes must stay in the owned DEV namespace'}}
 $required=@('Seed.Package.ps1','dev-shcn.ps1','shcn.policy.json','RBAC_INSPECT.sql')
 if(@($manifest.files.Keys).Count -ne $required.Count){throw 'Invalid SHCN seed file inventory'}
 foreach($name in $required){if(-not $manifest.files.ContainsKey($name)){throw 'Missing SHCN seed package file'}}
 foreach($name in $manifest.files.Keys){
  if($name -notmatch '^[A-Za-z0-9_.-]+$' -or $name.Contains('..')){throw 'Unsafe SHCN seed file path'}
  $file=[IO.Path]::GetFullPath((Join-Path $root $name))
  if(-not $file.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or -not(Test-Path -LiteralPath $file -PathType Leaf)){throw 'SHCN seed file outside package or missing'}
  if((Get-ShcnTextHash $file) -cne $manifest.files[$name]){throw "SHCN seed package checksum mismatch: $name"}
 }
 return @{Manifest=$manifest;ManifestSHA256=(Get-ShcnTextHash $manifestFile)}
}

function New-ShcnDevSettings($Package){
 $settings=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'shcn.policy.json') -Raw|ConvertFrom-Json -AsHashtable
 # Role/permission comes from shared DB tables; local settings cannot grant SHCN capability.
 if($settings['ctsv.shcn.phan-cong-khoa.staff-code-column'] -cne 'MA_CB'){throw 'Seed mapping must preserve MaHS -> staff_code -> ma_cb'}
 if($settings.ContainsKey('ctsv.shcn.phan-cong-khoa.prepare-role-codes') -or $settings.ContainsKey('ctsv.shcn.phan-cong-khoa.read-role-codes')){throw 'Role-list authorization is retired; use database permissions'}
 return $settings
}

function Assert-ShcnSeedRecord($Package,$Record,[bool]$AllowMissing=$false){
 if(-not $Record){if($AllowMissing){return $true};throw 'SHCN seed version not recorded in both databases; run seed first'}
 if($Record.PackageId -cne $Package.Manifest.packageId -or $Record.Version -cne $Package.Manifest.version -or $Record.ManifestSHA256 -cne $Package.ManifestSHA256 -or $Record.SourceCode -cne $Package.Manifest.sourceCode){throw 'SHCN seed version/checksum differs from the database. No automatic upgrade; use the reviewed version upgrade procedure.'}
 return $true
}

function Assert-ShcnUpgradeRecord($Package,$Record){
 if(-not $Record){throw 'No legacy seed package record to upgrade; use seed for a fresh DEV database'}
 if($Record.PackageId -cne $Package.Manifest.packageId -or $Record.SourceCode -cne $Package.Manifest.sourceCode){throw 'Foreign seed package/source; no upgrade'}
 if($Record.Version -ceq $Package.Manifest.version){return (Assert-ShcnSeedRecord $Package $Record)}
 foreach($from in $Package.Manifest.upgradesFrom){if($Record.Version -ceq $from.version -and $Record.ManifestSHA256 -ceq $from.manifestSHA256){return $true}}
 throw 'Unsupported seed version/hash; no automatic upgrade'
}
