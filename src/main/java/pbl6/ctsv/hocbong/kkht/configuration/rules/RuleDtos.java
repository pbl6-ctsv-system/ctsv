package pbl6.ctsv.hocbong.kkht.configuration.rules;

import java.time.LocalDate;
import java.util.List;

public final class RuleDtos {
    private RuleDtos() {}
    public record Conditions(String academicAverageMin,int conductScoreMin,String firstAttemptGradeMin,
            boolean withinStandardDuration,String progressRatioMin) {}
    public record Level(String academicAverageMin,int conductScoreMin,String tuitionMultiplier) {}
    public record Levels(Level A,Level B,Level C) {}
    public record Priority(List<String> levelOrder,List<String> rankingOrder,String fallback) {
        public Priority { levelOrder=List.copyOf(levelOrder); rankingOrder=List.copyOf(rankingOrder); }
    }
    public record Rounding(String stage,int scale,String mode) {}
    public record TuitionBasis(String formula,Rounding rounding) {}
    public record TiePolicy(String mode) {}
    public record LastSlot(String minimumRemainderRatio,String comparison,String amountMode) {}
    public record Command(String legalBasis,LocalDate effectiveFrom,LocalDate effectiveTo,Conditions conditions,
            Levels levels,Priority priority,TuitionBasis tuitionBasis,TiePolicy tiePolicy,LastSlot lastSlot) {}
    public record Detail(String id,int version,String legalBasis,String effectiveFrom,String effectiveTo,
            Conditions conditions,Levels levels,Priority priority,TuitionBasis tuitionBasis,TiePolicy tiePolicy,
            LastSlot lastSlot,String status,String usedAt) {}
    public record Page(List<Detail> items,int page,int size,long totalElements) {
        public Page { items=List.copyOf(items); }
    }
    public record View(Detail body,String etag) {}
}
