package ai.typesafe;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class TypeSafeClient implements AutoCloseable {
    private static final String SDK_VALUE = "typesafe-sdk/0.6.0";
    private static final Set<String> PROTECTED_HEADERS = Set.of(
            "accept", "authorization", "content-type", "user-agent",
            "x-typesafe-sdk", "x-typesafe-runtime", "x-typesafe-retry-count");

    private final HttpClient httpClient;
    private final boolean ownsHttpClient;
    private final ResolvedOptions options;
    private final Random random = new Random();

    public TypeSafeClient() {
        this(TypeSafeClientOptions.fromEnvironment(), null);
    }

    public TypeSafeClient(TypeSafeClientOptions options) {
        this(options, null);
    }

    public TypeSafeClient(TypeSafeClientOptions options, HttpClient httpClient) {
        this.options = (options == null ? TypeSafeClientOptions.fromEnvironment() : options).resolve();
        this.ownsHttpClient = httpClient == null;
        this.httpClient = httpClient == null
                ? HttpClient.newBuilder().connectTimeout(this.options.timeout()).build()
                : httpClient;
    }

    public URI baseUrl() {
        return options.baseUrl();
    }

    public String defaultModel() {
        return options.defaultModel();
    }

    public LogLevel logLevel() {
        return options.logLevel();
    }

    public Duration timeout() {
        return options.timeout();
    }

    public RetryPolicy retry() {
        return options.retry();
    }

    public Map<String, String> defaultHeaders() {
        return options.defaultHeaders();
    }

    public SystemOneResult systemOne(SystemOneRequest request) {
        return systemOne(request, RequestOptions.none());
    }

    public SystemOneResult systemOne(SystemOneRequest request, RequestOptions requestOptions) {
        return systemOneWithResponse(request, requestOptions).data();
    }

    public ApiResponse<SystemOneResult> systemOneWithResponse(SystemOneRequest request) {
        return systemOneWithResponse(request, RequestOptions.none());
    }

    public ApiResponse<SystemOneResult> systemOneWithResponse(
            SystemOneRequest request, RequestOptions requestOptions) {
        return await(systemOneWithResponseAsync(request, requestOptions));
    }

    public CompletableFuture<SystemOneResult> systemOneAsync(SystemOneRequest request) {
        return systemOneAsync(request, RequestOptions.none());
    }

    public CompletableFuture<SystemOneResult> systemOneAsync(
            SystemOneRequest request, RequestOptions requestOptions) {
        return systemOneWithResponseAsync(request, requestOptions).thenApply(ApiResponse::data);
    }

    public CompletableFuture<ApiResponse<SystemOneResult>> systemOneWithResponseAsync(
            SystemOneRequest request, RequestOptions requestOptions) {
        if (request == null) {
            throw new TypeSafeException("A request is required.");
        }
        SystemOneRequest payload =
                request.model() == null ? request.withModel(options.defaultModel()) : request;
        return send(
                "/v1/systemone",
                "POST",
                Wire.serialize(payload),
                Wire::parseSystemOne,
                requestOptions);
    }

    public List<ModelCard> models() {
        return models(RequestOptions.none());
    }

    public List<ModelCard> models(RequestOptions requestOptions) {
        return modelsWithResponse(requestOptions).data();
    }

    public ApiResponse<List<ModelCard>> modelsWithResponse() {
        return modelsWithResponse(RequestOptions.none());
    }

    public ApiResponse<List<ModelCard>> modelsWithResponse(RequestOptions requestOptions) {
        return await(modelsWithResponseAsync(requestOptions));
    }

    public CompletableFuture<List<ModelCard>> modelsAsync() {
        return modelsAsync(RequestOptions.none());
    }

    public CompletableFuture<List<ModelCard>> modelsAsync(RequestOptions requestOptions) {
        return modelsWithResponseAsync(requestOptions).thenApply(ApiResponse::data);
    }

    public CompletableFuture<ApiResponse<List<ModelCard>>> modelsWithResponseAsync(
            RequestOptions requestOptions) {
        return send("/v1/models", "GET", null, Wire::parseModels, requestOptions);
    }

    @Override
    public void close() {
        if (ownsHttpClient) {
            httpClient.close();
        }
    }

    private <T> T await(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new ApiUserAbortException("Request was cancelled by the caller.", exception);
        } catch (CancellationException exception) {
            throw new ApiUserAbortException("Request was cancelled by the caller.", exception);
        } catch (ExecutionException exception) {
            throw rethrow(exception.getCause());
        }
    }

    private static RuntimeException rethrow(Throwable cause) {
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        return new TypeSafeException("The request failed.", cause);
    }

    private <T> CompletableFuture<ApiResponse<T>> send(
            String path,
            String method,
            String body,
            Function<String, T> parser,
            RequestOptions requestOptions) {
        RequestOptions resolvedRequestOptions =
                requestOptions == null ? RequestOptions.none() : requestOptions;
        RetryPolicy retry = options.retry().layer(resolvedRequestOptions.retry());
        Duration timeout = resolvedRequestOptions.timeout() == null
                ? options.timeout()
                : resolvedRequestOptions.timeout();
        if (timeout.isZero() || timeout.isNegative()) {
            throw new TypeSafeException("timeout must be positive.");
        }

        AbortableFuture<ApiResponse<T>> result = new AbortableFuture<>();
        attempt(result, 0, path, method, body, parser, resolvedRequestOptions, retry, timeout);
        return result;
    }

    private <T> void attempt(
            AbortableFuture<ApiResponse<T>> result,
            int attempt,
            String path,
            String method,
            String body,
            Function<String, T> parser,
            RequestOptions requestOptions,
            RetryPolicy retry,
            Duration timeout) {
        if (result.isDone()) {
            return;
        }

        HttpRequest request;
        try {
            request = createRequest(path, method, body, requestOptions.headers(), attempt, timeout);
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
            return;
        }
        logRequest(request);

        CompletableFuture<HttpResponse<byte[]>> exchange =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        result.track(exchange);

        exchange.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((response, throwable) -> {
                    if (result.isDone()) {
                        return;
                    }
                    try {
                        if (throwable != null) {
                            exchange.cancel(true);
                            onFailure(unwrap(throwable), result, attempt, path, method, body, parser,
                                    requestOptions, retry, timeout);
                            return;
                        }
                        onResponse(response, result, attempt, path, method, body, parser,
                                requestOptions, retry, timeout);
                    } catch (RuntimeException exception) {
                        result.completeExceptionally(exception);
                    }
                });
    }

    private <T> void onResponse(
            HttpResponse<byte[]> response,
            AbortableFuture<ApiResponse<T>> result,
            int attempt,
            String path,
            String method,
            String body,
            Function<String, T> parser,
            RequestOptions requestOptions,
            RetryPolicy retry,
            Duration timeout) {
        int status = response.statusCode();
        log(LogLevel.INFO, method + " " + path + " returned " + status + ".");

        if (retry.statusCodes().contains(status) && attempt < retry.maxRetries()) {
            Duration delay = retryDelay(response.headers(), attempt, retry);
            log(LogLevel.INFO, "Retrying " + method + " " + path + " after status " + status
                    + " in " + delay.toMillis() + " ms.");
            scheduleRetry(result, attempt, path, method, body, parser, requestOptions, retry, timeout, delay);
            return;
        }

        String text = new String(response.body(), StandardCharsets.UTF_8);
        if (status < 200 || status >= 300) {
            result.completeExceptionally(ApiErrors.create(response, text, retry));
            return;
        }

        result.complete(ApiResponse.of(parser.apply(text), response, text));
    }

    private <T> void onFailure(
            Throwable cause,
            AbortableFuture<ApiResponse<T>> result,
            int attempt,
            String path,
            String method,
            String body,
            Function<String, T> parser,
            RequestOptions requestOptions,
            RetryPolicy retry,
            Duration timeout) {
        if (cause instanceof CancellationException) {
            result.completeExceptionally(
                    new ApiUserAbortException("Request was cancelled by the caller.", cause));
            return;
        }

        boolean timedOut = cause instanceof HttpTimeoutException || cause instanceof TimeoutException;
        if (timedOut) {
            if (retry.retryTimeouts() && attempt < retry.maxRetries()) {
                Duration delay = backoff(attempt, retry);
                log(LogLevel.INFO, "Retrying " + method + " " + path + " after a timeout in "
                        + delay.toMillis() + " ms.");
                scheduleRetry(result, attempt, path, method, body, parser, requestOptions, retry,
                        timeout, delay);
                return;
            }
            log(LogLevel.INFO, method + " " + path + " timed out.");
            result.completeExceptionally(
                    new ApiTimeoutException("Request timed out after " + timeout + ".", cause, timeout));
            return;
        }

        if (cause instanceof IOException || cause instanceof InterruptedException) {
            if (retry.retryConnectionErrors() && attempt < retry.maxRetries()) {
                Duration delay = backoff(attempt, retry);
                log(LogLevel.INFO, "Retrying " + method + " " + path
                        + " after a connection failure in " + delay.toMillis() + " ms.");
                scheduleRetry(result, attempt, path, method, body, parser, requestOptions, retry,
                        timeout, delay);
                return;
            }
            log(LogLevel.INFO, method + " " + path + " failed to connect.");
            result.completeExceptionally(
                    new ApiConnectionException("Connection to TypeSafe failed.", cause));
            return;
        }

        result.completeExceptionally(rethrow(cause));
    }

    private <T> void scheduleRetry(
            AbortableFuture<ApiResponse<T>> result,
            int attempt,
            String path,
            String method,
            String body,
            Function<String, T> parser,
            RequestOptions requestOptions,
            RetryPolicy retry,
            Duration timeout,
            Duration delay) {
        CompletableFuture<Void> waiter = new CompletableFuture<>();
        result.track(waiter);
        waiter.completeOnTimeout(null, Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS)
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        if (!result.isDone()) {
                            result.completeExceptionally(new ApiUserAbortException(
                                    "Request was cancelled by the caller while waiting to retry.",
                                    unwrap(throwable)));
                        }
                        return;
                    }
                    attempt(result, attempt + 1, path, method, body, parser, requestOptions, retry, timeout);
                });
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof CompletionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
    }

    private HttpRequest createRequest(
            String path,
            String method,
            String body,
            Map<String, String> callHeaders,
            int attempt,
            Duration timeout) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(options.baseUrl() + path))
                .timeout(timeout);

        addUnprotected(builder, options.defaultHeaders());
        addUnprotected(builder, callHeaders);

        builder.header("Accept", "application/json");
        builder.header("Authorization", "Bearer " + options.apiKey());
        builder.header("User-Agent", SDK_VALUE);
        builder.header("X-TypeSafe-SDK", SDK_VALUE);
        builder.header("X-TypeSafe-Runtime", runtimeDescription());
        if (attempt > 0) {
            builder.header("X-TypeSafe-Retry-Count", Integer.toString(attempt));
        }

        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    private void addUnprotected(HttpRequest.Builder builder, Map<String, String> headers) {
        headers.forEach((name, value) -> {
            if (PROTECTED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                return;
            }
            try {
                builder.setHeader(name, value);
            } catch (IllegalArgumentException exception) {
                log(LogLevel.WARN, "Ignoring header '" + name + "', which the HTTP client restricts.");
            }
        });
    }

    private static String runtimeDescription() {
        return "Java/" + System.getProperty("java.version")
                + " (" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + "; " + System.getProperty("os.arch") + ")";
    }

    private void logRequest(HttpRequest request) {
        if (!isEnabled(LogLevel.DEBUG)) {
            return;
        }
        log(LogLevel.DEBUG, request.method() + " " + request.uri() + " "
                + CredentialRedactor.redactHeaders(request.headers().map()));
    }

    private boolean isEnabled(LogLevel level) {
        return options.logLevel() != LogLevel.OFF && level.compareTo(options.logLevel()) >= 0;
    }

    private void log(LogLevel level, String message) {
        if (isEnabled(level)) {
            options.logger().log(level, message);
        }
    }

    private Duration retryDelay(HttpHeaders headers, int attempt, RetryPolicy retry) {
        if (retry.respectRetryAfter()) {
            Optional<Duration> server = RetryAfter.read(headers, retry.maxRetryAfter());
            if (server.isPresent()) {
                return server.get();
            }
        }
        return backoff(attempt, retry);
    }

    private Duration backoff(int attempt, RetryPolicy retry) {
        double exponential = Math.min(
                retry.maxDelay().toMillis(),
                retry.initialDelay().toMillis() * Math.pow(2, attempt));
        double factor = 1 - (random.nextDouble() * retry.subtractiveJitter());
        return Duration.ofMillis((long) (exponential * factor));
    }
}
