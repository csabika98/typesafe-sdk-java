package ai.typesafe;

import ai.typesafe.json.Json;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

final class TestSupport {
    static final String RESULT_JSON = """
            {"model":"jev-latest","answers":{\
            "ok":{"type":"noul","noul":0.75},\
            "pick":{"type":"choice","choice":"a","confidence":0.9,"probabilities":{"a":0.9,"b":0.1}},\
            "rating":{"type":"score","score":2,"confidence":0.8,\
            "legend":{"1":"bad","2":{"label":"good"}},"probabilities":{"1":0.2,"2":0.8}}},\
            "usage":{"input_tokens":4,"output_tokens":2}}""";

    static final String EMPTY_MODELS = "{\"models\":[]}";

    private TestSupport() {
    }

    static TypeSafeClientOptions.Builder options() {
        return TypeSafeClientOptions.builder()
                .apiKey("secret-value")
                .baseUrl("https://example.test")
                .environment(name -> null)
                .retry(RetryOptions.builder()
                        .initialDelay(Duration.ofMillis(1))
                        .maxDelay(Duration.ofMillis(2))
                        .subtractiveJitter(0)
                        .build());
    }

    static TypeSafeClient client(HttpClient httpClient) {
        return new TypeSafeClient(options().build(), httpClient);
    }

    static UnaryOperator<String> environment(Map<String, String> values) {
        return values::get;
    }

    static SystemOneRequest request() {
        return SystemOneRequest.builder()
                .state("state")
                .question("x", Questions.noul("Classify"))
                .build();
    }

    static RequestOptions noRetry() {
        return RequestOptions.builder()
                .retry(RetryOptions.builder().maxRetries(0).build())
                .build();
    }

    static List<ai.typesafe.json.JsonValue> labels(String... labels) {
        return java.util.Arrays.stream(labels).map(Json::of).toList();
    }

    static final class RecordingLogger implements TypeSafeLogger {
        private final StringBuilder message = new StringBuilder();
        private final List<LogLevel> levels = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public synchronized void log(LogLevel level, String text) {
            levels.add(level);
            message.append(text);
        }

        synchronized String message() {
            return message.toString();
        }

        List<LogLevel> levels() {
            return levels;
        }
    }
}
