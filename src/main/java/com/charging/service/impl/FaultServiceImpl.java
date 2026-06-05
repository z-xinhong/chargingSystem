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
import com.charging.mapper.WaitingQueueMapper;
import com.charging.service.FaultService;
import com.charging.service.ScheduleService;
import com.charging.entity.WaitingQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FaultServiceImpl implements FaultService {

    @Autowired
    private ChargingPileMapper chargingPileMapper;

    @Autowired
    private PileQueueMapper pileQueueMapper;

    @Autowired
    private ChargingRequestMapper chargingRequestMapper;

    @Autowired
    private FaultLogMapper faultLogMapper;

    @Autowired
    private WaitingQueueMapper waitingQueueMapper;

    @Autowired
    private ScheduleService scheduleService;

    @Override
    @Transactional
    public Result simulate(Long pileId, String schedulePolicy, String remark) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            return Result.error("充电桩不存在");
        }

        String policy = schedulePolicy == null || schedulePolicy.isBlank() ? "PRIORITY" : schedulePolicy;

        pile.setStatus("FAULT");
        chargingPileMapper.updateById(pile);

        FaultLog faultLog = new FaultLog();
        faultLog.setPileId(pileId);
        faultLog.setFaultTime(LocalDateTime.now());
        faultLog.setSchedulePolicy(policy);
        faultLog.setRemark(remark);
        faultLogMapper.insert(faultLog);

        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId);
        List<PileQueue> affectedQueues = pileQueueMapper.selectList(wrapper);

        for (PileQueue queue : affectedQueues) {
            ChargingRequest request = chargingRequestMapper.selectById(queue.getRequestId());
            if (request != null) {
                request.setStatus("WAITING");
                chargingRequestMapper.updateById(request);

                WaitingQueue waitingQueue = new WaitingQueue();
                waitingQueue.setRequestId(request.getId());
                waitingQueue.setQueueNumber(request.getQueueNumber());
                waitingQueue.setMode(request.getMode());
                waitingQueue.setPositionNo(nextWaitingQueuePosition(request.getMode()));
                waitingQueueMapper.insert(waitingQueue);
            }

            pileQueueMapper.deleteById(queue.getId());
        }

        scheduleService.dispatch(policy);

        return Result.success(faultLog);
    }

    @Override
    @Transactional
    public Result recover(Long pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            return Result.error("充电桩不存在");
        }

        pile.setStatus("IDLE");
        chargingPileMapper.updateById(pile);

        QueryWrapper<FaultLog> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId)
                .isNull("recover_time")
                .orderByDesc("fault_time")
                .last("limit 1");
        FaultLog faultLog = faultLogMapper.selectOne(wrapper);
        if (faultLog != null) {
            faultLog.setRecoverTime(LocalDateTime.now());
            faultLogMapper.updateById(faultLog);
        }

        scheduleService.dispatch("BATCH_SHORTEST");
        return Result.success(pile);
    }

    private int nextWaitingQueuePosition(String mode) {
        QueryWrapper<WaitingQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("mode", mode).orderByDesc("position_no").last("limit 1");
        WaitingQueue last = waitingQueueMapper.selectOne(wrapper);
        return last == null || last.getPositionNo() == null ? 1 : last.getPositionNo() + 1;
    }
}
