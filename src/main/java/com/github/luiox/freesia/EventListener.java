package com.github.luiox.freesia;

import com.github.luiox.freesia.handler.ListenerPriority;

/**
 * One listener method, bound to its owning instance and its target event type.
 *
 * <p>Resolved once at registration; the dispatch loop does nothing but call
 * {@link #invoke(Event)}.
 */
public interface EventListener {

    /**
     * Calls the listener. Declared to accept {@link Event} so a bucket can hold entries
     * for one concrete event type without generic gymnastics; the bucket guarantees the
     * runtime type matches {@link #eventType()}.
     */
    void invoke(Event event);

    Class<? extends Event> eventType();

    ListenerPriority priority();

    /**
     * Tie-break within one priority level, ascending. This is the supported way to
     * interleave listeners, as opposed to arithmetic on the priority value, which makes
     * the resulting order impossible to reason about once more than one module does it.
     */
    int order();

    /** The instance the listener method is invoked on. */
    Object owner();

    /**
     * A short human-readable identity for diagnostics. Should name the owning class and
     * the method: "a listener threw" is useless when there are hundreds.
     */
    String describe();
}
