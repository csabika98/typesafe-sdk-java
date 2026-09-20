package ai.typesafe;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RequestOptions {
    private static final RequestOptions NONE = builder().build();

    private Duration timeout;
    private RetryOptions retry;
    private Map<String, String> headers = Map.of();

    private RequestOptions() {
    }

    public static RequestOptions none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    Duration timeout() {
        return timeout;
    }

    RetryOptions retry() {
        return retry;
    }

    Map<String, String> headers() {
        return headers;
    }

    public static final class Builder {
        private final RequestOptions options = new RequestOptions();
        private final Map<String, String> headers = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder timeout(Duration timeout) {
            options.timeout = timeout;
            return this;
        }

        public Builder retry(RetryOptions retry) {
            options.retry = retry;
            return this;
        }

        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers.putAll(headers);
            return this;
        }

        public RequestOptions build() {
            options.headers = Map.copyOf(headers);
            return options;
        }
    }
}
