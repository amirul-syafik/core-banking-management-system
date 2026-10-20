package com.cbms.controller;

import com.cbms.dto.BankWorkflowAssignTaskRequest;
import com.cbms.dto.BankWorkflowTaskResponse;
import com.cbms.service.BankWorkflowTaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workflows/tasks")
public class BankWorkflowTaskController {
    private final BankWorkflowTaskService service;

    public BankWorkflowTaskController(BankWorkflowTaskService service) {
        this.service = service;
    }

    @GetMapping("/queue")
    public List<BankWorkflowTaskResponse> queue(Authentication auth) {
        return service.queue(auth);
    }

    @GetMapping("/mine")
    public List<BankWorkflowTaskResponse> mine(Authentication auth) {
        return service.mine(auth);
    }

    @GetMapping("/instance/{instanceId}")
    public List<BankWorkflowTaskResponse> byInstance(@PathVariable Long instanceId) {
        return service.byInstance(instanceId);
    }

    @PostMapping("/initialize/{instanceId}")
    @ResponseStatus(HttpStatus.CREATED)
    public BankWorkflowTaskResponse initialize(@PathVariable Long instanceId, Authentication auth) {
        return service.initialize(instanceId, auth);
    }

    @PostMapping("/{taskId}/claim")
    public BankWorkflowTaskResponse claim(@PathVariable Long taskId, Authentication auth) {
        return service.claim(taskId, auth);
    }

    @PostMapping("/{taskId}/assign")
    public BankWorkflowTaskResponse assign(@PathVariable Long taskId,
                                           @Valid @RequestBody BankWorkflowAssignTaskRequest request,
                                           Authentication auth) {
        return service.assign(taskId, request, auth);
    }
}
