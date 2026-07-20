package com.task.jobscheduler.service;

import com.task.jobscheduler.dto.response.JobExecutionResponse;
import com.task.jobscheduler.entity.JobExecution;
import com.task.jobscheduler.enums.ExecutionStatus;

import java.util.List;

public interface JobExecutionService {

    JobExecution startExecution(Long jobId);

    void completeExecution(Long executionId, ExecutionStatus status, String errorMessage);

    JobExecutionResponse getExecutionById(Long id);

    List<JobExecutionResponse> getExecutionsForJob(Long jobId);

    List<JobExecutionResponse> getAllExecutions();
}
