package com.task.jobscheduler.exception;

public class JobExecutionNotFoundException extends RuntimeException {

    public JobExecutionNotFoundException(Long id) {
        super("Job execution not found with id: " + id);
    }
}
