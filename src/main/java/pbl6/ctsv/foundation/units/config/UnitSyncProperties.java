package pbl6.ctsv.foundation.units.config;

import java.net.URI;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("foundation.units")
public record UnitSyncProperties(long sourceSystemId,URI baseUrl,String serviceKey,
        String expectedDatabase,String expectedInstance,@DefaultValue("100") int pageSize,
        @DefaultValue("false") boolean runOnStartup,@DefaultValue("SYNC") Operation operation,Path reviewFile) {
    public enum Operation { SYNC, PREVIEW, CONFIRMED }
    public UnitSyncProperties {
        if(sourceSystemId<1||baseUrl==null||serviceKey==null||serviceKey.isBlank()
                ||expectedDatabase==null||expectedDatabase.isBlank()||expectedInstance==null||expectedInstance.isBlank()
                ||pageSize<1||pageSize>500||operation==null)
            throw new IllegalArgumentException("Explicit SIS source, credential, target and valid page limit are required");
        if(runOnStartup&&operation!=Operation.SYNC&&reviewFile==null)
            throw new IllegalArgumentException("A private review file is required for baseline confirmation");
    }
    @Override public String toString(){return "UnitSyncProperties[sourceSystemId="+sourceSystemId+",serviceKey=REDACTED]";}
}
