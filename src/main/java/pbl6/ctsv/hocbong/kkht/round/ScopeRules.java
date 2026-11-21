package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Contract 0.3. Canonical IDs and numeric ordering are shared by persistence and intent hashing. */
final class ScopeRules {
    static final int LIMIT=2000;
    record Pair(long cohortId,long majorId) {}
    record Selection(String mode,List<Pair> groups) {
        Selection { groups=List.copyOf(groups); }
    }
    record Create(long sourceSemesterId,String catalogRevision,Selection scope) {}
    record Update(String catalogRevision,Selection scope) {}
    static final Comparator<Pair> PAIRS=Comparator.comparingLong(Pair::cohortId).thenComparingLong(Pair::majorId);

    static Create create(JsonNode body) {
        shape(body,Set.of("sourceSemesterId","catalogRevision","scope"),"body");
        return new Create(textId(body.get("sourceSemesterId"),"sourceSemesterId"),revision(body),selection(body.get("scope")));
    }
    static Update update(JsonNode body) {
        shape(body,Set.of("catalogRevision","scope"),"body");
        return new Update(revision(body),selection(body.get("scope")));
    }
    static UUID requestId(String key) {
        if(key==null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            throw RoundFailure.invalid("Idempotency-Key");
        return UUID.fromString(key);
    }
    static UUID lookupId(String key) {
        try { return requestId(key); } catch(RoundFailure e) { throw RoundFailure.invalid("requestId"); }
    }
    private static String revision(JsonNode body) {
        var value=body.get("catalogRevision");
        if(!value.isTextual() || !value.textValue().matches("[A-Za-z0-9_-]{43}")) throw RoundFailure.invalid("catalogRevision");
        // A SHA-256 base64url encoding has exactly 256 bits, including canonical pad bits.
        String revision=value.textValue();
        if(!Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(revision)).equals(revision))
            throw RoundFailure.invalid("catalogRevision");
        return revision;
    }
    private static Selection selection(JsonNode node) {
        if(node==null || !node.isObject() || !node.path("mode").isTextual()) throw RoundFailure.invalid("scope");
        String mode=node.get("mode").textValue();
        if("all".equals(mode)) {shape(node,Set.of("mode"),"scope");return new Selection(mode,List.of());}
        if(!"groups".equals(mode)) throw RoundFailure.invalid("scope");
        shape(node,Set.of("mode","groups"),"scope");
        var groups=node.get("groups");
        if(!groups.isArray() || groups.isEmpty() || groups.size()>LIMIT) throw RoundFailure.invalid("scope");
        var selected=new TreeSet<>(PAIRS);
        for(var group:groups) {
            shape(group,Set.of("cohortId","majorId"),"scope");
            if(!selected.add(new Pair(textId(group.get("cohortId"),"scope"),textId(group.get("majorId"),"scope"))))
                throw RoundFailure.invalid("scope");
        }
        return new Selection(mode,List.copyOf(selected));
    }
    private static long textId(JsonNode value,String field) {
        if(value==null || !value.isTextual()) throw RoundFailure.invalid(field);
        return RoundRules.id(field,value.textValue());
    }
    private static void shape(JsonNode value,Set<String> fields,String field) {
        if(value==null || !value.isObject() || value.size()!=fields.size()) throw RoundFailure.invalid(field);
        for(var key:fields) if(!value.has(key)) throw RoundFailure.invalid(field);
    }
    static byte[] intentHash(Create command) {
        var text=new StringBuilder("hocbong:create:0.3|").append(command.sourceSemesterId()).append('|')
            .append(command.catalogRevision()).append('|').append(command.scope().mode());
        command.scope().groups().stream().sorted(PAIRS).forEach(pair->text.append('|').append(pair.cohortId()).append(':').append(pair.majorId()));
        try {return MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));}
        catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
    static String name(String year,int order,int number) {
        return year.replace('-','/')+" - Học kỳ "+order+" - Đợt "+number;
    }
    private ScopeRules() {}
}
