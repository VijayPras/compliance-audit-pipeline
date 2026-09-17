package com.auditpipeline.auditservice.controller;

import com.auditpipeline.auditservice.sse.AlertBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
public class ComplianceStreamController {

    private final AlertBroadcaster alertBroadcaster;

    /**
     * The React dashboard subscribes to this with a native EventSource
     * connection (see the build plan, Phase 3) -- no polling, no WebSocket
     * handshake, just a long-lived HTTP response the server pushes into.
     */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream() {
        return alertBroadcaster.subscribe();
    }
}
