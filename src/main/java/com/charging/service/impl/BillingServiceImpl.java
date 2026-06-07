package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.charging.common.Result;
import com.charging.dto.EndChargingDTO;
import com.charging.entity.Bill;
import com.charging.entity.ChargingPile;
import com.charging.entity.ChargingRequest;
import com.charging.entity.PileQueue;
import com.charging.mapper.BillMapper;
import com.charging.mapper.ChargingPileMapper;
import com.charging.mapper.ChargingRequestMapper;
import com.charging.mapper.PileQueueMapper;
import com.charging.service.BillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BillingServiceImpl implements BillingService {

    private static final BigDecimal SERVICE_PRICE = new BigDecimal("0.80");

    @Autowired
    private BillMapper billMapper;

    @Autowired
    private ChargingRequestMapper chargingRequestMapper;

    @Autowired
    private PileQueueMapper pileQueueMapper;

    @Autowired
    private ChargingPileMapper chargingPileMapper;

    @Override
    @Transactional
    public Result endCharging(EndChargingDTO dto, Long userId) {
        if (dto == null || dto.getRequestId() == null) {
            return Result.error("参数错误");
        }

        ChargingRequest request = chargingRequestMapper.selectById(dto.getRequestId());
        Result access = validateRequest(userId, request);
        if (access != null) {
            return access;
        }
        if ("COMPLETED".equals(request.getStatus())) {
            return Result.error("该充电请求已结束");
        }
        if ("CANCELLED".equals(request.getStatus())) {
            return Result.error("已取消的充电请求不能结束充电");
        }

        Bill bill = createBillAndFinishRequest(request);
        return Result.success(toBillResponse(bill));
    }

    @Override
    @Transactional
    public Long generateFaultBill(Long requestId) {
        ChargingRequest request = chargingRequestMapper.selectById(requestId);
        if (request == null || "COMPLETED".equals(request.getStatus()) || "CANCELLED".equals(request.getStatus())) {
            return null;
        }

        Bill bill = createBillAndFinishRequest(request);
        return bill.getId();
    }

    @Override
    public Result list(Integer page, Integer size, Long userId) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 10 : size;

        QueryWrapper<Bill> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        Page<Bill> billPage = billMapper.selectPage(new Page<>(currentPage, pageSize), wrapper);
        List<Map<String, Object>> records = billPage.getRecords().stream()
                .filter(bill -> belongsToUser(bill, userId))
                .map(this::toBillResponse)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("page", currentPage);
        data.put("size", pageSize);
        data.put("total", records.size());
        data.put("records", records);
        return Result.success(data);
    }

    @Override
    public Result detail(Long billId, Long userId) {
        Bill bill = billMapper.selectById(billId);
        if (bill == null) {
            return Result.error("详单不存在");
        }
        if (!belongsToUser(bill, userId)) {
            return Result.error("无权访问该详单");
        }
        return Result.success(toBillResponse(bill));
    }

    private Result validateRequest(Long userId, ChargingRequest request) {
        if (request == null) {
            return Result.error("充电请求不存在");
        }
        if (!request.getUserId().equals(userId)) {
            return Result.error("无权访问该充电请求");
        }
        return null;
    }

    private Bill createBillAndFinishRequest(ChargingRequest request) {
        ChargingPile pile = findPileByRequest(request.getId());
        double power = pile == null || pile.getPower() == null || pile.getPower() <= 0
                ? ("FAST".equals(request.getMode()) ? 30.0 : 10.0)
                : pile.getPower();
        double actualKwh = roundDouble(request.getRequestedKwh() == null ? 0 : request.getRequestedKwh());
        double durationHours = roundDouble(actualKwh / power);

        BigDecimal kwh = BigDecimal.valueOf(actualKwh);
        BigDecimal electricityFee = kwh.multiply(currentElectricityPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal serviceFee = kwh.multiply(SERVICE_PRICE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalFee = electricityFee.add(serviceFee).setScale(2, RoundingMode.HALF_UP);

        Bill bill = new Bill();
        bill.setRequestId(request.getId());
        bill.setPileId(pile == null ? null : pile.getId());
        bill.setActualKwh(actualKwh);
        bill.setDurationHours(durationHours);
        bill.setElectricityFee(electricityFee);
        bill.setServiceFee(serviceFee);
        bill.setTotalFee(totalFee);
        bill.setCreatedAt(LocalDateTime.now());
        billMapper.insert(bill);

        request.setStatus("COMPLETED");
        chargingRequestMapper.updateById(request);
        finishPileQueue(request.getId(), pile);
        return bill;
    }

    private ChargingPile findPileByRequest(Long requestId) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("request_id", requestId).last("limit 1");
        PileQueue queue = pileQueueMapper.selectOne(wrapper);
        return queue == null ? null : chargingPileMapper.selectById(queue.getPileId());
    }

    private void finishPileQueue(Long requestId, ChargingPile pile) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("request_id", requestId);
        pileQueueMapper.delete(wrapper);

        if (pile != null) {
            QueryWrapper<PileQueue> remainingWrapper = new QueryWrapper<>();
            remainingWrapper.eq("pile_id", pile.getId());
            long remaining = pileQueueMapper.selectCount(remainingWrapper);
            pile.setStatus(remaining > 0 ? "CHARGING" : "IDLE");
            pile.setTotalChargeCount((pile.getTotalChargeCount() == null ? 0 : pile.getTotalChargeCount()) + 1);
            pile.setTotalChargeTime((pile.getTotalChargeTime() == null ? 0 : pile.getTotalChargeTime()));
            pile.setTotalChargeKwh((pile.getTotalChargeKwh() == null ? 0 : pile.getTotalChargeKwh()));
            chargingPileMapper.updateById(pile);
        }
    }

    private boolean belongsToUser(Bill bill, Long userId) {
        ChargingRequest request = chargingRequestMapper.selectById(bill.getRequestId());
        return request != null && request.getUserId().equals(userId);
    }

    private BigDecimal currentElectricityPrice() {
        LocalTime now = LocalTime.now();
        if (inRange(now, 10, 15) || inRange(now, 18, 21)) {
            return new BigDecimal("1.00");
        }
        if (inRange(now, 7, 10) || inRange(now, 15, 18) || inRange(now, 21, 23)) {
            return new BigDecimal("0.70");
        }
        return new BigDecimal("0.40");
    }

    private boolean inRange(LocalTime time, int startHour, int endHour) {
        return !time.isBefore(LocalTime.of(startHour, 0)) && time.isBefore(LocalTime.of(endHour, 0));
    }

    private Map<String, Object> toBillResponse(Bill bill) {
        Map<String, Object> data = new HashMap<>();
        data.put("billId", bill.getId());
        data.put("requestId", bill.getRequestId());
        data.put("pileId", bill.getPileId());
        data.put("actualKwh", bill.getActualKwh());
        data.put("durationHours", bill.getDurationHours());
        data.put("electricityFee", bill.getElectricityFee());
        data.put("serviceFee", bill.getServiceFee());
        data.put("totalFee", bill.getTotalFee());
        data.put("createdAt", bill.getCreatedAt());
        return data;
    }

    private double roundDouble(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
