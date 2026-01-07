package org.lpd.solarstorageplatform.model;


import lombok.Data;

@Data
public class ModbusConfigReq {
    private String ip;
    private int port;

    // getter / setter
}
