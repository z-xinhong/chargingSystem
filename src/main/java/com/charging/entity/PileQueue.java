package com.charging.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("pile_queue")
public class PileQueue {
    private Long id;
    private Long pileId;
    private Long requestId;
    private Integer positionNo;
    private String status;
}
