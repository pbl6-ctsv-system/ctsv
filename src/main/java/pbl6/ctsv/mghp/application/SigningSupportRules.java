package pbl6.ctsv.mghp.application;
/** Incident bookkeeping only; no transition grants authority to modify a signing request. */
final class SigningSupportRules {
 private SigningSupportRules(){}
 static String publicStatus(String state){return "failed".equals(state)||"unknown".equals(state)?"needs_support":state;}
 static void body(com.fasterxml.jackson.databind.JsonNode body,java.util.List<String> required,java.util.List<String> optional){
  if(body==null||!body.isObject())throw IntakeProblem.invalid("body");
  for(String key:required)if(!body.has(key))throw IntakeProblem.invalid(key);
  body.fieldNames().forEachRemaining(key->{if(!required.contains(key)&&!optional.contains(key))throw IntakeProblem.invalid(key);});
 }
 static String transition(String state,String action){
  if("start".equals(action)&&("waiting".equals(state)||"working".equals(state)))return "working";
  if("finish".equals(action)&&("working".equals(state)||"resolved".equals(state)))return "resolved";
  throw IntakeProblem.conflict("SUPPORT_TRANSITION_UNAVAILABLE");
 }
}
