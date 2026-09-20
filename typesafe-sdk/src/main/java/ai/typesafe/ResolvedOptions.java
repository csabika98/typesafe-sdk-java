package ai.typesafe;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

record ResolvedOptions(
        String apiKey,
        URI baseUrl,
        String defaultModel,
        Duration timeout,
        LogLevel logLevel,
        RetryPolicy retry,
        Map<String, String> defaultHeaders,
        TypeSafeLogger logger) {
}
