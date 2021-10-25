package eu.mihosoft.devcom;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A command for sending and receiving data to/from a device.
 *
 * @param <T> the data type, e.g. String or Packet.
 */
public final class Command<T> {

    // final/immutable
    private final T data;
    private final Consumer<T> onSent;
    private final Consumer<T> onReceived;
    private final Boolean replyExpected;
    private final BiConsumer<T, Exception> onError;
    private final Consumer<String> onCancellationRequested;

    // non-final/mutable
    private volatile CompletableFuture<T> reply;
    private volatile boolean consumed;
    private volatile boolean cancellationRequested;

    /**
     * Creates a new command.
     * @param data data to send
     * @param onSent consumer to call if a data has been sent
     * @param replyExpected predicate that indicates whether a reply is expected
     * @param onReceived consumer to call if a response has been received
     * @param onError consumer to call if an error occurs
     * @param onCancellationRequested consumer called if cancellation has been requested
     */
    public Command(
        T data,
        Consumer<T> onSent,
        Boolean replyExpected,
        Consumer<T> onReceived,
        BiConsumer<T, Exception> onError,
        Consumer<String> onCancellationRequested) {
        this.data = data;
        this.onSent = onSent;
        this.onReceived = onReceived;
        this.replyExpected = replyExpected;
        this.reply = null;
        this.onError = onError;
        this.onCancellationRequested = onCancellationRequested;
    }

    // TODO 25.10.2021 would work great but needs more thoughts on identity checks and collections
//    /**
//     * Resets this command for being reused for sending and receiving data.
//     */
//    public void reset() {
//        this.consumed = false;
//        var r = this.reply;
//        if(r!=null) {
//            r.completeExceptionally(new RuntimeException("Reset"));
//        }
//        this.reply = null;
//    }

    /**
     * Returns a new Command builder.
     * @param <T>
     * @return a new Command builder
     */
    public static <T> Builder<T> newBuilder() {
        return Builder.newBuilder();
    }

    /**
     * Command builder.
     *
     * @param <T>
     */
    public static final class Builder<T> {
        private T data;
        private Consumer<T> onSent;
        private Consumer<T> onReceived;
        private Boolean replyExpected;
        private BiConsumer<T, Exception> onError;
        private Consumer<String> onCancellationRequested;

        private Builder() {

        }

        static <T> Builder<T> newBuilder() {
            return new Builder<>();
        }

        /**
         *
         * @param data data to be sent by the command
         * @return this builder
         */
        public Builder<T> withData(T data) {
            this.data = data;
            return this;
        }

        /**
         *
         * @param onSent called if/when the data has been sent (may be {@code null}).
         * @return this builder
         */
        public Builder<T> withOnSent(Consumer<T> onSent) {
            this.onSent = onSent;
            return this;
        }

        /**
         *
         * @param onReceived called if/when the data has been received (may be {@code null}).
         * @return this builder
         */
        public Builder<T> withOnReceived(Consumer<T> onReceived) {
            this.onReceived = onReceived;
            return this;
        }

        /**
         *
         * @param replyExpected defines whether a reply is expected.
         * @return this builder
         */
        public Builder<T> withReplyExpected(boolean replyExpected) {
            this.replyExpected = replyExpected;
            return this;
        }

        /**
         *
         * @param onError error handler is called whenever an error occurs (may be {@code null}).
         * @return this builder
         */
        public Builder<T> withOnError(BiConsumer<T, Exception> onError) {
            this.onError = onError;

            return this;
        }


        /**
         *
         * @param onCancellationRequested handler is called if cancellation is requested (may be {@code null}).
         * @return this builder
         */
        public Builder<T> withOnCancellationRequested(Consumer<String> onCancellationRequested) {
            this.onCancellationRequested = onCancellationRequested;

            return this;
        }

        /**
         * Creates a new command.
         * @return new command
         */
        public Command<T> build() {
            return new Command<>(data,onSent, replyExpected, onReceived, onError, onCancellationRequested);
        }

    }

    /**
     * Indicates whether a reply is expected by this command.
     * @return {@code true} if a reply is expected; {@code false} otherwise
     */
    public boolean isReplyExpected() {
        return replyExpected==null?true:replyExpected;
    }

    /**
     * Returns the future reply.
     * @return reply
     */
    public CompletableFuture<T> getReply() {

        synchronized (this) {
            if (reply == null) {
                reply = new CompletableFuture<>();
            }
        }

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

    /*pkg private*/ Consumer<T> getOnReceived() {
        return onReceived;
    }
    /*pkg private*/ Consumer<T> getOnSent() {
        return onSent;
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
