package com.task.jobscheduler.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class JobChangedEvent {

    public enum ChangeType {
        UPSERTED,
        DELETED
    }

    private final Long jobId;
    private final ChangeType changeType;
}
