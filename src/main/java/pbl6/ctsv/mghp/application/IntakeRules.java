package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.*;

final class IntakeRules {
    private IntakeRules() {}
    static final List<String> CLAIMS=List.of("fullName","citizenId","birthDate","birthPlace","gender","ethnicityId","classId","studentCode","cohortId","managementUnitId","permanentAddress","phoneNumber");
    static final List<String> PEOPLE=List.of("relation","fullName","citizenId","ethnicityId","permanentAddress","localityCode","workplaceUnitId","workplaceName","workplaceGroup","employmentStatus","statusFrom","statusUntil","matchingStaffCode");
    static final List<String> STATEMENTS=List.of("firstDegree","alreadyBenefitedAtSameLevel","salariedWhileStudying","declarationConfirmed");
    static final List<String> DOCUMENT=List.of("requirementId","fileId","documentNumber","issuer","issuedOn","expiresOn","relatedPersonIndex");
    static final Set<String> STATUSES=Set.of("submitted","processing","supplementing","supplement_responded","approved","rejected","completed","withdrawn","reopened","UNSPECIFIED");

    static JsonNode shape(JsonNode value,Collection<String> keys) {
        if(value==null || !value.isObject() || value.size()!=keys.size()) throw IntakeProblem.invalid("body");
        for(var key:keys) if(!value.has(key)) throw IntakeProblem.invalid(key);
        return value;
    }

    static String text(JsonNode v,String field,int max,boolean required) {
        if(!required && v.isNull()) return null;
        if(!v.isTextual() || v.textValue().length()>max || (required && v.textValue().isBlank()))
            throw IntakeProblem.invalid(field);
        return v.textValue();
    }

    static long id(String value,String field) {
        if(value==null || !value.matches("[1-9][0-9]{0,18}")) throw IntakeProblem.invalid(field);
        try { return Long.parseLong(value); }
        catch(NumberFormatException failure) { throw IntakeProblem.invalid(field); }
    }

    static long id(JsonNode value,String field) { return id(text(value,field,19,true),field); }
    static Long nullableId(JsonNode n,String key) { return n.get(key).isNull()?null:id(n.get(key),key); }

    static LocalDate date(JsonNode v,String key,boolean required) {
        String value=text(v,key,10,required);
        if(value==null)return null;
        try {
            var parsed=LocalDate.parse(value);
            if(parsed.getYear()<1 || !parsed.toString().equals(value)) throw IntakeProblem.invalid(key);
            return parsed;
        } catch(java.time.DateTimeException e){throw IntakeProblem.invalid(key);}
    }

    static Boolean bool(JsonNode value,String key) {
        if(value.isNull())return null;
        if(!value.isBoolean())throw IntakeProblem.invalid(key);
        return value.booleanValue();
    }

    static int page(String value,String field,int fallback,int min,int max) {
        if(value==null) return fallback;
        try {
            if(!value.matches("0|[1-9][0-9]{0,9}")) throw IntakeProblem.invalid(field);
            int parsed=Integer.parseInt(value);
            if(parsed<min || parsed>max)throw IntakeProblem.invalid(field);
            return parsed;
        } catch(NumberFormatException e){throw IntakeProblem.invalid(field);}
    }

    static UUID operation(JsonNode v) {
        String value=text(v,"operationId",36,true);
        try {
            var id=UUID.fromString(value);
            if(!id.toString().equalsIgnoreCase(value))throw IntakeProblem.invalid("operationId");
            return id;
        } catch(IllegalArgumentException e){throw IntakeProblem.invalid("operationId");}
    }

    static List<JsonNode> array(JsonNode v,String key,boolean required) {
        if(!v.isArray() || required && v.isEmpty())throw IntakeProblem.invalid(key);
        List<JsonNode> result=new ArrayList<>();
        v.forEach(result::add);
        return result;
    }

    static String status(String value) {
        if(value!=null&&!STATUSES.contains(value))throw IntakeProblem.invalid("status");
        return value;
    }
}
