package com.task.jobscheduler.service.impl;

import com.task.jobscheduler.dto.request.JobRequest;
import com.task.jobscheduler.dto.response.JobResponse;
import com.task.jobscheduler.entity.Job;
import com.task.jobscheduler.enums.JobStatus;
import com.task.jobscheduler.exception.JobNotFoundException;
import com.task.jobscheduler.mapper.JobMapper;
import com.task.jobscheduler.repository.JobRepository;
import com.task.jobscheduler.service.JobService;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final JobMapper jobMapper;

    public JobServiceImpl(JobRepository jobRepository, JobMapper jobMapper) {
        this.jobRepository = jobRepository;
        this.jobMapper = jobMapper;
    }

    @Override
    public JobResponse createJob(JobRequest request) {
        validateCronExpression(request.getCronExpression());

        Job job = new Job();
        job.setName(request.getName());
        job.setCronExpression(request.getCronExpression());
        job.setStatus(request.getStatus() != null ? request.getStatus() : JobStatus.ACTIVE);

        Job saved = jobRepository.save(job);
        return jobMapper.toResponse(saved);
    }

    @Override
    public JobResponse updateJob(Long id, JobRequest request) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));

        validateCronExpression(request.getCronExpression());

        job.setName(request.getName());
        job.setCronExpression(request.getCronExpression());
        if (request.getStatus() != null) {
            job.setStatus(request.getStatus());
        }

        Job saved = jobRepository.save(job);
        return jobMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public JobResponse getJobById(Long id) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new JobNotFoundException(id));
        return jobMapper.toResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobResponse> getAllJobs() {
        return jobRepository.findAll()
                .stream()
                .map(jobMapper::toResponse)
                .toList();
    }

    @Override
    public void deleteJob(Long id) {
        if (!jobRepository.existsById(id)) {
            throw new JobNotFoundException(id);
        }
        jobRepository.deleteById(id);
    }

    private void validateCronExpression(String cronExpression) {
        if (!CronExpression.isValidExpression(cronExpression)) {
            throw new IllegalArgumentException("Invalid cron expression: " + cronExpression);
        }
    }
}
