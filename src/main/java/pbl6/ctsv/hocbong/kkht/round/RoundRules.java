package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;

/** T00 v0.2.1: source codes determine scholarship periods; raw source order is never used. */
final class RoundRules {
    record Period(String academicYear,int semesterOrder,boolean summer) {}
    static Optional<Period> period(String code) {
        if(code==null || !code.matches("[0-9]{2}(10|20|21)")) return Optional.empty();
        int year=2000+Integer.parseInt(code.substring(0,2));
        return Optional.of(new Period(year+"-"+(year+1),code.endsWith("10")?1:2,code.endsWith("21")));
    }
    static long id(String field,String value) {
        try {
            if(value==null || !value.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            return Long.parseLong(value);
        } catch(NumberFormatException e) { throw RoundFailure.invalid(field); }
    }
    static int[] page(String page,String size) {
        int p=page==null?0:queryInteger("page",page,true);
        int s=size==null?20:queryInteger("size",size,false);
        if(s>100) throw RoundFailure.invalid("size");
        if((long)p*s>100000) throw RoundFailure.invalid("page");
        return new int[]{p,s};
    }
    private static int queryInteger(String field,String value,boolean zeroAllowed) {
        try {
            if(!value.matches(zeroAllowed?"0|[1-9][0-9]*":"[1-9][0-9]*")) throw new NumberFormatException();
            return Integer.parseInt(value);
        } catch(NumberFormatException e) { throw RoundFailure.invalid(field); }
    }
    static String queryText(String value) {
        if(value==null) return null;
        String q=value.strip();
        if(q.codePointCount(0,q.length())>128) throw RoundFailure.invalid("q");
        return q.isEmpty()?null:q;
    }
    static String tag(long id,byte[] version,Object body,ObjectMapper json) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            digest.update(("hocbong:round:"+id+"|application/json|identity|").getBytes(StandardCharsets.UTF_8));
            digest.update(version);
            return "\""+Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(json.writeValueAsBytes(body)))+"\"";
        } catch(Exception e) { throw new IllegalStateException("Cannot encode round representation",e); }
    }
    static void match(String actual,String supplied) {
        if(supplied==null) throw new RoundFailure(428,"PRECONDITION_REQUIRED","If-Match");
        if(!supplied.matches("\"[A-Za-z0-9_-]+\"")) throw RoundFailure.invalid("If-Match");
        if(!actual.equals(supplied)) throw new RoundFailure(412,"VERSION_CONFLICT","If-Match");
    }
}
