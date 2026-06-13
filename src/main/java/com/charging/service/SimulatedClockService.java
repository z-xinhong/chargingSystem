package com.charging.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class SimulatedClockService {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 6, 13);
    private static final LocalTime DAY_START = LocalTime.of(6, 0);
    private static final LocalTime DAY_END = LocalTime.of(23, 0);
    private static final long TIME_SCALE = 10L;
    private static final long ACTIVE_DAY_SECONDS = Duration.between(DAY_START, DAY_END).getSeconds();
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final long realStartMillis = System.currentTimeMillis();

    public LocalDateTime now() {
        long realElapsedSeconds = Math.max(0L, (System.currentTimeMillis() - realStartMillis) / 1000L);
        long simulatedElapsedSeconds = realElapsedSeconds * TIME_SCALE;
        long dayOffset = simulatedElapsedSeconds / ACTIVE_DAY_SECONDS;
        long secondsInActiveDay = simulatedElapsedSeconds % ACTIVE_DAY_SECONDS;
        return LocalDateTime.of(BASE_DATE.plusDays(dayOffset), DAY_START).plusSeconds(secondsInActiveDay);
    }

    public LocalDate today() {
        return now().toLocalDate();
    }

    public LocalTime currentTime() {
        return now().toLocalTime();
    }

    public Map<String, Object> snapshot() {
        LocalDateTime current = now();
        Map<String, Object> data = new HashMap<>();
        data.put("currentDateTime", current);
        data.put("displayTime", current.format(DISPLAY_FORMATTER));
        data.put("date", current.toLocalDate());
        data.put("time", current.toLocalTime());
        data.put("startDate", BASE_DATE);
        data.put("dayStart", DAY_START);
        data.put("dayEnd", DAY_END);
        data.put("timeScale", TIME_SCALE);
        data.put("description", "现实 1 分钟 = 模拟 10 分钟，模拟时间每日 06:00-23:00 循环");
        return data;
    }
}
