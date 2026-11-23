$ErrorActionPreference='Stop'
. "$PSScriptRoot/Dev.Common.ps1"
$fixture=Join-Path ([IO.Path]::GetTempPath()) ('ctsv-ca-test-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $fixture|Out-Null
try{
 $empty=Get-DevCaEnvironment $fixture
 if($empty.Count -ne 0){throw 'Missing config must stay opt-in'}
 $credential=[pscredential]::new('synthetic-client',(ConvertTo-SecureString 'synthetic-test-secret' -AsPlainText -Force))
 $credential|Export-Clixml -LiteralPath (Join-Path $fixture 'credential.clixml')
 $actual=Get-DevCaEnvironment $fixture
 if($actual.CA_DUT_CLIENT_ID -ne 'synthetic-client' -or $actual.CA_DUT_CLIENT_SECRET -ne 'synthetic-test-secret' -or $actual.CA_DUT_URL -ne 'https://chukyso.dut.udn.vn'){throw 'DPAPI credential load failed'}
 Write-Output 'PASS 2: CA config absent stays disabled; DPAPI config loads into child environment only.'
}finally{
 # Remove only the explicitly created fixture file and its empty directory, no recursion.
 $path=Join-Path $fixture 'credential.clixml';if(Test-Path -LiteralPath $path){Remove-Item -LiteralPath $path};Remove-Item -LiteralPath $fixture
}
