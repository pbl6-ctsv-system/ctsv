param([switch]$Create)
$ErrorActionPreference='Stop'
$taskDb='ctsv_signing_support_test_20261125'
$taskMarker='ctsv-signing-support-20261125'
function Connect([string]$database){
 $c=[System.Data.SqlClient.SqlConnection]::new("Server=localhost\DEV2022;Database=$database;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connect Timeout=8")
 $c.Open();return $c
}
function Scalar($connection,[string]$sql){$command=$connection.CreateCommand();$command.CommandText=$sql;return $command.ExecuteScalar()}
$master=Connect 'master'
try{
 if((Scalar $master "SELECT CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName'))") -ne 'DEV2022'){throw 'Wrong SQL instance'}
 $exists=Scalar $master "SELECT COUNT(*) FROM sys.databases WHERE name=N'ctsv_signing_support_test_20261125'"
 if($exists -eq 0){
  if(-not $Create){throw 'Missing approved test DB; pass -Create only with owner authorization'}
  $command=$master.CreateCommand();$command.CommandText='CREATE DATABASE [ctsv_signing_support_test_20261125] COLLATE Vietnamese_100_CI_AS_SC';[void]$command.ExecuteNonQuery()
  $created=$true
 }else{$created=$false}
}finally{$master.Dispose()}
$test=Connect $taskDb
try{
 if((Scalar $test 'SELECT IIF(owner_sid=SUSER_SID() AND compatibility_level=160 AND collation_name=N''Vietnamese_100_CI_AS_SC'',1,0) FROM sys.databases WHERE database_id=DB_ID()') -ne 1){throw 'Test DB owner/compat/collation mismatch'}
 $marker=Scalar $test "SELECT CONVERT(nvarchar(128),value) FROM sys.extended_properties WHERE class=0 AND name=N'signing_support_test_owner'"
 if($created){$command=$test.CreateCommand();$command.CommandText="EXEC sys.sp_addextendedproperty @name=N'signing_support_test_owner',@value=N'ctsv-signing-support-20261125'";[void]$command.ExecuteNonQuery()}
 elseif($marker -ne $taskMarker){throw 'Existing DB marker mismatch; refusing takeover'}
 [ordered]@{database=$taskDb;instance='DEV2022';created=$created;ownerVerified=$true;marker=$taskMarker;action='No migration, reset or cleanup'}|ConvertTo-Json
}finally{$test.Dispose()}
