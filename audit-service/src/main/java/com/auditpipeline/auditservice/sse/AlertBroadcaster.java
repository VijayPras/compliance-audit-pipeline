package com.auditpipeline.auditservice.sse;

import com.auditpipeline.auditservice.dto.ComplianceAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds every currently-connected dashboard's SSE emitter and fans an alert
 * out to all of them. Deliberately in-memory: fine for a single instance of
 * this service, which matches the project's scope.
 */
@Component
@Slf4j
public class AlertBroadcaster {

    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    public void broadcast(ComplianceAlert alert) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("compliance-alert")
                        .data(alert));
            } catch (IOException e) {
                log.warn("Dropping dead SSE emitter: {}", e.getMessage());
                emitters.remove(emitter);
            }
        }
    }
}
