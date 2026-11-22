package pbl6.ctsv.mghp.application;

import java.util.Map;
import java.util.Set;

final class ApplicationDispositionRules {
 private ApplicationDispositionRules() {}
 static boolean accepts(String status){
  return status!=null&&Set.of("submitted","processing","supplementing","supplement_responded").contains(status);
 }
 static boolean pdfReady(Map<String,String> currentChecks){
  return "pass".equals(currentChecks.get("content"))&&currentChecks.entrySet().stream()
   .filter(e->!"hard_copy".equals(e.getKey()))
   .noneMatch(e->Set.of("fail","needs_verification").contains(e.getValue()));
 }
 static boolean generalTarget(Long requirement,Long document,Long semester,Long evaluation,String field){
  return requirement==null&&document==null&&semester==null&&evaluation==null&&field==null;
 }
}
