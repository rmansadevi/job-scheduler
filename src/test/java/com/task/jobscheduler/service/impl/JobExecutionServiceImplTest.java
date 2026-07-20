package com.task.jobscheduler.service.impl;

import com.task.jobscheduler.dto.response.JobExecutionResponse;
import com.task.jobscheduler.entity.Job;
import com.task.jobscheduler.entity.JobExecution;
import com.task.jobscheduler.enums.ExecutionStatus;
import com.task.jobscheduler.exception.JobExecutionNotFoundException;
import com.task.jobscheduler.exception.JobNotFoundException;
import com.task.jobscheduler.mapper.JobExecutionMapper;
import com.task.jobscheduler.repository.JobExecutionRepository;
import com.task.jobscheduler.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobExecutionServiceImplTest {

    @Mock
    private JobExecutionRepository jobExecutionRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecutionMapper jobExecutionMapper;

    private JobExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JobExecutionServiceImpl(jobExecutionRepository, jobRepository, jobExecutionMapper);
    }

    @Test
    void startExecution_createsRunningRecord_linkedToJob() {
        Job job = new Job();
        job.setId(1L);
        job.setName("Nightly Report");
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobExecutionRepository.save(any(JobExecution.class))).thenAnswer(invocation -> {
            JobExecution execution = invocation.getArgument(0);
            execution.setId(100L);
            return execution;
        });

        JobExecution result = service.startExecution(1L);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getJob()).isSameAs(job);
        assertThat(result.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
        assertThat(result.getStartTime()).isNotNull();
        assertThat(result.getEndTime()).isNull();

        ArgumentCaptor<JobExecution> captor = ArgumentCaptor.forClass(JobExecution.class);
        verify(jobExecutionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ExecutionStatus.RUNNING);
    }

    @Test
    void startExecution_throwsJobNotFound_whenJobDeletedBeforeFiring() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startExecution(99L))
                .isInstanceOf(JobNotFoundException.class);

        verify(jobExecutionRepository, never()).save(any());
    }

    @Test
    void completeExecution_setsSuccessStatus_andClearsErrorMessage() {
        JobExecution execution = new JobExecution();
        execution.setId(100L);
        execution.setStatus(ExecutionStatus.RUNNING);
        when(jobExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));

        service.completeExecution(100L, ExecutionStatus.SUCCESS, null);

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(execution.getEndTime()).isNotNull();
        assertThat(execution.getErrorMessage()).isNull();
        verify(jobExecutionRepository).save(execution);
    }

    @Test
    void completeExecution_setsFailedStatus_withErrorMessage() {
        JobExecution execution = new JobExecution();
        execution.setId(100L);
        execution.setStatus(ExecutionStatus.RUNNING);
        when(jobExecutionRepository.findById(100L)).thenReturn(Optional.of(execution));

        service.completeExecution(100L, ExecutionStatus.FAILED, "boom");

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.getErrorMessage()).isEqualTo("boom");
        assertThat(execution.getEndTime()).isNotNull();
    }

    @Test
    void completeExecution_throwsJobExecutionNotFound_whenExecutionMissing() {
        when(jobExecutionRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeExecution(404L, ExecutionStatus.SUCCESS, null))
                .isInstanceOf(JobExecutionNotFoundException.class);

        verify(jobExecutionRepository, never()).save(any());
    }

    @Test
    void getExecutionById_returnsMappedResponse_whenFound() {
        JobExecution execution = new JobExecution();
        execution.setId(1L);
        when(jobExecutionRepository.findById(1L)).thenReturn(Optional.of(execution));
        JobExecutionResponse expected = new JobExecutionResponse(1L, 5L, "Job", LocalDateTime.now(), null, ExecutionStatus.RUNNING, null);
        when(jobExecutionMapper.toResponse(execution)).thenReturn(expected);

        JobExecutionResponse actual = service.getExecutionById(1L);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void getExecutionById_throwsNotFound_whenMissing() {
        when(jobExecutionRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getExecutionById(1L))
                .isInstanceOf(JobExecutionNotFoundException.class);
    }

    @Test
    void getExecutionsForJob_throwsJobNotFound_whenJobDoesNotExist() {
        when(jobRepository.existsById(5L)).thenReturn(false);

        assertThatThrownBy(() -> service.getExecutionsForJob(5L))
                .isInstanceOf(JobNotFoundException.class);

        verify(jobExecutionRepository, never()).findByJobIdOrderByStartTimeDesc(any());
    }

    @Test
    void getExecutionsForJob_returnsHistoryOrderedMostRecentFirst_whenJobExists() {
        when(jobRepository.existsById(5L)).thenReturn(true);
        JobExecution exec1 = new JobExecution();
        exec1.setId(1L);
        JobExecution exec2 = new JobExecution();
        exec2.setId(2L);
        when(jobExecutionRepository.findByJobIdOrderByStartTimeDesc(5L)).thenReturn(List.of(exec2, exec1));
        when(jobExecutionMapper.toResponse(exec2)).thenReturn(new JobExecutionResponse(2L, 5L, "Job", null, null, ExecutionStatus.SUCCESS, null));
        when(jobExecutionMapper.toResponse(exec1)).thenReturn(new JobExecutionResponse(1L, 5L, "Job", null, null, ExecutionStatus.FAILED, "err"));

        List<JobExecutionResponse> result = service.getExecutionsForJob(5L);

        assertThat(result).extracting(JobExecutionResponse::getId).containsExactly(2L, 1L);
    }

    @Test
    void getExecutionsForJob_returnsEmptyList_whenJobHasNoExecutions() {
        when(jobRepository.existsById(5L)).thenReturn(true);
        when(jobExecutionRepository.findByJobIdOrderByStartTimeDesc(5L)).thenReturn(List.of());

        List<JobExecutionResponse> result = service.getExecutionsForJob(5L);

        assertThat(result).isEmpty();
    }

    @Test
    void getAllExecutions_mapsEveryPersistedExecution() {
        JobExecution exec1 = new JobExecution();
        exec1.setId(1L);
        JobExecution exec2 = new JobExecution();
        exec2.setId(2L);
        when(jobExecutionRepository.findAll()).thenReturn(List.of(exec1, exec2));
        when(jobExecutionMapper.toResponse(exec1)).thenReturn(new JobExecutionResponse(1L, 5L, "Job", null, null, ExecutionStatus.SUCCESS, null));
        when(jobExecutionMapper.toResponse(exec2)).thenReturn(new JobExecutionResponse(2L, 6L, "Job2", null, null, ExecutionStatus.FAILED, "err"));

        List<JobExecutionResponse> result = service.getAllExecutions();

        assertThat(result).hasSize(2);
    }
}
