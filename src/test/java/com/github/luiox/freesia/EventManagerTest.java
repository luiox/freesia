package com.github.luiox.freesia;

import com.github.luiox.freesia.handler.Listener;
import com.github.luiox.freesia.handler.ListenerPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventManagerTest {

    private EventManager bus;
    private ThrowingCollector errors;

    @BeforeEach
    void setUp() {
        Log.reset();
        StaticListener.called = false;
        PrivateListener.called = false;
        this.bus = new EventManager();
        this.errors = new ThrowingCollector();
        this.bus.setErrorHandler(this.errors);
    }

    @Test
    void registrationIsIdempotentPerContainer() {
        CounterListener listener = new CounterListener();

        assertTrue(this.bus.register(listener));
        assertTrue(this.bus.isRegistered(listener));
        assertFalse(this.bus.register(listener), "a second register must be rejected");

        this.bus.post(new Ping());
        assertEquals(1, listener.count.get());

        assertTrue(this.bus.unregister(listener));
        assertFalse(this.bus.isRegistered(listener));
        assertFalse(this.bus.unregister(listener));

        this.bus.post(new Ping());
        assertEquals(1, listener.count.get(), "an unregistered listener must stop receiving");
    }

    /**
     * Regression: listeners used to be collected in a TreeSet ordered by priority alone, so
     * two listeners sharing a priority compared equal and the second was dropped silently.
     */
    @Test
    void everyListenerAtTheSamePriorityRuns() {
        SamePriorityListener listener = new SamePriorityListener();

        assertTrue(this.bus.register(listener));
        this.bus.post(new Ping());

        assertEquals(List.of("first", "second", "third"), listener.calls,
                "a same-priority listener must not be dropped for looking like a duplicate");
    }

    @Test
    void dispatchFollowsPriorityThenDeclaredOrder() {
        OrderedListener listener = new OrderedListener();
        this.bus.register(listener);

        this.bus.post(new Ping());

        assertEquals(List.of("first", "early", "normal", "late", "last"), listener.calls);
    }

    @Test
    void cancellationStopsLowerPriorities() {
        this.bus.register(new CancellingListener());

        Cancel event = new Cancel();
        this.bus.post(event);

        assertTrue(event.isCancelled());
        assertEquals(List.of("cancels"), Log.entries);
    }

    /** The listener that cancels did run; only what follows it is skipped. */
    @Test
    void theCancellingListenerItselfStillRuns() {
        this.bus.register(new RecordingListener("before", false));
        this.bus.register(new RecordingListener("cancels", true));
        this.bus.register(new RecordingListener("after", false));

        this.bus.post(new Cancel());

        assertEquals(List.of("before", "cancels"), Log.entries);
    }

    /**
     * One broken module must not disable the others for the session, and the failure must
     * still be reported. Previously the exception was printed and swallowed, so the feature
     * was simply off with no way to tell which listener was at fault.
     */
    @Test
    void aThrowingListenerIsIsolatedAndReported() {
        this.bus.register(new RecordingListener("before", false));
        this.bus.register(new ThrowingListener());
        this.bus.register(new RecordingListener("after", false));

        this.bus.post(new Ping());

        assertEquals(List.of("before", "after"), Log.entries, "listeners after the failure must still run");
        assertEquals(1, this.errors.count, "the failure must reach the error handler");
        assertSame(ThrowingListener.class, this.errors.listener.owner().getClass(),
                "the report must identify the owning module");
    }

    /**
     * Unregistering mid-dispatch must not disturb the dispatch in progress. A feature that
     * switches itself off once its work is done is a real pattern, not a contrived one.
     */
    @Test
    void unregisterDuringDispatchDoesNotDisturbTheCurrentDispatch() {
        SelfRemovingListener selfRemover = new SelfRemovingListener(this.bus);
        this.bus.register(selfRemover);
        this.bus.register(new RecordingListener("sibling", false));

        this.bus.post(new Ping());
        assertEquals(List.of("removes itself", "sibling"), Log.entries);

        Log.reset();
        this.bus.post(new Ping());
        assertEquals(List.of("sibling"), Log.entries, "it must stay removed");
    }

    @Test
    void hasListenersAnswersWithoutDispatching() {
        assertFalse(this.bus.hasListeners(Ping.class));
        this.bus.register(new CounterListener());
        assertTrue(this.bus.hasListeners(Ping.class));
        assertFalse(this.bus.hasListeners(Pong.class));
    }

    @Test
    void postReturnsTheSameInstanceSoFieldsCanBeReadBack() {
        this.bus.register(new MutatingListener());

        Ping event = new Ping();
        Ping returned = this.bus.post(event);

        assertSame(event, returned);
        assertEquals("rewritten", returned.value, "a hook site must be able to read mutations back");
    }

    /** Dispatch is by exact runtime class; a listener for a supertype does not fire. */
    @Test
    void dispatchIsByExactRuntimeClass() {
        ExactClassListener listener = new ExactClassListener();
        this.bus.register(listener);

        this.bus.post(new SubPing());
        assertEquals(0, listener.count.get(), "a Ping listener must not receive a SubPing");

        this.bus.post(new Ping());
        assertEquals(1, listener.count.get());
    }

    @Test
    void staticListenerMethodsAreSupported() {
        assertTrue(this.bus.register(new StaticListener()));
        this.bus.post(new StaticPing());
        assertTrue(StaticListener.called);
    }

    /**
     * Regression: the call body used to be built from {@code MethodHandles.lookup()} inside
     * the compiler class, which has no private access to a listener declared elsewhere, so
     * a private listener method failed to register regardless of setAccessible.
     */
    @Test
    void privateListenerMethodsInOtherClassesAreInvokable() {
        assertTrue(this.bus.register(new PrivateListener()));
        this.bus.post(new Ping());
        assertTrue(PrivateListener.called, "a private @Listener method must be compiled and called");
    }

    @Test
    void listenerContainersWithNoListenersAreNotRegistered() {
        assertFalse(this.bus.register(new NoListeners()));
    }

    // --- fixtures -------------------------------------------------------------------------

    static final class Log {
        static final List<String> entries = new ArrayList<>();

        private Log() {
        }

        static void reset() {
            entries.clear();
        }
    }

    static final class ThrowingCollector implements ListenerErrorHandler {
        int count;
        EventListener listener;

        @Override
        public void onListenerError(EventListener listener, Event event, Throwable error) {
            this.count++;
            this.listener = listener;
        }
    }

    public static class Ping extends Event {
        String value = "original";
    }

    public static class SubPing extends Ping {
    }

    public static class Pong extends Event {
    }

    public static class StaticPing extends Event {
    }

    public static class Cancel extends CancellableEvent {
    }

    public static final class CounterListener {
        final AtomicInteger count = new AtomicInteger();

        @Listener
        public void onPing(Ping event) {
            this.count.incrementAndGet();
        }
    }

    public static final class SamePriorityListener {
        final List<String> calls = new ArrayList<>();

        @Listener
        public void a(Ping event) {
            this.calls.add("first");
        }

        @Listener
        public void b(Ping event) {
            this.calls.add("second");
        }

        @Listener
        public void c(Ping event) {
            this.calls.add("third");
        }
    }

    public static final class OrderedListener {
        final List<String> calls = new ArrayList<>();

        @Listener(priority = ListenerPriority.NORMAL, order = -10)
        public void early(Ping event) {
            this.calls.add("early");
        }

        @Listener
        public void plain(Ping event) {
            this.calls.add("normal");
        }

        @Listener(priority = ListenerPriority.NORMAL, order = 10)
        public void late(Ping event) {
            this.calls.add("late");
        }

        @Listener(priority = ListenerPriority.FIRST)
        public void first(Ping event) {
            this.calls.add("first");
        }

        @Listener(priority = ListenerPriority.LAST)
        public void last(Ping event) {
            this.calls.add("last");
        }
    }

    public static final class CancellingListener {
        @Listener(priority = ListenerPriority.NORMAL)
        public void cancel(Cancel event) {
            Log.entries.add("cancels");
            event.setCancelled(true);
        }

        @Listener(priority = ListenerPriority.LAST)
        public void neverRuns(Cancel event) {
            Log.entries.add("must not run");
        }
    }

    public static final class RecordingListener {
        private final String name;
        private final boolean cancel;

        RecordingListener(String name, boolean cancel) {
            this.name = name;
            this.cancel = cancel;
        }

        @Listener
        public void onCancel(Cancel event) {
            record();
            if (this.cancel) {
                event.setCancelled(true);
            }
        }

        @Listener
        public void onPing(Ping event) {
            record();
        }

        private void record() {
            Log.entries.add(this.name);
        }
    }

    public static final class ThrowingListener {
        @Listener
        public void on(Ping event) {
            throw new IllegalStateException("this module is broken");
        }
    }

    public static final class SelfRemovingListener {
        private final EventManager bus;

        SelfRemovingListener(EventManager bus) {
            this.bus = bus;
        }

        @Listener(priority = ListenerPriority.FIRST)
        public void on(Ping event) {
            Log.entries.add("removes itself");
            this.bus.unregister(this);
        }
    }

    public static final class MutatingListener {
        @Listener
        public void on(Ping event) {
            event.value = "rewritten";
        }
    }

    public static final class ExactClassListener {
        final AtomicInteger count = new AtomicInteger();

        @Listener
        public void on(Ping event) {
            this.count.incrementAndGet();
        }
    }

    public static final class NoListeners {
    }

    public static final class HolderForStaticListener {
    }

    public static final class StaticListener {
        static boolean called;

        @Listener
        public static void on(StaticPing event) {
            called = true;
        }
    }

    public static final class PrivateListener {
        static boolean called;

        @Listener
        private void on(Ping event) {
            called = true;
        }
    }
}
