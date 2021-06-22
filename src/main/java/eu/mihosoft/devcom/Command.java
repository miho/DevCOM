package eu.mihosoft.devcom;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A command for sending and receiving data to/from a device.
 *
 * @param <T> the data type, e.g. String or Packet.
 */
public class Command<T> {

    private final T data;
    private final Consumer<T> onResponse;
    private final CompletableFuture<T> reply;
    private final BiConsumer<T, Exception> onError;
    private final Consumer<String> onCancellationRequested;
    private volatile boolean consumed;
    private volatile boolean cancellationRequested;

    /**
     * Creates a new command.
     * @param data data to send
     * @param onResponse consumer to call if a response has been received
     * @param onError consumer to call if an error occurs
     * @param onCancellationRequested consumer called if cancellation has been requested
     */
    public Command(T data, Consumer<T> onResponse, BiConsumer<T, Exception> onError, Consumer<String> onCancellationRequested) {
        this.data = data;
        this.onResponse = onResponse;
        this.reply = new CompletableFuture<>();
        this.onError = onError;
        this.onCancellationRequested = onCancellationRequested;
    }

    /**
     * Indicates whether a reply is expected by this command.
     * @return {@code true} if a reply is expected; {@code false} otherwise
     */
    public boolean isReplyExpected() {
        return true;
    }

    /**
     * Returns the future reply.
     * @return reply
     */
    public CompletableFuture<T> getReply() {
        return reply;
    }

    /**
     * Returns the data to send.
     * @return the data to send
     */
    public T getData() {
        return data;
    }
    

    /**
     * Indicates whether this command has been consumed.
     * @return {@code if this command has been consumed}; {@code false} otherwise
     */
    public boolean isConsumed() {
        return consumed;
    }

    /**
     * Requests command cancellation.
     */
    public void requestCancellation() {
        this.cancellationRequested = true;
    }

    /**
     * Indicates whether cancellation has been requested.
     * @return {@code if cancellation has been requested}; {@code false} otherwise
     */
    public boolean isCancellationRequested() {
        return cancellationRequested;
    }

    // ---------------------------------------------------------
    // PRIVATE METHODS
    // ---------------------------------------------------------

    /*pkg private*/ Consumer<T> getOnResponse() {
        return onResponse;
    }

    /*pkg private*/ Consumer<String> getOnHandleCancellationRequest() {
        return onCancellationRequested;
    }

    /*pkg private*/ BiConsumer<T, Exception> getOnError() {
        return onError;
    }

    /*pkg private*/ void consume() {
        this.consumed = true;
    }

    @Override
    public String toString() {
        return "[cmd: " + "data=" + (data==null?"<no data>":data) + "]";
    }
}
