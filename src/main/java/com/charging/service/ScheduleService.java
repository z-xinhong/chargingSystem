package com.charging.service;

import com.charging.common.Result;
import com.charging.dto.AdminBulkRequestDTO;

public interface ScheduleService {
    Result dispatch(String policy);

    Result selectMode(String mode);

    Result bulkCreateRequests(AdminBulkRequestDTO dto);

    Result snapshot();

    Result waitingQueue();

    Result pileQueue();
}
