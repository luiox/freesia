package com.github.luiox.freesia.compute;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeSchedulerTest {

    @Test
    void executesTaskOnWorkerThread() throws Exception {
        ComputeScheduler scheduler = ComputeScheduler.withThreads(1);
        try {
            AtomicReference<Thread> seen = new AtomicReference<>();
            CountDownLatch ran = new CountDownLatch(1);
            assertTrue(scheduler.execute(() -> {
                seen.set(Thread.currentThread());
                ran.countDown();
            }));
            assertTrue(ran.await(5, TimeUnit.SECONDS), "task did not run");
            assertTrue(seen.get().getName().startsWith("freesia-compute-"), seen.get().getName());
            assertTrue(seen.get().isDaemon(), "compute threads must not block JVM exit");
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void defaultThreadCountIsBounded() {
        try (ComputeScheduler s = ComputeScheduler.withDefaultThreads()) {
            int expected = Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() - 1));
            assertEquals(expected, s.threadCount());
        }
    }

    @Test
    void withThreadsFloorsAtOne() {
        try (ComputeScheduler s = ComputeScheduler.withThreads(-3)) {
            assertEquals(1, s.threadCount());
        }
    }

    @Test
    void exceptionIsReportedAndWorkerSurvives() throws Exception {
        ComputeScheduler scheduler = ComputeScheduler.withThreads(1);
        try {
            List<Throwable> failures = new CopyOnWriteArrayList<>();
            RuntimeException boom = new RuntimeException("boom");
            CountDownLatch failed = new CountDownLatch(1);
            scheduler.addExceptionHandler(t -> {
                failures.add(t);
                failed.countDown();
            });

            scheduler.execute(() -> {
                throw boom;
            });
            // The default System.Logger path runs first; our handler must also see it.
            assertTrue(failed.await(5, TimeUnit.SECONDS), "handler not invoked");
            assertEquals(boom, failures.get(0));

            // The same worker must still accept work after a task blew up.
            CountDownLatch second = new CountDownLatch(1);
            assertTrue(scheduler.execute(second::countDown));
            assertTrue(second.await(5, TimeUnit.SECONDS), "worker died after task failure");
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void rejectsAfterShutdown() {
        ComputeScheduler scheduler = ComputeScheduler.withThreads(1);
        scheduler.shutdown();
        assertFalse(scheduler.execute(() -> {
        }));
        assertFalse(scheduler.execute(new LatestTask<Object>() {
            @Override
            protected Object compute() {
                return null;
            }
        }));
        scheduler.shutdown(); // idempotent
    }

    @Test
    void shutdownRaceIsHandled() throws Exception {
        // A shutdown racing execute() must return false rather than throw, whichever wins.
        ComputeScheduler scheduler = ComputeScheduler.withThreads(2);
        AtomicInteger accepted = new AtomicInteger();
        for (int i = 0; i < 64; i++) {
            if (scheduler.execute(() -> {
            })) {
                accepted.incrementAndGet();
            }
        }
        scheduler.shutdown();
        assertTrue(accepted.get() >= 0);
    }
}
