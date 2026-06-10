package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charging.common.Result;
import com.charging.entity.Bill;
import com.charging.entity.ChargingPile;
import com.charging.entity.ChargingRequest;
import com.charging.entity.PileQueue;
import com.charging.entity.User;
import com.charging.entity.WaitingQueue;
import com.charging.mapper.BillMapper;
import com.charging.mapper.ChargingPileMapper;
import com.charging.mapper.ChargingRequestMapper;
import com.charging.mapper.PileQueueMapper;
import com.charging.mapper.UserMapper;
import com.charging.mapper.WaitingQueueMapper;
import com.charging.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class VehicleServiceImpl implements VehicleService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ChargingRequestMapper chargingRequestMapper;

    @Autowired
    private WaitingQueueMapper waitingQueueMapper;

    @Autowired
    private PileQueueMapper pileQueueMapper;

    @Autowired
    private ChargingPileMapper chargingPileMapper;

    @Autowired
    private BillMapper billMapper;

    @Override
    public Result list(String keyword) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("role", "USER");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(item -> item.like("username", keyword)
                    .or().like("phone", keyword)
                    .or().like("plate_no", keyword));
        }
        wrapper.orderByAsc("id");

        List<Map<String, Object>> data = new ArrayList<>();
        for (User user : userMapper.selectList(wrapper)) {
            data.add(toVehicleResponse(user));
        }
        return Result.success(data);
    }

    @Override
    public Result save(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return Result.error("用户名不能为空");
        }

        if (user.getId() == null) {
            if (user.getPassword() == null || user.getPassword().isBlank()) {
                user.setPassword("123456");
            }
            user.setRole("USER");
            userMapper.insert(user);
        } else {
            User old = userMapper.selectById(user.getId());
            if (old == null) {
                return Result.error("用户不存在");
            }
            old.setUsername(user.getUsername());
            old.setPhone(user.getPhone());
            old.setPlateNo(user.getPlateNo());
            old.setBatteryCapacity(user.getBatteryCapacity());
            old.setRole("USER");
            userMapper.updateById(old);
            user = old;
        }

        return Result.success(toVehicleResponse(user));
    }

    @Override
    @Transactional
    public Result delete(Long userId) {
        if (userId == null) {
            return Result.error("用户 ID 不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        Set<Long> affectedPileIds = new HashSet<>();
        QueryWrapper<ChargingRequest> requestWrapper = new QueryWrapper<>();
        requestWrapper.eq("user_id", userId);
        List<ChargingRequest> requests = chargingRequestMapper.selectList(requestWrapper);

        for (ChargingRequest request : requests) {
            QueryWrapper<Bill> billWrapper = new QueryWrapper<>();
            billWrapper.eq("request_id", request.getId());
            billMapper.delete(billWrapper);

            QueryWrapper<WaitingQueue> waitingWrapper = new QueryWrapper<>();
            waitingWrapper.eq("request_id", request.getId());
            waitingQueueMapper.delete(waitingWrapper);

            QueryWrapper<PileQueue> pileQueueWrapper = new QueryWrapper<>();
            pileQueueWrapper.eq("request_id", request.getId());
            List<PileQueue> pileQueues = pileQueueMapper.selectList(pileQueueWrapper);
            for (PileQueue queue : pileQueues) {
                affectedPileIds.add(queue.getPileId());
                pileQueueMapper.deleteById(queue.getId());
            }

            chargingRequestMapper.deleteById(request.getId());
        }

        userMapper.deleteById(userId);
        for (Long pileId : affectedPileIds) {
            reindexPileQueue(pileId);
            updatePileStatus(pileId);
        }

        return Result.success();
    }

    private Map<String, Object> toVehicleResponse(User user) {
        ChargingRequest request = currentRequest(user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("phone", user.getPhone());
        data.put("plateNo", user.getPlateNo());
        data.put("batteryCapacity", user.getBatteryCapacity());
        data.put("currentRequest", request == null ? null : request.getQueueNumber());
        data.put("status", request == null ? "IDLE" : request.getStatus());
        return data;
    }

    private ChargingRequest currentRequest(Long userId) {
        QueryWrapper<ChargingRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .notIn("status", "COMPLETED", "CANCELLED")
                .orderByDesc("created_at")
                .last("limit 1");
        return chargingRequestMapper.selectOne(wrapper);
    }

    private void reindexPileQueue(Long pileId) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId).orderByAsc("position_no", "id");
        List<PileQueue> queues = pileQueueMapper.selectList(wrapper);

        for (int i = 0; i < queues.size(); i++) {
            PileQueue queue = queues.get(i);
            queue.setPositionNo(i + 1);
            queue.setStatus(i == 0 ? "CHARGING" : "WAITING");
            pileQueueMapper.updateById(queue);

            ChargingRequest request = chargingRequestMapper.selectById(queue.getRequestId());
            if (request != null && !"COMPLETED".equals(request.getStatus()) && !"CANCELLED".equals(request.getStatus())) {
                request.setStatus(queue.getStatus());
                if ("CHARGING".equals(queue.getStatus())) {
                    request.setCreatedAt(java.time.LocalDateTime.now());
                }
                chargingRequestMapper.updateById(request);
            }
        }
    }

    private void updatePileStatus(Long pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null || "FAULT".equals(pile.getStatus()) || "OFFLINE".equals(pile.getStatus())) {
            return;
        }

        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId);
        long count = pileQueueMapper.selectCount(wrapper);
        pile.setStatus(count > 0 ? "CHARGING" : "IDLE");
        chargingPileMapper.updateById(pile);
    }
}
