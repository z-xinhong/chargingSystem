package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charging.common.Result;
import com.charging.entity.ChargingPile;
import com.charging.entity.ChargingRequest;
import com.charging.entity.FaultLog;
import com.charging.entity.PileQueue;
import com.charging.mapper.ChargingPileMapper;
import com.charging.mapper.ChargingRequestMapper;
import com.charging.mapper.FaultLogMapper;
import com.charging.mapper.PileQueueMapper;
import com.charging.service.BillingService;
import com.charging.service.FaultService;
import com.charging.service.ScheduleService;
import com.charging.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FaultServiceImpl implements FaultService {

    private static final int MAX_PILE_QUEUE_SIZE = 5;
    private static final String POLICY_PRIORITY = "PRIORITY";
    private static final String POLICY_TIME_ORDER = "TIME_ORDER";

    @Autowired
    private ChargingPileMapper chargingPileMapper;

    @Autowired
    private PileQueueMapper pileQueueMapper;

    @Autowired
    private ChargingRequestMapper chargingRequestMapper;

    @Autowired
    private FaultLogMapper faultLogMapper;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private BillingService billingService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Override
    @Transactional
    public Result simulate(Long pileId, String schedulePolicy, String remark) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            return Result.error("充电桩不存在");
        }

        String policy = normalizeFaultPolicy(schedulePolicy, POLICY_PRIORITY);
        pile.setStatus("FAULT");
        chargingPileMapper.updateById(pile);

        FaultLog faultLog = new FaultLog();
        faultLog.setPileId(pileId);
        faultLog.setFaultTime(LocalDateTime.now());
        faultLog.setSchedulePolicy(policy);
        faultLog.setRemark(remark);
        faultLogMapper.insert(faultLog);
        systemConfigService.setCallingPaused(true);

        List<PileQueue> affectedQueues = selectPileQueues(pileId);
        List<Map<String, Object>> affectedVehicles = new ArrayList<>();
        Long generatedBillId = null;

        for (PileQueue queue : affectedQueues) {
            ChargingRequest request = chargingRequestMapper.selectById(queue.getRequestId());
            if (request == null) {
                continue;
            }

            Map<String, Object> affectedVehicle = buildVehicleData(request, "FAULT_STOPPED", pileId, null);
            affectedVehicles.add(affectedVehicle);

            if (isCharging(queue)) {
                Long billId = billingService.generateFaultBill(request.getId());
                if (generatedBillId == null) {
                    generatedBillId = billId;
                }
                request = chargingRequestMapper.selectById(queue.getRequestId());
                if (request == null) {
                    continue;
                }
            }

            request.setStatus("FAULT_STOPPED");
            chargingRequestMapper.updateById(request);
            restoreFaultQueue(queue);
        }

        ChargingPile latestPile = chargingPileMapper.selectById(pileId);
        List<Map<String, Object>> movedVehicles = new ArrayList<>();
        if (latestPile != null) {
            latestPile.setStatus("FAULT");
            chargingPileMapper.updateById(latestPile);
            movedVehicles = dispatchFaultPileQueue(latestPile, policy);
        }

        boolean stillPaused = hasRemainingFaultQueue(pileId);
        systemConfigService.setCallingPaused(stillPaused);
        if (!stillPaused) {
            scheduleService.dispatch("BATCH_SHORTEST");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("faultPileId", pileId);
        data.put("schedulePolicy", policy);
        data.put("pausedCalling", systemConfigService.isCallingPaused());
        data.put("generatedBillId", generatedBillId);
        data.put("affectedVehicles", affectedVehicles);
        data.put("faultLogId", faultLog.getId());
        data.put("rescheduled", true);
        data.put("movedVehicles", movedVehicles.size());
        data.put("assignedVehicles", movedVehicles);
        return Result.success(data);
    }

    @Override
    @Transactional
    public Result dispatchFault(Long pileId, String schedulePolicy) {
        ChargingPile faultPile = chargingPileMapper.selectById(pileId);
        if (faultPile == null) {
            return Result.error("充电桩不存在");
        }
        if (!"FAULT".equalsIgnoreCase(faultPile.getStatus())) {
            return Result.error("当前充电桩不是故障状态");
        }

        String policy = normalizeFaultPolicy(schedulePolicy, POLICY_PRIORITY);
        List<Map<String, Object>> movedVehicles = dispatchFaultPileQueue(faultPile, policy);

        updateLatestFaultPolicy(pileId, policy);
        boolean stillPaused = hasRemainingFaultQueue(pileId);
        systemConfigService.setCallingPaused(stillPaused);
        if (!stillPaused) {
            scheduleService.dispatch("BATCH_SHORTEST");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("faultPileId", pileId);
        data.put("schedulePolicy", policy);
        data.put("pausedCalling", systemConfigService.isCallingPaused());
        data.put("rescheduled", true);
        data.put("movedVehicles", movedVehicles.size());
        data.put("assignedVehicles", movedVehicles);
        return Result.success(data);
    }

    @Override
    @Transactional
    public Result recover(Long pileId, String schedulePolicy) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            return Result.error("充电桩不存在");
        }

        String policy = POLICY_TIME_ORDER;
        pile.setStatus("IDLE");
        chargingPileMapper.updateById(pile);

        FaultLog faultLog = selectLatestUnrecoveredFault(pileId);
        if (faultLog != null) {
            faultLog.setRecoverTime(LocalDateTime.now());
            faultLog.setSchedulePolicy(policy);
            faultLogMapper.updateById(faultLog);
        }

        boolean shouldPauseCalling = hasUnchargedQueueOnOtherSameTypePiles(pile);
        systemConfigService.setCallingPaused(shouldPauseCalling);
        List<Map<String, Object>> movedVehicles = shouldPauseCalling
                ? dispatchRecoveredPileByTimeOrder(pile)
                : new ArrayList<>();

        systemConfigService.setCallingPaused(false);
        scheduleService.dispatch("BATCH_SHORTEST");

        Map<String, Object> data = new HashMap<>();
        data.put("recoveredPileId", pileId);
        data.put("schedulePolicy", policy);
        data.put("pausedCalling", false);
        data.put("rescheduled", true);
        data.put("movedVehicles", movedVehicles.size());
        data.put("assignedVehicles", movedVehicles);
        return Result.success(data);
    }

    private List<Map<String, Object>> dispatchFaultPileQueue(ChargingPile faultPile, String policy) {
        List<PileQueue> faultQueues = selectPileQueues(faultPile.getId());
        Comparator<PileQueue> comparator = POLICY_TIME_ORDER.equals(policy)
                ? Comparator.comparing((PileQueue queue) -> queueNumberOrder(queue.getRequestId()))
                        .thenComparing(PileQueue::getPositionNo, Comparator.nullsLast(Integer::compareTo))
                : Comparator.comparing(PileQueue::getPositionNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(queue -> queueNumberOrder(queue.getRequestId()));
        faultQueues.sort(comparator);

        List<Map<String, Object>> movedVehicles = new ArrayList<>();

        for (PileQueue queue : faultQueues) {
            ChargingRequest request = chargingRequestMapper.selectById(queue.getRequestId());
            if (request == null || isCompleted(request)) {
                pileQueueMapper.deleteById(queue.getId());
                continue;
            }

            ChargingPile targetPile = findBestPile(faultPile.getType(), faultPile.getId());
            if (targetPile == null) {
                request.setStatus("FAULT_STOPPED");
                chargingRequestMapper.updateById(request);
                queue.setStatus("FAULT_STOPPED");
                pileQueueMapper.updateById(queue);
                continue;
            }

            pileQueueMapper.deleteById(queue.getId());
            movedVehicles.add(assignToPile(request, targetPile, policy, queue.getPileId()));
        }

        return movedVehicles;
    }

    private List<Map<String, Object>> dispatchRecoveredPileByTimeOrder(ChargingPile recoveredPile) {
        List<PileQueue> candidates = collectUnchargedQueuesForRecovery(recoveredPile);
        candidates.sort(Comparator
                .comparing((PileQueue queue) -> queueNumberOrder(queue.getRequestId()))
                .thenComparing(PileQueue::getPositionNo, Comparator.nullsLast(Integer::compareTo)));

        List<RequeueCandidate> requeueCandidates = new ArrayList<>();
        for (PileQueue queue : candidates) {
            ChargingRequest request = chargingRequestMapper.selectById(queue.getRequestId());
            if (request == null || isCompleted(request)) {
                pileQueueMapper.deleteById(queue.getId());
                continue;
            }

            requeueCandidates.add(new RequeueCandidate(request, queue.getPileId()));
            pileQueueMapper.deleteById(queue.getId());
        }

        List<Map<String, Object>> movedVehicles = new ArrayList<>();
        for (RequeueCandidate candidate : requeueCandidates) {
            ChargingPile targetPile = findBestPile(recoveredPile.getType(), null);
            if (targetPile == null) {
                candidate.request.setStatus("FAULT_STOPPED");
                chargingRequestMapper.updateById(candidate.request);
                continue;
            }

            movedVehicles.add(assignToPile(candidate.request, targetPile, "TIME_ORDER_REQUEUE", candidate.fromPileId));
        }

        return movedVehicles;
    }

    private List<PileQueue> collectUnchargedQueuesForRecovery(ChargingPile recoveredPile) {
        List<ChargingPile> sameTypePiles = selectSameTypeAvailablePiles(recoveredPile.getType(), null);
        List<PileQueue> candidates = new ArrayList<>();

        for (ChargingPile pile : sameTypePiles) {
            for (PileQueue queue : selectPileQueues(pile.getId())) {
                if (!isCharging(queue)) {
                    candidates.add(queue);
                }
            }
        }

        return candidates;
    }

    private Map<String, Object> assignToPile(ChargingRequest request, ChargingPile targetPile, String from, Long fromPileId) {
        int nextPosition = nextPileQueuePosition(targetPile.getId());
        String queueStatus = nextPosition == 1 ? "CHARGING" : "WAITING";

        PileQueue newQueue = new PileQueue();
        newQueue.setPileId(targetPile.getId());
        newQueue.setRequestId(request.getId());
        newQueue.setPositionNo(nextPosition);
        newQueue.setStatus(queueStatus);
        pileQueueMapper.insert(newQueue);

        request.setStatus(queueStatus);
        chargingRequestMapper.updateById(request);

        if ("CHARGING".equals(queueStatus)) {
            targetPile.setStatus("CHARGING");
            chargingPileMapper.updateById(targetPile);
        }

        return buildVehicleData(request, queueStatus, fromPileId, targetPile.getId(), from);
    }

    private ChargingPile findBestPile(String type, Long excludedPileId) {
        List<ChargingPile> piles = selectSameTypeAvailablePiles(type, excludedPileId);
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

    private List<ChargingPile> selectSameTypeAvailablePiles(String type, Long excludedPileId) {
        QueryWrapper<ChargingPile> wrapper = new QueryWrapper<>();
        wrapper.eq("type", type)
                .ne("status", "FAULT")
                .ne("status", "OFFLINE")
                .orderByAsc("id");
        if (excludedPileId != null) {
            wrapper.ne("id", excludedPileId);
        }
        return chargingPileMapper.selectList(wrapper);
    }

    private void restoreFaultQueue(PileQueue sourceQueue) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("request_id", sourceQueue.getRequestId()).last("limit 1");
        PileQueue existingQueue = pileQueueMapper.selectOne(wrapper);

        if (existingQueue == null) {
            existingQueue = new PileQueue();
            existingQueue.setPileId(sourceQueue.getPileId());
            existingQueue.setRequestId(sourceQueue.getRequestId());
            existingQueue.setPositionNo(sourceQueue.getPositionNo());
            existingQueue.setStatus("FAULT_STOPPED");
            pileQueueMapper.insert(existingQueue);
            return;
        }

        existingQueue.setPileId(sourceQueue.getPileId());
        existingQueue.setPositionNo(sourceQueue.getPositionNo());
        existingQueue.setStatus("FAULT_STOPPED");
        pileQueueMapper.updateById(existingQueue);
    }

    private boolean hasUnchargedQueueOnOtherSameTypePiles(ChargingPile recoveredPile) {
        List<ChargingPile> sameTypePiles = selectSameTypeAvailablePiles(recoveredPile.getType(), recoveredPile.getId());
        for (ChargingPile pile : sameTypePiles) {
            for (PileQueue queue : selectPileQueues(pile.getId())) {
                if (!isCharging(queue)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<PileQueue> selectPileQueues(Long pileId) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId).orderByAsc("position_no");
        return pileQueueMapper.selectList(wrapper);
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
        List<PileQueue> queues = selectPileQueues(pile.getId());
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

    private int queueNumberOrder(Long requestId) {
        ChargingRequest request = chargingRequestMapper.selectById(requestId);
        if (request == null || request.getQueueNumber() == null) {
            return Integer.MAX_VALUE;
        }

        String digits = request.getQueueNumber().replaceAll("\\D+", "");
        if (digits.isBlank()) {
            return Integer.MAX_VALUE;
        }

        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private boolean hasRemainingFaultQueue(Long pileId) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId);
        return pileQueueMapper.selectCount(wrapper) > 0;
    }

    private boolean isCharging(PileQueue queue) {
        return "CHARGING".equalsIgnoreCase(queue.getStatus());
    }

    private boolean isCompleted(ChargingRequest request) {
        return "COMPLETED".equalsIgnoreCase(request.getStatus());
    }

    private String normalizeFaultPolicy(String policy, String defaultPolicy) {
        if (policy == null || policy.isBlank()) {
            return defaultPolicy;
        }

        if (POLICY_TIME_ORDER.equalsIgnoreCase(policy)) {
            return POLICY_TIME_ORDER;
        }
        return POLICY_PRIORITY;
    }

    private FaultLog selectLatestUnrecoveredFault(Long pileId) {
        QueryWrapper<FaultLog> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId)
                .isNull("recover_time")
                .orderByDesc("fault_time")
                .last("limit 1");
        return faultLogMapper.selectOne(wrapper);
    }

    private void updateLatestFaultPolicy(Long pileId, String policy) {
        FaultLog faultLog = selectLatestUnrecoveredFault(pileId);
        if (faultLog != null) {
            faultLog.setSchedulePolicy(policy);
            faultLogMapper.updateById(faultLog);
        }
    }

    private Map<String, Object> buildVehicleData(ChargingRequest request, String status, Long fromPileId, Long toPileId) {
        return buildVehicleData(request, status, fromPileId, toPileId, null);
    }

    private Map<String, Object> buildVehicleData(ChargingRequest request, String status, Long fromPileId, Long toPileId, String from) {
        Map<String, Object> data = new HashMap<>();
        data.put("queueNumber", request.getQueueNumber());
        data.put("userId", request.getUserId());
        data.put("requestId", request.getId());
        data.put("mode", request.getMode());
        data.put("status", status);
        data.put("fromPileId", fromPileId);
        data.put("toPileId", toPileId);
        if (from != null) {
            data.put("from", from);
        }
        return data;
    }

    private static class RequeueCandidate {
        private final ChargingRequest request;
        private final Long fromPileId;

        private RequeueCandidate(ChargingRequest request, Long fromPileId) {
            this.request = request;
            this.fromPileId = fromPileId;
        }
    }
}
