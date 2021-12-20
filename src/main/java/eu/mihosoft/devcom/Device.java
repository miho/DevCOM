package eu.mihosoft.devcom;

import vjavax.observer.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * A device combines a data connection and a controller. It handles the
 * process of connecting and disconnecting from a physical device.
 *
 * @param <T> protocol data type, such as String
 */
public final class Device<T> implements AutoCloseable {

    private final BiConsumer<Device<T>, DataConnection<T, ?>> connectionBehavior;
    private final Controller<T, DataConnection<T, ?>> controller;
    private volatile DataConnection<T, ?> connection;
    private final AtomicReference<State> connectionState = new AtomicReference<>(State.DISCONNECTED);
    private final List<BiConsumer<State,State>> stateChangedListeners = new ArrayList<>();

    /**
     * Connection state.
     */
    public enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        ERROR
    }

    private Device(BiConsumer<Device<T>, DataConnection<T, ?>> connectionBehavior) {
        this.connectionBehavior = connectionBehavior;
        this.controller = new Controller<>();
    }

    /**
     * Returns a new device builder.
     * @param <T> protocol data type, such as String
     * @return a new device builder
     */
    public static <T> DeviceBuilder<T> newBuilder() {
        return new DeviceBuilder<>();
    }

    /**
     * Device builder.
     * @param <T> protocol data type, such as String
     */
    public static final class DeviceBuilder<T> {
        private BiConsumer<Device<T>, DataConnection<T, ?>> connectionBehavior;

        DeviceBuilder() {
            //
        }

        /**
         * Defines the connection behavior.
         * @param connectionBehavior connection behavior
         * @return this device builder
         */
        public DeviceBuilder<T> withConnectionBehavior(BiConsumer<Device<T>, DataConnection<T,?>> connectionBehavior) {
            this.connectionBehavior = connectionBehavior;
            return this;
        }

        /**
         * Returns a new device.
         *
         * @return a new device
         */
        public Device<T> build() {
            return new Device<>(connectionBehavior);
        }
    }

    /**
     * Returns the current connection state.
     * @return the current connection state
     */
    public State getConnectionState() {
        return connectionState.get();
    }

    /**
     * Determines whether this device is connected.
     * @return {@code true} if this device is connected; {@code false} otherwise
     */
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

    /**
     * Registers a listener that is notified whenever the connection state changes.
     * @param l listener to register
     * @return subscription that allows to unregister the listener
     */
    public Subscription registerOnConnectionStateChanged(BiConsumer<State, State> l) {
        stateChangedListeners.add(l);
        return ()->stateChangedListeners.remove(l);
    }

    /**
     * Start the connection procedure.
     * @param connection the connection to use for communication
     * @return a future that is completed as soon is connection is established or the connection attempt failed
     */
    public CompletableFuture<Void> connectAsync(DataConnection<T, ?> connection) {
        this.connection = connection;

        return CompletableFuture.runAsync(()->{
            if(isConnected()) {
                close();
            }
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

    /**
     * Closes this device asynchronously.
     * @return a future that is completed of the connection procedure is finished
     */
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

    /**
     *
     * @return the controller that is used for sending and receiving data and commands
     */
    public Controller<T, DataConnection<T, ?>> getController() {
        return controller;
    }
}