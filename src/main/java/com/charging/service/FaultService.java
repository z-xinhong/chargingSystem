package com.charging.service;

import com.charging.common.Result;

public interface FaultService {
    Result simulate(Long pileId, String schedulePolicy, String remark);

    Result recover(Long pileId);
}
