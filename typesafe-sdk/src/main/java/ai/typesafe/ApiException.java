package ai.typesafe;

import java.util.List;
import java.util.Map;

public class ApiException extends TypeSafeException {
    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final transient Map<String, List<String>> headers;
    private final transient Object body;
    private final String requestId;

    public ApiException(
            String message,
            int statusCode,
            Map<String, List<String>> headers,
            Object body,
            String requestId) {
        super(message);
        this.statusCode = statusCode;
        this.headers = Map.copyOf(headers);
        this.body = body;
        this.requestId = requestId;
    }

    public int statusCode() {
        return statusCode;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public Object body() {
        return body;
    }

    public String requestId() {
        return requestId;
    }
}
