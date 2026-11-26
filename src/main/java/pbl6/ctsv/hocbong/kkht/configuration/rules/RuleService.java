package pbl6.ctsv.hocbong.kkht.configuration.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import pbl6.ctsv.hocbong.kkht.configuration.rules.entity.RuleVersion;
import pbl6.ctsv.hocbong.kkht.round.RoundAccess;
import pbl6.ctsv.hocbong.kkht.round.RoundFailure;
import pbl6.ctsv.hocbong.kkht.round.RoundRules;
import pbl6.ctsv.hocbong.kkht.round.entity.ScholarshipAudit;

class RuleService {
    private final RuleRepository repository;
    private final ObjectMapper json;
    private final Clock clock;
    RuleService(RuleRepository repository,ObjectMapper json,Clock clock) { this.repository=repository;this.json=json;this.clock=clock; }
    RuleDtos.Page list(int[] page) { return new RuleDtos.Page(repository.list(page[0],page[1]).stream().map(this::detail).toList(),page[0],page[1],repository.count()); }
    RuleDtos.View get(long id) { return view(repository.get(id)); }
    RuleDtos.View create(RuleDtos.Command command,RoundAccess.Actor actor) {
        repository.serializeWrites();
        var row=new RuleVersion();row.setNumber(repository.nextNumber());row.setStatus("draft");apply(row,command);repository.save(row);
        var result=view(row);audit(actor,"rule.created",null,result.body());return result;
    }
    RuleDtos.View update(long id,RuleDtos.Command command,String ifMatch,RoundAccess.Actor actor) {
        repository.serializeWrites();var row=repository.lock(id);var before=view(row);RoundRules.match(before.etag(),ifMatch);
        if(row.getUsedAt()!=null||!"draft".equals(row.getStatus()))throw new RoundFailure(409,"RULE_VERSION_IN_USE");
        apply(row,command);repository.save(row);var result=view(row);audit(actor,"rule.updated",before.body(),result.body());return result;
    }
    private void apply(RuleVersion row,RuleDtos.Command c) {
        row.setLegalBasis(c.legalBasis());row.setEffectiveFrom(c.effectiveFrom());row.setEffectiveTo(c.effectiveTo());
        row.setConditionsJson(encode(c.conditions()));row.setLevelsJson(encode(c.levels()));row.setPriorityJson(encode(c.priority()));
        row.setTuitionJson(encode(c.tuitionBasis()));row.setTieJson(encode(c.tiePolicy()));
        row.setLastSlotRatio(new BigDecimal(c.lastSlot().minimumRemainderRatio()));row.setLastSlotMode(c.lastSlot().amountMode());
    }
    private RuleDtos.Detail detail(RuleVersion row) {
        try {
            var node=json.createObjectNode().put("legalBasis",row.getLegalBasis()).put("effectiveFrom",row.getEffectiveFrom().toString());
            if(row.getEffectiveTo()==null)node.putNull("effectiveTo");else node.put("effectiveTo",row.getEffectiveTo().toString());
            node.set("conditions",json.readTree(row.getConditionsJson()));node.set("levels",json.readTree(row.getLevelsJson()));node.set("priority",json.readTree(row.getPriorityJson()));
            node.set("tuitionBasis",json.readTree(row.getTuitionJson()));node.set("tiePolicy",json.readTree(row.getTieJson()));
            node.putObject("lastSlot").put("minimumRemainderRatio",row.getLastSlotRatio().toPlainString()).put("comparison","GT").put("amountMode",row.getLastSlotMode());
            var c=RuleRules.command(node);
            return new RuleDtos.Detail(row.getId().toString(),row.getNumber(),c.legalBasis(),c.effectiveFrom().toString(),c.effectiveTo()==null?null:c.effectiveTo().toString(),
                    c.conditions(),c.levels(),c.priority(),c.tuitionBasis(),c.tiePolicy(),c.lastSlot(),row.getStatus(),
                    row.getUsedAt()==null?null:row.getUsedAt().toInstant().toString());
        } catch(Exception e) { throw new IllegalStateException("Stored rule version is invalid",e); }
    }
    private RuleDtos.View view(RuleVersion row) {
        var body=detail(row);
        try {
            var hash=MessageDigest.getInstance("SHA-256");hash.update(("hocbong:rule:"+row.getId()+"|application/json|").getBytes(StandardCharsets.UTF_8));hash.update(row.getVersion());
            return new RuleDtos.View(body,"\""+Base64.getUrlEncoder().withoutPadding().encodeToString(hash.digest(json.writeValueAsBytes(body)))+"\"");
        } catch(Exception e) { throw new IllegalStateException("Cannot encode rule representation",e); }
    }
    private String encode(Object body) { try { return json.writeValueAsString(body); } catch(Exception e) { throw new IllegalStateException("Cannot encode rule JSON",e); } }
    private void audit(RoundAccess.Actor actor,String action,RuleDtos.Detail before,RuleDtos.Detail after) {
        var event=new ScholarshipAudit();event.setUserId(actor.userId());event.setRoleId(actor.roleId());event.setObjectType("rule_version");event.setObjectId(Long.parseLong(after.id()));
        event.setScope("HOC_BONG");event.setAction(action);event.setBeforeData(before==null?null:encode(before));event.setAfterData(encode(after));
        event.setOccurredAt(OffsetDateTime.now(clock));event.setCorrelationId(UUID.randomUUID());repository.audit(event);
    }
}
