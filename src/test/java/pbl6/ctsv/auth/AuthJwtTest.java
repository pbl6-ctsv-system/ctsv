package pbl6.ctsv.auth;

import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class AuthJwtTest {
    private static final KeyPair TEST_KEY=key();
    private static KeyPair key(){
        try{var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);return g.generateKeyPair();}
        catch(GeneralSecurityException e){throw new ExceptionInInitializerError(e);}
    }
    @ParameterizedTest
    @ValueSource(strings={"signature","issuer","audience","kid","alg","jku","x5u","missingVersion","numericVersion",
        "negativeVersion","overflowVersion","leadingZeroVersion","missingSub","invalidSub","numericExp","wrongLifetime","futureIat","futureNbf","duplicateClaim"})
    void independentlySignedInvalidJwtVariantsAreRejected(String variant)throws Exception {
        Instant now=Instant.parse("2026-11-12T01:00:00Z");
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var h=json.createObjectNode().put("alg","RS256").put("kid","test");
        var c=json.createObjectNode().put("iss","issuer").put("aud","aud").put("sub",UUID.randomUUID().toString())
            .put("iat",now.getEpochSecond()).put("exp",now.getEpochSecond()+900).put("tokenVersion","4");
        switch(variant){
            case "issuer"->c.put("iss","other");case "audience"->c.put("aud","other");
            case "kid"->h.put("kid","unknown");case "alg"->h.put("alg","HS256");
            case "jku","x5u"->h.put(variant,"https://untrusted.invalid/keys");
            case "missingVersion"->c.remove("tokenVersion");case "numericVersion"->c.put("tokenVersion",4);
            case "negativeVersion"->c.put("tokenVersion","-1");case "overflowVersion"->c.put("tokenVersion","9223372036854775808");
            case "leadingZeroVersion"->c.put("tokenVersion","04");case "missingSub"->c.remove("sub");case "invalidSub"->c.put("sub","not-a-uuid");
            case "numericExp"->c.put("exp","9999999999");case "wrongLifetime"->c.put("exp",now.getEpochSecond()+901);
            case "futureIat"->{c.put("iat",now.getEpochSecond()+31);c.put("exp",now.getEpochSecond()+931);}
            case "futureNbf"->c.put("nbf",now.getEpochSecond()+31);
        }
        String claims=json.writeValueAsString(c);
        if(variant.equals("duplicateClaim")) claims=claims.substring(0,claims.length()-1)+",\"iss\":\"issuer\"}";
        var enc=Base64.getUrlEncoder().withoutPadding();
        String input=enc.encodeToString(json.writeValueAsBytes(h))+"."+enc.encodeToString(claims.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var signer=Signature.getInstance("SHA256withRSA");signer.initSign(TEST_KEY.getPrivate());
        signer.update(input.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        byte[] signature=signer.sign();if(variant.equals("signature")) signature[0]^=1;
        String token=input+"."+enc.encodeToString(signature);
        var verifier=new AuthJwt("issuer","aud","test",(RSAPrivateKey)TEST_KEY.getPrivate(),
            Map.of("test",(RSAPublicKey)TEST_KEY.getPublic()),Clock.fixed(now,ZoneOffset.UTC));
        assertThatThrownBy(()->verifier.verify(token)).isInstanceOfSatisfying(AuthFailure.class,e->assertThat(e.code()).isEqualTo("UNAUTHENTICATED"));
    }
    @Test void validatesAllowlistedKeysAndExpirySkewWithNoExternalKeyLookup() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var current=generator.generateKeyPair();
        var previous=generator.generateKeyPair();
        var id=UUID.randomUUID();
        var issued=Instant.parse("2026-11-12T01:00:00Z");
        var keys=Map.of("current",(RSAPublicKey)current.getPublic(),"previous",(RSAPublicKey)previous.getPublic());
        var old=new AuthJwt("issuer","aud","previous",(RSAPrivateKey)previous.getPrivate(),keys,Clock.fixed(issued,ZoneOffset.UTC));
        var token=old.issue(id,Long.MAX_VALUE,issued);
        var verifier=new AuthJwt("issuer","aud","current",(RSAPrivateKey)current.getPrivate(),keys,
            Clock.fixed(issued.plusSeconds(929),ZoneOffset.UTC));
        assertThat(verifier.verify(token)).isEqualTo(new AuthAccess.Principal("issuer",id,Long.MAX_VALUE));
        var expired=new AuthJwt("issuer","aud","current",(RSAPrivateKey)current.getPrivate(),keys,
            Clock.fixed(issued.plusSeconds(930),ZoneOffset.UTC));
        assertThatThrownBy(()->expired.verify(token)).isInstanceOf(AuthFailure.class);
        var unknown=new AuthJwt("issuer","aud","current",(RSAPrivateKey)current.getPrivate(),Map.of("current",(RSAPublicKey)current.getPublic()),
            Clock.fixed(issued,ZoneOffset.UTC));
        assertThatThrownBy(()->unknown.verify(token)).isInstanceOf(AuthFailure.class);
        var differentAudience=new AuthJwt("issuer","other","current",(RSAPrivateKey)current.getPrivate(),keys,Clock.fixed(issued,ZoneOffset.UTC));
        assertThatThrownBy(()->differentAudience.verify(token)).isInstanceOf(AuthFailure.class);
        var differentIssuer=new AuthJwt("other","aud","current",(RSAPrivateKey)current.getPrivate(),keys,Clock.fixed(issued,ZoneOffset.UTC));
        assertThatThrownBy(()->differentIssuer.verify(token)).isInstanceOf(AuthFailure.class);
        String[] parts=token.split("\\.");
        assertThatThrownBy(()->verifier.verify(parts[0]+"."+parts[1]+"."+"AAAA")).isInstanceOf(AuthFailure.class);
    }
}
