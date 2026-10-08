package com.github.luiox.freesia.compute;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A single-slot computation whose result must be ignored if a newer one was submitted.
 *
 * <p>The shape is the classic snapshot &rarr; compute &rarr; consume pipeline: the posting
 * thread builds an immutable input, hands this task to a {@link ComputeScheduler}, and later
 * the same thread consumes the previous tick's result and sends it. The computation itself is
 * a pure function of the input; it runs on a worker and may take as long as it likes.
 *
 * <p><strong>Discarding stale results is mandatory, not optional.</strong> A result computed
 * from a two-tick-old snapshot describes a world that no longer exists; applying it produces
 * actions against state that is already gone, and because the next result overwrites the slot
 * the bug is effectively undebuggable. So the acceptance rule is baked into the type: record
 * {@link #seq} when you submit, and consume only via {@link #takeIfLatest}, which yields
 * {@code null} for anything but the most recently submitted task.
 *
 * <p>{@code T} must be immutable, or at least never written after {@link #compute} returns -
 * the worker publishes it through a plain volatile write and the consumer reads it from
 * another thread. A mutable {@code T} shared between the two is a data race the type system
 * cannot catch.
 *
 * <p>The intended cadence keeps exactly one task in flight: submit once per tick, overwrite,
 * never batch. There is no back-pressure because there is nothing to back-pressure - the
 * newest input always wins and everything older is already worthless.
 *
 * @param <T> the result type; treat it as immutable
 */
public abstract class LatestTask<T> implements Runnable {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    /**
     * Monotonically increasing across every task in the JVM, assigned at construction.
     * Comparisons are only ever made within one pipeline - submitter records it, consumer
     * checks it - and a global counter keeps those comparisons correct.
     */
    public final long seq = SEQUENCE.incrementAndGet();

    private volatile T result;
    private volatile boolean done;

    /**
     * The pure computation. Called once, on a worker thread. Throwing is safe: the task
     * simply never completes, and the scheduler's exception handler is notified.
     */
    protected abstract T compute() throws Exception;

    @Override
    public final void run() {
        try {
            T value = this.compute();
            this.result = value;
            this.done = true;
        } catch (Throwable t) {
            // Rethrow: the ComputeScheduler's task wrapper owns failure reporting, and a
            // failed computation must leave `done` false so takeIfLatest yields null.
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new IllegalStateException("compute() threw a checked exception", t);
        }
    }

    /**
     * @return whether {@link #compute} ran to completion.
     */
    public final boolean isDone() {
        return this.done;
    }

    /**
     * The result, whatever its age. Exists for diagnostics and tests; production consumers
     * should use {@link #takeIfLatest} so staleness cannot slip through.
     */
    public final T resultUnsafe() {
        return this.done ? this.result : null;
    }

    /**
     * The result, but only if this is still the newest task the caller submitted.
     *
     * <p>Pass the value of {@link #seq} recorded at submit time. A returned {@code null}
     * means one of: not finished yet, the computation threw, or a newer task was submitted
     * in the meantime. All three mean the same thing operationally - <em>use nothing, wait
     * for the next tick</em>.
     *
     * @param lastSubmittedSeq the {@link #seq} of the most recent task actually accepted by
     *                         the scheduler, as recorded by the submitter
     */
    public final T takeIfLatest(long lastSubmittedSeq) {
        if (this.seq != lastSubmittedSeq || !this.done) {
            return null;
        }
        return this.result;
    }
}
