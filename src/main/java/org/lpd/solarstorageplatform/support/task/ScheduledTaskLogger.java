package org.lpd.solarstorageplatform.support.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class ScheduledTaskLogger {

    private final AtomicInteger executionCounter = new AtomicInteger(0);

    // 每次有定时任务执行前调用
    public void logStart(String taskName) {
        int order = executionCounter.incrementAndGet();
        log.info("定时任务执行顺序 #{}, 任务: {}, 线程: {}, 时间: {}", 
                 order, taskName, Thread.currentThread().getName(), LocalDateTime.now());
    }

    // 可选：在任务结束时重置（如果想每个调度周期清零）
    // public void reset() { executionCounter.set(0); }
}