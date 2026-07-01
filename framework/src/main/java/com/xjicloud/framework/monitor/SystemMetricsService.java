package com.xjicloud.framework.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import org.springframework.stereotype.Service;

@Service
public class SystemMetricsService {

    private final SystemInfo systemInfo = new SystemInfo();
    private final ObjectMapper objectMapper;

    public SystemMetricsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> collectMetrics() {
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        OperatingSystem os = systemInfo.getOperatingSystem();
        CentralProcessor cpu = hal.getProcessor();
        GlobalMemory memory = hal.getMemory();

        double load = cpu.getSystemCpuLoad(500) * 100;
        long totalMem = memory.getTotal();
        long usedMem = totalMem - memory.getAvailable();

        long totalDisk = 0;
        long usedDisk = 0;
        FileSystem fs = os.getFileSystem();
        for (OSFileStore store : fs.getFileStores()) {
            totalDisk += store.getTotalSpace();
            usedDisk += store.getTotalSpace() - store.getUsableSpace();
        }

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("hostname", os.getNetworkParams().getHostName());
        metrics.put("os", os.getFamily() + " " + os.getVersionInfo().getVersion());
        metrics.put("cpuCores", cpu.getLogicalProcessorCount());
        metrics.put("cpuUsagePercent", Math.round(load * 10) / 10.0);
        metrics.put("memoryTotalBytes", totalMem);
        metrics.put("memoryUsedBytes", usedMem);
        metrics.put("diskTotalBytes", totalDisk);
        metrics.put("diskUsedBytes", usedDisk);
        metrics.put("loadAverage", cpu.getSystemLoadAverage(1)[0]);
        return metrics;
    }

    public String collectMetricsJson() {
        try {
            return objectMapper.writeValueAsString(collectMetrics());
        } catch (Exception e) {
            return "{}";
        }
    }
}
