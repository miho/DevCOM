package eu.mihosoft.devcom.vmfmodel;

import eu.mihosoft.vmf.core.DefaultValue;
import eu.mihosoft.vmf.core.ExternalType;
import eu.mihosoft.vmf.core.Immutable;

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
