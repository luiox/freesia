package com.github.luiox.freesia.handler;

import com.github.luiox.freesia.filter.EventFilter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a single-argument method as an event listener.
 *
 * <p>The argument type is the event type; dispatch is by exact runtime class, so a
 * listener for a supertype never sees a subtype.
 *
 * <p>Listener methods are discovered on the whole class chain and on implemented
 * interfaces, so the natural way to share behaviour is a base class or an interface
 * carrying the {@code @Listener} methods. A method overridden further down the chain is
 * registered once, at the most derived declaration.
 *
 * <pre>{@code
 * public final class Tick {
 *     private static final Tick INSTANCE = new Tick();
 *
 *     public static Tick get() {
 *         INSTANCE.reset();
 *         return INSTANCE;
 *     }
 *
 *     public long tickCount;
 *
 *     @Override
 *     public void reset() {
 *         this.tickCount = 0L;
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Listener {

    /** Types instantiated once per listener and consulted before the method is called. */
    Class<? extends EventFilter>[] filters() default {};

    ListenerPriority priority() default ListenerPriority.NORMAL;

    /**
     * Tie-break within one priority, ascending. Use this rather than inventing new
     * priority values.
     */
    int order() default 0;
}
