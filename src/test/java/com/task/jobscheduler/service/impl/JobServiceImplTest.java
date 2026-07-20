package com.task.jobscheduler.service.impl;

import com.task.jobscheduler.dto.request.JobRequest;
import com.task.jobscheduler.dto.response.JobResponse;
import com.task.jobscheduler.entity.Job;
import com.task.jobscheduler.enums.JobStatus;
import com.task.jobscheduler.exception.JobNotFoundException;
import com.task.jobscheduler.mapper.JobMapper;
import com.task.jobscheduler.repository.JobRepository;
import com.task.jobscheduler.scheduler.JobChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceImplTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobMapper jobMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private JobServiceImpl jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobServiceImpl(jobRepository, jobMapper, eventPublisher);
    }

    private JobRequest validRequest() {
        JobRequest request = new JobRequest();
        request.setName("Nightly Report");
        request.setCronExpression("0 0 0 * * *");
        return request;
    }

    @Test
    void createJob_persistsWithDefaultActiveStatus_whenStatusOmitted() {
        JobRequest request = validRequest();

        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId(1L);
            job.setCreatedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            return job;
        });
        when(jobMapper.toResponse(any(Job.class))).thenReturn(new JobResponse(
                1L, "Nightly Report", "0 0 0 * * *", JobStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now()));

        JobResponse response = jobService.createJob(request);

        assertThat(response.getStatus()).isEqualTo(JobStatus.ACTIVE);

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(JobStatus.ACTIVE);
        assertThat(jobCaptor.getValue().getName()).isEqualTo("Nightly Report");

        ArgumentCaptor<JobChangedEvent> eventCaptor = ArgumentCaptor.forClass(JobChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getJobId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().getChangeType()).isEqualTo(JobChangedEvent.ChangeType.UPSERTED);
    }

    @Test
    void createJob_honorsExplicitStatus_whenProvided() {
        JobRequest request = validRequest();
        request.setStatus(JobStatus.INACTIVE);

        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobMapper.toResponse(any(Job.class))).thenReturn(new JobResponse(
                1L, "Nightly Report", "0 0 0 * * *", JobStatus.INACTIVE, null, null));

        jobService.createJob(request);

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(JobStatus.INACTIVE);
    }

    @Test
    void createJob_rejectsInvalidCronExpression_andNeverPersists() {
        JobRequest request = validRequest();
        request.setCronExpression("not a cron");

        assertThatThrownBy(() -> jobService.createJob(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cron expression");

        verifyNoInteractions(jobRepository, eventPublisher);
    }

    @Test
    void updateJob_throwsJobNotFound_whenJobDoesNotExist() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.updateJob(99L, validRequest()))
                .isInstanceOf(JobNotFoundException.class);

        verify(jobRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateJob_rejectsInvalidCronExpression_beforeMutatingEntity() {
        Job existing = new Job();
        existing.setId(5L);
        existing.setName("Old Name");
        existing.setCronExpression("0 0 0 * * *");
        existing.setStatus(JobStatus.ACTIVE);
        when(jobRepository.findById(5L)).thenReturn(Optional.of(existing));

        JobRequest request = validRequest();
        request.setCronExpression("garbage");

        assertThatThrownBy(() -> jobService.updateJob(5L, request))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(existing.getName()).isEqualTo("Old Name");
        verify(jobRepository, never()).save(any());
    }

    @Test
    void updateJob_keepsExistingStatus_whenRequestStatusIsNull() {
        Job existing = new Job();
        existing.setId(5L);
        existing.setName("Old Name");
        existing.setCronExpression("0 0 0 * * *");
        existing.setStatus(JobStatus.INACTIVE);
        when(jobRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobMapper.toResponse(any(Job.class))).thenReturn(new JobResponse(
                5L, "New Name", "0 0 0 * * *", JobStatus.INACTIVE, null, null));

        JobRequest request = validRequest();
        request.setName("New Name");
        request.setStatus(null);

        jobService.updateJob(5L, request);

        assertThat(existing.getStatus()).isEqualTo(JobStatus.INACTIVE);
        assertThat(existing.getName()).isEqualTo("New Name");
    }

    @Test
    void updateJob_appliesExplicitStatusChange_andPublishesEvent() {
        Job existing = new Job();
        existing.setId(5L);
        existing.setName("Old Name");
        existing.setCronExpression("0 0 0 * * *");
        existing.setStatus(JobStatus.ACTIVE);
        when(jobRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobMapper.toResponse(any(Job.class))).thenReturn(new JobResponse(
                5L, "Old Name", "0 0 0 * * *", JobStatus.INACTIVE, null, null));

        JobRequest request = validRequest();
        request.setStatus(JobStatus.INACTIVE);

        jobService.updateJob(5L, request);

        assertThat(existing.getStatus()).isEqualTo(JobStatus.INACTIVE);
        ArgumentCaptor<JobChangedEvent> eventCaptor = ArgumentCaptor.forClass(JobChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getChangeType()).isEqualTo(JobChangedEvent.ChangeType.UPSERTED);
    }

    @Test
    void getJobById_returnsMappedResponse_whenFound() {
        Job job = new Job();
        job.setId(7L);
        when(jobRepository.findById(7L)).thenReturn(Optional.of(job));
        JobResponse expected = new JobResponse(7L, "Job", "0 0 0 * * *", JobStatus.ACTIVE, null, null);
        when(jobMapper.toResponse(job)).thenReturn(expected);

        JobResponse actual = jobService.getJobById(7L);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void getJobById_throwsJobNotFound_whenMissing() {
        when(jobRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobService.getJobById(404L))
                .isInstanceOf(JobNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getAllJobs_mapsEveryPersistedJob() {
        Job job1 = new Job();
        job1.setId(1L);
        Job job2 = new Job();
        job2.setId(2L);
        when(jobRepository.findAll()).thenReturn(List.of(job1, job2));
        when(jobMapper.toResponse(job1)).thenReturn(new JobResponse(1L, "A", "0 0 0 * * *", JobStatus.ACTIVE, null, null));
        when(jobMapper.toResponse(job2)).thenReturn(new JobResponse(2L, "B", "0 0 0 * * *", JobStatus.ACTIVE, null, null));

        List<JobResponse> result = jobService.getAllJobs();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(JobResponse::getId).containsExactly(1L, 2L);
    }

    @Test
    void getAllJobs_returnsEmptyList_whenNoJobsExist() {
        when(jobRepository.findAll()).thenReturn(List.of());

        List<JobResponse> result = jobService.getAllJobs();

        assertThat(result).isEmpty();
    }

    @Test
    void deleteJob_removesJobAndPublishesDeletedEvent() {
        when(jobRepository.existsById(3L)).thenReturn(true);

        jobService.deleteJob(3L);

        verify(jobRepository, times(1)).deleteById(3L);
        ArgumentCaptor<JobChangedEvent> eventCaptor = ArgumentCaptor.forClass(JobChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getJobId()).isEqualTo(3L);
        assertThat(eventCaptor.getValue().getChangeType()).isEqualTo(JobChangedEvent.ChangeType.DELETED);
    }

    @Test
    void deleteJob_throwsJobNotFound_andNeverDeletesOrPublishes_whenMissing() {
        when(jobRepository.existsById(3L)).thenReturn(false);

        assertThatThrownBy(() -> jobService.deleteJob(3L))
                .isInstanceOf(JobNotFoundException.class);

        verify(jobRepository, never()).deleteById(any());
        verifyNoInteractions(eventPublisher);
    }
}
