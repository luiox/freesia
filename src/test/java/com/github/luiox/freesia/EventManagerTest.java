package com.github.luiox.freesia;

import com.github.luiox.freesia.filter.EventFilter;
import com.github.luiox.freesia.handler.EventHandler;
import com.github.luiox.freesia.handler.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventManagerTest {

    private final EventManager eventManager = new EventManager();

    @AfterEach
    void tearDown() {
        ToggleFilter.allow = true;
        StaticListener.called.set(false);
    }

    @Test
    void shouldManageListenerRegistrationLifecycle() {
        BasicListener listener = new BasicListener();

        assertTrue(eventManager.register(listener));
        assertTrue(eventManager.isRegistered(listener));
        assertFalse(eventManager.register(listener));

        eventManager.post(new BasicEvent());
        assertEquals(1, listener.counter.get());

        assertTrue(eventManager.unregister(listener));
        assertFalse(eventManager.isRegistered(listener));
        assertFalse(eventManager.unregister(listener));

        eventManager.post(new BasicEvent());
        assertEquals(1, listener.counter.get());
    }

    @Test
    void shouldDispatchByDescendingPriority() {
        OrderListener listener = new OrderListener();
        eventManager.register(listener);

        eventManager.post(new OrderEvent());

        assertEquals(Arrays.asList("high", "normal", "low"), listener.order);
    }

    @Test
    void shouldStopDispatchWhenEventCancelled() {
        CancelListener listener = new CancelListener();
        eventManager.register(listener);

        CancelEvent event = new CancelEvent();
        eventManager.post(event);

        assertTrue(event.isCancelled());
        assertEquals(1, listener.firstCounter.get());
        assertEquals(0, listener.secondCounter.get());
    }

    @Test
    void shouldApplyListenerFilters() {
        FilteredListener listener = new FilteredListener();
        eventManager.register(listener);

        ToggleFilter.allow = false;
        eventManager.post(new FilteredEvent());
        assertEquals(0, listener.counter.get());

        ToggleFilter.allow = true;
        eventManager.post(new FilteredEvent());
        assertEquals(1, listener.counter.get());
    }

    @Test
    void shouldDispatchAsyncListener() throws InterruptedException {
        AsyncListener listener = new AsyncListener();
        eventManager.register(listener);

        long callerThreadId = Thread.currentThread().getId();
        eventManager.post(new AsyncEvent(callerThreadId));

        assertTrue(listener.latch.await(2, TimeUnit.SECONDS));
        assertTrue(listener.executed.get());
        assertTrue(listener.executedOnDifferentThread.get());
    }

    @Test
    void shouldSupportStaticListenerMethods() {
        eventManager.register(new StaticListener());

        eventManager.post(new StaticEvent());

        assertTrue(StaticListener.called.get());
    }

    public static class BasicEvent {}

    public static class BasicListener {
        private final AtomicInteger counter = new AtomicInteger();

        @Listener
        public void onBasic(BasicEvent event) {
            counter.incrementAndGet();
        }
    }

    public static class OrderEvent {}

    public static class OrderListener {
        private final List<String> order = new ArrayList<>();

        @Listener(priority = 100)
        public void onHigh(OrderEvent event) {
            order.add("high");
        }

        @Listener(priority = 0)
        public void onNormal(OrderEvent event) {
            order.add("normal");
        }

        @Listener(priority = -100)
        public void onLow(OrderEvent event) {
            order.add("low");
        }
    }

    public static class CancelEvent implements Cancellable {
        private volatile boolean cancelled;

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        void cancel() {
            this.cancelled = true;
        }
    }

    public static class CancelListener {
        private final AtomicInteger firstCounter = new AtomicInteger();
        private final AtomicInteger secondCounter = new AtomicInteger();

        @Listener(priority = 50)
        public void first(CancelEvent event) {
            firstCounter.incrementAndGet();
            event.cancel();
        }

        @Listener(priority = -50)
        public void second(CancelEvent event) {
            secondCounter.incrementAndGet();
        }
    }

    public static class FilteredEvent {}

    public static class ToggleFilter implements EventFilter<FilteredEvent> {
        static volatile boolean allow = true;

        @Override
        public boolean test(EventHandler paramEventHandler, FilteredEvent paramE) {
            return allow;
        }
    }

    public static class FilteredListener {
        private final AtomicInteger counter = new AtomicInteger();

        @Listener(filters = {ToggleFilter.class})
        public void onFiltered(FilteredEvent event) {
            counter.incrementAndGet();
        }
    }

    public static class AsyncEvent {
        private final long callerThreadId;

        AsyncEvent(long callerThreadId) {
            this.callerThreadId = callerThreadId;
        }
    }

    public static class AsyncListener {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicBoolean executed = new AtomicBoolean(false);
        private final AtomicBoolean executedOnDifferentThread = new AtomicBoolean(false);

        @Listener(async = true)
        public void onAsync(AsyncEvent event) {
            executed.set(true);
            executedOnDifferentThread.set(Thread.currentThread().getId() != event.callerThreadId);
            latch.countDown();
        }
    }

    public static class StaticEvent {}

    public static class StaticListener {
        private static final AtomicBoolean called = new AtomicBoolean(false);

        @Listener
        public static void onStatic(StaticEvent event) {
            called.set(true);
        }
    }
}
