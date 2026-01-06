package org.lpd.solarstorageplatform.support;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;


@Service
public class ModbusSupportService {

    private final String ipAddress = "192.168.1.35";
    private final int port = 502;
    private Socket socket;
    private OutputStream out;
    private InputStream in;
    private int transactionId = 1;
    private long lastHeartbeat = 0;
    private static final long HEARTBEAT_INTERVAL = 30000; // 30秒


    @PostConstruct
    public void init() {
        connect();
    }

    private synchronized void connect() {
        try {
            socket = new Socket(ipAddress, port);
            socket.setSoTimeout(3000);
            out = socket.getOutputStream();
            in = socket.getInputStream();
            lastHeartbeat = System.currentTimeMillis();
            System.out.println("Storage已连接到 Modbus 设备 " + ipAddress + ":" + port);
        } catch (Exception e) {
            System.err.println("连接失败: " + e.getMessage());
            socket = null;
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("Modbus 连接已关闭");
            }
        } catch (Exception e) {
            System.err.println("关闭连接异常: " + e.getMessage());
        }
    }
    private synchronized int nextTid() {
        return transactionId++ & 0xFFFF;
    }

    /**
     * byte数组转16进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim().replace(" ", " ");
    }

    /**
     * 检查连接健康状态（包含心跳）
     */
    public synchronized boolean isHealthy() {
        if (socket == null || socket.isClosed()) {
            return false;
        }

        if (System.currentTimeMillis() - lastHeartbeat > HEARTBEAT_INTERVAL) {
            return pingDevice();
        }

        return true;
    }

    /**
     * 心跳检测
     */
    private boolean pingDevice() {
        try {
            int tid = nextTid();
            byte[] heartbeatFrame = {
                    (byte) (tid >> 8), (byte) tid,
                    0x00, 0x00,
                    0x00, 0x06,
                    0x01, 0x01, 0x00, 0x00, 0x00, 0x01
            };

            out.write(heartbeatFrame);
            out.flush();

            byte[] buffer = new byte[260];
            int len = in.read(buffer);
            lastHeartbeat = System.currentTimeMillis();

            return len > 0;
        } catch (Exception e) {
            try { socket.close(); } catch (Exception ignored) {}
            socket = null;
            return false;
        }
    }

    /**
     * 发送消息返回详细的执行结果
     */

    public synchronized Map<String, Object> sendFrameDetailed(byte[] frame, String description) {
        Map<String, Object> result = new HashMap<>();
        // 1. 健康检查
        if (!isHealthy()) {
            System.out.println("连接不健康，尝试重连...");
            connect();
            if (!isHealthy()) {
                result.put("success", false);
                result.put("message", "重连失败");
                result.put("description", description);
                result.put("request", bytesToHex(frame));
                result.put("response", null);
                return result;
            }
        }
        try {
            out.write(frame);
            out.flush();
            lastHeartbeat = System.currentTimeMillis();
//            System.out.println("执行：" + description);
            System.out.print("发送 → ");
            for (byte b : frame) System.out.printf("%02X ", b);
            System.out.println();
            byte[] buffer = new byte[260];
            int len = in.read(buffer);
            String requestHex = bytesToHex(frame);
            String responseHex = null;
            if (len > 0) {
                byte[] response = new byte[len];
                System.arraycopy(buffer, 0, response, 0, len);
                responseHex = bytesToHex(response);
                System.out.print("接收 ← ");
                for (int i = 0; i < len; i++) System.out.printf("%02X ", buffer[i]);
                System.out.println();
                // 检查响应是否有效（事务ID匹配 + 功能码正常）
                boolean validResponse = len >= 8 &&
                        frame[0] == response[0] && frame[1] == response[1] &&
                        response[7] == frame[7]; // 功能码匹配
                result.put("success", validResponse);
                result.put("message", description + " → 执行成功");
            } else {
                System.out.println("无响应");
                result.put("success", false);
                result.put("message", description + " → 无响应");
            }
            result.put("description", description);
            result.put("request", requestHex);
            result.put("response", responseHex);
            return result;
        } catch (Exception e) {
            System.err.println("通讯异常: " + e.getMessage());
            try { socket.close(); } catch (Exception ignored) {}
            socket = null;
            result.put("success", false);
            result.put("message", description + " → 通讯异常: " + e.getMessage());
            result.put("description", description);
            result.put("request", bytesToHex(frame));
            result.put("response", null);
            return result;
        }
    }

    public boolean isConnected() {
        return isHealthy();
    }



    // 读取14光伏表
    public Map<String, Object> read14() {
        byte[] request = {
                0x00, 0x00,   // Transaction ID
                0x00, 0x00,   // Protocol ID
                0x00, 0x06,   // Length = 6（不变，因为还是读1个寄存器）
                0x01,         // Unit ID（可按需改）
                0x03,         // Function Code
                0x02, (byte) 0x2C, // 744
                0x00, 0x04    // 寄存器数量 = 4
        };
        return sendFrameDetailed(request, "null");
    }


    // 测试14
    public Map<String, Object> test14() {
        byte[] request = {
                0x00, 0x00,   // Transaction ID
                0x00, 0x00,   // Protocol ID
                0x00, 0x06,   // Length = 6（不变，因为还是读1个寄存器）
                0x01,         // Unit ID（可按需改）
                0x03,         // Function Code
                0x02, 0x2c,
                0x00, 0x01    // 寄存器数量 = 1
        };
        return sendFrameDetailed(request, "null");
    }


    // 读取Na 6
    public Map<String, Object> readNa6() {
        byte[] request = {
                0x00, 0x00,   // Transaction ID
                0x00, 0x00,   // Protocol ID
                0x00, 0x06,   // Length = 6（不变，因为还是读1个寄存器）
                0x01,         // Unit ID（可按需改）
                0x03,         // Function Code
                0x02, 0x4C,   // 读取 589 590 的总电压和电流消息
                0x00, 0x02    // 寄存器数量 = 2
        };
        return sendFrameDetailed(request, "null");
    }






}