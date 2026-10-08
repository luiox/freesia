package com.github.luiox.freesia.compute;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * A small fixed pool for CPU-bound work that may lag behind the caller's cadence.
 *
 * <p>This is the second of the two dispatch subsystems. The {@link com.github.luiox.freesia.EventManager
 * event bus} notifies listeners synchronously on the posting thread and never blocks; this
 * scheduler runs work that is too expensive for that path. The dividing line is whether the
 * work may touch live game (or UI) state: anything that can goes through the bus on the
 * owning thread, anything that cannot - pure functions over immutable input - belongs here.
 *
 * <p>Three properties are deliberate:
 *
 * <ol>
 *   <li><strong>Bounded threads.</strong> {@link #withDefaultThreads()} sizes the pool to
 *       {@code availableProcessors() - 1}, capped at 6. One pipeline at a time is the normal
 *       case; more threads only add context switches and GC contention.</li>
 *   <li><strong>Daemon threads.</strong> Forgetting to shut a compute pool down must not
 *       keep a process alive.</li>
 *   <li><strong>No internal queue to speak of.</strong> Tasks land on the executor's queue
 *       and the caller is expected to keep at most one in flight per pipeline (see
 *       {@link LatestTask}). The scheduler never dispatches events and the bus never submits
 *       tasks - the only thing the two subsystems share is an immutable result object.</li>
 * </ol>
 *
 * <p>Tasks that throw are reported to the {@link #setExceptionHandler exception handler} and
 * otherwise ignored; a failing computation must not take the worker down.
 */
public final class ComputeScheduler implements AutoCloseable {

    private static final int MAX_DEFAULT_THREADS = 6;

    private final int threadCount;
    private final ExecutorService pool;
    private final List<Consumer<Throwable>> exceptionHandlers = new CopyOnWriteArrayList<>();
    private volatile boolean shutdown;

    private ComputeScheduler(int threads) {
        final AtomicInteger index = new AtomicInteger();
        final ThreadGroup group = new ThreadGroup("freesia-compute");
        this.threadCount = threads;
        this.pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(group, runnable, "freesia-compute-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * A pool with {@code availableProcessors() - 1} workers, capped at 6 and floored at 1.
     */
    public static ComputeScheduler withDefaultThreads() {
        return new ComputeScheduler(defaultThreadCount());
    }

    /**
     * A pool with an explicit number of workers. Values below 1 become 1.
     */
    public static ComputeScheduler withThreads(int threads) {
        return new ComputeScheduler(Math.max(1, threads));
    }

    private static int defaultThreadCount() {
        return Math.max(1, Math.min(MAX_DEFAULT_THREADS, Runtime.getRuntime().availableProcessors() - 1));
    }

    /**
     * The number of workers this pool runs.
     */
    public int threadCount() {
        return this.threadCount;
    }

    /**
     * Runs {@code task} on a worker thread.
     *
     * @return {@code false} if the pool is already shut down, in which case the task was
     *         not run and never will be; a caller that must distinguish "no result yet"
     *         from "there will never be a result" checks this.
     */
    public boolean execute(Runnable task) {
        if (this.shutdown) {
            return false;
        }
        try {
            this.pool.execute(() -> {
                try {
                    task.run();
                } catch (Throwable t) {
                    this.report(t);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            // A concurrent shutdown raced the shutdown check; the task will never run.
            return false;
        }
    }

    /**
     * Submits a {@link LatestTask} and returns {@code false} if the pool is shut down. The
     * caller records {@code task.seq} as its last submitted sequence number only when this
     * returns {@code true} - otherwise the consumer would wait for a result that is never
     * coming.
     */
    public boolean execute(LatestTask<?> task) {
        return this.execute((Runnable) task);
    }

    /**
     * Installs a handler for exceptions thrown by tasks. The default is a warning on
     * {@link System.Logger}; handlers are consulted in installation order and every one of
     * them sees every failure.
     */
    public void addExceptionHandler(Consumer<Throwable> handler) {
        this.exceptionHandlers.add(handler);
    }

    /**
     * Stops accepting tasks and lets running tasks finish. Idempotent.
     */
    public void shutdown() {
        this.shutdown = true;
        this.pool.shutdown();
    }

    @Override
    public void close() {
        this.shutdown();
    }

    private void report(Throwable t) {
        System.getLogger(ComputeScheduler.class.getName()).log(System.Logger.Level.WARNING,
                "compute task failed", t);
        for (Consumer<Throwable> handler : this.exceptionHandlers) {
            handler.accept(t);
        }
    }
}
