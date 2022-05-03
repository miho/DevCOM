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
