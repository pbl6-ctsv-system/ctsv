package pbl6.ctsv.shcn.phancongkhoa.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.net.ssl.*;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Ephemeral synthetic keys only. No token/password/key is logged or persisted in shared evidence. */
final class ShcnApiTestKeys {
    static final String ISSUER="https://auth.shcn.test",ORIGIN="https://web.shcn.test";
    private final KeyPair auth;
    private final Path privateKey,publicKey,keyStore;
    private final String password;
    final SSLContext tls;
    ShcnApiTestKeys(){
        try{
            var gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);auth=gen.generateKeyPair();
            privateKey=pem("PRIVATE KEY",auth.getPrivate().getEncoded());publicKey=pem("PUBLIC KEY",auth.getPublic().getEncoded());
            Path dir=Files.createTempDirectory("shcn-api-tls-");dir.toFile().deleteOnExit();keyStore=dir.resolve("test.p12");keyStore.toFile().deleteOnExit();password=UUID.randomUUID().toString();
            var command=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","keytool.exe").toString(),"-genkeypair","-keyalg","RSA","-keysize","2048","-alias","shcn-test","-dname","CN=127.0.0.1","-ext","SAN=ip:127.0.0.1","-validity","2","-storetype","PKCS12","-keystore",keyStore.toString(),"-storepass:env","SHCN_SYNTHETIC_TLS_PASSWORD","-noprompt");
            command.environment().put("SHCN_SYNTHETIC_TLS_PASSWORD",password);
            var child=command.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!child.waitFor(20,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();throw new IllegalStateException("Synthetic TLS key generation timeout");}
            if(child.exitValue()!=0)throw new IllegalStateException("Synthetic TLS key generation failed");
            var store=KeyStore.getInstance("PKCS12");try(var input=Files.newInputStream(keyStore)){store.load(input,password.toCharArray());}
            var trust=KeyStore.getInstance("PKCS12");trust.load(null,null);trust.setCertificateEntry("shcn-test",store.getCertificate("shcn-test"));
            var trustFactory=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trustFactory.init(trust);
            tls=SSLContext.getInstance("TLS");tls.init(null,trustFactory.getTrustManagers(),null);
        }catch(Exception e){throw new IllegalStateException("Cannot prepare synthetic SHCN test keys",e);}
    }
    void properties(DynamicPropertyRegistry r){
        r.add("ctsv.auth.issuer",()->ISSUER);r.add("ctsv.auth.audience",()->"ctsv");r.add("ctsv.auth.signing-kid",()->"shcn-test");
        r.add("ctsv.auth.private-key",privateKey::toString);r.add("ctsv.auth.public-keys[shcn-test]",publicKey::toString);r.add("ctsv.auth.origins[0]",()->ORIGIN);
        r.add("server.ssl.enabled",()->true);r.add("server.ssl.key-store",()->keyStore.toUri().toString());r.add("server.ssl.key-store-type",()->"PKCS12");r.add("server.ssl.key-store-password",()->password);
    }
    String token(UUID account,long version,Instant issued){
        try{
            var json=new ObjectMapper();var enc=Base64.getUrlEncoder().withoutPadding();
            String header=enc.encodeToString(json.writeValueAsBytes(Map.of("alg","RS256","typ","JWT","kid","shcn-test")));
            String payload=enc.encodeToString(json.writeValueAsBytes(Map.of("iss",ISSUER,"aud","ctsv","sub",account.toString(),"iat",issued.getEpochSecond(),"exp",issued.getEpochSecond()+900,"tokenVersion",Long.toString(version))));
            String input=header+"."+payload;var signature=Signature.getInstance("SHA256withRSA");signature.initSign(auth.getPrivate());signature.update(input.getBytes(StandardCharsets.US_ASCII));return input+"."+enc.encodeToString(signature.sign());
        }catch(Exception e){throw new IllegalStateException("Cannot issue synthetic fixture token",e);}
    }
    private static Path pem(String label,byte[] value)throws Exception{
        Path file=Files.createTempFile("shcn-synthetic-key-",".pem");file.toFile().deleteOnExit();Files.writeString(file,"-----BEGIN "+label+"-----\n"+Base64.getEncoder().encodeToString(value)+"\n-----END "+label+"-----");return file;
    }
}
