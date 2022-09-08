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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Stream connection for reading and writing data from/to io streams.
 */
public final class StreamConnection<T> implements DataConnection<T, StreamConnection<T>> {
    // task to be executed if the selected connection has been successfully opened
    private Consumer<StreamConnection<T>> onConnectionOpened;
    // task to be executed if the communication with the selected streams failed
    private BiConsumer<StreamConnection<T>, Exception> onIOError;
    private volatile Consumer<T> onDataReceived;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Thread receiveThread;
    private final DataFormat<T> format;
    private final List<Consumer<T>> dataListeners = new ArrayList<>();
    private final List<Consumer<DataConnection<T, ?>>> openListeners = new ArrayList<>();
    private final List<Consumer<DataConnection<T, ?>>> closeListeners = new ArrayList<>();
    private final List<BiConsumer<DataConnection<T, ?>, Exception>> ioErrorListeners = new ArrayList<>();
    private boolean open;

    private final ReentrantLock openListenersLock = new ReentrantLock();
    private final ReentrantLock closeListenersLock = new ReentrantLock();
    private final ReentrantLock ioErrorListenersLock = new ReentrantLock();
    private final ReentrantLock dataListenersLock = new ReentrantLock();
    private final ReentrantLock sequentialEventExecutorLock = new ReentrantLock();

    private volatile ExecutorService sequentialEventExecutor;
    private final ExecutorService listenerExecutor = Executors.newCachedThreadPool();

    private Consumer<DataConnection<T, ?>> onConnectionClosed;

    /**
     * Creates a new connection instance.
     *
     * @param format the data format to use for communication
     */
    public StreamConnection(DataFormat<T> format) {
        this.format = format;
    }

    /**
     * Creates a new connection instance.
     *
     * @param onConnectionOpened task to be executed if the selected COM-port has been successfully opened
     * @param onIOError task to be executed if an io error has occurred
     */
    public StreamConnection(DataFormat<T> format, Consumer<StreamConnection<T>> onConnectionOpened,
                            BiConsumer<StreamConnection<T>, Exception> onIOError) {
        this.format = format;
        this.onConnectionOpened = onConnectionOpened;
        this.onIOError = onIOError;
    }

    @Override
    public StreamConnection<T> setOnDataReceived(Consumer<T> onDataReceived) {
        this.onDataReceived = onDataReceived;
        return this;
    }

    @Override
    public StreamConnection<T> setOnIOError(BiConsumer<StreamConnection<T>, Exception> onIOError) {
        this.onIOError = onIOError;
        return this;
    }

    /**
     * Specifies the action to be performed if the connection has been opened.
     * @param onConnectionOpened the action to be performed if the connection has been opened
     */
    public StreamConnection<T> setOnConnectionOpened(Consumer<StreamConnection<T>> onConnectionOpened) {
        this.onConnectionOpened = onConnectionOpened;
        return this;
    }

    @Override
    public Subscription registerDataListener(Consumer<T> l) {
        dataListenersLock.lock();
        try {
            dataListeners.add(l);
        } finally {
            dataListenersLock.unlock();
        }
        return ()-> {
            dataListenersLock.lock();
            try {
                dataListeners.remove(l);
            } finally {
                dataListenersLock.unlock();
            }
        };
    }

    @Override
    public Subscription registerConnectionOpenedListener(Consumer<DataConnection<T, ?>> l) {
        openListenersLock.lock();
        try {
            openListeners.add(l);
        } finally {
            openListenersLock.unlock();
        }
        return ()-> {
            openListenersLock.lock();
            try {
                openListeners.remove(l);
            } finally {
                openListenersLock.unlock();
            }
        };
    }


    @Override
    public Subscription registerConnectionClosedListener(Consumer<DataConnection<T, ?>> l) {
        closeListenersLock.lock();
        try {
            closeListeners.add(l);
        } finally {
            closeListenersLock.unlock();
        }
        return ()-> {
            closeListenersLock.lock();
            try {
                closeListeners.remove(l);
            } finally {
                closeListenersLock.unlock();
            }
        };
    }

    @Override
    public Subscription registerIOErrorListener(BiConsumer<DataConnection<T, ?>, Exception> l) {
        ioErrorListenersLock.lock();
        try {
            ioErrorListeners.add(l);
        } finally {
            ioErrorListenersLock.unlock();
        }

        return ()-> {
            ioErrorListenersLock.lock();
            try {
                ioErrorListeners.remove(l);
            } finally {
                ioErrorListenersLock.unlock();
            }
        };
    }

    /**
     * Opens the specified port and connects to it.
     * @param inputStream input stream to be used by this connection
     * @param outputStream output stream to be used by this connection
     */
    public void open(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        open();
    }

    /**
     * Sets the configuration to use for connecting to a port.
     * @param inputStream the input stream to be used by this connection
     * @param outputStream the output stream to be used by this connection
     * @return this connection
     */
    public StreamConnection<T> setStreams(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;

        if (this.inputStream == null || this.outputStream == null) {
            throw new RuntimeException("Please specify streams before trying to open this connection. " +
                    "See 'setStreams(InputStream inputStream, OutputStream outputStream)'.");
        }

        if(isOpen()) {
            throw new RuntimeException("Please don't set streams while this connection is open.");
        }

        return this;
    }



