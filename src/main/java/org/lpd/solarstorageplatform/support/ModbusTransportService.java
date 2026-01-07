package org.lpd.solarstorageplatform.support;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class ModbusTransportService {

    private final ModbusConnectionService connectionService;

    public ModbusTransportService(ModbusConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    /**
     * 发送 Modbus 报文并返回详细结果
     */
    public synchronized Map<String, Object> sendFrameDetailed(byte[] frame, String description) {
        Map<String, Object> result = new HashMap<>();

        if (!connectionService.ensureConnected()) {
            result.put("success", false);
            result.put("message", "Modbus 未连接");
            result.put("description", description);
            result.put("request", bytesToHex(frame));
            result.put("response", null);
            return result;
        }

        try {
            OutputStream out = connectionService.getOutputStream();
            InputStream in = connectionService.getInputStream();

            // 发送
            out.write(frame);
            out.flush();

            System.out.print("发送 → ");
            printHex(frame);

            // 接收
            byte[] buffer = new byte[260];
            int len = in.read(buffer);
            if (len <= 0) {
                throw new RuntimeException("无 Modbus 响应");
            }

            byte[] response = new byte[len];
            System.arraycopy(buffer, 0, response, 0, len);

            System.out.print("接收 ← ");
            printHex(response);

            // 简单校验
            boolean valid =
                    response.length >= 8 &&
                    response[0] == frame[0] &&
                    response[1] == frame[1] &&
                    response[7] == frame[7];

            result.put("success", valid);
            result.put("message", valid ? "执行成功" : "响应校验失败");
            result.put("description", description);
            result.put("request", bytesToHex(frame));
            result.put("response", bytesToHex(response));
            return result;

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "通讯异常: " + e.getMessage());
            result.put("description", description);
            result.put("request", bytesToHex(frame));
            result.put("response", null);
            return result;
        }
    }

    /* ================= 工具方法 ================= */

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    private void printHex(byte[] data) {
        for (byte b : data) {
            System.out.printf("%02X ", b);
        }
        System.out.println();
    }


    //demo
    public Map<String, Object> readOne() {
        byte[] request = {
                0x00, 0x00,   // Transaction ID
                0x00, 0x00,   // Protocol ID
                0x00, 0x06,   // Length = 6（不变，因为还是读1个寄存器）
                0x01,         // Unit ID（可按需改）
                0x03,         // Function Code
                0x00, 0x00,   // 寄存器位置
                0x00, 0x01    // 寄存器数量 = 2
        };
        return sendFrameDetailed(request, "null");
    }
}
