package com.github.luiox.freesia;

/**
 * Thrown when a {@link SingletonEvent} is posted while the same instance is already
 * being dispatched further up the stack.
 *
 * <p>This always indicates a listener that triggers another post of the same event type.
 * The usual cause is a listener that sends a packet, which for some packet types posts
 * an event again. Either make the nested post use a fresh instance, or - far more often
 * the right fix - queue the work and do it after the current dispatch returns.
 */
public final class ReentrantPostException extends EventBusViolation {
    private static final long serialVersionUID = 1L;

    private final transient Class<? extends Event> eventType;

    public ReentrantPostException(Class<? extends Event> eventType) {
        super("reentrant post of " + eventType.getName()
                + ": the singleton instance is already being dispatched. Use a fresh instance for the nested post,"
                + " or defer the work until after the current dispatch returns.");
        this.eventType = eventType;
    }

    public Class<? extends Event> getEventType() {
        return this.eventType;
    }
}
