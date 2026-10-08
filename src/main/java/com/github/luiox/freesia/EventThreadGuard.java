package com.github.luiox.freesia;

/**
 * Checks that an event is posted from a thread allowed to post it.
 *
 * <p>This exists because a real risk in a game client is not contention but silently
 * shared mutable state: a hook that fires on the render thread and a hook that fires
 * on the client thread both reaching into the same module field. That is a data race
 * that shows up as intermittent, unreproducible misbehaviour.
 *
 * <p>A guard turns that into a crash on the first occurrence. It is deliberately
 * pluggable rather than built in, because freesia has no notion of what a render thread
 * is; the embedding application supplies the policy.
 *
 * <p>See {@link #ACCEPT_ALL} for the default.
 */
@FunctionalInterface
public interface EventThreadGuard {

    EventThreadGuard ACCEPT_ALL = (eventType, poster) -> true;

    /**
     * @return whether {@code poster} is allowed to post events of {@code eventType}
     */
    boolean isValidThread(Class<? extends Event> eventType, Thread poster);

    /**
     * Called when {@link #isValidThread} returns false. The default throws, which is
     * the useful behaviour; override to log and continue if you would rather degrade
     * than crash.
     */
    default void onViolation(Class<? extends Event> eventType, Thread poster) {
        throw new EventThreadViolationException(eventType, poster);
    }
}
