package pbl6.ctsv.hocbong.kkht.configuration.rules;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import pbl6.ctsv.hocbong.kkht.configuration.rules.entity.RuleVersion;
import pbl6.ctsv.hocbong.kkht.round.RoundAccess;
import pbl6.ctsv.hocbong.kkht.round.RoundFailure;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipAudit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuleServiceTest {
    private final RuleRepository repository=mock(RuleRepository.class);
    private final RoundAccess.Actor actor=new RoundAccess.Actor(10,null,20L);
    private RuleService service;
    private RuleVersion row;
    @BeforeEach void setup() {
        service=new RuleService(repository,RuleFixtures.JSON,Clock.fixed(Instant.parse("2026-11-26T00:00:00Z"),ZoneOffset.UTC));
        row=new RuleVersion();row.setNumber(7);row.setStatus("draft");
        var c=RuleFixtures.command();row.setLegalBasis(c.legalBasis());row.setEffectiveFrom(c.effectiveFrom());row.setEffectiveTo(c.effectiveTo());
        row.setConditionsJson(RuleFixtures.JSON.valueToTree(c.conditions()).toString());
        row.setLevelsJson(RuleFixtures.JSON.valueToTree(c.levels()).toString());row.setPriorityJson(RuleFixtures.JSON.valueToTree(c.priority()).toString());
        row.setTuitionJson(RuleFixtures.JSON.valueToTree(c.tuitionBasis()).toString());row.setTieJson(RuleFixtures.JSON.valueToTree(c.tiePolicy()).toString());
        row.setLastSlotRatio(new java.math.BigDecimal("0.5000000000"));row.setLastSlotMode("REMAINING_FUND");
        ReflectionTestUtils.setField(row,"id",7L);ReflectionTestUtils.setField(row,"version",new byte[]{0,0,0,0,0,0,0,1});
        when(repository.get(7)).thenReturn(row);when(repository.lock(7)).thenReturn(row);when(repository.nextNumber()).thenReturn(8);
        doAnswer(call->{var entity=(RuleVersion)call.getArgument(0);ReflectionTestUtils.setField(entity,"id",entity.getId()==null?8L:entity.getId());ReflectionTestUtils.setField(entity,"version",new byte[]{0,0,0,0,0,0,0,2});return null;}).when(repository).save(any());
    }
    @Test void HB_RULE_05_createUsesServerVersionAndRetainsFullTypedSnapshotWithAudit() throws Exception {
        var view=service.create(RuleFixtures.command(),actor);
        assertThat(view.body().id()).isEqualTo("8");assertThat(view.body().version()).isEqualTo(8);
        assertThat(view.body().status()).isEqualTo("draft");assertThat(view.body().usedAt()).isNull();
        assertThat(view.body().levels().A().tuitionMultiplier()).isEqualTo("1.5000");
        var event=org.mockito.ArgumentCaptor.forClass(ScholarshipAudit.class);verify(repository).audit(event.capture());
        assertThat(event.getValue().getAction()).isEqualTo("rule.created");assertThat(event.getValue().getUserId()).isEqualTo(10);
        assertThat(event.getValue().getBeforeData()).isNull();assertThat(event.getValue().getOccurredAt()).isEqualTo(OffsetDateTime.parse("2026-11-26T00:00:00Z"));
        assertThat(RuleFixtures.JSON.readTree(event.getValue().getAfterData()).at("/lastSlot/comparison").textValue()).isEqualTo("GT");
    }
    @Test void HB_RULE_06_updatePreservesBusinessVersionAndReturnsFreshTagAndBeforeAfter() {
        var old=service.get(7);var next=service.update(7,RuleFixtures.command(),old.etag(),actor);
        assertThat(next.body().version()).isEqualTo(7);assertThat(next.etag()).isNotEqualTo(old.etag());
        verify(repository,never()).nextNumber();verify(repository).audit(argThat(e->e.getAction().equals("rule.updated")&&e.getBeforeData()!=null&&e.getAfterData()!=null));
    }
    @Test void HB_RULE_06_preconditionErrorsNeverPersistOrAudit() {
        for(String tag:new String[]{null,"*","W/\"v\"","\"a\", \"b\"","\"stale\""})assertThatThrownBy(()->service.update(7,RuleFixtures.command(),tag,actor)).isInstanceOf(RoundFailure.class);
        verify(repository,never()).save(any());verify(repository,never()).audit(any());
    }
    @Test void HB_RULE_07_usedVersionRemainsImmutableAndItsUsageChangesRepresentationTag() {
        var old=service.get(7);row.setStatus("used");ReflectionTestUtils.setField(row,"usedAt",OffsetDateTime.parse("2026-11-26T01:00:00Z"));
        var used=service.get(7);assertThat(used.body().usedAt()).isEqualTo("2026-11-26T01:00:00Z");assertThat(used.etag()).isNotEqualTo(old.etag());
        assertThatThrownBy(()->service.update(7,RuleFixtures.command(),used.etag(),actor)).hasMessage("RULE_VERSION_IN_USE");verify(repository,never()).save(any());
    }
    @Test void HB_RULE_04_listKeepsRequestedPageAndTotalWithoutMutations() {
        when(repository.list(2,10)).thenReturn(List.of(row));when(repository.count()).thenReturn(21L);
        var page=service.list(new int[]{2,10});assertThat(page.page()).isEqualTo(2);assertThat(page.size()).isEqualTo(10);assertThat(page.totalElements()).isEqualTo(21);assertThat(page.items()).extracting(RuleDtos.Detail::id).containsExactly("7");verify(repository,never()).save(any());
    }
    @Test void HB_RULE_09_auditFailureIsPropagatedForTransactionRollback() {
        doThrow(new IllegalStateException("synthetic audit failure")).when(repository).audit(any());
        assertThatThrownBy(()->service.create(RuleFixtures.command(),actor)).hasMessage("synthetic audit failure");
    }
    @Test void HB_RULE_11_corruptStoredJsonFailsClosedInsteadOfReturningUncheckedRules() {
        row.setConditionsJson("{}");assertThatThrownBy(()->service.get(7)).isInstanceOf(IllegalStateException.class);
    }
}
