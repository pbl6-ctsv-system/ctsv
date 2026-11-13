package pbl6.ctsv.foundation.units;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import pbl6.ctsv.foundation.units.client.SisUnitsClient;
import static org.assertj.core.api.Assertions.*;

/** HTTP is real, external SIS here is deliberately STUB, no database proof. */
class SisUnitsClientTest {
    HttpServer server;AtomicInteger calls=new AtomicInteger();int status=200;String body;
    java.util.concurrent.ExecutorService executor;
    volatile int firstResponseDelayMillis;
    final java.util.List<Long> callTimes=new java.util.concurrent.CopyOnWriteArrayList<>();
    @BeforeEach void start()throws Exception {
        body="""
          {"mode":"full","source_epoch":"11111111-2222-3333-4444-555555555555","export_id":"22222222-2222-3333-4444-555555555555",
          "from_version":null,"to_version":"123","as_of":"2026-11-11T00:00:00Z","next_cursor":null,"items":[]}
          """;
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        executor=java.util.concurrent.Executors.newCachedThreadPool();server.setExecutor(executor);
        server.createContext("/api/dao-tao/don-vi",exchange->{
            int requestNumber=calls.incrementAndGet();
            callTimes.add(System.nanoTime());
            if(requestNumber==1&&firstResponseDelayMillis>0)try{Thread.sleep(firstResponseDelayMillis);}
            catch(InterruptedException e){Thread.currentThread().interrupt();exchange.close();return;}
            assertThat(exchange.getRequestHeaders().getFirst("X-Service-Key")).isEqualTo("fixture-client-key");
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type",status==200?"application/json":"application/problem+json");
            exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });
        server.start();
    }
    SisUnitsClient client(){return new SisUnitsClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"fixture-client-key",100);}
    @AfterEach void stop(){server.stop(0);executor.shutdownNow();}
    @Test void validResponseIsSchemaCheckedAndDecoded() {
        try(var client=client()){var page=client.full();assertThat(page).isNotNull();assertThat(page.toVersion()).isEqualTo("123");}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={502,503,504})
    void sa047_retriesAtMostThreeCallsOnTransientGatewayOrSourceFailure(int failureStatus) {
        status=failureStatus;body="{\"code\":\"SOURCE_UNAVAILABLE\"}";
        try(var client=client()){assertThatThrownBy(client::full).isInstanceOf(SisUnitsClient.RemoteFailure.class);}
        assertThat(calls.get()).isEqualTo(3);
        long firstGap=java.time.Duration.ofNanos(callTimes.get(1)-callTimes.get(0)).toMillis();
        long secondGap=java.time.Duration.ofNanos(callTimes.get(2)-callTimes.get(1)).toMillis();
        assertThat(firstGap).isGreaterThanOrEqualTo(100);assertThat(secondGap).isGreaterThanOrEqualTo(200);
        try{new com.fasterxml.jackson.databind.ObjectMapper().writeValue(java.nio.file.Path.of("target","p01-http-retry-evidence.json").toFile(),
            java.util.Map.of("externalSIS","STUB","transport","REAL_HTTP","status",failureStatus,"calls",3,"firstGapMillis",firstGap,"secondGapMillis",secondGap));}
        catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"400,INVALID_REQUEST","401,INVALID_SERVICE_KEY","403,SERVICE_FORBIDDEN","409,SOURCE_EPOCH_CHANGED","409,SOURCE_LINEAGE_INVALID","409,FULL_SYNC_REQUIRED","409,EXPORT_EXPIRED","503,SYNC_NOT_READY","503,EXPORT_LIMIT_EXCEEDED"})
    void sa048_doesNotRetryNonTransientResponse(int failureStatus,String code) {
        status=failureStatus;body="{\"code\":\""+code+"\"}";
        try(var client=client()){assertThatThrownBy(client::full).isInstanceOf(SisUnitsClient.RemoteFailure.class);}
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void sa042_invalidJsonOrShapeStopsWithoutRetry() {
        body="{\"items\":[]}";
        try(var client=client()){assertThatThrownBy(client::full).isInstanceOf(IllegalArgumentException.class);}
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void sa042_057_boundsUntrustedResponseBytesBeforeTreeMaterialization() {
        body=" ".repeat(8*1024*1024+1);
        try(var client=client()){assertThatThrownBy(client::full).isInstanceOf(IllegalArgumentException.class);}
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void sa015_047_actualDefaultReadTimeoutRetriesSameRequestAndThenSucceeds(){
        firstResponseDelayMillis=41000;long start=System.nanoTime();
        try(var client=client()){assertThat(client.full().toVersion()).isEqualTo("123");}
        long elapsed=java.time.Duration.ofNanos(System.nanoTime()-start).toMillis();
        assertThat(calls.get()).isEqualTo(2);assertThat(elapsed).isBetween(39500L,50000L);
    }
}

