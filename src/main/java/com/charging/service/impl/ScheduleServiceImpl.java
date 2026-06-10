package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charging.common.Result;
import com.charging.dto.AdminBulkRequestDTO;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScheduleServiceImpl implements ScheduleService {

    private static final int MAX_PILE_QUEUE_SIZE = 3;
    private static final int WAITING_AREA_SIZE = 5;
    private static final double FAST_POWER = 30.0;
    private static final String MODE_NORMAL = "NORMAL";
    private static final String MODE_SINGLE = "SINGLE_SHORTEST";
    private static final String MODE_BATCH = "BATCH_SHORTEST";
    private static final String STATUS_BATCH_PENDING = "BATCH_PENDING";

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
        String scheduleMode = systemConfigService.getScheduleMode();
        if (MODE_SINGLE.equals(scheduleMode)) {
            List<Map<String, Object>> assignedVehicles = dispatchSingleTotalShortest();
            return Result.success(assignedVehicles.size());
        }
        if (MODE_BATCH.equals(scheduleMode)) {
            List<Map<String, Object>> assignedVehicles = tryDispatchBatchTotalShortest();
            return Result.success(assignedVehicles.size());
        }

        String normalizedPolicy = normalizePolicy(policy, "BATCH_SHORTEST");
        if ("PRIORITY".equalsIgnoreCase(normalizedPolicy) || "TIME_ORDER".equalsIgnoreCase(normalizedPolicy)) {
            normalizedPolicy = "BATCH_SHORTEST";
        }
        List<Map<String, Object>> assignedVehicles = dispatchInternal(normalizedPolicy);
        return Result.success(assignedVehicles.size());
    }

    @Override
    public Result selectMode(String mode) {
        String normalizedMode = normalizeScheduleMode(mode);
        if (normalizedMode == null) {
            return Result.error("调度方式必须是 NORMAL、SINGLE_SHORTEST 或 BATCH_SHORTEST");
        }
        if (!systemConfigService.selectScheduleMode(normalizedMode)) {
            return Result.error("调度方式已锁定，本次后端启动期间不能再次修改");
        }
        return Result.success(Map.of(
                "scheduleMode", systemConfigService.getScheduleMode(),
                "locked", systemConfigService.isScheduleModeLocked()
        ));
    }

    @Override
    @Transactional
    public Result bulkCreateRequests(AdminBulkRequestDTO dto) {
        String scheduleMode = systemConfigService.getScheduleMode();
        if (MODE_NORMAL.equals(scheduleMode)) {
            return Result.error("正常调度模式不支持管理员批量创建请求");
        }
        if (dto == null || dto.getRequestedKwh() == null || dto.getRequestedKwh() <= 0
                || dto.getCount() == null || dto.getCount() <= 0) {
            return Result.error("充电度数和请求数量必须大于 0");
        }

        List<User> users = selectNormalUsers();
        if (users.isEmpty()) {
            return Result.error("没有可绑定的普通用户");
        }

        List<Map<String, Object>> created = new ArrayList<>();
        for (int i = 0; i < dto.getCount(); i++) {
            User user = users.get(i % users.size());
            ChargingRequest request = new ChargingRequest();
            request.setUserId(user.getId());
            request.setRequestedKwh(dto.getRequestedKwh());
            request.setCreatedAt(LocalDateTime.now());

            if (MODE_BATCH.equals(scheduleMode)) {
                request.setMode("FAST");
                request.setQueueType("FAST");
                request.setQueueNumber(nextQueueNumber("BATCH"));
                request.setStatus(STATUS_BATCH_PENDING);
                chargingRequestMapper.insert(request);
            } else {
                String requestMode = normalizeRequestMode(dto.getMode());
                if (requestMode == null) {
                    return Result.error("单次调度需要选择 FAST 或 SLOW");
                }
                request.setMode(requestMode);
                request.setQueueType(requestMode);
                request.setQueueNumber(nextQueueNumber(requestMode));
                request.setStatus("WAITING");
                chargingRequestMapper.insert(request);
                addToWaitingQueue(request);
            }

            created.add(requestData(request, user));
        }

        List<Map<String, Object>> assignedVehicles = MODE_BATCH.equals(scheduleMode)
                ? tryDispatchBatchTotalShortest()
                : dispatchSingleTotalShortest();

        Map<String, Object> data = new HashMap<>();
        data.put("createdCount", created.size());
        data.put("requests", created);
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
        snapshot.put("scheduleMode", systemConfigService.getScheduleMode());
        snapshot.put("scheduleModeLocked", systemConfigService.isScheduleModeLocked());
        snapshot.put("batchPending", buildBatchPending());
        snapshot.put("batchRequiredCount", batchRequiredCount());

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
            if (nextPosition == 1) {
                request.setCreatedAt(LocalDateTime.now());
            }
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

    private List<Map<String, Object>> dispatchSingleTotalShortest() {
        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("id");
        List<WaitingQueue> waitingQueues = waitingQueueMapper.selectList(wrapper);
        Map<Long, PileLoad> loads = buildPileLoads(false);
        List<Map<String, Object>> assignedVehicles = new ArrayList<>();

        for (String mode : List.of("FAST", "SLOW")) {
            List<WaitingQueue> candidates = waitingQueues.stream()
                    .filter(queue -> mode.equalsIgnoreCase(queue.getMode()))
                    .sorted(Comparator.comparingDouble(queue -> requestedKwh(queue.getRequestId())))
                    .toList();

            for (WaitingQueue waitingQueue : candidates) {
                ChargingRequest request = chargingRequestMapper.selectById(waitingQueue.getRequestId());
                if (request == null) {
                    waitingQueueMapper.deleteById(waitingQueue.getId());
                    continue;
                }

                PileLoad target = loads.values().stream()
                        .filter(load -> mode.equalsIgnoreCase(load.pile.getType()))
                        .filter(load -> load.availableSlots > 0)
                        .min(Comparator.comparingDouble(load -> load.loadHours + requestHours(request, load.pile)))
                        .orElse(null);
                if (target == null) {
                    continue;
                }

                assignedVehicles.add(assignToPileQueue(request, waitingQueue, target));
            }
        }

        reindexAllWaitingQueues();
        return assignedVehicles;
    }

    private List<Map<String, Object>> tryDispatchBatchTotalShortest() {
        List<ChargingRequest> pending = selectBatchPendingRequests();
        int requiredCount = batchRequiredCount();
        if (pending.size() < requiredCount) {
            return new ArrayList<>();
        }

        Map<Long, PileLoad> loads = buildPileLoads(true);
        List<ChargingRequest> candidates = pending.stream()
                .limit(requiredCount)
                .sorted(Comparator.comparingDouble(this::requestedKwh))
                .toList();
        List<Map<String, Object>> assignedVehicles = new ArrayList<>();
        int waitingAssigned = 0;

        for (ChargingRequest request : candidates) {
            PileLoad target = loads.values().stream()
                    .filter(load -> load.availableSlots > 0)
                    .min(Comparator.comparingDouble(load -> load.loadHours + requestHours(request, load.pile)))
                    .orElse(null);

            if (target != null) {
                assignedVehicles.add(assignToPileQueue(request, null, target));
            } else if (waitingAssigned < WAITING_AREA_SIZE) {
                PileLoad waitingTarget = loads.values().stream()
                        .min(Comparator.comparingDouble(load -> load.loadHours + requestHours(request, load.pile)))
                        .orElse(null);
                if (waitingTarget == null) {
                    break;
                }
                request.setMode("FAST");
                request.setQueueType("FAST");
                request.setStatus("WAITING");
                chargingRequestMapper.updateById(request);
                addToWaitingQueue(request);
                assignedVehicles.add(assignmentData(request, waitingTarget.pile, "BATCH_PENDING", "WAITING_AREA", waitingTarget.loadHours));
                waitingTarget.loadHours += requestHours(request, waitingTarget.pile);
                waitingAssigned++;
            } else {
                break;
            }
        }

        return assignedVehicles;
    }

    private Map<String, Object> assignToPileQueue(ChargingRequest request, WaitingQueue waitingQueue, PileLoad target) {
        int nextPosition = nextPileQueuePosition(target.pile.getId());
        PileQueue pileQueue = new PileQueue();
        pileQueue.setPileId(target.pile.getId());
        pileQueue.setRequestId(request.getId());
        pileQueue.setPositionNo(nextPosition);
        pileQueue.setStatus(nextPosition == 1 ? "CHARGING" : "WAITING");
        pileQueueMapper.insert(pileQueue);

        if (STATUS_BATCH_PENDING.equalsIgnoreCase(request.getStatus())) {
            request.setMode("FAST");
            request.setQueueType("FAST");
        } else {
            request.setMode(target.pile.getType());
            request.setQueueType(target.pile.getType());
        }
        request.setStatus(pileQueue.getStatus());
        if (nextPosition == 1) {
            request.setCreatedAt(LocalDateTime.now());
        }
        chargingRequestMapper.updateById(request);

        if (nextPosition == 1) {
            target.pile.setStatus("CHARGING");
            chargingPileMapper.updateById(target.pile);
        }
        if (waitingQueue != null) {
            waitingQueueMapper.deleteById(waitingQueue.getId());
        }

        Map<String, Object> assigned = assignmentData(request, target.pile, waitingQueue == null ? "BATCH_PENDING" : "WAITING_AREA", "PILE_QUEUE", target.loadHours);
        target.loadHours += requestHours(request, target.pile);
        target.availableSlots--;
        return assigned;
    }

    private Map<String, Object> assignmentData(ChargingRequest request, ChargingPile pile, String from, String to, double waitHours) {
        Map<String, Object> assigned = new HashMap<>();
        assigned.put("queueNumber", request.getQueueNumber());
        assigned.put("userId", request.getUserId());
        assigned.put("from", from);
        assigned.put("to", to);
        assigned.put("toPileId", pile.getId());
        assigned.put("estimatedWaitMinutes", roundTwo(waitHours * 60));
        assigned.put("estimatedFinishMinutes", roundTwo((waitHours + requestHours(request, pile)) * 60));
        return assigned;
    }

    private Map<Long, PileLoad> buildPileLoads(boolean includeAllTypes) {
        QueryWrapper<ChargingPile> wrapper = new QueryWrapper<>();
        wrapper.ne("status", "FAULT").ne("status", "OFFLINE").orderByAsc("id");
        List<ChargingPile> piles = chargingPileMapper.selectList(wrapper);

        Map<Long, PileLoad> loads = new LinkedHashMap<>();
        for (ChargingPile pile : piles) {
            int activeSize = activeQueueSize(pile.getId());
            int availableSlots = Math.max(0, MAX_PILE_QUEUE_SIZE - activeSize);
            loads.put(pile.getId(), new PileLoad(pile, estimatedPileChargeHours(pile), availableSlots));
        }
        return loads;
    }

    private List<ChargingRequest> selectBatchPendingRequests() {
        QueryWrapper<ChargingRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("status", STATUS_BATCH_PENDING).orderByAsc("id");
        return chargingRequestMapper.selectList(wrapper);
    }

    private List<Map<String, Object>> buildBatchPending() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (ChargingRequest request : selectBatchPendingRequests()) {
            data.add(requestData(request, userMapper.selectById(request.getUserId())));
        }
        return data;
    }

    private List<User> selectNormalUsers() {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("role", "USER").orderByAsc("id");
        return userMapper.selectList(wrapper);
    }

    private Map<String, Object> requestData(ChargingRequest request, User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", request.getId());
        data.put("queueNumber", request.getQueueNumber());
        data.put("userId", request.getUserId());
        data.put("username", user == null ? null : user.getUsername());
        data.put("mode", request.getMode());
        data.put("queueType", request.getQueueType());
        data.put("requestedKwh", request.getRequestedKwh());
        data.put("status", request.getStatus());
        return data;
    }

    private void addToWaitingQueue(ChargingRequest request) {
        WaitingQueue waitingQueue = new WaitingQueue();
        waitingQueue.setRequestId(request.getId());
        waitingQueue.setQueueNumber(request.getQueueNumber());
        waitingQueue.setMode(request.getMode());
        waitingQueue.setPositionNo(nextWaitingPosition(request.getMode()));
        waitingQueueMapper.insert(waitingQueue);
    }

    private int nextWaitingPosition(String mode) {
        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("mode", mode).orderByDesc("position_no").last("limit 1");
        WaitingQueue last = waitingQueueMapper.selectOne(wrapper);
        return last == null || last.getPositionNo() == null ? 1 : last.getPositionNo() + 1;
    }

    private String nextQueueNumber(String queueType) {
        String prefix = "FAST".equals(queueType) ? "F" : "SLOW".equals(queueType) ? "T" : "B";
        QueryWrapper<ChargingRequest> wrapper = new QueryWrapper<>();
        wrapper.likeRight("queue_number", prefix).select("queue_number");
        int maxNumber = 0;
        for (ChargingRequest request : chargingRequestMapper.selectList(wrapper)) {
            String queueNumber = request.getQueueNumber();
            if (queueNumber == null || !queueNumber.startsWith(prefix)) {
                continue;
            }
            try {
                maxNumber = Math.max(maxNumber, Integer.parseInt(queueNumber.substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // Ignore malformed queue numbers and continue from the valid max.
            }
        }
        return prefix + (maxNumber + 1);
    }

    private void reindexAllWaitingQueues() {
        reindexWaitingQueue("FAST");
        reindexWaitingQueue("SLOW");
    }

    private void reindexWaitingQueue(String mode) {
        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("mode", mode).orderByAsc("position_no", "id");
        List<WaitingQueue> queues = waitingQueueMapper.selectList(wrapper);
        for (int i = 0; i < queues.size(); i++) {
            WaitingQueue queue = queues.get(i);
            queue.setPositionNo(i + 1);
            waitingQueueMapper.updateById(queue);
        }
    }

    private int batchRequiredCount() {
        return Math.toIntExact(chargingPileMapper.selectCount(null)) * MAX_PILE_QUEUE_SIZE + WAITING_AREA_SIZE;
    }

    private double requestedKwh(Long requestId) {
        ChargingRequest request = chargingRequestMapper.selectById(requestId);
        return requestedKwh(request);
    }

    private double requestedKwh(ChargingRequest request) {
        return request == null || request.getRequestedKwh() == null ? 0 : request.getRequestedKwh();
    }

    private double requestHours(ChargingRequest request, ChargingPile pile) {
        double power = effectivePower(pile, request);
        return requestedKwh(request) / power;
    }

    private double effectivePower(ChargingPile pile, ChargingRequest request) {
        if (isBatchModeRequest(request)) {
            return FAST_POWER;
        }
        return pile == null || pile.getPower() == null || pile.getPower() <= 0 ? 1 : pile.getPower();
    }

    private boolean isBatchModeRequest(ChargingRequest request) {
        return request != null && request.getQueueNumber() != null && request.getQueueNumber().startsWith("B");
    }

    private String normalizeScheduleMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_NORMAL;
        }
        String normalized = mode.trim().toUpperCase();
        if ("NORMAL".equals(normalized)) {
            return MODE_NORMAL;
        }
        if ("SINGLE".equals(normalized) || MODE_SINGLE.equals(normalized)) {
            return MODE_SINGLE;
        }
        if ("BATCH".equals(normalized) || MODE_BATCH.equals(normalized)) {
            return MODE_BATCH;
        }
        return null;
    }

    private String normalizeRequestMode(String mode) {
        if (mode == null) {
            return null;
        }
        String normalized = mode.trim().toUpperCase();
        return "FAST".equals(normalized) || "SLOW".equals(normalized) ? normalized : null;
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
        boolean batchMode = MODE_BATCH.equals(systemConfigService.getScheduleMode());
        for (ChargingPile pile : chargingPileMapper.selectList(null)) {
            Map<String, Object> item = new HashMap<>();
            String type = batchMode ? "FAST" : pile.getType();
            item.put("pileId", pile.getId());
            item.put("name", pile.getPileCode() + ("FAST".equalsIgnoreCase(type) ? " 快充桩" : " 慢充桩"));
            item.put("type", type);
            item.put("status", pile.getStatus());
            item.put("power", batchMode ? FAST_POWER : pile.getPower());
            data.add(item);
        }
        return data;
    }

    private Map<String, List<Map<String, Object>>> buildPileQueues() {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.in("status", "CHARGING", "WAITING");
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
        wrapper.in("status", "CHARGING", "WAITING");
        return Math.toIntExact(pileQueueMapper.selectCount(wrapper));
    }

    private int nextPileQueuePosition(Long pileId) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId)
                .in("status", "CHARGING", "WAITING")
                .orderByDesc("position_no")
                .last("limit 1");
        PileQueue last = pileQueueMapper.selectOne(wrapper);
        return last == null || last.getPositionNo() == null ? 1 : last.getPositionNo() + 1;
    }

    private double estimatedPileChargeHours(ChargingPile pile) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pile.getId());
        wrapper.in("status", "CHARGING", "WAITING");
        List<PileQueue> queues = pileQueueMapper.selectList(wrapper);

        double totalHours = 0;

        for (PileQueue queue : queues) {
            ChargingRequest request = chargingRequestMapper.selectById(queue.getRequestId());
            if (request != null && request.getRequestedKwh() != null) {
                totalHours += request.getRequestedKwh() / effectivePower(pile, request);
            }
        }

        return totalHours;
    }

    private double estimateFinishMinutes(ChargingPile pile, ChargingRequest request) {
        if (request.getRequestedKwh() == null) {
            return 0;
        }
        double power = effectivePower(pile, request);
        return roundTwo(request.getRequestedKwh() / power * 60);
    }

    private double estimateFullMinutes(String mode, ChargingRequest request) {
        if (request == null || request.getRequestedKwh() == null) {
            return 0;
        }
        double power = "SLOW".equalsIgnoreCase(mode) ? 10.0 : 30.0;
        return roundTwo(request.getRequestedKwh() / power * 60);
    }

    private Map<String, Object> buildChargingProgress(PileQueue queue, ChargingPile pile, ChargingRequest request) {
        Map<String, Object> data = new HashMap<>();
        if (request == null || request.getRequestedKwh() == null || pile == null || !"CHARGING".equalsIgnoreCase(queue.getStatus())) {
            data.put("chargedKwh", 0);
            data.put("remainingKwh", request == null ? 0 : request.getRequestedKwh());
            data.put("remainingMinutes", pile == null || request == null ? 0 : estimateFinishMinutes(pile, request));
            return data;
        }

        double power = effectivePower(pile, request);
        LocalDateTime startTime = request.getCreatedAt() == null ? LocalDateTime.now() : request.getCreatedAt();
        double elapsedHours = Math.max(0, Duration.between(startTime, LocalDateTime.now()).getSeconds()) / 3600.0;
        double chargedKwh = Math.min(request.getRequestedKwh(), elapsedHours * power);
        double remainingKwh = Math.max(0, request.getRequestedKwh() - chargedKwh);

        data.put("chargedKwh", roundOne(chargedKwh));
        data.put("remainingKwh", roundOne(remainingKwh));
        data.put("remainingMinutes", roundTwo(remainingKwh / power * 60));
        return data;
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundTwo(double value) {
        return Math.round(value * 100.0) / 100.0;
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

    private static class PileLoad {
        private final ChargingPile pile;
        private double loadHours;
        private int availableSlots;

        private PileLoad(ChargingPile pile, double loadHours, int availableSlots) {
            this.pile = pile;
            this.loadHours = loadHours;
            this.availableSlots = availableSlots;
        }
    }
}
