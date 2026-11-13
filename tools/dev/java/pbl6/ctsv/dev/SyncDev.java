package pbl6.ctsv.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import pbl6.ctsv.foundation.sync.*;
import pbl6.ctsv.foundation.units.client.SisUnitsClient;
import pbl6.ctsv.foundation.units.mapper.UnitSourceMapper;
import pbl6.ctsv.foundation.units.repository.UnitSyncRepository;
import pbl6.ctsv.foundation.units.service.*;

/** Explicit development command, compiled outside the application. No new API, bean or sync engine. */
public final class SyncDev {
    static final ObjectMapper JSON=new ObjectMapper();
    static final String SOURCE="DEV-SIS-SYNC", MARKER="sis-local-dev-v1";
    static Properties p;
    static Path home;
    static String stage="config";
    static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    public static void main(String[] args){
        try{run(args);}catch(Exception e){
            // Do not print arbitrary SQL/HTTP messages, credentials, tokens or connection strings.
            System.err.println("Sync DEV failed at "+stage+" ["+e.getClass().getSimpleName()+"]. No fallback/reset. Inspect target, permissions and recovery runbook.");
            for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof SQLException s)System.err.println("SQL code="+s.getErrorCode()+", state="+s.getSQLState());
            if(e instanceof CtHttpClient.RemoteFailure r)System.err.println("Provider status="+r.status+", code="+r.code);
            if(e.getMessage()!=null&&Set.of("SOURCE_EPOCH_CHANGED","BASELINE_CONFIRMATION_REQUIRED","SYNC_BUSY","SOURCE_LINEAGE_INVALID").contains(e.getMessage()))System.err.println(e.getMessage());
            System.exit(1);
        }
    }
    static void run(String[] args)throws Exception{
        require(args.length>=1&&Set.of("setup","check","status","sync","update","insert","delete").contains(args[0]),"mode");
        home=Path.of(Objects.requireNonNull(System.getenv("CTSV_SYNC_HOME")));
        p=new Properties();try(var in=Files.newInputStream(home.resolve("sync.properties"))){p.load(in);}
        boolean setup=args[0].equals("setup");
        stage="SIS target";var sis=open("sis_dev",setup);
        stage="CTSV target";var ctsv=open("ctsv_dev",false);
        if(setup){
            stage="SIS approved migrations";
            String location="filesystem:"+p.getProperty("sis.root").replace('\\','/')+"/src/main/resources/db/migration";
            var flyway=Flyway.configure().dataSource(sis).locations(location).cleanDisabled(true).baselineOnMigrate(false).load();
            var j=new JdbcTemplate(sis);
            boolean history=j.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE name='flyway_schema_history'",Integer.class)==1;
            if(history)flyway.validate();
            else require(j.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",Integer.class)==0,"nonempty without history");
            flyway.migrate();flyway.validate();require(flyway.info().pending().length==0,"pending migrations");
            stage="persistent source epoch";
            var old=property(sis,"sis_dev_epoch");
            if(old==null)setProperty(sis,"sis_dev_epoch",p.getProperty("source.epoch"));
            else require(old.equals(p.getProperty("source.epoch")),"epoch mismatch; do not rebind");
            stage="synthetic fixtures";fixtures(sis,"setup");
            stage="CTSV source registry";source(ctsv,true);
            stage="Java leaf trust";trust();
        }else{
            stage="SIS history/options check";
            var flyway=Flyway.configure().dataSource(sis).locations("filesystem:"+p.getProperty("sis.root").replace('\\','/')+"/src/main/resources/db/migration").cleanDisabled(true).baselineOnMigrate(false).load();
            flyway.validate();require(flyway.info().pending().length==0,"pending migration; explicit setup required");
            var j=new JdbcTemplate(sis);
            require(j.queryForObject("SELECT COUNT(*) FROM sys.databases WHERE database_id=DB_ID() AND snapshot_isolation_state=1",Integer.class)==1,"SNAPSHOT required");
            require(j.queryForObject("SELECT COUNT(*) FROM sys.change_tracking_databases WHERE database_id=DB_ID() AND is_auto_cleanup_on=1",Integer.class)==1,"CT required");
            require(j.queryForObject("SELECT COUNT(*) FROM sys.change_tracking_tables WHERE object_id IN(OBJECT_ID(N'dbo.DonVi'),OBJECT_ID(N'dbo.DanToc'))",Integer.class)==2,"fixture tables must be tracked");
            require(p.getProperty("source.epoch").equals(property(sis,"sis_dev_epoch")),"epoch mismatch");
            if(Set.of("update","insert","delete").contains(args[0])){stage="owned synthetic mutation";fixtures(sis,args[0]);}
            if(args[0].equals("sync")){
                require(args.length==3&&Set.of("units","ethnicities","all").contains(args[1])&&Set.of("auto","full","incremental").contains(args[2]),"sync arguments");
                stage="source registry";long id=source(ctsv,false);
                System.setProperty("javax.net.ssl.trustStore",home.resolve("sis-trust.p12").toString());
                System.setProperty("javax.net.ssl.trustStoreType","PKCS12");
                System.setProperty("javax.net.ssl.trustStorePassword",p.getProperty("trust.password"));
                for(String resource:args[1].equals("all")?List.of("units","ethnicities"):List.of(args[1])){
                    stage="sync "+resource;
                    boolean has=checkpoint(ctsv,id,resource)!=null;
                    require(!args[2].equals("full")||!has,"full is bootstrap only; checkpoint already exists");
                    require(!args[2].equals("incremental")||has,"incremental requires successful bootstrap");
                    if(resource.equals("units")){
                        try(var client=new SisUnitsClient(URI.create(p.getProperty("provider.url")),p.getProperty("service.key"),100)){
                            var service=new UnitSyncService(new UnitSyncGuard(ctsv,"ctsv_dev","DEV2022"),new UnitSyncRepository(new UnitSourceMapper()),client,id,100);
                            var result=service.sync();System.out.println("units "+(has?"incremental":"full")+" OK rows="+result.processed()+" checkpoint="+result.checkpoint());
                        }
                    }else{
                        var profile=profile(resource);int pageSize=profile.path("defaultLimit").asInt();long maxRows=profile.path("maxRows").asLong();
                        require(pageSize>0&&maxRows>0,"pinned resource limits");
                        try(var client=new CtHttpClient(URI.create(p.getProperty("provider.url")),resource,p.getProperty("service.key"),pageSize)){
                            var result=new CtSyncService(new DurableSyncStore(ctsv,"ctsv_dev","DEV2022"),client,new JdbcFoundationTarget(resource),id,pageSize,maxRows).sync();
                            System.out.println(resource+" "+(has?"incremental":"full")+" OK rows="+result.processed()+" checkpoint="+result.epoch()+":"+result.version());
                        }
                    }
                }
            }
        }
        stage="metadata/snapshot";snapshot(sis,ctsv,args[0]);
        System.out.println("DEV targets verified: localhost\\DEV2022 / sis_dev + ctsv_dev. Mode="+args[0]);
    }
    static com.fasterxml.jackson.databind.JsonNode profile(String resource)throws Exception{
        try(var in=SyncDev.class.getResourceAsStream("/contracts/ct26-resources.json")){
            for(var row:JSON.readTree(in))if(row.path("resource").asText().equals(resource))return row.path("profile");
        }
        throw new IllegalArgumentException("Unknown pinned resource profile");
    }
    static DataSource open(String db,boolean create)throws Exception{
        require(Set.of("sis_dev","ctsv_dev").contains(db),"target allowlist");
        String url=p.getProperty(db.equals("sis_dev")?"sis.jdbc.url":"ctsv.jdbc.url");
        require(url!=null&&url.matches("jdbc:sqlserver://localhost:[0-9]+;databaseName="+db+";integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5;connectRetryCount=0"),"exact local target");
        // Verify server before CREATE; never fallback to a test database.
        try(var c=DriverManager.getConnection(url.replace("databaseName="+db+";","databaseName=master;"));var s=c.createStatement();var r=s.executeQuery("SELECT CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')),CONVERT(int,SERVERPROPERTY('ProductMajorVersion')),DB_ID(N'"+db+"')")){
            require(r.next()&&"DEV2022".equals(r.getString(1))&&r.getInt(2)==16,"server identity");
            if(r.getObject(3)==null){require(create&&db.equals("sis_dev"),"missing DB; run CTSV setup first");r.close();s.execute("CREATE DATABASE [sis_dev] COLLATE Vietnamese_100_CI_AS_SC");}
        }
        var ds=new SimpleDriverDataSource(new com.microsoft.sqlserver.jdbc.SQLServerDriver(),url,new Properties());
        var j=new JdbcTemplate(ds);
        var rows=j.queryForList("SELECT DB_NAME() db,CONVERT(nvarchar(128),SERVERPROPERTY('InstanceName')) instance,SUSER_SNAME(owner_sid) owner,SUSER_SNAME() login,compatibility_level compat,collation_name collation FROM sys.databases WHERE database_id=DB_ID()");
        verifyIdentity(db,rows.getFirst());
        String name=db.equals("sis_dev")?"sis_dev_owner":"ctsv_dev_owner";
        String expected=db.equals("sis_dev")?MARKER:"ctsv-local-dev-v1";
        String marker=property(ds,name);
        if(marker==null){require(create&&db.equals("sis_dev")&&j.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped=0",Integer.class)==0,"unmanaged DB");setProperty(ds,name,expected);}
        else require(expected.equals(marker),"managed marker");
        return ds;
    }
    static void verifyIdentity(String db,Map<String,Object> row){
        require(Set.of("sis_dev","ctsv_dev").contains(db)&&db.equals(row.get("db"))&&"DEV2022".equals(row.get("instance"))&&Objects.equals(row.get("owner"),row.get("login"))&&row.get("compat") instanceof Number n&&n.intValue()==160&&"Vietnamese_100_CI_AS_SC".equals(row.get("collation")),"target identity/owner/options");
    }
    static String property(DataSource ds,String name){
        var rows=new JdbcTemplate(ds).queryForList("SELECT CONVERT(nvarchar(max),value) v FROM sys.extended_properties WHERE class=0 AND name=?",name);
        return rows.isEmpty()?null:(String)rows.getFirst().get("v");
    }
    static void setProperty(DataSource ds,String name,String value){
        var j=new JdbcTemplate(ds);j.update(property(ds,name)==null?"EXEC sys.sp_addextendedproperty @name=?,@value=?":"EXEC sys.sp_updateextendedproperty @name=?,@value=?",name,value);
    }
    static long source(DataSource ds,boolean setup){
        var j=new JdbcTemplate(ds);
        var rows=j.queryForList("SELECT id,url_goc,loai_he_thong,che_do_ket_noi,dang_hoat_dong FROM dbo.he_thong_ngoai WHERE ma=?",SOURCE);
        require(rows.size()<=1,"duplicate source registry");
        if(rows.isEmpty()){
            require(setup,"source registry missing");
            j.update("INSERT dbo.he_thong_ngoai(ma,ten,loai_he_thong,che_do_ket_noi,url_goc,tham_chieu_thong_tin_xac_thuc,dang_hoat_dong,thoi_diem_tao,thoi_diem_cap_nhat) VALUES(?,N'SIS DEV SYNTHETIC',N'SIS',N'API',?,N'local:sync/service-key',1,SYSUTCDATETIME(),SYSUTCDATETIME())",SOURCE,p.getProperty("provider.url"));
            return source(ds,false);
        }
        var r=rows.getFirst();require(p.getProperty("provider.url").equals(r.get("url_goc"))&&"SIS".equals(r.get("loai_he_thong"))&&Boolean.TRUE.equals(r.get("dang_hoat_dong")),"source registry conflict; no overwrite");
        return ((Number)r.get("id")).longValue();
    }
    @SuppressWarnings("unchecked") static void fixtures(DataSource ds,String mode)throws Exception{
        var j=new JdbcTemplate(ds);String raw=property(ds,"sis_dev_fixture_v1");
        Map<String,Object> ids=raw==null?new LinkedHashMap<>():JSON.readValue(raw,LinkedHashMap.class);
        // Each unit of fixture work and its ownership ledger commit together.
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        tx.executeWithoutResult(t->{
            if(raw==null){require(mode.equals("setup"),"fixture ownership missing");ids.put("revision",0);}
            int nextGeneration=((Number)ids.getOrDefault("extraGeneration",0)).intValue()+1;
            for(String resource:List.of("units","ethnicities")){
                String table=resource.equals("units")?"DonVi":"DanToc",pk=resource.equals("units")?"ID":"DanTocID",code=resource.equals("units")?"MaDV":"MaDanToc",label=resource.equals("units")?"TenDV":"TenDanToc";
                String tag=resource.equals("units")?"DEV-SYNC-UNIT":"DEV-SYNC-DT";
                if(mode.equals("setup")){
                    if(!ids.containsKey(resource)){
                        require(j.queryForObject("SELECT COUNT(*) FROM dbo."+table+" WHERE "+code+" IN (?,?)",Integer.class,tag,tag+"-X")==0,"fixture code collision");
                        ids.put(resource,insert(j,table,pk,code,label,tag));
                    }else owned(j,table,pk,code,ids.get(resource),tag);
                }else if(mode.equals("update")){
                    owned(j,table,pk,code,ids.get(resource),tag);
                    j.update("UPDATE dbo."+table+" SET "+label+"=? WHERE "+pk+"=?","SYNTHETIC DEV revision "+(((Number)ids.get("revision")).intValue()+1),ids.get(resource));
                }else if(mode.equals("insert")){
                    if(!ids.containsKey(resource+"Extra")){
                        // A deleted source identity keeps its local row/history. Never reuse its unique business code for a new ID.
                        String extraTag=tag+"-X-"+nextGeneration;
                        require(j.queryForObject("SELECT COUNT(*) FROM dbo."+table+" WHERE "+code+"=?",Integer.class,extraTag)==0,"unowned extra fixture");
                        ids.put(resource+"Extra",insert(j,table,pk,code,label,extraTag));ids.put(resource+"ExtraCode",extraTag);ids.put("extraGeneration",nextGeneration);
                    }else owned(j,table,pk,code,ids.get(resource+"Extra"),ids.getOrDefault(resource+"ExtraCode",tag+"-X").toString());
                }else if(mode.equals("delete")&&ids.containsKey(resource+"Extra")){
                    String extraTag=ids.getOrDefault(resource+"ExtraCode",tag+"-X").toString();
                    owned(j,table,pk,code,ids.get(resource+"Extra"),extraTag);
                    require(j.update("DELETE FROM dbo."+table+" WHERE "+pk+"=? AND "+code+"=?",ids.get(resource+"Extra"),extraTag)==1,"fixture delete");ids.remove(resource+"Extra");ids.remove(resource+"ExtraCode");
                }
            }
            if(mode.equals("update"))ids.put("revision",((Number)ids.get("revision")).intValue()+1);
            try{setProperty(ds,"sis_dev_fixture_v1",JSON.writeValueAsString(ids));}catch(Exception e){throw new IllegalStateException("fixture ledger",e);}
        });
    }
    static long insert(JdbcTemplate j,String table,String pk,String code,String label,String tag){
        return j.queryForObject("DECLARE @ids TABLE(id bigint); INSERT dbo."+table+"("+code+","+label+") OUTPUT INSERTED."+pk+" INTO @ids VALUES(?,?); SELECT id FROM @ids",Long.class,tag,"SYNTHETIC DEV fixture");
    }
    static void owned(JdbcTemplate j,String table,String pk,String code,Object id,String tag){require(id!=null&&j.queryForObject("SELECT COUNT(*) FROM dbo."+table+" WITH(UPDLOCK,HOLDLOCK) WHERE "+pk+"=? AND "+code+"=?",Integer.class,id,tag)==1,"owned fixture changed or missing");}
    static void trust()throws Exception{
        var store=KeyStore.getInstance("PKCS12");store.load(null,p.getProperty("trust.password").toCharArray());
        try(var in=Files.newInputStream(Path.of(p.getProperty("certificate")))){store.setCertificateEntry("own-local-dev-leaf",CertificateFactory.getInstance("X.509").generateCertificate(in));}
        try(var out=Files.newOutputStream(home.resolve("sis-trust.p12"))){store.store(out,p.getProperty("trust.password").toCharArray());}
    }
    static String checkpoint(DataSource ds,long id,String resource)throws SQLException{
        if(resource.equals("units")){try(var c=ds.getConnection()){return new UnitSyncRepository(new UnitSourceMapper()).checkpoint(c,id);}}
        var rows=new JdbcTemplate(ds).queryForList("SELECT CONVERT(varchar(36),source_epoch)+':'+CONVERT(varchar(20),ct_version) cp FROM dbo.dong_bo_trang_thai WHERE he_thong_nguon_id=? AND tai_nguyen=? AND ct_version IS NOT NULL",id,resource);
        return rows.isEmpty()?null:(String)rows.getFirst().get("cp");
    }
    static void snapshot(DataSource sis,DataSource ctsv,String mode)throws Exception{
        var out=new LinkedHashMap<String,Object>();var s=new JdbcTemplate(sis);var c=new JdbcTemplate(ctsv);long id=source(ctsv,false);
        out.put("mode",mode);out.put("server","localhost\\DEV2022");out.put("databases",List.of("sis_dev","ctsv_dev"));out.put("sourceSystemId",id);out.put("sourceEpoch",property(sis,"sis_dev_epoch"));
        out.put("fixture",JSON.readTree(property(sis,"sis_dev_fixture_v1")));
        out.put("options",s.queryForList("SELECT snapshot_isolation_state_desc snapshot_state,is_read_committed_snapshot_on rcsi FROM sys.databases WHERE database_id=DB_ID()"));
        out.put("tracking",s.queryForList("SELECT retention_period,retention_period_units_desc,is_auto_cleanup_on FROM sys.change_tracking_databases WHERE database_id=DB_ID()"));
        out.put("trackedTables",s.queryForObject("SELECT COUNT(*) FROM sys.change_tracking_tables",Integer.class));
        out.put("migrations",s.queryForList("SELECT version,checksum,success FROM dbo.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank"));
        var checkpoints=new LinkedHashMap<String,String>();for(var r:List.of("units","ethnicities"))checkpoints.put(r,checkpoint(ctsv,id,r));out.put("checkpoints",checkpoints);
        out.put("units",c.queryForList("SELECT id,ma,ten,ma_dinh_danh_ben_ngoai sourceId,dang_hoat_dong_nguon present FROM dbo.don_vi WHERE he_thong_nguon_id=? ORDER BY id",id));
        out.put("ethnicities",c.queryForList("SELECT id,ma,ten,ma_dinh_danh_ben_ngoai sourceId,dang_hien_dien_nguon present FROM dbo.dan_toc WHERE he_thong_nguon_id=? ORDER BY id",id));
        out.put("runs",c.queryForList("SELECT TOP(8) che_do_dong_bo mode,trang_thai status,con_tro_truoc beforeCheckpoint,con_tro_sau afterCheckpoint FROM dbo.dot_dong_bo WHERE he_thong_nguon_id=? ORDER BY id DESC",id));
        out.put("ctRuns",c.queryForList("SELECT TOP(8) che_do mode,trang_thai status,tu_version lowerVersion,den_version upperVersion FROM dbo.dong_bo_luot WHERE he_thong_nguon_id=? AND tai_nguyen=N'ethnicities' ORDER BY thoi_diem_tao DESC",id));
        out.put("registryCount",c.queryForObject("SELECT COUNT(*) FROM dbo.he_thong_ngoai WHERE ma=?",Integer.class,SOURCE));
        out.put("bootstrapCounts",Map.of("units",c.queryForObject("SELECT COUNT(*) FROM dbo.dot_dong_bo WHERE he_thong_nguon_id=? AND loai_doi_tuong=N'units' AND che_do_dong_bo='full' AND trang_thai='success'",Integer.class,id),"ethnicities",c.queryForObject("SELECT COUNT(*) FROM dbo.dong_bo_luot WHERE he_thong_nguon_id=? AND tai_nguyen=N'ethnicities' AND che_do='full' AND trang_thai='SUCCESS'",Integer.class,id)));
        Files.writeString(home.resolve("status.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsString(out));
        System.out.println("Source registry="+id+" epoch="+out.get("sourceEpoch")+" checkpoints="+checkpoints+"; details: private sync/status.json");
    }
}

