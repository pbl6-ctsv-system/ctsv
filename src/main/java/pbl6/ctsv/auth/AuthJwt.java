package pbl6.ctsv.auth;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;

final class AuthJwt {
    private static final Base64.Encoder ENCODE = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODE = Base64.getUrlDecoder();
    private final ObjectMapper json = new ObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final String issuer;
    private final String audience;
    private final String kid;
    private final RSAPrivateKey signing;
    private final Map<String,RSAPublicKey> publicKeys;
    private final Clock clock;

    AuthJwt(String issuer,String audience,String kid,RSAPrivateKey signing,Map<String,RSAPublicKey> keys,Clock clock) {
        this.issuer=issuer;
        this.audience=audience;
        this.kid=kid;
        this.signing=signing;
        this.publicKeys=Map.copyOf(keys);
        this.clock=clock;
        if (issuer.isBlank() || issuer.length()>512 || audience.isBlank() || kid.isBlank()
            || signing.getModulus().bitLength()<2048 || keys.get(kid)==null
            || !signing.getModulus().equals(keys.get(kid).getModulus())
            || keys.values().stream().anyMatch(k->k.getModulus().bitLength()<2048))
            throw new IllegalArgumentException("Invalid Auth signing key configuration");
    }

    String issue(UUID account,long version,Instant now) {
        try {
            var header = json.createObjectNode().put("alg","RS256").put("typ","JWT").put("kid",kid);
            var claims = json.createObjectNode().put("iss",issuer).put("aud",audience).put("sub",account.toString())
                .put("iat",now.getEpochSecond()).put("exp",now.getEpochSecond()+900).put("tokenVersion",Long.toString(version));
            String input = ENCODE.encodeToString(json.writeValueAsBytes(header)) + "." + ENCODE.encodeToString(json.writeValueAsBytes(claims));
            var sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(signing);
            sig.update(input.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return input+"."+ENCODE.encodeToString(sig.sign());
        } catch (GeneralSecurityException|java.io.IOException e) {
            throw AuthFailure.unavailable();
        }
    }

    AuthAccess.Principal verify(String raw) {
        try {
            if (raw==null || raw.length()>8192) throw invalid();
            String[] parts=raw.split("\\.",-1);
            if(parts.length!=3 || Arrays.stream(parts).anyMatch(p->!p.matches("[A-Za-z0-9_-]+"))) throw invalid();
            JsonNode h=json.readTree(DECODE.decode(parts[0]));
            if(!"RS256".equals(h.path("alg").textValue()) || h.has("jku") || h.has("x5u") || h.has("crit")
                || h.has("jwk") || h.has("x5c") || h.has("b64")) throw invalid();
            String keyId=h.path("kid").textValue();
            if(keyId==null || !publicKeys.containsKey(keyId)) throw invalid();
            var sig=Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKeys.get(keyId));
            sig.update((parts[0]+"."+parts[1]).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            if(!sig.verify(DECODE.decode(parts[2]))) throw invalid();
            JsonNode c=json.readTree(DECODE.decode(parts[1]));
            JsonNode aud=c.path("aud");
            boolean expectedAudience=aud.isTextual()&&audience.equals(aud.textValue());
            if(aud.isArray()) {
                expectedAudience=false;
                for(var value:aud) if(audience.equals(value.textValue())) expectedAudience=true;
            }
            if(!issuer.equals(c.path("iss").textValue()) || !expectedAudience) throw invalid();
            UUID account=UUID.fromString(c.path("sub").textValue());
            if(!account.toString().equals(c.path("sub").textValue())) throw invalid();
            long exp=seconds(c.path("exp")), iat=seconds(c.path("iat"));
            Instant now=clock.instant();
            if(Math.subtractExact(exp,iat)!=900 || !now.isBefore(Instant.ofEpochSecond(exp).plusSeconds(30))
                || Instant.ofEpochSecond(iat).isAfter(now.plusSeconds(30))
                || c.has("nbf")&&Instant.ofEpochSecond(seconds(c.get("nbf"))).isAfter(now.plusSeconds(30))) throw invalid();
            String version=c.path("tokenVersion").textValue();
            if(version==null || !version.matches("0|[1-9][0-9]{0,18}")) throw invalid();
            return new AuthAccess.Principal(issuer,account,Long.parseLong(version));
        } catch (GeneralSecurityException | java.io.IOException | RuntimeException e) {
            throw invalid();
        }
    }

    private static long seconds(JsonNode n) {
        if(!n.isIntegralNumber() || !n.canConvertToLong()) throw invalid();
        return n.longValue();
    }
    private static AuthFailure invalid(){return new AuthFailure(401,"UNAUTHENTICATED");}
}
