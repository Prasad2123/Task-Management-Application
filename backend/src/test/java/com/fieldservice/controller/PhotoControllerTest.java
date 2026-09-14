package com.fieldservice.controller;

import com.fieldservice.dto.UpdatePhotoMetadataRequest;
import com.fieldservice.dto.WorkPhotoResponse;
import com.fieldservice.entity.PhotoCategory;
import com.fieldservice.entity.UserEntity;
import com.fieldservice.entity.UserRole;
import com.fieldservice.entity.WorkPhotoEntity;
import com.fieldservice.service.PhotoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PhotoControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PhotoService photoService;

    @InjectMocks
    private PhotoController photoController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UserEntity serviceBoy;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(photoController).build();

        serviceBoy = UserEntity.builder()
                .id(1L).name("Rahul").email("service@demo.com")
                .role(UserRole.SERVICE_BOY).active(true).build();

        var auth = new UsernamePasswordAuthenticationToken(serviceBoy, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testUploadPhoto_endpoint() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "image-content".getBytes()
        );

        WorkPhotoResponse response = WorkPhotoResponse.builder()
                .id(5L)
                .workId(10L)
                .title("Inspection")
                .category("SITE_INSPECTION")
                .photoUrl("/api/works/10/photos/5")
                .uploadStatus("UPLOADED")
                .build();

        when(photoService.uploadPhoto(eq(10L), any(), eq("Inspection"), eq(PhotoCategory.SITE_INSPECTION), any(), eq(serviceBoy)))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/works/10/photos")
                        .file(file)
                        .param("title", "Inspection")
                        .param("category", "SITE_INSPECTION"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("Inspection"))
                .andExpect(jsonPath("$.uploadStatus").value("UPLOADED"));
    }

    @Test
    void testGetPhotos_endpoint() throws Exception {
        WorkPhotoResponse response = WorkPhotoResponse.builder()
                .id(5L)
                .workId(10L)
                .title("Inspection")
                .category("SITE_INSPECTION")
                .build();

        when(photoService.getPhotosForWork(10L, serviceBoy)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/works/10/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].title").value("Inspection"));
    }

    @Test
    void testGetPhotoStream_endpoint() throws Exception {
        WorkPhotoEntity photoEntity = WorkPhotoEntity.builder()
                .id(5L)
                .fileName("test.jpg")
                .contentType("image/jpeg")
                .fileSize(13L)
                .build();
        ByteArrayResource resource = new ByteArrayResource("image-content".getBytes());

        when(photoService.getPhotoStream(10L, 5L, serviceBoy))
                .thenReturn(new PhotoService.PhotoStreamResult(photoEntity, resource));

        mockMvc.perform(get("/api/works/10/photos/5"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(content().bytes("image-content".getBytes()));
    }

    @Test
    void testUpdatePhotoMetadata_endpoint() throws Exception {
        UpdatePhotoMetadataRequest request = UpdatePhotoMetadataRequest.builder()
                .caption("Updated caption")
                .category(PhotoCategory.EQUIPMENT_CHECK)
                .build();

        WorkPhotoResponse response = WorkPhotoResponse.builder()
                .id(5L)
                .caption("Updated caption")
                .category("EQUIPMENT_CHECK")
                .build();

        when(photoService.updatePhotoMetadata(eq(10L), eq(5L), any(UpdatePhotoMetadataRequest.class), eq(serviceBoy)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/works/10/photos/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caption").value("Updated caption"))
                .andExpect(jsonPath("$.category").value("EQUIPMENT_CHECK"));
    }

    @Test
    void testDeletePhoto_endpoint() throws Exception {
        doNothing().when(photoService).deletePhoto(10L, 5L, serviceBoy);

        mockMvc.perform(delete("/api/works/10/photos/5"))
                .andExpect(status().isNoContent());

        verify(photoService).deletePhoto(10L, 5L, serviceBoy);
    }
}
