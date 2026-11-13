package pbl6.ctsv.foundation.sync;
import javax.sql.DataSource;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ApplicationRunner;
import com.fasterxml.jackson.databind.*;

/** One explicit internal resource operation. Scheduling/capacity orchestration does not create a public API. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="foundation.resources.enabled",havingValue="true")
@EnableConfigurationProperties(FoundationSyncProperties.class)
public class FoundationSyncConfiguration {
    private JsonNode profile(String resource){
        try(var in=getClass().getResourceAsStream("/contracts/ct26-resources.json")){
            for(var row:new ObjectMapper().readTree(in))if(row.path("resource").asText().equals(resource))return row.path("profile");
        }catch(java.io.IOException e){throw new IllegalStateException("Pinned resource catalog unavailable",e);}
        throw new IllegalArgumentException("Unknown P02 resource");
    }
    @Bean DurableSyncStore foundationResourceStore(DataSource ds,FoundationSyncProperties p){return new DurableSyncStore(ds,p.expectedDatabase(),p.expectedInstance());}
    @Bean(destroyMethod="close") CtHttpClient foundationResourceClient(FoundationSyncProperties p){return new CtHttpClient(p.baseUrl(),p.resource(),p.serviceKey(),profile(p.resource()).path("defaultLimit").asInt());}
    @Bean CtSyncService foundationResourceSyncService(DurableSyncStore store,CtHttpClient client,FoundationSyncProperties p){
        var limits=profile(p.resource());return new CtSyncService(store,client,new JdbcFoundationTarget(p.resource()),p.sourceSystemId(),limits.path("defaultLimit").asInt(),limits.path("maxRows").asLong());
    }
    @Bean @ConditionalOnProperty(name="foundation.resources.run-on-startup",havingValue="true")
    ApplicationRunner foundationResourceRunner(CtSyncService service){return args->service.sync();}
}
