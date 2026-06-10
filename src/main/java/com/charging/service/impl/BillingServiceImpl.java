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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
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

        PileQueue currentQueue = findPileQueue(request.getId());
        if (currentQueue == null || !"CHARGING".equalsIgnoreCase(currentQueue.getStatus())) {
            return Result.error("只有正在充电的请求才能结束充电");
        }

        Bill bill = createBillAndFinishRequest(request, currentQueue);
        return Result.success(toBillResponse(bill));
    }

    @Override
    @Transactional
    public Long generateFaultBill(Long requestId) {
        ChargingRequest request = chargingRequestMapper.selectById(requestId);
        if (request == null || "COMPLETED".equals(request.getStatus()) || "CANCELLED".equals(request.getStatus())) {
            return null;
        }

        PileQueue currentQueue = findPileQueue(request.getId());
        if (currentQueue == null || !"CHARGING".equalsIgnoreCase(currentQueue.getStatus())) {
            return null;
        }

        Bill bill = createFaultInterruptedBill(request, currentQueue);
        return bill.getId();
    }

    @Override
    @Transactional
    public Long completeIfFullyCharged(Long requestId) {
        ChargingRequest request = chargingRequestMapper.selectById(requestId);
        if (request == null || !"CHARGING".equalsIgnoreCase(request.getStatus())) {
            return null;
        }

        PileQueue currentQueue = findPileQueue(request.getId());
        if (currentQueue == null || !"CHARGING".equalsIgnoreCase(currentQueue.getStatus())) {
            return null;
        }

        ChargingPile pile = chargingPileMapper.selectById(currentQueue.getPileId());
        if (!isFullyCharged(request, pile)) {
            return null;
        }

        Bill bill = createBillAndFinishRequest(request, currentQueue);
        return bill.getId();
    }

    @Override
    public Result list(Integer page, Integer size, Long userId) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 10 : size;
        List<Long> requestIds = selectUserRequestIds(userId);

        if (requestIds.isEmpty()) {
            Map<String, Object> emptyData = new HashMap<>();
            emptyData.put("page", currentPage);
            emptyData.put("size", pageSize);
            emptyData.put("total", 0L);
            emptyData.put("records", Collections.emptyList());
            return Result.success(emptyData);
        }

        QueryWrapper<Bill> wrapper = new QueryWrapper<>();
        wrapper.in("request_id", requestIds).orderByDesc("created_at");
        Page<Bill> billPage = billMapper.selectPage(new Page<>(currentPage, pageSize), wrapper);
        List<Map<String, Object>> records = billPage.getRecords().stream()
                .map(this::toBillResponse)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("page", currentPage);
        data.put("size", pageSize);
        data.put("total", billPage.getTotal());
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
        if ("COMPLETED".equals(request.getStatus())) {
            return Result.error("该充电请求已结束");
        }
        if ("CANCELLED".equals(request.getStatus())) {
            return Result.error("已取消的充电请求不能结束充电");
        }
        return null;
    }

    private Bill createBillAndFinishRequest(ChargingRequest request, PileQueue currentQueue) {
        ChargingPile pile = chargingPileMapper.selectById(currentQueue.getPileId());
        ChargeSnapshot snapshot = calculateChargedSnapshot(request, pile);
        Bill bill = insertBill(request, currentQueue, pile, snapshot.durationHours, snapshot.actualKwh);

        request.setStatus("COMPLETED");
        chargingRequestMapper.updateById(request);

        pileQueueMapper.deleteById(currentQueue.getId());
        reindexPileQueue(currentQueue.getPileId());
        updatePileStatistics(pile, snapshot.durationHours, snapshot.actualKwh);
        return bill;
    }

    private Bill createFaultInterruptedBill(ChargingRequest request, PileQueue currentQueue) {
        ChargingPile pile = chargingPileMapper.selectById(currentQueue.getPileId());
        ChargeSnapshot snapshot = calculateChargedSnapshot(request, pile);
        Bill bill = insertBill(request, currentQueue, pile, snapshot.durationHours, snapshot.actualKwh);

        double remainingKwh = roundDouble(Math.max(0, (request.getRequestedKwh() == null ? 0 : request.getRequestedKwh()) - snapshot.actualKwh));
        updatePileStatistics(pile, snapshot.durationHours, snapshot.actualKwh);

        if (remainingKwh <= 0) {
            request.setRequestedKwh(0.0);
            request.setStatus("COMPLETED");
            chargingRequestMapper.updateById(request);
            pileQueueMapper.deleteById(currentQueue.getId());
            reindexPileQueue(currentQueue.getPileId());
        } else {
            request.setRequestedKwh(remainingKwh);
            request.setStatus("FAULT_STOPPED");
            request.setCreatedAt(LocalDateTime.now());
            chargingRequestMapper.updateById(request);
        }
        return bill;
    }

    private Bill insertBill(ChargingRequest request, PileQueue currentQueue, ChargingPile pile, double durationHours, double actualKwh) {
        BigDecimal kwh = BigDecimal.valueOf(actualKwh);
        BigDecimal electricityFee = kwh.multiply(currentElectricityPrice()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal serviceFee = kwh.multiply(SERVICE_PRICE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalFee = electricityFee.add(serviceFee).setScale(2, RoundingMode.HALF_UP);

        Bill bill = new Bill();
        bill.setRequestId(request.getId());
        bill.setPileId(pile == null ? currentQueue.getPileId() : pile.getId());
        bill.setActualKwh(actualKwh);
        bill.setDurationHours(durationHours);
        bill.setElectricityFee(electricityFee);
        bill.setServiceFee(serviceFee);
        bill.setTotalFee(totalFee);
        bill.setCreatedAt(LocalDateTime.now());
        billMapper.insert(bill);
        return bill;
    }

    private ChargeSnapshot calculateChargedSnapshot(ChargingRequest request, ChargingPile pile) {
        double power = pile == null || pile.getPower() == null || pile.getPower() <= 0
                ? ("FAST".equals(request.getMode()) ? 30.0 : 10.0)
                : pile.getPower();
        double requestedKwh = request.getRequestedKwh() == null ? 0 : request.getRequestedKwh();
        long elapsedSeconds = request.getCreatedAt() == null
                ? 0
                : Math.max(0, Duration.between(request.getCreatedAt(), LocalDateTime.now()).getSeconds());
        double chargedBySeconds = elapsedSeconds / 3600.0 * power;
        double actualKwh = roundKwh(Math.min(requestedKwh, chargedBySeconds));
        double durationHours = roundHours(actualKwh / power);
        return new ChargeSnapshot(durationHours, actualKwh);
    }

    private PileQueue findPileQueue(Long requestId) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("request_id", requestId).last("limit 1");
        return pileQueueMapper.selectOne(wrapper);
    }

    private void reindexPileQueue(Long pileId) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId).orderByAsc("position_no", "id");
        List<PileQueue> queues = pileQueueMapper.selectList(wrapper);

        for (int i = 0; i < queues.size(); i++) {
            PileQueue queue = queues.get(i);
            String status = i == 0 ? "CHARGING" : "WAITING";
            queue.setPositionNo(i + 1);
            queue.setStatus(status);
            pileQueueMapper.updateById(queue);

            ChargingRequest request = chargingRequestMapper.selectById(queue.getRequestId());
            if (request != null && !"COMPLETED".equals(request.getStatus()) && !"CANCELLED".equals(request.getStatus())) {
                request.setStatus(status);
                if ("CHARGING".equals(status)) {
                    request.setCreatedAt(LocalDateTime.now());
                }
                chargingRequestMapper.updateById(request);
            }
        }
    }

    private void updatePileStatistics(ChargingPile pile, double durationHours, double actualKwh) {
        if (pile == null) {
            return;
        }

        QueryWrapper<PileQueue> remainingWrapper = new QueryWrapper<>();
        remainingWrapper.eq("pile_id", pile.getId());
        long remaining = pileQueueMapper.selectCount(remainingWrapper);
        pile.setStatus(remaining > 0 ? "CHARGING" : "IDLE");
        pile.setTotalChargeCount((pile.getTotalChargeCount() == null ? 0 : pile.getTotalChargeCount()) + 1);
        pile.setTotalChargeTime(roundDouble((pile.getTotalChargeTime() == null ? 0 : pile.getTotalChargeTime()) + durationHours));
        pile.setTotalChargeKwh(roundDouble((pile.getTotalChargeKwh() == null ? 0 : pile.getTotalChargeKwh()) + actualKwh));
        chargingPileMapper.updateById(pile);
    }

    private List<Long> selectUserRequestIds(Long userId) {
        QueryWrapper<ChargingRequest> requestWrapper = new QueryWrapper<>();
        requestWrapper.eq("user_id", userId).select("id");
        return chargingRequestMapper.selectList(requestWrapper).stream()
                .map(ChargingRequest::getId)
                .collect(Collectors.toList());
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

    private boolean isFullyCharged(ChargingRequest request, ChargingPile pile) {
        if (request.getCreatedAt() == null || request.getRequestedKwh() == null || request.getRequestedKwh() <= 0) {
            return false;
        }

        double power = pile == null || pile.getPower() == null || pile.getPower() <= 0
                ? ("FAST".equals(request.getMode()) ? 30.0 : 10.0)
                : pile.getPower();
        long elapsedSeconds = Math.max(0, Duration.between(request.getCreatedAt(), LocalDateTime.now()).getSeconds());
        long requiredSeconds = Math.max(1L, (long) Math.ceil(request.getRequestedKwh() / power * 3600));
        return elapsedSeconds >= requiredSeconds;
    }

    private Map<String, Object> toBillResponse(Bill bill) {
        LocalDateTime endTime = bill.getCreatedAt();
        LocalDateTime startTime = null;
        if (endTime != null && bill.getDurationHours() != null) {
            long durationSeconds = Math.max(0L, Math.round(bill.getDurationHours() * 3600));
            startTime = endTime.minusSeconds(durationSeconds);
        }

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
        data.put("generatedTime", bill.getCreatedAt());
        data.put("startTime", startTime);
        data.put("endTime", endTime);
        return data;
    }

    private double roundDouble(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double roundKwh(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private double roundHours(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }

    private static class ChargeSnapshot {
        private final double durationHours;
        private final double actualKwh;

        private ChargeSnapshot(double durationHours, double actualKwh) {
            this.durationHours = durationHours;
            this.actualKwh = actualKwh;
        }
    }
}
