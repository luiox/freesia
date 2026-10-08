package com.github.luiox.freesia.handler;

/**
 * The compiled call body of a listener method.
 *
 * <p>The parameter is {@code Object} rather than the event type on purpose: this is the
 * erasure-erased signature handed to {@code LambdaMetafactory}, so that one generated
 * class serves listeners of every event type. The cast to the concrete type happens
 * inside the generated lambda, once, at a monomorphic call site.
 */
@FunctionalInterface
public interface EventAction {
    void invoke(Object event) throws Throwable;
}
