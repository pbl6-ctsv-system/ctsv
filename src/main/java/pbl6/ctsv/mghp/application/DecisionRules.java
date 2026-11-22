package pbl6.ctsv.mghp.application;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
final class DecisionRules {
 record Source(long student,long process,long semester,String scope,long evaluation,long policyVersion,long period,BigDecimal rate,Integer priority,LocalDate from,LocalDate to){}
 private record Group(long student,long process,long semester,String scope){}
 static List<Source> winners(List<Source> sources){
  var groups=new LinkedHashMap<Group,List<Source>>();
  for(var s:sources){
   if(s.rate()==null||s.rate().signum()<0||s.rate().compareTo(BigDecimal.ONE)>0||s.from()==null||s.to()==null||s.from().isAfter(s.to()))throw IntakeProblem.invalid("eligiblePeriods");
   groups.computeIfAbsent(new Group(s.student(),s.process(),s.semester(),s.scope()),k->new ArrayList<>()).add(s);
  }
  var order=Comparator.comparing(Source::rate,Comparator.reverseOrder()).thenComparing(Source::priority,Comparator.nullsLast(Comparator.naturalOrder())).thenComparingLong(Source::policyVersion).thenComparingLong(Source::evaluation);
  var result=new ArrayList<Source>();
  for(var group:groups.values()){
   var winner=group.stream().min(order).orElseThrow();
   var periods=group.stream().filter(s->s.evaluation()==winner.evaluation()).sorted(Comparator.comparing(Source::from).thenComparing(Source::to).thenComparingLong(Source::period)).toList();
   Source previous=null;
   for(var p:periods){
    if(p.rate().compareTo(winner.rate())!=0||p.policyVersion()!=winner.policyVersion()||previous!=null&&!p.from().isAfter(previous.to()))throw IntakeProblem.conflict("INTERVAL_CONFLICT");
    result.add(p);previous=p;
   }
  }
  return List.copyOf(result);
 }
}
