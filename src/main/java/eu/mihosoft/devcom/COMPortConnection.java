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

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortTimeoutException;
import org.tinylog.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * COM port connection for reading and writing data from and to a COM port.
 */
public final class COMPortConnection<T> implements DataConnection<T, COMPortConnection<T>> {

    private final static String TAG = "eu.mihosoft.devcom:connection";
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
        DevCOM.checkInit();
        this.onPortFailed = (comPortBonsaiConnection, e) -> {};
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

    /**
     * Gets the timeout for closing the port.
     * @return the timeout for closing the port in milliseconds
     */
    public long getPortCloseTimeout() {
        return connection.getPortCloseTimeout();
    }

    /**
     * Sets the timeout for closing the port.
     * @param portCloseTimeout the timeout for closing the port in milliseconds
     * @return this connection
     */
    public COMPortConnection<T> setPortCloseTimeout(long portCloseTimeout) {
        this.connection.setPortCloseTimeout(portCloseTimeout);
        return this;
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
            this.port = openPort(config, connection::close);
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
    public void close() throws RuntimeException {
        try {
            connection.close();
        } finally {
            if (port != null) {
                boolean closed = port.closePort();
                port = null;

                if (!closed) {
                    var ex = new RuntimeException("Could not close port: " + config.getName());
                    if (onPortFailed != null) onPortFailed.accept(this, ex);
                    throw ex;
                }
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

    @Override
    public Subscription registerIOErrorListener(BiConsumer<DataConnection<T, ?>, Exception> l) {
        return connection.registerIOErrorListener(l);
    }

    /**
     * Utility method for opening the selected COM-port.
     *
     * @return the serial port object to be used form communication
     */
    private static SerialPort openPort(PortConfig config, Runnable onCloseOperation)  throws RuntimeException {

        var port = SerialPort.getCommPort(config.getName());

        if(port==null) {
            var ex = new RuntimeException("Cannot find selected COM port: " + config.getName());
            throw ex;
        }

        port.setComPortTimeouts(
            SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_READ_BLOCKING
            , 0/*wait until data to read is available*/, config.getWriteTimeout()
        );

        port.flushIOBuffers();

        port.setComPortParameters(config.getBaudRate(),
            config.getNumberOfDataBits(),
            config.getStopBits().getValue(),
            config.getParityBits().getValue());

        if (!port.openPort(config.getSafetyTimeout())) {
            var ex = new RuntimeException("Cannot open port: " + config.getName());
            throw ex;
        } else {
            port.addDataListener(new SerialPortDataListener() {
                @Override
                public int getListeningEvents() { return SerialPort.LISTENING_EVENT_PORT_DISCONNECTED; }
                @Override
                public void serialEvent(SerialPortEvent event)
                {
                    try {
                        if (onCloseOperation != null) {
                            Logger.tag(TAG).debug(
                                "closing port '" + event.getSerialPort().getSystemPortName()
                                    + "' (custom callback) because of event: " + event.getEventType());
                            onCloseOperation.run();
                            Logger.tag(TAG).debug(
                                "closed port '" + event.getSerialPort().getSystemPortName()
                                    + "' (custom callback) because of event: " + event.getEventType());
                        }
                    } finally {
                        port.removeDataListener();
                        Logger.tag(TAG).debug(
                            "device on port '" + event.getSerialPort().getSystemPortName()
                                + "' disconnected. closing port.");
                        event.getSerialPort().closePort();
                    }
                }
            });

        }

        return port;
    }

    @Override
    public DataFormat<T> getFormat() {
        return connection.getFormat();
    }

    private static List<SerialPort> getAvailablePorts() {
        var ports = Arrays.asList(SerialPort.getCommPorts());
        return ports;
    }

    /**
     * Returns a list of COM port names of all ports currently available.
     * @return a list of COM port names of all ports currently available
     */
    public static List<String> getPortNames() {
        return getAvailablePorts().stream().map(p->p.getSystemPortName()).
                collect(Collectors.toList());
    }

    /**
     * Returns a list of the infos of all ports currently available.
     * @return a list of the infos of all ports currently available
     */
    public static List<PortInfo> getPortInfos() {
        return getAvailablePorts().stream().map(p->PortInfo.newBuilder()
                .withName(p.getSystemPortName())
                .withDescription(p.getPortDescription())
                .withLocation(p.getPortLocation())
                .withExtendedName(p.getDescriptivePortName()).build())
                .collect(Collectors.toList());
    }

    @Override
    public void setOnConnectionClosed(Consumer<DataConnection<T, ?>> onConnectionClosed) {
        connection.setOnConnectionClosed(onConnectionClosed);
    }
}