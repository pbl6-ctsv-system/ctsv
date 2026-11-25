package pbl6.ctsv.mghp.application;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProcessingStageStatusTest {
 private Map<String,Object> facts(String intake,String roundStatus,boolean sealed,String result){
  var values=new HashMap<String,Object>();values.put("intake",intake);values.put("roundStatus",roundStatus);
  if(sealed)values.put("onlineClosedAt","2026-11-25T04:00:00Z");if(result!=null)values.put("result",result);return values;
 }
 private Object status(Map<String,Object> facts){return new ApplicationWorkflow(null).fields(facts).get("workflowStatus");}
 @Test void submittedStaysSubmittedWhileRoundStillReceives(){
  assertThat(status(facts("submitted","open",false,null))).isEqualTo("submitted");
 }
 @Test void closingReceptionMovesUnprocessedSubmittedToProcessingV1(){
  assertThat(status(facts("submitted","closed",false,null))).isEqualTo("processing_v1");
 }
 @Test void existingOnlineProcessingIsExplicitlyV1(){
  assertThat(status(facts("processing","closed",false,null))).isEqualTo("processing_v1");
 }
 @Test void approvedOnlineRemainsV1UntilOnlineSeal(){
  assertThat(status(facts("approved","closed",false,null))).isEqualTo("approved_v1");
 }
 @Test void sealingOnlineTransfersApprovedFilesToProcessingV2(){
  assertThat(status(facts("approved","closed",true,null))).isEqualTo("processing_v2");
  assertThat(status(facts("approved","closed",true,"can_xac_minh"))).isEqualTo("processing_v2");
 }
 @Test void paperVerdictsAndCouncilStillTakePriority(){
  assertThat(status(facts("approved","closed",true,"dat"))).isEqualTo("approved_v2");
  assertThat(status(facts("approved","closed",true,"khong_dat"))).isEqualTo("hard_copy_failed");
  var closed=facts("approved","closed",true,"dat");closed.put("closedAt","2026-11-25T05:00:00Z");
  assertThat(status(closed)).isEqualTo("awaiting_council");closed.put("selection","fully_selected");assertThat(status(closed)).isEqualTo("selected");
 }
 @Test void closingRoundDoesNotOverwriteSupplementWithdrawalOrRejection(){
  for(String intake:new String[]{"supplementing","supplement_responded","withdrawn","rejected","reopened"})
   assertThat(status(facts(intake,"closed",false,null))).as(intake).isEqualTo(intake);
 }
}
