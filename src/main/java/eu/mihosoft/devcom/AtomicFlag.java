package eu.mihosoft.devcom;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntBinaryOperator;

/**
 * Flag wrap for atomically flipping a boolean value.
 */
public final class AtomicFlag {
    private final AtomicBoolean flag;

    /**
     * Creates a new atomic flag.
     */
    public AtomicFlag() {
        this(false);
    }

    /**
     * Creates a new atomic flag.
     *
     * @param initialValue the initial value of the boolean
     */
    public AtomicFlag(boolean initialValue) {
        flag = new AtomicBoolean(initialValue);
    }

    /**
     * Creates a new atomic flag.
     *
     * @param atomicBoolean the atomic boolean to wrap
     */
    public AtomicFlag(AtomicBoolean atomicBoolean) {
        flag = atomicBoolean;
    }

    /**
     * Flip the wrapped AtomicBoolean.
     *
     * It sets the boolean value to false if it is true, and to true if it is false
     * with memory effects as specified by {@link java.lang.invoke.VarHandle#setVolatile}.
     *
     * @return new boolean value of AtomicBoolean
     * @see AtomicInteger#accumulateAndGet(int x, IntBinaryOperator accumulatorFunction)
     */
    public boolean negate() {
        boolean prev = flag.get(), next = false;
        for (boolean haveNext = false; ; ) {
            if (!haveNext) {
                next = !prev;
            }
            if (flag.weakCompareAndSetVolatile(prev, next)) {
                return next;
            }
            haveNext = (prev == (prev = flag.get()));
        }
    }
}
