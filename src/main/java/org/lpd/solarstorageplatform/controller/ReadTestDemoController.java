package org.lpd.solarstorageplatform.controller;


import jakarta.annotation.Resource;
import org.lpd.solarstorageplatform.support.ModbusTransportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/demo/read")
@CrossOrigin(origins = "*") // 允许所有来源跨域（仅限开发环境！）
public class ReadTestDemoController {


    @Resource
    private ModbusTransportService modbusDCDC5Service;


    @GetMapping("/read1")
    public ResponseEntity<Map<String, Object>> read1() {
        Map<String, Object> result = modbusDCDC5Service.readOne();
        return ResponseEntity.ok(result);
    }








}