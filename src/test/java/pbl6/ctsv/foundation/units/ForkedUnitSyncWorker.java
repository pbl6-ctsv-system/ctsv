package pbl6.ctsv.foundation.units;

import java.net.URI;
import pbl6.ctsv.foundation.units.client.SisUnitsClient;
import pbl6.ctsv.foundation.units.mapper.UnitSourceMapper;
import pbl6.ctsv.foundation.units.repository.UnitSyncRepository;
import pbl6.ctsv.foundation.units.service.*;
import pbl6.p00.SliceATestDatabaseSupport;

/** Test-only child JVM. Hard halt is confined to this process and the guarded CTSV test target. */
public final class ForkedUnitSyncWorker {
    public static void main(String[] args)throws Exception {
        long source=Long.parseLong(args[0]);String stage=args[2];
        if(!java.util.Set.of("page.afterCommit","final.afterCheckpoint").contains(stage))throw new IllegalArgumentException("Unknown test barrier");
        var guard=new UnitSyncGuard(SliceATestDatabaseSupport.dataSource(),"ctsv_test_20261111","DEV2022");
        var repository=new UnitSyncRepository(new UnitSourceMapper());
        try(var client=new SisUnitsClient(URI.create(args[1]),System.getenv("P01_FIXTURE_SERVICE_KEY"),1)){
            new UnitSyncService(guard,repository,client,source,1){
                @Override protected void fault(String point,java.sql.Connection c){
                    if(point.equals(stage)){
                        System.out.println("P01_CRASH_BARRIER:"+stage);System.out.flush();Runtime.getRuntime().halt(86);
                    }
                }
            }.sync();
        }
        throw new IllegalStateException("Expected crash barrier was not reached");
    }
}
