package eu.mihosoft.devcom;

import com.fazecast.jSerialComm.SerialPort;

/**
 * Stop bit configurations that are supported by the underlying COM port library.
 */
public enum StopBits {

    ONE_STOP_BIT(SerialPort.ONE_STOP_BIT),
    TWO_STOP_BITS(SerialPort.TWO_STOP_BITS);


    private StopBits(int value) {
        this.value = value;
    }

    /*pkg private */ int getValue() {
        return this.value;
    }

    private int value;
}
