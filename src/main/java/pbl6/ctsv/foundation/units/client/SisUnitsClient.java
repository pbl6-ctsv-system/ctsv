package pbl6.ctsv.foundation.units.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import com.fasterxml.jackson.databind.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.*;
import pbl6.ctsv.foundation.units.dto.UnitExportResponse;
import pbl6.ctsv.foundation.units.validation.UnitContractValidator;

/** Private service credential, bounded response, fixed source route and bounded retries. */
public class SisUnitsClient implements AutoCloseable {
    private static final int MAX_RESPONSE_BYTES=8*1024*1024;
    private static final Set<String> CODES=Set.of("INVALID_REQUEST","INVALID_SERVICE_KEY","SERVICE_FORBIDDEN",
        "EXPORT_EXPIRED","FULL_SYNC_REQUIRED","SOURCE_EPOCH_CHANGED","SOURCE_LINEAGE_INVALID","SYNC_NOT_READY",
        "SOURCE_UNAVAILABLE","EXPORT_LIMIT_EXCEEDED","EXPORT_CAPACITY_EXCEEDED");
    private final HttpClient transport;
    private final RestClient client;
    private final ObjectMapper mapper=new ObjectMapper();
    private final UnitContractValidator validator=new UnitContractValidator();
    private final int pageSize;

    public SisUnitsClient(URI base,String key,int pageSize) {
        if(base==null||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null
                ||key==null||key.isBlank()||pageSize<1||pageSize>500)
            throw new IllegalArgumentException("Invalid SIS client configuration");
        boolean local=Set.of("localhost","127.0.0.1","::1","[::1]").contains(base.getHost());
        if(!"https".equalsIgnoreCase(base.getScheme())&&!("http".equalsIgnoreCase(base.getScheme())&&local))
            throw new IllegalArgumentException("Service credential requires HTTPS outside loopback");
        this.pageSize=pageSize;
        transport=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory=new JdkClientHttpRequestFactory(transport);factory.setReadTimeout(Duration.ofSeconds(40));
        client=RestClient.builder().baseUrl(base.resolve("/").toString()).requestFactory(factory)
                .defaultHeader("X-Service-Key",key).defaultHeader("Accept","application/json").build();
        mapper.getFactory().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    public UnitExportResponse full(){return request("?limit="+pageSize);}
    public UnitExportResponse incremental(UUID epoch,long lower) {
        if(epoch==null||lower<0)throw new IllegalArgumentException("A valid checkpoint is required");
        return request("?since_version="+lower+"&source_epoch="+epoch+"&limit="+pageSize);
    }
    public UnitExportResponse next(String cursor){return request("?cursor="+URLEncoder.encode(cursor,StandardCharsets.UTF_8));}
    protected UnitExportResponse request(String query) {
        for(int attempt=0;attempt<3;attempt++) {
            try {
                return client.get().uri("/api/dao-tao/don-vi"+query).exchange((request,response)->{
                    int status=response.getStatusCode().value();byte[] body;
                    try {body=response.getBody().readNBytes(status==200?MAX_RESPONSE_BYTES+1:16385);}
                    catch(java.io.IOException e){throw new ResourceAccessException("SIS transport unavailable",e);}
                    if(status!=200) {
                        String code="SOURCE_UNAVAILABLE";
                        try {String candidate=mapper.readTree(body).path("code").asText();if(CODES.contains(candidate))code=candidate;}
                        catch(java.io.IOException|RuntimeException ignored){}
                        throw new RemoteFailure(status,code);
                    }
                    if(body.length>MAX_RESPONSE_BYTES)throw new IllegalArgumentException("SIS page exceeds the approved byte bound");
                    var type=response.getHeaders().getContentType();
                    if(type==null||!type.isCompatibleWith(MediaType.APPLICATION_JSON))throw new IllegalArgumentException("SIS response is not JSON");
                    try {
                        JsonNode node=mapper.readTree(body);validator.validate(node);
                        if(node.path("items").size()>pageSize)throw new IllegalArgumentException("SIS page exceeds requested limit");
                        return mapper.treeToValue(node,UnitExportResponse.class);
                    }catch(java.io.IOException e){throw new IllegalArgumentException("Invalid SIS response JSON");}
                });
            }catch(RemoteFailure e){
                boolean retry=(e.status()==502||e.status()==503||e.status()==504)
                        &&!Set.of("SYNC_NOT_READY","EXPORT_LIMIT_EXCEEDED").contains(e.code());
                if(!retry||attempt==2)throw e;
            }catch(ResourceAccessException e){
                if(attempt==2)throw new RemoteFailure(503,"SOURCE_UNAVAILABLE");
            }
            try {Thread.sleep((100L<<attempt)+ThreadLocalRandom.current().nextLong(51));}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("SIS request interrupted");}
        }
        throw new IllegalStateException("Unreachable retry state");
    }
    @Override public void close(){transport.close();}
    public static final class RemoteFailure extends IllegalStateException {
        private final int status;private final String code;
        public RemoteFailure(int status,String code){super(code);this.status=status;this.code=code;}
        public int status(){return status;} public String code(){return code;}
    }
}

