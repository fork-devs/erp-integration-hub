package com.erp.integrationhub.interfaces.rest.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthAndMediaControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(), new MediaUploadController()).build();
    }

    @Test
    @DisplayName("GET / returns 200 OK with root ping metadata")
    void shouldReturnRootPing() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.app").value("ShiftIQ Platform API"))
                .andExpect(jsonPath("$.docs").value("/swagger-ui.html"));
    }

    @Test
    @DisplayName("GET /health returns 200 OK with UP status")
    void shouldReturnHealthCheck() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("POST /api/v1/media/upload uploads image and returns 201 Created with CDN url")
    void shouldUploadImage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-vehicle.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake image content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/media/upload")
                        .file(file)
                        .param("folder", "vehicles"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/media/upload rejects non-image formats with 400 Bad Request")
    void shouldRejectInvalidFormat() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.exe",
                "application/octet-stream",
                "binary content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/media/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }
}
