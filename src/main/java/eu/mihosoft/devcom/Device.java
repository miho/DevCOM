package eu.mihosoft.devcom;

import vjavax.observer.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;


public final class Device<T> implements AutoCloseable {

    private final BiConsumer<Device<T>, DataConnection<T, ?>> connectionBehavior;
    private final Controller<T, DataConnection<T, ?>> controller;
    private volatile DataConnection<T, ?> connection;
    private final AtomicReference<State> connectionState = new AtomicReference<>(State.DISCONNECTED);
    private final List<BiConsumer<State,State>> stateChangedListeners = new ArrayList<>();

    public enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        ERROR
    }

    public Device(BiConsumer<Device<T>, DataConnection<T, ?>> connectionBehavior) {
        this.connectionBehavior = connectionBehavior;
        this.controller = new Controller<>();
    }

    public static <T> DeviceBuilder<T> newBuilder() {
        return new DeviceBuilder<>();
    }

    public static final class DeviceBuilder<T> {
        private BiConsumer<Device<T>, DataConnection<T, ?>> connectionBehavior;

        DeviceBuilder() {
            //
        }

        public DeviceBuilder<T> withConnectionBehavior(BiConsumer<Device<T>, DataConnection<T,?>> connectionBehavior) {
            this.connectionBehavior = connectionBehavior;
            return this;
        }

        public Device<T> build() {
            return new Device<>(connectionBehavior);
        }
    }

    public State getConnectionState() {
        return connectionState.get();
    }

    public boolean isConnected() {
        return connectionState.get() == State.CONNECTED;
    }

    private void setConnectionState(State s) {
        var prev = connectionState.getAndSet(s);

        if(s != prev) {
            CompletableFuture.runAsync(() -> stateChangedListeners.parallelStream().
                filter(l -> l != null).forEach(l -> l.accept(prev, s))).join();
        }
    }

    public Subscription registerOnConnectionStateChanged(BiConsumer<State, State> l) {
        stateChangedListeners.add(l);
        return ()->stateChangedListeners.remove(l);
    }

    public CompletableFuture<Void> connectAsync(DataConnection<T, ?> connection) {
        this.connection = connection;
        return CompletableFuture.runAsync(()->{
            setConnectionState(State.CONNECTING);
            try {
                controller.init((DataConnection<T, DataConnection<T, ?>>) connection);
                connectionBehavior.accept(this, connection);
                setConnectionState(State.CONNECTED);
            } catch(Exception ex) {
                ex.printStackTrace();
                setConnectionState(State.ERROR);
            }
        });
    }

    @Override
    public void close() {
        closeAsync().join();
    }

    public CompletableFuture<Void> closeAsync() {
        return CompletableFuture.runAsync(()->{
            setConnectionState(State.DISCONNECTING);
            try {

                try (var ctrlRes = controller;
                     var conRes = connection) {
                    // auto close
                }

                setConnectionState(State.DISCONNECTED);
            } catch(Exception ex) {
                setConnectionState(State.ERROR);
            }
        });
    }

    public Controller<T, DataConnection<T, ?>> getController() {
        return controller;
    }
}