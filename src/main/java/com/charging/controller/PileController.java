package com.charging.controller;

import com.charging.common.Result;
import com.charging.service.PileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pile")
public class PileController {

    @Autowired
    private PileService pileService;

    @GetMapping("/status")
    public Result status() {
        return pileService.status();
    }

    @GetMapping("/queue/{pileId}")
    public Result queue(@PathVariable Long pileId) {
        return pileService.queue(pileId);
    }

    @PostMapping("/start/{pileId}")
    public Result start(@PathVariable Long pileId) {
        return pileService.start(pileId);
    }

    @PostMapping("/stop/{pileId}")
    public Result stop(@PathVariable Long pileId) {
        return pileService.stop(pileId);
    }
}
