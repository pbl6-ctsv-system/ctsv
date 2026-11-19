package pbl6.ctsv.hocbong.kkht.round;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import pbl6.ctsv.auth.AuthAccess;

final class RoundHttp {
    private static final int BODY_LIMIT = 1_048_576;
    private static final String BEARER = "Bearer ";

    private final ObjectMapper json;

    RoundHttp(ObjectMapper json) {
        this.json = json.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    static AuthAccess.Principal principal(HttpServletRequest request, AuthAccess auth) {
        String authorization = singleton(request, HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.length() <= BEARER.length()
                || !authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            throw new RoundFailure(401, "UNAUTHENTICATED");
        }
        return auth.verifyAccessToken(authorization.substring(BEARER.length()));
    }

    static Map<String, String[]> getQuery(HttpServletRequest request, String... allowed) {
        singleton(request, HttpHeaders.CONTENT_TYPE);
        ifMatch(request);
        try {
            if (request.getContentLengthLong() > 0 || request.getInputStream().read() != -1) {
                throw RoundFailure.invalid("body");
            }
        } catch (IOException e) {
            throw RoundFailure.invalid("body");
        }

        Set<String> keys = Set.of(allowed);
        Map<String, String[]> query;
        try {
            query = request.getParameterMap();
        } catch (IllegalStateException e) {
            throw RoundFailure.invalid("query");
        }
        query.forEach((key, values) -> {
            if (!keys.contains(key) || values.length != 1) {
                throw RoundFailure.invalid(key);
            }
        });
        return query;
    }

    static String parameter(Map<String, String[]> query, String key) {
        String[] values = query.get(key);
        return values == null ? null : values[0];
    }

    static String ifMatch(HttpServletRequest request) {
        return singleton(request, HttpHeaders.IF_MATCH);
    }

    JsonNode body(HttpServletRequest request) {
        if (request.getQueryString() != null) {
            throw RoundFailure.invalid("query");
        }
        String contentType = singleton(request, HttpHeaders.CONTENT_TYPE);
        if (!jsonUtf8(contentType) || !identityEncoding(request)) {
            throw new RoundFailure(415, "UNSUPPORTED_MEDIA_TYPE");
        }
        if (request.getContentLengthLong() > BODY_LIMIT) {
            throw new RoundFailure(413, "PAYLOAD_TOO_LARGE");
        }
        try {
            byte[] bytes = request.getInputStream().readNBytes(BODY_LIMIT + 1);
            if (bytes.length > BODY_LIMIT) {
                throw new RoundFailure(413, "PAYLOAD_TOO_LARGE");
            }
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode body = json.readTree(text);
            if (body == null || !body.isObject()) {
                throw RoundFailure.invalid("body");
            }
            return body;
        } catch (IOException e) {
            throw RoundFailure.invalid("body");
        }
    }

    private static boolean jsonUtf8(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            var charset = mediaType.getCharset();
            return MediaType.APPLICATION_JSON.equalsTypeAndSubtype(mediaType)
                    && (mediaType.getParameters().isEmpty()
                        || mediaType.getParameters().size() == 1 && StandardCharsets.UTF_8.equals(charset));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean identityEncoding(HttpServletRequest request) {
        var values = request.getHeaders(HttpHeaders.CONTENT_ENCODING);
        return !values.hasMoreElements()
                || "identity".equalsIgnoreCase(values.nextElement().strip()) && !values.hasMoreElements();
    }

    private static String singleton(HttpServletRequest request, String name) {
        var values = request.getHeaders(name);
        if (!values.hasMoreElements()) {
            return null;
        }
        String value = values.nextElement();
        if (values.hasMoreElements()) {
            throw RoundFailure.invalid(name);
        }
        // These two fields cannot contain a combined list of values. If-Match syntax is
        // evaluated against the resolved round by the application service.
        if (!HttpHeaders.IF_MATCH.equals(name) && value.indexOf(',') >= 0) {
            throw RoundFailure.invalid(name);
        }
        return value;
    }
}
