package ai.typesafe;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class RateLimitException extends ApiException {
    private static final long serialVersionUID = 1L;

    private final transient Duration retryDelay;

    public RateLimitException(
            String message,
            int statusCode,
            Map<String, List<String>> headers,
            Object body,
            String requestId,
            Duration retryDelay) {
        super(message, statusCode, headers, body, requestId);
        this.retryDelay = retryDelay;
    }

    public Optional<Duration> retryDelay() {
        return Optional.ofNullable(retryDelay);
    }
}
