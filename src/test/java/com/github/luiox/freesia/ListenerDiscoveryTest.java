package com.github.luiox.freesia;

import com.github.luiox.freesia.handler.Listener;
import com.github.luiox.freesia.handler.ListenerPriority;
import com.github.luiox.freesia.handler.MethodListenerScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Listener discovery. Every test here exists because getting it wrong produces a feature
 * that silently never runs - no exception, no log line, just an absence.
 */
class ListenerDiscoveryTest {

    private EventManager bus;

    @BeforeEach
    void setUp() {
        Calls.reset();
        this.bus = new EventManager().setErrorHandler((listener, event, error) -> {
        });
    }

    /**
     * Regression: the scanner used {@code getDeclaredMethods()} on the container class only,
     * which does not return inherited methods, so every listener declared on a base class -
     * the natural way to share behaviour - was silently dropped.
     */
    @Test
    void listenersInheritedFromABaseClassAreRegistered() {
        assertTrue(this.bus.register(new DerivedListener()));

        this.bus.post(new Tick());

        assertEquals(List.of("base-first", "derived", "base-second"), Calls.entries,
                "HIGH then NORMAL then LOW, inherited listeners included");
    }

    @Test
    void listenersFromAnIntermediateBaseClassAreRegistered() {
        assertTrue(this.bus.register(new DeepListener()));

        this.bus.post(new Tick());

        assertEquals(List.of("root", "middle", "leaf"), Calls.entries);
    }

    @Test
    void defaultInterfaceMethodsAreRegistered() {
        assertTrue(this.bus.register(new HookedListener()));

        this.bus.post(new Tick());

        assertEquals(List.of("interface-b", "interface-a", "own"), Calls.entries,
                "the HIGH-priority interface hook must run first");
    }

    /** A class method beats an interface default with the same signature. */
    @Test
    void aClassMethodOverridesAnInterfaceDefault() {
        assertTrue(this.bus.register(new OverridingListener()));

        this.bus.post(new Tick());

        assertEquals(List.of("class wins"), Calls.entries, "the interface default must not also fire");
    }

    @Test
    void anOverriddenListenerIsRegisteredExactlyOnce() {
        MethodListenerScanner scanner = new MethodListenerScanner();

        List<EventListener> found = scanner.locate(new OverridingListener());

        assertEquals(1, found.size(), "an override must not produce two listeners");
        assertTrue(found.get(0).describe().startsWith(OverridingListener.class.getName()));
    }

    @Test
    void twoInterfacesDeclaringTheSameSignatureRegisterOnce() {
        assertTrue(this.bus.register(new DiamondListener()));

        this.bus.post(new Tick());

        assertEquals(List.of("diamond once"), Calls.entries,
                "an inherited default must be registered once, not once per path to it");
    }

    @Test
    void priorityIsHonouredAcrossInheritedListeners() {
        assertTrue(this.bus.register(new MixedPriorityListener()));

        this.bus.post(new Tick());

        assertEquals(List.of("high", "normal", "low"), Calls.entries);
    }

    @Test
    void privateInheritedListenersAreFound() {
        assertTrue(this.bus.register(new PrivateBaseListener()));

        this.bus.post(new Tick());

        assertEquals(List.of("private base"), Calls.entries);
    }

    @Test
    void listenersWithUnusableSignaturesAreRejectedRatherThanFatal() {
        List<EventListener> found = new MethodListenerScanner().locate(new MalformedListener());

        List<String> names = found.stream()
                .map(EventListener::describe)
                .map(name -> name.substring(name.lastIndexOf('#') + 1))
                .collect(Collectors.toList());

        assertEquals(List.of("wellFormed"), names, "only the well-formed signature survives");
    }

    @Test
    void listenersOfDifferentEventTypesOnOneObjectAreAllFound() {
        assertTrue(this.bus.register(new MultiEventListener()));

        this.bus.post(new Tick());
        this.bus.post(new Ping());

        assertEquals(List.of("tick", "ping"), Calls.entries);
    }

