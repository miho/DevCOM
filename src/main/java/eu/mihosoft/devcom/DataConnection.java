/*
 * Copyright 2019-2022 Michael Hoffer <info@michaelhoffer.de>. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * If you use this software for scientific research then please cite the following publication(s):
 *
 * M. Hoffer, C. Poliwoda, & G. Wittum. (2013). Visual reflection library:
 * a framework for declarative GUI programming on the Java platform.
 * Computing and Visualization in Science, 2013, 16(4),
 * 181–192. http://doi.org/10.1007/s00791-014-0230-y
 */
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
     * Registers a listener that is notified whenever the connection is opened.
     * @param l the data listener to register
     * @return a subscription that allows a listener to be unsubscribed
     */
    Subscription registerConnectionOpenedListener(Consumer<DataConnection<T, ?>> l);

    /**
     * Registers a listener that is notified whenever the connection is closed.
     * @param l the data listener to register
     * @return a subscription that allows a listener to be unsubscribed
     */
    Subscription registerConnectionClosedListener(Consumer<DataConnection<T, ?>> l);

    /**
     * Registers a listener that is notified whenever an I/O error occurees.
     * @param l the data listener to register
     * @return a subscription that allows a listener to be unsubscribed
     */
    Subscription registerIOErrorListener(BiConsumer<DataConnection<T, ?>, Exception> l);


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
    void open() throws RuntimeException;

    @Override
    /**
     * Closes the connection.
     */
    void close() throws RuntimeException;

    /**
     *
     * @param onConnectionClosed
     */
    void setOnConnectionClosed(Consumer<DataConnection<T, ?>> onConnectionClosed);
}
