package com.github.luiox.freesia.handler;

public enum ListenerPriority {
    LOWEST(-114514),
    LOWER(-777),
    LOW(-666),
    NORMAL(0),
    HIGH(666),
    HIGHER(777),
    HIGHEST(114514);

    private final int priorityLevel;

    ListenerPriority(int priorityLevel) {
        this.priorityLevel = priorityLevel;
    }

    public int getPriorityLevel() {
        return this.priorityLevel;
    }
}