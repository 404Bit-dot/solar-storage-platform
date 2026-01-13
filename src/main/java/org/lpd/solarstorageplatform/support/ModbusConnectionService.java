package org.lpd.solarstorageplatform.support;

import lombok.extern.slf4j.Slf4j;
import org.lpd.solarstorageplatform.model.ModbusConnectionInfo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

@Slf4j
@Service
public class ModbusConnectionService {

    /** 当前配置（前端可修改） */
    private volatile String ipAddress = "192.168.1.35";
    private volatile int port = 502;

    /** Socket 资源 */
    private Socket socket;
    private OutputStream out;
    private InputStream in;

    /** IO 串行锁（Modbus TCP 必须串行） */
    private final Object ioLock = new Object();

    @PostConstruct
    public void init() {
        log.info("ModbusConnectionService 初始化完成，等待连接配置");
    }

    /**
     * 建立 TCP 连接
     */
    public synchronized boolean connect() {
        closeInternal(); // 防止脏连接

        try {
            Socket newSocket = new Socket();
            newSocket.connect(new InetSocketAddress(ipAddress, port), 3000);
            newSocket.setSoTimeout(5000);

            this.socket = newSocket;
            this.out = newSocket.getOutputStream();
            this.in = newSocket.getInputStream();

            log.info("Modbus 已连接 {}:{}", ipAddress, port);
            return true;
        } catch (Exception e) {
            log.error("Modbus 连接失败 {}:{} - {}", ipAddress, port, e.getMessage());
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
        log.warn("Modbus 当前未连接，尝试重连");
        return connect();
    }

    /**
     * 当前是否已建立 TCP 连接（展示 + 参考级）
     */
    public synchronized boolean isConnected() {
        return socket != null
                && socket.isConnected()
                && !socket.isClosed()
                && !socket.isInputShutdown()
                && !socket.isOutputShutdown();
    }

    /**
     * 获取输出流（调用前 ensureConnected）
     */
    public OutputStream getOutputStream() {
        if (!isConnected()) {
            throw new IllegalStateException("Modbus 未连接");
        }
        return out;
    }

    /**
     * 获取输入流
     */
    public InputStream getInputStream() {
        if (!isConnected()) {
            throw new IllegalStateException("Modbus 未连接");
        }
        return in;
    }

    /**
     * 统一返回连接信息（给前端）
     */
    public synchronized ModbusConnectionInfo getConnectionInfo() {
        ModbusConnectionInfo info = new ModbusConnectionInfo();
        info.setIp(ipAddress);
        info.setPort(port);
        info.setConnected(isConnected());
        return info;
    }

    /**
     * 前端更新 IP / 端口
     */
    public synchronized void updateConfig(String ip, int port) {
        validateConfig(ip, port);

        boolean changed =
                !this.ipAddress.equals(ip) || this.port != port;

        if (!changed) {
            log.info("Modbus 配置未变化，忽略更新");
            return;
        }

        log.info("Modbus 配置变更 {}:{} → {}:{}", this.ipAddress, this.port, ip, port);

        closeInternal();
        this.ipAddress = ip;
        this.port = port;

        connect();
    }

    /**
     * 心跳检测（每 10 秒）
     * 使用一次最小 Modbus TCP 报文尝试
     */
    @Scheduled(fixedDelay = 10_000)
    public void heartbeat() {
        if (!ensureConnected()) {
            log.warn("Modbus 心跳：连接不可用");
            return;
        }

        synchronized (ioLock) {
            try {
                // 最小 Modbus TCP 报文（示例：事务ID=1，读0寄存器）
                byte[] heartbeat = new byte[]{
                        0x00, 0x01, // Transaction ID
                        0x00, 0x00, // Protocol ID
                        0x00, 0x06, // Length
                        0x01,       // Unit ID
                        0x03,       // Function Code（读保持寄存器）
                        0x00, 0x00, // 起始地址
                        0x00, 0x01  // 数量
                };

                out.write(heartbeat);
                out.flush();

                // 只要能读到数据，就认为连接是活的
                byte[] respHeader = new byte[7];
                int read = in.read(respHeader);

                if (read <= 0) {
                    throw new RuntimeException("心跳无响应");
                }

                log.debug("Modbus 心跳成功");

            } catch (Exception e) {
                log.warn("Modbus 心跳失败，连接将重置: {}", e.getMessage());
                close();
            }
        }
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
        } catch (Exception e) {
            log.debug("关闭 InputStream 异常", e);
        }

        try {
            if (out != null) out.close();
        } catch (Exception e) {
            log.debug("关闭 OutputStream 异常", e);
        }

        try {
            if (socket != null) socket.close();
        } catch (Exception e) {
            log.debug("关闭 Socket 异常", e);
        }

        socket = null;
        in = null;
        out = null;
    }

    @PreDestroy
    public synchronized void close() {
        closeInternal();
        log.info("Modbus 连接已关闭");
    }
}
