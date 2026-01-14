package org.lpd.solarstorageplatform.support.task;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.lpd.solarstorageplatform.support.ModbusTransportService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class taskTwo {

    @Resource
    private ModbusTransportService modbusTransportService;

    @Resource
    private ScheduledTaskLogger taskLogger;


    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public Map<String, Object> readTwo() {
        taskLogger.logStart("demo2");

        byte[] request = {
                0x00, 0x00,   // Transaction ID
                0x00, 0x00,   // Protocol ID
                0x00, 0x06,   // Length = 6（不变，因为还是读1个寄存器）
                0x01,         // Unit ID（可按需改）
                0x03,         // Function Code
                0x00, 0x00,   // 寄存器位置
                0x00, 0x01    // 寄存器数量 = 2
        };
        return modbusTransportService.sendFrameDetailed(request, "null");
    }
}
