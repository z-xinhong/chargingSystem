package com.charging.service;

import java.util.Map;

public interface SystemConfigService {
    Map<String, Object> getConfig();

    Map<String, Object> saveConfig(Map<String, Object> config);

    boolean isCallingPaused();

    void setCallingPaused(boolean callingPaused);
}
