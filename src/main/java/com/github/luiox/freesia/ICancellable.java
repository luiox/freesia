package com.github.luiox.freesia;

/**
 * An event whose dispatch can be cut short.
 *
 * <p>Cancellation is observed after each listener returns, so the listener that
 * cancelled still ran, and every listener of lower priority is skipped. Listeners of
 * equal priority registered later are skipped too - ordering is deterministic, so
 * "who gets to cancel" is always answerable by reading the priorities.
 */
public interface ICancellable {

    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
