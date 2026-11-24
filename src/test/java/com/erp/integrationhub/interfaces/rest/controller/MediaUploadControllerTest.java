package com.erp.integrationhub.interfaces.rest.controller;

import com.erp.integrationhub.infrastructure.adapter.external.R2MediaStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaUploadControllerTest {

    @Test
    @DisplayName("POST /api/v1/media/upload uploads valid image to R2 and returns 201")
    void uploadValidImage_Returns201() throws Exception {
        R2MediaStorageService storage = mock(R2MediaStorageService.class);
        when(storage.upload(any(), eq("branches")))
                .thenReturn(new R2MediaStorageService.StoredMediaResult(
                        "media-123",
                        "https://cdn.example.com/driveos/branches/media-123-banner.jpg",
                        "branches/media-123-banner.jpg"));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MediaUploadController(storage)).build();
        MockMultipartFile file = new MockMultipartFile(
                "file", "banner.jpg", "image/jpeg", "fake-jpeg-bytes".getBytes());

        mockMvc.perform(multipart("/api/v1/media/upload")
                        .file(file)
                        .param("folder", "branches"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaId").value("media-123"))
                .andExpect(jsonPath("$.url").value("https://cdn.example.com/driveos/branches/media-123-banner.jpg"))
                .andExpect(jsonPath("$.immutable").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/media/upload rejects unsupported executable file with 400")
    void uploadExecutable_Returns400() throws Exception {
        R2MediaStorageService storage = mock(R2MediaStorageService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new MediaUploadController(storage)).build();

        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", "mz".getBytes());

        mockMvc.perform(multipart("/api/v1/media/upload").file(file))
                .andExpect(status().isBadRequest());

        verify(storage, never()).upload(any(), any());
    }
}
