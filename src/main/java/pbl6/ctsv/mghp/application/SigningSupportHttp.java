package pbl6.ctsv.mghp.application;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pbl6.ctsv.auth.AuthAccess;
import static pbl6.ctsv.mghp.application.IntakeRules.*;

@org.springframework.stereotype.Component
@ConditionalOnProperty(name="ctsv.mghp.signing.enabled",havingValue="true")
class SigningSupportHttp {
 final AuthAccess auth;final ObjectMapper json;
 SigningSupportHttp(AuthAccess auth,ObjectMapper json){this.auth=auth;this.json=json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);}
 AuthAccess.Principal principal(HttpServletRequest r){var h=Collections.list(r.getHeaders("Authorization"));if(h.size()!=1||!h.getFirst().startsWith("Bearer "))throw new IntakeProblem(401,"UNAUTHENTICATED","request");return auth.verifyAccessToken(h.getFirst().substring(7));}
 void query(HttpServletRequest r,String...allowed){for(var e:r.getParameterMap().entrySet())if(!List.of(allowed).contains(e.getKey())||e.getValue().length!=1)throw IntakeProblem.invalid("query");if("GET".equals(r.getMethod())&&(r.getContentLengthLong()>0||r.getHeader("Transfer-Encoding")!=null))throw IntakeProblem.invalid("body");}
 JsonNode body(HttpServletRequest r)throws IOException{
  try{if(r.getContentType()==null||!MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(r.getContentType())))throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");}catch(InvalidMediaTypeException e){throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");}
  if(r.getHeader("Content-Encoding")!=null&&!"identity".equalsIgnoreCase(r.getHeader("Content-Encoding")))throw new IntakeProblem(415,"UNSUPPORTED_MEDIA_TYPE","body");
  byte[] bytes=r.getInputStream().readNBytes(32769);if(bytes.length>32768)throw new IntakeProblem(413,"PAYLOAD_TOO_LARGE","body");
  try{return json.readTree(java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString());}catch(com.fasterxml.jackson.core.JsonProcessingException|java.nio.charset.CharacterCodingException e){throw IntakeProblem.invalid("body");}
 }
 ResponseEntity<?> reply(ApplicationReads.Detail view){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(view.tag()).body(view.body());}
 ResponseEntity<?> reply(Map<String,Object> view){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view);}
}
