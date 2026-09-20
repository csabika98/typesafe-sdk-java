package ai.typesafe;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

public final class TypeSafeClientOptions {
    private String apiKey;
    private URI baseUrl;
    private String defaultModel;
    private Duration timeout;
    private LogLevel logLevel;
    private RetryOptions retry;
    private Map<String, String> defaultHeaders;
    private TypeSafeLogger logger;
    private UnaryOperator<String> environment = System::getenv;

    private TypeSafeClientOptions() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static TypeSafeClientOptions fromEnvironment() {
        return builder().build();
    }

    ResolvedOptions resolve() {
        String resolvedKey = apiKey == null ? environmentValue("TYPESAFE_API_KEY") : apiKey.trim();
        if (resolvedKey == null || resolvedKey.isBlank()) {
            throw new TypeSafeException("An API key is required. Set apiKey or TYPESAFE_API_KEY.");
        }

        URI configuredBaseUrl = baseUrl;
        if (configuredBaseUrl == null) {
            String fromEnvironment = environmentValue("TYPESAFE_BASE_URL");
            configuredBaseUrl = fromEnvironment == null
                    ? URI.create("https://api.typesafe.ai")
                    : createUri(fromEnvironment, "TYPESAFE_BASE_URL");
        }
        URI resolvedBaseUrl = createUri(trimTrailingSlashes(configuredBaseUrl.toString()), "baseUrl");

        String resolvedModel = defaultModel == null
                ? orDefault(environmentValue("TYPESAFE_DEFAULT_MODEL"), "jev-latest")
                : defaultModel.trim();
        if (resolvedModel.isBlank()) {
            throw new TypeSafeException("defaultModel cannot be blank.");
        }

        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new TypeSafeException("timeout must be positive.");
        }

        Map<String, String> resolvedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (defaultHeaders != null) {
            resolvedHeaders.putAll(defaultHeaders);
        }

        return new ResolvedOptions(
                resolvedKey,
                resolvedBaseUrl,
                resolvedModel,
                timeout == null ? Duration.ofSeconds(10) : timeout,
                resolveLogLevel(logLevel, environmentValue("TYPESAFE_LOG_LEVEL")),
                RetryPolicy.DEFAULT.layer(retry),
                java.util.Collections.unmodifiableMap(resolvedHeaders),
                logger == null ? new ConsoleLogger() : logger);
    }

    private String environmentValue(String name) {
        String value = environment.apply(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static String trimTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static URI createUri(String value, String name) {
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute() || uri.getHost() == null) {
                throw new TypeSafeException(name + " must be an absolute URI.");
            }
            return uri;
        } catch (URISyntaxException exception) {
            throw new TypeSafeException(name + " must be an absolute URI.", exception);
        }
    }

    private static LogLevel resolveLogLevel(LogLevel explicitLevel, String environmentLevel) {
        if (explicitLevel != null) {
            return explicitLevel;
        }
        if (environmentLevel == null) {
            return LogLevel.WARN;
        }
        return switch (environmentLevel.toLowerCase(Locale.ROOT)) {
            case "trace" -> LogLevel.TRACE;
            case "debug" -> LogLevel.DEBUG;
            case "info" -> LogLevel.INFO;
            case "warn" -> LogLevel.WARN;
            case "error" -> LogLevel.ERROR;
            case "off" -> LogLevel.OFF;
            default -> throw new TypeSafeException(
                    "Invalid log level '" + environmentLevel + "' in TYPESAFE_LOG_LEVEL.");
        };
    }

    public static final class Builder {
        private final TypeSafeClientOptions options = new TypeSafeClientOptions();

        private Builder() {
        }

        public Builder apiKey(String apiKey) {
            options.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(URI baseUrl) {
            options.baseUrl = baseUrl;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            options.baseUrl = URI.create(baseUrl);
            return this;
        }

        public Builder defaultModel(String defaultModel) {
            options.defaultModel = defaultModel;
            return this;
        }

        public Builder timeout(Duration timeout) {
            options.timeout = timeout;
            return this;
        }

        public Builder logLevel(LogLevel logLevel) {
            options.logLevel = logLevel;
            return this;
        }

        public Builder retry(RetryOptions retry) {
            options.retry = retry;
            return this;
        }

        public Builder defaultHeaders(Map<String, String> defaultHeaders) {
            options.defaultHeaders = new LinkedHashMap<>(defaultHeaders);
            return this;
        }

        public Builder defaultHeader(String name, String value) {
            if (options.defaultHeaders == null) {
                options.defaultHeaders = new LinkedHashMap<>();
            }
            options.defaultHeaders.put(name, value);
            return this;
        }

        public Builder logger(TypeSafeLogger logger) {
            options.logger = logger;
            return this;
        }

        public Builder environment(UnaryOperator<String> environment) {
            options.environment = environment;
            return this;
        }

        public TypeSafeClientOptions build() {
            return options;
        }
    }
}
