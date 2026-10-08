package com.github.luiox.freesia.dispatch;

import com.github.luiox.freesia.ListenerErrorHandler;

/**
 * The mutable bits every {@link Bucket} shares.
 *
 * <p>Keeping them in one box rather than copying them into each bucket means swapping the
 * error handler takes effect everywhere at once, with no window in which some buckets hold
 * the old one. Only the error path reads through this, so it costs nothing when listeners
 * behave.
 */
public final class DispatchContext {

    public volatile ListenerErrorHandler errorHandler = ListenerErrorHandler.logging();
}
