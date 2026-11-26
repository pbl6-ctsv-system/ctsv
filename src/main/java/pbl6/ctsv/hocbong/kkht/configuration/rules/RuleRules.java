package pbl6.ctsv.hocbong.kkht.configuration.rules;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import pbl6.ctsv.hocbong.kkht.round.RoundFailure;

final class RuleRules {
    private RuleRules() {}
    static RuleDtos.Command command(JsonNode body) {
        object(body,"body","legalBasis","effectiveFrom","effectiveTo","conditions","levels","priority","tuitionBasis","tiePolicy","lastSlot");
        String basis=text(body,"legalBasis",4000);
        LocalDate from=date(body,"effectiveFrom"),to=body.get("effectiveTo").isNull()?null:date(body,"effectiveTo");
        if(to!=null&&to.isBefore(from))throw RoundFailure.invalid("effectiveTo");
        var c=body.get("conditions");
        object(c,"conditions","academicAverageMin","conductScoreMin","firstAttemptGradeMin","withinStandardDuration","progressRatioMin");
        if(!c.path("withinStandardDuration").isBoolean()||!c.path("withinStandardDuration").booleanValue())throw RoundFailure.invalid("conditions.withinStandardDuration");
        var conditions=new RuleDtos.Conditions(decimal(c,"academicAverageMin",2,BigDecimal.ZERO,BigDecimal.TEN),
                integer(c,"conductScoreMin",0,100),decimal(c,"firstAttemptGradeMin",2,BigDecimal.ZERO,BigDecimal.TEN),
                true,decimal(c,"progressRatioMin",10,new BigDecimal("1"),new BigDecimal("999999999.9999999999")));
        var l=body.get("levels");object(l,"levels","A","B","C");
        var levels=new RuleDtos.Levels(level(l.get("A"),"A"),level(l.get("B"),"B"),level(l.get("C"),"C"));
        if(d(levels.A().academicAverageMin()).compareTo(d(levels.B().academicAverageMin()))<=0
                ||d(levels.B().academicAverageMin()).compareTo(d(levels.C().academicAverageMin()))<=0
                ||d(levels.C().academicAverageMin()).compareTo(d(conditions.academicAverageMin()))<0
                ||levels.A().conductScoreMin()<=levels.B().conductScoreMin()||levels.B().conductScoreMin()<=levels.C().conductScoreMin()
                ||levels.C().conductScoreMin()<conditions.conductScoreMin()
                ||d(levels.A().tuitionMultiplier()).compareTo(d(levels.B().tuitionMultiplier()))<0
                ||d(levels.B().tuitionMultiplier()).compareTo(d(levels.C().tuitionMultiplier()))<0)throw RoundFailure.invalid("levels");
        var p=body.get("priority");object(p,"priority","levelOrder","rankingOrder","fallback");
        var order=List.of("A","B","C");var ranking=List.of("ACADEMIC_AVERAGE_DESC","CONDUCT_SCORE_DESC");
        exactArray(p,"levelOrder",order);exactArray(p,"rankingOrder",ranking);constant(p,"fallback","HIGHEST_ELIGIBLE");
        var priority=new RuleDtos.Priority(order,ranking,"HIGHEST_ELIGIBLE");
        var tuition=body.get("tuitionBasis");object(tuition,"tuitionBasis","formula","rounding");
        constant(tuition,"formula","ACTUAL_PAID_FIRST_ATTEMPT_CREDIT_RATIO");
        var rounding=tuition.get("rounding");object(rounding,"tuitionBasis.rounding","stage","scale","mode");
        constant(rounding,"stage","FINAL_AMOUNT");integer(rounding,"scale",0,0);constant(rounding,"mode","HALF_UP");
        var tuitionBasis=new RuleDtos.TuitionBasis("ACTUAL_PAID_FIRST_ATTEMPT_CREDIT_RATIO",new RuleDtos.Rounding("FINAL_AMOUNT",0,"HALF_UP"));
        var tie=body.get("tiePolicy");object(tie,"tiePolicy","mode");constant(tie,"mode","COUNCIL_WHEN_BOTH_SCORES_EQUAL");
        var last=body.get("lastSlot");object(last,"lastSlot","minimumRemainderRatio","comparison","amountMode");
        constant(last,"minimumRemainderRatio","0.5000000000");constant(last,"comparison","GT");constant(last,"amountMode","REMAINING_FUND");
        return new RuleDtos.Command(basis,from,to,conditions,levels,priority,tuitionBasis,
                new RuleDtos.TiePolicy("COUNCIL_WHEN_BOTH_SCORES_EQUAL"),new RuleDtos.LastSlot("0.5000000000","GT","REMAINING_FUND"));
    }
    private static RuleDtos.Level level(JsonNode node,String name) {
        object(node,"levels."+name,"academicAverageMin","conductScoreMin","tuitionMultiplier");
        return new RuleDtos.Level(decimal(node,"academicAverageMin",2,BigDecimal.ZERO,BigDecimal.TEN),
                integer(node,"conductScoreMin",0,100),decimal(node,"tuitionMultiplier",4,new BigDecimal("0.0001"),new BigDecimal("999999999999999.9999")));
    }
    private static void object(JsonNode node,String path,String...keys) {
        if(node==null||!node.isObject())throw RoundFailure.invalid(path);
        var allowed=Set.of(keys);
        node.fieldNames().forEachRemaining(key->{if(!allowed.contains(key))throw RoundFailure.invalid(path+"."+key);});
        for(String key:keys)if(!node.has(key))throw RoundFailure.invalid(path+"."+key);
    }
    private static String text(JsonNode node,String field,int max) {
        var value=node.get(field);
        if(value==null||!value.isTextual()||value.textValue().isBlank()||value.textValue().length()>max)throw RoundFailure.invalid(field);
        String text=value.textValue();
        for(int i=0;i<text.length();i++)if(Character.isSurrogate(text.charAt(i))) {
            if(!Character.isHighSurrogate(text.charAt(i))||i+1==text.length()||!Character.isLowSurrogate(text.charAt(++i)))throw RoundFailure.invalid(field);
        }
        return text;
    }
    private static String decimal(JsonNode node,String field,int scale,BigDecimal min,BigDecimal max) {
        String value=text(node,field,30);
        if(!value.matches("(0|[1-9][0-9]*)\\.[0-9]{"+scale+"}"))throw RoundFailure.invalid(field);
        BigDecimal number=d(value);
        if(number.compareTo(min)<0||number.compareTo(max)>0)throw RoundFailure.invalid(field);
        return value;
    }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static int integer(JsonNode node,String field,int min,int max) {
        var value=node.get(field);
        if(value==null||!value.isIntegralNumber()||!value.canConvertToInt()||value.intValue()<min||value.intValue()>max)throw RoundFailure.invalid(field);
        return value.intValue();
    }
    private static LocalDate date(JsonNode node,String field) {
        String value=text(node,field,10);
        try { if(!value.matches("(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new DateTimeParseException("SQL date",value,0);return LocalDate.parse(value); }
        catch(DateTimeParseException e) { throw RoundFailure.invalid(field); }
    }
    private static void constant(JsonNode node,String field,String expected) { if(!expected.equals(text(node,field,128)))throw RoundFailure.invalid(field); }
    private static void exactArray(JsonNode node,String field,List<String> expected) {
        var value=node.get(field);if(value==null||!value.isArray()||value.size()!=expected.size())throw RoundFailure.invalid(field);
        for(int i=0;i<expected.size();i++)if(!value.get(i).isTextual()||!expected.get(i).equals(value.get(i).textValue()))throw RoundFailure.invalid(field);
    }
}
