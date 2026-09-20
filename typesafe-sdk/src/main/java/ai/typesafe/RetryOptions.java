package ai.typesafe;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class RetryOptions {
    private Integer maxRetries;
    private Duration initialDelay;
    private Duration maxDelay;
    private Double subtractiveJitter;
    private Set<Integer> statusCodes;
    private Boolean respectRetryAfter;
    private Duration maxRetryAfter;
    private Boolean retryConnectionErrors;
    private Boolean retryTimeouts;

    private RetryOptions() {
    }

    public static Builder builder() {
        return new Builder();
    }

    Integer maxRetries() {
        return maxRetries;
    }

    Duration initialDelay() {
        return initialDelay;
    }

    Duration maxDelay() {
        return maxDelay;
    }

    Double subtractiveJitter() {
        return subtractiveJitter;
    }

    Set<Integer> statusCodes() {
        return statusCodes;
    }

    Boolean respectRetryAfter() {
        return respectRetryAfter;
    }

    Duration maxRetryAfter() {
        return maxRetryAfter;
    }

    Boolean retryConnectionErrors() {
        return retryConnectionErrors;
    }

    Boolean retryTimeouts() {
        return retryTimeouts;
    }

    public static final class Builder {
        private final RetryOptions options = new RetryOptions();

        private Builder() {
        }

        public Builder maxRetries(int maxRetries) {
            options.maxRetries = maxRetries;
            return this;
        }

        public Builder initialDelay(Duration initialDelay) {
            options.initialDelay = initialDelay;
            return this;
        }

        public Builder maxDelay(Duration maxDelay) {
            options.maxDelay = maxDelay;
            return this;
        }

        public Builder subtractiveJitter(double subtractiveJitter) {
            options.subtractiveJitter = subtractiveJitter;
            return this;
        }

        public Builder statusCodes(Collection<Integer> statusCodes) {
            options.statusCodes = new LinkedHashSet<>(statusCodes);
            return this;
        }

        public Builder respectRetryAfter(boolean respectRetryAfter) {
            options.respectRetryAfter = respectRetryAfter;
            return this;
        }

        public Builder maxRetryAfter(Duration maxRetryAfter) {
            options.maxRetryAfter = maxRetryAfter;
            return this;
        }

        public Builder retryConnectionErrors(boolean retryConnectionErrors) {
            options.retryConnectionErrors = retryConnectionErrors;
            return this;
        }

        public Builder retryTimeouts(boolean retryTimeouts) {
            options.retryTimeouts = retryTimeouts;
            return this;
        }

        public RetryOptions build() {
            return options;
        }
    }
}