    /**
     * Opens the specified port and connects to it.
     */
    @Override
    public void open() {

        if (this.inputStream == null || this.outputStream == null) {
            throw new RuntimeException("Please specify streams before trying to open this connection. " +
                    "See 'setStreams(InputStream inputStream, OutputStream outputStream)'.");
        }

        if(isOpen()) {
            throw new RuntimeException("Please close this connection before opening it.");
        }

        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }

        restartSequentialExecutor();

        receiveThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && isOpen()) {

                try {
                    final var p = format.readData(inputStream);

                    notifyDataListeners(p);

                    if (onDataReceived != null) {
                        getSequentialEventExecutor().submit(() -> onDataReceived.accept(p));
                    }
                } catch (IOException | RuntimeException e) {

                    notifyIOListeners(e);

                    if (onIOError != null) {
                        onIOError.accept(this, e);
                    } else {
                        org.tinylog.Logger.debug(e);
                    }
                }
            }
        });
        receiveThread.start();
        this.open = true;
        notifyOpenConnectionListeners();
        if (onConnectionOpened != null) onConnectionOpened.accept(this);
    }

    private void restartSequentialExecutor() {
        sequentialEventExecutorLock.lock();
        try {
            stopSequentialExecutorIfRunning();
            sequentialEventExecutor = Executors.newSingleThreadExecutor();
        } finally {
            sequentialEventExecutorLock.unlock();
        }
    }

    private void stopSequentialExecutorIfRunning() {
        sequentialEventExecutorLock.lock();
        try {
            if (sequentialEventExecutor != null) {
                sequentialEventExecutor.shutdownNow();
                sequentialEventExecutor = null;
            }
        } finally {
            sequentialEventExecutorLock.unlock();
        }
    }

    private ExecutorService getSequentialEventExecutor() {
        sequentialEventExecutorLock.lock();
        try {
            return sequentialEventExecutor;
        } finally {
            sequentialEventExecutorLock.unlock();
        }
    }

    @Override
    public void writeData(T msg) throws IOException {

        if(!isOpen()) {
            throw new RuntimeException("Open this connection before writing to it.");
        }

        try {

            format.writeData(msg, outputStream);
            outputStream.flush();

        } catch (IOException | RuntimeException e) {

            notifyIOListeners(e);

            if (onIOError != null) {
                onIOError.accept(this, e);
            }

            throw e;
        }
    }

    /**
     * Closes the connection to the specified port.
     */
    @Override
    public void close() {

        if (receiveThread != null) {

            receiveThread.interrupt();

            try {
                receiveThread.join(3000);
            } catch (InterruptedException e) {
               receiveThread.interrupt();
            }
            receiveThread = null;
        }

        try(InputStream is = this.inputStream; OutputStream os = this.outputStream) {
        } catch (IOException e) {
            notifyIOListeners(e);

            if (onIOError != null) {
                onIOError.accept(this, e);
            }

            throw new RuntimeException("Cannot close this connection", e);
        }

        open = false;

        notifyCloseConnectionListeners();

        stopSequentialExecutorIfRunning();

        if (onConnectionClosed != null) onConnectionClosed.accept(this);
    }



    private void notifyIOListeners(Exception e) {
        ioErrorListenersLock.lock();
        try {
            var listenersToNotify = new ArrayList<>(ioErrorListeners);

            getSequentialEventExecutor().submit(() -> {
                CompletableFuture.allOf(listenersToNotify.stream()
                        .filter(l->l!=null)
                        .map(l -> CompletableFuture.runAsync(() -> l.accept(this, e), listenerExecutor))
                        .toArray(CompletableFuture[]::new))
                    .join();
            });
        } finally {
            ioErrorListenersLock.unlock();
        }
    }

    private void notifyOpenConnectionListeners() {
        openListenersLock.lock();
        try {
            var listenersToNotify = new ArrayList<>(openListeners);

            getSequentialEventExecutor().submit(() -> {
                // TODO
                CompletableFuture.allOf(listenersToNotify.stream()
                        .filter(l->l!=null)
                        .map(l -> CompletableFuture.runAsync(() -> l.accept(this), listenerExecutor))
                        .toArray(CompletableFuture[]::new))
                    .join();
            });

        } finally {
            openListenersLock.unlock();
        }
    }

    private void notifyCloseConnectionListeners() {
        closeListenersLock.lock();
        try {
            var listenersToNotify = new ArrayList<>(closeListeners);

            getSequentialEventExecutor().submit(() -> {
                CompletableFuture.allOf(listenersToNotify.stream()
                        .filter(l->l!=null)
                        .map(l -> CompletableFuture.runAsync(() -> l.accept(this), listenerExecutor))
                        .toArray(CompletableFuture[]::new))
                    .join();
            });
        } finally {
            closeListenersLock.unlock();
        }
    }

    private void notifyDataListeners(T p) {
        dataListenersLock.lock();
        try {
            var listenersToNotify = new ArrayList<>(dataListeners);

            getSequentialEventExecutor().submit(() -> {
                CompletableFuture.allOf(listenersToNotify.stream()
                        .filter(l->l!=null)
                        .map(l -> CompletableFuture.runAsync(() -> l.accept(p), listenerExecutor))
                        .toArray(CompletableFuture[]::new))
                    .join();
            });

        } finally {
            dataListenersLock.unlock();
        }
    }

    @Override
    public void setOnConnectionClosed(Consumer<DataConnection<T, ?>> onConnectionClosed) {
        this.onConnectionClosed = onConnectionClosed;
    }

    @Override
    public DataFormat<T> getFormat() {
        return format;
    }

    @Override
    public boolean isOpen() {
        return open;
    }
}