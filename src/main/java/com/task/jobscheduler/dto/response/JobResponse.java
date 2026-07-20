package com.task.jobscheduler.dto.response;

import com.task.jobscheduler.enums.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class JobResponse {

    private Long id;
    private String name;
    private String cronExpression;
    private JobStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
