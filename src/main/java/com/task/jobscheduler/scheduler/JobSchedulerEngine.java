package com.task.jobscheduler.scheduler;

import com.task.jobscheduler.entity.Job;
import com.task.jobscheduler.entity.JobExecution;
import com.task.jobscheduler.enums.ExecutionStatus;
import com.task.jobscheduler.enums.JobStatus;
import com.task.jobscheduler.repository.JobRepository;
import com.task.jobscheduler.service.JobExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;

/**
 * Dynamically schedules ACTIVE jobs using Spring's TaskScheduler, keeping the
 * schedule in sync with the database as jobs are created, updated, deleted,
 * activated or deactivated (see {@link JobChangedEvent}).
 */
@Slf4j
@Component
public class JobSchedulerEngine {

    private final TaskScheduler taskScheduler;
    private final Executor jobTaskExecutor;
    private final JobRepository jobRepository;
    private final JobExecutionService jobExecutionService;
    private final Map<Long, ScheduledJobHandle> scheduledJobs = new ConcurrentHashMap<>();

    public JobSchedulerEngine(TaskScheduler taskScheduler,
                               @Qualifier("jobTaskExecutor") Executor jobTaskExecutor,
                               JobRepository jobRepository,
                               JobExecutionService jobExecutionService) {
        this.taskScheduler = taskScheduler;
        this.jobTaskExecutor = jobTaskExecutor;
        this.jobRepository = jobRepository;
        this.jobExecutionService = jobExecutionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadActiveJobsOnStartup() {
        List<Job> activeJobs = jobRepository.findAll().stream()
                .filter(job -> job.getStatus() == JobStatus.ACTIVE)
                .toList();

        log.info("Loading {} active job(s) for scheduling on startup", activeJobs.size());
        activeJobs.forEach(this::scheduleJob);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobChanged(JobChangedEvent event) {
        if (event.getChangeType() == JobChangedEvent.ChangeType.DELETED) {
            cancelJob(event.getJobId());
            return;
        }

        jobRepository.findById(event.getJobId()).ifPresentOrElse(job -> {
            if (job.getStatus() == JobStatus.ACTIVE) {
                scheduleJob(job);
            } else {
                cancelJob(job.getId());
            }
        }, () -> cancelJob(event.getJobId()));
    }

    private void scheduleJob(Job job) {
        ScheduledJobHandle existing = scheduledJobs.get(job.getId());
        if (existing != null && existing.cronExpression().equals(job.getCronExpression())) {
            return;
        }

        cancelJob(job.getId());

        CronTrigger trigger;
        try {
            trigger = new CronTrigger(job.getCronExpression());
        } catch (IllegalArgumentException ex) {
            log.error("Skipping job '{}' (id={}): invalid cron expression '{}'",
                    job.getName(), job.getId(), job.getCronExpression());
            return;
        }

        ScheduledFuture<?> future = taskScheduler.schedule(() -> dispatch(job), trigger);
        scheduledJobs.put(job.getId(), new ScheduledJobHandle(job.getCronExpression(), future));
        log.info("Scheduled job '{}' (id={}) with cron '{}'", job.getName(), job.getId(), job.getCronExpression());
    }

    private void cancelJob(Long jobId) {
        ScheduledJobHandle handle = scheduledJobs.remove(jobId);
        if (handle != null) {
            handle.future().cancel(false);
            log.info("Unscheduled job (id={})", jobId);
        }
    }

    private void dispatch(Job job) {
        // Hand off to a dedicated executor so the scheduler's trigger thread is freed immediately.
        jobTaskExecutor.execute(() -> execute(job));
    }

    private void execute(Job job) {
        JobExecution execution;
        try {
            execution = jobExecutionService.startExecution(job.getId());
        } catch (Exception ex) {
            log.error("Failed to record execution start for job '{}' (id={}): {}",
                    job.getName(), job.getId(), ex.getMessage(), ex);
            return;
        }

        log.info("Job '{}' (id={}) execution started (executionId={})", job.getName(), job.getId(), execution.getId());
        try {
            // Placeholder for the actual job payload; real task logic will be plugged in later.
            recordCompletion(job, execution.getId(), ExecutionStatus.SUCCESS, null);
        } catch (Exception ex) {
            recordCompletion(job, execution.getId(), ExecutionStatus.FAILED, ex.getMessage());
        }
    }

    private void recordCompletion(Job job, Long executionId, ExecutionStatus status, String errorMessage) {
        try {
            jobExecutionService.completeExecution(executionId, status, errorMessage);
            log.info("Job '{}' (id={}) execution {} (executionId={})", job.getName(), job.getId(), status, executionId);
        } catch (Exception ex) {
            log.error("Failed to record execution completion for job '{}' (id={}, executionId={}): {}",
                    job.getName(), job.getId(), executionId, ex.getMessage(), ex);
        }
    }

    private record ScheduledJobHandle(String cronExpression, ScheduledFuture<?> future) {
    }
}
