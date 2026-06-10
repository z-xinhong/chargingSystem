package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charging.common.Result;
import com.charging.dto.ModifyRequestDTO;
import com.charging.dto.RequestDTO;
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
import com.charging.service.ChargingRequestService;
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
import java.util.List;
import java.util.Map;

@Service
public class ChargingRequestServiceImpl implements ChargingRequestService {

    private static final int WAITING_AREA_SIZE = 5;

    @Autowired
    private ChargingRequestMapper mapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WaitingQueueMapper waitingQueueMapper;

    @Autowired
    private PileQueueMapper pileQueueMapper;

    @Autowired
    private ChargingPileMapper chargingPileMapper;

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private BillingService billingService;

    @Override
    @Transactional
    public Result submit(Long userId, RequestDTO dto) {
        Result validation = validateRequest(userId, dto == null ? null : dto.getMode(), dto == null ? null : dto.getRequestedKwh());
        if (validation != null) {
            return validation;
        }
        if (waitingAreaCount() >= WAITING_AREA_SIZE) {
            return Result.error("等候区已满，无法提交新的充电请求");
        }

        ChargingRequest req = new ChargingRequest();
        req.setUserId(userId);
        req.setMode(normalizeMode(dto.getMode()));
        req.setRequestedKwh(dto.getRequestedKwh());
        req.setQueueType(req.getMode());
        req.setStatus("WAITING");
        req.setQueueNumber(nextQueueNumber(req.getMode()));
        req.setCreatedAt(LocalDateTime.now());
        mapper.insert(req);

        addToWaitingQueue(req);
        if (!systemConfigService.isCallingPaused()) {
            scheduleService.dispatch("BATCH_SHORTEST");
        }

        ChargingRequest latest = mapper.selectById(req.getId());
        return Result.success(toQueueResponse(latest == null ? req : latest, false));
    }

    @Override
    @Transactional
    public Result modify(Long userId, ModifyRequestDTO dto) {
        if (dto == null || dto.getRequestId() == null) {
            return Result.error("参数错误");
        }

        ChargingRequest req = mapper.selectById(dto.getRequestId());
        Result access = validateAccessibleRequest(userId, req);
        if (access != null) {
            return access;
        }
        if (!"WAITING".equals(req.getStatus())) {
            return Result.error("只能修改等待中的充电请求");
        }
        if (findPileQueue(req.getId()) != null) {
            return Result.error("已进入充电桩队列的请求不能修改");
        }
        if (findWaitingQueue(req.getId()) == null) {
            return Result.error("只能修改等待区中的充电请求");
        }
        if (dto.getMode() == null && dto.getRequestedKwh() == null) {
            return Result.error("mode 和 requestedKwh 至少填写一个");
        }

        String oldMode = req.getMode();
        String newMode = dto.getMode() == null ? req.getMode() : dto.getMode();
        Double newKwh = dto.getRequestedKwh() == null ? req.getRequestedKwh() : dto.getRequestedKwh();
        Result validation = validateRequest(userId, newMode, newKwh);
        if (validation != null) {
            return validation;
        }

        String normalizedMode = normalizeMode(newMode);
        boolean modeChanged = !normalizedMode.equals(req.getMode());
        req.setMode(normalizedMode);
        req.setQueueType(normalizedMode);
        req.setRequestedKwh(newKwh);
        if (modeChanged) {
            req.setQueueNumber(nextQueueNumber(normalizedMode));
        }
        mapper.updateById(req);

        removeFromWaitingQueue(req.getId());
        reindexWaitingQueue(oldMode);
        addToWaitingQueue(req);
        if (!systemConfigService.isCallingPaused()) {
            scheduleService.dispatch("BATCH_SHORTEST");
        }

        ChargingRequest latest = mapper.selectById(req.getId());
        return Result.success(toQueueResponse(latest == null ? req : latest, false));
    }

    @Override
    @Transactional
    public Result cancel(Long userId, Long requestId) {
        ChargingRequest req = mapper.selectById(requestId);
        Result access = validateAccessibleRequest(userId, req);
        if (access != null) {
            return access;
        }
        if ("COMPLETED".equals(req.getStatus())) {
            return Result.error("已完成的充电请求不能取消");
        }
        if ("CANCELLED".equals(req.getStatus())) {
            return Result.success();
        }

        PileQueue pileQueue = findPileQueue(requestId);
        if (pileQueue != null) {
            if ("CHARGING".equalsIgnoreCase(pileQueue.getStatus()) || "CHARGING".equalsIgnoreCase(req.getStatus())) {
                return Result.error("正在充电的请求不能取消，请使用结束充电生成详单");
            }
            pileQueueMapper.deleteById(pileQueue.getId());
            reindexPileQueue(pileQueue.getPileId());
            updatePileStatusAfterCancel(pileQueue.getPileId());
            scheduleService.dispatch("BATCH_SHORTEST");
        } else {
            removeFromWaitingQueue(requestId);
            reindexWaitingQueue(req.getMode());
        }

        req.setStatus("CANCELLED");
        mapper.updateById(req);
        return Result.success();
    }

