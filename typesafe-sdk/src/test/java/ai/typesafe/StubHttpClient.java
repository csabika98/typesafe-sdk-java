package ai.typesafe;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

final class StubHttpClient extends HttpClient {
    @FunctionalInterface
    interface Handler {
        CompletableFuture<HttpResponse<byte[]>> handle(HttpRequest request, int attempt);
    }

    private final Handler handler;
    private final AtomicInteger attempts = new AtomicInteger();
    private final List<HttpRequest> requests = new CopyOnWriteArrayList<>();
    private volatile boolean closed;

    StubHttpClient(Handler handler) {
        this.handler = handler;
    }

    static StubHttpClient always(int status, String body, String... headerPairs) {
        return new StubHttpClient((request, attempt) ->
                CompletableFuture.completedFuture(StubResponse.of(request, status, body, headerPairs)));
    }

    static StubHttpClient sequence(StubResponse.Spec... specs) {
        List<StubResponse.Spec> list = List.of(specs);
        return new StubHttpClient((request, attempt) -> CompletableFuture.completedFuture(
                list.get(Math.min(attempt - 1, list.size() - 1)).toResponse(request)));
    }

    int attempts() {
        return attempts.get();
    }

    List<HttpRequest> requests() {
        return new ArrayList<>(requests);
    }

    HttpRequest lastRequest() {
        return requests.get(requests.size() - 1);
    }

    boolean isClosed() {
        return closed;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        requests.add(request);
        return (CompletableFuture<HttpResponse<T>>) (CompletableFuture<?>)
                handler.handle(request, attempts.incrementAndGet());
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return sendAsync(request, responseBodyHandler);
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
        try {
            return sendAsync(request, responseBodyHandler).get();
        } catch (java.util.concurrent.ExecutionException exception) {
            if (exception.getCause() instanceof IOException cause) {
                throw cause;
            }
            throw new IOException(exception.getCause());
        }
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
        return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
        try {
            return SSLContext.getDefault();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public SSLParameters sslParameters() {
        return new SSLParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return Optional.empty();
    }

    @Override
    public Version version() {
        return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
        return Optional.empty();
    }
}
