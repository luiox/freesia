package com.github.luiox.freesia;

/**
 * Convenience base class for a cancellable {@link Event}.
 *
 * <p>Subclasses must be dispatched from a single thread, or at least not concurrently -
 * the flag below has no synchronisation, by design. See {@link SingletonEvent} for the
 * reason that matters.
 */
public abstract class CancellableEvent extends Event implements ICancellable {
    private boolean cancelled;

    @Override
    public final boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public final void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /** Resets the cancelled flag. Call from your own reset path, if the event has one. */
    protected final void clearCancelled() {
        this.cancelled = false;
    }
}
