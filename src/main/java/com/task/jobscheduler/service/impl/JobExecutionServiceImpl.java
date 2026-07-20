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
import com.task.jobscheduler.service.JobExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional
public class JobExecutionServiceImpl implements JobExecutionService {

    private final JobExecutionRepository jobExecutionRepository;
    private final JobRepository jobRepository;
    private final JobExecutionMapper jobExecutionMapper;

    public JobExecutionServiceImpl(JobExecutionRepository jobExecutionRepository,
                                    JobRepository jobRepository,
                                    JobExecutionMapper jobExecutionMapper) {
        this.jobExecutionRepository = jobExecutionRepository;
        this.jobRepository = jobRepository;
        this.jobExecutionMapper = jobExecutionMapper;
    }

    @Override
    public JobExecution startExecution(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        JobExecution execution = new JobExecution();
        execution.setJob(job);
        execution.setStartTime(LocalDateTime.now());
        execution.setStatus(ExecutionStatus.RUNNING);

        JobExecution saved = jobExecutionRepository.save(execution);
        log.debug("Recorded execution start (executionId={}) for job id={}", saved.getId(), jobId);
        return saved;
    }

    @Override
    public void completeExecution(Long executionId, ExecutionStatus status, String errorMessage) {
        JobExecution execution = jobExecutionRepository.findById(executionId)
                .orElseThrow(() -> new JobExecutionNotFoundException(executionId));

        execution.setEndTime(LocalDateTime.now());
        execution.setStatus(status);
        execution.setErrorMessage(errorMessage);

        jobExecutionRepository.save(execution);
        log.debug("Recorded execution completion (executionId={}) with status={}", executionId, status);
    }

    @Override
    @Transactional(readOnly = true)
    public JobExecutionResponse getExecutionById(Long id) {
        JobExecution execution = jobExecutionRepository.findById(id)
                .orElseThrow(() -> new JobExecutionNotFoundException(id));
        return jobExecutionMapper.toResponse(execution);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobExecutionResponse> getExecutionsForJob(Long jobId) {
        if (!jobRepository.existsById(jobId)) {
            throw new JobNotFoundException(jobId);
        }
        return jobExecutionRepository.findByJobIdOrderByStartTimeDesc(jobId)
                .stream()
                .map(jobExecutionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobExecutionResponse> getAllExecutions() {
        return jobExecutionRepository.findAll()
                .stream()
                .map(jobExecutionMapper::toResponse)
                .toList();
    }
}
