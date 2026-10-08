package com.github.luiox.freesia;

/**
 * An {@link Event} that is expected to be allocated once per type and reused, so that
 * hot paths such as per-tick or per-packet notification allocate nothing.
 *
 * <p>The contract is narrow and worth stating plainly, because violating it produces
 * bugs that look like randomness:
 *
 * <ol>
 *   <li><strong>One instance per event type.</strong> The hook site calls the type's
 *       static accessor, which calls {@link #reset()} before returning, then assigns
 *       every field and posts it. Resetting at <em>acquisition</em> rather than after
 *       dispatch is what allows the hook to read the event's fields back after
 *       {@code post} returns.</li>
 *   <li><strong>Never escape the dispatch call stack.</strong> Storing the event in a
 *       field, queueing it, or handing it to another thread is not allowed - the next
 *       post overwrites it. Use an ordinary {@link Event} for anything that escapes.</li>
 *   <li><strong>Never post the same instance reentrantly.</strong> A listener that
 *       triggers another post of the same type would have its fields clobbered by the
 *       inner dispatch. The bus detects this and throws {@link ReentrantPostException}
 *       rather than letting it corrupt silently.</li>
 *   <li><strong>One posting thread at a time.</strong> The reentrancy flag is a plain
 *       field with no memory barriers. Concurrent posts from two threads are a contract
 *       violation; the flag will still usually catch it, but do not rely on that.</li>
 * </ol>
 */
public abstract class SingletonEvent extends Event {
    private boolean dispatching;

    /**
     * Clears every field, so the next dispatch starts from a known state.
     *
     * <p>Called by the static accessor that hands out the instance, never by the bus.
     */
    public abstract void reset();

    public final boolean isDispatching() {
        return this.dispatching;
    }

    /**
     * @return {@code false} if this instance is already being dispatched further up the
     *         stack, in which case the caller must not dispatch it again.
     */
    public final boolean tryBeginDispatch() {
        if (this.dispatching) {
            return false;
        }
        this.dispatching = true;
        return true;
    }

    public final void endDispatch() {
        this.dispatching = false;
    }
}
