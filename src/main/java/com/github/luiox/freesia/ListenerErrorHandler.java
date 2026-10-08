package com.github.luiox.freesia;

/**
 * Receives exceptions thrown by listeners.
 *
 * <p>A listener that throws must not abort the dispatch: the remaining listeners of that
 * event still have work to do this tick, and one broken module should not disable every
 * other module for the rest of the session. Equally, the failure must not vanish - a
 * combat feature that quietly stops running is far more expensive to diagnose than a
 * crash.
 *
 * <p>So the bus isolates <em>and</em> reports. Supply your own handler if you want to
 * count failures, surface them in an overlay, or disable the offending module.
 */
@FunctionalInterface
public interface ListenerErrorHandler {

    void onListenerError(EventListener listener, Event event, Throwable error);

    /**
     * The default: one line on the system logger, naming the owning class, the method
     * and the event, because a stack trace alone does not tell you which of several
     * hundred listeners failed.
     */
    static ListenerErrorHandler logging() {
        System.Logger logger = System.getLogger(EventManager.class.getPackageName());
        return (listener, event, error) -> logger.log(System.Logger.Level.ERROR,
                "listener " + listener.describe() + " threw while handling " + event.getClass().getName(), error);
    }
}
