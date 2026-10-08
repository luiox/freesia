package com.github.luiox.freesia;

/**
 * A {@link SingletonEvent} that can also be cancelled.
 *
 * <p>Reuse and cancellation are orthogonal axes, and the hot events that need both - a
 * movement event whose rotation a listener overwrites and another listener may veto, say -
 * would otherwise have to hand-roll the flag. Combines
 * {@link SingletonEvent#reset() the reset contract} with
 * {@link CancellableEvent#clearCancelled() the cancelled flag}.
 */
public abstract class SingletonCancellableEvent extends SingletonEvent implements ICancellable {

    private boolean cancelled;

    @Override
    public final boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public final void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /** Resets the cancelled flag. Call from your {@link SingletonEvent#reset()}. */
    protected final void clearCancelled() {
        this.cancelled = false;
    }
}
