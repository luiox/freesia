package com.github.luiox.freesia.handler;

/**
 * Wraps whatever a listener method threw, naming the listener and the event.
 *
 * <p>The bus routes this to the configured {@link com.github.luiox.freesia.ListenerErrorHandler}
 * so the report identifies which of several hundred listeners failed. Without that, the
 * only clue is a stack trace whose top frame is the dispatch loop.
 */
public final class ListenerInvocationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String listener;
    private final Class<?> eventType;

    public ListenerInvocationException(String listener, Class<?> eventType, Throwable cause) {
        super(listener + " threw while handling " + eventType.getName(), cause);
        this.listener = listener;
        this.eventType = eventType;
    }

    public String getListener() {
        return this.listener;
    }

    public Class<?> getEventType() {
        return this.eventType;
    }
}
