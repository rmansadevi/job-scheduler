package com.task.jobscheduler.scheduler;

import com.task.jobscheduler.entity.Job;
import com.task.jobscheduler.entity.JobExecution;
import com.task.jobscheduler.enums.ExecutionStatus;
import com.task.jobscheduler.enums.JobStatus;
import com.task.jobscheduler.repository.JobRepository;
import com.task.jobscheduler.service.JobExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobSchedulerEngineTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private Executor jobTaskExecutor;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecutionService jobExecutionService;

    private JobSchedulerEngine engine;

    @BeforeEach
    void setUp() {
        engine = new JobSchedulerEngine(taskScheduler, jobTaskExecutor, jobRepository, jobExecutionService);
        // Run whatever is submitted to the "job execution" pool inline, synchronously, so
        // dispatch -> execute can be exercised deterministically in tests. Only exercised by
        // tests that actually fire the captured trigger runnable, so stub it leniently.
        lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(jobTaskExecutor).execute(any(Runnable.class));
    }

    private Job activeJob(Long id, String name, String cron) {
        Job job = new Job();
        job.setId(id);
        job.setName(name);
        job.setCronExpression(cron);
        job.setStatus(JobStatus.ACTIVE);
        return job;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ScheduledFuture mockFuture() {
        return mock(ScheduledFuture.class);
    }

    @Test
    void loadActiveJobsOnStartup_schedulesOnlyActiveJobs() {
        Job active = activeJob(1L, "Active Job", "0 0 0 * * *");
        Job inactive = activeJob(2L, "Inactive Job", "0 0 12 * * *");
        inactive.setStatus(JobStatus.INACTIVE);

        when(jobRepository.findAll()).thenReturn(List.of(active, inactive));
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mockFuture());

        engine.loadActiveJobsOnStartup();

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler, times(1)).schedule(any(Runnable.class), triggerCaptor.capture());
        assertThat(((CronTrigger) triggerCaptor.getValue()).getExpression()).isEqualTo("0 0 0 * * *");
    }

    @Test
    void loadActiveJobsOnStartup_skipsJobWithInvalidCron_withoutThrowing() {
        Job invalidCronJob = activeJob(1L, "Broken Job", "not a cron expression");
        when(jobRepository.findAll()).thenReturn(List.of(invalidCronJob));

        assertThatCode(() -> engine.loadActiveJobsOnStartup()).doesNotThrowAnyException();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void onJobChanged_deleted_cancelsScheduledJob() {
        Job job = activeJob(1L, "Job", "0 0 0 * * *");
        ScheduledFuture future = mockFuture();
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(future);

        engine.onJobChanged(new JobChangedEvent(1L, JobChangedEvent.ChangeType.UPSERTED));
        engine.onJobChanged(new JobChangedEvent(1L, JobChangedEvent.ChangeType.DELETED));

        verify(future, times(1)).cancel(false);
    }

    @Test
    void scheduleJob_isIdempotent_whenCronExpressionUnchanged() {
        Job job = activeJob(1L, "Job", "0 0 0 * * *");
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(taskScheduler.schedule(any(Runnable.class), any(Trigger.class))).thenReturn(mockFuture());

        engine.onJobChanged(new JobChangedEvent(1L, JobChangedEvent.ChangeType.UPSERTED));
        engine.onJobChanged(new JobChangedEvent(1L, JobChangedEvent.ChangeType.UPSERTED));

        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    void firedTrigger_recordsSuccessfulExecution_endToEnd() {
        Job job = activeJob(1L, "Job", "0 0 0 * * *");
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(runnableCaptor.capture(), any(Trigger.class))).thenReturn(mockFuture());

        JobExecution execution = new JobExecution();
        execution.setId(500L);
        when(jobExecutionService.startExecution(1L)).thenReturn(execution);

        engine.onJobChanged(new JobChangedEvent(1L, JobChangedEvent.ChangeType.UPSERTED));

        // Simulate the cron trigger firing.
        runnableCaptor.getValue().run();

        verify(jobExecutionService).startExecution(1L);
        verify(jobExecutionService).completeExecution(500L, ExecutionStatus.SUCCESS, null);
    }

    @Test
    void firedTrigger_doesNotPropagate_whenStartExecutionFails() {
        Job job = activeJob(1L, "Job", "0 0 0 * * *");
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        when(taskScheduler.schedule(runnableCaptor.capture(), any(Trigger.class))).thenReturn(mockFuture());

        when(jobExecutionService.startExecution(1L)).thenThrow(new RuntimeException("db down"));

        engine.onJobChanged(new JobChangedEvent(1L, JobChangedEvent.ChangeType.UPSERTED));

        assertThatCode(() -> runnableCaptor.getValue().run()).doesNotThrowAnyException();

        verify(jobExecutionService, never()).completeExecution(any(), any(), any());
    }
}
