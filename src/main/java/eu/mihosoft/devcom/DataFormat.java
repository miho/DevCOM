package eu.mihosoft.devcom;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
