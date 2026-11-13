package pbl6.ctsv.foundation.sync;

import java.sql.*;
import java.util.*;
import java.time.*;
import java.nio.file.*;
import java.net.http.*;
import java.util.stream.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import pbl6.ctsv.CtsvApplication;
import pbl6.ctsv.foundation.units.client.SisUnitsClient;
import pbl6.ctsv.foundation.units.service.*;
import pbl6.ctsv.foundation.units.repository.UnitSyncRepository;
import pbl6.ctsv.foundation.units.mapper.UnitSourceMapper;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import static org.assertj.core.api.Assertions.*;

/** Two actual Boot backends/SQL databases; all P02 production adapters. Synthetic fixtures only. */
@SpringBootTest(classes=CtsvApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FoundationResourcesIT {
    static final ObjectMapper JSON=new ObjectMapper();
    final List<Map<String,Object>> timeline=new ArrayList<>();
    P02ResourceProvider provider;long source;DurableSyncStore store;
    static Stream<String> resources(){return FoundationResourceCatalog.all().stream().map(FoundationResourceCatalog.Resource::resource);}
    @BeforeAll void prepare()throws Exception {
        P02Database.migrate();DurableStoreIT.ds=P02Database.source();P02ResourcesFixture.seed();
        Files.createDirectories(P02ResourceProvider.OUT);Path bindingFile=P02ResourceProvider.OUT.resolve("fixture-binding.json");
        if(Files.exists(bindingFile)){
            var binding=JSON.readTree(bindingFile.toFile());source=binding.path("source").asLong();
            assertThat(binding.path("database").asText()).isEqualTo(P02Database.NAME);
            assertThat(scalar("SELECT COUNT(*) FROM dbo.he_thong_ngoai WHERE id="+source+" AND ma LIKE N'P02-%'")).isEqualTo(1);
            provider=new P02ResourceProvider(UUID.fromString(binding.path("epoch").asText()));
        }else{
            source=DurableStoreIT.source();provider=new P02ResourceProvider();
            JSON.writeValue(bindingFile.toFile(),Map.of("source",source,"epoch",provider.epoch.toString(),"database",P02Database.NAME));
        }
        store=DurableStoreIT.store();provider.start();
        try(var client=new SisUnitsClient(provider.uri,provider.key,100)){
            var guard=new UnitSyncGuard(P02Database.source(),P02Database.NAME,"DEV2022");
            new UnitSyncService(guard,new UnitSyncRepository(new UnitSourceMapper()),client,source,100).sync();
        }
    }
    @AfterAll void close()throws Exception{if(provider!=null)provider.close();}
    long scalar(String sql)throws Exception{try(var c=P02Database.open()){return P02Database.scalar(c,sql);}}
    JsonNode rawCall(String resource,String query,String key,int expected)throws Exception{
        String path="";for(var r:JSON.readTree(getClass().getResourceAsStream("/contracts/ct26-resources.json")))if(r.path("resource").asText().equals(resource))path=r.path("url").asText();
        var request=HttpRequest.newBuilder(provider.uri.resolve(path+query)).GET();if(key!=null)request.header("X-Service-Key",key);
        try(var http=HttpClient.newHttpClient()){
            var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(resource+" "+response.body()).isEqualTo(expected);
            assertThat(response.body()).doesNotContain(provider.key,provider.denied,"jdbc:sqlserver");return JSON.readTree(response.body());
        }
    }
    @ParameterizedTest(name="{0}: full/delta/delete/replay/security/SQL mapping") @MethodSource("resources")
    void productionSliceEndToEnd(String resource)throws Exception {
        long started=System.nanoTime();provider.start();var spec=FoundationResourceCatalog.resource(resource);var fixture=P02ResourcesFixture.resource(resource);String pk=fixture.path("key").asText();
        var target=new JdbcFoundationTarget(resource);var rows=fixture.path("rows");long first=rows.get(0).path(pk).asLong(),probe=rows.get(3).path(pk).asLong();
        assertThat(rawCall(resource,"",null,401).path("code").asText()).isEqualTo("INVALID_SERVICE_KEY");
        assertThat(rawCall(resource,"",provider.denied,403).path("code").asText()).isEqualTo("SERVICE_FORBIDDEN");
        assertThat(rawCall(resource,"?limit=0",provider.key,400).path("code").asText()).isEqualTo("INVALID_REQUEST");
        long fullU;long fullRows=0;int pages=0;
        try(var client=new CtHttpClient(provider.uri,resource,provider.key,1);var lease=store.acquire(source,resource)){
            var page=client.full();fullU=CtSchema.number(page.path("to_version"));UUID run=store.beginRun(lease,page);String used=null;
            // A write after the snapshot must stay out of this export, then arrive through CT.
            var changed=changedRow(spec,rows);
            P02ResourcesFixture.write(fixture,changed);P02ResourcesFixture.write(fixture,rows.get(3));
            while(true){pages++;fullRows+=page.path("items").size();var result=store.page(lease,run,pages,used,page,target);if(result.complete())break;used=page.path("next_cursor").asText();lease.beforeHttp();page=client.next(used);}
            assertThat(pages).isGreaterThanOrEqualTo(3);assertThat(store.checkpoint(lease).version()).isEqualTo(fullU);
        }
        for(int i=0;i<3;i++)assertMapped(spec,rows.get(i));
        long local=FoundationTargetIT.local(source,spec.target(),first);
        long deltaU;
        try(var client=new CtHttpClient(provider.uri,resource,provider.key,2)){
            var result=new CtSyncService(store,client,target,source,2,1000000).sync();deltaU=result.version();assertThat(deltaU).isGreaterThan(fullU);
        }
        assertThat(FoundationTargetIT.local(source,spec.target(),first)).isEqualTo(local).isNotEqualTo(first);
        assertMapped(spec,changedRow(spec,rows));
        long probeLocal=FoundationTargetIT.local(source,spec.target(),probe);assertThat(probeLocal).isNotEqualTo(probe);
        provider.start();P02ResourcesFixture.deleteProbe(resource);
        long deleteU;
        try(var client=new CtHttpClient(provider.uri,resource,provider.key,2)){
            var result=new CtSyncService(store,client,target,source,2,1000000).sync();deleteU=result.version();assertThat(deleteU).isGreaterThan(deltaU);
            assertThat(scalar("SELECT COUNT(*) FROM dbo.["+spec.target()+"] WHERE id="+probeLocal+" AND dang_hien_dien_nguon=0")).isEqualTo(1);
            var replay=new CtSyncService(store,client,target,source,2,1000000).sync();assertThat(replay.processed()).isZero();
        }
        timeline.add(new LinkedHashMap<>(Map.of("resource",resource,"source",source,"fullRows",fullRows,"pages",pages,"fullU",fullU,"deltaU",deltaU,"deleteU",deleteU,"sourceId",first,"localId",local,"elapsedMs",Duration.ofNanos(System.nanoTime()-started).toMillis())));
        JSON.writerWithDefaultPrettyPrinter().writeValue(P02ResourceProvider.OUT.resolve("resources-evidence.json").toFile(),timeline);
    }
    JsonNode changedRow(FoundationResourceCatalog.Resource spec,JsonNode rows){
        var changed=(ObjectNode)rows.get(0).deepCopy();
        var fields=spec.fields().stream().filter(f->!f.json().equals("source_id")).toList();
        var selected=fields.stream().filter(f->f.parent()==null).findFirst().orElse(fields.getLast());
        if(selected.parent()!=null)changed.set(selected.sql(),rows.get(1).path(selected.sql()));
        else if(selected.sqlType().contains("varchar"))changed.put(selected.sql(),rows.get(0).path(selected.sql()).asText()+"-UPDATED");
        else if(selected.sqlType().equals("smallint")||selected.sqlType().equals("int"))changed.put(selected.sql(),8);
        else if(selected.sqlType().startsWith("decimal"))changed.put(selected.sql(),"2.5000000000");
        else throw new IllegalStateException("Fixture needs an explicit update variant");
        return changed;
    }
    void assertMapped(FoundationResourceCatalog.Resource spec,JsonNode row)throws Exception{
        String sourceKey=spec.fields().stream().filter(f->f.json().equals("source_id")).findFirst().orElseThrow().sql();long local=FoundationTargetIT.local(source,spec.target(),row.path(sourceKey).asLong());
        try(var c=P02Database.open();var s=c.createStatement();var q=s.executeQuery("SELECT * FROM dbo.["+spec.target()+"] WHERE id="+local)){
            assertThat(q.next()).isTrue();
            for(var f:spec.fields()){
                if(f.json().equals("source_id"))continue;var expected=row.path(f.sql());String actual=q.getString(f.target());
                if(f.parent()!=null){assertThat(actual).isEqualTo(Long.toString(FoundationTargetIT.local(source,f.parent(),expected.asLong()))).isNotEqualTo(expected.asText());}
                else if(f.localType().startsWith("decimal"))assertThat(new java.math.BigDecimal(actual)).isEqualByComparingTo(new java.math.BigDecimal(expected.asText()));
                else assertThat(actual).as(spec.resource()+"."+f.json()).isEqualTo(expected.asText());
            }
            assertThat(q.getObject("thoi_diem_cap_nhat_nguon")).isNull();assertThat(q.getInt("dang_hien_dien_nguon")).isEqualTo(1);
        }
    }
}
