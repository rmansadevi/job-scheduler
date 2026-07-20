package com.task.jobscheduler.controller;

import com.task.jobscheduler.dto.response.JobExecutionResponse;
import com.task.jobscheduler.service.JobExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class JobExecutionController {

    private final JobExecutionService jobExecutionService;

    public JobExecutionController(JobExecutionService jobExecutionService) {
        this.jobExecutionService = jobExecutionService;
    }

    @GetMapping("/jobs/{jobId}/executions")
    public ResponseEntity<List<JobExecutionResponse>> getExecutionsForJob(@PathVariable Long jobId) {
        return ResponseEntity.ok(jobExecutionService.getExecutionsForJob(jobId));
    }

    @GetMapping("/executions/{id}")
    public ResponseEntity<JobExecutionResponse> getExecutionById(@PathVariable Long id) {
        return ResponseEntity.ok(jobExecutionService.getExecutionById(id));
    }

    @GetMapping("/executions")
    public ResponseEntity<List<JobExecutionResponse>> getAllExecutions() {
        return ResponseEntity.ok(jobExecutionService.getAllExecutions());
    }
}
