package pbl6.ctsv.hocbong.kkht.configuration.combination;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import pbl6.ctsv.hocbong.kkht.round.RoundFailure;
import pbl6.ctsv.hocbong.kkht.round.RoundRules;

final class CombinationRules {
    private CombinationRules() {}

    static CombinationDtos.Command command(JsonNode body) {
        if (body==null || !body.isObject()) throw RoundFailure.invalid("body");
        Set<String> fields=Set.of("programId","cohortId","majorId","curriculumCode","standardSemesters",
                "programType","effectiveFrom","effectiveTo");
        body.fieldNames().forEachRemaining(field -> {
            if (!fields.contains(field)) throw RoundFailure.invalid(field);
        });
        long program=RoundRules.id("programId",text(body,"programId"));
        long cohort=RoundRules.id("cohortId",text(body,"cohortId"));
        long major=RoundRules.id("majorId",text(body,"majorId"));
        String curriculum=text(body,"curriculumCode");
        if (curriculum.isBlank() || curriculum.length()>512) throw RoundFailure.invalid("curriculumCode");
        var semesters=body.get("standardSemesters");
        if (semesters==null || !semesters.isIntegralNumber() || !semesters.canConvertToInt()
                || semesters.intValue()<1 || semesters.intValue()>Short.MAX_VALUE) {
            throw RoundFailure.invalid("standardSemesters");
        }
        String type=text(body,"programType");
        if (!Set.of("CN","KS").contains(type)) throw RoundFailure.invalid("programType");
        LocalDate since=date(body,"effectiveFrom");
        LocalDate until=body.path("effectiveTo").isMissingNode() || body.path("effectiveTo").isNull()
                ? null : date(body,"effectiveTo");
        if (until!=null && until.isBefore(since)) throw RoundFailure.invalid("effectiveTo");
        return new CombinationDtos.Command(program,cohort,major,curriculum,semesters.intValue(),type,since,until);
    }

    private static String text(JsonNode body,String field) {
        var value=body.get(field);
        if (value==null || !value.isTextual()) throw RoundFailure.invalid(field);
        return value.textValue();
    }

    private static LocalDate date(JsonNode body,String field) {
        String text=text(body,field);
        try {
            if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new DateTimeParseException("date",text,0);
            LocalDate value=LocalDate.parse(text);
            if (value.getYear()<1) throw new DateTimeParseException("SQL date",text,0);
            return value;
        } catch (DateTimeParseException ex) { throw RoundFailure.invalid(field); }
    }
}
