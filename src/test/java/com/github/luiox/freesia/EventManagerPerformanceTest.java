package com.github.luiox.freesia;

import com.github.luiox.freesia.handler.Listener;
import com.github.luiox.freesia.handler.ListenerPriority;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Microbenchmarks. Skipped unless {@code -Dfreesia.benchmark=true}.
 *
 * <p>The earlier version of this file accumulated into an {@code AtomicLong} inside the
 * measured loop, so it was timing compare-and-swap rather than dispatch - roughly twenty
 * times the cost of the thing under test, which is enough to hide every effect the
 * benchmark existed to expose. The accumulator is now a plain field.
 *
 * <p>Allocation per post is measured directly through {@code ThreadMXBean}, because for
 * this library that is the number that actually matters: it is the input to GC, and GC is
 * what makes a client stutter.
 */
class EventManagerPerformanceTest {

    private static final int WARMUP = 200_000;
    private static final int ITERATIONS = 2_000_000;

    private static final long ALLOCATOR_ID;

    static {
        com.sun.management.ThreadMXBean bean = allocationBean();
        ALLOCATOR_ID = bean == null ? -1 : bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    @Test
    void measureDispatchThroughput() {
        Assumptions.assumeTrue(Boolean.getBoolean("freesia.benchmark"), "enable with -Dfreesia.benchmark=true");

        EventManager bus = new EventManager();
        Sink sink = new Sink();
        bus.register(sink);

        run(bus, new BenchEvent(), WARMUP, ITERATIONS, "fresh event, one listener", sink);
    }

    @Test
    void measureSingletonEventThroughput() {
        Assumptions.assumeTrue(Boolean.getBoolean("freesia.benchmark"), "enable with -Dfreesia.benchmark=true");

        EventManager bus = new EventManager();
        Sink sink = new Sink();
        bus.register(sink);

        run(bus, BenchSingleton.get(), WARMUP, ITERATIONS, "singleton event, one listener", sink);
    }

    @Test
    void measureWideDispatch() {
        Assumptions.assumeTrue(Boolean.getBoolean("freesia.benchmark"), "enable with -Dfreesia.benchmark=true");

        EventManager bus = new EventManager();
        int width = 64;
        for (int i = 0; i < width; i++) {
            bus.register(new Sink());
        }

        // A hundred listeners is the realistic upper bound for one event type in a real
        // client. This shows how much of the cost is the megamorphic call rather than the
        // lookup, which is the thing the design notes say to stop worrying about.
        run(bus, new BenchEvent(), WARMUP, ITERATIONS / 4, width + " listeners", null);
    }

    @Test
    void measureAllocationPerPost() {
        Assumptions.assumeTrue(Boolean.getBoolean("freesia.benchmark"), "enable with -Dfreesia.benchmark=true");

        com.sun.management.ThreadMXBean bean = allocationBean();
        Assumptions.assumeTrue(bean != null, "allocation measurement needs a HotSpot ThreadMXBean");

        EventManager bus = new EventManager();
        bus.register(new Sink());

        for (int i = 0; i < WARMUP; i++) {
            bus.post(new BenchEvent());
        }
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().getId());

        int n = 100_000;
        for (int i = 0; i < n; i++) {
            bus.post(new BenchEvent());
        }
        long after = bean.getThreadAllocatedBytes(Thread.currentThread().getId());

        report("fresh event: %.1f bytes/post", (after - before) / (double) n);

        BenchSingleton singleton = BenchSingleton.get();
        for (int i = 0; i < WARMUP; i++) {
            bus.post(singleton);
        }
        before = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
        for (int i = 0; i < n; i++) {
            bus.post(BenchSingleton.get());
        }
        after = bean.getThreadAllocatedBytes(Thread.currentThread().getId());

        report("singleton event: %.1f bytes/post", (after - before) / (double) n);
    }

    @Test
    void measureRegistrationCost() {
        Assumptions.assumeTrue(Boolean.getBoolean("freesia.benchmark"), "enable with -Dfreesia.benchmark=true");

        int n = 20_000;
        long start = System.nanoTime();
        List<EventManager> buses = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            EventManager bus = new EventManager();
            bus.register(new Sink());
            buses.add(bus);
        }
        long end = System.nanoTime();

        report("register: %.0f ns/op", (end - start) / (double) n);
        report("distinct buses built: %d", buses.size());
    }

    private static void run(EventManager bus, Event prototype, int warmup, int iterations,
                            String label, Sink sink) {
        for (int i = 0; i < warmup; i++) {
            bus.post(prototype);
        }

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            bus.post(prototype);
        }
        long end = System.nanoTime();

        double seconds = (end - start) / 1_000_000_000.0;
        report("%s: %.2f ns/op, %.0f ops/s", label, (end - start) / (double) iterations, iterations / seconds);
        if (sink != null) {
            report("%s: sink=%d (must be non-zero, otherwise the JIT elided the work)",
                    label, sink.count);
        }
    }

    private static void report(String format, Object... args) {
        System.out.printf("[freesia] " + format + "%n", args);
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean) {
            return (com.sun.management.ThreadMXBean) bean;
        }
        return null;
    }

    // --- fixtures -------------------------------------------------------------------------

    public static class BenchEvent extends Event {
    }

    public static final class BenchSingleton extends SingletonCancellableEvent {
        private static final BenchSingleton INSTANCE = new BenchSingleton();

        public static BenchSingleton get() {
            INSTANCE.reset();
            return INSTANCE;
        }

        public int value;

        @Override
        public void reset() {
            this.value = 0;
            clearCancelled();
        }
    }

    /** A plain field, so the measurement is dispatch and not atomics. */
    public static final class Sink {
        long count;

        @Listener(priority = ListenerPriority.NORMAL)
        public void on(BenchEvent event) {
            this.count++;
        }

        @Listener(priority = ListenerPriority.NORMAL)
        public void onSingleton(BenchSingleton event) {
            this.count++;
        }
    }
}
