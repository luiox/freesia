package com.github.luiox.freesia;

/**
 * Base type for everything dispatched through an {@link EventBus}.
 *
 * <p>An event carries the data a hook site captured, nothing else. It is deliberately
 * <em>not</em> {@link ICancellable}: the overwhelming majority of events are pure
 * notifications, and making cancellation universal would force every dispatch loop to
 * pay for a check that can never fire. Use {@link CancellableEvent} for the events that
 * genuinely can be cancelled.
 *
 * <p><strong>Event types are dispatched by exact runtime class.</strong> A listener
 * registered for a supertype never receives a subtype event. If you need a hook that
 * covers a family of events, declare one listener per concrete event class - do not
 * introduce an inheritance hierarchy and rely on the bus walking it.
 */
public abstract class Event {
}
