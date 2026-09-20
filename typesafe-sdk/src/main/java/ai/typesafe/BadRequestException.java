package ai.typesafe;

import java.util.List;
import java.util.Map;

public final class BadRequestException extends ApiException {
    private static final long serialVersionUID = 1L;

    public BadRequestException(
            String message,
            int statusCode,
            Map<String, List<String>> headers,
            Object body,
            String requestId) {
        super(message, statusCode, headers, body, requestId);
    }
}
