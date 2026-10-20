package com.cbms.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.*;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.*;

@Service
public class AdminMonitoringService {
    private final DataSource dataSource;
    private final String logPath;
    private final long startedAt = ManagementFactory.getRuntimeMXBean().getStartTime();

    public AdminMonitoringService(DataSource dataSource, @Value("${app.monitoring.log-path:logs/cbms.log}") String logPath) {
        this.dataSource = dataSource;
        this.logPath = logPath;
    }

    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("health", health());
        result.put("resources", resources());
        result.put("database", database());
        result.put("alerts", alerts());
        return result;
    }

    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("checkedAt", Instant.now().toString());
        result.put("startedAt", Instant.ofEpochMilli(startedAt).toString());
        result.put("uptimeMillis", System.currentTimeMillis() - startedAt);
        return result;
    }

    public Map<String, Object> resources() {
        Map<String, Object> result = new LinkedHashMap<>();
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        result.put("heapUsedBytes", heap.getUsed());
        result.put("heapMaxBytes", heap.getMax());
        result.put("nonHeapUsedBytes", nonHeap.getUsed());
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        result.put("threadCount", threads.getThreadCount());
        result.put("peakThreadCount", threads.getPeakThreadCount());
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        result.put("availableProcessors", os.getAvailableProcessors());
        result.put("systemLoadAverage", os.getSystemLoadAverage());
        if (os instanceof com.sun.management.OperatingSystemMXBean extended) {
            result.put("processCpuLoad", extended.getProcessCpuLoad());
            result.put("systemCpuLoad", extended.getCpuLoad());
        }
        try {
            Path log = Path.of(logPath).toAbsolutePath().normalize();
            Path directory = log.getParent();
            FileStore store = Files.getFileStore(Files.exists(directory) ? directory : Path.of(".").toAbsolutePath());
            result.put("diskUsableBytes", store.getUsableSpace());
            result.put("diskTotalBytes", store.getTotalSpace());
            result.put("logFileBytes", Files.isRegularFile(log) ? Files.size(log) : 0L);
        } catch (Exception ex) {
            result.put("diskStatus", "UNAVAILABLE");
        }
        return result;
    }

    public Map<String, Object> database() {
        Map<String, Object> result = new LinkedHashMap<>();
        long started = System.nanoTime();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next() || rs.getInt(1) != 1) throw new IllegalStateException("Database check failed");
            result.put("status", "UP");
        } catch (Exception ex) {
            result.put("status", "DOWN");
        }
        result.put("responseTimeMillis", (System.nanoTime() - started) / 1_000_000L);
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool != null) {
                result.put("activeConnections", pool.getActiveConnections());
                result.put("idleConnections", pool.getIdleConnections());
                result.put("pendingConnections", pool.getThreadsAwaitingConnection());
                result.put("totalConnections", pool.getTotalConnections());
            }
        }
        return result;
    }

    public Map<String, Object> alerts() {
        List<Map<String, String>> items = new ArrayList<>();
        Map<String, Object> db = database();
        if (!"UP".equals(db.get("status")))
            items.add(alert("ERROR", "DATABASE_DOWN", "PostgreSQL connectivity check failed"));
        Map<String, Object> resources = resources();
        long used = ((Number) resources.get("heapUsedBytes")).longValue();
        long max = ((Number) resources.get("heapMaxBytes")).longValue();
        if (max > 0 && used * 100.0 / max >= 85)
            items.add(alert("WARN", "HIGH_HEAP", "JVM heap usage is at least 85%"));
        if (resources.containsKey("diskUsableBytes") && resources.containsKey("diskTotalBytes")) {
            long free = ((Number) resources.get("diskUsableBytes")).longValue();
            long total = ((Number) resources.get("diskTotalBytes")).longValue();
            if (total > 0 && free * 100.0 / total < 10)
                items.add(alert("WARN", "LOW_DISK", "Less than 10% disk space remains"));
        }
        return Map.of("checkedAt", Instant.now().toString(), "count", items.size(), "alerts", items);
    }

    private Map<String, String> alert(String level, String code, String message) {
        return Map.of("level", level, "code", code, "message", message);
    }
}
