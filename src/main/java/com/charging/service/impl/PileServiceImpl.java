package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charging.common.Result;
import com.charging.entity.ChargingPile;
import com.charging.entity.ChargingRequest;
import com.charging.entity.PileQueue;
import com.charging.entity.User;
import com.charging.mapper.ChargingPileMapper;
import com.charging.mapper.ChargingRequestMapper;
import com.charging.mapper.PileQueueMapper;
import com.charging.mapper.UserMapper;
import com.charging.service.PileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PileServiceImpl implements PileService {

    @Autowired
    private ChargingPileMapper chargingPileMapper;

    @Autowired
    private PileQueueMapper pileQueueMapper;

    @Autowired
    private ChargingRequestMapper chargingRequestMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    public Result status() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (ChargingPile pile : chargingPileMapper.selectList(null)) {
            data.add(buildPileStatus(pile));
        }
        return Result.success(data);
    }

    @Override
    public Result queue(Long pileId) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId).orderByAsc("position_no");
        List<Map<String, Object>> data = new ArrayList<>();
        for (PileQueue queue : pileQueueMapper.selectList(wrapper)) {
            data.add(buildQueueVehicle(queue));
        }
        return Result.success(data);
    }

    @Override
    public Result start(Long pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            return Result.error("充电桩不存在");
        }

        pile.setStatus("IDLE");
        chargingPileMapper.updateById(pile);
        return Result.success();
    }

    @Override
    public Result stop(Long pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            return Result.error("充电桩不存在");
        }

        pile.setStatus("OFFLINE");
        chargingPileMapper.updateById(pile);
        return Result.success();
    }

    private Map<String, Object> buildPileStatus(ChargingPile pile) {
        Map<String, Object> data = new HashMap<>();
        data.put("pileId", pile.getId());
        data.put("name", pile.getPileCode() + ("FAST".equalsIgnoreCase(pile.getType()) ? " 快充桩" : " 慢充桩"));
        data.put("type", pile.getType());
        data.put("status", pile.getStatus());
        data.put("isWorking", !"FAULT".equalsIgnoreCase(pile.getStatus()) && !"OFFLINE".equalsIgnoreCase(pile.getStatus()));
        data.put("power", pile.getPower());
        data.put("totalCount", pile.getTotalChargeCount());
        data.put("totalDuration", pile.getTotalChargeTime());
        data.put("totalKwh", pile.getTotalChargeKwh());
        return data;
    }

    private Map<String, Object> buildQueueVehicle(PileQueue queue) {
        Map<String, Object> data = new HashMap<>();
        ChargingRequest request = chargingRequestMapper.selectById(queue.getRequestId());
        User user = request == null ? null : userMapper.selectById(request.getUserId());

        data.put("queueNumber", request == null ? null : request.getQueueNumber());
        data.put("userId", request == null ? null : request.getUserId());
        data.put("mode", request == null ? null : request.getMode());
        data.put("batteryCapacity", user == null ? null : user.getBatteryCapacity());
        data.put("requestedKwh", request == null ? null : request.getRequestedKwh());
        data.put("waitingMinutes", 0);
        data.put("estimatedFinishMinutes", estimateFinishMinutes(queue, request));
        data.put("status", queue.getStatus());
        return data;
    }

    private int estimateFinishMinutes(PileQueue queue, ChargingRequest request) {
        if (request == null || request.getRequestedKwh() == null) {
            return 0;
        }

        ChargingPile pile = chargingPileMapper.selectById(queue.getPileId());
        double power = pile == null || pile.getPower() == null || pile.getPower() <= 0 ? 1 : pile.getPower();
        return (int) Math.ceil(request.getRequestedKwh() / power * 60);
    }
}
