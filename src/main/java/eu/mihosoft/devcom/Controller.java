package eu.mihosoft.devcom;

import java.io.IOException;
import java.util.Deque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A device controller for concurrent communication, e.g., via (virtual) COM ports.
 */
public class Controller<T,V extends DataConnection<T, ?>> implements AutoCloseable {
    private final Object simpleLock = new Object();
    private final Deque<Command<T>> cmdQueue = new LinkedBlockingDeque<>();
    private final Deque<Command<T>> replyQueue = new LinkedBlockingDeque<>();
    private volatile ExecutorService executor;
    /*pkg private*/ final AtomicReference<CompletableFuture<Void>> queueTaskFuture = new AtomicReference<>();
    private volatile Thread queueThread;
    private volatile long cmdTimeout = 0/*no timeout, unit: ms*/;
    private volatile Consumer<InterruptedException> onInterrupted;

    /**
     * Creates a new controller instance.
     */
    public Controller() {
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Creates a new controller instance.
     * @param onInterrupted the consumer to call if the command queue thread is interrupted (may be null)
     */
    public Controller(Consumer<InterruptedException> onInterrupted) {
        setOnInterrupted(onInterrupted);
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Sets the command timeout (until the command is sent).
     * @param milliseconds duration in milliseconds (0 means no timeout)
     * @return this controller
     */
    public Controller<T,V> setCommandTimeout(long milliseconds) {
        this.cmdTimeout = milliseconds;
        return this;
    }

    /**
     * Specifies the consumer to call if the command queue thread is interrupted.
     * @param onInterrupted the consumer to call (may be null)
     */
    public final void setOnInterrupted(Consumer<InterruptedException> onInterrupted) {
        this.onInterrupted = onInterrupted;
    }

    /**
     * Initializes this controller.
     *
     * @param dataConnection the data connection to use for communication
     */
    public void init(DataConnection<T, V> dataConnection) {

        Consumer<T> onDataReceived = msg -> {
            // find first element that matches reply
            replyQueue.stream().filter(c -> dataConnection.getFormat().isReply(c, msg)).findFirst().ifPresent( cmd -> {
                replyQueue.removeFirstOccurrence(cmd);
                cmd.getReply().complete(msg);
                if(cmd.getOnResponse()!=null) {
                    cmd.getOnResponse().accept(msg);
                }
            });
        };

        dataConnection.setOnDataReceived(onDataReceived);

        if(queueThread!=null) {
            queueThread.interrupt();
        }

        queueThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    var cmd = cmdQueue.pollFirst();
                    if(cmd==null) {
                        synchronized (simpleLock) {
                            simpleLock.wait(1000/*ms*/);
                        }
                        continue; // nothing to process
                    }

                    CompletableFuture<Void> cmdFuture = new CompletableFuture<>();
                    queueTaskFuture.set(cmdFuture);
                    if(executor == null) executor = Executors.newSingleThreadExecutor();
                    executor.execute(() -> {
                        // don't process consumed commands
                        if (cmd.isConsumed()) {
                            return;
                        }
                        if (cmd.isCancellationRequested()) {
                            cmd.getReply().completeExceptionally(new RuntimeException("Command '" + cmd + "' cancelled."));
                            var onCancel = cmd.getOnHandleCancellationRequest();
                            if (onCancel != null) {
                                try {
                                    onCancel.accept("Cancellation requested via cmd");
                                } catch (Exception ex) {
                                    if (cmd.getOnError() != null) {
                                        cmd.getOnError().accept(cmd.getData(), ex);
                                    } else {
                                        org.tinylog.Logger.debug(ex, "Cannot send command: {}", cmd.getData());
                                    }
                                }
                            }
                        } else {
                            if(cmd.isReplyExpected()) replyQueue.addLast(cmd);

                            T msg = cmd.getData();
                            try {
                                dataConnection.writeData(msg);
                            } catch (IOException e) {
                                replyQueue.remove(cmd);
                                if (cmd.getOnError() != null) {
                                    try {
                                        cmd.getOnError().accept(msg, e);
                                    } catch(Exception ex) {
                                        // exception handled by 'onError'
                                        throw new RuntimeException(e);
                                    }
                                } else {
                                    throw new RuntimeException(e);
                                }
                            }
                        }

                        cmdFuture.complete(null);
                    });

                    if(cmdTimeout==0) {
                        cmdFuture.get();
                    } else {
                        cmdFuture.get(cmdTimeout, TimeUnit.MILLISECONDS);
                    }
                } catch(InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    if(onInterrupted!=null) {
                        onInterrupted.accept(ex);
                    }
                } catch (Throwable e) {
                    org.tinylog.Logger.debug(e, "QUEUE error:");
                }
            }
        });
        queueThread.start();
    }

    /**
     * Closes this controller (also shuts down schedulers/executors).
     */
    @Override
    public void close() {
        try {
            if(queueThread!=null) {
                queueThread.interrupt();
                queueThread = null;
            }
        } finally {
            if(executor == null) return;
            try {
                executor.shutdown();
            } finally {
                executor = null;
            }

            cmdQueue.forEach(cmd -> {
                try {
                    cmd.requestCancellation();
                } catch (Exception ex) {
                    org.tinylog.Logger.debug(ex, "Command cancellation error");
                }
            });

            cmdQueue.clear();

            replyQueue.forEach(cmd -> {
                try {
                    cmd.requestCancellation();
                } catch (Exception ex) {
                    org.tinylog.Logger.debug(ex, "Command cancellation error");
                }
            });

            replyQueue.clear();
        }
    }

    /**
     * Closes this controller. This method blocks until the controller is closed
     * (all tasks have been executed), or the timeout occurs. If no timeout is specified
     * the associated executor will shutdown immediately.
     * @param timeout the timeout in milliseconds
     */
    public boolean close(final long timeout) throws InterruptedException {
        try {
            if(queueThread!=null) {
                queueThread.interrupt();
                queueThread = null;
            }
        } finally {

            cmdQueue.forEach(cmd -> {
                try {
                    cmd.requestCancellation();
                } catch (Exception ex) {
                    org.tinylog.Logger.debug(ex, "Command cancellation error");
                }
            });

            cmdQueue.clear();

            replyQueue.forEach(cmd -> {
                try {
                    cmd.requestCancellation();
                } catch (Exception ex) {
                    org.tinylog.Logger.debug(ex, "Command cancellation error");
                }
            });

            replyQueue.clear();

            if(executor == null) return true;
            try {
                if(timeout == 0) {
                    executor.shutdownNow();
                    return true;
                } else {
                    return executor.awaitTermination(timeout, TimeUnit.MILLISECONDS);
                }
            } finally {
                executor = null;
            }
        }
    }


    /**
     * Sends a command to the device and waits for a reply (blocking).
     *
     * @param cmd command to send
     */
    public Command<T> sendCommand(Command<T> cmd) {
        try {
            sendCommandAsync(cmd).getReply().get();
            return cmd;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            var ex = new RuntimeException("Reply cannot be received", e);
            org.tinylog.Logger.debug(e);
            throw ex;
        } catch (ExecutionException e) {
            var ex = new RuntimeException("Reply cannot be received", e);
            org.tinylog.Logger.debug(e);
            throw ex;
        }
    }

    /**
     * Sends a command to the device and waits for a reply (blocking).
     *
     * @param cmd command to send
     */
    public Command<T> sendCommandAsync(Command<T> cmd) {
        dispatchCmd(cmd);
        return cmd;
    }

    /**
     * Sends the specified data to the device asynchronously.
     * @param msg the message to send
     * @return a future that will be completed when the reply message has been received
     */
    public Command<T> sendCommandAsync(T msg) {
        var command = new Command<T>(msg, null, (m, e)-> {
            String eMsg = "Cannot send command: " + m;
            org.tinylog.Logger.debug(e, eMsg);
            throw new RuntimeException(eMsg, e);
        }, null);
        sendCommandAsync(command);
        return command;
    }

    /**
     * Sends the specified data to the device (blocking).
     * @param msg the message to send
     * @return the reply message
     */
    public T sendCommand(T msg) {
        try {
            return sendCommandAsync(msg).getReply().get(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException|TimeoutException e) {
            var ex = new RuntimeException("Reply cannot be received", e);
            org.tinylog.Logger.debug(e);
            throw ex;
        }
    }

    /**
     * Sends the specified data to the device (no reply expected).
     *
     * @param msg the message to send
     */
    public void sendData(T msg) {
        sendCommand(new Command<T>(msg, null /*no reply expected*/,(m, e)-> {
            org.tinylog.Logger.error(e, "Cannot send data: {}", m);
        },null));
    }

    /**
     * Dispatches a command to the command queue.
     * @param cmd the command to dispatch
     */
    private void dispatchCmd(Command<T> cmd) {
        
        if(queueThread==null) {
            throw new RuntimeException("Not initialized. Please call 'init(...)' first");
        }
        
        cmdQueue.addLast(cmd);

        synchronized (simpleLock) {
            simpleLock.notifyAll();
        }
    }

}
