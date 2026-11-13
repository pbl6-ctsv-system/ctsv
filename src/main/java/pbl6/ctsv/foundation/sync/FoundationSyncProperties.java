package pbl6.ctsv.foundation.sync;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("foundation.resources")
public record FoundationSyncProperties(long sourceSystemId,URI baseUrl,String serviceKey,
        String expectedDatabase,String expectedInstance,String resource,@DefaultValue("false") boolean runOnStartup) {
    public FoundationSyncProperties {
        if(sourceSystemId<1||baseUrl==null||serviceKey==null||serviceKey.isBlank()||expectedDatabase==null||expectedDatabase.isBlank()||expectedInstance==null||expectedInstance.isBlank())
            throw new IllegalArgumentException("Explicit SIS source/credential/target required");
        FoundationResourceCatalog.resource(resource);
    }
    @Override public String toString(){return "FoundationSyncProperties[resource="+resource+",sourceSystemId="+sourceSystemId+",serviceKey=REDACTED]";}
}
