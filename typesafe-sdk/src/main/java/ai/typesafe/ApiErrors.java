package ai.typesafe;

import ai.typesafe.json.Json;
import ai.typesafe.json.JsonArray;
import ai.typesafe.json.JsonObject;
import ai.typesafe.json.JsonParseException;
import ai.typesafe.json.JsonString;
import ai.typesafe.json.JsonValue;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ApiErrors {
    private static final int MAX_MESSAGE_BODY = 200;

    private ApiErrors() {
    }

    static ApiException create(HttpResponse<byte[]> response, String text, RetryPolicy retry) {
        Object body = null;
        JsonValue json = null;
        if (!text.isEmpty()) {
            try {
                json = Json.parse(text);
                body = json;
            } catch (JsonParseException exception) {
                body = text;
            }
        }

        int status = response.statusCode();
        Map<String, List<String>> headers = response.headers().map();
        String requestId = response.headers().firstValue("x-typesafe-request-id").orElse(null);
        String message = formatMessage(status, json, text);

        return switch (status) {
            case 400 -> new BadRequestException(message, status, headers, body, requestId);
            case 401 -> new AuthenticationException(message, status, headers, body, requestId);
            case 403 -> new PermissionDeniedException(message, status, headers, body, requestId);
            case 404 -> new NotFoundException(message, status, headers, body, requestId);
            case 422 -> new UnprocessableEntityException(message, status, headers, body, requestId);
            case 429 -> new RateLimitException(
                    message,
                    status,
                    headers,
                    body,
                    requestId,
                    RetryAfter.read(response.headers(), retry.maxRetryAfter()).orElse(null));
            default -> status >= 500
                    ? new InternalServerException(message, status, headers, body, requestId)
                    : new ApiException(message, status, headers, body, requestId);
        };
    }

    private static String formatMessage(int status, JsonValue body, String text) {
        if (text.isBlank()) {
            return status + " status code (no body)";
        }

        String detail = switch (body) {
            case JsonString string -> string.value();
            case JsonObject object -> extractObjectMessage(object);
            case null, default -> null;
        };
        if (detail != null && !detail.isBlank()) {
            return status + " " + detail;
        }

        String truncated = text.length() > MAX_MESSAGE_BODY
                ? text.substring(0, MAX_MESSAGE_BODY) + "…"
                : text;
        return status + " " + truncated;
    }

    private static String extractObjectMessage(JsonObject root) {
        return string(root, "error")
                .or(() -> nestedMessage(root, "error"))
                .or(() -> string(root, "message"))
                .or(() -> string(root, "detail"))
                .or(() -> nestedMessage(root, "detail"))
                .or(() -> root.get("detail") instanceof JsonArray detail
                        ? Optional.of(formatValidationDetails(detail))
                        : Optional.empty())
                .orElse(null);
    }

    private static Optional<String> string(JsonObject root, String name) {
        return root.get(name) instanceof JsonString value ? Optional.of(value.value()) : Optional.empty();
    }

    private static Optional<String> nestedMessage(JsonObject root, String name) {
        return root.get(name) instanceof JsonObject nested ? string(nested, "message") : Optional.empty();
    }

    private static String formatValidationDetails(JsonArray detail) {
        return detail.values().stream()
                .map(ApiErrors::formatValidationDetail)
                .collect(Collectors.joining("; "));
    }

    private static String formatValidationDetail(JsonValue value) {
        if (!(value instanceof JsonObject item)) {
            return value.toJson();
        }
        String path = item.get("loc") instanceof JsonArray location ? formatLocation(location) : null;
        String message = item.get("msg") instanceof JsonString msg ? msg.value() : item.toJson();
        return path == null || path.isEmpty() ? message : path + ": " + message;
    }

    private static String formatLocation(JsonArray location) {
        Stream<String> segments = location.values().stream()
                .map(segment -> segment instanceof JsonString string ? string.value() : segment.toJson());
        return segments
                .dropWhile(segment -> segment.equalsIgnoreCase("body"))
                .collect(Collectors.joining("."));
    }
}
