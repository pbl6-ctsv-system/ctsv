package pbl6.ctsv.foundation.units.config;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import pbl6.ctsv.foundation.units.client.SisUnitsClient;
import pbl6.ctsv.foundation.units.mapper.UnitSourceMapper;
import pbl6.ctsv.foundation.units.repository.UnitSyncRepository;
import pbl6.ctsv.foundation.units.service.*;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="foundation.units.enabled",havingValue="true")
@EnableConfigurationProperties(UnitSyncProperties.class)
public class UnitSyncConfiguration {
    @Bean(destroyMethod="close") SisUnitsClient sisUnitsClient(UnitSyncProperties p){
        return new SisUnitsClient(p.baseUrl(),p.serviceKey(),p.pageSize());
    }
    @Bean UnitSyncRepository unitSyncRepository(){return new UnitSyncRepository(new UnitSourceMapper());}
    @Bean UnitSyncGuard unitSyncGuard(DataSource ds,UnitSyncProperties p){
        return new UnitSyncGuard(ds,p.expectedDatabase(),p.expectedInstance());
    }
    @Bean UnitSyncService unitSyncService(UnitSyncGuard guard,UnitSyncRepository repository,SisUnitsClient client,UnitSyncProperties p){
        return new UnitSyncService(guard,repository,client,p.sourceSystemId(),p.pageSize());
    }
    @Bean @ConditionalOnProperty(name="foundation.units.run-on-startup",havingValue="true")
    UnitSyncRunner unitSyncRunner(UnitSyncService service,UnitSyncProperties p){
        return new UnitSyncRunner(service,p);
    }
}
