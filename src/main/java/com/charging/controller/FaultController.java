package com.charging.controller;

import com.charging.common.Result;
import com.charging.dto.FaultSimulateDTO;
import com.charging.service.FaultService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fault")
public class FaultController {

    @Autowired
    private FaultService faultService;

    @PostMapping("/simulate/{pileId}")
    public Result simulate(@PathVariable Long pileId,
                           @RequestBody(required = false) FaultSimulateDTO dto) {
        String schedulePolicy = dto == null ? null : dto.getSchedulePolicy();
        String remark = dto == null ? null : dto.getRemark();
        return faultService.simulate(pileId, schedulePolicy, remark);
    }

    @PostMapping("/recover/{pileId}")
    public Result recover(@PathVariable Long pileId) {
        return faultService.recover(pileId);
    }
}
