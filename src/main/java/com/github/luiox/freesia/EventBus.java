package com.github.luiox.freesia;

/**
 * Accepts listeners and delivers events to them.
 *
 * <p>Dispatch is by exact runtime class: a listener registered for a supertype does not
 * receive a subtype event. The reason is that walking the type hierarchy has to be
 * re-done whenever a new subclass appears, and needs de-duplication to avoid delivering
 * both a supertype and subtype listener for the same event. Event hierarchies that read
 * naturally in source are a naming convention, not a bus feature.
 */
public interface EventBus {

    /**
     * Delivers {@code event} to every listener registered for its exact class.
     *
     * <p>Returns the same instance so a hook site can read mutated fields back after the
     * call - a movement event whose rotation a listener overwrote, say. Exceptions thrown
     * by a listener are routed to the {@link ListenerErrorHandler} and do not abort the
     * remaining listeners; only a {@link SingletonEvent} reentrancy violation and a
     * {@link EventThreadGuard} rejection propagate.
     */
    <E extends Event> E post(E event);

    /**
     * Whether anything listens for {@code eventType}. Cheap enough to call from a hook
     * site on the hot path when the answer decides whether to build an event at all.
     */
    <E extends Event> boolean hasListeners(Class<E> eventType);

    /** @return false if this container was already registered, or declares no listeners */
    boolean register(Object listenerContainer);

    boolean unregister(Object listenerContainer);
}
