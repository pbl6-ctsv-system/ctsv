package pbl6.ctsv.auth;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import pbl6.ctsv.mghp.configuration.StageHandoffDatabase;
import static org.assertj.core.api.Assertions.*;

/** Targeted real Auth/HTTPS/SQL tests; CA and storage are the existing P11 stubs. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,classes=SigningLogoutHttpIT.Config.class,
 properties={"ctsv.auth.enabled=true","ctsv.foundation.enabled=true","ctsv.mghp.configuration.enabled=true","ctsv.mghp.applications.enabled=true","ctsv.files.enabled=true","ctsv.files.reconciliation.enabled=false","ctsv.mghp.signing.enabled=true","ctsv.mghp.signing.worker.enabled=false","server.address=127.0.0.1"})
class SigningLogoutHttpIT extends SigningHttpIT {
 @Configuration(proxyBeanMethods=false) @org.springframework.boot.autoconfigure.EnableAutoConfiguration
 @org.springframework.boot.autoconfigure.domain.EntityScan({"pbl6.ctsv.foundation","pbl6.ctsv.mghp"})
 @ComponentScan(basePackages={"pbl6.ctsv.auth","pbl6.ctsv.foundation","pbl6.ctsv.mghp","pbl6.ctsv.shared"},excludeFilters={@ComponentScan.Filter(type=FilterType.CUSTOM,classes=org.springframework.boot.context.TypeExcludeFilter.class),@ComponentScan.Filter(type=FilterType.REGEX,pattern=".*(?:Test|IT)(?:\\$.*)?")})
 static class Config extends SigningHttpIT.Config {
  @Override @Bean DataSource source()throws Exception{return StageHandoffDatabase.migrate();}
 }
 @Autowired AuthService auth;
 @Test void actualLogoutDoesNotCancelAcceptedWorkButOldJwtCannotCallHttp()throws Exception{
  var own=identity("mghp_chuyen_vien");var account=auth.db.account(auth.verifyAccessToken(own[1]).accountId(),false);
  var login=auth.login(account.username(),PASSWORD,"127.0.0.1");String old=System.getProperty("ctsv.mghp.signing.minutes-first.user-id");
  System.setProperty("ctsv.mghp.signing.minutes-first.user-id",own[0]);try{
   var q=prepare(document(bundle(true),true));long id=q.body().path("id").asLong();String path="/api/ctsv/yeu-cau-ky/"+id;
   queue(path,login.response().accessToken(),false);var p=(ProviderStub)provider;int signs=p.signs;
   assertThat(auth.logout(login.cookie())).isTrue();
   call("GET",path,null,login.response().accessToken(),null).expect(401);
   assertThat(worker.runRequest(id)).isTrue();assertThat(p.signs).isEqualTo(signs+1);
   var stored=call("GET",path,null,cv,null).expect(200);assertThat(stored.body().path("status").asText()).isEqualTo("waiting_next");
   assertThat(stored.body().path("steps").get(0).path("outputFile").path("state").asText()).isEqualTo("ready");
  }finally{System.setProperty("ctsv.mghp.signing.minutes-first.user-id",old);}
 }
 @Test void disabledAccountStillStopsAcceptedWorkBeforeCa()throws Exception{checkBlocked("disabled");}
 @Test void removedCtsvAccessStillStopsAcceptedWorkBeforeCa()throws Exception{checkBlocked("ctsv");}
 @Test void removedBusinessRoleStillStopsAcceptedWorkBeforeCa()throws Exception{checkBlocked("mghp");}
 @Test void ppCanConfirmAgainAfterSafeRecoveryWithoutDuplicatingAProviderSign()throws Exception{
  var b=bundle(true);var bb=prepare(document(b,true));String bbPath="/api/ctsv/yeu-cau-ky/"+bb.body().path("id").asText();
  runStep(bbPath,cv,false);runStep(bbPath,pht,false);
  var q=prepare(document(b,false));long id=q.body().path("id").asLong();String path="/api/ctsv/yeu-cau-ky/"+id;
  queue(path,pp,true);var p=(ProviderStub)provider;int signs=p.signs,uploads=p.uploads;p.certificateUnavailable=true;
  try{worker.runRequest(id);}finally{p.certificateUnavailable=false;}
  assertThat(p.signs).isEqualTo(signs);assertThat(p.uploads).isEqualTo(uploads);
  var failed=call("GET",path,null,cv,null).expect(200);
  call("POST",path+"/doi-soat",java.util.Map.of("reason","SYNTHETIC: yêu cầu PP tự xác nhận lại sau lỗi trước dispatch"),cv,failed.tag()).expect(202);
  assertThat(worker.runRequest(id)).isFalse();assertThat(p.signs).isEqualTo(signs);
  assertThat(runStep(path,pp,true).body().path("status").asText()).isEqualTo("waiting_next");assertThat(p.signs).isEqualTo(signs+1);
 }
 void checkBlocked(String type)throws Exception{
  var own=identity("mghp_chuyen_vien");String old=System.getProperty("ctsv.mghp.signing.minutes-first.user-id");
  System.setProperty("ctsv.mghp.signing.minutes-first.user-id",own[0]);try{
   var q=prepare(document(bundle(true),true));long id=q.body().path("id").asLong();String path="/api/ctsv/yeu-cau-ky/"+id;queue(path,own[1],false);var p=(ProviderStub)provider;int signs=p.signs,uploads=p.uploads;
   String account=auth.verifyAccessToken(own[1]).accountId().toString();
   if(type.equals("disabled"))jdbc.update("UPDATE dbo.TaiKhoan SET TrangThai=0,PhienBanToken=PhienBanToken+1 WHERE ID=?",account);
   else if(type.equals("ctsv"))jdbc.update("DELETE FROM dbo.TaiKhoan_VaiTro WHERE TaiKhoanID=?",account);
   else jdbc.update("UPDATE dbo.phan_quyen_nguoi_dung SET hieu_luc_den=DATEADD(day,-1,SYSUTCDATETIME()) WHERE nguoi_dung_id=? AND ma_phan_he=N'MGHP'",Long.parseLong(own[0]));
   worker.runRequest(id);assertThat(p.signs).isEqualTo(signs);assertThat(p.uploads).isEqualTo(uploads);
   assertThat(call("GET",path,null,cv,null).expect(200).body().path("lastErrorCode").asText()).isEqualTo("SIGNING_AUTHORIZATION_REVOKED");
  }finally{System.setProperty("ctsv.mghp.signing.minutes-first.user-id",old);}
 }
}
