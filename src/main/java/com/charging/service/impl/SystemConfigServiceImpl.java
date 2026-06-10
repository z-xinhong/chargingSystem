package com.charging.service.impl;

import com.charging.service.SystemConfigService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    private final Map<String, Object> config = new ConcurrentHashMap<>();

    public SystemConfigServiceImpl() {
        config.put("fastPileCount", 3);
        config.put("slowPileCount", 2);
        config.put("fastPower", 30);
        config.put("slowPower", 10);
        config.put("waitingAreaSize", 5);
        config.put("chargingQueueLength", 3);
        config.put("callingPaused", false);
        config.put("scheduleMode", "");
        config.put("scheduleModeLocked", false);
        config.put("defaultSchedulePolicy", "BATCH_SHORTEST");
        config.put("defaultFaultPolicy", "PRIORITY");
    }

    @Override
    public Map<String, Object> getConfig() {
        return new LinkedHashMap<>(config);
    }

    @Override
    public Map<String, Object> saveConfig(Map<String, Object> newConfig) {
        if (newConfig != null) {
            newConfig.remove("scheduleMode");
            newConfig.remove("scheduleModeLocked");
            config.putAll(newConfig);
        }
        return getConfig();
    }

    @Override
    public boolean isCallingPaused() {
        Object value = config.get("callingPaused");
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    @Override
    public void setCallingPaused(boolean callingPaused) {
        config.put("callingPaused", callingPaused);
    }

    @Override
    public String getScheduleMode() {
        Object value = config.get("scheduleMode");
        String mode = value == null ? "" : String.valueOf(value);
        return mode.isBlank() ? "NORMAL" : mode;
    }

    @Override
    public boolean isScheduleModeLocked() {
        Object value = config.get("scheduleModeLocked");
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    @Override
    public boolean selectScheduleMode(String scheduleMode) {
        if (isScheduleModeLocked()) {
            return false;
        }
        config.put("scheduleMode", scheduleMode == null || scheduleMode.isBlank() ? "NORMAL" : scheduleMode.trim().toUpperCase());
        config.put("scheduleModeLocked", true);
        return true;
    }
}
