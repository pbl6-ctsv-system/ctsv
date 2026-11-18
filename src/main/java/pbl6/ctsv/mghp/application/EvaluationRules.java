package pbl6.ctsv.mghp.application;

import java.time.LocalDate;
import java.util.*;
import static pbl6.ctsv.mghp.application.IntakeProblem.*;

final class EvaluationRules {
    static final Set<String> RESULTS=Set.of("dat","khong_dat","can_xac_minh");
    static final Set<String> CRITERIA=Set.of("dat","khong_dat","can_xac_minh","chua_xet","khong_ap_dung");
    static final Set<String> EVIDENCE=Set.of("dat","khong_dat","can_xac_minh","khong_ap_dung");
    record Interval(LocalDate from,LocalDate to) {}
    static void periods(List<Interval> periods,LocalDate from,LocalDate to) {
        if(from==null||to==null||from.isAfter(to))throw conflict("EVALUATION_NOT_READY");
        LocalDate end=null;
        for(var p:periods.stream().sorted(Comparator.comparing(Interval::from)).toList()){
            if(p.from()==null||p.to()==null||p.from().isAfter(p.to())||p.from().isBefore(from)||p.to().isAfter(to)
                ||end!=null&&!p.from().isAfter(end))throw conflict("EVALUATION_NOT_READY");
            end=p.to();
        }
    }
    static boolean criterionReady(String result,String note,boolean required,boolean conditional){
        if(result==null||"chua_xet".equals(result))return !required;
        if("khong_ap_dung".equals(result)&&!conditional)return false;
        return "dat".equals(result)||note!=null&&!note.isBlank();
    }
    static boolean ageReady(String policy,LocalDate birth,LocalDate reference){
        return !"M3".equals(policy)||birth!=null&&reference!=null&&!birth.isAfter(reference);
    }
}
