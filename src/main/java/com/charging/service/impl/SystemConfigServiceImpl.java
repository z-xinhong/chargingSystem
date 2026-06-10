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
}
