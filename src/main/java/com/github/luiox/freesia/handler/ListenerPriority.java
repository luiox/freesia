package com.github.luiox.freesia.handler;

/**
 * Dispatch order, highest first.
 *
 * <p>The numeric values are spaced so that a level can be inserted between two existing
 * ones without renumbering, but the levels themselves are the intended vocabulary: a
 * listener declares which of five rungs it sits on, and anything finer-grained is
 * expressed with {@link Listener#order()}.
 *
 * <p>Arithmetic on these values - {@code HIGHEST + 1} and friends - is exactly the thing
 * this type exists to prevent. It works, it reads fine in isolation, and it makes the
 * resulting order unanswerable from the source once three modules do it.
 */
public enum ListenerPriority {
    FIRST(0),
    HIGH(1),
    NORMAL(2),
    LOW(3),
    LAST(4);

    private final int level;

    ListenerPriority(int level) {
        this.level = level;
    }

    public int level() {
        return this.level;
    }
}
