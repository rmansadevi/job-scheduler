package com.task.jobscheduler.mapper;

import com.task.jobscheduler.dto.response.JobResponse;
import com.task.jobscheduler.entity.Job;
import org.springframework.stereotype.Component;

@Component
public class JobMapper {

    public JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getId(),
                job.getName(),
                job.getCronExpression(),
                job.getStatus(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
