package com.github.luiox.freesia.compute;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatestTaskTest {

    /** Mirrors the pipeline the docs prescribe: submit, then consume only the latest. */
    @Test
    void staleResultsAreRejectedBySequenceNumber() throws Exception {
        ComputeScheduler scheduler = ComputeScheduler.withThreads(1);
        try {
            LatestTask<String> first = task("old-world");
            LatestTask<String> second = task("new-world");

            assertTrue(scheduler.execute(first));
            long lastSubmitted = second.seq; // what the submitter records for 'second'
            assertTrue(scheduler.execute(second));

            awaitDone(first);
            awaitDone(second);

            // first.seq != lastSubmitted, so its result is worthless even though it ran.
            assertNull(first.takeIfLatest(lastSubmitted), "a stale result leaked through");
            assertEquals("new-world", second.takeIfLatest(lastSubmitted));
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void unrunTaskConsumesAsNull() {
        LatestTask<String> task = task("late");
        assertNull(task.takeIfLatest(task.seq), "an un-run task produced a result");
        assertFalse(task.isDone());
    }

    @Test
    void failedComputeConsumesAsNull() throws Exception {
        ComputeScheduler scheduler = ComputeScheduler.withThreads(1);
        try {
            LatestTask<String> task = new LatestTask<>() {
                @Override
                protected String compute() {
                    throw new IllegalStateException("no plan this tick");
                }
            };
            CountDownLatch failed = new CountDownLatch(1);
            scheduler.addExceptionHandler(t -> failed.countDown());
            assertTrue(scheduler.execute(task));
            assertTrue(failed.await(5, TimeUnit.SECONDS));
            awaitDone(task);
            assertNull(task.takeIfLatest(task.seq), "a failed computation produced a result");
            assertFalse(task.isDone());
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void sequenceNumbersAreMonotonic() {
        long a = task("a").seq;
        long b = task("b").seq;
        long c = task("c").seq;
        assertTrue(a < b && b < c, "seq must increase: " + a + ", " + b + ", " + c);
    }

    @Test
    void resultPublishesAcrossThreads() throws Exception {
        // The volatile write on the worker must be visible to a consumer on another thread
        // without any additional synchronisation.
        ComputeScheduler scheduler = ComputeScheduler.withThreads(1);
        try {
            AtomicBoolean observed = new AtomicBoolean();
            LatestTask<String> task = task("visible");
            assertTrue(scheduler.execute(task));
            Thread consumer = new Thread(() -> {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < deadline) {
                    if (task.takeIfLatest(task.seq) != null) {
                        observed.set(true);
                        return;
                    }
                    Thread.onSpinWait();
                }
            });
            consumer.start();
            consumer.join(TimeUnit.SECONDS.toMillis(6));
            assertTrue(observed.get(), "consumer never saw the published result");
        } finally {
            scheduler.shutdown();
        }
    }

    private static LatestTask<String> task(String value) {
        return new LatestTask<>() {
            @Override
            protected String compute() {
                return value;
            }
        };
    }

    private static void awaitDone(LatestTask<?> task) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!task.isDone() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
    }
}
