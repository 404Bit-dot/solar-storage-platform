package org.lpd.solarstorageplatform.model;

import lombok.Data;

@Data
public class ModbusConnectionInfo {

    private String ip;
    private int port;
    private boolean connected;
}
