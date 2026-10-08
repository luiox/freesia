package com.github.luiox.freesia.filter;

import com.github.luiox.freesia.Event;
import com.github.luiox.freesia.EventListener;

/**
 * A precondition evaluated before the listener method runs. Returning {@code false} skips
 * the method and nothing else - the event is not cancelled and dispatch continues.
 *
 * <p>Typed against the concrete event so implementations get a checked parameter. The bus
 * stores filters in a raw {@code EventFilter[]}, which is sound because erasure makes the
 * second parameter {@link Event} whatever {@code E} was; a filter handed the wrong event
 * type fails loudly at the bridge method rather than silently passing.
 *
 * <p>Filters exist for conditions cheap enough to evaluate here - a feature flag, a config
 * toggle. Anything that needs to read world state belongs in the listener body, because
 * filters run on every dispatch, including the ones that end up cancelled away.
 */
@FunctionalInterface
public interface EventFilter<E extends Event> {

    boolean test(EventListener listener, E event);
}
