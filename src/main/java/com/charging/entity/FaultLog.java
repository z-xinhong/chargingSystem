package com.charging.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("fault_log")
public class FaultLog {
    private Long id;
    private Long pileId;
    private LocalDateTime faultTime;
    private LocalDateTime recoverTime;
    private String schedulePolicy;
    private String remark;
}
