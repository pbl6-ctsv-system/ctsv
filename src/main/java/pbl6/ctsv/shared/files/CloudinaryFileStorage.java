package pbl6.ctsv.shared.files;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

final class CloudinaryFileStorage implements PrivateFileStorage, AutoCloseable {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(CloudinaryFileStorage.class);
    private static final int JSON_LIMIT = 131_072;
    private final String cloud;
    private final String key;
    private final String secret;
    private final String folder;
    private final boolean fixedFolder;
    private final Duration timeout;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    CloudinaryFileStorage(String cloud, String key, String secret, String folder, String mode, Duration timeout, ObjectMapper json) {
        if (!cloud.matches("[A-Za-z0-9_-]{1,128}") || !key.matches("[0-9]{1,64}") || secret.isBlank()
            || folder.length()>64 || !folder.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*")
            || !("dynamic".equals(mode) || "fixed".equals(mode))
            || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("Invalid Cloudinary configuration");
        }
        this.cloud = cloud;
        this.key = key;
        this.secret = secret;
        this.folder = folder;
        this.fixedFolder = "fixed".equals(mode);
        this.timeout = timeout;
        this.json = json;
    }

    @Override public String cloud() { return cloud; }

    @Override public String publicId(String filename) { return fixedFolder ? folder+"/"+filename : filename; }

