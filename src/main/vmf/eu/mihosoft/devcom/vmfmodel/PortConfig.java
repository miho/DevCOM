package eu.mihosoft.devcom.vmfmodel;

import eu.mihosoft.vmf.core.Doc;
import eu.mihosoft.vmf.core.DefaultValue;
import eu.mihosoft.vmf.core.ExternalType;
import eu.mihosoft.vmf.core.Immutable;

import java.util.List;

@ExternalType(pkgName = "eu.mihosoft.devcom")
interface StopBits {}

@ExternalType(pkgName = "eu.mihosoft.devcom")
interface ParityBits {}

@Immutable
interface PortConfig {
    @DefaultValue("\"COM0\"")
    String getName();
    @DefaultValue("8")
    int getNumberOfDataBits();
    @DefaultValue("115200")
    int getBaudRate();
    @DefaultValue("ParityBits.NO_PARITY")
    ParityBits getParityBits();
    @DefaultValue("StopBits.ONE_STOP_BIT")
    StopBits getStopBits();
    @DefaultValue("false")
    boolean isRS485ModeEnabled();

    @DefaultValue("500")
    int getSafetyTimeout();

    @DefaultValue("0")
    int getWriteTimeout();

}

@Immutable
interface DeviceInfo {
    String getDeviceClass();
    String getDevice();
    String getMCUType();
    String getSerialNumber();
}

@Doc("Port event.")
@Immutable()
interface PortEvent {
    @Doc("Timestamp (milliseconds since January 1st, 1970).")
    long getTimestamp();

    @Doc("Names of ports added since last scanning.")
    String[] getAdded();

    @Doc("Names of ports removed since last scanning.")
    String[] getRemoved();
}
