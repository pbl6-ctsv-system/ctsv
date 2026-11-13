package pbl6.ctsv.foundation.sync;
import java.net.*;import java.util.*;import java.util.concurrent.atomic.*;import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.HttpServer;import com.fasterxml.jackson.databind.*;import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/** REAL loopback transport to an EXTERNAL STUB; not SQL/provider evidence. */
class CtHttpClientTest {
 static final ObjectMapper JSON=new ObjectMapper();
 @Test void temporaryGatewayRetryIsBoundedAndUsesDefaultBackoff()throws Exception{
  var calls=new AtomicInteger();var times=new ArrayList<Long>();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/dao-tao/dan-toc",exchange->{times.add(System.nanoTime());int n=calls.incrementAndGet();int status=n<3?502:200;byte[] body=(status==200?DurableStoreIT.page(UUID.randomUUID(),5,null,DurableStoreIT.change(2,"DT")).toString():"gateway unavailable").getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type",status==200?"application/json":"text/plain");exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
  try(var c=new CtHttpClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"ethnicities","synthetic-key",500)){assertThat(c.full().path("items").size()).isEqualTo(1);assertThat(calls.get()).isEqualTo(3);assertThat(java.time.Duration.ofNanos(times.get(1)-times.get(0)).toMillis()).isGreaterThanOrEqualTo(950);assertThat(java.time.Duration.ofNanos(times.get(2)-times.get(1)).toMillis()).isGreaterThanOrEqualTo(1950);}finally{server.stop(0);}
 }
 @Test void malformedProblemDetailMustNotBeInterpretedAsFullRecovery()throws Exception{
  var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/api/dao-tao/dan-toc",exchange->{calls.incrementAndGet();byte[] body="{\"code\":\"FULL_SYNC_REQUIRED\"}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/problem+json");exchange.sendResponseHeaders(409,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
  try(var c=new CtHttpClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"ethnicities","synthetic-key",500)){
   assertThatThrownBy(c::full).isInstanceOf(IllegalArgumentException.class);assertThat(calls.get()).isEqualTo(1);
  }finally{server.stop(0);}
 }

 @Test void oversizedBodyIsRejectedOnceBeforeJsonParsing()throws Exception {
  var calls=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);byte[] body=new byte[8388609];java.util.Arrays.fill(body,(byte)'x');
  server.createContext("/api/dao-tao/dan-toc",e->{calls.incrementAndGet();e.getResponseHeaders().set("Content-Type","application/json");e.getResponseHeaders().set("Cache-Control","no-store");try{e.sendResponseHeaders(200,body.length);e.getResponseBody().write(body);}catch(java.io.IOException ignored){}finally{e.close();}});server.start();
  try(var c=new CtHttpClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"ethnicities","synthetic-key",500)){
   assertThatThrownBy(c::full).isInstanceOf(IllegalArgumentException.class);assertThat(calls.get()).isEqualTo(1);
  }finally{server.stop(0);}
 }
}
