package com.github.luiox.freesia;

import com.github.luiox.freesia.dispatch.Bucket;
import com.github.luiox.freesia.dispatch.DispatchContext;
import com.github.luiox.freesia.handler.ListenerScanner;
import com.github.luiox.freesia.handler.MethodListenerScanner;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default {@link EventBus}.
 *
 * <p>Safe to post from several threads and to register from several threads. Dispatch
 * itself takes no lock: it reads an immutable snapshot through a single volatile field.
 * Registration and unregistration do synchronise, but only against each other, and the
 * map mutation is a single atomic operation.
 */
public final class EventManager implements EventBus {

    private final Map<Class<? extends Event>, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<Object, List<EventListener>> registered = new ConcurrentHashMap<>();
    private final DispatchContext context = new DispatchContext();
    private final ListenerScanner scanner;

    private volatile EventThreadGuard threadGuard;

    public EventManager() {
        this(new MethodListenerScanner());
    }

    public EventManager(ListenerScanner scanner) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
    }

    @Override
    public <E extends Event> E post(E event) {
        Objects.requireNonNull(event, "event");
        Bucket bucket = this.buckets.get(event.getClass());
        if (bucket == null) {
            return event;
        }

        EventThreadGuard guard = this.threadGuard;
        if (guard != null && !guard.isValidThread(event.getClass(), Thread.currentThread())) {
            guard.onViolation(event.getClass(), Thread.currentThread());
        }

        if (event instanceof SingletonEvent singleton) {
            if (!singleton.tryBeginDispatch()) {
                throw new ReentrantPostException(event.getClass());
            }
            try {
                bucket.dispatch(event);
            } finally {
                singleton.endDispatch();
            }
        } else {
            bucket.dispatch(event);
        }
        return event;
    }

    @Override
    public <E extends Event> boolean hasListeners(Class<E> eventType) {
        Bucket bucket = this.buckets.get(eventType);
        return bucket != null && bucket.size() > 0;
    }

    @Override
    public boolean register(Object listenerContainer) {
        Objects.requireNonNull(listenerContainer, "listenerContainer");
        if (this.registered.containsKey(listenerContainer)) {
            return false;
        }
        List<EventListener> listeners = this.scanner.locate(listenerContainer);
        if (listeners.isEmpty()) {
            return false;
        }
        // Claim first, publish second: a concurrent register of the same instance must not
        // be able to slip a second copy of the same listeners into the buckets.
        if (this.registered.putIfAbsent(listenerContainer, listeners) != null) {
            return false;
        }
        for (EventListener listener : listeners) {
            bucketFor(listener.eventType()).insert(listener);
        }
        return true;
    }

    @Override
    public boolean unregister(Object listenerContainer) {
        List<EventListener> listeners = this.registered.remove(listenerContainer);
        if (listeners == null) {
            return false;
        }
        for (EventListener listener : listeners) {
            // computeIfPresent, not remove(key, value): a bucket emptied by this removal may
            // already have received a listener from a concurrent register, and dropping the
            // whole map entry would take that one down with it.
            this.buckets.computeIfPresent(listener.eventType(), (type, bucket) ->
                    bucket.remove(listener) && bucket.size() == 0 ? null : bucket);
        }
        return true;
    }

    private Bucket bucketFor(Class<? extends Event> eventType) {
        return this.buckets.computeIfAbsent(eventType,
                type -> new Bucket(ICancellable.class.isAssignableFrom(type), this.context));
    }

    /**
     * Replaces the handler that receives exceptions thrown by listeners. Reasonable to
     * call after construction; the swap reaches every existing bucket immediately.
     */
    public EventManager setErrorHandler(ListenerErrorHandler errorHandler) {
        this.context.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
        return this;
    }

    /**
     * Installs a check that an event is posted from an allowed thread. Passing
     * {@link EventThreadGuard#ACCEPT_ALL} removes the check.
     *
     * <p>The guard runs once per post, before any listener, so a violation is reported
     * without any listener having run against state it should not have touched.
     */
    public EventManager setThreadGuard(EventThreadGuard guard) {
        this.threadGuard = guard;
        return this;
    }

    public boolean isRegistered(Object listenerContainer) {
        return this.registered.containsKey(listenerContainer);
    }

    /** The listeners currently registered for one event type, in dispatch order. */
    public List<EventListener> listenersOf(Class<? extends Event> eventType) {
        Bucket bucket = this.buckets.get(eventType);
        return bucket == null ? List.of() : bucket.listeners();
    }
}
