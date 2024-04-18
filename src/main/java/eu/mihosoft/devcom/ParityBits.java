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
        DevCOM.checkInit();
        this.value = value;
    }

    private final int value;

    public int getValue() {
        return this.value;
    }

}
