package com.fieldservice.storage;

import com.fieldservice.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class FileSystemPhotoStorageServiceTest {

    @TempDir
    Path tempStorageDir;

    private FileSystemPhotoStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new FileSystemPhotoStorageService(tempStorageDir.toString());
        storageService.init();
    }

    @Test
    void testStoreAndLoadJpeg_success() throws IOException {
        byte[] content = "test-jpeg-content".getBytes();
        ByteArrayInputStream is = new ByteArrayInputStream(content);

        String ref = storageService.store(1L, "photo.jpg", "image/jpeg", is, content.length);

        assertThat(ref).startsWith("1/");
        assertThat(ref).endsWith(".jpg");

        Resource resource = storageService.loadAsResource(ref);
        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
        try (var in = resource.getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void testStorePng_success() {
        byte[] content = "test-png-content".getBytes();
        ByteArrayInputStream is = new ByteArrayInputStream(content);

        String ref = storageService.store(2L, "shot.png", "image/png", is, content.length);

        assertThat(ref).startsWith("2/");
        assertThat(ref).endsWith(".png");
    }

    @Test
    void testStore_unsupportedContentType_rejected() {
        byte[] content = "fake-exe".getBytes();
        ByteArrayInputStream is = new ByteArrayInputStream(content);

        assertThatThrownBy(() -> storageService.store(1L, "script.sh", "application/x-sh", is, content.length))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported media type");
    }

    @Test
    void testDelete_removesFile() {
        byte[] content = "to-delete".getBytes();
        ByteArrayInputStream is = new ByteArrayInputStream(content);

        String ref = storageService.store(1L, "del.jpg", "image/jpeg", is, content.length);
        storageService.delete(ref);

        assertThatThrownBy(() -> storageService.loadAsResource(ref))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testPathTraversal_rejected() {
        assertThatThrownBy(() -> storageService.loadAsResource("../../../windows/system.ini"))
                .isInstanceOf(SecurityException.class);
    }
}
