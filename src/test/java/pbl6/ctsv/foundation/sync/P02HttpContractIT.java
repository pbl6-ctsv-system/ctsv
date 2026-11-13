package pbl6.ctsv.foundation.sync;
import java.util.*;
import java.util.stream.*;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import com.fasterxml.jackson.databind.*;
import static org.assertj.core.api.Assertions.*;

/** Actual SIS HTTP/security chain; rejected requests do not consume export slots. */
class P02HttpContractIT {
    static final ObjectMapper JSON=new ObjectMapper();static P02ResourceProvider provider;
    @BeforeAll static void start()throws Exception{provider=new P02ResourceProvider();provider.start();}
    @AfterAll static void close()throws Exception{if(provider!=null)provider.close();}
    static Stream<JsonNode> resources()throws Exception{
        var rows=new ArrayList<JsonNode>();for(var row:JSON.readTree(P02HttpContractIT.class.getResourceAsStream("/contracts/ct26-resources.json")))
            if(!Set.of("units","staff","class-advisors").contains(row.path("resource").asText()))rows.add(row);return rows.stream();
    }
    static void call(HttpClient http,String path,String query,String key,boolean body,int expected,String code)throws Exception{
        var builder=HttpRequest.newBuilder(provider.uri.resolve(path+query));
        if(key!=null)builder.header("X-Service-Key",key);
        if(body)builder.method("GET",HttpRequest.BodyPublishers.ofString("{}"));else builder.GET();
        var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(path+query+" => "+response.body()).isEqualTo(expected);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var error=JSON.readTree(response.body());assertThat(error.path("code").asText()).isEqualTo(code);
        assertThat(response.body()).doesNotContain(provider.key,provider.denied,"SQLServerException","jdbc:sqlserver");
    }
    @ParameterizedTest(name="{index}: resource query/body/key constraints") @MethodSource("resources")
    void eachResourceUsesItsOwnBoundsAndNeverAcceptsBodyOrUnauthorizedCaller(JsonNode resource)throws Exception{
        String path=resource.path("url").asText();int max=resource.path("profile").path("maxLimit").asInt();
        try(var http=HttpClient.newHttpClient()){
            call(http,path,"",null,false,401,"INVALID_SERVICE_KEY");call(http,path,"","wrong-test-only-key",false,401,"INVALID_SERVICE_KEY");
            call(http,path,"",provider.denied,false,403,"SERVICE_FORBIDDEN");
            for(var query:List.of("?limit=0","?limit=-1","?limit=1.5","?limit="+(max+1),"?limit=abc","?limit=","?unknown=1","?limit=1&limit=2","?cursor=x&limit=1","?since_version=0","?source_epoch="+provider.epoch))
                call(http,path,query,provider.key,false,400,"INVALID_REQUEST");
            call(http,path,"",provider.key,true,400,"INVALID_REQUEST");
        }
    }
}
