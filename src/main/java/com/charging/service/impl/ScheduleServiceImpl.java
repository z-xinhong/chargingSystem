package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charging.common.Result;
import com.charging.entity.ChargingPile;
import com.charging.entity.ChargingRequest;
import com.charging.entity.PileQueue;
import com.charging.entity.User;
import com.charging.entity.WaitingQueue;
import com.charging.mapper.ChargingPileMapper;
import com.charging.mapper.ChargingRequestMapper;
import com.charging.mapper.PileQueueMapper;
import com.charging.mapper.UserMapper;
import com.charging.mapper.WaitingQueueMapper;
import com.charging.service.BillingService;
import com.charging.service.ScheduleService;
import com.charging.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScheduleServiceImpl implements ScheduleService {

    private static final int MAX_PILE_QUEUE_SIZE = 5;

    @Autowired
    private WaitingQueueMapper waitingQueueMapper;

    @Autowired
    private PileQueueMapper pileQueueMapper;

    @Autowired
    private ChargingPileMapper chargingPileMapper;

    @Autowired
    private ChargingRequestMapper chargingRequestMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private BillingService billingService;

    @Override
    @Transactional
    public Result dispatch(String policy) {
        String normalizedPolicy = normalizePolicy(policy, "BATCH_SHORTEST");
        if ("PRIORITY".equalsIgnoreCase(normalizedPolicy) || "TIME_ORDER".equalsIgnoreCase(normalizedPolicy)) {
            normalizedPolicy = "BATCH_SHORTEST";
        }
        List<Map<String, Object>> assignedVehicles = dispatchInternal(normalizedPolicy);
        return Result.success(assignedVehicles.size());
    }

    @Override
    @Transactional
    public Result dispatchOnce() {
        List<Map<String, Object>> assignedVehicles = dispatchInternal("SINGLE_SHORTEST");

        Map<String, Object> data = new HashMap<>();
        data.put("dispatchType", "ONCE");
        data.put("assignedVehicles", assignedVehicles);
        return Result.success(data);
    }

    @Override
    @Transactional
    public Result dispatchBatch() {
        List<Map<String, Object>> assignedVehicles = dispatchInternal("BATCH_SHORTEST");

        Map<String, Object> data = new HashMap<>();
        data.put("dispatchType", "BATCH");
        data.put("assignedVehicles", assignedVehicles);
        return Result.success(data);
    }

    @Override
    @Transactional
    public Result snapshot() {
        autoCompleteFinishedCharging();
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("waitingArea", buildWaitingArea());
        snapshot.put("piles", buildPiles());
        snapshot.put("pileQueues", buildPileQueues());
        snapshot.put("pausedCalling", systemConfigService.isCallingPaused());

        Map<String, Object> data = new HashMap<>();
        data.put("snapshot", snapshot);
        return Result.success(data);
    }

    @Override
    public Result waitingQueue() {
        return Result.success(buildWaitingArea());
    }

    @Override
    public Result pileQueue() {
        return Result.success(buildPileQueues());
    }

    private List<Map<String, Object>> dispatchInternal(String policy) {
        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("position_no");
        List<WaitingQueue> waitingQueues = waitingQueueMapper.selectList(wrapper);

        int dispatchLimit = "SINGLE_SHORTEST".equalsIgnoreCase(policy) ? 1 : waitingQueues.size();
        int dispatchedCount = 0;
        List<Map<String, Object>> assignedVehicles = new ArrayList<>();

        for (WaitingQueue waitingQueue : waitingQueues) {
            if (dispatchedCount >= dispatchLimit) {
                break;
            }

            ChargingRequest request = chargingRequestMapper.selectById(waitingQueue.getRequestId());
            if (request == null) {
                waitingQueueMapper.deleteById(waitingQueue.getId());
                continue;
            }

            ChargingPile targetPile = findShortestAvailablePile(waitingQueue.getMode());
            if (targetPile == null) {
                continue;
            }

            int nextPosition = nextPileQueuePosition(targetPile.getId());
            PileQueue pileQueue = new PileQueue();
            pileQueue.setPileId(targetPile.getId());
            pileQueue.setRequestId(waitingQueue.getRequestId());
            pileQueue.setPositionNo(nextPosition);
            pileQueue.setStatus(nextPosition == 1 ? "CHARGING" : "WAITING");
            pileQueueMapper.insert(pileQueue);

            request.setStatus(nextPosition == 1 ? "CHARGING" : "WAITING");
            chargingRequestMapper.updateById(request);

            if (nextPosition == 1) {
                targetPile.setStatus("CHARGING");
                chargingPileMapper.updateById(targetPile);
            }

            waitingQueueMapper.deleteById(waitingQueue.getId());

            Map<String, Object> assigned = new HashMap<>();
            assigned.put("queueNumber", request.getQueueNumber());
            assigned.put("userId", request.getUserId());
            assigned.put("from", "WAITING_AREA");
            assigned.put("toPileId", targetPile.getId());
            assigned.put("estimatedFinishMinutes", estimateFinishMinutes(targetPile, request));
            assignedVehicles.add(assigned);

            dispatchedCount++;
        }

        return assignedVehicles;
    }

    private List<Map<String, Object>> buildWaitingArea() {
        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("mode", "position_no");
        List<WaitingQueue> waitingQueues = waitingQueueMapper.selectList(wrapper);

        List<Map<String, Object>> data = new ArrayList<>();
        for (WaitingQueue waitingQueue : waitingQueues) {
            ChargingRequest request = chargingRequestMapper.selectById(waitingQueue.getRequestId());
            User user = request == null ? null : userMapper.selectById(request.getUserId());

            Map<String, Object> item = new HashMap<>();
            item.put("queueNumber", waitingQueue.getQueueNumber());
            item.put("userId", request == null ? null : request.getUserId());
            item.put("mode", waitingQueue.getMode());
            item.put("batteryCapacity", user == null ? null : user.getBatteryCapacity());
            item.put("requestedKwh", request == null ? null : request.getRequestedKwh());
            item.put("requiredChargeMinutes", request == null ? 0 : estimateFullMinutes(waitingQueue.getMode(), request));
            item.put("waitingMinutes", 0);
            data.add(item);
        }
        return data;
    }

    private List<Map<String, Object>> buildPiles() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (ChargingPile pile : chargingPileMapper.selectList(null)) {
            Map<String, Object> item = new HashMap<>();
            item.put("pileId", pile.getId());
            item.put("name", pile.getPileCode() + ("FAST".equalsIgnoreCase(pile.getType()) ? " 快充桩" : " 慢充桩"));
            item.put("type", pile.getType());
            item.put("status", pile.getStatus());
            item.put("power", pile.getPower());
            data.add(item);
        }
        return data;
    }

    private Map<String, List<Map<String, Object>>> buildPileQueues() {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("pile_id", "position_no");
        List<PileQueue> queues = pileQueueMapper.selectList(wrapper);

        Map<String, List<Map<String, Object>>> data = new LinkedHashMap<>();
        for (PileQueue queue : queues) {
            ChargingRequest request = chargingRequestMapper.selectById(queue.getRequestId());
            ChargingPile pile = chargingPileMapper.selectById(queue.getPileId());

            Map<String, Object> item = new HashMap<>();
            item.put("queueNumber", request == null ? null : request.getQueueNumber());
            item.put("userId", request == null ? null : request.getUserId());
            item.put("mode", request == null ? null : request.getMode());
            item.put("requestedKwh", request == null ? null : request.getRequestedKwh());
            item.put("status", queue.getStatus());
            item.put("requiredChargeMinutes", pile == null || request == null ? 0 : estimateFinishMinutes(pile, request));
            item.put("estimatedFinishMinutes", pile == null || request == null ? 0 : estimateFinishMinutes(pile, request));
            item.putAll(buildChargingProgress(queue, pile, request));

            data.computeIfAbsent(String.valueOf(queue.getPileId()), key -> new ArrayList<>()).add(item);
        }
        return data;
    }

    private ChargingPile findShortestAvailablePile(String mode) {
        QueryWrapper<ChargingPile> wrapper = new QueryWrapper<>();
        wrapper.eq("type", mode)
                .ne("status", "FAULT")
                .ne("status", "OFFLINE");
        List<ChargingPile> piles = chargingPileMapper.selectList(wrapper);

        ChargingPile bestPile = null;
        double bestLoad = Double.MAX_VALUE;

        for (ChargingPile pile : piles) {
            int queueSize = activeQueueSize(pile.getId());
            if (queueSize >= MAX_PILE_QUEUE_SIZE) {
                continue;
            }

            double load = estimatedPileChargeHours(pile);
            if (load < bestLoad) {
                bestLoad = load;
                bestPile = pile;
            }
        }

        return bestPile;
    }

    private int activeQueueSize(Long pileId) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId);
        return Math.toIntExact(pileQueueMapper.selectCount(wrapper));
    }

    private int nextPileQueuePosition(Long pileId) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId).orderByDesc("position_no").last("limit 1");
        PileQueue last = pileQueueMapper.selectOne(wrapper);
        return last == null || last.getPositionNo() == null ? 1 : last.getPositionNo() + 1;
    }

    private double estimatedPileChargeHours(ChargingPile pile) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pile.getId());
        List<PileQueue> queues = pileQueueMapper.selectList(wrapper);

        double totalHours = 0;
        double power = pile.getPower() == null || pile.getPower() <= 0 ? 1 : pile.getPower();

        for (PileQueue queue : queues) {
            ChargingRequest request = chargingRequestMapper.selectById(queue.getRequestId());
            if (request != null && request.getRequestedKwh() != null) {
                totalHours += request.getRequestedKwh() / power;
            }
        }

        return totalHours;
    }

    private int estimateFinishMinutes(ChargingPile pile, ChargingRequest request) {
        if (request.getRequestedKwh() == null) {
            return 0;
        }
        double power = pile.getPower() == null || pile.getPower() <= 0 ? 1 : pile.getPower();
        return (int) Math.ceil(request.getRequestedKwh() / power * 60);
    }

    private int estimateFullMinutes(String mode, ChargingRequest request) {
        if (request == null || request.getRequestedKwh() == null) {
            return 0;
        }
        double power = "SLOW".equalsIgnoreCase(mode) ? 10.0 : 30.0;
        return (int) Math.ceil(request.getRequestedKwh() / power * 60);
    }

    private Map<String, Object> buildChargingProgress(PileQueue queue, ChargingPile pile, ChargingRequest request) {
        Map<String, Object> data = new HashMap<>();
        if (request == null || request.getRequestedKwh() == null || pile == null || !"CHARGING".equalsIgnoreCase(queue.getStatus())) {
            data.put("chargedKwh", 0);
            data.put("remainingKwh", request == null ? 0 : request.getRequestedKwh());
            data.put("remainingMinutes", pile == null || request == null ? 0 : estimateFinishMinutes(pile, request));
            return data;
        }

        double power = pile.getPower() == null || pile.getPower() <= 0 ? 1 : pile.getPower();
        LocalDateTime startTime = request.getCreatedAt() == null ? LocalDateTime.now() : request.getCreatedAt();
        double elapsedHours = Math.max(0, Duration.between(startTime, LocalDateTime.now()).toMinutes()) / 60.0;
        double chargedKwh = Math.min(request.getRequestedKwh(), elapsedHours * power);
        double remainingKwh = Math.max(0, request.getRequestedKwh() - chargedKwh);

        data.put("chargedKwh", roundOne(chargedKwh));
        data.put("remainingKwh", roundOne(remainingKwh));
        data.put("remainingMinutes", (int) Math.ceil(remainingKwh / power * 60));
        return data;
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private void autoCompleteFinishedCharging() {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("status", "CHARGING");
        boolean completedAny = false;
        for (PileQueue queue : pileQueueMapper.selectList(wrapper)) {
            if (billingService.completeIfFullyCharged(queue.getRequestId()) != null) {
                completedAny = true;
            }
        }
        if (completedAny) {
            dispatchInternal("BATCH_SHORTEST");
        }
    }

    private String normalizePolicy(String policy, String defaultPolicy) {
        return policy == null || policy.isBlank() ? defaultPolicy : policy;
    }
}
