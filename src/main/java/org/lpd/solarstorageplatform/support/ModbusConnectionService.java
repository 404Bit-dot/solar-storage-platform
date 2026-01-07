package org.lpd.solarstorageplatform.support;

import org.lpd.solarstorageplatform.model.ModbusConnectionInfo;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

@Service
public class ModbusConnectionService {

    /** 当前配置（前端可修改） */
    private volatile String ipAddress = "192.168.1.35";
    private volatile int port = 502;

    /** Socket 资源 */
    private Socket socket;
    private OutputStream out;
    private InputStream in;

    @PostConstruct
    public void init() {
        System.out.println("ModbusConnectionService 初始化完成，等待配置连接");
    }

    /**
     * 前端更新 IP / 端口
     */
    public synchronized void updateConfig(String ip, int port) {
        validateConfig(ip, port);

        boolean changed =
                !this.ipAddress.equals(ip) || this.port != port;

        if (!changed) {
            return;
        }

        closeInternal();

        this.ipAddress = ip;
        this.port = port;

        connect();
    }

    /**
     * 建立 TCP 连接
     */
    public synchronized boolean connect() {
        closeInternal(); // ⭐ 关键：防止脏资源

        try {
            Socket newSocket = new Socket();
            newSocket.connect(new InetSocketAddress(ipAddress, port), 3000);
            newSocket.setSoTimeout(3000);

            this.socket = newSocket;
            this.out = newSocket.getOutputStream();
            this.in = newSocket.getInputStream();

            System.out.println("Modbus 已连接 " + ipAddress + ":" + port);
            return true;
        } catch (Exception e) {
            System.err.println("Modbus 连接失败: " + e.getMessage());
            closeInternal();
            return false;
        }
    }

    /**
     * 确保连接可用（业务调用前）
     */
    public synchronized boolean ensureConnected() {
        if (isConnected()) {
            return true;
        }
        return connect();
    }

    /**
     * 当前是否已建立 TCP 连接（展示级）
     */
    public synchronized boolean isConnected() {
        return socket != null
                && socket.isConnected()
                && !socket.isClosed();
    }

    /**
     * 获取输出流（调用前务必 ensureConnected）
     */
    public synchronized OutputStream getOutputStream() {
        if (!isConnected()) {
            throw new IllegalStateException("Modbus 未连接");
        }
        return out;
    }

    /**
     * 获取输入流
     */
    public synchronized InputStream getInputStream() {
        if (!isConnected()) {
            throw new IllegalStateException("Modbus 未连接");
        }
        return in;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }


    /**
     * 统一返回连接信息
     */
    public synchronized ModbusConnectionInfo getConnectionInfo() {
        ModbusConnectionInfo info = new ModbusConnectionInfo();
        info.setIp(ipAddress);
        info.setPort(port);
        info.setConnected(isConnected());
        return info;
    }

    /**
     * 参数校验
     */
    private void validateConfig(String ip, int port) {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("IP 地址不能为空");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("端口号非法: " + port);
        }
    }

    /**
     * 内部关闭连接
     */
    private void closeInternal() {
        try {
            if (in != null) in.close();
        } catch (Exception ignored) {}

        try {
            if (out != null) out.close();
        } catch (Exception ignored) {}

        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}

        socket = null;
        in = null;
        out = null;
    }

    @PreDestroy
    public synchronized void close() {
        closeInternal();
        System.out.println("Modbus 连接已关闭");
    }
}
