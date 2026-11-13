package pbl6.ctsv.foundation.units;
import org.junit.jupiter.api.Test;
import pbl6.ctsv.foundation.units.service.UnitSyncGuard;
import pbl6.p00.SliceATestDatabaseSupport;
import static org.assertj.core.api.Assertions.*;
class UnitSyncGuardIT {
    @Test void sa019_onlyOneConnectionOwnsResourceAndLockReleases()throws Exception {
        var guard=new UnitSyncGuard(SliceATestDatabaseSupport.dataSource(),"ctsv_test_20261111","DEV2022");
        assertThatCode(()->{
            try(var first=guard.acquire(987654321L)){
                assertThat(first.connection().getAutoCommit()).isTrue();
                assertThatThrownBy(()->guard.acquire(987654321L)).isInstanceOf(UnitSyncGuard.BusyException.class);
                first.begin();assertThat(first.connection().getAutoCommit()).isFalse();
                first.connection().rollback();first.connection().setAutoCommit(true);
            }
            try(var next=guard.acquire(987654321L)){assertThat(next.connection().isClosed()).isFalse();}
        }).doesNotThrowAnyException();
    }
    @Test void sa019_targetMismatchNeverAcquiresLock()throws Exception {
        var guard=new UnitSyncGuard(SliceATestDatabaseSupport.dataSource(),"sis_test_20261111","DEV2022");
        assertThatThrownBy(()->guard.acquire(987654321L)).isInstanceOf(IllegalStateException.class).hasMessage("Unexpected sync database target");
    }
}
