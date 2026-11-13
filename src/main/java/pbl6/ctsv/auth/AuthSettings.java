package pbl6.ctsv.auth;

import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ctsv.auth")
record AuthSettings(String issuer,String audience,String signingKid,Path privateKey,
                    Map<String,Path> publicKeys,Set<String> origins,Set<String> trustedSources,
                    Map<String,Caller> callers,boolean bootstrapEnabled) {
    record Caller(String key,Set<String> capabilities){
        @Override public String toString(){return "Caller[credential configured]";}
    }

    AuthSettings {
        if(issuer==null || audience==null || signingKid==null || privateKey==null || publicKeys==null || origins==null || origins.isEmpty())
            throw new IllegalArgumentException("Explicit Auth issuer, audience, keys and origins required");
        publicKeys=Map.copyOf(publicKeys);
        origins=Set.copyOf(origins);
        trustedSources=trustedSources==null?Set.of():Set.copyOf(trustedSources);
        callers=callers==null?Map.of():Map.copyOf(callers);
        for(String origin:origins){
            var uri=URI.create(origin);
            if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || !uri.getPath().isEmpty()
                || uri.getQuery()!=null || uri.getFragment()!=null)
                throw new IllegalArgumentException("Invalid Auth web origin");
        }
        var keys=new HashSet<String>();
        for(var c:callers.values())
            if(c.key()==null || c.key().isBlank() || !keys.add(c.key()))
                throw new IllegalArgumentException("Invalid Auth service configuration");
    }
}
