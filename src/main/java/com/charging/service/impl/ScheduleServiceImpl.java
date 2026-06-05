package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charging.common.Result;
import com.charging.entity.ChargingPile;
import com.charging.entity.ChargingRequest;
import com.charging.entity.PileQueue;
import com.charging.entity.WaitingQueue;
import com.charging.mapper.ChargingPileMapper;
import com.charging.mapper.ChargingRequestMapper;
import com.charging.mapper.PileQueueMapper;
import com.charging.mapper.WaitingQueueMapper;
import com.charging.service.ScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

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

    @Override
    @Transactional
    public Result dispatch(String policy) {
        String normalizedPolicy = policy == null || policy.isBlank() ? "BATCH_SHORTEST" : policy;

        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("position_no");
        List<WaitingQueue> waitingQueues = waitingQueueMapper.selectList(wrapper);

        if ("PRIORITY".equalsIgnoreCase(normalizedPolicy)) {
            waitingQueues.sort(Comparator.comparing((WaitingQueue item) -> !"FAST".equalsIgnoreCase(item.getMode()))
                    .thenComparing(WaitingQueue::getPositionNo, Comparator.nullsLast(Integer::compareTo)));
        }

        int dispatchLimit = "SINGLE_SHORTEST".equalsIgnoreCase(normalizedPolicy) ? 1 : waitingQueues.size();
        int dispatchedCount = 0;

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
            dispatchedCount++;
        }

        return Result.success(dispatchedCount);
    }

    @Override
    public Result waitingQueue() {
        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("mode", "position_no");
        return Result.success(waitingQueueMapper.selectList(wrapper));
    }

    @Override
    public Result pileQueue() {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("pile_id", "position_no");
        return Result.success(pileQueueMapper.selectList(wrapper));
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
}
