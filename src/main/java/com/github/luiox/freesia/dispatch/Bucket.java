package com.github.luiox.freesia.dispatch;

import com.github.luiox.freesia.Event;
import com.github.luiox.freesia.EventBusViolation;
import com.github.luiox.freesia.EventListener;
import com.github.luiox.freesia.ICancellable;

import java.util.ArrayList;
import java.util.List;

/**
 * The listeners for one concrete event type, kept in dispatch order.
 *
 * <p>Three decisions live here, and each of them fixes something that measurably hurts on
 * a hot path.
 *
 * <p><strong>A flat snapshot array, not a list.</strong> Listeners are allocated at
 * unrelated times and land scattered across the heap, so walking a linked structure - or
 * allocating a {@code CopyOnWriteArrayList} iterator per dispatch - touches a cache line
 * per listener. Dispatch reads the array once through a volatile field: no allocation, no
 * iterator, no indirection.
 *
 * <p><strong>Copy on write.</strong> A listener may unregister itself, or another
 * listener, while the event is in flight. Mutating a live array would be a data race and
 * locking would put the cost of a rare operation onto the hottest path. Instead dispatch
 * reads an immutable snapshot and mutation swaps in a new one, so a dispatch already in
 * progress finishes on the state it started with.
 *
 * <p><strong>One loop per cancellation kind.</strong> Whether an event type can be
 * cancelled is known when the bucket is created, so the common case - a notification that
 * can never be cancelled - has no cancellation branch in its loop at all, which also keeps
 * the branch predictor at 100% there.
 */
public final class Bucket {

    private static final EventListener[] EMPTY = new EventListener[0];

    private final List<Entry> entries;
    private final boolean cancellable;
    private final DispatchContext context;

    private volatile EventListener[] snapshot = EMPTY;
    private long nextSequence;

    public Bucket(boolean cancellable, DispatchContext context) {
        this.entries = new ArrayList<>(4);
        this.cancellable = cancellable;
        this.context = context;
    }

    public boolean isCancellable() {
        return this.cancellable;
    }

    public synchronized int size() {
        return this.entries.size();
    }

    public synchronized void insert(EventListener listener) {
        long sequence = this.nextSequence++;
        int at = 0;
        while (at < this.entries.size() && runsBefore(this.entries.get(at), listener, sequence)) {
            at++;
        }
        this.entries.add(at, new Entry(listener, sequence));
        publish();
    }

    public synchronized boolean remove(EventListener listener) {
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.entries.get(i).listener == listener) {
                this.entries.remove(i);
                publish();
                return true;
            }
        }
        return false;
    }

    private void publish() {
        EventListener[] flat = new EventListener[this.entries.size()];
        for (int i = 0; i < flat.length; i++) {
            flat[i] = this.entries.get(i).listener;
        }
        this.snapshot = flat;
    }

    public void dispatch(Event event) {
        EventListener[] listeners = this.snapshot;
        int n = listeners.length;
        if (this.cancellable) {
            ICancellable cancellable = (ICancellable) event;
            for (int i = 0; i < n; i++) {
                EventListener listener = listeners[i];
                try {
                    listener.invoke(event);
                } catch (EventBusViolation violation) {
                    throw violation;
                } catch (Throwable t) {
                    this.context.errorHandler.onListenerError(listener, event, t);
                }
                if (cancellable.isCancelled()) {
                    return;
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                EventListener listener = listeners[i];
                try {
                    listener.invoke(event);
                } catch (EventBusViolation violation) {
                    throw violation;
                } catch (Throwable t) {
                    this.context.errorHandler.onListenerError(listener, event, t);
                }
            }
        }
    }

    /**
     * The total order: priority level, then the declared {@code order}, then registration
     * sequence. The last term makes the result independent of insertion order for equal
     * declarations, so dispatch order can be answered by reading the source.
     */
    private static boolean runsBefore(Entry left, EventListener right, long rightSequence) {
        if (left.listener.priority() != right.priority()) {
            return left.listener.priority().level() < right.priority().level();
        }
        if (left.listener.order() != right.order()) {
            return left.listener.order() < right.order();
        }
        return left.sequence < rightSequence;
    }

    /** Read-only view, for diagnostics and tests. */
    public synchronized List<EventListener> listeners() {
        List<EventListener> view = new ArrayList<>(this.entries.size());
        for (Entry entry : this.entries) {
            view.add(entry.listener);
        }
        return List.copyOf(view);
    }

    @Override
    public String toString() {
        return "Bucket[" + this.snapshot.length + (this.cancellable ? ", cancellable" : "") + ']';
    }

    /** Registration bookkeeping. Exists only at mutation time; dispatch never sees it. */
    private static final class Entry {
        private final EventListener listener;
        private final long sequence;

        Entry(EventListener listener, long sequence) {
            this.listener = listener;
            this.sequence = sequence;
        }
    }
}