    @Override
    public Result status(Long userId, Long requestId) {
        ChargingRequest req = mapper.selectById(requestId);
        Result access = validateAccessibleRequest(userId, req);
        if (access != null) {
            return access;
        }

        Long billId = billingService.completeIfFullyCharged(requestId);
        if (billId != null) {
            scheduleService.dispatch("BATCH_SHORTEST");
            req = mapper.selectById(requestId);
        }
        Map<String, Object> data = toQueueResponse(req, true);
        if (billId != null) {
            data.put("autoCompleted", true);
            data.put("generatedBillId", billId);
        }
        return Result.success(data);
    }

    @Override
    @Transactional
    public Result listActive(Long userId) {
        QueryWrapper<ChargingRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .notIn("status", "COMPLETED", "CANCELLED")
                .orderByDesc("created_at");
        List<ChargingRequest> requests = mapper.selectList(wrapper);

        boolean completedAny = false;
        for (ChargingRequest request : requests) {
            if (billingService.completeIfFullyCharged(request.getId()) != null) {
                completedAny = true;
            }
        }
        if (completedAny) {
            scheduleService.dispatch("BATCH_SHORTEST");
            requests = mapper.selectList(wrapper);
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (ChargingRequest request : requests) {
            data.add(toQueueResponse(request, true));
        }
        return Result.success(data);
    }

    private Result validateRequest(Long userId, String mode, Double requestedKwh) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        String normalizedMode = normalizeMode(mode);
        if (!"FAST".equals(normalizedMode) && !"SLOW".equals(normalizedMode)) {
            return Result.error("充电模式必须为 FAST 或 SLOW");
        }
        if (requestedKwh == null || requestedKwh <= 0) {
            return Result.error("请求充电量必须大于 0");
        }
        if (user.getBatteryCapacity() != null && requestedKwh > user.getBatteryCapacity()) {
            return Result.error("请求充电量不能超过电池容量");
        }
        return null;
    }

    private Result validateAccessibleRequest(Long userId, ChargingRequest req) {
        if (req == null) {
            return Result.error("充电请求不存在");
        }
        if (!req.getUserId().equals(userId)) {
            return Result.error("无权访问该充电请求");
        }
        return null;
    }

    private String normalizeMode(String mode) {
        return mode == null ? null : mode.trim().toUpperCase();
    }

    private String nextQueueNumber(String mode) {
        String prefix = "FAST".equals(mode) ? "F" : "T";
        QueryWrapper<ChargingRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("queue_type", mode);
        return prefix + (mapper.selectCount(wrapper) + 1);
    }

    private void addToWaitingQueue(ChargingRequest req) {
        WaitingQueue waitingQueue = new WaitingQueue();
        waitingQueue.setRequestId(req.getId());
        waitingQueue.setQueueNumber(req.getQueueNumber());
        waitingQueue.setMode(req.getMode());
        waitingQueue.setPositionNo(nextWaitingPosition(req.getMode()));
        waitingQueueMapper.insert(waitingQueue);
    }

    private long waitingAreaCount() {
        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        return waitingQueueMapper.selectCount(wrapper);
    }

    private void removeFromWaitingQueue(Long requestId) {
        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("request_id", requestId);
        waitingQueueMapper.delete(wrapper);
    }

    private WaitingQueue findWaitingQueue(Long requestId) {
        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("request_id", requestId).last("limit 1");
        return waitingQueueMapper.selectOne(wrapper);
    }

