package com.charging.service;

import com.charging.common.Result;

public interface ScheduleService {
    Result dispatch(String policy);

    Result snapshot();

    Result waitingQueue();

    Result pileQueue();
}
