package pbl6.ctsv.mghp.application;

import java.util.Set;

/** Pure business guards; provider/storage observations never grant a capability. */
final class SigningRules {
 private SigningRules(){}
 static void bindable(String state,String dataset,String bound,boolean ready){
  if(!"locked".equals(state))throw IntakeProblem.conflict("DOCUMENT_SOURCE_NOT_LOCKED");
  if(dataset==null||!dataset.matches("[a-f0-9]{64}")||!dataset.equals(bound))throw IntakeProblem.conflict("DOCUMENT_DATASET_MISMATCH");
  if(!ready)throw IntakeProblem.conflict("FILE_NOT_READY");
 }
 static void replaceable(String state){
  if(state!=null&&!Set.of("prepared","refused").contains(state))throw IntakeProblem.conflict("DOCUMENT_ALREADY_USED");
 }
 static void canAct(long actor,long assigned,String state,int order,int current,boolean minutesReady){
  if(actor!=assigned)throw new IntakeProblem(403,"FORBIDDEN","request");
  if(!minutesReady)throw IntakeProblem.conflict("MINUTES_NOT_COMPLETED");
  if(order!=current||!"waiting".equals(state))throw IntakeProblem.conflict("SIGNING_STEP_NOT_READY");
 }
 static boolean completed(boolean receipt,boolean verified,boolean committedArtifact){return receipt&&verified&&committedArtifact;}
 static String recovery(String phase,boolean receipt){
  if(receipt&&Set.of("download","storage").contains(phase))return "download";
  if(Set.of("upload_intent","sign_intent").contains(phase))return "unknown";
  return phase;
 }
}