    private PileQueue findPileQueue(Long requestId) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("request_id", requestId).last("limit 1");
        return pileQueueMapper.selectOne(wrapper);
    }

    private int nextWaitingPosition(String mode) {
        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("mode", mode).orderByDesc("position_no").last("limit 1");
        WaitingQueue last = waitingQueueMapper.selectOne(wrapper);
        return last == null || last.getPositionNo() == null ? 1 : last.getPositionNo() + 1;
    }

    private void reindexWaitingQueue(String mode) {
        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("mode", mode).orderByAsc("position_no", "id");
        List<WaitingQueue> queues = waitingQueueMapper.selectList(wrapper);
        for (int i = 0; i < queues.size(); i++) {
            WaitingQueue queue = queues.get(i);
            int position = i + 1;
            if (queue.getPositionNo() == null || queue.getPositionNo() != position) {
                queue.setPositionNo(position);
                waitingQueueMapper.updateById(queue);
            }
        }
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

            ChargingRequest request = mapper.selectById(queue.getRequestId());
            if (request != null && !"CANCELLED".equals(request.getStatus()) && !"COMPLETED".equals(request.getStatus())) {
                request.setStatus(status);
                if ("CHARGING".equals(status)) {
                    request.setCreatedAt(LocalDateTime.now());
                }
                mapper.updateById(request);
            }
        }
    }

    private void updatePileStatusAfterCancel(Long pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null || "FAULT".equals(pile.getStatus()) || "OFFLINE".equals(pile.getStatus())) {
            return;
        }

        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId);
        long remaining = pileQueueMapper.selectCount(wrapper);
        pile.setStatus(remaining > 0 ? "CHARGING" : "IDLE");
        chargingPileMapper.updateById(pile);
    }

    private int waitingCount(ChargingRequest req) {
        WaitingQueue currentWaiting = findWaitingQueue(req.getId());
        if (currentWaiting != null && currentWaiting.getPositionNo() != null) {
            QueryWrapper<WaitingQueue> countWrapper = new QueryWrapper<>();
            countWrapper.eq("mode", req.getMode()).lt("position_no", currentWaiting.getPositionNo());
            return Math.toIntExact(waitingQueueMapper.selectCount(countWrapper));
        }

        PileQueue currentPileQueue = findPileQueue(req.getId());
        if (currentPileQueue == null || currentPileQueue.getPositionNo() == null) {
            return 0;
        }
        return Math.max(currentPileQueue.getPositionNo() - 1, 0);
    }

    private Map<String, Object> toQueueResponse(ChargingRequest req, boolean includeEstimate) {
        int waitingCount = waitingCount(req);
        Map<String, Object> data = new HashMap<>();
        data.put("requestId", req.getId());
        data.put("queueNumber", req.getQueueNumber());
        data.put("queueType", req.getQueueType());
        data.put("waitingCount", waitingCount);
        data.put("status", req.getStatus());
        data.put("requestedKwh", req.getRequestedKwh());
        data.put("location", requestLocation(req));
        if (includeEstimate) {
            data.put("estimatedWaitMinutes", estimateWaitMinutes(req));
            data.putAll(buildChargingProgress(req));
        }
        return data;
    }

    private String requestLocation(ChargingRequest request) {
        PileQueue pileQueue = findPileQueue(request.getId());
        if (pileQueue != null) {
            return "CHARGING".equalsIgnoreCase(pileQueue.getStatus()) ? "CHARGING_AREA" : "PILE_QUEUE";
        }
        if (findWaitingQueue(request.getId()) != null) {
            return "WAITING_AREA";
        }
        return "NONE";
    }

    private double estimateWaitMinutes(ChargingRequest request) {
        PileQueue pileQueue = findPileQueue(request.getId());
        if (pileQueue != null) {
            return estimateWaitMinutesInPileQueue(pileQueue);
        }

        WaitingQueue waitingQueue = findWaitingQueue(request.getId());
        if (waitingQueue != null) {
            return estimateWaitMinutesInWaitingArea(request, waitingQueue);
        }

        return 0;
    }

    private double estimateWaitMinutesInPileQueue(PileQueue currentQueue) {
        if ("CHARGING".equalsIgnoreCase(currentQueue.getStatus())) {
            return 0;
        }

        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", currentQueue.getPileId())
                .lt("position_no", currentQueue.getPositionNo())
                .orderByAsc("position_no", "id");
        double waitMinutes = 0;
        for (PileQueue queue : pileQueueMapper.selectList(wrapper)) {
            ChargingRequest request = mapper.selectById(queue.getRequestId());
            ChargingPile pile = chargingPileMapper.selectById(queue.getPileId());
            waitMinutes += "CHARGING".equalsIgnoreCase(queue.getStatus())
                    ? estimateRemainingMinutes(pile, request)
                    : estimateFullMinutes(pile, request);
        }
        return roundTwo(waitMinutes);
    }

    private double estimateWaitMinutesInWaitingArea(ChargingRequest targetRequest, WaitingQueue targetWaitingQueue) {
        List<ChargingPile> piles = selectAvailablePiles(targetRequest.getMode());
        if (piles.isEmpty()) {
            return 0;
        }

        List<PileLoad> loads = new ArrayList<>();
        for (ChargingPile pile : piles) {
            loads.add(new PileLoad(pile, estimatePileLoadMinutes(pile)));
        }

        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("mode", targetRequest.getMode()).orderByAsc("position_no", "id");
        for (WaitingQueue waitingQueue : waitingQueueMapper.selectList(wrapper)) {
            ChargingRequest request = mapper.selectById(waitingQueue.getRequestId());
            if (request == null) {
                continue;
            }

            PileLoad bestLoad = loads.stream()
                    .min(Comparator.comparingDouble(load -> load.minutes))
                    .orElse(null);
            if (bestLoad == null) {
                return 0;
            }
            if (waitingQueue.getId().equals(targetWaitingQueue.getId())) {
                return roundTwo(bestLoad.minutes);
            }
            bestLoad.minutes += estimateFullMinutes(bestLoad.pile, request);
        }
        return 0;
    }

    private Map<String, Object> buildChargingProgress(ChargingRequest request) {
        Map<String, Object> data = new HashMap<>();
        double requestedKwh = request.getRequestedKwh() == null ? 0 : request.getRequestedKwh();
        if ("COMPLETED".equalsIgnoreCase(request.getStatus())) {
            data.put("chargedKwh", requestedKwh);
            data.put("remainingKwh", 0);
            data.put("remainingMinutes", 0);
            return data;
        }

        PileQueue pileQueue = findPileQueue(request.getId());
        ChargingPile pile = pileQueue == null ? null : chargingPileMapper.selectById(pileQueue.getPileId());

        if (pileQueue == null || pile == null || !"CHARGING".equalsIgnoreCase(pileQueue.getStatus())) {
            data.put("chargedKwh", 0);
            data.put("remainingKwh", requestedKwh);
            data.put("remainingMinutes", estimateFullMinutes(pile, request));
            return data;
        }

        double power = pile.getPower() == null || pile.getPower() <= 0 ? 1 : pile.getPower();
        LocalDateTime startTime = request.getCreatedAt() == null ? LocalDateTime.now() : request.getCreatedAt();
        double elapsedHours = Math.max(0, Duration.between(startTime, LocalDateTime.now()).getSeconds()) / 3600.0;
        double chargedKwh = Math.min(requestedKwh, elapsedHours * power);
        double remainingKwh = Math.max(0, requestedKwh - chargedKwh);

        data.put("chargedKwh", roundOne(chargedKwh));
        data.put("remainingKwh", roundOne(remainingKwh));
        data.put("remainingMinutes", roundTwo(remainingKwh / power * 60));
        return data;
    }

    private double estimateFullMinutes(ChargingPile pile, ChargingRequest request) {
        if (request == null || request.getRequestedKwh() == null) {
            return 0;
        }
        double power = pile == null || pile.getPower() == null || pile.getPower() <= 0
                ? ("FAST".equals(request.getMode()) ? 30.0 : 10.0)
                : pile.getPower();
        return roundTwo(request.getRequestedKwh() / power * 60);
    }

    private double estimateRemainingMinutes(ChargingPile pile, ChargingRequest request) {
        if (request == null || request.getRequestedKwh() == null) {
            return 0;
        }

        double power = pile == null || pile.getPower() == null || pile.getPower() <= 0
                ? ("FAST".equals(request.getMode()) ? 30.0 : 10.0)
                : pile.getPower();
        LocalDateTime startTime = request.getCreatedAt() == null ? LocalDateTime.now() : request.getCreatedAt();
        double elapsedHours = Math.max(0, Duration.between(startTime, LocalDateTime.now()).getSeconds()) / 3600.0;
        double remainingKwh = Math.max(0, request.getRequestedKwh() - elapsedHours * power);
        return roundTwo(remainingKwh / power * 60);
    }

    private double estimatePileLoadMinutes(ChargingPile pile) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pile.getId()).orderByAsc("position_no", "id");
        double minutes = 0;
        for (PileQueue queue : pileQueueMapper.selectList(wrapper)) {
            ChargingRequest request = mapper.selectById(queue.getRequestId());
            minutes += "CHARGING".equalsIgnoreCase(queue.getStatus())
                    ? estimateRemainingMinutes(pile, request)
                    : estimateFullMinutes(pile, request);
        }
        return minutes;
    }

    private List<ChargingPile> selectAvailablePiles(String mode) {
        QueryWrapper<ChargingPile> wrapper = new QueryWrapper<>();
        wrapper.eq("type", mode)
                .ne("status", "FAULT")
                .ne("status", "OFFLINE")
                .orderByAsc("id");
        return chargingPileMapper.selectList(wrapper);
    }

    private double roundOne(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundTwo(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static class PileLoad {
        private final ChargingPile pile;
        private double minutes;

        private PileLoad(ChargingPile pile, double minutes) {
            this.pile = pile;
            this.minutes = minutes;
        }
    }
}
