package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charging.common.Result;
import com.charging.dto.ModifyRequestDTO;
import com.charging.dto.RequestDTO;
import com.charging.entity.ChargingRequest;
import com.charging.entity.ChargingPile;
import com.charging.entity.PileQueue;
import com.charging.entity.User;
import com.charging.entity.WaitingQueue;
import com.charging.mapper.ChargingRequestMapper;
import com.charging.mapper.ChargingPileMapper;
import com.charging.mapper.PileQueueMapper;
import com.charging.mapper.UserMapper;
import com.charging.mapper.WaitingQueueMapper;
import com.charging.service.ChargingRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChargingRequestServiceImpl implements ChargingRequestService {

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

    @Override
    @Transactional
    public Result submit(Long userId, RequestDTO dto) {
        Result validation = validateRequest(userId, dto == null ? null : dto.getMode(), dto == null ? null : dto.getRequestedKwh());
        if (validation != null) {
            return validation;
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
        return Result.success(toQueueResponse(req, false));
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
            return Result.error("mode和requestedKwh至少填写一个");
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
        return Result.success(toQueueResponse(req, false));
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
            pileQueueMapper.deleteById(pileQueue.getId());
            reindexPileQueue(pileQueue.getPileId());
            updatePileStatusAfterCancel(pileQueue.getPileId());
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
        return Result.success(toQueueResponse(req, true));
    }

    private Result validateRequest(Long userId, String mode, Double requestedKwh) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        String normalizedMode = normalizeMode(mode);
        if (!"FAST".equals(normalizedMode) && !"SLOW".equals(normalizedMode)) {
            return Result.error("充电模式必须为FAST或SLOW");
        }
        if (requestedKwh == null || requestedKwh <= 0) {
            return Result.error("请求充电量必须大于0");
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
        if (includeEstimate) {
            data.put("estimatedWaitMinutes", waitingCount * ("FAST".equals(req.getMode()) ? 30 : 60));
        }
        return data;
    }
}
