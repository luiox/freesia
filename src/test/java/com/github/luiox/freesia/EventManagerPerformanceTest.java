package com.github.luiox.freesia;

import com.github.luiox.freesia.handler.Listener;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

class EventManagerPerformanceTest {

    @Test
    void benchmarkDispatchThroughput() {
        Assumptions.assumeTrue(Boolean.getBoolean("freesia.benchmark"), "Enable with -Dfreesia.benchmark=true");

        EventManager eventManager = new EventManager();
        BenchmarkListener listener = new BenchmarkListener();
        eventManager.addListener(listener);

        int warmup = 200_000;
        int iterations = 2_000_000;

        for (int i = 0; i < warmup; i++) {
            eventManager.post(new BenchmarkEvent(i));
        }

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            eventManager.post(new BenchmarkEvent(i));
        }
        long end = System.nanoTime();

        double seconds = (end - start) / 1_000_000_000.0;
        double throughput = iterations / seconds;
        double avgNs = (double) (end - start) / iterations;

        System.out.printf("[freesia-benchmark] iterations=%d, seconds=%.4f, throughput=%.2f ops/s, avg=%.2f ns/op%n",
                iterations, seconds, throughput, avgNs);

        if (listener.sum.get() == Long.MIN_VALUE) {
            throw new AssertionError("unreachable");
        }
    }

    public static class BenchmarkEvent {
        final int value;

        BenchmarkEvent(int value) {
            this.value = value;
        }
    }

    public static class BenchmarkListener {
        final AtomicLong sum = new AtomicLong();

        @Listener
        public void onEvent(BenchmarkEvent event) {
            sum.addAndGet(event.value);
        }
    }
}
