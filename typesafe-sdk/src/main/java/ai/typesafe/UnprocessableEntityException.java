package ai.typesafe;

import java.util.List;
import java.util.Map;

public final class UnprocessableEntityException extends ApiException {
    private static final long serialVersionUID = 1L;

    public UnprocessableEntityException(
            String message,
            int statusCode,
            Map<String, List<String>> headers,
            Object body,
            String requestId) {
        super(message, statusCode, headers, body, requestId);
    }
}
