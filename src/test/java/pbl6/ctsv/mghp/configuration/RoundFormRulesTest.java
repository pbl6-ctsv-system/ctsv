package pbl6.ctsv.mghp.configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoundFormRulesTest {
 ObjectNode content() throws Exception {return (ObjectNode)new ObjectMapper().readTree("""
 {"code":"SYNTHETIC-FORM","semesterId":"1","name":"Đợt thực hành","description":null,
 "opensAt":null,"submissionDeadline":null,"reviewDeadline":null,"allowRetroactive":true,
 "retroactiveMode":"theo_phap_luat","midSemesterMode":"khoang_ngay_chuyen_vien_xac_nhan","policyVersions":[]}
 """);}
 @Test void newWireAcceptsDraftWithoutRetiredGuidance() throws Exception {assertDoesNotThrow(()->RoundRules.validate(content()));}
 @Test void retiredFieldsAreNotSilentlyStoredOrAccepted() throws Exception {var n=content();n.putNull("declarationGuidance");n.putNull("lateSubmissionGuidance");assertThrows(ConfigFailure.class,()->RoundRules.validate(n));}
}
