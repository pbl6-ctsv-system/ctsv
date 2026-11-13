package pbl6.ctsv.foundation.sync;
import java.net.*;import java.net.http.*;import java.nio.*;import java.nio.charset.StandardCharsets;import java.time.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.Flow.*;import java.io.*;
import com.fasterxml.jackson.databind.*;
/** Bounded response subscriber and a whole-request deadline; no credentials in errors/logs. */
public class CtHttpClient implements AutoCloseable {
 public static final class RemoteFailure extends RuntimeException {public final int status;public final String code;RemoteFailure(int status,String code){super(code==null?"Remote HTTP "+status:code);this.status=status;this.code=code;}}
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 private final ObjectMapper json=new ObjectMapper();private final CtSchema schema;private final URI base;private final String path,key;private final int limit;private final long maxBytes;private final Duration timeout;
 public CtHttpClient(URI base,String resource,String key,int limit){
  if(key==null||key.isBlank())throw new IllegalArgumentException("Service credential required");this.base=base;this.key=key;
  try(var in=getClass().getResourceAsStream("/contracts/ct26-resources.json")){
   JsonNode entry=null;for(var row:json.readTree(in))if(row.path("resource").asText().equals(resource))entry=row;
   if(entry==null||resource.equals("units")||limit<1||limit>entry.path("profile").path("maxLimit").asInt())throw new IllegalArgumentException("Invalid approved resource/limit");
   path=entry.path("url").asText();maxBytes=entry.path("profile").path("maxBytes").asLong();timeout=Duration.ofSeconds(entry.path("profile").path("deadlineSeconds").asLong()+10);this.limit=limit;
  }catch(IOException e){throw new IllegalStateException("Pinned catalog unavailable",e);}schema=new CtSchema(resource);
 }
 public JsonNode full(){return fetch("?limit="+limit);}
 public JsonNode incremental(DurableSyncStore.Checkpoint cp){return fetch("?since_version="+cp.version()+"&source_epoch="+cp.epoch()+"&limit="+limit);}
 public JsonNode next(String cursor){return fetch("?cursor="+URLEncoder.encode(cursor,StandardCharsets.UTF_8));}
 protected void pause(int attempt){try{Thread.sleep((attempt==0?1000:2000)+ThreadLocalRandom.current().nextInt(101));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Sync interrupted",e);}}
 private JsonNode fetch(String query){
  for(int attempt=0;attempt<3;attempt++){
   CompletableFuture<HttpResponse<byte[]>> future=null;
   try{
    var request=HttpRequest.newBuilder(base.resolve(path+query)).GET().timeout(timeout).header("X-Service-Key",key).build();
    future=client.sendAsync(request,info->new BoundedBody(info.statusCode()==200?maxBytes:65536));var response=future.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
    if(response.statusCode()!=200){
     if(response.statusCode()==502||response.statusCode()==504){if(attempt<2){pause(attempt);continue;}throw new RemoteFailure(response.statusCode(),null);}
     JsonNode problem;try{problem=json.readTree(response.body());}catch(IOException e){throw new IllegalArgumentException("Invalid source ProblemDetail");}
     for(String field:List.of("type","title","detail","instance","code","traceId"))if(!problem.path(field).isTextual())throw new IllegalArgumentException("Invalid source ProblemDetail");
     String code=problem.path("code").textValue();
     Map<String,Integer> codes=Map.ofEntries(Map.entry("INVALID_REQUEST",400),Map.entry("INVALID_SERVICE_KEY",401),Map.entry("SERVICE_FORBIDDEN",403),Map.entry("EXPORT_EXPIRED",409),Map.entry("FULL_SYNC_REQUIRED",409),Map.entry("SOURCE_EPOCH_CHANGED",409),Map.entry("SOURCE_LINEAGE_INVALID",409),Map.entry("SYNC_NOT_READY",503),Map.entry("SOURCE_UNAVAILABLE",503),Map.entry("EXPORT_LIMIT_EXCEEDED",503),Map.entry("EXPORT_CAPACITY_EXCEEDED",503));
     if(!response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json")||!problem.path("status").isInt()||problem.path("status").asInt()!=response.statusCode()||!Objects.equals(codes.get(code),response.statusCode()))throw new IllegalArgumentException("Invalid source error status/code");
     UUID.fromString(problem.path("traceId").asText());
     boolean retry=response.statusCode()==503&&Set.of("SOURCE_UNAVAILABLE","EXPORT_CAPACITY_EXCEEDED").contains(code);
     if(retry&&attempt<2){pause(attempt);continue;}throw new RemoteFailure(response.statusCode(),code);
    }
    if(!response.headers().firstValue("Content-Type").orElse("").startsWith("application/json")||!response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))throw new IllegalArgumentException("Invalid source response headers");
    JsonNode page;try{page=json.readTree(response.body());}catch(IOException e){throw new IllegalArgumentException("Invalid source JSON");}schema.validate(page);return page;
   }catch(RemoteFailure|IllegalArgumentException e){throw e;}
   catch(ExecutionException e){if(e.getCause() instanceof IllegalArgumentException)throw new IllegalArgumentException("Source response exceeds approved byte bound");if(attempt==2)throw new IllegalStateException("Source connection failed after three calls");pause(attempt);}
   catch(TimeoutException e){if(future!=null)future.cancel(true);if(attempt==2)throw new IllegalStateException("Source deadline exceeded after three calls");pause(attempt);}
   catch(InterruptedException e){if(future!=null)future.cancel(true);Thread.currentThread().interrupt();throw new IllegalStateException("Sync interrupted");}
  }throw new IllegalStateException("Source retries exhausted");
 }
 private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
  private final long max;private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private final CompletableFuture<byte[]> result=new CompletableFuture<>();private Subscription subscription;
  BoundedBody(long max){this.max=max;}
  public CompletionStage<byte[]> getBody(){return result;}
  public void onSubscribe(Subscription s){subscription=s;s.request(1);}
  public void onNext(List<ByteBuffer> buffers){try{for(var buffer:buffers){if(buffer.remaining()>max-bytes.size()){subscription.cancel();result.completeExceptionally(new IllegalArgumentException("Bounded body exceeded"));return;}byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.write(part);}subscription.request(1);}catch(IOException e){subscription.cancel();result.completeExceptionally(e);}}
  public void onError(Throwable e){result.completeExceptionally(e);}public void onComplete(){result.complete(bytes.toByteArray());}
 }
 @Override public void close(){client.close();}
}
