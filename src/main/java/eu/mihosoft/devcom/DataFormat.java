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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Data format for device communication.
 *
 * @param <T> data type (e.g. String or binary packet)
 */
public interface DataFormat<T> {
    /**
     * Reads data from the specified input stream.
     * @param is input stream for reading the data
     * @return data that has been read from the specified input stream
     * @throws IOException if an I/O error occurs
     */
    T readData(InputStream is) throws IOException;

    /**
     * Writes data to the specified input stream.
     * @param os output stream for writing the data
     * @throws IOException if an I/O error occurs
     */
    void writeData(T data, OutputStream os) throws IOException;

    /**
     * Determines whether the specified data is a reply to the command.
     * @param cmd the command
     * @param replyData potential reply packet
     * @return {@code true} if the data is a reply to the command; {@code false otherwise}
     */
    boolean isReply(Command<T> cmd, T replyData);

    /**
     * Returns a value contained in the data by name, e.g., a packet entry.
     * @param name of the value to access
     * @param data data to access
     * @param <V> data type of the value, e.g., Integer or Boolean.
     * @return the optional value by name (value might not exits)
     */
    default <V> Optional<V> getValueByName(String name, T data) {
        return Optional.empty();
    }
}
