package ai.typesafe;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;

record StubResponse(HttpRequest request, int status, byte[] body, HttpHeaders headers)
        implements HttpResponse<byte[]> {
    record Spec(int status, String body, String... headerPairs) {
        HttpResponse<byte[]> toResponse(HttpRequest request) {
            return StubResponse.of(request, status, body, headerPairs);
        }
    }

    static HttpResponse<byte[]> of(HttpRequest request, int status, String body, String... headerPairs) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("content-type", List.of("application/json"));
        for (int index = 0; index < headerPairs.length; index += 2) {
            headers.put(headerPairs[index].toLowerCase(java.util.Locale.ROOT),
                    List.of(headerPairs[index + 1]));
        }
        return new StubResponse(
                request,
                status,
                body.getBytes(StandardCharsets.UTF_8),
                HttpHeaders.of(headers, (name, value) -> true));
    }

    @Override
    public int statusCode() {
        return status;
    }

    @Override
    public HttpRequest request() {
        return request;
    }

    @Override
    public Optional<HttpResponse<byte[]>> previousResponse() {
        return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public byte[] body() {
        return body;
    }

    @Override
    public Optional<SSLSession> sslSession() {
        return Optional.empty();
    }

    @Override
    public URI uri() {
        return request.uri();
    }

    @Override
    public HttpClient.Version version() {
        return HttpClient.Version.HTTP_1_1;
    }
}
