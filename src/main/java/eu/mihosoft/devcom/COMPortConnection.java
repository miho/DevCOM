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
 * COM port connection for reading and writing data from and to a COM port.
 */
public final class COMPortConnection<T> implements DataConnection<T, COMPortConnection<T>> {
    // COM-port config
    private PortConfig config;
    // task to be executed if the communication with the selected COM-port failed
    private BiConsumer<COMPortConnection<T>, Exception> onPortFailed;
    // serial port used for communication
    private volatile SerialPort port;
    // stream connection
    private final StreamConnection<T> connection;

    /**
     * Creates a new connection instance.
     *
     * @param format the data format to use for communication
     */
    public COMPortConnection(DataFormat<T> format) {
        this.onPortFailed = (comPortBonsaiConnection, e) -> {e.printStackTrace();};
        connection = new StreamConnection<>(format);
    }

    /**
     * Creates a new connection instance.
     *
     * @param onPortOpened task to be executed if the selected COM-port has been successfully opened
     * @param onPortFailed task to be executed if communication with the selected COM-port failed
     * @param onIOError task to be executed if an io error has occurred
     */
    public COMPortConnection(DataFormat<T> format, Consumer<COMPortConnection<T>> onPortOpened,
                             BiConsumer<COMPortConnection<T>, Exception> onPortFailed,
                             BiConsumer<COMPortConnection<T>, Exception> onIOError) {
        this.onPortFailed = onPortFailed;

        Consumer<StreamConnection<T>> onPortOpenedFwd = null;
        if(onPortOpened!=null) {
            onPortOpenedFwd = (c)->onPortOpened.accept(this);
        }

        BiConsumer<StreamConnection<T>, Exception> onIOErrorFwd = null;
        if(onIOError!=null) {
            onIOErrorFwd = (sC, e) -> onIOError.accept(this, e);
        }

        connection = new StreamConnection<T>(format, onPortOpenedFwd, onIOErrorFwd);
    }


    @Override
    public COMPortConnection<T> setOnDataReceived(Consumer<T> onDataReceived) {
        connection.setOnDataReceived(onDataReceived);
        return this;
    }

    @Override
    public COMPortConnection<T> setOnIOError(BiConsumer<COMPortConnection<T>, Exception> onIOError) {
        BiConsumer<StreamConnection<T>, Exception> onIOErrorFwd = null;
        if(onIOError!=null) {
            onIOErrorFwd = (sC, e) -> onIOError.accept(this, e);
        }
        connection.setOnIOError(onIOErrorFwd);
        return this;
    }

    /**
     * Specifies the action to be performed if the port cannot be opened.
     * @param onPortFailed the action to be performed if the port cannot be opened
     */
    public COMPortConnection<T> setOnPortFailed(BiConsumer<COMPortConnection<T>, Exception> onPortFailed) {
        this.onPortFailed = onPortFailed;
        return this;
    }

    /**
     * Specifies the action to be performed if the port has been opened.
     * @param onPortOpened the action to be performed if the port has been opened
     */
    public COMPortConnection<T> setOnPortOpened(Consumer<COMPortConnection<T>> onPortOpened) {

        Consumer<StreamConnection<T>> onPortOpenedFwd = null;
        if(onPortOpened!=null) {
            onPortOpenedFwd = (c)->onPortOpened.accept(this);
        }
        connection.setOnConnectionOpened(onPortOpenedFwd);

        return this;
    }

    @Override
    public Subscription registerDataListener(Consumer<T> l) {
        return connection.registerDataListener(l);
    }

    /**
     * Opens the specified port and connects to it.
     * @param config port config (name, baud rate etc.)
     */
    public void open(PortConfig config) {
        this.config = config;
        open();
    }

    /**
     * Sets the configuration to use for connecting to a port.
     * @param config the port configuration (name, baud rate etc.)
     * @return this connection
     */
    public COMPortConnection<T> setPortConfig(PortConfig config) {
        this.config = config;

        return this;
    }

    /**
     * Opens the specified port and connects to it.
     */
    @Override
    public void open() {

        if(port!=null) {
            throw new RuntimeException("Please close the connection to the current port ('"
                    + config.getName() + "') before connecting it.");
        }

        if (this.config == null) {
            throw new RuntimeException("Please specify a configuration before trying to open this connection. " +
                    "See 'setPortConfig(PortConfig config)'.");
        }

        try {
            this.port = openPort(config);
            var inputStream = port.getInputStream();
            var outputStream = port.getOutputStream();
            connection.open(inputStream, outputStream);

        } catch(Exception ex) {
            if (onPortFailed != null) onPortFailed.accept(this, ex);
            throw ex;
        }
    }

    @Override
    public void writeData(T msg) throws IOException {
        connection.writeData(msg);
    }

    @Override
    public boolean isOpen() {
        return connection.isOpen();
    }

    /**
     * Returns the config of the port this object is currently connected to.
     * @return the config of the port this object is currently connected to
     */
    public PortConfig getPortConfig() {
        return config;
    }

    /**
     * Closes the connection to the specified port.
     */
    @Override
    public void close() throws RuntimeException{
        try {
            connection.close();
        } finally {
            if (port != null) {
                if (!port.closePort()) {
                    var ex = new RuntimeException("Could not close port: " + config.getName());
                    if (onPortFailed != null) onPortFailed.accept(this, ex);
                    throw ex;
                }
                port = null;
            }

        }
    }

    @Override
    public Subscription registerConnectionClosedListener(Consumer<DataConnection<T, ?>> l) {
        return connection.registerConnectionClosedListener(l);
    }

    @Override
    public Subscription registerConnectionOpenedListener(Consumer<DataConnection<T, ?>> l) {
        return connection.registerConnectionOpenedListener(l);
    }

    /**
     * Utility method for opening the selected COM-port.
     *
     * @return the serial port object to be used form communication
     */
    private static SerialPort openPort(PortConfig config)  throws RuntimeException{

        AtomicReference<SerialPort> result = new AtomicReference<>();

        Arrays.asList(SerialPort.getCommPorts()).stream().filter(p -> config.getName().equals(p.getSystemPortName()))
                .findFirst().ifPresentOrElse((port -> {

            port.setComPortTimeouts(
                    SerialPort.TIMEOUT_READ_BLOCKING|SerialPort.TIMEOUT_READ_BLOCKING
                    , 0/*wait until done*/, 0/*wait until done*/
            );

            port.setComPortParameters(config.getBaudRate(),
                    config.getNumberOfDataBits(),
                    config.getStopBits().getValue(),
                    config.getParityBits().getValue());

            if (!port.openPort()) {
                var ex = new RuntimeException("Cannot open port: " + port.getDescriptivePortName());
                throw ex;
            }

            result.set(port);

        }), () -> {
            var ex = new RuntimeException("Cannot find selected COM port: " + config.getName());
            throw ex;
        });

        return result.get();

    }

    @Override
    public DataFormat<T> getFormat() {
        return connection.getFormat();
    }

    /**
     * Returns a list of all COM ports currently available.
     * @return a list of all COM ports currently available
     */
    public static List<String> getPortNames() {
        return Arrays.asList(SerialPort.getCommPorts()).stream().map(p->p.getSystemPortName()).
                collect(Collectors.toList());
    }

    @Override
    public void setOnConnectionClosed(Consumer<DataConnection<T, ?>> onConnectionClosed) {
        connection.setOnConnectionClosed(onConnectionClosed);
    }
}