    @Test
    void aContainerIsRegisteredOnceEvenWithManyListeners() {
        MultiEventListener listener = new MultiEventListener();

        assertTrue(this.bus.register(listener));
        assertFalse(this.bus.register(listener), "the same instance must not be registered twice");

        this.bus.post(new Tick());
        assertEquals(List.of("tick"), Calls.entries);
    }

    // --- fixtures -------------------------------------------------------------------------

    static final class Calls {
        static final List<String> entries = new java.util.ArrayList<>();

        private Calls() {
        }

        static void reset() {
            entries.clear();
        }
    }

    public static class Tick extends Event {
    }

    public static class Ping extends Event {
    }

    public static class BaseListener {
        @Listener(priority = ListenerPriority.HIGH)
        public void first(Tick event) {
            Calls.entries.add("base-first");
        }

        @Listener(priority = ListenerPriority.LOW)
        public void second(Tick event) {
            Calls.entries.add("base-second");
        }
    }

    public static final class DerivedListener extends BaseListener {
        @Listener(priority = ListenerPriority.NORMAL)
        public void own(Tick event) {
            Calls.entries.add("derived");
        }
    }

    public static class RootListener {
        @Listener(priority = ListenerPriority.FIRST)
        public void root(Tick event) {
            Calls.entries.add("root");
        }
    }

    public static class MiddleListener extends RootListener {
        @Listener
        public void middle(Tick event) {
            Calls.entries.add("middle");
        }
    }

    public static final class DeepListener extends MiddleListener {
        @Listener(priority = ListenerPriority.LAST)
        public void leaf(Tick event) {
            Calls.entries.add("leaf");
        }
    }

    public interface HookA {
        @Listener
        default void a(Tick event) {
            Calls.entries.add("interface-a");
        }
    }

    public interface HookB {
        @Listener(priority = ListenerPriority.HIGH)
        default void b(Tick event) {
            Calls.entries.add("interface-b");
        }
    }

    public static final class HookedListener implements HookA, HookB {
        @Listener(priority = ListenerPriority.LOW)
        public void own(Tick event) {
            Calls.entries.add("own");
        }
    }

    public interface Named {
        @Listener
        default void handle(Tick event) {
            Calls.entries.add("interface loses");
        }
    }

    public static final class OverridingListener implements Named {
        @Override
        @Listener
        public void handle(Tick event) {
            Calls.entries.add("class wins");
        }
    }

    public interface Left {
        @Listener
        default void shared(Tick event) {
            Calls.entries.add("diamond once");
        }
    }

        /** A real diamond: Right inherits Left's default, so the signature is reachable twice. */
    public interface Right extends Left {
    }

        public static final class DiamondListener implements Right {
    }

    public static class PriorityBase {
        @Listener(priority = ListenerPriority.NORMAL)
        public void normal(Tick event) {
            Calls.entries.add("normal");
        }
    }

    public static final class MixedPriorityListener extends PriorityBase {
        @Listener(priority = ListenerPriority.HIGH)
        public void high(Tick event) {
            Calls.entries.add("high");
        }

        @Listener(priority = ListenerPriority.LOW)
        public void low(Tick event) {
            Calls.entries.add("low");
        }
    }

    public static class PrivateBase {
        @Listener
        private void hidden(Tick event) {
            Calls.entries.add("private base");
        }
    }

    public static final class PrivateBaseListener extends PrivateBase {
    }

    public static final class MalformedListener {
        @Listener
        public void wellFormed(Tick event) {
            Calls.entries.add("well-formed");
        }

        @Listener
        public void tooManyArguments(Tick event, int extra) {
        }

        @Listener
        public void wrongArgumentType(String notAnEvent) {
        }

        @Listener
        public void noArguments() {
        }
    }

    public static final class MultiEventListener {
        @Listener
        public void onTick(Tick event) {
            Calls.entries.add("tick");
        }

        @Listener
        public void onPing(Ping event) {
            Calls.entries.add("ping");
        }
    }
}
