package com.task.jobscheduler.controller;

import com.task.jobscheduler.dto.response.JobExecutionResponse;
import com.task.jobscheduler.enums.ExecutionStatus;
import com.task.jobscheduler.exception.JobExecutionNotFoundException;
import com.task.jobscheduler.exception.JobNotFoundException;
import com.task.jobscheduler.service.JobExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobExecutionController.class)
class JobExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobExecutionService jobExecutionService;

    private JobExecutionResponse response(Long id, ExecutionStatus status) {
        return new JobExecutionResponse(id, 1L, "Nightly Report", LocalDateTime.now(),
                status == ExecutionStatus.RUNNING ? null : LocalDateTime.now(), status,
                status == ExecutionStatus.FAILED ? "boom" : null);
    }

    @Test
    void getExecutionsForJob_returns200_withHistory() throws Exception {
        when(jobExecutionService.getExecutionsForJob(1L))
                .thenReturn(List.of(response(2L, ExecutionStatus.SUCCESS), response(1L, ExecutionStatus.FAILED)));

        mockMvc.perform(get("/api/jobs/{jobId}/executions", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[1].status").value("FAILED"));
    }

    @Test
    void getExecutionsForJob_returns404_whenJobDoesNotExist() throws Exception {
        when(jobExecutionService.getExecutionsForJob(404L)).thenThrow(new JobNotFoundException(404L));

        mockMvc.perform(get("/api/jobs/{jobId}/executions", 404L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getExecutionsForJob_returns200_withEmptyList_whenJobHasNoHistory() throws Exception {
        when(jobExecutionService.getExecutionsForJob(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/jobs/{jobId}/executions", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getExecutionById_returns200_whenFound() throws Exception {
        when(jobExecutionService.getExecutionById(5L)).thenReturn(response(5L, ExecutionStatus.SUCCESS));

        mockMvc.perform(get("/api/executions/{id}", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void getExecutionById_returns404_whenMissing() throws Exception {
        when(jobExecutionService.getExecutionById(999L)).thenThrow(new JobExecutionNotFoundException(999L));

        mockMvc.perform(get("/api/executions/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Job execution not found with id: 999"));
    }

    @Test
    void getAllExecutions_returns200_withFullHistory() throws Exception {
        when(jobExecutionService.getAllExecutions())
                .thenReturn(List.of(response(1L, ExecutionStatus.SUCCESS), response(2L, ExecutionStatus.RUNNING)));

        mockMvc.perform(get("/api/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
