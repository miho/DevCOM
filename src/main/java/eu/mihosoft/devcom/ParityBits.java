package eu.mihosoft.devcom;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Parity bit configurations that are supported by the underlying COM port library.
 */
public enum ParityBits {

    NO_PARITY(SerialPort.NO_PARITY),
    EVEN_PARITY(SerialPort.EVEN_PARITY),
    ODD_PARITY(SerialPort.ODD_PARITY),
    MARK_PARITY(SerialPort.MARK_PARITY),
    SPACE_PARITY(SerialPort.SPACE_PARITY);

    private ParityBits(int value) {
        this.value = value;
    }

    private int value;

    int getValue() {
        return this.value;
    }

}
