package pbl6.ctsv.foundation.units;

import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import pbl6.ctsv.foundation.units.config.*;
import pbl6.ctsv.foundation.units.dto.BaselineApproval;
import pbl6.ctsv.foundation.units.service.UnitSyncService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnitSyncRunnerTest {
    @TempDir Path directory;
    final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    UnitSyncProperties properties(UnitSyncProperties.Operation mode,Path file){
        return new UnitSyncProperties(1,URI.create("http://127.0.0.1:9"),"synthetic-key","ctsv_test_20261111","DEV2022",100,true,mode,file);
    }
    @Test void previewWritesUnapprovedScopeAndNeverOverwritesExistingReview()throws Exception {
        var service=mock(UnitSyncService.class);Path file=directory.resolve("review.json");UUID epoch=UUID.randomUUID();
        when(service.preview()).thenReturn(new UnitSyncService.BaselineScope(1,null,epoch,Map.of("1","DV"),Map.of()));
        var runner=new UnitSyncRunner(service,properties(UnitSyncProperties.Operation.PREVIEW,file));
        runner.run(new DefaultApplicationArguments());var node=mapper.readTree(file.toFile());
        assertThat(node.path("approvedBy").asText()).isEmpty();assertThat(node.path("reason").asText()).isEmpty();assertThat(node.path("approvedAt").isNull()).isTrue();
        assertThat(node.path("sourceEpoch").asText()).isEqualTo(epoch.toString());verify(service,never()).sync(any());
        assertThatThrownBy(()->runner.run(new DefaultApplicationArguments())).isInstanceOf(FileAlreadyExistsException.class);
    }
    @Test void confirmedFilePassesOnlyExplicitOperatorApprovalToScopedService()throws Exception {
        var service=mock(UnitSyncService.class);Path file=directory.resolve("approved.json");
        var approval=new BaselineApproval(1,null,UUID.randomUUID(),Map.of("1","DV"),Map.of(),"fixture-operator","Checked identities",Instant.now());
        mapper.writeValue(file.toFile(),approval);
        new UnitSyncRunner(service,properties(UnitSyncProperties.Operation.CONFIRMED,file)).run(new DefaultApplicationArguments());
        verify(service).sync(eq(approval));verify(service,never()).sync();
    }
    @Test void syncModeHasNoImplicitReviewOrApproval()throws Exception {
        var service=mock(UnitSyncService.class);
        new UnitSyncRunner(service,properties(UnitSyncProperties.Operation.SYNC,null)).run(new DefaultApplicationArguments());
        verify(service).sync();verify(service,never()).sync(any());verify(service,never()).preview();
    }
}
