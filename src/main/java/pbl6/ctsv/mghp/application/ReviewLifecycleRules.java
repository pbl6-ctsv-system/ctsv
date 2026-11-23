package pbl6.ctsv.mghp.application;

/** Read progress is independent of the immutable submission/decision snapshots. */
final class ReviewLifecycleRules {
 private ReviewLifecycleRules() {}
 static boolean canEvaluate(String intake,boolean terminal){return "approved".equals(intake)&&!terminal;}
 static String status(String intake,String result,boolean closed,String selection){
  if("rejected".equals(intake)||"withdrawn".equals(intake))return intake;
  if("fully_selected".equals(selection))return "selected";
  if("not_selected".equals(selection))return "not_selected";
  if("khong_dat".equals(result))return "hard_copy_failed";
  if("dat".equals(result))return closed?"awaiting_council":"approved_v2";
  return "approved".equals(intake)?"approved_v1":intake;
 }
}
