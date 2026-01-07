package org.lpd.solarstorageplatform.controller;

import org.lpd.solarstorageplatform.model.ModbusConfigReq;
import org.lpd.solarstorageplatform.model.ModbusConnectionInfo;
import org.lpd.solarstorageplatform.support.ModbusConnectionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/modbus")
public class ModbusConfigController {

    private final ModbusConnectionService connectionService;

    public ModbusConfigController(ModbusConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    /**
     * 前端动态修改 Modbus 连接信息
     */
    @PostMapping("/config")
    public String updateConfig(@RequestBody ModbusConfigReq req) {
        connectionService.updateConfig(req.getIp(), req.getPort());
        return "OK";
    }

    @GetMapping("/status")
    public ModbusConnectionInfo status() {
        return connectionService.getConnectionInfo();
    }
}
