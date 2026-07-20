package com.task.jobscheduler.service;

import com.task.jobscheduler.dto.request.JobRequest;
import com.task.jobscheduler.dto.response.JobResponse;

import java.util.List;

public interface JobService {

    JobResponse createJob(JobRequest request);

    JobResponse updateJob(Long id, JobRequest request);

    JobResponse getJobById(Long id);

    List<JobResponse> getAllJobs();

    void deleteJob(Long id);
}
