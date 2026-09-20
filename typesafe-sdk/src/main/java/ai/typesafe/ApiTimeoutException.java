package ai.typesafe;

import java.time.Duration;

public final class ApiTimeoutException extends ApiConnectionException {
    private static final long serialVersionUID = 1L;

    private final transient Duration timeout;

    public ApiTimeoutException(String message, Throwable cause, Duration timeout) {
        super(message, cause);
        this.timeout = timeout;
    }

    public Duration timeout() {
        return timeout;
    }
}
