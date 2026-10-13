package com.parosurvivors.serviya.services.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class ServicePhotoStorageServiceTest {

    @TempDir
    Path tempDir;

    private ServicePhotoStorageService createService() {
        return new ServicePhotoStorageService(tempDir.toString());
    }

    private MultipartFile validImage(String filename) {
        return new MultipartFile() {
            @Override public String getName() { return "files"; }
            @Override public String getOriginalFilename() { return filename; }
            @Override public String getContentType() { return "image/jpeg"; }
            @Override public boolean isEmpty() { return false; }
            @Override public long getSize() { return 100; }
            @Override public byte[] getBytes() { return new byte[]{1, 2, 3}; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(getBytes()); }
            @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), getBytes()); }
            @Override public void transferTo(Path dest) throws IOException { Files.write(dest, getBytes()); }
        };
    }

    private MultipartFile textFile() {
        return new MultipartFile() {
            @Override public String getName() { return "files"; }
            @Override public String getOriginalFilename() { return "doc.txt"; }
            @Override public String getContentType() { return "text/plain"; }
            @Override public boolean isEmpty() { return false; }
            @Override public long getSize() { return 50; }
            @Override public byte[] getBytes() { return new byte[]{1}; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(getBytes()); }
            @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), getBytes()); }
            @Override public void transferTo(Path dest) throws IOException { Files.write(dest, getBytes()); }
        };
    }

    private MultipartFile emptyFile() {
        return new MultipartFile() {
            @Override public String getName() { return "files"; }
            @Override public String getOriginalFilename() { return "empty.jpg"; }
            @Override public String getContentType() { return "image/jpeg"; }
            @Override public boolean isEmpty() { return true; }
            @Override public long getSize() { return 0; }
            @Override public byte[] getBytes() { return new byte[0]; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
            @Override public void transferTo(File dest) {}
            @Override public void transferTo(Path dest) {}
        };
    }

    @Test
    void storePhotosReturnsEmptyWhenNoFiles() {
        ServicePhotoStorageService svc = createService();

        List<String> result = svc.storePhotos(1L, List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void storePhotosReturnsEmptyWhenListIsNull() {
        ServicePhotoStorageService svc = createService();

        List<String> result = svc.storePhotos(1L, null);

        assertThat(result).isEmpty();
    }

    @Test
    void storePhotosThrowsWhenExceeds15Files() {
        ServicePhotoStorageService svc = createService();
        List<MultipartFile> tooMany = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            tooMany.add(validImage("photo" + i + ".jpg"));
        }

        assertThatThrownBy(() -> svc.storePhotos(1L, tooMany))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("15");
    }

    @Test
    void storePhotosThrowsOnInvalidExtension() {
        ServicePhotoStorageService svc = createService();

        assertThatThrownBy(() -> svc.storePhotos(1L, List.of(textFile())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("imagen");
    }

    @Test
    void storePhotosStoresAndReturnsPathsHappyPath() throws IOException {
        ServicePhotoStorageService svc = createService();

        List<String> result = svc.storePhotos(42L, List.of(validImage("a.jpg"), validImage("b.png")));

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).startsWith("/uploads/services/42/").endsWith(".jpg");
        assertThat(result.get(1)).startsWith("/uploads/services/42/").endsWith(".png");
        Path serviceDir = tempDir.resolve("services").resolve("42");
        assertThat(Files.list(serviceDir).count()).isEqualTo(2);
    }

    @Test
    void storePhotosSkipsEmptyFiles() throws IOException {
        ServicePhotoStorageService svc = createService();

        List<String> result = svc.storePhotos(1L, List.of(emptyFile(), validImage("real.jpg")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).endsWith(".jpg");
    }

    @Test
    void deletePhotosHandlesEmptyAndNull() {
        ServicePhotoStorageService svc = createService();

        svc.deletePhotos(null);
        svc.deletePhotos(List.of());
        svc.deletePhotos(List.of("  "));

        // No exception thrown
        assertThat(true).isTrue();
    }

    @Test
    void deletePhotosRemovesExistingFiles() throws IOException {
        ServicePhotoStorageService svc = createService();
        Path serviceDir = tempDir.resolve("services").resolve("1");
        Files.createDirectories(serviceDir);
        Path file = serviceDir.resolve("test.jpg");
        Files.write(file, new byte[]{1, 2, 3});

        svc.deletePhotos(List.of("/uploads/services/1/test.jpg"));

        assertThat(Files.exists(file)).isFalse();
    }
}
