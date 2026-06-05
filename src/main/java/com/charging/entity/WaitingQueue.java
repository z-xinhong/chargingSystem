package com.charging.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("waiting_queue")
public class WaitingQueue {
    private Long id;
    private Long requestId;
    private String queueNumber;
    private String mode;
    private Integer positionNo;
}
