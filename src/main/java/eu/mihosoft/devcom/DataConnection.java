package eu.mihosoft.devcom;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Data connection for reading and writing data. Applications include reading and writing from/to a stream,
 * COM ports etc.
 */
public interface DataConnection<T, V extends DataConnection<T, ?>> extends AutoCloseable {

    /**
     * Sets the action to be executed if data has been received.
     * @param onDataReceived consumer to be called if data has been received
     */
    V setOnDataReceived(Consumer<T> onDataReceived);

    /**
     * Specifies the action to be performed if an I/O error occurs.
     * @param onIOError the action to be performed if an I/O error occurs
     */
    V setOnIOError(BiConsumer<V, Exception> onIOError);

    /**
     * Registers a data listener that is notified whenever a message has been received.
     * @param l the data listener to register
     * @return a subscription that allows a listener to be unsubscribed
     */
    Subscription registerDataListener(Consumer<T> l);

    /**
     * Writes the specified raw message to the output stream.
     *
     * @param msg          the message to send
     * @throws IOException if an i/o error occurs during message sending
     */
    void writeData(T msg) throws IOException;

    /**
     * Returns the data format used by this connection.
     * @return the data format used by this connection
     */
    DataFormat<T> getFormat();

    /**
     * Indicates whether this connection is currently open.
     * @return {@code true} if this connection is currently open; {@code false} otherwise
     */
    boolean isOpen();

    /**
     * Listener subscription.
     */
    @FunctionalInterface
    public interface Subscription {
        /**
         * Unsubscribes the listener.
         */
        void unsubscribe();
    }

    /**
     * Opens the connection.
     */
    void open();

    @Override
    /**
     * Closes the connection.
     */
    void close();
}
