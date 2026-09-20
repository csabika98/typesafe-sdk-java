package ai.typesafe;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

final class AbortableFuture<T> extends CompletableFuture<T> {
    private final AtomicReference<CompletableFuture<?>> inFlight = new AtomicReference<>();

    void track(CompletableFuture<?> stage) {
        inFlight.set(stage);
        if (isDone()) {
            stage.cancel(true);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean aborted = completeExceptionally(
                new ApiUserAbortException("Request was cancelled by the caller."));
        CompletableFuture<?> stage = inFlight.get();
        if (stage != null) {
            stage.cancel(true);
        }
        return aborted;
    }

    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        AbortableFuture<U> derived = new AbortableFuture<>();
        derived.track(this);
        return derived;
    }
}
