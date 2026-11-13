package pbl6.ctsv.foundation.units;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.*;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import pbl6.ctsv.foundation.units.client.SisUnitsClient;
import pbl6.ctsv.foundation.units.dto.*;
import pbl6.ctsv.foundation.units.mapper.UnitSourceMapper;
import pbl6.ctsv.foundation.units.repository.UnitSyncRepository;
import pbl6.ctsv.foundation.units.service.*;
import pbl6.p01.FaultDatabase;
import static org.assertj.core.api.Assertions.*;

/** All writes stay in the one approved fault DB. Provider HTTP and consumer SQL are real. */
final class FaultSyncFixture implements AutoCloseable {
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    static final Path EVIDENCE=Path.of("target","p01-fault");
    static final String RUN=UUID.randomUUID().toString();
    static final List<Map<String,Object>> events=new ArrayList<>();
    final String prefix="P01-FAULT-"+UUID.randomUUID();
    final String key=UUID.randomUUID().toString(),deniedKey=UUID.randomUUID().toString();
    UUID epoch=UUID.randomUUID();
    URI uri;Process provider;
    int port;
    final UnitSyncRepository repository=new UnitSyncRepository(new UnitSourceMapper());
    FaultSyncFixture()throws Exception {Files.createDirectories(EVIDENCE);}
    static void projection()throws Exception {
        try(var c=FaultDatabase.open()){
            if(scalar(c,"SELECT COUNT(*) FROM sys.tables WHERE object_id=OBJECT_ID('dbo.don_vi')")==0){
                FaultDatabase.guard(c);
                ScriptUtils.executeSqlScript(c,new EncodedResource(new ClassPathResource("fixtures/units/fault-consumer-projection.sql"),java.nio.charset.StandardCharsets.UTF_8),
                    false,false,"--","GO","/*","*/");
            }
            assertThat(scalar(c,"SELECT COUNT(*) FROM sys.triggers WHERE name IN(N'tr_nhat_ky_thao_tac_immutable',N'tr_chi_tiet_nhat_ky_thao_tac_immutable')")).isEqualTo(2);
        }
    }
    static long scalar(Connection c,String sql)throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery(sql)){if(!r.next())throw new IllegalStateException("No scalar");return r.getLong(1);}
    }
    static void record(String name,Map<String,?> data)throws Exception{
        var event=new LinkedHashMap<String,Object>();event.put("scenario",name);event.put("at",Instant.now().toString());
        event.put("database",FaultDatabase.NAME);event.put("sourceSQL","REAL");event.put("sourceHTTP","REAL");
        event.put("consumer","PRODUCTION_SERVICE_REAL_SQL");event.put("physicalDatabases",1);event.putAll(data);
        events.add(event);JSON.writerWithDefaultPrettyPrinter().writeValue(EVIDENCE.resolve("events-"+RUN+".json").toFile(),events);
        JSON.writerWithDefaultPrettyPrinter().writeValue(EVIDENCE.resolve("events.json").toFile(),events);
    }
    static void destructive(String name,String sql)throws Exception{
        try(var c=FaultDatabase.open()){
            FaultDatabase.guard(c);record("GUARD:"+name,Map.of("instance",FaultDatabase.INSTANCE,"ownerVerified",true,"marker",FaultDatabase.MARKER));
            try(var s=c.createStatement()){s.execute(sql);}
        }
    }
    void resetSource()throws Exception{destructive("RESET_SOURCE_FIXTURE","DELETE dbo.DonVi;");}
    long add(String suffix,String raw)throws Exception{
        try(var c=FaultDatabase.open();var s=c.prepareStatement("DECLARE @ids TABLE(id bigint); INSERT dbo.DonVi(MaDV,TenDV,LuuTru,IsUs) OUTPUT inserted.ID INTO @ids VALUES(?,N'Đơn vị giả lập',?,NULL); SELECT id FROM @ids")){
            s.setString(1,prefix+"-"+suffix);s.setString(2,raw);try(var r=s.executeQuery()){r.next();return r.getLong(1);}
        }
    }
    void update(long id,String raw)throws Exception{
        try(var c=FaultDatabase.open();var s=c.prepareStatement("UPDATE dbo.DonVi SET LuuTru=? WHERE ID=?")){
            s.setString(1,raw);s.setLong(2,id);assertThat(s.executeUpdate()).isEqualTo(1);
        }
    }
    long current()throws SQLException{return FaultDatabase.number("SELECT CHANGE_TRACKING_CURRENT_VERSION()");}
    long minimum()throws SQLException{return FaultDatabase.number("SELECT CHANGE_TRACKING_MIN_VALID_VERSION(OBJECT_ID('dbo.DonVi'))");}
    String jdbcUrl()throws SQLException{
        return ((org.springframework.jdbc.datasource.SimpleDriverDataSource)FaultDatabase.dataSource()).getUrl()
            +"integratedSecurity=true;encrypt=true;trustServerCertificate=true;connectRetryCount=0;";
    }
    void start()throws Exception{start(null);}
    void start(String impersonatedUser)throws Exception{
        stop();FaultDatabase.dataSource(); // Verify target before launching the provider.
        Path jar=Path.of("..","daotao","target","daotao-0.0.1-SNAPSHOT.jar").toAbsolutePath().normalize();
        if(!Files.isRegularFile(jar))throw new IllegalStateException("Build the current SIS jar first");
        var arguments=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
            "-Xmx512m","-jar",jar.toString(),"--spring.profiles.active=local","--server.address=127.0.0.1","--server.port="+port,
            "--spring.datasource.hikari.connection-timeout=3000","--spring.datasource.hikari.maximum-pool-size=2"));
        if(impersonatedUser!=null){
            if(!Set.of("p01_fault_no_select","p01_fault_no_ct").contains(impersonatedUser))throw new IllegalArgumentException("Unknown test principal");
            arguments.add("--spring.datasource.hikari.connection-init-sql=EXECUTE AS USER=N'"+impersonatedUser+"'");
        }
        Path log=EVIDENCE.resolve("sis-"+UUID.randomUUID()+".log");
        var builder=new ProcessBuilder(arguments);
        builder.environment().put("SIS_JDBC_URL",jdbcUrl());builder.environment().put("SIS_SOURCE_EPOCH",epoch.toString());
        builder.environment().put("SIS_SERVICE_KEY",key);
        builder.environment().put("SPRING_APPLICATION_JSON",JSON.writeValueAsString(Map.of("sis",Map.of("units",
            Map.of("service-keys",Map.of("ctsv",key,"blocked",deniedKey),"service-resources",Map.of("ctsv","units","blocked","other"))))));
        builder.redirectErrorStream(true).redirectOutput(log.toFile());provider=builder.start();
        var regex=Pattern.compile("Tomcat started on port (\\d+)");long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
        while(System.nanoTime()<deadline&&provider.isAlive()){
            if(Files.exists(log)){var matcher=regex.matcher(Files.readString(log));if(matcher.find()){
                port=Integer.parseInt(matcher.group(1));uri=URI.create("http://127.0.0.1:"+port);
                record("PROVIDER_START",Map.of("epoch",epoch.toString(),"pid",provider.pid(),"log",log.toString(),"role",impersonatedUser==null?"owner":impersonatedUser));return;
            }}
            Thread.sleep(100);
        }
        throw new IllegalStateException("Fault provider startup failed; inspect "+log);
    }
    void stop()throws Exception{
        if(provider!=null&&provider.isAlive()){provider.destroy();if(!provider.waitFor(10,TimeUnit.SECONDS))provider.destroyForcibly().waitFor();}
    }
    HttpResponse<String> call(String query,String credential)throws Exception{
        var builder=HttpRequest.newBuilder(uri.resolve("/api/dao-tao/don-vi"+query)).timeout(Duration.ofSeconds(45)).GET();
        if(credential!=null)builder.header("X-Service-Key",credential);
        try(var client=HttpClient.newHttpClient()){return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());}
    }
    JsonNode ok(String query)throws Exception{
        var r=call(query,key);assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        var json=JSON.readTree(r.body());new pbl6.ctsv.foundation.units.validation.UnitContractValidator().validate(json);return json;
    }
    void error(String query,String credential,int status,String code)throws Exception{
        var r=call(query,credential);assertThat(r.statusCode()).as(r.body()).isEqualTo(status);
        assertThat(r.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        var json=JSON.readTree(r.body());assertThat(json.path("code").asText()).isEqualTo(code);
        for(String field:List.of("type","title","status","detail","instance","code","traceId"))assertThat(json.has(field)).as(field).isTrue();
        assertThat(json.path("status").asInt()).isEqualTo(status);UUID.fromString(json.path("traceId").asText());
        if(status==400)assertThat(json.path("errors").isArray()).isTrue();
        assertThat(r.body()).doesNotContain(key,deniedKey,"SQLServerException","jdbc:sqlserver","at pbl6.");
        record("HTTP:"+code,Map.of("status",status,"request",query,"response",json));
    }
    ObservedClient client(int size){return new ObservedClient(uri,key,size);}
    static class ObservedClient extends SisUnitsClient {
        UnitExportResponse lastFull;int fullCalls,incrementalCalls,cursorCalls;
        ObservedClient(URI uri,String key,int size){super(uri,key,size);}
        @Override public UnitExportResponse full(){fullCalls++;return lastFull=super.full();}
        @Override public UnitExportResponse incremental(UUID e,long l){incrementalCalls++;return super.incremental(e,l);}
        @Override public UnitExportResponse next(String cursor){cursorCalls++;return super.next(cursor);}
    }
    UnitSyncService service(ObservedClient client,long source,int size){
        try{return new UnitSyncService(new UnitSyncGuard(FaultDatabase.dataSource(),FaultDatabase.NAME,FaultDatabase.INSTANCE),repository,client,source,size);}
        catch(SQLException e){throw new IllegalStateException(e);}
    }
    void runConsumerBackend(long source)throws Exception{
        Path jar=Path.of("target","ctsv-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        Path log=EVIDENCE.resolve("ctsv-"+UUID.randomUUID()+".log");
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Xmx512m","-jar",jar.toString(),
            "--spring.profiles.active=local","--spring.main.web-application-type=none");
        builder.environment().put("CTSV_JDBC_URL",jdbcUrl());
        var settings=new LinkedHashMap<String,Object>();settings.put("enabled",true);settings.put("run-on-startup",true);
        settings.put("source-system-id",source);settings.put("base-url",uri.toString());settings.put("service-key",key);
        settings.put("expected-database",FaultDatabase.NAME);settings.put("expected-instance",FaultDatabase.INSTANCE);settings.put("page-size",500);
        builder.environment().put("SPRING_APPLICATION_JSON",JSON.writeValueAsString(Map.of("foundation",Map.of("units",settings))));
        builder.redirectErrorStream(true).redirectOutput(log.toFile());Process consumer=builder.start();
        try{
            assertThat(consumer.waitFor(45,TimeUnit.SECONDS)).as("consumer backend deadline; "+log).isTrue();
            assertThat(consumer.exitValue()).as("consumer backend log: "+log).isZero();
            assertThat(Files.readString(log)).doesNotContain(key,deniedKey);
            record("CTSV_BACKEND_COMPLETED",Map.of("sourceId",source,"pid",consumer.pid(),"sourcePid",provider.pid(),"log",log.toString(),"checkpoint",checkpoint(source)));
        }finally{if(consumer.isAlive())consumer.destroyForcibly().waitFor();}
    }
    record Consumer(long source,Map<String,Long> mapping,long local,long child,long audit,String checkpoint) {}
    Consumer seedConsumer(UnitExportResponse baseline)throws Exception{
        try(var c=FaultDatabase.open()){
            c.setAutoCommit(false);long source,local,child,audit;
            try(var s=c.prepareStatement("INSERT dbo.he_thong_ngoai(ma,ten) VALUES(?,N'Synthetic fault consumer'); SELECT CAST(SCOPE_IDENTITY() AS bigint)")){
                s.setString(1,prefix+"-"+UUID.randomUUID().toString().substring(0,8));try(var r=s.executeQuery()){r.next();source=r.getLong(1);}
            }
            String tag=prefix+"-"+source;
            try(var s=c.prepareStatement("INSERT dbo.don_vi(ma,dang_hoat_dong) VALUES(?,1); SELECT CAST(SCOPE_IDENTITY() AS bigint)")){
                s.setString(1,tag+"-LOCAL");try(var r=s.executeQuery()){r.next();local=r.getLong(1);}
            }
            repository.upsertPage(c,source,baseline.items());var mapping=repository.mappings(c,source);
            long first=mapping.get(baseline.items().getFirst().sourceId()),last=mapping.get(baseline.items().getLast().sourceId());
            try(var s=c.prepareStatement("UPDATE dbo.don_vi SET loai_don_vi=N'LOCAL',don_vi_cha_id=?,dang_hoat_dong=1 WHERE id=?")){s.setLong(1,local);s.setLong(2,first);s.executeUpdate();}
            try(var s=c.prepareStatement("INSERT dbo.don_vi(ma,don_vi_cha_id,dang_hoat_dong) VALUES(?,?,1); SELECT CAST(SCOPE_IDENTITY() AS bigint)")){
                s.setString(1,tag+"-CHILD");s.setLong(2,last);try(var r=s.executeQuery()){r.next();child=r.getLong(1);}
            }
            audit=new pbl6.ctsv.shared.audit.AuditWriter().append(c,source,"FAULT_HISTORY","don_vi",Long.toString(last),"Synthetic retained history",Map.of("fixture",prefix));
            long run=repository.startRun(c,source,"full",null);String checkpoint=baseline.sourceEpoch()+":"+baseline.toVersion();
            repository.completeRun(c,run,checkpoint,baseline.asOf(),baseline.items().size());c.commit();
            return new Consumer(source,mapping,local,child,audit,checkpoint);
        }
    }
    long emptyConsumer()throws Exception{
        try(var c=FaultDatabase.open();var s=c.prepareStatement("INSERT dbo.he_thong_ngoai(ma,ten) VALUES(?,N'Synthetic empty consumer'); SELECT CAST(SCOPE_IDENTITY() AS bigint)")){
            s.setString(1,prefix+"-"+UUID.randomUUID().toString().substring(0,8));try(var r=s.executeQuery()){r.next();return r.getLong(1);}
        }
    }
    String checkpoint(long source)throws Exception{try(var c=FaultDatabase.open()){return repository.checkpoint(c,source);}}
    String raw(long source,String id)throws Exception{
        try(var c=FaultDatabase.open();var s=c.prepareStatement("SELECT luu_tru_nguon FROM dbo.don_vi WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai=?")){
            s.setLong(1,source);s.setString(2,id);try(var r=s.executeQuery()){assertThat(r.next()).isTrue();return r.getString(1);}
        }
    }
    boolean active(long source,String id)throws Exception{
        try(var c=FaultDatabase.open();var s=c.prepareStatement("SELECT dang_hoat_dong_nguon FROM dbo.don_vi WHERE he_thong_nguon_id=? AND ma_dinh_danh_ben_ngoai=?")){
            s.setLong(1,source);s.setString(2,id);try(var r=s.executeQuery()){assertThat(r.next()).isTrue();return r.getBoolean(1);}
        }
    }
    void preserve(Consumer consumer)throws Exception{
        try(var c=FaultDatabase.open()){
            assertThat(repository.mappings(c,consumer.source())).isEqualTo(consumer.mapping());
            assertThat(scalar(c,"SELECT dang_hoat_dong FROM dbo.don_vi WHERE id="+consumer.local())).isEqualTo(1);
            assertThat(scalar(c,"SELECT COUNT(*) FROM dbo.nhat_ky_thao_tac WHERE id="+consumer.audit())).isEqualTo(1);
            long last=new ArrayList<>(consumer.mapping().values()).stream().filter(id->{
                try{return scalar(c,"SELECT COUNT(*) FROM dbo.don_vi WHERE id="+consumer.child()+" AND don_vi_cha_id="+id)==1;}catch(SQLException e){throw new IllegalStateException(e);}
            }).findFirst().orElseThrow();
            assertThat(last).isPositive();
            long first=consumer.mapping().entrySet().stream().min(Comparator.comparingLong(e->Long.parseLong(e.getKey()))).orElseThrow().getValue();
            assertThat(scalar(c,"SELECT COUNT(*) FROM dbo.don_vi WHERE id="+first+" AND don_vi_cha_id="+consumer.local()+" AND loai_don_vi=N'LOCAL' AND dang_hoat_dong=1")).isEqualTo(1);
        }
    }
    String projectionHash(Consumer consumer)throws Exception{
        try(var c=FaultDatabase.open();var s=c.createStatement();var r=s.executeQuery("SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',CONVERT(varbinary(max),(SELECT * FROM dbo.don_vi WHERE he_thong_nguon_id="+consumer.source()+" OR id IN("+consumer.local()+","+consumer.child()+") ORDER BY id FOR JSON PATH,INCLUDE_NULL_VALUES))),2)")){
            r.next();return r.getString(1);
        }
    }
    void cleanupConsumer(Consumer consumer)throws Exception{
        try(var c=FaultDatabase.open()){
            FaultDatabase.guard(c);c.setAutoCommit(false);
            try(var s=c.prepareStatement("DELETE dbo.don_vi WHERE id=?")){s.setLong(1,consumer.child());s.executeUpdate();}
            try(var s=c.prepareStatement("DELETE dbo.don_vi WHERE he_thong_nguon_id=?")){s.setLong(1,consumer.source());s.executeUpdate();}
            try(var s=c.prepareStatement("DELETE dbo.don_vi WHERE id=?")){s.setLong(1,consumer.local());s.executeUpdate();}
            c.commit(); // Keep immutable audit, source registry and all run history.
        }
    }
    @Override public void close()throws Exception{stop();}
}
