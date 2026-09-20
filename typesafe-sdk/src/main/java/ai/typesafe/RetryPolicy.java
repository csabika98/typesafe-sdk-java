package ai.typesafe;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.IntStream;

public final class RetryPolicy {
    private static final Set<Integer> DEFAULT_STATUS_CODES = defaultStatusCodes();

    static final RetryPolicy DEFAULT = new RetryPolicy(
            2,
            Duration.ofMillis(500),
            Duration.ofSeconds(5),
            0.25,
            DEFAULT_STATUS_CODES,
            true,
            Duration.ofSeconds(60),
            true,
            true);

    private final int maxRetries;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double subtractiveJitter;
    private final Set<Integer> statusCodes;
    private final boolean respectRetryAfter;
    private final Duration maxRetryAfter;
    private final boolean retryConnectionErrors;
    private final boolean retryTimeouts;

    RetryPolicy(
            int maxRetries,
            Duration initialDelay,
            Duration maxDelay,
            double subtractiveJitter,
            Collection<Integer> statusCodes,
            boolean respectRetryAfter,
            Duration maxRetryAfter,
            boolean retryConnectionErrors,
            boolean retryTimeouts) {
        this.maxRetries = maxRetries;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.subtractiveJitter = subtractiveJitter;
        this.statusCodes = Set.copyOf(new LinkedHashSet<>(statusCodes));
        this.respectRetryAfter = respectRetryAfter;
        this.maxRetryAfter = maxRetryAfter;
        this.retryConnectionErrors = retryConnectionErrors;
        this.retryTimeouts = retryTimeouts;
        validate();
    }

    private static Set<Integer> defaultStatusCodes() {
        Set<Integer> statuses = new LinkedHashSet<>();
        statuses.add(408);
        statuses.add(429);
        IntStream.range(500, 600).forEach(statuses::add);
        return Set.copyOf(statuses);
    }

    public int maxRetries() {
        return maxRetries;
    }

    public Duration initialDelay() {
        return initialDelay;
    }

    public Duration maxDelay() {
        return maxDelay;
    }

    public double subtractiveJitter() {
        return subtractiveJitter;
    }

    public Set<Integer> statusCodes() {
        return statusCodes;
    }

    public boolean respectRetryAfter() {
        return respectRetryAfter;
    }

    public Duration maxRetryAfter() {
        return maxRetryAfter;
    }

    public boolean retryConnectionErrors() {
        return retryConnectionErrors;
    }

    public boolean retryTimeouts() {
        return retryTimeouts;
    }

    RetryPolicy layer(RetryOptions options) {
        if (options == null) {
            return this;
        }
        return new RetryPolicy(
                options.maxRetries() == null ? maxRetries : options.maxRetries(),
                options.initialDelay() == null ? initialDelay : options.initialDelay(),
                options.maxDelay() == null ? maxDelay : options.maxDelay(),
                options.subtractiveJitter() == null ? subtractiveJitter : options.subtractiveJitter(),
                options.statusCodes() == null ? statusCodes : options.statusCodes(),
                options.respectRetryAfter() == null ? respectRetryAfter : options.respectRetryAfter(),
                options.maxRetryAfter() == null ? maxRetryAfter : options.maxRetryAfter(),
                options.retryConnectionErrors() == null
                        ? retryConnectionErrors
                        : options.retryConnectionErrors(),
                options.retryTimeouts() == null ? retryTimeouts : options.retryTimeouts());
    }

    private void validate() {
        if (maxRetries < 0) {
            throw new TypeSafeException("maxRetries cannot be negative.");
        }
        if (initialDelay.isNegative()) {
            throw new TypeSafeException("initialDelay cannot be negative.");
        }
        if (maxDelay.isNegative()) {
            throw new TypeSafeException("maxDelay cannot be negative.");
        }
        if (maxRetryAfter.isNegative()) {
            throw new TypeSafeException("maxRetryAfter cannot be negative.");
        }
        if (subtractiveJitter < 0 || subtractiveJitter > 1) {
            throw new TypeSafeException("subtractiveJitter must be between 0 and 1.");
        }
        if (statusCodes.stream().anyMatch(status -> status < 100 || status > 999)) {
            throw new TypeSafeException("Retry status codes must be between 100 and 999.");
        }
    }
}
