package com.charging.service;

import com.charging.common.Result;

public interface FaultService {
    Result simulate(Long pileId, String schedulePolicy, String remark);

    Result dispatchFault(Long pileId, String schedulePolicy);

    Result recover(Long pileId, String schedulePolicy);
}
