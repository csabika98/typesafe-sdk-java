package ai.typesafe;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ApiResponse<T>(T data, int statusCode, Map<String, List<String>> headers, String rawBody) {
    public ApiResponse {
        Objects.requireNonNull(headers, "headers");
        headers = Map.copyOf(headers);
    }

    static <T> ApiResponse<T> of(T data, HttpResponse<byte[]> response, String rawBody) {
        return new ApiResponse<>(data, response.statusCode(), response.headers().map(), rawBody);
    }

    public Optional<String> requestId() {
        return headers.getOrDefault("x-typesafe-request-id", List.of()).stream().findFirst();
    }
}
