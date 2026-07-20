package com.task.jobscheduler.mapper;

import com.task.jobscheduler.dto.response.JobExecutionResponse;
import com.task.jobscheduler.entity.JobExecution;
import org.springframework.stereotype.Component;

@Component
public class JobExecutionMapper {

    public JobExecutionResponse toResponse(JobExecution execution) {
        return new JobExecutionResponse(
                execution.getId(),
                execution.getJob().getId(),
                execution.getJob().getName(),
                execution.getStartTime(),
                execution.getEndTime(),
                execution.getStatus(),
                execution.getErrorMessage()
        );
    }
}
