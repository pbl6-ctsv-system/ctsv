package pbl6.ctsv.mghp.application;

/** Read progress is independent of the immutable submission/decision snapshots. */
final class ReviewLifecycleRules {
 private ReviewLifecycleRules() {}
 static boolean canEvaluate(String intake,boolean terminal){return "approved".equals(intake)&&!terminal;}
}
