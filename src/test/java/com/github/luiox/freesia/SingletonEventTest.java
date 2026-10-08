package com.github.luiox.freesia;

import com.github.luiox.freesia.handler.Listener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingletonEventTest {

    private EventManager bus;

    @BeforeEach
    void setUp() {
        Rec.entries.clear();
        Rec.observed.set(0);
        this.bus = new EventManager().setErrorHandler((listener, event, error) -> {
        });
    }

    @Test
    void resetClearsStateBeforeReuse() {
        Tick tick = Tick.get();
        tick.count = 99;
        tick.label = "dirty";
        tick.setCancelled(true);

        Tick again = Tick.get();

        assertSame(tick, again, "the accessor hands back the same instance");
        assertEquals(0, again.count);
        assertNull(again.label);
        assertFalse(again.isCancelled());
    }

    /**
     * Resetting at acquisition rather than after dispatch is what lets a hook site read a
     * mutation back out of the event once post returns.
     */
    @Test
    void fieldsSurviveUntilTheHookReadsThemBack() {
        this.bus.register(new Observer());

        Tick tick = Tick.get();
        tick.count = 7;
        this.bus.post(tick);

        assertEquals(7, tick.count);
        assertEquals(7, Rec.observed.get(), "the listener saw the field the hook assigned");
    }

    /**
     * A nested post of the same singleton would have its fields clobbered by the inner
     * dispatch while the outer listener still expects them. Detected, not absorbed.
     */
    @Test
    void reentrantPostIsRejected() {
        Rec.bus = this.bus;
        this.bus.register(new ReentrantListener());

        assertThrows(ReentrantPostException.class, () -> this.bus.post(Tick.get()));
    }

    @Test
    void theExceptionNamesTheEventType() {
        Rec.bus = this.bus;
        this.bus.register(new ReentrantListener());

        ReentrantPostException thrown = assertThrows(ReentrantPostException.class, () -> this.bus.post(Tick.get()));

        assertSame(Tick.class, thrown.getEventType());
    }

    /**
     * A failed dispatch must not leave the event permanently marked as in-flight. The
     * listener exception itself is isolated by the bucket, so post returns normally.
     */
    @Test
    void theGuardIsReleasedEvenWhenAListenerThrows() {
        FailureListener.throwNow = true;
        this.bus.register(new FailureListener());

        this.bus.post(Tick.get());
        assertFalse(Tick.get().isDispatching(), "the flag must be cleared on the way out");

        FailureListener.throwNow = false;
        this.bus.post(Tick.get());
        assertEquals(2, Rec.observed.get(), "the next dispatch must run normally");
    }

    @Test
    void nestedPostOfADifferentEventTypeIsFine() {
        Rec.bus = this.bus;
        this.bus.register(new NestingListener());

        this.bus.post(Tick.get());

        assertEquals(List.of("tick-in", "pong", "tick-out"), Rec.entries);
    }

    @Test
    void ordinaryEventsCarryNoGuard() {
        OrdinaryListener listener = new OrdinaryListener();
        this.bus.register(listener);

        Plain plain = new Plain();
        this.bus.post(plain);
        this.bus.post(plain);

        assertEquals(2, listener.count, "an ordinary event may be posted as often as you like");
    }

    @Test
    void aThreadGuardCanForbidAThreadWithoutAffectingTheLegalOne() throws Exception {
        Thread owner = Thread.currentThread();
        CountDownLatch ran = new CountDownLatch(1);
        AtomicReference<Throwable> rejected = new AtomicReference<>();

        this.bus.setThreadGuard((eventType, poster) -> poster == owner);
        this.bus.register(new LatchListener(ran));

        this.bus.post(Tick.get());
        assertTrue(ran.await(1, TimeUnit.SECONDS), "the owning thread must be allowed");

        Thread intruder = new Thread(() -> {
            try {
                this.bus.post(Tick.get());
            } catch (Throwable t) {
                rejected.set(t);
            }
        }, "not-the-owner");
        intruder.start();
        intruder.join(5_000);

        assertSame(EventThreadViolationException.class, rejected.get().getClass(),
                "a post from the wrong thread must be rejected");
    }

    @Test
    void theViolationIsReportedBeforeAnyListenerRuns() throws Exception {
        this.bus.setThreadGuard((eventType, poster) -> false);
        this.bus.register(new NestingListener2());

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread intruder = new Thread(() -> {
            try {
                this.bus.post(Tick.get());
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        intruder.start();
        intruder.join(5_000);

        assertSame(EventThreadViolationException.class, thrown.get().getClass());
        assertTrue(Rec.entries.isEmpty(), "no listener may run against state the guard rejected");
    }

    @Test
    void acceptAllIsTheDefault() {
        assertTrue(EventThreadGuard.ACCEPT_ALL.isValidThread(Tick.class, Thread.currentThread()));
        assertTrue(EventThreadGuard.ACCEPT_ALL.isValidThread(Tick.class, new Thread()));
    }

    // --- fixtures -------------------------------------------------------------------------

    static final class Rec {
        static final List<String> entries = new ArrayList<>();
        static final AtomicInteger observed = new AtomicInteger();
        static EventManager bus;
    }

    public static class Tick extends SingletonCancellableEvent {
        private static final Tick INSTANCE = new Tick();

        public static Tick get() {
            INSTANCE.reset();
            return INSTANCE;
        }

        public int count;
        public String label;

        @Override
        public void reset() {
            this.count = 0;
            this.label = null;
            clearCancelled();
        }
    }

    public static class Pong extends Event {
    }

    public static class Plain extends CancellableEvent {
    }

    public static final class Observer {
        @Listener
        public void on(Tick event) {
            Rec.observed.set(event.count);
            Rec.entries.add("observed " + event.count);
        }
    }

    public static final class ReentrantListener {
        @Listener
        public void on(Tick event) {
            Rec.entries.add("outer entered");
            Rec.bus.post(Tick.get());
            Rec.entries.add("outer resumed");
        }
    }

    public static final class FailureListener {
        static boolean throwNow;

        @Listener
        public void on(Tick event) {
            Rec.observed.incrementAndGet();
            if (throwNow) {
                throw new IllegalStateException("module is broken");
            }
        }
    }

    public static final class NestingListener {
        @Listener
        public void onTick(Tick event) {
            Rec.entries.add("tick-in");
            Rec.bus.post(new Pong());
            Rec.entries.add("tick-out");
        }

        @Listener
        public void onPong(Pong event) {
            Rec.entries.add("pong");
        }
    }

    public static final class NestingListener2 {
        @Listener
        public void on(Tick event) {
            Rec.entries.add("must not run");
        }
    }

    public static final class OrdinaryListener {
        int count;

        @Listener
        public void on(Plain event) {
            this.count++;
        }
    }

    public static final class LatchListener {
        private final CountDownLatch latch;

        LatchListener(CountDownLatch latch) {
            this.latch = latch;
        }

        @Listener
        public void on(Tick event) {
            this.latch.countDown();
        }
    }
}
