package ai.typesafe;

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

final class BodyCollector implements Flow.Subscriber<ByteBuffer> {
    private final List<byte[]> chunks = new CopyOnWriteArrayList<>();
    private final CompletableFuture<String> result = new CompletableFuture<>();

    private BodyCollector() {
    }

    static String collect(HttpRequest.BodyPublisher publisher) {
        BodyCollector collector = new BodyCollector();
        publisher.subscribe(collector);
        try {
            return collector.result.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(ByteBuffer item) {
        byte[] chunk = new byte[item.remaining()];
        item.get(chunk);
        chunks.add(chunk);
    }

    @Override
    public void onError(Throwable throwable) {
        result.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        int size = chunks.stream().mapToInt(chunk -> chunk.length).sum();
        byte[] body = new byte[size];
        int offset = 0;
        for (byte[] chunk : chunks) {
            System.arraycopy(chunk, 0, body, offset, chunk.length);
            offset += chunk.length;
        }
        result.complete(new String(body, StandardCharsets.UTF_8));
    }
}
