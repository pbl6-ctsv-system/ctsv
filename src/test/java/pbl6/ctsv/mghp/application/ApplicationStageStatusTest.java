package pbl6.ctsv.mghp.application;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ApplicationStageStatusTest {
 @Test void commonStatusDoesNotMistakeOnlineApprovalForFinalApproval(){
  var workflow=new ApplicationWorkflow(null);
  assertThat(workflow.fields(Map.of("intake","submitted","roundStatus","open"))).containsEntry("status","submitted").containsEntry("onlineStatus","pending");
  assertThat(workflow.fields(Map.of("intake","submitted","roundStatus","closed"))).containsEntry("status","processing");
  assertThat(workflow.fields(Map.of("intake","supplement_responded","roundStatus","open"))).containsEntry("status","processing").containsEntry("onlineStatus","supplement_responded");
  assertThat(workflow.fields(Map.of("intake","approved","roundStatus","closed"))).containsEntry("status","processing").containsEntry("onlineStatus","approved").containsEntry("hardCopyStatus","not_transferred");
 }
 @Test void paperOutcomePreservesOnlineOutcomeAndRejectionReason(){
  var workflow=new ApplicationWorkflow(null);
  var paper=Map.<String,Object>of("intake","approved","roundStatus","closed","onlineClosedAt","sealed");
  assertThat(workflow.fields(paper)).containsEntry("hardCopyStatus","pending");
  var draft=new java.util.HashMap<>(paper);draft.put("draft",1);
  assertThat(workflow.fields(draft)).containsEntry("hardCopyStatus","in_progress").containsEntry("status","processing");
  var passed=new java.util.HashMap<>(paper);passed.put("result","dat");
  assertThat(workflow.fields(passed)).containsEntry("status","approved").containsEntry("hardCopyStatus","approved");
  passed.put("result","khong_dat");passed.put("reviewReason","Giấy không đúng chủ thể");
  assertThat(workflow.fields(passed)).containsEntry("status","rejected").containsEntry("onlineStatus","approved").containsEntry("hardCopyStatus","rejected").containsEntry("rejectionStage","hard_copy").containsEntry("rejectionReason","Giấy không đúng chủ thể");
 }
 @Test void councilOutcomeIsSeparateAndNotLeakedToStudentsBeforePublication(){
  var workflow=new ApplicationWorkflow(null);
  var facts=Map.<String,Object>of("intake","approved","result","dat","closedAt","sealed","selection","fully_selected","published",0);
  assertThat(workflow.fields(facts,false)).containsEntry("status","approved").containsEntry("councilResult","selected");
  assertThat(workflow.fields(facts,true)).containsEntry("status","approved").containsEntry("councilResult",null).doesNotContainKey("intakeSealed");
  var published=new java.util.HashMap<>(facts);published.put("published",1);
  assertThat(workflow.fields(published,true)).containsEntry("councilResult","selected");
 }
 @Test void legacyCompletedIsNotInventedAsAnApproval(){
  assertThat(new ApplicationWorkflow(null).fields(Map.of("intake","completed","roundStatus","completed"))).containsEntry("status",null).containsEntry("onlineStatus","unspecified");
 }
}
