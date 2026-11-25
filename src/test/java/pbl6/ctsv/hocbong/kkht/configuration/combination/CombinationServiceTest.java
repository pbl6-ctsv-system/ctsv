package pbl6.ctsv.hocbong.kkht.configuration.combination;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pbl6.ctsv.foundation.api.FoundationAccess;
import pbl6.ctsv.foundation.api.ScholarshipCombinationSource;
import pbl6.ctsv.hocbong.kkht.configuration.combination.entity.TrainingCombination;
import pbl6.ctsv.hocbong.kkht.round.RoundAccess;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipAudit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CombinationServiceTest {
    private final CombinationRepository repository=mock(CombinationRepository.class);
    private final ScholarshipCombinationSource source=mock(ScholarshipCombinationSource.class);
    private final CombinationUsage usage=mock(CombinationUsage.class);
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final CombinationDtos.Command command=new CombinationDtos.Command(1,2,3,"SYN-KHUNG",8,"CN",
            LocalDate.of(2024,9,1),null);
    private final RoundAccess.Actor actor=new RoundAccess.Actor(10,null,20L);
    private CombinationService service;
    private TrainingCombination row;

    @BeforeEach void setup() {
        service=new CombinationService(repository,source,List.of(usage),json,Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"),ZoneOffset.UTC));
        row=new TrainingCombination();
        ReflectionTestUtils.setField(row,"id",7L);
        ReflectionTestUtils.setField(row,"version",new byte[]{0,0,0,0,0,0,0,1});
        row.setProgramId(1L); row.setProgramCode("P"); row.setProgramName("Tên nguồn");
        row.setCohortId(2L); row.setCohortCode("K"); row.setMajorId(3L); row.setMajorCode("N");
        row.setCurriculumCode("SYN-KHUNG"); row.setStandardSemesters((short)8); row.setProgramType("CN");
        row.setEffectiveFrom(command.effectiveFrom());
        when(repository.get(7)).thenReturn(row); when(repository.lock(7)).thenReturn(row);
        when(source.resolve(1,2,3,"SYN-KHUNG")).thenReturn(new ScholarshipCombinationSource.Resolved(1,"P","Tên nguồn",2,"K",3,"N","SYN-KHUNG"));
        doAnswer(invocation -> {
            TrainingCombination value=invocation.getArgument(0);
            ReflectionTestUtils.setField(value,"id",7L);
            ReflectionTestUtils.setField(value,"version",new byte[]{0,0,0,0,0,0,0,2});
            return null;
        }).when(repository).save(any());
    }

    @Test void HB_COMB_04_createCopiesNamesFromSourceAndAppendsAudit() throws Exception {
        var view=service.create(command,actor);
        assertThat(view.body().programName()).isEqualTo("Tên nguồn");
        assertThat(view.body().id()).isEqualTo("7");
        assertThat(view.etag()).matches("\"[A-Za-z0-9_-]+\"");
        var events=org.mockito.ArgumentCaptor.forClass(ScholarshipAudit.class);
        verify(repository).audit(events.capture());
        assertThat(events.getValue().getUserId()).isEqualTo(10);
        assertThat(events.getValue().getAction()).isEqualTo("combination.created");
        assertThat(json.readTree(events.getValue().getAfterData()).get("majorId").asText()).isEqualTo("3");
    }

    @Test void HB_COMB_03_overlapStopsWriteAndAudit() {
        when(repository.overlaps(0,command)).thenReturn(true);
        assertThatThrownBy(() -> service.create(command,actor)).hasMessage("COMBINATION_OVERLAP");
        verify(repository,never()).save(any()); verify(repository,never()).audit(any());
    }

    @Test void HB_COMB_01_sourceFailureDoesNotSaveOrInventCodes() {
        when(source.resolve(anyLong(),anyLong(),anyLong(),anyString()))
                .thenThrow(new FoundationAccess.Failure(400,"INVALID_COMBINATION_SOURCE","majorId"));
        assertThatThrownBy(() -> service.create(command,actor)).hasMessage("INVALID_COMBINATION_SOURCE");
        verify(repository,never()).save(any()); verify(repository,never()).audit(any());
    }

    @Test void HB_COMB_07_checksRequiredMalformedAndStaleTagsBeforeSourceOrWrite() {
        assertThatThrownBy(() -> service.update(7,command,null,actor)).hasMessage("PRECONDITION_REQUIRED");
        assertThatThrownBy(() -> service.update(7,command,"*",actor)).hasMessage("VALIDATION_ERROR");
        assertThatThrownBy(() -> service.update(7,command,"\"stale\"",actor)).hasMessage("VERSION_CONFLICT");
        verifyNoInteractions(source); verify(repository,never()).save(any());
    }

    @Test void HB_COMB_04_updateReturnsFreshTagAndAuditBeforeAfter() {
        String tag=service.get(7).etag();
        var view=service.update(7,command,tag,actor);
        assertThat(view.etag()).isNotEqualTo(tag);
        verify(repository).overlaps(7,command);
        verify(repository).audit(argThat(event -> event.getBeforeData()!=null && event.getAction().equals("combination.updated")));
    }

    @Test void HB_COMB_04_usedConfigurationCannotBeUpdatedOrDeleted() {
        when(usage.used(7)).thenReturn(true);
        String tag=service.get(7).etag();
        assertThatThrownBy(() -> service.update(7,command,tag,actor)).hasMessage("COMBINATION_IN_USE");
        assertThatThrownBy(() -> service.delete(7,tag,actor)).hasMessage("COMBINATION_IN_USE");
        verify(repository,never()).save(any()); verify(repository,never()).delete(any());
    }

    @Test void HB_COMB_04_deleteRequiresFreshTagAndRetainsTombstoneAudit() {
        assertThatThrownBy(() -> service.delete(7,"\"stale\"",actor)).hasMessage("VERSION_CONFLICT");
        service.delete(7,service.get(7).etag(),actor);
        verify(repository).delete(row);
        verify(repository).audit(argThat(event -> event.getAction().equals("combination.deleted")
                && event.getAfterData().contains("\"deleted\":true") && event.getBeforeData()!=null));
    }
}
