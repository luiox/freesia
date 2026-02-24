package com.github.luiox.freesia;


public class Event implements ICancellable {
    private volatile boolean cancelled;

    public Event() {
        this.cancelled = false;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
