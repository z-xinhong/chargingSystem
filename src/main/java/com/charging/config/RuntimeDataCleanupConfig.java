package com.charging.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RuntimeDataCleanupConfig {

    private static final Logger log = LoggerFactory.getLogger(RuntimeDataCleanupConfig.class);

    private final JdbcTemplate jdbcTemplate;

    public RuntimeDataCleanupConfig(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PreDestroy
    public void cleanupRuntimeDataOnShutdown() {
        try {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
            jdbcTemplate.execute("TRUNCATE TABLE bill");
            jdbcTemplate.execute("TRUNCATE TABLE pile_queue");
            jdbcTemplate.execute("TRUNCATE TABLE waiting_queue");
            jdbcTemplate.execute("TRUNCATE TABLE fault_log");
            jdbcTemplate.execute("TRUNCATE TABLE charging_request");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
            jdbcTemplate.update("""
                    UPDATE charging_pile
                    SET status = 'IDLE',
                        total_charge_count = 0,
                        total_charge_time = 0,
                        total_charge_kwh = 0
                    """);
            log.info("Runtime data cleaned on application shutdown; users and charging piles were kept.");
        } catch (Exception ex) {
            log.warn("Failed to clean runtime data on application shutdown.", ex);
        }
    }
}
