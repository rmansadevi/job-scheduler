package com.task.jobscheduler.dto.request;

import com.task.jobscheduler.enums.JobStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobRequest {

    @NotBlank(message = "Job name is required")
    private String name;

    @NotBlank(message = "Cron expression is required")
    private String cronExpression;

    private JobStatus status;
}
