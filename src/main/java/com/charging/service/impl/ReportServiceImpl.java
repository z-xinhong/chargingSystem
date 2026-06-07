package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charging.common.Result;
import com.charging.entity.Bill;
import com.charging.mapper.BillMapper;
import com.charging.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportServiceImpl implements ReportService {

    @Autowired
    private BillMapper billMapper;

    @Override
    public Result list(String period) {
        String normalizedPeriod = period == null || period.isBlank() ? "DAY" : period.toUpperCase(Locale.ROOT);

        QueryWrapper<Bill> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("created_at");
        List<Bill> bills = billMapper.selectList(wrapper);

        Map<String, ReportRow> rows = new LinkedHashMap<>();
        for (Bill bill : bills) {
            String periodValue = periodValue(bill, normalizedPeriod);
            Long pileId = bill.getPileId() == null ? 0L : bill.getPileId();
            String key = periodValue + "#" + pileId;
            ReportRow row = rows.computeIfAbsent(key, item -> new ReportRow(periodValue, pileId));
            row.add(bill);
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (ReportRow row : rows.values()) {
            data.add(row.toMap());
        }
        return Result.success(data);
    }

    private String periodValue(Bill bill, String period) {
        LocalDate date = bill.getCreatedAt() == null ? LocalDate.now() : bill.getCreatedAt().toLocalDate();
        if ("MONTH".equals(period)) {
            return String.format("%d-%02d", date.getYear(), date.getMonthValue());
        }
        if ("WEEK".equals(period)) {
            int week = date.get(WeekFields.ISO.weekOfWeekBasedYear());
            int year = date.get(WeekFields.ISO.weekBasedYear());
            return String.format("%d-W%02d", year, week);
        }
        return date.toString();
    }

    private static class ReportRow {
        private final String period;
        private final Long pileId;
        private int totalCount;
        private double totalDuration;
        private double totalKwh;
        private BigDecimal electricityFee = BigDecimal.ZERO;
        private BigDecimal serviceFee = BigDecimal.ZERO;
        private BigDecimal totalFee = BigDecimal.ZERO;

        private ReportRow(String period, Long pileId) {
            this.period = period;
            this.pileId = pileId;
        }

        private void add(Bill bill) {
            totalCount++;
            totalDuration += bill.getDurationHours() == null ? 0 : bill.getDurationHours();
            totalKwh += bill.getActualKwh() == null ? 0 : bill.getActualKwh();
            electricityFee = electricityFee.add(nullToZero(bill.getElectricityFee()));
            serviceFee = serviceFee.add(nullToZero(bill.getServiceFee()));
            totalFee = totalFee.add(nullToZero(bill.getTotalFee()));
        }

        private Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("period", period);
            data.put("pileId", pileId);
            data.put("totalCount", totalCount);
            data.put("totalDuration", round(totalDuration));
            data.put("totalKwh", round(totalKwh));
            data.put("electricityFee", electricityFee);
            data.put("serviceFee", serviceFee);
            data.put("totalFee", totalFee);
            return data;
        }

        private static BigDecimal nullToZero(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }

        private static double round(double value) {
            return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
    }
}
