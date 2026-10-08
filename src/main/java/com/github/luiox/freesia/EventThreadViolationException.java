package com.github.luiox.freesia;

/**
 * Thrown when an event is posted from a thread the configured {@link EventThreadGuard}
 * does not allow.
 */
public final class EventThreadViolationException extends EventBusViolation {
    private static final long serialVersionUID = 1L;

    private final transient Class<? extends Event> eventType;
    private final transient Thread poster;

    public EventThreadViolationException(Class<? extends Event> eventType, Thread poster) {
        super("thread '" + poster.getName() + "' is not allowed to post " + eventType.getName());
        this.eventType = eventType;
        this.poster = poster;
    }

    public Class<? extends Event> getEventType() {
        return this.eventType;
    }

    public Thread getPoster() {
        return this.poster;
    }
}