    @Override public Asset upload(String objectKey, Path file, long size) {
        String uploadId = fixedFolder ? objectKey.substring(folder.length()+1) : objectKey;
        var fields = signed(Map.of("public_id",uploadId,"type","authenticated","overwrite","false",
            fixedFolder ? "folder" : "asset_folder",folder));
        String boundary = UUID.randomUUID().toString();
        var prefix = new StringBuilder();
        fields.forEach((name,value) -> prefix.append("--").append(boundary)
            .append("\r\nContent-Disposition: form-data; name=\"").append(name)
            .append("\"\r\n\r\n").append(value).append("\r\n"));
        prefix.append("--").append(boundary)
            .append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"document.pdf\"")
            .append("\r\nContent-Type: application/pdf\r\n\r\n");
        try {
            var body = HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofString(prefix.toString()),
                HttpRequest.BodyPublishers.ofFile(file), HttpRequest.BodyPublishers.ofString("\r\n--"+boundary+"--\r\n"));
            // /upload is the Upload API operation. The signed 'type' selects authenticated delivery.
            var request = request("/raw/upload").header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(body).build();
            var result = responseJson(request,"upload",false).orElseThrow(FileProblem::unavailable);
            if (result.path("bytes").asLong(-1) != size) {
                LOG.warn("Cloudinary upload returned a mismatched byte count");
                throw FileProblem.unavailable();
            }
            if (!fixedFolder && result.has("asset_folder") && !folder.equals(result.path("asset_folder").asText())) {
                LOG.warn("Cloudinary upload returned an unexpected asset folder");
                throw FileProblem.unavailable();
            }
            return asset(result, objectKey);
        } catch (IOException failure) {
            throw FileProblem.unavailable();
        }
    }

    @Override public void download(String assetId, Path destination, long byteLimit) {
        var fields = signed(Map.of("asset_id", assetId, "expires_at", Long.toString(Instant.now().plusSeconds(180).getEpochSecond())));
        String body = fields.entrySet().stream().map(e -> encode(e.getKey())+"="+encode(e.getValue()))
            .collect(Collectors.joining("&"));
        var response = send(request("/asset/download").header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), info -> {
                if (info.statusCode() != 200) {
                    LOG.warn("Cloudinary download rejected, status {}",info.statusCode());
                    throw FileProblem.unavailable();
                }
                return limited(HttpResponse.BodySubscribers.ofFile(destination),byteLimit,FileProblem.integrity());
            });
        if (response.statusCode() != 200) throw FileProblem.unavailable();
    }

    @Override public Optional<Asset> find(String objectKey) {
        var request = request("/resources/raw/authenticated/"+encode(objectKey))
            .header("Authorization",basicAuth()).GET().build();
        return responseJson(request,"lookup",true).map(node -> asset(node,objectKey));
    }

    private String basicAuth() {
        return "Basic "+Base64.getEncoder().encodeToString((key+":"+secret).getBytes(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder request(String route) {
        return HttpRequest.newBuilder(URI.create("https://api.cloudinary.com/v1_1/"+cloud+route)).timeout(timeout);
    }

    private Optional<JsonNode> responseJson(HttpRequest request, String operation, boolean allowMissing) {
        var response = send(request,info -> limited(HttpResponse.BodySubscribers.ofByteArray(),JSON_LIMIT,FileProblem.unavailable()));
        if (allowMissing && response.statusCode()==404) return Optional.empty();
        if (response.statusCode()!=200) {
            LOG.warn("Cloudinary {} rejected, status {}, diagnostic {}",operation,response.statusCode(),diagnostic(response));
            throw FileProblem.unavailable();
        }
        try {
            return Optional.ofNullable(json.readTree(response.body()));
        } catch (IOException failure) {
            throw FileProblem.unavailable();
        }
    }

    private String diagnostic(HttpResponse<byte[]> response) {
        if (response.headers().firstValue("X-Mitmproxy-Blocked-Reason").isPresent()) return "proxy-policy";
        try {
            var body=json.readTree(response.body());
            if (body==null || !body.path("error").isObject()) return "non-provider-response";
            String message=body.path("error").path("message").asText().toLowerCase(java.util.Locale.ROOT);
            if (message.contains("untrusted") || message.contains("disabled") || message.contains("suspended")) return "account-restriction";
            if (message.contains("signature") || message.contains("api key")) return "credentials-or-signature";
            if (message.contains("preset")) return "upload-preset";
            if (message.contains("authenticated") || message.contains("delivery type") || message.contains("resource type")
                || message.matches(".*\\braw\\b.*")) return "asset-type";
            if (message.contains("file type")) return "file-type-policy";
            if (message.contains("format")) return "file-format-policy";
            if (message.contains("rate") || message.contains("quota") || message.contains("limit")) return "usage-limit";
            if (message.contains("request forbidden")) return "provider-request-forbidden";
            return "provider-error";
        } catch (IOException failure) {
            return "non-provider-response";
        }
    }

    private static Asset asset(JsonNode node, String objectKey) {
        String id = node.path("asset_id").asText();
        long version = node.path("version").asLong(-1);
        if (!objectKey.equals(node.path("public_id").asText()) || !"raw".equals(node.path("resource_type").asText())
            || !"authenticated".equals(node.path("type").asText()) || !id.matches("[A-Za-z0-9_-]{1,128}") || version<1) {
            LOG.warn("Cloudinary response did not confirm the expected private asset metadata");
            throw FileProblem.unavailable();
        }
        return new Asset(id,version);
    }

    private Map<String,String> signed(Map<String,String> values) {
        var fields = new TreeMap<>(values);
        fields.put("timestamp",Long.toString(Instant.now().getEpochSecond()));
        String material = fields.entrySet().stream().map(e -> e.getKey()+"="+e.getValue()).collect(Collectors.joining("&"));
        fields.put("signature",HexFormat.of().formatHex(PdfSpool.digest().digest((material+secret).getBytes(StandardCharsets.UTF_8))));
        fields.put("api_key",key);
        return fields;
    }

    private static String encode(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        var result = client.sendAsync(request,handler);
        try {
            return result.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            result.cancel(true);
            Thread.currentThread().interrupt();
            throw FileProblem.unavailable();
        } catch (java.util.concurrent.TimeoutException failure) {
            result.cancel(true);
            LOG.warn("Cloudinary connection exceeded the request timeout");
            throw FileProblem.unavailable();
        } catch (java.util.concurrent.ExecutionException failure) {
            for (Throwable cause=failure; cause!=null; cause=cause.getCause()) {
                if (cause instanceof FileProblem known) throw known;
            }
            LOG.warn("Cloudinary transport failed ({})",failure.getCause().getClass().getSimpleName());
            throw FileProblem.unavailable();
        }
    }

    private static <T> HttpResponse.BodySubscriber<T> limited(HttpResponse.BodySubscriber<T> sink, long limit, FileProblem overflow) {
        return new HttpResponse.BodySubscriber<>() {
            private Flow.Subscription subscription;
            private long bytes;
            private boolean cancelled;
            @Override public CompletionStage<T> getBody() { return sink.getBody(); }
            @Override public void onSubscribe(Flow.Subscription value) { subscription=value; sink.onSubscribe(value); }
            @Override public void onNext(List<ByteBuffer> buffers) {
                for (var buffer : buffers) bytes += buffer.remaining();
                if (bytes > limit) {
                    cancelled=true;
                    subscription.cancel();
                    sink.onError(overflow);
                } else sink.onNext(buffers);
            }
            @Override public void onError(Throwable failure) { if (!cancelled) sink.onError(failure); }
            @Override public void onComplete() { if (!cancelled) sink.onComplete(); }
        };
    }

    @Override public void close() { client.close(); }
}
