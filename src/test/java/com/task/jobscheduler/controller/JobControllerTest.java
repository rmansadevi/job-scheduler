package com.task.jobscheduler.controller;

import com.task.jobscheduler.dto.request.JobRequest;
import com.task.jobscheduler.dto.response.JobResponse;
import com.task.jobscheduler.enums.JobStatus;
import com.task.jobscheduler.exception.JobNotFoundException;
import com.task.jobscheduler.service.JobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobService jobService;

    private JobResponse sampleResponse() {
        return new JobResponse(1L, "Nightly Report", "0 0 0 * * *", JobStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void createJob_returns201_withBody_onValidRequest() throws Exception {
        JobRequest request = new JobRequest();
        request.setName("Nightly Report");
        request.setCronExpression("0 0 0 * * *");

        when(jobService.createJob(any(JobRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Nightly Report"));
    }

    @Test
    void createJob_returns400_whenNameBlank() throws Exception {
        JobRequest request = new JobRequest();
        request.setName("");
        request.setCronExpression("0 0 0 * * *");

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        verify(jobService, never()).createJob(any());
    }

    @Test
    void createJob_returns400_whenCronExpressionBlank() throws Exception {
        JobRequest request = new JobRequest();
        request.setName("Nightly Report");
        request.setCronExpression("");

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cronExpression").exists());
    }

    @Test
    void createJob_returns400_whenServiceRejectsInvalidCron() throws Exception {
        JobRequest request = new JobRequest();
        request.setName("Nightly Report");
        request.setCronExpression("garbage");

        when(jobService.createJob(any(JobRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid cron expression: garbage"));

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid cron expression: garbage"));
    }

    @Test
    void updateJob_returns200_onValidRequest() throws Exception {
        JobRequest request = new JobRequest();
        request.setName("Renamed");
        request.setCronExpression("0 0 0 * * *");

        when(jobService.updateJob(eq(1L), any(JobRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(put("/api/jobs/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateJob_returns404_whenJobDoesNotExist() throws Exception {
        JobRequest request = new JobRequest();
        request.setName("Renamed");
        request.setCronExpression("0 0 0 * * *");

        when(jobService.updateJob(eq(404L), any(JobRequest.class)))
                .thenThrow(new JobNotFoundException(404L));

        mockMvc.perform(put("/api/jobs/{id}", 404L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Job not found with id: 404"));
    }

    @Test
    void getJobById_returns200_whenFound() throws Exception {
        when(jobService.getJobById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/jobs/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nightly Report"));
    }

    @Test
    void getJobById_returns404_whenMissing() throws Exception {
        when(jobService.getJobById(404L)).thenThrow(new JobNotFoundException(404L));

        mockMvc.perform(get("/api/jobs/{id}", 404L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllJobs_returns200_withList() throws Exception {
        when(jobService.getAllJobs()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getAllJobs_returns200_withEmptyList_whenNoJobsExist() throws Exception {
        when(jobService.getAllJobs()).thenReturn(List.of());

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteJob_returns204_onSuccess() throws Exception {
        mockMvc.perform(delete("/api/jobs/{id}", 1L))
                .andExpect(status().isNoContent());

        verify(jobService).deleteJob(1L);
    }

    @Test
    void deleteJob_returns404_whenJobDoesNotExist() throws Exception {
        org.mockito.Mockito.doThrow(new JobNotFoundException(404L)).when(jobService).deleteJob(404L);

        mockMvc.perform(delete("/api/jobs/{id}", 404L))
                .andExpect(status().isNotFound());
    }
}
