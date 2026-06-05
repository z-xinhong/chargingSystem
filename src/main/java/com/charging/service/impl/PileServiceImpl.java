package com.charging.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.charging.common.Result;
import com.charging.entity.ChargingPile;
import com.charging.entity.PileQueue;
import com.charging.mapper.ChargingPileMapper;
import com.charging.mapper.PileQueueMapper;
import com.charging.service.PileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PileServiceImpl implements PileService {

    @Autowired
    private ChargingPileMapper chargingPileMapper;

    @Autowired
    private PileQueueMapper pileQueueMapper;

    @Override
    public Result status() {
        return Result.success(chargingPileMapper.selectList(null));
    }

    @Override
    public Result queue(Long pileId) {
        QueryWrapper<PileQueue> wrapper = new QueryWrapper<>();
        wrapper.eq("pile_id", pileId).orderByAsc("position_no");
        return Result.success(pileQueueMapper.selectList(wrapper));
    }

    @Override
    public Result start(Long pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            return Result.error("充电桩不存在");
        }

        pile.setStatus("IDLE");
        chargingPileMapper.updateById(pile);
        return Result.success(pile);
    }

    @Override
    public Result stop(Long pileId) {
        ChargingPile pile = chargingPileMapper.selectById(pileId);
        if (pile == null) {
            return Result.error("充电桩不存在");
        }

        pile.setStatus("OFFLINE");
        chargingPileMapper.updateById(pile);
        return Result.success(pile);
    }
}
