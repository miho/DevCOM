package eu.mihosoft.devcom;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortTimeoutException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
        dataListeners.add(l);
        return ()-> dataListeners.remove(l);
    }

    @Override
    public Subscription registerConnectionOpenedListener(Consumer<DataConnection<T, ?>> l) {
        openListeners.add(l);
        return ()-> openListeners.remove(l);
    }


    @Override
    public Subscription registerConnectionClosedListener(Consumer<DataConnection<T, ?>> l) {
        closeListeners.add(l);
        return ()-> closeListeners.remove(l);
    }

    @Override
    public Subscription registerIOErrorListener(BiConsumer<DataConnection<T, ?>, Exception> l) {
        ioErrorListeners.add(l);
        return ()-> ioErrorListeners.remove(l);
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

        receiveThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && isOpen()) {
                try {
                    final var p = format.readData(inputStream);
                    CompletableFuture.runAsync(()->dataListeners.parallelStream().
                            filter(l -> l != null).forEach(l -> l.accept(p)));
                    if (onDataReceived != null) {
                        CompletableFuture.runAsync(()->onDataReceived.accept(p));
                    }
                } catch (IOException | RuntimeException e) {

                    CompletableFuture.runAsync(()->ioErrorListeners.parallelStream().
                        filter(l -> l != null).forEach(l -> l.accept(StreamConnection.this, e)));

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
        CompletableFuture.runAsync(()->openListeners.parallelStream().
            filter(l -> l != null).forEach(l -> l.accept(StreamConnection.this)));
        if (onConnectionOpened != null) onConnectionOpened.accept(this);
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

            CompletableFuture.runAsync(()->ioErrorListeners.parallelStream().
                filter(l -> l != null).forEach(l -> l.accept(StreamConnection.this, e)));

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
            CompletableFuture.runAsync(()->ioErrorListeners.parallelStream().
                filter(l -> l != null).forEach(l -> l.accept(StreamConnection.this, e)));

            if (onIOError != null) {
                onIOError.accept(this, e);
            }

            throw new RuntimeException("Cannot close this connection", e);
        }

        open = false;

        CompletableFuture.runAsync(()->closeListeners.parallelStream().
            filter(l -> l != null).forEach(l -> l.accept(StreamConnection.this)));

        if (onConnectionClosed != null) onConnectionClosed.accept(this);
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