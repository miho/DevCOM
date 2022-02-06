package eu.mihosoft.devcom;

import eu.mihosoft.devcom.PortEvent;
import vjavax.observer.Subscription;

import java.util.*;
import java.util.concurrent.*;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * COM-Port scanner. The purpose of this class is to notify about added and removed ports
 * as well as creating data connections for each available port. While real COM ports are
 * usually not added and removed during runtime, the growing popularity of USB/FTDI-based
 * COM-ports make this scanner a useful tool for discovering available ports and establishing
 * data connections.
 */
public enum PortScanner {

    /**
     * The instance of this scanner.
     */
    INSTANCE;

    // number of threads used for port discovery
    private static int MAX_DISCOVERY_THREADS = 16;

    // port event listeners
    private final List<Consumer<PortEvent>> listeners
        = new ArrayList<>();

    // list of discovered ports
    private final List<String> portList = new ArrayList<>();

    // future used to cancel periodic scanning
    private ScheduledFuture<?> f;

    // start this scanner automatically
    static {
        getInstance().start();
    }

    /**
     * Returns the instance of this scanner.
     * @return the instance of this scanner
     */
    public static PortScanner getInstance() {
        return INSTANCE;
    }

    /**
     * Starts this port scanner with the default period.
     */
    public void start() {
        start(1000/*ms*/);
    }

    private ScheduledExecutorService executor;

    /**
     * Starts this port scanner with the specified period.
     * @param period scanner period in milliseconds
     */
    public void start(long period) {
        stop();
        executor = Executors.newScheduledThreadPool(MAX_DISCOVERY_THREADS+1);
        f = executor.scheduleAtFixedRate(()->{
            var evt = pollPorts();
            if(!evt.getAdded().isEmpty() || !evt.getRemoved().isEmpty()) {
                for (var l : listeners) {
                    executor.execute(()->l.accept(evt));
                }
            }
        }, 0, period, TimeUnit.MILLISECONDS);
    }

    /**
     * Indicates whether this scanner is currently running.
     * @return {@code true} if this code is currently running; {@code false} otherwise
     */
    public boolean isRunning() {
        if(f==null) return false;

        return !f.isCancelled() && !f.isDone();
    }

    /**
     * Stops this scanner.
     */
    public void stop() {
        if(f!=null) {
            f.cancel(true);
            f = null;
        }

        var e = executor;
        if(e!=null) {
            e.shutdown();
            executor = null;
        }
    }

    /**
     * Returns a port that can successfully be connected to according to the specified
     * try-connect function.
     * @param portNames list of port names
     * @param connectionProvider connection provider
     * @param tryConnect function that attempts to connect to the specified port
     * @param <T> data type used for communication (e.g., java.lang.String or a custom packet format)
     * @return optional COM-port connection if a port that can be connected to could be found
     */
    public static <T> Optional<COMPortConnection<T>> findPort(
        List<String> portNames,
        Function<String, COMPortConnection<T>> connectionProvider,
        Function<COMPortConnection<T>, Boolean> tryConnect) {
        var executor = Executors.newFixedThreadPool(Math.min(portNames.size(), MAX_DISCOVERY_THREADS));
        return Optional.ofNullable(CompletableFuture.supplyAsync(
            () -> {
                try {
                    return executor.invokeAny(portNames.stream().map(
                        portName -> (Callable<COMPortConnection<T>>) () -> {
                            var conn = connectionProvider.apply(portName);
                            if (tryConnect.apply(conn)) {
                                return conn;
                            } else {
                                throw new RuntimeException("Cannot connect to port " + portName);
                            }
                        }).collect(Collectors.toList())
                    );
                } catch (Exception e) {
                    // e.printStackTrace();
                    return null;
                }
            }
        ).completeOnTimeout(null, 100000, TimeUnit.MILLISECONDS).handle((comPortConnection, throwable) -> {
            executor.shutdown();
            return comPortConnection;
        }).join());
    }

    /**
     * Polls COM ports.
     * @return event that describes which ports have been added or removed since last calling this method
     */
    private PortEvent pollPorts () {
        var currentlyAvailablePorts = COMPortConnection.getPortNames();

        var added = new ArrayList<String>();
        var removed = new ArrayList<String>();
        computeDiff(portList, currentlyAvailablePorts, added, removed);

        portList.addAll(added);
        portList.removeAll(removed);

        return PortEvent.newBuilder()
            .withTimestamp(System.currentTimeMillis())
            .withAdded(added)
            .withRemoved(removed)
                .build();
    }

    /**
     * Event esception that contains a list of causing exceptions during port scanning.
     */
    public static class EventException extends RuntimeException {
        private final List<Exception> exceptions = new ArrayList<>();
        private List<Exception> unmodifiableExceptions = Collections.unmodifiableList(exceptions);

        /**
         * Returns a list of causing exceptions.
         * @return list of causing exceptions
         */
        public List<Exception> getExceptions() {
            return unmodifiableExceptions;
        }

        /**
         * Creates a new event exception.
         * @param msg exception message
         * @param exceptions exceptions causing this exception
         */
        public EventException(String msg, List<Exception> exceptions) {
            super(msg);
            this.exceptions.addAll(exceptions);
        }

        /**
         * Creates a new event exception.
         * @param msg exception message
         * @param exceptions exceptions causing this exception
         */
        public EventException(String msg, Exception... exceptions) {
            this(msg, Arrays.asList(exceptions));
        }
    }

    /**
     * Adds a port listener to this scanner.
     * @param consumer port listener
     * @return a subscription that can be used to unsubscribe the specified listener from this port scanner
     */
    public Subscription addPortListener(Consumer<PortEvent> consumer) {
        listeners.add(consumer);

        return () -> {
            listeners.remove(consumer);
        };
    }


    /**
     * Returns list containing the elements of the specified collection where the given elements have been removed.
     *
     * @param collection the collection
     * @param remove     elements to be removed
     * @param <E>        element type
     * @return list containing the elements of the specified collection where the given elements have been removed
     */
    private static <E> List<E> removeAll(final Collection<E> collection, final Collection<?> remove) {
        return collection.stream().filter(e -> !remove.contains(e)).collect(Collectors.toList());
    }

    /**
     * Computes the difference between the specified collections.
     *
     * @param oldCollection old collection to compare
     * @param newCollection new collection to compare
     * @param added         elements that have been added
     * @param removed       elements that have been removed
     * @param <E>           element type
     */
    private static <E> void computeDiff(final Collection<E> oldCollection, final Collection<E> newCollection,
                                       final Collection<E> added, final Collection removed) {
        added.addAll(removeAll(newCollection, oldCollection));
        removed.addAll(removeAll(oldCollection, newCollection));
    }
}
