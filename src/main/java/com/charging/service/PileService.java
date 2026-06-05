package com.charging.service;

import com.charging.common.Result;

public interface PileService {
    Result status();

    Result queue(Long pileId);

    Result start(Long pileId);

    Result stop(Long pileId);
}
