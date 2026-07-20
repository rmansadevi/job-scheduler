package com.task.jobscheduler.dto.response;

import com.task.jobscheduler.enums.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class JobExecutionResponse {

    private Long id;
    private Long jobId;
    private String jobName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ExecutionStatus status;
    private String errorMessage;
}
