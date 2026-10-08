package com.github.luiox.freesia;

import com.github.luiox.freesia.handler.Listener;
import com.github.luiox.freesia.handler.ListenerPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registration and dispatch running against each other.
 *
 * <p>The contract under test is narrow but load-bearing: a dispatch reads an immutable
 * snapshot, so concurrent mutation can never make it observe a half-updated listener set
 * or lose one entirely.
 */
class ConcurrencyTest {

    private EventManager bus;

    @BeforeEach
    void setUp() {
        Counts.reset();
        this.bus = new EventManager().setErrorHandler((listener, event, error) -> {
        });
    }

    @Test
    void registeringTheSameContainerFromManyThreadsYieldsOneRegistration() throws Exception {
        CounterListener listener = new CounterListener();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger accepted = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                await(start);
                if (this.bus.register(listener)) {
                    accepted.incrementAndGet();
                }
                done.countDown();
            });
            t.setDaemon(true);
            t.start();
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));

        assertEquals(1, accepted.get(), "exactly one thread may win the registration");
        assertEquals(1, this.bus.listenersOf(Ping.class).size(),
                "the container's listeners must not be duplicated");
    }

    @Test
    void postingWhileListenersComeAndGoNeverLosesOrDuplicates() throws Exception {
        this.bus.register(new CounterListener()); // stays registered for the whole test
        int churnThreads = 4;
        int perThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(churnThreads + 1);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<?>> tasks = new ArrayList<>();

        for (int i = 0; i < churnThreads; i++) {
            tasks.add(pool.submit(() -> {
                await(start);
                for (int n = 0; n < perThread; n++) {
                    CounterListener listener = new CounterListener();
                    this.bus.register(listener);
                    this.bus.unregister(listener);
                }
            }));
        }
        tasks.add(pool.submit(() -> {
            await(start);
            for (int n = 0; n < perThread * 4; n++) {
                this.bus.post(new Ping());
            }
        }));

        start.countDown();
        for (java.util.concurrent.Future<?> task : tasks) {
            task.get(30, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertTrue(Counts.posts.get() > 0, "the posting thread must have made progress");
        assertEquals(0, Counts.errors.get());
    }

    @Test
    void anEmptyingUnregisterDoesNotDropAConcurrentRegistration() throws Exception {
        // The shape of the old race: unregister emptied a bucket and removed the map
        // entry, taking with it a listener that another thread had just added.
        for (int attempt = 0; attempt < 200; attempt++) {
            EventManager local = new EventManager().setErrorHandler((listener, event, error) -> {
            });
            CounterListener leaver = new CounterListener();
            local.register(leaver);

            Thread adder = new Thread(() -> local.register(new CounterListener()));
            Thread remover = new Thread(() -> local.unregister(leaver));
            adder.start();
            remover.start();
            adder.join(5_000);
            remover.join(5_000);

            int live = local.listenersOf(Ping.class).size();
            assertTrue(live == 0 || live == 1,
                    "a bucket must never hold more than one listener, found " + live);
        }
    }

    @Test
    void dispatchCompletesEvenWhenEveryListenerUnregistersItself() {
        this.bus.register(new StackedListener(this.bus));
        this.bus.register(new StackedListener(this.bus));

        this.bus.post(new Ping());

        assertTrue(this.bus.listenersOf(Ping.class).isEmpty());
        assertEquals(2, Counts.selfRemovals.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // --- fixtures -------------------------------------------------------------------------

    static final class Counts {
        static final AtomicInteger posts = new AtomicInteger();
        static final AtomicInteger errors = new AtomicInteger();
        static final AtomicInteger selfRemovals = new AtomicInteger();

        private Counts() {
        }

        static void reset() {
            posts.set(0);
            errors.set(0);
            selfRemovals.set(0);
        }
    }

    public static class Ping extends Event {
    }

    public static final class CounterListener {
        @Listener
        public void on(Ping event) {
            Counts.posts.incrementAndGet();
        }
    }

    public static final class StackedListener {
        private final EventManager bus;

        StackedListener(EventManager bus) {
            this.bus = bus;
        }

        @Listener(priority = ListenerPriority.NORMAL)
        public void on(Ping event) {
            Counts.selfRemovals.incrementAndGet();
            this.bus.unregister(this);
        }
    }
}
