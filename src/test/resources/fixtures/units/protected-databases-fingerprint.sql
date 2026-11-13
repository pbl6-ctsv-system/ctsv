SET NOCOUNT ON;
IF CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName'))<>N'DEV2022'
 OR DB_NAME() NOT IN(N'sis_test_20261111',N'ctsv_test_20261111')
 THROW 51092,N'Unexpected protected database fingerprint target.',1;
CREATE TABLE #fingerprint(table_name nvarchar(300),row_count bigint,data_hash varchar(64));
DECLARE @schema sysname,@table sysname,@order nvarchar(max),@sql nvarchar(max),@label nvarchar(300);
DECLARE c CURSOR LOCAL FAST_FORWARD FOR
 SELECT SCHEMA_NAME(schema_id),name FROM sys.tables WHERE is_ms_shipped=0 ORDER BY SCHEMA_NAME(schema_id),name;
OPEN c;FETCH NEXT FROM c INTO @schema,@table;
WHILE @@FETCH_STATUS=0
BEGIN
 SELECT @order=STRING_AGG(CONVERT(nvarchar(max),QUOTENAME(col.name)),N',') WITHIN GROUP(ORDER BY ic.key_ordinal)
 FROM sys.indexes ix JOIN sys.index_columns ic ON ic.object_id=ix.object_id AND ic.index_id=ix.index_id
 JOIN sys.columns col ON col.object_id=ic.object_id AND col.column_id=ic.column_id
 WHERE ix.object_id=OBJECT_ID(QUOTENAME(@schema)+N'.'+QUOTENAME(@table)) AND ix.is_primary_key=1 AND ic.key_ordinal>0;
 IF @order IS NULL THROW 51093,N'Cannot deterministically fingerprint a table without a primary key.',1;
 SET @label=QUOTENAME(@schema)+N'.'+QUOTENAME(@table);
 SET @sql=N'DECLARE @json nvarchar(max)=(SELECT * FROM '+@label+N' ORDER BY '+@order+
 N' FOR JSON PATH,INCLUDE_NULL_VALUES); INSERT #fingerprint SELECT @label,COUNT_BIG(*),CONVERT(varchar(64),HASHBYTES(''SHA2_256'',CONVERT(varbinary(max),@json)),2) FROM '+@label+N';';
 EXEC sys.sp_executesql @sql,N'@label nvarchar(300)',@label;
 FETCH NEXT FROM c INTO @schema,@table;
END;
CLOSE c;DEALLOCATE c;
SELECT DB_NAME() AS database_name,table_name,row_count,data_hash FROM #fingerprint ORDER BY table_name;
