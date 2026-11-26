package pbl6.ctsv.hocbong.kkht.configuration.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class RuleFixtures {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private RuleFixtures() {}
    static ObjectNode body() {
        try (var input = RuleFixtures.class.getResourceAsStream("/fixtures/hocbong/rules/valid-rule.json")) {
            if (input == null) throw new AssertionError("Missing rule fixture");
            return (ObjectNode) JSON.readTree(input);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }
    static RuleDtos.Command command() { return RuleRules.command(body()); }
}
