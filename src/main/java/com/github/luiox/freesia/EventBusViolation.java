package com.github.luiox.freesia;

/**
 * A violation of the bus's own contract, as opposed to a failure inside a listener.
 *
 * <p>The distinction matters because the two are handled differently. A listener that
 * throws is isolated: the remaining listeners still run and the failure is reported. A
 * contract violation - posting a {@link SingletonEvent} reentrantly, or from a thread the
 * {@link EventThreadGuard} forbids - means the state is already suspect, so dispatch is
 * aborted and the exception propagates. Swallowing it would leave the module quietly doing
 * nothing, which is the failure mode this whole design is trying to eliminate.
 */
public abstract class EventBusViolation extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    protected EventBusViolation(String message) {
        super(message);
    }
}
