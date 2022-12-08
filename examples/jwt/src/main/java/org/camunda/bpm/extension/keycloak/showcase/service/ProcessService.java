package org.camunda.bpm.extension.keycloak.showcase.service;

import org.camunda.bpm.engine.RuntimeService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ProcessService {

    private final RuntimeService runtimeService;

    public ProcessService(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startProcess(){
        runtimeService.startProcessInstanceByKey("camunda.showcase");
    }
}
