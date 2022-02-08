package eu.mihosoft.devcom;

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
    private volatile ExecutorService cmdExecutor;
    private volatile ExecutorService executor;
    /*pkg private*/ final AtomicReference<CompletableFuture<Void>> queueTaskFuture = new AtomicReference<>();
    private volatile Thread queueThread;
    private volatile long cmdTimeout = 0/*no timeout, unit: ms*/;
    private volatile long dataTimeout = 0/*no timeout, unit: ms*/;
    private volatile Consumer<InterruptedException> onInterrupted;

    /**
     * Creates a new controller instance.
     */
    public Controller() {
        this.cmdExecutor = Executors.newSingleThreadExecutor();
        this.executor = Executors.newCachedThreadPool();
    }

    /**
     * Creates a new controller instance.
     * @param onInterrupted the consumer to call if the command queue thread is interrupted (may be null)
     */
    public Controller(Consumer<InterruptedException> onInterrupted) {
        setOnInterrupted(onInterrupted);
        this.cmdExecutor = Executors.newSingleThreadExecutor();
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
     * Sets the data timeout (until the command is sent).
     * @param milliseconds duration in milliseconds (0 means no timeout)
     * @return this controller
     */
    public Controller<T,V> setDataTimeout(long milliseconds) {
        this.dataTimeout = milliseconds;
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
            replyQueue.stream()
                .filter(c -> dataConnection.getFormat().isReply(c, msg)).findFirst().ifPresent( cmd -> {
                replyQueue.removeFirstOccurrence(cmd);
                cmd.getReply().complete(msg);
                if(cmd.getOnReceived()!=null) {
                    cmd.getOnReceived().accept(msg);
                }
            });
        };

        dataConnection.setOnDataReceived(onDataReceived);

        dataConnection.setOnIOError((conn, e1) -> {
            // find first element that matches reply
            replyQueue.stream().filter(cmd->cmd!=null).
                forEach(cmd->{
                    cmd.requestCancellation();
                    cmd.getReply().completeExceptionally(new RuntimeException("Cancelling. I/O error occurred.", e1));
                });
            replyQueue.clear();
        });


        dataConnection.setOnConnectionClosed(o -> {
            replyQueue.stream().filter(cmd->cmd!=null).
                forEach(cmd->{
                    cmd.requestCancellation();
                    cmd.getReply().completeExceptionally(new RuntimeException("Cancelling. Connection closed."));
                });
            replyQueue.clear();
        });

        if(queueThread!=null) {
            queueThread.interrupt();
        }

        queueThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Command<T> cmdImmutable = null;
                try {
                    var cmd = cmdQueue.pollFirst();
                    cmdImmutable = cmd;
                    if (cmd == null) {
                        synchronized (simpleLock) {
                            simpleLock.wait(1000/*ms*/);
                        }
                        continue; // nothing to process
                    }

                    CompletableFuture<Void> cmdFuture = new CompletableFuture<>();
                    queueTaskFuture.set(cmdFuture);
                    if (cmdExecutor == null) cmdExecutor = Executors.newSingleThreadExecutor();
                    cmdExecutor.execute(() -> {
                        // don't process consumed commands
                        if (cmd.isConsumed()) {
                            return;
                        }
                        if (cmd.isCancellationRequested()) {
                            cmd.getReply().completeExceptionally(
                                new RuntimeException("Command '" + cmd + "' cancelled.")
                            );
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
                            if (cmd.isReplyExpected()) {
                                replyQueue.addLast(cmd);
                                // ensure result is invalidated if timeout exceeded
                                if (executor == null) executor = Executors.newCachedThreadPool();
                                CompletableFuture.delayedExecutor(cmdTimeout, TimeUnit.MILLISECONDS, executor)
                                    .execute(()->{
                                    if(cmd.getReply().isDone()||cmd.getReply().isCancelled()) return;

                                    cmd.getReply().completeExceptionally(new TimeoutException());
                                    replyQueue.removeFirstOccurrence(cmd);
                                });
                            } else {
                                cmd.getReply().complete(null);
                            }

                            T msg = cmd.getData();
                            try {
                                dataConnection.writeData(msg);
                                cmd.consume();
                                if (cmd.getOnSent() != null) {
                                    try {
                                        cmd.getOnSent().accept(msg);
                                    } catch (Exception ex) {
                                        // exception handled by 'onError'
                                        throw new RuntimeException(ex);
                                    }
                                }
                            } catch (Exception e) {
                                replyQueue.remove(cmd);
                                cmdFuture.completeExceptionally(e);
                                if (cmd.isReplyExpected()) cmd.getReply().completeExceptionally(e);
                                if (cmd.getOnError() != null) {
                                    try {
                                        cmd.getOnError().accept(msg, e);
                                    } catch (Exception ex) {
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

                    if (cmdTimeout == 0) {
                        cmdFuture.get();
                        cmdImmutable = null;
                    } else {
                        cmdFuture.get(cmdTimeout, TimeUnit.MILLISECONDS);
                        cmdImmutable = null;
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    if (onInterrupted != null) {
                        onInterrupted.accept(ex);
                    }
                    if(cmdImmutable!=null) cmdImmutable.getReply().completeExceptionally(ex);
                } catch(TimeoutException ex) {
                    if(cmdImmutable!=null) cmdImmutable.getReply().completeExceptionally(ex);
                } catch (Throwable ex) {
                    if(cmdImmutable!=null) cmdImmutable.getReply().completeExceptionally(ex);
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

            try {
                if (cmdExecutor == null) return;
                try {
                    cmdExecutor.shutdown();
                } finally {
                    cmdExecutor = null;
                }
            } finally {
                if(executor == null) return;
                try {
                    executor.shutdown();
                } finally {
                    executor = null;
                }
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
                    cmd.getReply().completeExceptionally(
                        new RuntimeException("Cancellation requested. Controller shutdown.")
                    );
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
                    cmd.getReply().completeExceptionally(
                        new RuntimeException("Cancellation requested. Controller shutdown.")
                    );
                } catch (Exception ex) {
                    org.tinylog.Logger.debug(ex, "Command cancellation error");
                }
            });

            replyQueue.clear();

            try {
                if (cmdExecutor == null) return true;
                try {
                    if (timeout == 0) {
                        cmdExecutor.shutdownNow();
                        return true;
                    } else {
                        return cmdExecutor.awaitTermination(timeout, TimeUnit.MILLISECONDS);
                    }
                } finally {
                    cmdExecutor = null;
                }
            } finally {
                if (executor == null) return true;
                try {
                    if (timeout == 0) {
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
            throw ex;
        } catch (ExecutionException e) {
            var ex = new RuntimeException("Reply cannot be received", e);
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
        var command = new Command<T>(msg, null, null,null, (m, e)-> {
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
            if(cmdTimeout>0) {
                return sendCommandAsync(msg).getReply().get(cmdTimeout, TimeUnit.MILLISECONDS);
            } else {
                return sendCommandAsync(msg).getReply().get();
            }
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
     * @return a future that will be completed when the data has been sent
     */
    public CompletableFuture<T> sendDataAsync(T msg) {
        CompletableFuture<T> sentF = new CompletableFuture<>();
        sendCommand(new Command<T>(msg, (m)->{sentF.complete(m);}, false,
            null /*no reply expected*/,(m, e)-> {
                sentF.completeExceptionally(e);
        },null));
        return sentF;
    }

    /**
     * Sends the specified data to the device (no reply expected). This method blocks until the data has been sent.
     *
     * @param msg the message to send
     */
    public void sendData(T msg) {
        CompletableFuture<T> sentF = new CompletableFuture<>();
        sendCommand(new Command<T>(msg, (m)->{sentF.complete(m);}, false,
            null /*no reply expected*/,(m, e)-> {
            sentF.completeExceptionally(e);
        },null));
        try {
            if(dataTimeout>0) {
                sentF.get(dataTimeout, TimeUnit.MILLISECONDS);
            } else {
                sentF.get();
            }
        } catch (InterruptedException | ExecutionException|TimeoutException e) {
            var ex = new RuntimeException("Data cannot be sent", e);
            throw ex;
        }
    }

    /**
     * Dispatches a command to the command queue.
     * @param cmd the command to dispatch
     */
    private void dispatchCmd(Command<T> cmd) {
        
        if(queueThread==null) {
            throw new RuntimeException("Not initialized. Please call 'init(...)' first.");
        }

        if(cmd.isConsumed()) {
            throw new RuntimeException("Command already consumed. Please call 'reset()' first or use a fresh command instance.");
        }
        
        cmdQueue.addLast(cmd);

        synchronized (simpleLock) {
            simpleLock.notifyAll();
        }
    }

}
